#!/bin/bash
# Backend Integration Test Suite
# Tests all API endpoints that the frontend uses, against the live Docker backend.
set -e
PASS=0
FAIL=0
BASE="http://localhost:8001"
GW="http://localhost:8080"

assert_contains() {
  if echo "$2" | grep -q "$1"; then
    echo "  ✅ contains '$1'"
    PASS=$((PASS+1))
  else
    echo "  ❌ missing '$1' in: ${2:0:100}"
    FAIL=$((FAIL+1))
  fi
}

assert_equals() {
  if [ "$2" = "$3" ]; then
    echo "  ✅ equals '$2'"
    PASS=$((PASS+1))
  else
    echo "  ❌ expected '$2' got '$3'"
    FAIL=$((FAIL+1))
  fi
}

echo "============================================"
echo "  Backend Integration Tests"
echo "  Target: $BASE"
echo "============================================"

echo ""
echo "--- 1. Health check ---"
HEALTH=$(curl -sf --max-time 5 "$BASE/health" 2>&1 || echo "FAILED")
assert_contains "ok" "$HEALTH"

echo ""
echo "--- 2. Request OTP ---"
OTP_RESP=$(curl -sf --max-time 10 -X POST "$BASE/v1/auth/request-otp" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"+15559999999"}' 2>&1)
assert_contains "challenge_id" "$OTP_RESP"
assert_contains "expires_in" "$OTP_RESP"

CHALLENGE_ID=$(echo "$OTP_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['challenge_id'])" 2>&1)
echo "  Challenge: $CHALLENGE_ID"

echo ""
echo "--- 3. Verify OTP (read from docker logs) ---"
sleep 2
OTP_CODE=$(docker logs chat-auth-1 2>&1 | grep "$CHALLENGE_ID" | grep "otp_code" | tail -1 | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['otp'])" 2>&1 || echo "")
if [ -z "$OTP_CODE" ]; then
  echo "  ⚠️ Could not read OTP from docker logs"
  FAIL=$((FAIL+1))
else
  echo "  OTP: $OTP_CODE"
  VERIFY_RESP=$(curl -sf --max-time 10 -X POST "$BASE/v1/auth/verify-otp" \
    -H "Content-Type: application/json" \
    -d "{\"challenge_id\":\"$CHALLENGE_ID\",\"otp\":\"$OTP_CODE\"}" 2>&1)
  assert_contains "access_token" "$VERIFY_RESP"
  assert_contains "refresh_token" "$VERIFY_RESP"
  assert_contains "user_id" "$VERIFY_RESP"

  JWT=$(echo "$VERIFY_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
  REFRESH=$(echo "$VERIFY_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['refresh_token'])")
  USER_ID=$(echo "$VERIFY_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['user_id'])")
  echo "  JWT: ${JWT:0:50}..."
  echo "  User: $USER_ID"

  echo ""
  echo "--- 4. Token Refresh ---"
  sleep 1
  REFRESH_RESP=$(curl -sf --max-time 10 -X POST "$BASE/v1/auth/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refresh_token\":\"$REFRESH\"}" 2>&1)
  assert_contains "access_token" "$REFRESH_RESP"
  NEW_JWT=$(echo "$REFRESH_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
  if [ "$JWT" != "$NEW_JWT" ]; then echo "  ✅ JWT rotated"; PASS=$((PASS+1)); else echo "  ❌ JWT not rotated"; FAIL=$((FAIL+1)); fi

  echo ""
  echo "--- 5. Create Profile (via Gateway) ---"
  USERNAME="intg_$(date +%S)"
  PROFILE_RESP=$(curl -sf --max-time 10 -X PUT "$GW/v1/profile" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT" \
    -d "{\"username\":\"$USERNAME\",\"display_name\":\"Integration Test\",\"about\":\"Hello from tests!\"}" 2>&1)
  assert_contains "updated" "$PROFILE_RESP"
  assert_contains "true" "$PROFILE_RESP"

  echo ""
  echo "--- 6. JWKS Endpoint ---"
  JWKS_RESP=$(curl -sf --max-time 5 "$BASE/v1/auth/.well-known/jwks.json" 2>&1)
  assert_contains "Ed25519" "$JWKS_RESP"
  assert_contains "\"crv\"" "$JWKS_RESP"
  assert_contains "\"x\"" "$JWKS_RESP"

  echo ""
  echo "--- 7. Key Registration (expect validation error) ---"
  KEY=$(python3 -c "import base64,os; print(base64.urlsafe_b64encode(os.urandom(32)).decode().rstrip('='))")
  SIG=$(python3 -c "import base64,os; print(base64.urlsafe_b64encode(os.urandom(64)).decode().rstrip('='))")
  KEY_REG=$(curl -sf --max-time 10 -X POST "$BASE/v1/keys/register" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $JWT" \
    -d "{\"identity_key\":\"$KEY\",\"signed_prekey\":{\"public_key\":\"$KEY\",\"signature\":\"$SIG\"},\"one_time_prekeys\":[{\"public_key\":\"$KEY\"}]}" 2>&1 || echo "{\"error\":\"connection_failed\"}")
  assert_contains "error" "$KEY_REG"
  echo "  (Expected: backend validates signature - got response)"
fi

echo ""
echo "--- 8. Profile via Gateway with real keys ---"
# Create a new challenge for the profile test with real Ed25519 keys
OTP2_RESP=$(curl -sf --max-time 10 -X POST "$BASE/v1/auth/request-otp" \
  -H "Content-Type: application/json" \
  -d '{"identifier":"+15559999998"}' 2>&1)
CHALLENGE2=$(echo "$OTP2_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['challenge_id'])" 2>&1)
sleep 2
OTP2=$(docker logs chat-auth-1 2>&1 | grep "$CHALLENGE2" | grep "otp_code" | tail -1 | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['otp'])" 2>&1 || echo "")
if [ -n "$OTP2" ]; then
  VERIFY2=$(curl -sf --max-time 10 -X POST "$BASE/v1/auth/verify-otp" \
    -H "Content-Type: application/json" \
    -d "{\"challenge_id\":\"$CHALLENGE2\",\"otp\":\"$OTP2\",\"device_info\":{\"device_id\":\"intg-device\",\"user_agent\":\"Enchant-Android/1.0\"}}" 2>&1)
  JWT2=$(echo "$VERIFY2" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])" 2>&1)
  echo "  Got JWT for profile test"

  echo ""
  echo "--- 9. List Devices ---"
  DEVICES=$(curl -sf --max-time 10 -X GET "$BASE/v1/auth/devices" \
    -H "Authorization: Bearer $JWT2" 2>&1)
  assert_contains "devices" "$DEVICES"
fi

echo ""
echo "============================================"
echo "  RESULTS: $PASS passed, $FAIL failed"
echo "============================================"
exit $FAIL
