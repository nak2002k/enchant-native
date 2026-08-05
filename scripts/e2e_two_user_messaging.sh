#!/usr/bin/env bash
# Two accounts on ONE emulator: org.enchant.messenger + org.enchant.messenger.peer
set -euo pipefail

register_user() {
  local pkg=$1
  local port=$2
  local phone=$3
  local username=$4
  local display=$5
  local agent="http://127.0.0.1:$port"

  # Only the user id may reach stdout; it is captured by the caller.
  echo "=== Register $username ($pkg, $phone) ===" >&2
  adb shell pm clear "$pkg" >/dev/null
  adb shell am start -n "$pkg/org.enchant.MainActivity" >/dev/null
  adb forward "tcp:$port" "tcp:$port" >/dev/null

  # Cold start after pm clear is slow and variable; poll until the agent binds.
  local i
  for i in $(seq 1 60); do
    curl -sf "$agent/health" >/dev/null 2>&1 && break
    sleep 1
  done
  curl -sf "$agent/health" >/dev/null || { echo "agent $port never came up" >&2; exit 1; }
  for i in $(seq 1 60); do
    curl -sf "$agent/state" 2>/dev/null | rg -q '"di_initialized":true' && break
    sleep 1
  done

  curl -sf -X POST "$agent/ui/action" -H 'Content-Type: application/json' -d '{"action":"skip_to_phone"}' >/dev/null
  sleep 1
  curl -sf -X POST "$agent/ui/phone" -H 'Content-Type: application/json' -d "{\"phone\":\"$phone\"}" >/dev/null
  sleep 4

  local phone_digits=${phone#+}
  local otp
  otp=$(podman logs enchant-auth-1 2>&1 | rg '"event":"otp_code"' | rg "$phone_digits" | tail -1 | sed -E 's/.*"otp":"([0-9]{6})".*/\1/')
  [ ${#otp} -eq 6 ] || { echo "OTP not found for $phone" >&2; exit 1; }
  echo "  OTP=$otp" >&2

  curl -sf -X POST "$agent/ui/otp" -H 'Content-Type: application/json' -d "{\"otp\":\"$otp\"}" >/dev/null
  sleep 4
  curl -sf -X POST "$agent/auth/profile" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"display_name\":\"$display\",\"about\":\"E2E $username\"}" >/dev/null
  sleep 1
  curl -sf -X POST "$agent/auth/register-keys" >/dev/null
  sleep 8
  curl -sf -X POST "$agent/ui/action" -H 'Content-Type: application/json' -d '{"action":"skip_pin"}' >/dev/null
  sleep 2
  curl -sf -X POST "$agent/ui/applock" -H 'Content-Type: application/json' -d '{"pin":"123456"}' >/dev/null
  sleep 2
  curl -sf -X POST "$agent/auth/skip-to-main" >/dev/null || true
  curl -sf -X POST "$agent/network/ws/connect" >/dev/null || true
  sleep 3

  curl -sf "$agent/state" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['user_id'])"
}

ALICE_ID=$(register_user org.enchant.messenger 19789 '+15553342001' alice 'Alice')
BOB_ID=$(register_user org.enchant.messenger.peer 19790 '+15553342002' bob 'Bob')

echo ""
echo "Alice: $ALICE_ID"
echo "Bob:   $BOB_ID"

echo "=== Add contacts ==="
curl -s -X POST http://127.0.0.1:19789/contacts/add -H 'Content-Type: application/json' \
  -d "{\"user_id\":\"$BOB_ID\",\"custom_name\":\"Bob\"}"; echo
curl -s -X POST http://127.0.0.1:19790/contacts/add -H 'Content-Type: application/json' \
  -d "{\"user_id\":\"$ALICE_ID\",\"custom_name\":\"Alice\"}"; echo

echo "=== Alice -> Bob ==="
curl -s -X POST http://127.0.0.1:19789/messages/send -H 'Content-Type: application/json' \
  -d "{\"recipient_user_id\":\"$BOB_ID\",\"text\":\"Hello Bob — encrypted test from Alice\"}"; echo
sleep 10

echo "=== Bob inbox ==="
curl -s "http://127.0.0.1:19790/conversations/$ALICE_ID/messages?limit=10"; echo

echo "=== Bob -> Alice ==="
curl -s -X POST http://127.0.0.1:19790/messages/send -H 'Content-Type: application/json' \
  -d "{\"recipient_user_id\":\"$ALICE_ID\",\"text\":\"Hi Alice — Bob got your message\"}"; echo
sleep 10

echo "=== Alice inbox ==="
curl -s "http://127.0.0.1:19789/conversations/$BOB_ID/messages?limit=10"; echo

echo "=== Crypto status ==="
curl -s http://127.0.0.1:19789/crypto/status; echo
curl -s http://127.0.0.1:19790/crypto/status; echo

echo "=== DONE ==="
