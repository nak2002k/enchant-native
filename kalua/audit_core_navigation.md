# Audit Report: core:navigation Module

**Module Path:** `/home/nsk/project/personal/enchant-native/core/navigation/`
**Audit Date:** 2026-05-29
**Files Analyzed:**
- `core/navigation/src/main/java/org/enchant/navigation/SafeNavigation.kt`
- `core/navigation/src/test/java/org/enchant/navigation/SafeNavigationTest.kt`
- `core/navigation/src/test/java/org/enchant/navigation/NavRouteTest.kt`
- `core/navigation/legacy/NavHost.kt`
- `core/navigation/legacy/NavRoute.kt`

---

## 1. Security Analysis

### CRITICAL: Unencoded Route Parameters (URL Injection Risk)
**Files:** `legacy/NavRoute.kt`, `legacy/NavHost.kt`

Route parameters are directly interpolated into route strings without URL encoding:

```kotlin
data class Conversation(val conversationId: String) : NavRoute() { 
    override val route = "conversation/{conversationId}" 
}
```

If `conversationId` contains characters like `/`, `#`, `?`, or `&`, these will corrupt the URL path and potentially allow path traversal or data leakage in logs.

**Example attack vector:**
```
conversationId = "../../../etc/passwd"
Result: "conversation/../../../etc/passwd"
```

**Recommendation:** Use `Uri.encode()` for all path parameters before constructing route strings, or use Navigation Safe Args with typed arguments.

---

### HIGH: Legacy NavHost Uses Raw String Concatenation
**File:** `legacy/NavHost.kt`

The `navigate()` and `navigateAndClearStack()` functions build route strings via string interpolation instead of using Navigation component argument bundles:

```kotlin
is NavRoute.Conversation -> "conversation/${conversationId}"
```

This bypasses Android's argument type safety and does not integrate with deep link verification. If a deep link is malformed or contains unexpected data, it won't be validated.

---

### MEDIUM: SafeNavigation Missing Action Verification for Deep Links
**File:** `core/navigation/src/main/java/org/enchant/navigation/SafeNavigation.kt`

The `safeNavigate` functions check `currentDestination?.getAction(resId)` before navigating. However, when a navigation is triggered via a deep link intent, the action resolution path differs and may bypass this check in edge cases. The safe navigation guards work for programmatic navigation but deep links handled by the system may not go through the same destination check.

---

### LOW: Sensitive Data in Log Statements
**File:** `SafeNavigation.kt` lines 35, 45

```kotlin
Log.w(TAG, "No ${getDisplayName(directions.actionId)} for $currentDestination")
```

While resource ID display names are generally safe, `currentDestination` string representation could contain sensitive route information if logged in production.

---

## 2. Bugs

### BUG 1: Dead Field in Search Route
**File:** `legacy/NavRoute.kt` line 22

```kotlin
data class Search(val conversationId: String? = null) : NavRoute() { 
    override val route = "search" 
}
```

The `conversationId` parameter is never used. The route string is always `"search"` regardless of the parameter value. This creates misleading API where callers might expect `Search("conv-123")` to navigate to a conversation-specific search, but it behaves identically to `Search()`.

---

### BUG 2: Duplicated Route String Generation Logic
**Files:** `legacy/NavRoute.kt`, `legacy/NavHost.kt`

Both files contain nearly identical when-expressions to convert NavRoute objects to route strings:

**In NavRoute.kt (not actually used for navigation):**
```kotlin
data class Conversation(val conversationId: String) : NavRoute() { 
    override val route = "conversation/{conversationId}" 
}
```

**In NavHost.kt (actually used for navigation):**
```kotlin
is NavRoute.Conversation -> "conversation/${conversationId}"
```

This duplication is error-prone. If one is updated without the other, behavior diverges. The proper pattern is to have NavRoute generate its own route string and NavHost delegate to it.

---

### BUG 3: Missing Type Safety in SafeNavigation Bundle Overload
**File:** `SafeNavigation.kt` line 23-29

```kotlin
fun NavController.safeNavigate(@IdRes resId: Int, arguments: Bundle?) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId, arguments)
    }
}
```

The `arguments: Bundle?` parameter has no type safety. Callers can pass incorrectly typed arguments that will fail at runtime. No compile-time validation ensures argument names and types match the destination's expected arguments.

---

### BUG 4: Inconsistent Route Parameter Handling
**Files:** `legacy/NavRoute.kt` lines 21-51

Some routes like `Conversation`, `IncomingCall`, etc. use path parameters. Others like `Search` have unused parameters. There is no consistency in:
- Whether parameters are optional or required
- Whether parameters are used in route construction
- Parameter naming conventions

---

## 3. Completeness Assessment

### Missing Features:

1. **No Safe Args Integration**
   The module doesn't use the Navigation Safe Args plugin, which provides compile-time type safety for navigation arguments.

2. **No Nested Navigation Graph Support**
   No utilities for handling child navigation controllers or nested graphs.

3. **No Back Stack Management Utilities**
   No helpers for:
   - Clearing specific destinations from back stack
   - Handling multiple same-destination navigations
   - Preventing duplicate destinations

4. **No Argument Validation**
   No utilities to validate that required arguments are present and correctly typed before navigation.

5. **No Deep Link Handling Utilities**
   No helpers for:
   - Parsing deep link query parameters
   - Handling optional deep link parameters
   - Converting deep links to navigation routes

---

## 4. Code Quality

### Quality Issues:

1. **Weak Test Coverage in SafeNavigationTest.kt**
   The test only verifies the package name exists:
   ```kotlin
   @Test
   fun `file exists in correct package`() {
       val packageName = this::class.java.packageName
       assertTrue(packageName.startsWith("org.enchant.navigation"))
   }
   ```
   No actual functionality is tested (safeNavigate behavior, error handling, etc.).

2. **Manual Route String Building in Tests**
   `NavRouteTest.kt` manually rebuilds route strings using when-expressions instead of using actual NavRoute methods:
   ```kotlin
   val routeString = when (route) {
       is NavRoute.Conversation -> "conversation/${route.conversationId}"
       else -> route.route
   }
   ```
   This doesn't test how NavHost actually generates routes.

3. **No Generic Route Argument Support**
   NavRoute sealed class cannot carry generic type-safe arguments. Each route variant must define its own fields manually.

4. **Inconsistent Use of Data Object vs Data Class**
   Simple routes use `data object` (correct), but parameterized routes use `data class` with `override val route = ...` which is redundant when the route can be derived from the parameters.

---

## 5. Recommendations

### Priority 1 (Security):
1. URL-encode all path parameters using `Uri.encode()` before constructing route strings
2. Migrate from legacy NavHost to proper Navigation component with argument bundles
3. Implement input validation for all navigation parameters

### Priority 2 (Bug Fixes):
1. Remove unused `conversationId` field from `Search` route or make it meaningful
2. Consolidate route string generation into a single source of truth in NavRoute
3. Add proper type-safe argument classes or use Safe Args plugin

### Priority 3 (Completeness):
1. Add back stack management utilities
2. Add deep link parsing utilities
3. Add argument validation helpers

### Priority 4 (Quality):
1. Expand SafeNavigationTest.kt to cover actual safeNavigate behavior
2. Refactor NavRouteTest.kt to test actual navigation flow
3. Add integration tests for navigation edge cases

---

## Summary Table

| Category | Severity | Issues |
|----------|----------|--------|
| Security | Critical | 2 |
| Security | High | 1 |
| Security | Medium | 1 |
| Security | Low | 1 |
| Bugs | High | 2 |
| Bugs | Medium | 2 |
| Completeness | High | 3 |
| Code Quality | Medium | 4 |

**Overall Risk:** MEDIUM-HIGH
