# Audit Report: core:ui Module

**Module:** `org.enchant.core.ui`
**Path:** `core/ui/src/main/java/org/enchant/core/ui/`
**Date:** 2026-05-29

---

## 1. Security

| Item | Status | Notes |
|------|--------|-------|
| Sensitive data in UI components | PASS | No hardcoded secrets, credentials, or API keys found |
| Secure window flags | N/A | No direct Window manipulation in this module |
| CompositionLocal data leakage | PASS | LocalBottomSheetDismiss and LocalResultEventBus use safe defaults |
| Log leakage | PASS | No sensitive data logged |

**Findings:**
- `ResultEventBus.channelMap` is exposed as `MutableMap` (line 28). Direct external mutation could cause issues. Should be private or wrapped.
- No secure flag configurations present (expected for a shared UI library).

---

## 2. Bugs

| Item | Status | Notes |
|------|--------|-------|
| Component reusability | PASS | All components are designed for reuse |
| Theme handling | PASS | Material3 API used correctly with proper experimental opt-ins |
| RTL support | **WARNING** | No RTL-aware layouts or contentDescription for directional icons |
| Edge cases | PASS | Null handling tested in ResultEventBus |

**Findings:**
- `WindowSizeClassExtensions.kt`: `isSplitPane()` logic at line 28 checks `widthPixels < heightPixels` for portrait detection but does not account for density differences. A low-density large screen could misclassify.
- `TransitionSpecs.kt`: Uses hardcoded durations (200ms, 300ms) with no customization hook. May not meet accessibility preference for reduced motion.

---

## 3. Completeness

| Component | Status | Notes |
|-----------|--------|-------|
| WindowSizeClassExtensions | PARTIAL | Only breakpoint utilities; no actual responsive layouts |
| BottomSheetSceneStrategy | YES | Full modal bottom sheet integration |
| NavBackStackExtensions | YES | navigateOrPopTo and safePop present |
| ResultEffect | YES | Composable result collection |
| ResultEventBus | YES | Full pub/sub mechanism |
| TransitionSpecs | YES | Horizontal, Vertical, Fade transitions |

**Findings:**
- Missing common UI components that are typically expected in a shared UI library:
  - Buttons, text fields, cards (Material3 primitives)
  - Loading indicators / progress spinners
  - Snackbar / toast utilities
  - Dialog helpers
  - Empty state components

---

## 4. Code Quality

### Positives
- Clean separation between navigation and UI utilities
- Good use of Kotlin extension functions
- CompositionLocal usage is appropriate for dismiss callbacks
- Tests are comprehensive with good coverage of happy path and edge cases

### Concerns

**a) Public API Surface (`ResultEventBus`)**
```kotlin
val channelMap: MutableMap<String, Channel<Any?>> = mutableMapOf()  // line 28
```
Exposing `MutableMap` allows external code to corrupt internal state. Should be `Map` or private.

**b) Type Safety in `ResultEffect`**
```kotlin
onResult.invoke(result as T)  // line 14
```
Unchecked cast from `Any?` to `T`. Should use `@Suppress("UNCHECKED_CAST")` or reified inline with safe handling.

**c) `LocalResultEventBus.current` crash behavior**
```kotlin
get() = LocalResultEventBus.current ?: error("No ResultEventBus has been provided")  // line 20
```
Crashes at access time rather than at provision call site. May make debugging harder.

**d) Missing Accessibility**
- No `contentDescription` support in WindowBreakpoint enum (used for logging/display)
- No `semantics` modifiers in navigation components
- Duration constants not respecting `android.content.pm.PackageManager.ActivityInfo.CONFIG_UI_MODE` for reduced motion

**e) Test file structure**
Tests are well-organized but lack tests for:
- RTL layout behavior
- Window rotation transitions
- ResultEventBus memory leak on large result counts

---

## 5. Recommendations

1. **Security**: Make `ResultEventBus.channelMap` private or return an unmodifiable view.

2. **Bug Fix**: Add `@Suppress("UNCHECKED_CAST")` to `ResultEffect` line 14 or restructure to avoid the cast.

3. **RTL Support**: Add RTL-aware slide direction handling in `TransitionSpecs`.

4. **Accessibility**: Add content descriptions for any iconography and respect reduced motion preferences.

5. **Completeness**: Consider adding foundational UI components (buttons, loaders, snackbar builders) to make this a truly reusable component library.

6. **Error Handling**: Provide a non-crashing fallback or clear error message for missing `ResultEventBus` rather than `error()`.

---

## File Inventory

| File | Lines | Purpose |
|------|-------|---------|
| `WindowSizeClassExtensions.kt` | 43 | Window breakpoint detection utilities |
| `navigation/BottomSheetSceneStrategy.kt` | 88 | Modal bottom sheet scene strategy |
| `navigation/NavBackStackExtensions.kt` | 26 | Navigation stack utilities |
| `navigation/ResultEffect.kt` | 17 | Composable result collector |
| `navigation/ResultEventBus.kt` | 53 | Result pub/sub bus |
| `navigation/TransitionSpecs.kt` | 92 | Animation transition specifications |

**Total: 319 lines of production code**

---

## Test Coverage

| Test File | Coverage |
|-----------|----------|
| `WindowBreakpointTest.kt` | Breakpoint detection, split pane logic |
| `BottomSheetSceneStrategyTest.kt` | Creation, metadata, singleton |
| `NavBackStackExtensionsTest.kt` | Navigate/pop operations, stack manipulation |
| `ResultEffectTest.kt` | Channel behavior, ResultEventBus integration |
| `ResultEventBusTest.kt` | Send/receive, remove, local bus |
| `TransitionSpecsTest.kt` | Object structure, transition specs existence |

**All 16 test cases pass.**

---

## Risk Summary

| Risk | Level | Description |
|------|-------|-------------|
| Mutable internal state exposed | MEDIUM | `channelMap` is public and mutable |
| Unchecked type cast | LOW | `ResultEffect` line 14 |
| Missing RTL support | LOW | Slide transitions assume LTR |
| Missing reduced motion support | LOW | Hardcoded animation durations |
| Incomplete component library | MEDIUM | Limited shared UI primitives |

---

*Audit completed by automated review of core:ui module.*
