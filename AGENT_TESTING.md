# Agent Testing (Debug Builds Only)

The **agent debug control plane** lets automated agents drive the Enchant Android app through text/JSON instead of tapping the UI. It is compiled **only into debug APKs** and is absent from release builds.

## Setup

```bash
# Build and install debug APK
cd frontend
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:installDebug

# Forward device port to host (agent server runs on the device)
adb forward tcp:19789 tcp:19789

# Launch app
adb shell am start -n org.enchant.messenger/org.enchant.MainActivity

# Tail structured events
adb logcat -s ENCHANT_AGENT
```

Base URL: `http://127.0.0.1:19789`

## Quick reference

| Action | Command |
|--------|---------|
| Health | `curl http://127.0.0.1:19789/health` |
| Full state | `curl http://127.0.0.1:19789/state` |
| List endpoints | `curl http://127.0.0.1:19789/help` |
| Event log | `curl 'http://127.0.0.1:19789/events?since=0&limit=50'` |
| Request OTP | `curl -X POST http://127.0.0.1:19789/auth/request-otp -H 'Content-Type: application/json' -d '{"identifier":"+15551234567"}'` |
| Verify OTP | `curl -X POST http://127.0.0.1:19789/auth/verify-otp -H 'Content-Type: application/json' -d '{"otp":"123456"}'` |
| Full registration | `curl -X POST http://127.0.0.1:19789/auth/full-flow -H 'Content-Type: application/json' -d '{"identifier":"+15551234567","otp":"123456","username":"alice01","display_name":"Alice"}'` |
| Skip to main (if already registered) | `curl -X POST http://127.0.0.1:19789/auth/skip-to-main` |
| Agree & Continue (welcome screen) | `curl -X POST http://127.0.0.1:19789/auth/accept-terms` |
| Skip permissions screen | `curl -X POST http://127.0.0.1:19789/auth/skip-permissions` |
| Skip PIN / app-lock screens | `curl -X POST http://127.0.0.1:19789/auth/skip-pin` then `curl -X POST http://127.0.0.1:19789/auth/skip-applock` |
| Open chats tab | `curl -X POST http://127.0.0.1:19789/nav -H 'Content-Type: application/json' -d '{"target":"chats"}'` |
| Open conversation | `curl -X POST http://127.0.0.1:19789/nav -H 'Content-Type: application/json' -d '{"target":"conversation","user_id":"USER_UUID"}'` |
| Send message | `curl -X POST http://127.0.0.1:19789/messages/send -H 'Content-Type: application/json' -d '{"recipient_user_id":"USER_UUID","text":"hello from agent"}'` |
| Send media | `curl -X POST http://127.0.0.1:19789/messages/media -H 'Content-Type: application/json' -d '{"recipient_user_id":"USER_UUID","conversation_id":"USER_UUID","file_path":"/sdcard/Download/test.jpg","mime_type":"image/jpeg"}'` |
| Send reaction | `curl -X POST http://127.0.0.1:19789/messages/reaction -H 'Content-Type: application/json' -d '{"conversation_id":"USER_UUID","emoji":"👍","envelope_id":"ENV_UUID"}'` |
| Create group | `curl -X POST http://127.0.0.1:19789/groups/create -H 'Content-Type: application/json' -d '{"name":"Test Group","initial_member_ids":["USER_UUID"]}'` |
| Start call | `curl -X POST http://127.0.0.1:19789/calls/start -H 'Content-Type: application/json' -d '{"remote_user_id":"USER_UUID","video":false}'` |
| Post status | `curl -X POST http://127.0.0.1:19789/status/text -H 'Content-Type: application/json' -d '{"text":"Hello status"}'` |
| Set app lock PIN | `curl -X POST http://127.0.0.1:19789/applock/set -H 'Content-Type: application/json' -d '{"pin":"123456"}'` |
| List conversations | `curl http://127.0.0.1:19789/conversations` |
| Connect WebSocket | `curl -X POST http://127.0.0.1:19789/network/ws/connect` |
| Crypto status | `curl http://127.0.0.1:19789/crypto/status` |

Read OTP from backend logs (dev mode), then call verify-otp or full-flow.

## What agents can do

Everything goes through the **same code paths as UI buttons**:

- **Auth**: request OTP, verify, register keys, set profile, complete registration, logout
- **Auth UI (debug)**: accept terms, skip permissions, skip PIN, skip app lock — same taps as welcome/permissions screens
- **Navigation**: switch tabs (chats/calls/stories), open conversations, settings, contacts, profile
- **Messaging**: list conversations, read messages, send text (optional sealed sender), media, stickers, reactions
- **Groups**: list, create, add members, join via invite link
- **Calls**: start outgoing call, read call log
- **Status**: feed, create text/media status, mark viewed
- **Stickers**: library, featured, install pack, send in chat
- **Backup**: cloud initiate/latest/restore, local encrypted export/import
- **App lock**: set PIN, verify PIN, disable
- **Contacts**: list, add, remove, blocked users
- **Network**: WebSocket connect/disconnect, connection state
- **Observability**: `/state`, `/events`, logcat `ENCHANT_AGENT` lines

## Agent workflow example

```bash
# 1. Check app is ready
curl -s http://127.0.0.1:19789/state | jq .

# 2. Register (OTP from backend logs)
curl -s -X POST http://127.0.0.1:19789/auth/full-flow \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"+15550000001","otp":"YOUR_OTP","username":"agent1","display_name":"Agent One"}'

# 3. Connect realtime
curl -s -X POST http://127.0.0.1:19789/network/ws/connect

# 4. Send a message
curl -s -X POST http://127.0.0.1:19789/messages/send \
  -H 'Content-Type: application/json' \
  -d '{"recipient_user_id":"OTHER_USER_UUID","text":"test"}'

# 5. Collect results
curl -s 'http://127.0.0.1:19789/events?since=0' | jq .
```

## Security

- Server binds **`127.0.0.1:19789`** only — not reachable from the network without `adb reverse`
- Module `:core:agent-debug` is **`debugImplementation`** — not in release APK
- Endpoints do **not** return private keys or full JWTs
- For production builds, this entire surface is **not compiled in**

## Extending

Add new endpoints in:

1. `core/agent-debug/.../AgentAppBridge.kt` — interface
2. `app/src/debug/.../EnchantAgentBridge.kt` — implementation (call real managers)
3. `core/agent-debug/.../AgentDebugServer.kt` — route mapping

Track UI screens via `AgentUiTracker.setAuthRoute()` / `setMainNavigation()` from Compose when needed.

## Firebase / `google-services.json`

Debug and release builds use the Google Services Gradle plugin (FCM push, Crashlytics). Gradle expects this file at:

`frontend/app/google-services.json`

It is **gitignored** (project-specific IDs). To obtain it:

1. Open [Firebase Console](https://console.firebase.google.com)
2. Create a project (or use an existing one)
3. **Add app** → Android → package name **`org.enchant.messenger`**
4. Download **`google-services.json`**
5. Copy it to `frontend/app/google-services.json`

See `frontend/app/google-services.json.example` for the expected shape. Push notifications and Crashlytics will not work until a real file is in place; the agent debug server does not require Firebase.
