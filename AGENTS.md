# enchant-native — Agent Instructions

> Every agent **must** read this file before any other file.

## Reading Order

Before doing anything, read these files in this exact order:

```
1. AGENTS.md                    ← You are here
2. AGENT_QUALITY_RULES.md       ← Strict testing & code standards
3. BACKEND_API_REFERENCE.md     ← Complete backend API (REST + WebSocket)
4. LEADING_APPS_REFERENCE_MAP.md  ← Signal Android architecture reference
5. PRODUCTION_REFERENCE.md      ← Production rules (10 categories, Signal-proven)
6. SCALABILITY_ANDROID.md       ← Android performance & scalability targets
7. SECURITY_ANDROID_PRACTICES.md  ← Android-specific security best practices
```

## Commit & Push Rule

Any change, no matter how small, must be committed and pushed to `main` immediately.

```bash
git add -A
git commit -m "description of change"
git push
```

Do not batch unrelated changes. Each logical change gets its own commit.

## Parallel Agent Rule

When multiple agents work in parallel:
1. Each agent creates its own branch: `feat/<short-description>`
2. Work on that branch, committing and pushing regularly
3. Create a Pull Request targeting `main`
4. PR must be reviewed before merging
5. Only after merge does change land in `main`

## Backend

The backend API is defined in `BACKEND_API_REFERENCE.md`. The live endpoint uses a Cloudflare tunnel. Services run on ports 8001-8022 + 8099 (admin).

## Test Every Edge Case

Every class, widget, provider, and utility must have tests covering:
1. Happy path — expected inputs produce expected outputs
2. Error/edge cases — null inputs, empty lists, network failures, malformed data
3. Boundary conditions — max lengths, timeouts, pagination limits
4. State transitions — loading → success → error, offline → online, unauthenticated → authenticated
5. Security invariants — verify no plaintext leaks in logs, no crashes on corrupted data

Run tests before every commit.
