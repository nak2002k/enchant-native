# feature:groups Audit

## Security Issues

1. **Invite link token not encrypted in transit or at rest**
   - `GroupsRepository.joinViaLink()` sends linkCode as plaintext path param `/v1/groups/join/$linkCode`
   - `GroupEditor.cycleGroupLinkPassword()` suggests link has a password but no Encryption layer visible
   - Invite link displayed directly in UI (`GroupInfoScreen`, `GroupInviteScreen`) with no redaction
   - **Impact**: Link stolen in network logs, shared screenshots, or device backup

2. **Role enforcement is UI-only, not server-verified**
   - `GroupInfoScreen` checks `group.myRole == "owner"` locally to show delete/promote buttons
   - `GroupMemberListScreen` checks `isAdmin` locally to show add/remove options
   - No `GroupEditor` method verifies permissions before executing privileged operations
   - **Impact**: Malicious user with direct API access can call `removeMember`, `setMemberAdmin`, `ejectMember`, `banUser`, `transferOwnership`, `terminateGroup` without server-side role check

3. **Member removal bypasses ban check**
   - `GroupEditor.removeMember()` and `GroupEditor.ejectMember()` are separate code paths
   - No API call to `banUser()` before removal
   - Former member can rejoin via link if link is still active
   - **Impact**: Banned users can re-enter group

4. **Group link state change not protected**
   - `GroupEditor.setJoinByGroupLinkState()` takes `GroupLinkState` but no permission check visible
   - Any member could toggle link on/off
   - **Impact**: Non-admin can enable/disable group join by link

5. **Ban list not checked before addMembers**
   - `GroupsRepository.addMembers()` and `GroupEditor.addMembers()` do not query banned users list
   - Banned users can be re-added
   - **Impact**: Previously banned users can rejoin

6. **Join request approval has no rate limiting**
   - Approve/deny buttons in `JoinRequestsScreen` trigger API calls directly on click
   - No debounce or throttle
   - **Impact**: Accidental double-tap can approve/deny twice

7. **Revision conflicts not enforced on reads**
   - `GroupStateProcessor.updateLocalGroupToRevision()` iterates change log but no optimistic locking
   - Stale data could be written if concurrent modification
   - **Impact**: Lost updates in multi-client scenarios

## Bugs

1. **Role case mismatch between screens and repository**
   - `GroupMemberListScreen.kt` uses `"OWNER"`, `"ADMIN"`, `"MEMBER"` (uppercase)
   - `GroupsRepository.kt` line 258 uses `"member"`, `"admin"`, `"superadmin"` (lowercase)
   - `GroupEntity.kt` likely stores lowercase
   - `GroupInfoScreen.kt` line 234 uses `"owner"` (lowercase)
   - **Impact**: Role comparisons fail silently; dropdown menus for role changes never show

2. **Duplicate data class definitions cause ambiguity**
   - `GroupMember` defined in `GroupsRepository.kt` line 31
   - `GroupMember` redefined in `GroupMemberListScreen.kt` line 15 with different fields (`displayName: String` vs `displayName: String?`)
   - `JoinRequest` defined in `GroupsRepository.kt` line 45
   - `JoinRequest` redefined in `JoinRequestsScreen.kt` line 18 with different fields
   - **Impact**: Type mismatch when passing data between layers; runtime crashes

3. **Hard-coded singleton in GroupsViewModel**
   - Line 31: `constructor() : this(GroupsRepository(ApiClient.getInstance(), DatabasePool.instance!!))`
   - Uses `!!` null check operator — crashes if not initialized
   - `GroupsRepository` also stores `DatabasePool` directly (no interface/mockability)
   - **Impact**: Unit tests must use reflection or unsafe pattern; CI reliability

4. **getCachedGroups Flow never updates**
   - `GroupsRepository.getCachedGroups()` (line 158) is a `callbackFlow` that fetches data once and closes
   - No mechanism to observe database changes and re-emit
   - No Flow subscription for live updates
   - **Impact**: UI shows stale group list until manually refreshed

5. **No error propagation in JoinRequestsScreen**
   - Lines 40-55: `LaunchedEffect` silently swallows failures (`onFailure = { }`)
   - API errors silently ignored, user sees empty list with no indication of failure
   - **Impact**: Network failures masked; user thinks no requests exist

6. **GroupsViewModel.loadGroupInfo() discards member data on failure**
   - Line 85-88: `else -> {}` — failure in `getGroupInfo` leads to empty `when`
   - No error shown to user, `currentGroup` stays null or stale
   - **Impact**: User gets no feedback on failure

7. **Concurrent modification race in GroupStateProcessor**
   - `updateLocalGroupToRevision()` fetches changelog then applies each revision sequentially
   - Between fetching changelog and applying first delta, another client could modify group
   - No locking mechanism
   - **Impact**: Replay attack or lost updates

8. **Delete operation does not clear local messages**
   - `GroupsRepository.deleteGroup()` (line 291) deletes group and members from DB
   - No call to clear chat messages associated with group
   - No call to clear pending invites list
   - **Impact**: Orphaned messages remain in local database

9. **Leave group operation missing**
   - `GroupsViewModel` has `deleteGroup` and `removeMember` but no `leaveGroup()` method
   - Non-owner cannot leave group via UI
   - `GroupInfoScreen` line 179-190 shows "Leave Group" button for non-owners but no handler
   - **Impact**: Users cannot leave groups they do not own

10. **Preview invite link leaks sensitive info**
    - `GroupsRepository.previewInviteLink()` returns group name, description, member count
    - This is a public endpoint — no auth check
    - **Impact**: Anyone with link code can enumerate group metadata

## Completeness Gaps

1. **Transfer ownership not tested**
   - `GroupsViewModel.transferOwnership()` exists but `GroupsViewModelTest` has no test for it
   - No test for ownership transfer conflict (new owner is already admin, etc.)

2. **Group update operations incomplete**
   - `GroupEditor` missing: `updateGroupDescription`, `updateGroupAvatar`, `updateGroupName` (each bundled in a generic update)
   - Disappearing messages timer set (`updateGroupTimer`) but no getter to display current value
   - No way to read current group access policies (`attributesAccess`, `membershipAccess`)

3. **Ban/unban operations not exposed to UI**
   - `GroupEditor.banUser()` and `unbanUser()` exist but no `GroupsViewModel` method calls them
   - No screen to view banned users

4. **Eject operation not exposed to UI**
   - `GroupEditor.ejectMember()` exists but `GroupsViewModel` has no `ejectMember()` method
   - `GroupInfoScreen` has no eject option (only remove which is different)

5. **Join via QR code not implemented**
   - `GroupInviteScreen` line 131 shows "QR Code Placeholder"
   - No actual QR generation or scanning

6. **No group media/avatar upload/download**
   - Referenced as `avatar_media_id` in `GroupStateProcessor` but no upload endpoint
   - No screen to change avatar

7. **No group search/discovery**
   - No endpoint to search public groups
   - Users can only join via link or request

8. **Revoke invite link not exposed to UI**
   - `GroupsRepository.revokeInviteLink()` exists but no UI action calls it
   - User cannot revoke active invite links

9. **No "block and remove" for bad actors**
   - `GroupEditor.ejectMember(userId, block=true, removeMessages=false)` exists but no UI
   - No way for owner to block a user AND remove them in one action from UI

10. **loadJoinRequests has no error handling**
    - Line 244-248: `loadJoinRequests` catches exceptions silently and returns empty list
    - No UI feedback for failures

## Code Quality Issues

1. **GroupsViewModel is a God object**
   - Single class handles: group CRUD, member management, invites, join requests, link preview
   - 270 lines, 20+ public functions
   - Violates Single Responsibility Principle

2. **Inconsistent error handling patterns**
   - `GroupsRepository` uses `Result`monad with `GroupResult.Failed`
   - `GroupEditor` throws raw `Throwable` and string-parses error messages (line 194-198)
   - `JoinRequestsScreen` has `onFailure = { }` empty blocks
   - No unified error handling strategy

3. **Revision extraction via string parsing is fragile**
   - Line 194-198: `extractRevision()` parses error message via `error.message` splitting
   - Server could change message format at any time
   - Unit test coverage for revision extraction is absent

4. **API endpoint paths duplicated across repository and editor**
   - GroupsRepository and GroupEditor both call same endpoints with same paths
   - No shared constants or single source of truth
   - Drift between the two will cause silent failures

5. **Null safety violations**
   - `GroupsViewModel.loadGroupInfo()` returns `GroupResult` but `repo.getGroupInfo()` can return `null` (line 25 mock says `returns null`)
   - No nullable check before accessing fields
   - `GroupInfoScreen()` line 50 assumes `group != null` but crashes on null

6. **GroupsRepository mixes network calls with direct SQL**
   - Lines 100-106: `pool.write {}` executes raw SQL strings directly
   - No use of DAO pattern (despite `GroupDao` existing and imported in `GroupStateProcessor`)
   - SQL injection risk with string interpolation (line 133-136, 298-299)

7. **Hardcoded retry delays**
   - `GroupEditor.executeWithRetry()` line 183: `delay(1000L * attempt)`
   - Exponential backoff but hardcoded 1 second base
   - No jitter — vulnerable to thundering herd

8. **Magic numbers and strings everywhere**
   - Role strings: `"owner"`, `"admin"`, `"superadmin"`, `"member"`, `"OWNER"`, `"ADMIN"`, `"MEMBER"`
   - No enum for MemberRole
   - Comparison strings scattered across files

9. **UI state mutation in viewModelScope without snapshot isolation**
   - Line 37-43: `_uiState.value = _uiState.value.copy()` followed by async call
   - If two operations interleave, state can become inconsistent
   - `loadGroups()` + `loadGroupInfo()` running concurrently both modify `isLoading`

10. **Test coverage gaps**
    - No tests for `GroupEditor` (the core edit operations)
    - No tests for `GroupStateProcessor`
    - No tests for race conditions in concurrent member add/remove
    - No tests for revision conflict handling
    - `GroupsViewModelTest` line 25: `coEvery { repo.getGroupInfo(any()) } returns null` — the repo returns `GroupResult`, not null, causing test to pass with wrong mock

11. **GroupStateProcessor silently swallows exceptions in getGroupChangeLog**
    - Line 88: `catch (_: Exception) { emptyList() }` 
    - All errors become empty lists — no differentiation between 404, 401, 500, network error
    - Debugging production issues impossible

12. **No pagination on getGroups or getMembers**
    - `GroupsRepository.getGroups()` returns all groups in one call
    - `GroupsRepository.getMembers()` returns all members
    - Large groups could cause OOM or timeout

13. **No WebSocket / real-time updates**
    - `GroupStateProcessor.handleP2PChange()` exists but no WebSocket subscription
    - No mechanism to receive live group updates from server
    - UI is eventually consistent only on reload

14. **GroupsRepository uses deprecated raw SQL**
    - Line 131: `db.execSQL("DELETE FROM groups_table")` — no WHERE clause wipes entire table
    - Should use transaction-wrapped batch Insert
    - Line 144: `db.rawQuery("SELECT * FROM groups_table", null)` — no projection, no sorting

15. **Inconsistent naming between API models and data models**
    - API returns `group_id`, code uses `groupId` (mixing snake_case and camelCase)
    - API returns `member_count`, code uses `memberCount`
    - No centralized mapping/transformation layer

## Recommendations (prioritized)

### Critical (Security/Breach Risk)
1. Move permission checks from UI to `GroupEditor` or introduce a server-side ACL layer before API calls
2. Add ban-list lookup before `addMembers()` in `GroupsRepository`
3. Encrypt invite link tokens at rest in SharedPreferences /KeyStore; use HTTPS only
4. Add revocation check before `acceptInvite()` for banned users

### High (Functional Bugs)
1. Fix role case mismatch by creating a `MemberRole` enum and using it everywhere
2. Remove duplicate `GroupMember`/`JoinRequest` definitions — consolidate to one source in `data/` package
3. Add `leaveGroup()` method to `GroupsViewModel` backed by `GroupsRepository.leaveGroup()`
4. Implement proper Flow-based caching in `getCachedGroups()` or remove it entirely
5. Add error feedback to `JoinRequestsScreen` instead of silent `onFailure = { }`

### Medium (Reliability)
1. Replace string-parsed revision extraction with structured error types from API
2. Add pagination to `getGroups()` and `getMembers()`
3. Replace raw SQL with DAO pattern (existing `GroupDao` should be used)
4. Add retry with jitter to `executeWithRetry()`
5. Add circuit breaker for repeated API failures

### Low (Code Quality)
1. Extract `GroupsViewModel` responsibilities into separate use-case classes (AddMemberUseCase, RemoveMemberUseCase, etc.)
2. Introduce shared API route constants to prevent drift
3. Add integration tests for concurrent member add/remove
4. Create `MemberRole` enum and replace all string literals
5. Add structured logging for all API failures with groupId correlation
