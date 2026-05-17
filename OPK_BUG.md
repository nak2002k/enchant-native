# OPK Upload Bug — HTTP 400 "at least 20 one time prekeys required"

## Symptom
On key setup/registration, the server returns HTTP 400:
```
at least 20 one time prekeys required
```

## Root Cause
`KeyManager.topUpOpks()` at `core/crypto/src/main/java/org/enchant/core/crypto/KeyManager.kt:245` calls `/v1/keys/opk-count` to check remaining OPKs, then generates and uploads a batch. The upload sends via `uploadOpks()` at line 265 which POSTs to `/v1/keys/opks`.

The server enforces a minimum of 20 OPKs. The batch size (`OPK_BATCH_SIZE` = 100 in RemoteConfig) should satisfy this, but the issue is either:
1. The `/v1/keys/opk-count` endpoint returns a different field name than expected by the client
2. The OPK upload request body format doesn't match the server's expected schema
3. `topUpOpks()` is never called during initial key generation (check if `KeyManager.init()` triggers it)

Check `core/auth/src/main/java/org/enchant/core/auth/AuthRepository.kt:178` for the repository-level upload logic and `/v1/keys/opk-count` response parsing.
