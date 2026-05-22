# Navigation Rewrite — Complete Implementation Reference

> This document defines every file, every interface, every class, every function, its full signature, and its single responsibility — down to the import level. Agents consuming this document should be able to implement the entire navigation system without referencing any other source.

---

## Table of Contents

1. [File Inventory: What Goes Where](#1-file-inventory-what-goes-where)
2. [Phase 0: Build Dependencies](#2-phase-0-build-dependencies)
3. [Phase 1: Core Infrastructure (core/navigation)](#3-phase-1-core-infrastructure-corenavigation)
   - [P1-01: TransitionSpecs.kt](#p1-01-transitionspecskt)
   - [P1-02: ResultEventBus.kt](#p1-02-resulteventbuskt)
   - [P1-03: ResultEffect.kt](#p1-03-resulteffectkt)
   - [P1-04: BottomSheetSceneStrategy.kt](#p1-04-bottomsheetscenestrategykt)
   - [P1-05: NavBackStackExtensions.kt](#p1-05-navbackstackextensionskt)
4. [Phase 2: Per-Feature NavKey + NavDisplay](#4-phase-2-per-feature-navkey--navdisplay)
   - [P2-01: <Feature>NavKey.kt Pattern](#p2-01-featurenavkeykt-pattern)
   - [P2-02: <Feature>NavDisplay.kt Pattern](#p2-02-featurenavdisplaykt-pattern)
   - [P2-03: <Feature>NavBackStackExtensions.kt Pattern](#p2-03-featurenavbackstackextensionskt-pattern)
5. [Phase 3: ViewModel-Driven Complex Flows](#5-phase-3-viewmodel-driven-complex-flows)
   - [P3-01: RegistrationFlowEvent.kt (Full Flow Events)](#p3-01-registrationfloweventkt-full-flow-events)
   - [P3-02: EmitterExtensions.kt](#p3-02-emitterextensionskt)
   - [P3-03: Full RegistrationNavigation.kt Reference](#p3-03-full-registrationnavigationkt-reference)
   - [P3-04: QuickTransferOldDeviceNavigation.kt (Simpler Flow)](#p3-04-quicktransferolddevicenavigationkt-simpler-flow)
   - [P3-05: RestoreLocalBackupNavKey + NavDisplay Reference](#p3-05-restorelocalbackupnavkey--navdisplay-reference)
6. [Phase 4: Fragment-Based XML Patterns (If Needed)](#6-phase-4-fragment-based-xml-patterns)
7. [Phase 4b: Embedding nav3 Inside a Fragment](#7-phase-4b-embedding-nav3-inside-a-fragment)
8. [Phase M: Main Navigation Shell (Adaptive Three-Pane Layout)](#8-phase-m-main-navigation-shell-adaptive-three-pane-layout)
   - [M1: MainNavigationDetailLocation.kt](#m1-mainnavigationdetaillocationkt)
   - [M2: MainNavigationListLocation.kt](#m2-mainnavigationlistlocationkt)
   - [M3: MainNavigationViewModel.kt](#m3-mainnavigationviewmodelkt)
   - [M4: MainNavigationRouter.kt](#m4-mainnavigationrouterkt)
   - [M5: AppScaffoldNavigator.kt](#m5-appscaffoldnavigatorkt)
   - [M6: AppScaffold.kt](#m6-appscaffoldkt)
   - [M7: MainNavigationBar.kt + MainNavigationRail.kt](#m7-mainnavigationbarkt--mainnavigationrailkt)
   - [M8: MainActivityComponents.kt](#m8-mainactivitycomponentskt)
   - [M9: WindowSizeClassExtensions.kt](#m9-windowsizeclassextensionskt)
9. [Phase 5: Migration of Current Code](#9-phase-5-migration-of-current-code)

---

## 1. File Inventory: What Goes Where

```
app/src/main/java/org/enchant/                        (main adaptive shell)
├── MainNavigationDetailLocation.kt                        M1
├── MainNavigationListLocation.kt                          M2
├── MainNavigationViewModel.kt                             M3
├── MainNavigationRouter.kt                                M4
├── MainNavigationRepository.kt                            M4b
├── window/
│   ├── AppScaffoldNavigator.kt                           M5
│   ├── AppScaffoldAnimationState.kt                      M5a
│   ├── NavigationType.kt                                 M5b
│   ├── AppPaneDragHandle.kt                              M5c
│   └── AppScaffold.kt                                     M6
├── main/
│   ├── MainNavigationBar.kt                              M7
│   ├── MainNavigationRail.kt                             M7
│   ├── MainFloatingActionButtons.kt                      M7b
│   └── MainActivityComponents.kt                         M8
└── util/
    └── WindowSizeClassExtensions.kt                       M9

core/ui/src/main/java/org/enchant/core/ui/             (shared UI + adaptive)
├── WindowSizeClassExtensions.kt                          M9
├── compose/
│   ├── ComposeFragment.kt
│   └── BottomSheets.kt                                   (custom bottom sheet wrapper)
└── navigation/
    ├── TransitionSpecs.kt                                 P1-01
    ├── ResultEventBus.kt                                  P1-02
    ├── ResultEffect.kt                                    P1-03
    ├── BottomSheetSceneStrategy.kt                        P1-04
    └── NavBackStackExtensions.kt                          P1-05

core/navigation/src/main/java/org/enchant/navigation/
└── NavRoute.kt ← DELETE THIS
    NavHost.kt  ← DELETE THIS

feature/<feature-name>/src/main/java/org/enchant/<feature>/
├── <Feature>NavKey.kt                                     P2-01
├── <Feature>NavDisplay.kt                                 P2-02
└── <Feature>NavBackStackExtensions.kt                     P2-03

feature/<feature-name>/src/main/java/org/enchant/<feature>/
├── <Feature>NavKey.kt                                     P2-01
├── <Feature>NavDisplay.kt                                 P2-02
└── <Feature>NavBackStackExtensions.kt                     P2-03
```

---

## 2. Phase 0: Build Dependencies

### File: `gradle/libs.versions.toml` (add these entries)

```toml
[versions]
androidx-navigation3-core = "1.0.0"
androidx-lifecycle-navigation3 = "2.10.0"
androidx-navigation = "2.9.8"

[libraries]
# Navigation 3 (type-safe Compose navigation)
navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "androidx-navigation3-core" }
navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "androidx-navigation3-core" }
# navigation3-event-compose is provided transitively by navigation3-ui.
# Imports: androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "androidx-lifecycle-navigation3" }

# Adaptive navigation (ThreePaneScaffold, NavigableListDetailPaneScaffold)
# Version managed by Compose BOM
androidx-compose-material3-adaptive-navigation = { module = "androidx.compose.material3.adaptive:adaptive-navigation" }

# Navigation 2 (fragment-based — used alongside nav3 for XML graphs and SafeNavigation)
androidx-navigation-fragment-ktx = { module = "androidx.navigation:navigation-fragment-ktx", version.ref = "androidx-navigation" }
androidx-navigation-ui-ktx = { module = "androidx.navigation:navigation-ui-ktx", version.ref = "androidx-navigation" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }
```

### File: `core/ui/build.gradle.kts` (add to dependencies block)

```kotlin
dependencies {
    // Navigation 3 core — provides NavKey, NavBackStack, NavEntry, NavDisplay, SceneStrategy
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    // navigation-event-compose is a transitive dep of navigation3-ui
    
    // Adaptive navigation — provides ThreePaneScaffold, NavigableListDetailPaneScaffold
    api(libs.androidx.compose.material3.adaptive.navigation)
    
    // Lifecycle integration — provides rememberViewModelStoreNavEntryDecorator
    implementation(libs.lifecycleViewmodelNavigation3)
    // existing deps stay
}
```

### File: `app/build.gradle.kts` (add these alongside existing deps)

```kotlin
dependencies {
    // Navigation 3
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    
    // Navigation 2 (coexists with nav3 — for XML graphs, Fragments, SafeNavigation)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)
    // existing deps stay
}
```

### File: `feature/<feature>/build.gradle.kts` (for features using nav3 flows)

```kotlin
dependencies {
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycleViewmodelNavigation3)
    // existing deps stay
}
```

### File: `settings.gradle.kts`

The core/navigation module must already be included. Do not change.

---

## 3. Phase 1: Core Infrastructure

> **Module location:** All Phase 1 files live in `core/ui/src/main/java/org/enchant/core/ui/navigation/` (NOT in `core/navigation`). Signal puts all nav3 infrastructure in `core/ui` because it depends on Compose UI primitives, not just navigation abstractions. The `core/navigation` module contains only the old `NavRoute`/`NavHost` which will be deleted.

---

### P1-01: TransitionSpecs.kt

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/navigation/TransitionSpecs.kt`

**Package:** `org.enchant.core.ui.navigation`

**Single responsibility:** Define reusable `AnimatedContentTransitionScope` lambdas so every `NavDisplay` can share animation specs without duplication. Provides exactly three spec variants per animation type: forward transition, pop transition, and predictive back pop transition.

**Imports required:**

```kotlin
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
```

**Structure and every member:**

```kotlin
object TransitionSpecs {

    object HorizontalSlide {
        private const val DURATION = 200

        // Forward: new screen enters from right, old screen exits to left
        val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            (
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(DURATION))
                + fadeIn(animationSpec = tween(DURATION))
            ) togetherWith (
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(DURATION))
                + fadeOut(animationSpec = tween(DURATION))
            )
        }

        // Pop: new screen enters from left, old screen exits to right
        val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            (
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(DURATION))
                + fadeIn(animationSpec = tween(DURATION))
            ) togetherWith (
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(DURATION))
                + fadeOut(animationSpec = tween(DURATION))
            )
        }

        // Predictive back: same directional animation as pop, but receives swipe edge
        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
            (
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(DURATION))
                + fadeIn(animationSpec = tween(DURATION))
            ) togetherWith (
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(DURATION))
                + fadeOut(animationSpec = tween(DURATION))
            )
        }
    }

    object VerticalSlide {
        private const val DURATION = 300

        // Forward: new screen slides up from bottom, old screen just fades out
        val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(DURATION))
            + fadeIn(animationSpec = tween(DURATION))
            togetherWith
            fadeOut(animationSpec = tween(DURATION))
        }

        // Pop: new screen just fades in, old screen slides down to bottom
        val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            fadeIn(animationSpec = tween(DURATION))
            togetherWith
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(DURATION))
            + fadeOut(animationSpec = tween(DURATION))
        }

        // Predictive back: same as pop
        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
            fadeIn(animationSpec = tween(DURATION))
            togetherWith
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(DURATION))
            + fadeOut(animationSpec = tween(DURATION))
        }
    }
}
```

**Tests to write:**

- `transitionSpec` produces correct direction (slide in from right, old slides left)
- `popTransitionSpec` reverses direction
- `predictivePopTransitionSpec` compiles with `@NavigationEvent.SwipeEdge Int` parameter
- `VerticalSlide.transitionSpec` slides up, does NOT slide old screen horizontally
- Each spec uses correct DURATION: Horizontal = 200, Vertical = 300
- Any agent calling a lambda with `tween(DURATION)` produces the same duration

---

### P1-02: ResultEventBus.kt

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/navigation/ResultEventBus.kt`

**Package:** `org.enchant.core.ui.navigation`

**Single responsibility:** Provide a channel-based event bus that allows screens to pass typed results to other screens without coupling. One screen calls `sendResult()` and another collects via `getResultFlow()`. The `LocalResultEventBus` object makes it available through Compose composition locals.

**Imports required:**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
```

**Structure and every member:**

```kotlin
object LocalResultEventBus {
    private val LocalResultEventBus: ProvidableCompositionLocal<ResultEventBus?> =
        compositionLocalOf { null }

    val current: ResultEventBus
        @Composable
        get() = LocalResultEventBus.current
            ?: error("No ResultEventBus has been provided")

    infix fun provides(bus: ResultEventBus): ProvidedValue<ResultEventBus?> {
        return LocalResultEventBus.provides(bus)
    }
}

class ResultEventBus {
    val channelMap: MutableMap<String, Channel<Any?>> = mutableMapOf()

    inline fun <reified T> getResultFlow(
        resultKey: String = T::class.toString()
    ): Flow<T>? = channelMap[resultKey]?.receiveAsFlow()

    inline fun <reified T> sendResult(
        resultKey: String = T::class.toString(),
        result: T
    ) {
        if (!channelMap.contains(resultKey)) {
            channelMap[resultKey] = Channel(
                capacity = BUFFERED,
                onBufferOverflow = BufferOverflow.SUSPEND
            )
        }
        channelMap[resultKey]?.trySend(result)
    }

    inline fun <reified T> removeResult(
        resultKey: String = T::class.toString()
    ) {
        channelMap.remove(resultKey)
    }
}
```

**Key implementation details:**
- `channelMap` stores `Channel<Any?>` (nullable type) to handle null results
- `sendResult` uses `trySend` (non-suspending) because channel is `BUFFERED`
- `receiveAsFlow()` converts the channel to a cold Flow
- `getResultFlow` returns `Flow<T>?` — returns null if no channel for that key
- `removeResult` cleanly removes the channel, preventing leaks
- `LocalResultEventBus` uses `compositionLocalOf` (not `staticCompositionLocalOf`) for recomposition scope

**Tests to write:**

- `sendResult` followed by `getResultFlow` collects the value
- Multiple results queued before collection are all delivered
- `sendResult` with different keys does not cross-contaminate
- `removeResult` removes the channel so `getResultFlow` returns null
- `getResultFlow` with no prior `sendResult` returns null
- Null results are transmitted correctly
- `LocalResultEventBus.current` throws error if not provided

---

### P1-03: ResultEffect.kt

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/navigation/ResultEffect.kt`

**Package:** `org.enchant.core.ui.navigation`

**Single responsibility:** A `@Composable` function that safely collects results from a `ResultEventBus` inside a `LaunchedEffect`, scoped to the result key and channel identity. Auto-restarts if the channel is replaced.

**Imports required:**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
```

**Structure:**

```kotlin
@Composable
inline fun <reified T> ResultEffect(
    resultEventBus: ResultEventBus = LocalResultEventBus.current,
    resultKey: String = T::class.toString(),
    crossinline onResult: suspend (T) -> Unit
) {
    LaunchedEffect(resultKey, resultEventBus.channelMap[resultKey]) {
        resultEventBus.getResultFlow<T>(resultKey)?.collect { result ->
            onResult.invoke(result as T)
        }
    }
}
```

**Key implementation details:**
- `inline` + `reified T` enables `T::class` at runtime for default key
- `LaunchedEffect` keys on both `resultKey` AND `resultEventBus.channelMap[resultKey]` — if the channel reference changes (replaced by sendResult), the effect cancels and restarts
- `crossinline onResult` since it's used inside a lambda
- `result as T` is an unchecked cast (safe in practice because same `T` is used to produce the channel)
- If `getResultFlow` returns null (no channel yet), the effect silently does nothing

**Tests to write:**

- `ResultEffect` collects values emitted by `sendResult`
- Multiple emissions are all collected
- Effect restarts when `channelMap[key]` reference changes
- Effect does nothing if no channel exists for the key
- Null values in the flow are handled

---

### P1-04: BottomSheetSceneStrategy.kt

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/navigation/BottomSheetSceneStrategy.kt`

**Package:** `org.enchant.core.ui.navigation`

**Single responsibility:** A `SceneStrategy` that detects entries marked with "bottomsheet" metadata and wraps their content in a `ModalBottomSheet`. Provides `LocalBottomSheetDismiss` so sheet content can dismiss itself with the hide animation before popping the back stack.

**Imports required:**

```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.launch
```

**Structure and every member:**

```kotlin
val LocalBottomSheetDismiss = staticCompositionLocalOf<() -> Unit> { {} }

@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()

        val animatedDismiss: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    onBack()
                }
            }
        }

        CompositionLocalProvider(LocalBottomSheetDismiss provides animatedDismiss) {
            ModalBottomSheet(
                onDismissRequest = onBack,
                sheetState = sheetState,
                properties = modalBottomSheetProperties
            ) {
                entry.Content()
            }
        }
    }
}

/**
 * NOTE: This implementation uses the custom `BottomSheets.BottomSheet` composable wrapper
 * (defined in `core/ui/.../compose/BottomSheets.kt`) instead of `ModalBottomSheet` directly.
 * This wrapper applies themed shared styling (colors, shapes, typography) consistently
 * across all bottom sheets in the app. If your app does not have this wrapper,
 * replace `BottomSheets.BottomSheet` with `ModalBottomSheet` and update the import.
 */
private val BOTTOM_SHEET_TAG = "BottomSheetScene"

@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>
    ): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val bottomSheetProperties =
            lastEntry?.metadata?.get(BOTTOM_SHEET_KEY) as? ModalBottomSheetProperties
        return bottomSheetProperties?.let { properties ->
            @Suppress("UNCHECKED_CAST")
            BottomSheetScene(
                key = lastEntry.contentKey as T,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                entry = lastEntry,
                modalBottomSheetProperties = properties,
                onBack = onBack
            )
        }
    }

    companion object {
        fun bottomSheet(
            modalBottomSheetProperties: ModalBottomSheetProperties =
                ModalBottomSheetProperties()
        ): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to modalBottomSheetProperties)

        internal const val BOTTOM_SHEET_KEY = "bottomsheet"
    }
}
```

**Key implementation details:**
- `BottomSheetScene` implements `OverlayScene<T>` which has `previousEntries` (screens behind) and `overlaidEntries` (what the sheet covers)
- `sceneStrategy` must be added **before** any non-overlay scene strategies
- `animatedDismiss` launches `sheetState.hide()` (async animation) and only calls `onBack()` after animation completes and sheet is not visible
- `skipPartiallyExpanded = true` in `rememberModalBottomSheetState`
- `ModalBottomSheetProperties` is passed through from the metadata map
- `calculateScene` returns null (no bottom sheet) if the last entry has no bottomSheet metadata
- Companion `bottomSheet()` function creates the metadata map for entry declarations

**Tests to write:**

- `calculateScene` returns `BottomSheetScene` when metadata contains bottomSheet key
- `calculateScene` returns null when no metadata
- `calculateScene` returns null when entries list is empty
- `calculateScene` correctly passes `previousEntries` and `overlaidEntries` to scene
- `LocalBottomSheetDismiss` default value (no-op) does not crash
- `animatedDismiss` calls `onBack` after hide completes

---

### P1-05: NavBackStackExtensions.kt

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/navigation/NavBackStackExtensions.kt`

**Package:** `org.enchant.core.ui.navigation`

**Single responsibility:** Provide reusable extension functions on `NavBackStack<NavKey>` for common navigation patterns: "pop-to-or-navigate", safe pop, and removal by predicate. These are not tied to any specific feature.

**Imports required:**

```kotlin
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
```

**Structure:**

```kotlin
fun NavBackStack<NavKey>.navigateOrPopTo(key: NavKey) {
    if (contains(key)) {
        popTo(key)
    } else {
        add(key)
    }
}

fun NavBackStack<NavKey>.safePop(): NavKey? {
    return if (isNotEmpty()) {
        val last = get(size - 1)
        removeAt(size - 1)
        last
    } else null
}

private fun NavBackStack<NavKey>.popTo(key: NavKey) {
    while (size > 1 && get(size - 1) != key) {
        removeAt(size - 1)
    }
}
```

**Tests to write:**

- `navigateOrPopTo` adds key if not in stack
- `navigateOrPopTo` pops to key and does not duplicate if already in stack
- `safePop` returns the removed key
- `safePop` does nothing and returns null if stack is empty
- `popTo` removes entries above target but keeps target and everything below
- `popTo` does nothing if no matching key found (or pops everything if key is at index 0)

---

### P1-06: SafeNavigation.kt

**Full path:** `core/navigation/src/main/java/org/enchant/navigation/SafeNavigation.kt`

**Package:** `org.enchant.navigation`

**Single responsibility:** Provide `safeNavigate()` extension functions on `NavController` that verify the `currentDestination` has the requested action before navigating. Prevents crashes from illegal navigation (e.g., navigating to a destination not in the current graph).

**Imports required:**

```kotlin
import android.content.res.Resources
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import org.enchant.core.dependencies.AppDependencies
```

**Structure:**

```kotlin
@file:JvmName("SafeNavigation")

package org.enchant.navigation

fun NavController.safeNavigate(@IdRes resId: Int) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId)
    } else {
        Log.w(TAG, "No action $resId for $currentDestination")
    }
}

fun NavController.safeNavigate(@IdRes resId: Int, arguments: Bundle?) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId, arguments)
    } else {
        Log.w(TAG, "No action $resId for $currentDestination")
    }
}

fun NavController.safeNavigate(directions: NavDirections) {
    if (currentDestination?.getAction(directions.actionId) != null) {
        navigate(directions)
    } else {
        Log.w(TAG, "No ${getDisplayName(directions.actionId)} for $currentDestination")
    }
}

fun NavController.safeNavigate(directions: NavDirections, navOptions: NavOptions?) {
    if (currentDestination?.getAction(directions.actionId) != null) {
        navigate(directions, navOptions)
    } else {
        Log.w(
            TAG,
            "No ${getDisplayName(directions.actionId)} for $currentDestination"
        )
    }
}

private fun getDisplayName(id: Int): String? {
    return if (id <= 0x00FFFFFF) {
        id.toString()
    } else {
        try {
            AppDependencies.application.resources.getResourceName(id)
        } catch (e: Resources.NotFoundException) {
            id.toString()
        }
    }
}

private const val TAG = "SafeNavigation"
```

**Key implementation details:**
- `@file:JvmName("SafeNavigation")` makes these accessible from Java as `SafeNavigation.safeNavigate(controller, ...)`.
- Every overload checks `currentDestination?.getAction(resId)` before navigating — if the action doesn't exist in the current graph, it logs a warning instead of crashing.
- `getDisplayName()` converts the integer resource ID to a human-readable name for logging, falling back to the raw ID if the resource can't be found.
- Covers both resource-ID-based navigation and `NavDirections`-based navigation.

**Tests to write:**

- `safeNavigate(resId)` calls `navigate(resId)` when action exists
- `safeNavigate(resId)` logs warning when action does not exist
- `safeNavigate(directions)` calls `navigate(directions)` when action exists
- `safeNavigate(directions)` logs warning when action does not exist
- `getDisplayName` returns resource name for valid resource IDs
- `getDisplayName` returns raw ID string for non-resource IDs

---

## 4. Phase 2: Per-Feature NavKey + NavDisplay

These three files form a pattern. Every feature that needs navigation creates all three.

---

### P2-01: <Feature>NavKey.kt Pattern

**Full path:** `feature/<feature>/src/main/java/org/enchant/<feature>/<Feature>NavKey.kt`

**Single responsibility:** Define all navigation destinations for this feature as a `@Serializable sealed interface` extending `NavKey`. Each destination is either a `data object` (no arguments) or a `data class` (typed arguments).

**Imports required:**

```kotlin
import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.Serializable
import org.enchant.core.model.AccountEntropyPool
import org.enchant.registration.util.AccountEntropyPoolParceler
import org.enchant.registration.util.AccountEntropyPoolSerializer
```

**Structure:**

```kotlin
sealed interface RegistrationFlowEvent : DebugLoggable {
    data class NavigateToScreen(val route: RegistrationNavKey) : RegistrationFlowEvent

    data object NavigateBack : RegistrationFlowEvent

    data object ResetState : RegistrationFlowEvent

    data class SessionUpdated(val session: SessionMetadata) : RegistrationFlowEvent

    data class E164Chosen(val e164: String) : RegistrationFlowEvent

    data class Registered(val accountEntropyPool: AccountEntropyPool) : RegistrationFlowEvent

    data class MasterKeyRestoredFromSvr(val masterKey: MasterKey) : RegistrationFlowEvent

    data object RecoveryPasswordInvalid : RegistrationFlowEvent

    data class PendingRestoreOptionSelected(val option: PendingRestoreOption?) : RegistrationFlowEvent

    data class UserSuppliedAepSubmitted(val aep: AccountEntropyPool) : RegistrationFlowEvent

    data class UserSuppliedAepVerified(val aep: AccountEntropyPool) : RegistrationFlowEvent

    data object RegistrationComplete : RegistrationFlowEvent
}
```

**Key details:**
- `sealed interface` — allows domain-specific events alongside navigation events
- **`DebugLoggable`** — the entire event hierarchy implements this interface for structured logging. Define `DebugLoggable` as a simple interface with a `debugDescription: String` property (each `data class`/`data object` auto-generates `toString`). Signal uses this for event logging without leaking PII.
- `ResetState` is for irrecoverable error recovery
- `SessionUpdated` + `E164Chosen` are non-navigation events that still flow through the same event channel
- ViewModel exposes a single `onEvent(RegistrationFlowEvent)` entry point

---

### P3-01b: RegistrationFlowState.kt

**Full path:** `feature/registration/src/main/java/org/enchant/registration/RegistrationFlowState.kt`

**Package:** `org.enchant.registration`

**Single responsibility:** Define the complete state for the registration flow, including the nav3 back stack, session metadata, and all intermediate registration data. The ViewModel exposes this as `StateFlow<RegistrationFlowState>`.

**Structure:**

```kotlin
data class RegistrationFlowState(
    val isRestoringNavigationState: Boolean = true,
    val backStack: NavBackStack<NavKey> = NavBackStack(
        initialStack = listOf(RegistrationNavKey.Welcome)
    ),
    val sessionMetadata: NetworkController.SessionMetadata? = null,
    val sessionE164: String? = null,
    val accountEntropyPool: AccountEntropyPool? = null,
    val temporaryMasterKey: MasterKey? = null,
    val doNotAttemptRecoveryPassword: Boolean = false,
    val pendingRestoreOption: PendingRestoreOption? = null,
    val unverifiedRestoredAep: AccountEntropyPool? = null,
    val finishRequests: SharedFlow<Unit> = MutableSharedFlow()
)
```

**How the ViewModel uses it:**

Signal's `RegistrationViewModel` extends `EventDrivenViewModel<RegistrationFlowEvent>`, a base class that:
1. Holds a `StateFlow<RegistrationFlowState>` internally
2. Exposes `fun onEvent(event: RegistrationFlowEvent)` as the single entry point
3. Routes events through `suspend fun processEvent(event: RegistrationFlowEvent)` which delegates to `suspend fun applyEvent(state: RegistrationFlowState, event: RegistrationFlowEvent): RegistrationFlowState`
4. Persists state (backStack, session data) via `RegistrationRepository.saveFlowState()` on each mutation

```kotlin
class RegistrationViewModel(
    private val repository: RegistrationRepository,
    savedStateHandle: SavedStateHandle
) : EventDrivenViewModel<RegistrationFlowEvent>(TAG) {

    val state: StateFlow<RegistrationFlowState> = _state.asStateFlow()
    val finishRequests: SharedFlow<Unit>
    val resultBus: ResultEventBus = ResultEventBus()

    override suspend fun processEvent(event: RegistrationFlowEvent) {
        val newState = applyEvent(_state.value, event)
        _state.value = newState
        persistFlowState(event)
    }

    suspend fun applyEvent(
        state: RegistrationFlowState,
        event: RegistrationFlowEvent
    ): RegistrationFlowState = when (event) {
        is RegistrationFlowEvent.ResetState -> RegistrationFlowState(isRestoringNavigationState = false)
        is RegistrationFlowEvent.NavigateToScreen -> applyNavigationToScreenEvent(state, event)
        is RegistrationFlowEvent.NavigateBack -> {
            val newBackStack = state.backStack.toMutableList().apply {
                if (size > 1) removeAt(size - 1)
            }
            state.copy(backStack = NavBackStack(newBackStack))
        }
        is RegistrationFlowEvent.SessionUpdated -> state.copy(sessionMetadata = event.session)
        is RegistrationFlowEvent.E164Chosen -> state.copy(sessionE164 = event.e164)
        is RegistrationFlowEvent.Registered -> state.copy(accountEntropyPool = event.accountEntropyPool)
        is RegistrationFlowEvent.MasterKeyRestoredFromSvr -> state.copy(temporaryMasterKey = event.masterKey)
        is RegistrationFlowEvent.RegistrationComplete -> {
            _finishRequests.tryEmit(Unit)
            applyNavigationToScreenEvent(state, RegistrationFlowEvent.NavigateToScreen(RegistrationNavKey.FullyComplete))
        }
        else -> state
    }
}
```

**Key implementation details:**
- `isRestoringNavigationState` starts as `true` — the `RegistrationNavHost` checks this and shows a loading spinner until `false`, preventing the NavDisplay from flashing before the saved back stack is restored.
- The ViewModel persists the flow state after each event via `RegistrationRepository.saveFlowState()`. On process death, the repository restores the saved `RegistrationFlowState`, and the ViewModel sets `isRestoringNavigationState = false` once the restore is complete.
- `finishRequests` is a `SharedFlow<Unit>` that the Activity collects to call `onBackPressed()` when the flow is fully complete.
- `resultBus: ResultEventBus` is shared across all child screens via `CompositionLocalProvider` (see `RegistrationNavHost`).

**Tests to write:**
- `ResetState` clears all state and sets `isRestoringNavigationState = false`
- `NavigateToScreen` appends to the back stack
- `NavigateBack` pops the back stack (no-op when only 1 entry)
- `SessionUpdated` sets session metadata
- `E164Chosen` sets the e164 value
- `RegistrationComplete` emits on `finishRequests`
- State persisted via repository survives process death

---

### P3-02: EmitterExtensions.kt

**Full path:** `feature/registration/src/main/java/org/enchant/registration/screens/util/EmitterExtensions.kt`

**Package:** `org.enchant.registration.screens.util`

**Single responsibility:** Convenience extension functions on `(RegistrationFlowEvent) -> Unit` lambdas so screen-level code can write `parentEventEmitter.navigateTo(route)` instead of `parentEventEmitter(RegistrationFlowEvent.NavigateToScreen(route))`.

**Imports:**

```kotlin
import org.enchant.registration.RegistrationFlowEvent
import org.enchant.registration.RegistrationNavKey
```

**Structure:**

```kotlin
fun ((RegistrationFlowEvent) -> Unit).navigateTo(route: RegistrationNavKey) {
    this(RegistrationFlowEvent.NavigateToScreen(route))
}

fun ((RegistrationFlowEvent) -> Unit).navigateBack() {
    this(RegistrationFlowEvent.NavigateBack)
}
```

**Tests to write:**

- `navigateTo(route)` emits `NavigateToScreen(route)`
- `navigateBack()` emits `NavigateBack`

---

### P3-03: Full RegistrationNavigation.kt Reference

**Full path:** `feature/registration/src/main/java/org/enchant/registration/RegistrationNavigation.kt`

**Package:** `org.enchant.registration`

**Single responsibility:** Define ALL routes for the registration flow AND provide the `@Composable RegistrationNavHost` that renders the entire flow using Nav3. This file is the largest navigation file — it defines ~20 routes and ~20 screen entries.

**PART A — Route Definitions (lines 1-200 equivalent):**

Imports:

```kotlin
import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.Serializable
import org.enchant.core.model.AccountEntropyPool
import org.enchant.registration.util.AccountEntropyPoolParceler
import org.enchant.registration.util.AccountEntropyPoolSerializer
```

```kotlin
@Serializable
@Parcelize
sealed interface RegistrationNavKey : NavKey, Parcelable {

    @Serializable data object Welcome : RegistrationNavKey

    @Serializable data class Permissions(
        val nextRoute: RegistrationNavKey
    ) : RegistrationNavKey

    @Serializable data object PhoneNumberEntry : RegistrationNavKey

    @Serializable data class CountryCodePicker(
        val country: CountryData? = null
    ) : RegistrationNavKey

    @Serializable data object VerificationCodeEntry : RegistrationNavKey

    @Serializable data class Captcha(
        val session: SessionMetadata
    ) : RegistrationNavKey

    @Serializable data object PinEntryForSvrRestore : RegistrationNavKey

    @Serializable data class PinEntryForRegistrationLock(
        val timeRemaining: Long,
        val svrCredentials: SvrCredentials
    ) : RegistrationNavKey

    @Serializable data class PinEntryForSmsBypass(
        val svrCredentials: SvrCredentials
    ) : RegistrationNavKey

    @Serializable data class AccountLocked(
        val timeRemainingMs: Long
    ) : RegistrationNavKey

    @Serializable data object PinCreate : RegistrationNavKey

    @Serializable data class ArchiveRestoreSelection(
        val restoreOptions: List<ArchiveRestoreOption>,
        val isPreRegistration: Boolean
    ) : RegistrationNavKey {
        companion object {
            fun forQuickRestore(hasRemoteBackup: Boolean): ArchiveRestoreSelection
            fun forManualRestore(): ArchiveRestoreSelection
            fun forPostRegister(): ArchiveRestoreSelection
        }
    }

    @Serializable data class LocalBackupRestore(
        val isPreRegistration: Boolean
    ) : RegistrationNavKey

    @Serializable data object EnterLocalBackupV1Passphrase : RegistrationNavKey

    @Serializable data object EnterAepForLocalBackup : RegistrationNavKey

    @Serializable data class EnterAepForRemoteBackupPreRegistration(
        val e164: String
    ) : RegistrationNavKey

    @Serializable data object EnterAepForRemoteBackupPostRegistration : RegistrationNavKey
    @Serializable
    @TypeParceler<AccountEntropyPool, AccountEntropyPoolParceler>
    data class RemoteRestore(
        @Serializable(with = AccountEntropyPoolSerializer::class)
        val aep: AccountEntropyPool
    ) : RegistrationNavKey

    @Serializable
    data object QuickRestoreQrScan : RegistrationNavKey
    @Serializable data object Transfer : RegistrationNavKey

    @Serializable data object Profile : RegistrationNavKey

    @Serializable data object FullyComplete : RegistrationNavKey
}
```

**Result keys (private constants):**

```kotlin
private const val CAPTCHA_RESULT = "captcha_token"
private const val COUNTRY_CODE_RESULT = "country_code_result"
private const val BACKUP_CREDENTIAL_RESULT = "backup_credential_result"
private const val LOCAL_BACKUP_RESTORE_RESULT = "local_backup_restore_result"
```

**PART B — RegistrationNavHost composable:**

Imports (additional):

```kotlin
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import org.enchant.core.ui.navigation.LocalResultEventBus
import org.enchant.core.ui.navigation.ResultEffect
import org.enchant.core.ui.navigation.TransitionSpecs
// feature screen imports
```

```kotlin
@Composable
fun RegistrationNavHost(
    registrationRepository: RegistrationRepository,
    registrationViewModel: RegistrationViewModel? = null,
    permissionsState: MultiplePermissionsState? = null,
    modifier: Modifier = Modifier,
    onRegistrationComplete: () -> Unit = {}
) {
    val viewModel: RegistrationViewModel = registrationViewModel ?: viewModel(
        factory = RegistrationViewModel.Factory(registrationRepository)
    )

    val registrationState by viewModel.state.collectAsStateWithLifecycle()

    // If restoring saved state, show loading indicator
    if (registrationState.isRestoringNavigationState) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Wire finish requests to Activity back press
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(viewModel, backDispatcher) {
        viewModel.finishRequests.collect {
            backDispatcher?.onBackPressed()
        }
    }

    // Provide ResultEventBus + NavigationEventDispatcherOwner to all child entries
    CompositionLocalProvider(
        LocalResultEventBus provides viewModel.resultBus,
        LocalNavigationEventDispatcherOwner provides
            rememberNavigationEventDispatcherOwner(parent = null)
    ) {
        val entryProvider = entryProvider {
            navigationEntries(
                registrationRepository = registrationRepository,
                registrationViewModel = viewModel,
                permissionsState = permissionsState,
                onRegistrationComplete = onRegistrationComplete
            )
        }

        val decorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator()
        )

        val entries = rememberDecoratedNavEntries(
            backStack = registrationState.backStack,
            entryDecorators = decorators,
            entryProvider = entryProvider
        )

        NavDisplay(
            entries = entries,
            onBack = { viewModel.onEvent(RegistrationFlowEvent.NavigateBack) },
            modifier = modifier,
            transitionSpec = {
                if (targetState.key is RegistrationNavKey.CountryCodePicker) {
                    TransitionSpecs.VerticalSlide.transitionSpec(this)
                } else {
                    TransitionSpecs.HorizontalSlide.transitionSpec(this)
                }
            },
            popTransitionSpec = {
                when {
                    initialState.key is RegistrationNavKey.CountryCodePicker ->
                        TransitionSpecs.VerticalSlide.popTransitionSpec(this)
                    else ->
                        TransitionSpecs.HorizontalSlide.popTransitionSpec(this)
                }
            },
            predictivePopTransitionSpec = {
                if (initialState.key is RegistrationNavKey.CountryCodePicker) {
                    TransitionSpecs.VerticalSlide.predictivePopTransitionSpec(this, it)
                } else {
                    TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec(this, it)
                }
            }
        )
    }
}
```

**PART C — navigationEntries function:**

```kotlin
private fun EntryProviderScope<NavKey>.navigationEntries(
    registrationRepository: RegistrationRepository,
    registrationViewModel: RegistrationViewModel,
    permissionsState: MultiplePermissionsState?,
    onRegistrationComplete: () -> Unit
) {
    val parentEventEmitter: (RegistrationFlowEvent) -> Unit =
        registrationViewModel::onEvent

    entry<RegistrationNavKey.Welcome> {
        val context = LocalContext.current
        WelcomeScreen(
            onEvent = { event ->
                when (event) {
                    WelcomeScreenEvents.Continue ->
                        parentEventEmitter.navigateTo(
                            RegistrationNavKey.Permissions(
                                nextRoute = RegistrationNavKey.PhoneNumberEntry
                            )
                        )
                    // ... other Welcome events
                }
            }
        )
    }

    entry<RegistrationNavKey.Permissions> { key ->
        PermissionsScreen(
            onProceed = { parentEventEmitter.navigateTo(key.nextRoute) }
        )
    }

    entry<RegistrationNavKey.PhoneNumberEntry> {
        val vm: PhoneNumberEntryViewModel = viewModel(
            factory = PhoneNumberEntryViewModel.Factory(...)
        )
        val state by vm.state.collectAsStateWithLifecycle()

        // Receive results from child screens
        ResultEffect<String?>(registrationViewModel.resultBus, CAPTCHA_RESULT) { token ->
            if (token != null) vm.onCaptchaCompleted(token)
        }
        ResultEffect<CountryData?>(registrationViewModel.resultBus, COUNTRY_CODE_RESULT) { country ->
            if (country != null) vm.onCountrySelected(country)
        }

        PhoneNumberScreen(state = state, onEvent = vm::onEvent)
    }

    entry<RegistrationNavKey.CountryCodePicker> { key ->
        val vm: CountryCodePickerViewModel = viewModel(...)
        val state by vm.state.collectAsStateWithLifecycle()
        CountryCodePickerScreen(state = state, onEvent = vm::onEvent)
    }

    entry<RegistrationNavKey.Captcha> {
        CaptchaScreen(
            onEvent = { event ->
                when (event) {
                    is CaptchaCompleted -> {
                        registrationViewModel.resultBus.sendResult(CAPTCHA_RESULT, event.token)
                        parentEventEmitter.navigateBack()
                    }
                    CaptchaCancel -> parentEventEmitter.navigateBack()
                }
            }
        )
    }

    // ... (repeat pattern for every screen)

    entry<RegistrationNavKey.FullyComplete> {
        LaunchedEffect(Unit) {
            onRegistrationComplete()
        }
    }
}
```

**Key details:**
- `isRestoringNavigationState` guard: if ViewModel is restoring from saved state, show spinner instead of NavDisplay
- `finishRequests` SharedFlow: used for scenarios like "registration completed, close this Activity"
- Both decorators: `SaveableStateHolder` + `ViewModelStore` in that order
- `LocalResultEventBus` wraps all entries via `CompositionLocalProvider`
- transitionSpec varies by route: CountryCodePicker uses vertical slide, others use horizontal
- `parentEventEmitter.navigateTo()` is an extension from EmitterExtensions

---

### P3-04: QuickTransferOldDeviceNavigation.kt (Simpler Flow)

**Full path:** `app/src/main/java/org/enchant/registration/olddevice/QuickTransferNavDisplay.kt`

> **Note:** In Signal, this lives directly in `app/src/main/java/org/thoughtcrime/securesms/registration/olddevice/` rather than in the `feature/registration` module. This is because the old device transfer flow has deep coupling with app-level dependencies (SMS interception, Bluetooth, WiFi Direct). If your architecture allows, prefer putting it in `feature/registration`; otherwise place it in `app/.../registration/olddevice/`.

**Package:** `org.enchant.registration.olddevice`

**Single responsibility:** A simpler ViewModel-driven flow with only 3 screens. Shows the minimal pattern.

```kotlin
@Parcelize
sealed interface TransferNavKey : NavKey, Parcelable {
    @Serializable data object Transfer : TransferNavKey
    @Serializable data object PrepareDevice : TransferNavKey
    @Serializable data object Done : TransferNavKey
}

@Composable
fun TransferNavDisplay(
    viewModel: TransferViewModel,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val backStack by viewModel.backStack.collectAsStateWithLifecycle()

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LaunchedEffect(viewModel, backDispatcher) {
        viewModel.finishRequests.collect {
            backDispatcher?.onBackPressed()
        }
    }

    // Simpler: only SaveableStateHolder decorator (no ViewModelStore)
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    )

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = decorators,
        entryProvider = entryProvider {
            entry<TransferNavKey.Transfer> {
                val state by viewModel.state.collectAsStateWithLifecycle()
                TransferScreen(state = state, onEvent = viewModel::onEvent)
            }
            entry<TransferNavKey.PrepareDevice> {
                val state by viewModel.state.collectAsStateWithLifecycle()
                PrepareDeviceScreen(state = state, onEvent = viewModel::onEvent)
            }
            entry<TransferNavKey.Done> {
                LaunchedEffect(Unit) { onFinished() }
            }
        }
    )

    NavDisplay(
        entries = entries,
        onBack = { viewModel.goBack() },
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec
    )
}
```

---

### P3-05: RestoreLocalBackupNavKey + NavDisplay Reference

**Full path (route def):** `feature/restore/src/main/java/org/enchant/restore/RestoreNavKey.kt`

**Full path (nav host):** `feature/restore/src/main/java/org/enchant/restore/RestoreNavDisplay.kt`

**Single responsibility:** Demonstrates how to mix regular screens with bottom sheet overlays in a single flow.

**Route definition:**

```kotlin
sealed interface RestoreNavKey : NavKey {
    @Serializable data object SelectRestoreType : RestoreNavKey
    @Serializable data object FolderInstructionSheet : RestoreNavKey
    @Serializable data object FileInstructionSheet : RestoreNavKey
    @Serializable data object SelectBackup : RestoreNavKey
    @Serializable data object SelectBackupSheet : RestoreNavKey
    @Serializable data object EnterBackupKey : RestoreNavKey
    @Serializable data object NoRecoveryKeySheet : RestoreNavKey
}
```

**NavDisplay:**

```kotlin
@Composable
fun RestoreNavDisplay(
    state: RestoreState,
    callback: RestoreCallback,
    isRegistrationInProgress: Boolean,
    enterBackupKeyState: EnterBackupKeyState,
    backupKey: String
) {
    val backStack = rememberNavBackStack(RestoreNavKey.SelectRestoreType)
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides
            rememberNavigationEventDispatcherOwner(parent = null)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                sceneStrategy = bottomSheetStrategy,
            entryProvider = entryProvider {

                // Regular screen entry
                entry<RestoreNavKey.SelectRestoreType> {
                    SelectRestoreTypeScreen(
                        onSelectFolder = { backStack.add(RestoreNavKey.FolderInstructionSheet) },
                        onSelectFile = { backStack.add(RestoreNavKey.FileInstructionSheet) }
                    )
                }

                // Bottom sheet entry — annotated with metadata
                entry<RestoreNavKey.FolderInstructionSheet>(
                    metadata = BottomSheetSceneStrategy.bottomSheet()
                ) {
                    FolderInstructionSheetContent(
                        onContinue = { /* launch folder picker */ }
                    )
                }

                entry<RestoreNavKey.FileInstructionSheet>(
                    metadata = BottomSheetSceneStrategy.bottomSheet()
                ) {
                    FileInstructionSheetContent(
                        onContinue = { /* launch file picker */ }
                    )
                }

                entry<RestoreNavKey.SelectBackup> {
                    SelectBackupScreen(
                        onRestore = { backStack.add(RestoreNavKey.EnterBackupKey) },
                        onSelectDifferent = { backStack.add(RestoreNavKey.SelectBackupSheet) }
                    )
                }

                entry<RestoreNavKey.SelectBackupSheet>(
                    metadata = BottomSheetSceneStrategy.bottomSheet()
                ) {
                    val dismissSheet = LocalBottomSheetDismiss.current
                    SelectBackupSheetContent(
                        onBackupSelected = {
                            callback.selectBackup(it)
                            dismissSheet()
                        }
                    )
                }

                entry<RestoreNavKey.EnterBackupKey> {
                    EnterBackupKeyScreen(
                        onNoKey = { backStack.add(RestoreNavKey.NoRecoveryKeySheet) }
                    )
                }

                entry<RestoreNavKey.NoRecoveryKeySheet>(
                    metadata = BottomSheetSceneStrategy.bottomSheet()
                ) {
                    val dismissSheet = LocalBottomSheetDismiss.current
                    NoRecoveryKeySheetContent(
                        onSkip = {
                            dismissSheet()
                            callback.displaySkipRestoreWarning()
                        }
                    )
                }
            }
        )

        // Dialogs rendered outside the back stack
        if (state.dialog != null) {
            RestoreDialog(
                dialog = state.dialog,
                onConfirm = { callback.onDialogConfirmed(state.dialog) },
                onDismiss = callback::clearDialog
            )
        }

        if (state.isLoadingBackupDirectory) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }  // Box
    }  // CompositionLocalProvider
}  // RestoreNavDisplay
```
```

---

## 6. Phase 4: Fragment-Based XML Graphs + SafeNavigation (Nav2 Coexistence)

If any screens still use Fragments alongside nav3, create XML nav graphs in `app/src/main/res/navigation/`. The app uses both Navigation 2 (XML fragment-based) and Navigation 3 (Compose-based) side by side — Nav2 for legacy screens, nav3 for new/rewritten screens.

**Single responsibility:** Define fragment destinations, typed arguments, and navigation actions with animations. The `SafeNavigation` utility prevents crashes by checking `currentDestination.getAction()` before navigating.

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/feature_flow"
    app:startDestination="@id/firstFragment">

    <fragment
        android:id="@+id/firstFragment"
        android:name="org.enchant.feature.FirstFragment"
        android:label="first_screen">

        <action
            android:id="@+id/go_to_secondFragment"
            app:destination="@id/secondFragment"
            app:enterAnim="@anim/nav_default_enter_anim"
            app:exitAnim="@anim/nav_default_exit_anim"
            app:popEnterAnim="@anim/nav_default_pop_enter_anim"
            app:popExitAnim="@anim/nav_default_pop_exit_anim" />

    </fragment>

    <fragment
        android:id="@+id/secondFragment"
        android:name="org.enchant.feature.SecondFragment">

        <argument
            android:name="itemId"
            app:argType="string" />

        <action
            android:id="@+id/go_to_firstFragment"
            app:destination="@id/firstFragment"
            app:popUpTo="@id/firstFragment"
            app:popUpToInclusive="true" />

    </fragment>

    <include app:graph="@navigation/sub_graph" />
</navigation>
```

**SafeNavigation for XML:**

> **File location:** `app/src/main/java/org/enchant/util/navigation/SafeNavigation.kt`
> **Package:** `org.enchant.util.navigation`
> 
> This file lives in the `app` module (not `core/ui`) because it wraps the old `Nav2` `NavController` for fragment-based XML navigation. Nav3 does not need it — nav3's type-safe `NavKey` system eliminates the need for `getAction()` checks.

```kotlin
@file:JvmName("SafeNavigation")

package org.enchant.util.navigation

import android.content.res.Resources
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions

fun NavController.safeNavigate(@IdRes resId: Int) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId)
    } else {
        Log.w("SafeNavigation", "No action $resId for $currentDestination")
    }
}

fun NavController.safeNavigate(@IdRes resId: Int, arguments: Bundle?) {
    if (currentDestination?.getAction(resId) != null) {
        navigate(resId, arguments)
    } else {
        Log.w("SafeNavigation", "No action $resId for $currentDestination")
    }
}

fun NavController.safeNavigate(directions: NavDirections) {
    if (currentDestination?.getAction(directions.actionId) != null) {
        navigate(directions)
    } else {
        Log.w("SafeNavigation", "No ${getDisplayName(directions.actionId)} for $currentDestination")
    }
}

fun NavController.safeNavigate(directions: NavDirections, navOptions: NavOptions?) {
    if (currentDestination?.getAction(directions.actionId) != null) {
        navigate(directions, navOptions)
    } else {
        Log.w("SafeNavigation", "No ${getDisplayName(directions.actionId)} for $currentDestination")
    }
}

private fun getDisplayName(id: Int): String? {
    return if (id <= 0x00FFFFFF) {
        id.toString()
    } else {
        try {
            AppDependencies.application.resources.getResourceName(id)
        } catch (e: Resources.NotFoundException) {
            id.toString()
        }
    }
}
```

```

---

## 8. Phase M: Main Navigation Shell (Adaptive Three-Pane Layout)

> **Design decision: Per-tab detail state preservation.** Unlike the reference app (which locks to secondary pane on tab switch, discarding conversation state), enchant-native preserves each tab's detail navigation independently. When you switch from Chats (in a conversation) to Calls, the conversation state is preserved. Switching back to Chats returns you to that conversation. This is a fundamental architectural difference — the reference optimizes for simplicity; enchant-native optimizes for user experience.

This phase defines the top-level adaptive shell that wraps all feature navigation. It replaces the flat `MainActivity` NavHost with a hierarchical structure: **list tabs (Chats/Archive/Calls/Stories)** at the top level, with **per-tab detail NavHostControllers** embedded in the primary pane of a `ThreePaneScaffold`.

**Architecture overview:**

```
MainActivity
├── MainNavigationViewModel  (state holder, per-tab detail stacks)
├── AppScaffoldNavigator<Any>  (wraps ThreePaneScaffoldNavigator)
├── AppScaffold
│   ├── SinglePaneAppScaffold (phone / API < 33) — AnimatedContent list/detail
│   └── AdaptiveAppScaffold (tablet) — NavigableListDetailPaneScaffold
│       ├── secondaryPane = ListAndNavigation + NavHost (list pane)
│       └── primaryPane = Tab's detail NavHostController (detail pane)
└── Per-tab NavHostControllers (chats, calls, stories, archive)
```

**Key files and their relationships:**

1. `MainNavigationListLocation` (M2) — enum of 4 tabs
2. `MainNavigationDetailLocation` (M1) — sealed interface of all possible detail destinations
3. `MainNavigationViewModel` (M3) — holds per-tab detail stacks, list focus, pane state
4. `MainNavigationRouter` (M4) — interface for `goToList()`, `goToDetail()`, `setFocusedPane()`
5. `AppScaffoldNavigator` (M5) — wraps `ThreePaneScaffoldNavigator` with animation coordination
6. `AppScaffold` (M6) — `SinglePaneAppScaffold` + `AdaptiveAppScaffold` branches
7. `MainNavigationBar` / `MainNavigationRail` (M7) — bottom nav bar and side rail
8. `MainActivityComponents` (M8) — per-tab `NavHostController` setup
9. `WindowSizeClassExtensions` (M9) — split pane detection

---

### M1: MainNavigationDetailLocation.kt

**Full path:** `app/src/main/java/org/enchant/MainNavigationDetailLocation.kt`

**Package:** `org.enchant`

**Single responsibility:** Define all possible destinations that can appear in the detail (primary) pane — conversations, call links, message details, settings, etc. This is a `@Serializable @Parcelize sealed interface` — the canonical type for all detail-level navigation.

**Imports required:**

```kotlin
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
```

**Structure:**

```kotlin
@Serializable
@Parcelize
sealed interface MainNavigationDetailLocation : Parcelable {

    @Serializable
    data object Empty : MainNavigationDetailLocation

    @Serializable
    data class Conversation(
        val conversationArgs: ConversationArgs
    ) : MainNavigationDetailLocation

    @Serializable
    data class CallLinkDetails(
        val callLinkRoomId: CallLinkRoomId
    ) : MainNavigationDetailLocation

    @Parcelize
    sealed interface Chats : MainNavigationDetailLocation {
        @Serializable
        data class MessageDetails(
            val recipientId: RecipientId,
            val messageId: MessageId
        ) : Chats

        @Serializable
        data class ConversationSettings(
            val recipientId: RecipientId
        ) : Chats
    }

    @Parcelize
    sealed interface Calls : MainNavigationDetailLocation {
        @Parcelize
        sealed class CallLinks : Calls {
            @Serializable
            data class EditCallLinkName(
                val callLinkRoomId: CallLinkRoomId
            ) : CallLinks()
        }
    }

    @Parcelize
    sealed class Stories : MainNavigationDetailLocation

    @Serializable
    data object Settings : MainNavigationDetailLocation
}
```

**Key implementation details:**
- `isContentRoot` property (extension) returns `true` for `Empty`, `Settings` — these are destinations that should clear the feature NavHost back stack when navigated to
- Each sub-destination is a `sealed interface` inside the parent, allowing hierarchical `when` exhaustion in navigation logic
- `ConversationArgs` must be `@Serializable` and `@Parcelize`
- `CallLinkRoomId`, `RecipientId`, `MessageId` must all be `@Parcelize`

**Tests to write:**

- Every location serializes and deserializes correctly
- `isContentRoot` returns true for `Empty` and `Settings`
- `isContentRoot` returns false for `Conversation`, `Chats.*`, `Calls.*`
- `data class` locations with same args compare equal

---

### M2: MainNavigationListLocation.kt

**Full path:** `app/src/main/java/org/enchant/MainNavigationListLocation.kt`

**Package:** `org.enchant`

**Single responsibility:** Define the 4 primary navigation tabs at the list level: CHATS, ARCHIVE, CALLS, STORIES. Each has a label resource, icon resource, and a flag indicating whether it has a detail NavHost (all do except STORIES in initial implementation).

**Imports required:**

```kotlin
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import org.enchant.R
```

**Structure:**

```kotlin
enum class MainNavigationListLocation(
    @StringRes val label: Int,
    @RawRes val icon: Int,
    val hasDetailNavHost: Boolean = true
) {
    CHATS(R.string.ConversationListTabs__chats, R.raw.chats_28),
    ARCHIVE(R.string.ConversationListTabs__archive, R.raw.archive_28),
    CALLS(R.string.ConversationListTabs__calls, R.raw.calls_28),
    STORIES(R.string.ConversationListTabs__stories, R.raw.stories_28, hasDetailNavHost = false),
    ;

    companion object {
        val DEFAULT = CHATS
    }
}
```

**Tests to write:**

- All 4 tabs have non-zero label and icon resources
- `hasDetailNavHost` is `true` for all except STORIES
- `DEFAULT` equals `CHATS`

---

### M3: MainNavigationViewModel.kt

**Full path:** `app/src/main/java/org/enchant/MainNavigationViewModel.kt`

**Package:** `org.enchant`

**Single responsibility:** Central state holder for all main navigation. Manages:
1. **Per-tab detail stacks** — each tab independently preserves its detail navigation state
2. **Active tab** — which tab is currently focused
3. **Pane focus with lock** — which pane (Secondary/Primary) has focus, plus `lockPaneToSecondary` guard that prevents accidental re-entry to the detail pane after back navigation or tab switch
4. **Full-screen mode** — whether a tab is in full-screen (no split) mode
5. **Megaphone state** — onboarding tips
6. **Early navigation** — queued navigation requests that fire once the `AppScaffoldNavigator` is wired up
7. **SavedStateHandle persistence** — `lockPaneToSecondary` flag survives process death

**Imports required:**

```kotlin
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.enchant.core.util.logging.Log
import org.enchant.main.MainNavigationRepository
import javax.inject.Inject
```

**Structure:**

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class MainNavigationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MainNavigationRepository = MainNavigationRepository
) : ViewModel(), MainNavigationRouter {

    companion object {
        private const val LOCK_PANE_TO_SECONDARY = "lock_pane_to_secondary"
        private val TAG = Log.tag(MainNavigationViewModel::class)
    }

    private val _mainNavigationState = MutableStateFlow(
        MainNavigationState(
            currentListLocation = MainNavigationListLocation.DEFAULT,
            chatsCount = 0,
            callsCount = 0,
            storiesCount = 0,
            storyFailure = false,
            isStoriesFeatureEnabled = true
        )
    )
    val mainNavigationState: StateFlow<MainNavigationState> = _mainNavigationState.asStateFlow()

    private val _chatsDetailStack = MutableStateFlow<List<MainNavigationDetailLocation>>(
        listOf(MainNavigationDetailLocation.Empty)
    )
    val chatsDetailStack: StateFlow<List<MainNavigationDetailLocation>> = _chatsDetailStack.asStateFlow()

    private val _archiveDetailStack = MutableStateFlow<List<MainNavigationDetailLocation>>(
        listOf(MainNavigationDetailLocation.Empty)
    )
    val archiveDetailStack: StateFlow<List<MainNavigationDetailLocation>> = _archiveDetailStack.asStateFlow()

    private val _callsDetailStack = MutableStateFlow<List<MainNavigationDetailLocation>>(
        listOf(MainNavigationDetailLocation.Empty)
    )
    val callsDetailStack: StateFlow<List<MainNavigationDetailLocation>> = _callsDetailStack.asStateFlow()

    private val _storiesDetailStack = MutableStateFlow<List<MainNavigationDetailLocation>>(
        listOf(MainNavigationDetailLocation.Empty)
    )
    val storiesDetailStack: StateFlow<List<MainNavigationDetailLocation>> = _storiesDetailStack.asStateFlow()

    private val _internalDetailLocation = MutableSharedFlow<MainNavigationDetailLocation>()
    val detailLocation: SharedFlow<MainNavigationDetailLocation> = _internalDetailLocation

    private val _paneFocusRequests = MutableSharedFlow<ThreePaneScaffoldRole?>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val paneFocusRequests: SharedFlow<ThreePaneScaffoldRole?> = _paneFocusRequests.asSharedFlow()

    private val _fullScreenPane = MutableStateFlow(false)
    val fullScreenPane: StateFlow<Boolean> = _fullScreenPane.asStateFlow()
    private val _isSplitPane = MutableStateFlow(true)

    val isSplitPane: StateFlow<Boolean> = _isSplitPane.asStateFlow()

    private val _activeChatThreadId = MutableStateFlow(-1L)
    val activeChatThreadId: StateFlow<Long> = _activeChatThreadId.asStateFlow()

    private val _activeCallId = MutableStateFlow<CallLogRow.Id?>(null)
    val activeCallId: StateFlow<CallLogRow.Id?> = _activeCallId.asStateFlow()

    val snackbarRegistry = SnackbarStateConsumerRegistry()

    /**
     * Guard that prevents the PRIMARY (detail) pane from being focused until
     * the user explicitly requests detail content via [goToDetail].
     * Set to `true` on back navigation (via [Nav] wrapper) and tab switch.
     * Set to `false` by [goToDetail].
     * Persisted across process death via [SavedStateHandle].
     */
    private var lockPaneToSecondary: Boolean by savedStateHandle.delegate(
        LOCK_PANE_TO_SECONDARY, true
    )

    private var navigator: AppScaffoldNavigator<Any>? = null
    private var navigatorScope: CoroutineScope? = null

    private val snackbarRegistry = SnackbarStateConsumerRegistry()

    init {
        // Collect unread counts from the repository and update navigation state
        performStoreUpdate(repository.getNumberOfUnreadMessages()) { unreadChats, state ->
            state.copy(chatsCount = unreadChats.toInt())
        }
        performStoreUpdate(repository.getNumberOfUnseenCalls()) { unseenCalls, state ->
            state.copy(callsCount = unseenCalls.toInt())
        }
        performStoreUpdate(repository.getNumberOfUnseenStories()) { unseenStories, state ->
            state.copy(storiesCount = unseenStories.toInt())
        }
        performStoreUpdate(repository.getHasFailedOutgoingStories()) { hasFailed, state ->
            state.copy(storyFailure = hasFailed)
        }

        viewModelScope.launch {
            _internalDetailLocation.collect { location ->
                updateActiveStateForLocation(location)
            }
        }
    }

    private var earlyNavigationListLocationRequested: MainNavigationListLocation? = null
    var earlyNavigationDetailLocationRequested: MainNavigationDetailLocation? = null
        private set

    private var earlyFocusedPaneRequested: ThreePaneScaffoldRole? = null

    fun clearEarlyDetailLocation() {
        earlyNavigationDetailLocationRequested = null
    }

    /**
     * Wraps the raw [ThreePaneScaffoldNavigator] in our [AppScaffoldNavigator]
     * and hooks into back-navigation to toggle [lockPaneToSecondary].
     * Must be called once during Activity composition.
     */
    fun wrapNavigator(
        composeScope: CoroutineScope,
        threePaneScaffoldNavigator: ThreePaneScaffoldNavigator<Any>
    ): AppScaffoldNavigator<Any> {
        this.navigatorScope = composeScope
        this.navigator = Nav(threePaneScaffoldNavigator)

        earlyNavigationListLocationRequested?.let {
            goTo(it)
        }
        earlyNavigationListLocationRequested = null

        earlyFocusedPaneRequested?.let {
            setFocusedPane(it)
        }
        earlyFocusedPaneRequested = null

        earlyNavigationDetailLocationRequested?.let { detail ->
            lockPaneToSecondary = false
            updateDetailLocation(detail)
        }

        return this.navigator!!
    }

    override fun goTo(location: MainNavigationListLocation) {
        lockPaneToSecondary = true
        if (navigator == null) {
            earlyNavigationListLocationRequested = location
            return
        }
        _mainNavigationState.update { it.copy(currentListLocation = location) }
        _paneFocusRequests.tryEmit(ThreePaneScaffoldRole.Secondary)
        _isSplitPane.value = true
    }

    override fun goToDetail(location: MainNavigationDetailLocation) {
        lockPaneToSecondary = false
        val currentTab = _mainNavigationState.value.currentListLocation
        val targetStack = getDetailStackForTab(currentTab)

        if (location.isContentRoot) {
            setDetailStackForTab(currentTab, listOf(location))
        } else {
            val existingIndex = targetStack.indexOfFirst {
                it::class == location::class && it != MainNavigationDetailLocation.Empty
            }
            if (existingIndex >= 0) {
                setDetailStackForTab(currentTab, targetStack.take(existingIndex + 1))
            } else {
                setDetailStackForTab(currentTab, targetStack + location)
            }
        }

        if (_mainNavigationState.value.currentListLocation != currentTab) {
            _mainNavigationState.update { it.copy(currentListLocation = currentTab) }
        }

        if (navigator == null) {
            earlyNavigationDetailLocationRequested = location
            return
        }
        updateDetailLocation(location)
    }

    private fun updateDetailLocation(location: MainNavigationDetailLocation) {
        viewModelScope.launch {
            _internalDetailLocation.emit(location)
        }
    }

    override fun setFocusedPane(role: ThreePaneScaffoldRole) {
        val effectiveRole = if (lockPaneToSecondary) {
            ThreePaneScaffoldRole.Secondary
        } else {
            role
        }
        if (navigator == null) {
            earlyFocusedPaneRequested = effectiveRole
            return
        }
        navigatorScope?.launch {
            navigator?.navigateTo(effectiveRole)
        }
        viewModelScope.launch {
            _paneFocusRequests.emit(effectiveRole)
        }
    }

    fun setFullScreenPane(fullScreen: Boolean) {
        _fullScreenPane.value = fullScreen
    }

    fun setSplitPane(isSplit: Boolean) {
        _isSplitPane.value = isSplit
    }

    fun navigateInCurrentTab(detailLocation: MainNavigationDetailLocation) {
        val currentTab = _mainNavigationState.value.currentListLocation
        val stack = getDetailStackForTab(currentTab).toMutableList()

        val existingIndex = stack.indexOfFirst { it::class == detailLocation::class }
        if (existingIndex >= 0) {
            stack.subList(existingIndex + 1, stack.size).clear()
        } else {
            stack.add(detailLocation)
        }

        setDetailStackForTab(currentTab, stack)
    }

    fun goBackInCurrentTab(): Boolean {
        val currentTab = _mainNavigationState.value.currentListLocation
        val stack = getDetailStackForTab(currentTab).toMutableList()

        if (stack.size <= 1) return false

        stack.removeAt(stack.size - 1)
        setDetailStackForTab(currentTab, stack)
        return true
    }

    private fun getDetailStackForTab(tab: MainNavigationListLocation): List<MainNavigationDetailLocation> {
        return when (tab) {
            MainNavigationListLocation.CHATS -> _chatsDetailStack.value
            MainNavigationListLocation.ARCHIVE -> _archiveDetailStack.value
            MainNavigationListLocation.CALLS -> _callsDetailStack.value
            MainNavigationListLocation.STORIES -> _storiesDetailStack.value
        }
    }

    private fun setDetailStackForTab(tab: MainNavigationListLocation, stack: List<MainNavigationDetailLocation>) {
        when (tab) {
            MainNavigationListLocation.CHATS -> _chatsDetailStack.value = stack
            MainNavigationListLocation.ARCHIVE -> _archiveDetailStack.value = stack
            MainNavigationListLocation.CALLS -> _callsDetailStack.value = stack
            MainNavigationListLocation.STORIES -> _storiesDetailStack.value = stack
        }
    }

    /**
     * Wraps [AppScaffoldNavigator] to intercept [navigateBack] and [seekBack]
     * so that [lockPaneToSecondary] is always re-enabled when the user
     * navigates away from the detail pane.
     */
    private inner class Nav<T>(delegate: ThreePaneScaffoldNavigator<T>) : AppScaffoldNavigator<T>(delegate) {
        override suspend fun navigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean {
            val result = super.navigateBack(backNavigationBehavior)
            if (result) {
                lockPaneToSecondary = true
            }
            return result
        }

        override suspend fun seekBack(backNavigationBehavior: BackNavigationBehavior, fraction: Float) {
            super.seekBack(backNavigationBehavior, fraction)
            if (fraction == 0f) {
                lockPaneToSecondary = true
            }
        }
    }

    private fun <T : Any> performStoreUpdate(
        flow: Flow<T>,
        fn: (T, MainNavigationState) -> MainNavigationState
    ) {
        viewModelScope.launch {
            flow.collectLatest { item ->
                _mainNavigationState.update { state -> fn(item, state) }
            }
        }
    }

    private fun updateActiveStateForLocation(location: MainNavigationDetailLocation) {
        when (location) {
            is MainNavigationDetailLocation.Conversation -> {
                _activeChatThreadId.update { location.controllerKey }
            }
            is MainNavigationDetailLocation.CallLinkDetails -> {
                _activeCallId.update { location.controllerKey }
            }
            else -> Unit
        }
    }

    data class MainNavigationState(
        val currentListLocation: MainNavigationListLocation = MainNavigationListLocation.CHATS,
        val chatsCount: Int = 0,
        val callsCount: Int = 0,
        val storiesCount: Int = 0,
        val storyFailure: Boolean = false,
        val isStoriesFeatureEnabled: Boolean = true,
        val compact: Boolean = false
    )
}
```

**Key implementation details:**
- **`lockPaneToSecondary`** — critical UX guard persisted via `SavedStateHandle`. Prevents accidental re-entry to the detail pane after back navigation or tab switch. Only `goToDetail()` clears it. The `Nav` inner class sets it on every `navigateBack`/`seekBack` completion.
- **`wrapNavigator()`** — must be called exactly once from `MainActivity` composition. Wraps the raw `ThreePaneScaffoldNavigator` in our `AppScaffoldNavigator` and replays any early navigation requests queued before the navigator was available.
- **`earlyNavigation*Requested`** — stores navigation commands issued before `wrapNavigator()` is called (e.g., from deep link handling in Activity.onCreate before setContent runs).
- **Per-tab detail stacks** — each of the 4 tabs has its own `MutableStateFlow<List<MainNavigationDetailLocation>>`. When switching tabs, the current stack is preserved and restored when switching back.
- `goToDetail` navigates within the current tab's stack. If the destination is a content root (Empty, Settings), the stack is reset. Otherwise, the destination replaces everything above it (if the same screen type exists) or is appended.
- `navigateInCurrentTab` and `goBackInCurrentTab` are called by the per-tab `NavHostController` when it navigates or pops.
- `paneFocusRequests` is a `SharedFlow` that AppScaffold consumes to animate pane focus changes.

**Tests to write:**
- `lockPaneToSecondary` is `true` after ViewModel init (default)
- `goToDetail(Conversation)` sets `lockPaneToSecondary = false`
- `goTo(CHATS)` sets `lockPaneToSecondary = true`
- `Nav.navigateBack()` re-sets `lockPaneToSecondary = true`
- `goTo(CHATS)` updates `currentListLocation` to CHATS and focuses Secondary pane
- `goToDetail(Conversation)` adds to the current tab's detail stack
- Switching tabs preserves the previous tab's detail stack
- `goToDetail(Empty)` clears the current tab's detail stack
- `goBackInCurrentTab` removes the last entry and returns true; returns false when stack has only Empty
- `navigateInCurrentTab` replaces same-type destinations and appends new ones
- All 4 detail stacks initialize to `[Empty]`
- `wrapNavigator()` replays early navigation requests
- `earlyNavigationDetailLocationRequested` is set when navigator is null

---

### M4: MainNavigationRouter.kt

**Full path:** `app/src/main/java/org/enchant/MainNavigationRouter.kt`

**Package:** `org.enchant`

**Single responsibility:** Hierarchical router interfaces that `MainNavigationViewModel` implements and the UI layer consumes. Provides type-safe sub-routers for per-tab detail navigation (chats, calls) and a top-level router for list/detail/focus operations.

**Imports required:**

```kotlin
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
```

**Structure:**

```kotlin
/**
 * Sub-router for navigation within the chats detail pane.
 * Provides type-safe access to [MainNavigationDetailLocation.Chats] destinations.
 */
interface MainNavigationChatDetailRouter {
    fun exitDetailLocation()
    fun goToChatDetail(location: MainNavigationDetailLocation.Chats)
}

/**
 * Sub-router for navigation within the calls detail pane.
 * Provides type-safe access to [MainNavigationDetailLocation.Calls] destinations.
 */
interface MainNavigationCallDetailRouter {
    fun exitDetailLocation()
    fun goToCallDetail(location: MainNavigationDetailLocation.Calls)
}

/**
 * Top-level router. Combined interface so that [MainNavigationViewModel]
 * implements all navigation in one class.
 */
interface MainNavigationRouter :
    MainNavigationChatDetailRouter,
    MainNavigationCallDetailRouter {

    fun goTo(location: MainNavigationListLocation)
    fun goTo(location: MainNavigationDetailLocation)

    override fun goToChatDetail(location: MainNavigationDetailLocation.Chats) = goTo(location)
    override fun goToCallDetail(location: MainNavigationDetailLocation.Calls) = goTo(location)
    override fun exitDetailLocation() = goTo(MainNavigationDetailLocation.Empty)
}
```

**Usage in MainActivityComponents.kt:**

```kotlin
@Composable
fun MainActivityComponents(
    mainNavigationViewModel: MainNavigationViewModel,
    modifier: Modifier = Modifier
) {
    // Use as top-level router:
    val router: MainNavigationRouter = mainNavigationViewModel
    router.goTo(MainNavigationListLocation.CHATS)
    router.goTo(MainNavigationDetailLocation.Conversation(args))

    // Or use the specific sub-router for type safety:
    val chatRouter: MainNavigationChatDetailRouter = mainNavigationViewModel
    chatRouter.goToChatDetail(MainNavigationDetailLocation.Chats.MessageDetails(rid, mid))
}
```

**Key implementation details:**
- Two sub-router interfaces (`MainNavigationChatDetailRouter`, `MainNavigationCallDetailRouter`) provide type-safe navigation for per-tab detail locations. This prevents accidentally passing a `Calls` destination to a chats context.
- `MainNavigationRouter` inherits both and adds list/detail/focus methods.
- Default implementations in the combined interface delegate `goToChatDetail`/`goToCallDetail` to `goTo(location)`.
- `exitDetailLocation()` provides a consistent way to navigate back to Empty from any detail context.
- The sub-router pattern is critical for Java consumers (e.g., legacy Fragment-based screens) that need typed navigation callbacks without knowing about the full router.

**Tests to write:**

- `MainNavigationViewModel` implements `MainNavigationRouter`
- `MainNavigationViewModel` implements both sub-routers
- `goToChatDetail(Chats.MessageDetails)` calls `goToDetail` with the correct location
- `goToCallDetail(Calls.EditCallLinkName)` calls `goToDetail` with the correct location
- `exitDetailLocation()` emits `MainNavigationDetailLocation.Empty`
- Calling `goTo(CHATS)` twice does not duplicate or cause issues
- Calling `goToDetail(location)` with `isContentRoot = true` resets the stack

---

### M4b: MainNavigationRepository.kt

**Full path:** `app/src/main/java/org/enchant/main/MainNavigationRepository.kt`

**Package:** `org.enchant.main`

**Single responsibility:** Provide reactive data sources for unread counts shown on navigation tabs (chats, calls, stories). This is a stateless repository object (injectable for testing) that exposes `Flow<Long>` observables backed by the database layer.

**Imports required:**

```kotlin
import kotlinx.coroutines.flow.Flow
import org.enchant.database.SignalDatabase
import org.enchant.recipients.Recipient
```

**Structure:**

```kotlin
object MainNavigationRepository {

    fun getNumberOfUnreadMessages(): Flow<Long> {
        return RxDatabaseObserver.conversationList
            .map { SignalDatabase.threads.getUnreadMessageCount() }
            .asFlow()
    }

    fun getNumberOfUnseenCalls(): Flow<Long> {
        return RxDatabaseObserver.conversationList
            .map { SignalDatabase.calls.getUnreadMissedCallCount() }
            .asFlow()
    }

    fun getNumberOfUnseenStories(): Flow<Long> {
        return RxDatabaseObserver.conversationList
            .map {
                SignalDatabase.messages
                    .getUnreadStoryThreadRecipientIds()
                    .map { Recipient.resolved(it) }
                    .filterNot { it.shouldHideStory }
                    .size
                    .toLong()
            }
            .asFlow()
    }

    fun getHasFailedOutgoingStories(): Flow<Boolean> {
        return RxDatabaseObserver.conversationList
            .map { SignalDatabase.messages.hasFailedOutgoingStory() }
            .asFlow()
    }
}
```

**Key implementation details:**
- All flows are derived from the same `RxDatabaseObserver.conversationList` observable, ensuring that a single database change triggers all count recalculations atomically.
- `RxDatabaseObserver.conversationList` is a reactive observable from the Signal database layer — adapt this to your database observer pattern (Room Flow, ContentObserver, etc.).
- `MainNavigationViewModel` collects all 4 flows in `init` and updates `MainNavigationState` accordingly (see M3: `performStoreUpdate`).
- The repository is intentionally stateless — it holds no cached counts. Caching happens in the ViewModel's StateFlow.

**Tests to write:**
- `getNumberOfUnreadMessages` returns the unread count from the database
- `getNumberOfUnseenCalls` returns the missed call count
- `getNumberOfUnseenStories` returns the count of unseen stories
- `getHasFailedOutgoingStories` returns true when there are failed stories
- All flows emit updates when the conversation list changes

---

### M5: AppScaffoldNavigator.kt

**Full path:** `app/src/main/java/org/enchant/window/AppScaffoldNavigator.kt`

**Package:** `org.enchant`

**Single responsibility:** Wrap Material3's `ThreePaneScaffoldNavigator` with animation state coordination and custom back handling. Designed to be subclassed (e.g., by [Nav] in M3) to intercept back/seek for `lockPaneToSecondary` management. The `state: NavigationState` property drives AnimatedContent transitions in the UI layer.

**Imports required:**

```kotlin
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.RememberInComposition
import org.enchant.core.ui.util.LocalResources
import org.enchant.core.ui.util.currentWindowAdaptiveInfo
import org.enchant.core.ui.util.WindowSizeClass
import org.enchant.core.ui.util.rememberIsSplitPane
```

**Note:** `@RememberInComposition` is from `androidx.lifecycle.compose` — it annotates a constructor so the instance is remembered across recompositions without the caller writing `remember { ... }`.

```kotlin
enum class NavigationState {
    ENTER,
    EXIT,
    SEEK,
    RELEASE
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Stable
open class AppScaffoldNavigator<T> @RememberInComposition constructor(
    private val delegate: ThreePaneScaffoldNavigator<T>
) : ThreePaneScaffoldNavigator<T> by delegate {

    var state: NavigationState by mutableStateOf(NavigationState.ENTER)
        private set

    override suspend fun navigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean {
        val result = delegate.navigateBack(backNavigationBehavior)
        if (result) {
            state = NavigationState.EXIT
        }
        return result
    }

    override suspend fun seekBack(backNavigationBehavior: BackNavigationBehavior, fraction: Float) {
        delegate.seekBack(backNavigationBehavior, fraction)
        state = when {
            fraction == 0f -> NavigationState.ENTER
            fraction < 0.5f -> NavigationState.SEEK
            else -> NavigationState.RELEASE
        }
    }

    fun navigateTo(pane: ThreePaneScaffoldRole, contentKey: T? = null) {
        state = NavigationState.ENTER
        delegate.navigateTo(pane, contentKey)
    }
}

@Composable
fun rememberAppScaffoldNavigator(
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    isSplitPane: Boolean = LocalResources.current.rememberIsSplitPane(),
    horizontalPartitionSpacerSize: Dp = windowSizeClass.horizontalPartitionDefaultSpacerSize,
    defaultPanePreferredWidth: Dp = windowSizeClass.listPaneDefaultPreferredWidth
): AppScaffoldNavigator<Any> {
    val delegate = rememberThreePaneScaffoldNavigatorDelegate(
        isSplitPane = isSplitPane,
        horizontalPartitionSpacerSize = horizontalPartitionSpacerSize,
        defaultPanePreferredWidth = defaultPanePreferredWidth
    )
    return remember { AppScaffoldNavigator(delegate) }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun rememberThreePaneScaffoldNavigatorDelegate(
    isSplitPane: Boolean,
    horizontalPartitionSpacerSize: Dp,
    defaultPanePreferredWidth: Dp
): ThreePaneScaffoldNavigator<Any> {
    return rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).copy(
            maxHorizontalPartitions = if (isSplitPane) 2 else 1,
            horizontalPartitionSpacerSize = horizontalPartitionSpacerSize,
            defaultPanePreferredWidth = defaultPanePreferredWidth
        )
    )
}
```

**Key implementation details:**
- Delegates all `ThreePaneScaffoldNavigator` operations to the internal `delegate` while adding `state` tracking
- **Open for subclassing** — `MainNavigationViewModel.Nav` extends this to intercept `navigateBack`/`seekBack` for `lockPaneToSecondary` management
- `state` drives `AnimatedContent` transitions in `AppScaffold` (ENTER/EXIT/SEEK/RELEASE)
- `navigateTo(pane, contentKey)` sets state to ENTER before delegating
- `seekBack` maps fraction values to NavigationState: 0→ENTER, 0-0.5→SEEK, 0.5+→RELEASE
- `rememberAppScaffoldNavigator` is the public factory
- `contentKey` is nullable and defaults to `null` — it's valid to just request pane focus without providing a key

**Tests to write:**

- `navigateTo` sets state to ENTER before delegating
- `navigateBack` that returns true sets state to EXIT
- `seekBack` with fraction=0 sets state to ENTER
- `seekBack` with fraction=0.3 sets state to SEEK
- `seekBack` with fraction=0.7 sets state to RELEASE
- `navigateTo` with `contentKey = null` does not crash
- Subclass overrides `navigateBack` and calls super

---

### M5a: AppScaffoldAnimationState.kt

**Full path:** `app/src/main/java/org/enchant/window/AppScaffoldAnimationState.kt`

**Package:** `org.enchant.window`

**Single responsibility:** Provide per-pane animated visual properties (alpha, offsetX, offsetY, scale, corner radius) driven by `NavigationState` transitions. Each pane (list, detail) gets its own `AppScaffoldAnimationState` instance, enabling independent enter/exit/seek/release animations that integrate with the predictive back gesture.

**Imports required:**

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
```

**Structure:**

```kotlin
object AppScaffoldAnimationDefaults {
    private const val ENTER_DURATION = 350
    private const val EXIT_DURATION = 300
    private const val SEEK_DURATION = 200

    fun tween(duration: Int = 350): TweenSpec<Float> = tween(duration)

    fun spring(damping: Float = Spring.DampingRatioMediumBouncy): SpringSpec<Float> = spring(damping)
}

@Stable
class AppScaffoldAnimationState {

    var alpha: Animatable<Float, AnimationVector1D> = Animatable(1f)
    var offsetX: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var offsetY: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var scale: Animatable<Float, AnimationVector1D> = Animatable(1f)
    var cornerRadius: Animatable<Float, AnimationVector1D> = Animatable(0f)

    suspend fun animateToEnterState() {
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f, AppScaffoldAnimationDefaults.tween(ENTER_DURATION)) }
            launch { offsetX.animateTo(0f, AppScaffoldAnimationDefaults.tween(ENTER_DURATION)) }
            launch { offsetY.animateTo(0f, AppScaffoldAnimationDefaults.tween(ENTER_DURATION)) }
            launch { scale.animateTo(1f, AppScaffoldAnimationDefaults.spring()) }
            launch { cornerRadius.animateTo(0f, AppScaffoldAnimationDefaults.tween(ENTER_DURATION)) }
        }
    }

    suspend fun animateToExitState(
        exitOffsetX: Float = 0f,
        exitOffsetY: Float = 0f,
        exitScale: Float = 1f
    ) {
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(0f, AppScaffoldAnimationDefaults.tween(EXIT_DURATION)) }
            launch { offsetX.animateTo(exitOffsetX, AppScaffoldAnimationDefaults.tween(EXIT_DURATION)) }
            launch { offsetY.animateTo(exitOffsetY, AppScaffoldAnimationDefaults.tween(EXIT_DURATION)) }
            launch { scale.animateTo(exitScale, AppScaffoldAnimationDefaults.spring()) }
            launch { cornerRadius.animateTo(0f, AppScaffoldAnimationDefaults.tween(EXIT_DURATION)) }
        }
    }

    suspend fun animateToSeekState(fraction: Float) {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        launch {
            alpha.animateTo(1f - clampedFraction * 0.3f, AppScaffoldAnimationDefaults.tween(SEEK_DURATION))
            offsetX.animateTo(clampedFraction * -50f, AppScaffoldAnimationDefaults.tween(SEEK_DURATION))
            offsetY.animateTo(clampedFraction * 20f, AppScaffoldAnimationDefaults.tween(SEEK_DURATION))
            scale.animateTo(1f - clampedFraction * 0.05f, AppScaffoldAnimationDefaults.spring())
            cornerRadius.animateTo(clampedFraction * 16f, AppScaffoldAnimationDefaults.tween(SEEK_DURATION))
        }
    }

    suspend fun animateToReleaseState() {
        animateToExitState(exitOffsetX = -100f, exitScale = 0.95f)
    }

    fun applyParentValues(): DrawTransform = {
        // Called from drawWithContent — applies corner radius clipping and background
        clipPath(
            path = CornerRadius(cornerRadius.value, cornerRadius.value).toPath(size)
        )
    }

    fun applyChildValues(): GraphicsLayerScope.() -> Unit = {
        this.alpha = alpha.value
        this.translationX = offsetX.value
        this.translationY = offsetY.value
        this.scaleX = scale.value
        this.scaleY = scale.value
    }
}

@Composable
fun rememberAppScaffoldAnimationState(): AppScaffoldAnimationState {
    return remember { AppScaffoldAnimationState() }
}
```

**Key implementation details:**
- Each `AppScaffoldAnimationState` owns 5 `Animatable` properties — they can be driven independently for different animation curves
- `animateToSeekState(fraction)` is the predictive back path: fraction 0 = fully visible, fraction 1 = nearly gone
- `applyParentValues()` is used in `drawWithContent` for corner radius clip
- `applyChildValues()` is used in `graphicsLayer` for transform properties
- Duration constants: ENTER=350ms, EXIT=300ms, SEEK=200ms
- Scale uses `spring()` for a bouncy feel on resize, all others use `tween()`
- `rememberAppScaffoldAnimationState()` is the composable factory (wraps remember)

**Tests to write:**

- `animateToEnterState` sets alpha=1, offsetX=0, offsetY=0, scale=1, cornerRadius=0
- `animateToExitState` with defaults sets alpha=0
- `animateToSeekState(0)` sets alpha=1, offsetX=0
- `animateToSeekState(1)` sets alpha=0.7, offsetX=-50, scale=0.95, cornerRadius=16
- `animateToReleaseState` sets alpha=0, offsetX=-100, scale=0.95
- Fraction is clamped to [0, 1]
- `applyChildValues` returns correct `GraphicsLayerScope` lambda

---

### M5b: NavigationType.kt

**Full path:** `app/src/main/java/org/enchant/window/NavigationType.kt`

**Package:** `org.enchant.window`

**Single responsibility:** Choose between bottom navigation bar (phone) and side navigation rail (tablet/foldable) based on window breakpoint and aspect ratio. Provides `NavigationType.rememberNavigationType()` as a composable utility.

**Imports required:**

```kotlin
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import org.enchant.core.ui.WindowBreakpoint
import org.enchant.core.ui.getWindowBreakpoint
import org.enchant.core.ui.isWidthExpanded
```

**Structure:**

```kotlin
enum class NavigationType {
    RAIL,
    BAR;

    companion object {
        /**
         * Selects [BAR] or [RAIL] based on [WindowBreakpoint]:
         * - SMALL → always BAR
         * - MEDIUM → RAIL if width is expanded (foldable unfolded), BAR otherwise
         * - LARGE → always RAIL (tablet)
         */
        @Composable
        fun rememberNavigationType(): NavigationType {
            val resources = LocalResources.current
            val config = LocalConfiguration.current
            val windowBreakpoint = remember(config) { resources.getWindowBreakpoint() }

            return when (windowBreakpoint) {
                WindowBreakpoint.SMALL -> BAR
                WindowBreakpoint.MEDIUM -> {
                    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
                    if (windowSizeClass.isWidthExpanded) {
                        RAIL
                    } else {
                        BAR
                    }
                }
                WindowBreakpoint.LARGE -> RAIL
            }
        }

        /**
         * Whether a navigation rail is visible. If false, the bottom bar is shown.
         */
        @Composable
        fun isRail(): Boolean = rememberNavigationType() == RAIL
    }
}
```

**Integration with AppScaffold:**

The `NavigationType` is consumed inside `ListAndNavigation` (within `AppScaffold`) like this:

```kotlin
@Composable
fun ListAndNavigation(
    listContent: @Composable () -> Unit,
    navRailContent: @Composable () -> Unit,
    bottomNavContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationType = NavigationType.rememberNavigationType()

    Row(modifier = modifier) {
        if (navigationType == NavigationType.RAIL) {
            navRailContent()
        }

        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.weight(1f)) {
                listContent()
            }

            if (navigationType == NavigationType.BAR) {
                bottomNavContent()
            }
        }
    }
}
```

**Tests to write:**

- `rememberNavigationType()` returns `BAR` when `WindowBreakpoint.SMALL`
- `rememberNavigationType()` returns `RAIL` when `WindowBreakpoint.LARGE`
- `rememberNavigationType()` returns `RAIL` when `MEDIUM` + width expanded
- `rememberNavigationType()` returns `BAR` when `MEDIUM` + width not expanded
- `isRail()` returns `true` for `RAIL`, `false` for `BAR`

---

### M5c: AppPaneDragHandle.kt

**Full path:** `app/src/main/java/org/enchant/window/AppPaneDragHandle.kt`

**Package:** `org.enchant.window`

**Single responsibility:** Provide the draggable divider between list and detail panes in the adaptive (tablet) scaffold. A small vertical pill that enables pane resize via touch.

**Imports required:**

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.paneExpansionDraggable
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
```

**Structure:**

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ThreePaneScaffoldScope.AppPaneDragHandle(
    paneExpansionState: PaneExpansionState,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .paneExpansionDraggable(
                state = paneExpansionState,
                minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                interactionSource = interactionSource,
                semanticsProperties = paneExpansionState.defaultDragHandleSemantics()
            )
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 48.dp)
                .background(
                    color = Color(0xFF605F5D),
                    shape = RoundedCornerShape(percent = 50)
                )
        )
    }
}
```

**Key implementation details:**
- Extension function on `ThreePaneScaffoldScope` so it can be used inside `NavigableListDetailPaneScaffold`'s `paneExpansionDragHandle` slot
- Uses `paneExpansionDraggable` modifier (Material3 adaptive) for gesture handling
- Visual: a 4dp × 48dp rounded pill in neutral gray
- `MutableInteractionSource` is remembered so it survives recomposition
- `defaultDragHandleSemantics()` provides accessibility semantics

**Tests to write:**

- `AppPaneDragHandle` composable renders and does not crash
- `paneExpansionDraggable` is called with correct `PaneExpansionState`
- Drag handle responds to touch events (integration test)

---

### M6: AppScaffold.kt

**Full path:** `app/src/main/java/org/enchant/window/AppScaffold.kt`

**Package:** `org.enchant.window`

**Single responsibility:** Top-level adaptive scaffold that branches into either `SinglePaneAppScaffold` (phone, API < 33) or `AdaptiveAppScaffold` (tablet/foldable). **Generic over content** — takes composable lambdas for secondary (list) pane, primary (detail) pane, navigation rail, and bottom navigation bar, making it a reusable shell not coupled to any ViewModel or NavHostController.

**Imports required:**

```kotlin
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
```

**Structure:**

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppScaffold(
    navigator: AppScaffoldNavigator<Any>,
    modifier: Modifier = Modifier,
    topBarContent: @Composable () -> Unit = {},
    primaryContent: @Composable () -> Unit = {},
    secondaryContent: @Composable () -> Unit,
    navRailContent: @Composable () -> Unit = {},
    bottomNavContent: @Composable () -> Unit = {},
    paneExpansionState: PaneExpansionState = rememberPaneExpansionState(),
    paneExpansionDragHandle: @Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.systemBars,
    animatorFactory: AppScaffoldAnimationStateFactory = AppScaffoldAnimationStateFactory.Default
) {
    val isForceSinglePane = false // set from developer options or testing
    val useSimpleScaffold = isForceSinglePane ||
        (navigator.scaffoldDirective.maxHorizontalPartitions == 1 && Build.VERSION.SDK_INT < 33)

    if (useSimpleScaffold) {
        SinglePaneAppScaffold(
            navigator = navigator,
            modifier = modifier,
            topBarContent = topBarContent,
            primaryContent = primaryContent,
            secondaryContent = secondaryContent,
            bottomNavContent = bottomNavContent,
            snackbarHost = snackbarHost,
            contentWindowInsets = contentWindowInsets,
            animatorFactory = animatorFactory
        )
    } else {
        AdaptiveAppScaffold(
            navigator = navigator,
            modifier = modifier,
            topBarContent = topBarContent,
            primaryContent = primaryContent,
            secondaryContent = secondaryContent,
            navRailContent = navRailContent,
            bottomNavContent = bottomNavContent,
            paneExpansionState = paneExpansionState,
            paneExpansionDragHandle = paneExpansionDragHandle,
            snackbarHost = snackbarHost,
            contentWindowInsets = contentWindowInsets,
            animatorFactory = animatorFactory
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AdaptiveAppScaffold(
    navigator: AppScaffoldNavigator<Any>,
    modifier: Modifier = Modifier,
    topBarContent: @Composable () -> Unit = {},
    primaryContent: @Composable () -> Unit = {},
    secondaryContent: @Composable () -> Unit,
    navRailContent: @Composable () -> Unit = {},
    bottomNavContent: @Composable () -> Unit = {},
    paneExpansionState: PaneExpansionState = rememberPaneExpansionState(),
    paneExpansionDragHandle: @Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.systemBars,
    animatorFactory: AppScaffoldAnimationStateFactory = AppScaffoldAnimationStateFactory.Default
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = contentWindowInsets,
        topBar = topBarContent,
        snackbarHost = snackbarHost,
        modifier = modifier
    ) { paddingValues ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                val animationState = with(animatorFactory) {
                    this@NavigableListDetailPaneScaffold.getListAnimationState(navigator.state)
                }
                AnimatedPane(
                    enterTransition = EnterTransition.None,
                    exitTransition = ExitTransition.None,
                    modifier = Modifier
                        .zIndex(0f)
                        .drawWithContent {
                            with(animationState) { applyParentValues() }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                with(animationState) { applyChildValues() }
                            }
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val minPaneWidth = navigator.scaffoldDirective.defaultPanePreferredWidth.roundToPx()
                                val width = max(minPaneWidth, constraints.maxWidth)
                                val placeable = measurable.measure(
                                    constraints.copy(minWidth = minPaneWidth, maxWidth = width)
                                )
                                layout(constraints.maxWidth, placeable.height) {
                                    placeable.placeRelative(x = 0, y = 0)
                                }
                            }
                    ) {
                        ListAndNavigation(
                            listContent = secondaryContent,
                            navRailContent = navRailContent,
                            bottomNavContent = bottomNavContent
                        )
                    }
                }
            },
            detailPane = {
                val animationState = with(animatorFactory) {
                    this@NavigableListDetailPaneScaffold.getDetailAnimationState(navigator.state)
                }
                AnimatedPane(
                    enterTransition = EnterTransition.None,
                    exitTransition = ExitTransition.None,
                    modifier = Modifier
                        .zIndex(1f)
                        .drawWithContent {
                            with(animationState) { applyParentValues() }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                with(animationState) { applyChildValues() }
                            }
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val minPaneWidth = navigator.scaffoldDirective.defaultPanePreferredWidth.roundToPx()
                                val width = max(minPaneWidth, constraints.maxWidth)
                                val placeable = measurable.measure(
                                    constraints.copy(minWidth = minPaneWidth, maxWidth = width)
                                )
                                layout(constraints.maxWidth, placeable.height) {
                                    placeable.placeRelative(x = 0, y = 0)
                                }
                            }
                    ) {
                        primaryContent()
                    }
                }
            },
            paneExpansionDragHandle = paneExpansionDragHandle,
            paneExpansionState = paneExpansionState,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun SinglePaneAppScaffold(
    navigator: AppScaffoldNavigator<Any>,
    modifier: Modifier = Modifier,
    topBarContent: @Composable () -> Unit = {},
    primaryContent: @Composable () -> Unit = {},
    secondaryContent: @Composable () -> Unit,
    bottomNavContent: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.systemBars,
    animatorFactory: AppScaffoldAnimationStateFactory = AppScaffoldAnimationStateFactory.Default
) {
    val showDetail = navigator.scaffoldValue.primary == PaneAdaptedValue.Expanded
    val coroutineScope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val directionMultiplier = if (isRtl) -1 else 1

    BackHandler(enabled = navigator.canNavigateBack()) {
        coroutineScope.launch { navigator.navigateBack() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = contentWindowInsets,
        topBar = topBarContent,
        snackbarHost = snackbarHost,
        modifier = modifier
    ) { paddingValues ->
        AnimatedContent(
            targetState = showDetail,
            transitionSpec = {
                val transform = when {
                    targetState ->
                        slideInHorizontally(
                            animationSpec = AppScaffoldAnimationDefaults.tween()
                        ) { fullWidth -> fullWidth * directionMultiplier } togetherWith
                        slideOutHorizontally(
                            animationSpec = AppScaffoldAnimationDefaults.tween()
                        ) { fullWidth -> -fullWidth * directionMultiplier }
                    else ->
                        slideInHorizontally(
                            animationSpec = AppScaffoldAnimationDefaults.tween()
                        ) { fullWidth -> -fullWidth * directionMultiplier } togetherWith
                        slideOutHorizontally(
                            animationSpec = AppScaffoldAnimationDefaults.tween()
                        ) { fullWidth -> fullWidth * directionMultiplier }
                }
                transform using SizeTransform(clip = false) { _, _ -> snap() }
            },
            modifier = Modifier.padding(paddingValues),
            label = "SinglePaneAppScaffold"
        ) { isDetail ->
            if (isDetail) {
                primaryContent()
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        secondaryContent()
                    }
                    bottomNavContent()
                }
            }
        }
    }
}

/**
 * Combines list content with navigation type selection (BAR vs RAIL).
 * Consumed by [AdaptiveAppScaffold]'s list pane.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ListAndNavigation(
    listContent: @Composable () -> Unit,
    navRailContent: @Composable () -> Unit,
    bottomNavContent: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.systemBars,
    modifier: Modifier = Modifier
) {
    val navigationType = NavigationType.rememberNavigationType()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {},
        contentWindowInsets = contentWindowInsets,
        snackbarHost = snackbarHost,
        modifier = modifier
    ) { paddingValues ->
        Row(modifier = Modifier.padding(paddingValues)) {
            if (navigationType == NavigationType.RAIL) {
                navRailContent()
            }

            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f)) {
                    listContent()
                }

                if (navigationType == NavigationType.BAR) {
                    bottomNavContent()
                }
            }
        }
    }
}
```

**Key implementation details:**
- **Generic over content** — takes lambdas instead of ViewModel/NavHostController references. Makes it testable and reusable across different Activity setups.
- `useSimpleScaffold` is true when `maxHorizontalPartitions == 1` (phone mode) OR Android SDK < 33 (no predictive back support).
- `AnimatedContent` handles the transition between list-only and detail-visible in phone mode with slide + SizeTransform.
- `NavigableListDetailPaneScaffold` provides the full multi-pane layout in tablet mode with per-pane animation via `AppScaffoldAnimationState`.
- `ListAndNavigation` integrates `NavigationType.rememberNavigationType()` to choose between rail (tablet) and bar (phone).
- `paneExpansionDragHandle` slot is filled with `AppPaneDragHandle` by the caller (typically `MainActivity`).
- `animatorFactory` allows callers to provide custom animation strategies; defaults to `AppScaffoldAnimationStateFactory.Default`.

**Tests to write:**

- `useSimpleScaffold` is true when `maxHorizontalPartitions == 1` and SDK < 33
- `useSimpleScaffold` is false when `maxHorizontalPartitions == 2` and SDK >= 33
- `SinglePaneAppScaffold` slides between list and detail
- `AdaptiveAppScaffold` uses `NavigableListDetailPaneScaffold` with correct navigator
- `ListAndNavigation` shows `navRailContent` when `NavigationType.RAIL`
- `ListAndNavigation` shows `bottomNavContent` when `NavigationType.BAR`
- `BackHandler` calls `navigator.navigateBack()` only when `canNavigateBack()` is true

---

### M7: MainNavigationBar.kt + MainNavigationRail.kt

**Full path (bar):** `app/src/main/java/org/enchant/main/MainNavigationBar.kt`
**Full path (rail):** `app/src/main/java/org/enchant/main/MainNavigationRail.kt`

**Package:** `org.enchant.main`

**Single responsibility:** The bottom navigation bar (phone) and navigation rail (tablet landscape) for the 4 main tabs. Supports compact mode (48dp height), animated Lottie tab icons, unread count badges, conditional ARCHIVE filtering, and FAB integration in the rail variant.

**Shared imports (both files):**

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
```

**MainNavigationBar.kt:**

```kotlin
package org.enchant.main

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.enchant.MainNavigationListLocation
import org.enchant.main.MainNavigationState

@Composable
fun MainNavigationBar(
    state: MainNavigationState,
    onDestinationSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(if (state.compact) 48.dp else 80.dp),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        val entries = remember(state.isStoriesFeatureEnabled) {
            if (state.isStoriesFeatureEnabled) {
                MainNavigationListLocation.entries.filterNot { it == MainNavigationListLocation.ARCHIVE }
            } else {
                MainNavigationListLocation.entries.filterNot {
                    it == MainNavigationListLocation.STORIES || it == MainNavigationListLocation.ARCHIVE
                }
            }
        }

        entries.forEach { destination ->
            val badgeCount = when (destination) {
                MainNavigationListLocation.CHATS -> state.chatsCount
                MainNavigationListLocation.CALLS -> state.callsCount
                MainNavigationListLocation.STORIES -> state.storiesCount
                MainNavigationListLocation.ARCHIVE -> 0
            }
            val selected = state.currentListLocation == destination

            NavigationBarItem(
                selected = selected,
                icon = {
                    NavigationDestinationIcon(
                        destination = destination,
                        selected = selected
                    )
                },
                label = if (state.compact) null else {
                    { Text(stringResource(destination.label)) }
                },
                onClick = { onDestinationSelected(destination) },
                modifier = Modifier.drawNavigationBarBadge(count = badgeCount, compact = state.compact)
            )
        }
    }
}

/**
 * Draws a pill badge over the navigation bar item for unread counts.
 * Uses [drawWithContent] because NavigationBarItem does not natively
 * support badge overlays outside of its icon slot.
 */
@Composable
private fun Modifier.drawNavigationBarBadge(count: Int, compact: Boolean): Modifier {
    if (count <= 0) return this

    val formatted = formatCount(count)
    val textMeasurer = rememberTextMeasurer()
    val color = colorResource(R.color.badge_unread)
    val textStyle = MaterialTheme.typography.labelMedium
    val textLayoutResult = remember(formatted) { textMeasurer.measure(formatted, textStyle) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = with(LocalDensity.current) { 4.dp.toPx() }
    val xOffsetExtra = with(LocalDensity.current) { 4.dp.toPx() }
    val yOffset = with(LocalDensity.current) { if (compact) 6.dp.toPx() else 10.dp.toPx() }

    return this
        .onSizeChanged { size = it }
        .drawWithContent {
            drawContent()
            val xOffset = size.width.toFloat() / 2f + xOffsetExtra
            if (size != IntSize.Zero) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(xOffset, yOffset),
                    size = Size(
                        textLayoutResult.size.width.toFloat() + padding * 2,
                        textLayoutResult.size.height.toFloat()
                    ),
                    cornerRadius = CornerRadius(yRadius, yRadius)
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    color = Color.White,
                    topLeft = Offset(xOffset + padding, yOffset)
                )
            }
        }
}

@Composable
private fun NavigationDestinationIcon(
    destination: MainNavigationListLocation,
    selected: Boolean
) {
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                MaterialTheme.colorScheme.onSurface.hashCode(),
                BlendModeCompat.SRC_ATOP
            ),
            keyPath = arrayOf("**")
        )
    )
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(destination.icon))
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = composition?.duration?.toInt() ?: 0)
    )
    LottieAnimation(
        composition = composition,
        progress = { if (selected) progress else 0f },
        dynamicProperties = dynamicProperties,
        modifier = Modifier.size(28.dp)
    )
}

private fun formatCount(count: Int): String {
    return if (count > 99) "99+" else count.toString()
}
```

**MainNavigationRail.kt:**

```kotlin
package org.enchant.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationListLocation

@Composable
fun MainNavigationRail(
    state: MainNavigationState,
    mainFloatingActionButtons: @Composable () -> Unit,
    onDestinationSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = remember(state.isStoriesFeatureEnabled) {
        if (state.isStoriesFeatureEnabled) {
            MainNavigationListLocation.entries.filterNot { it == MainNavigationListLocation.ARCHIVE }
        } else {
            MainNavigationListLocation.entries.filterNot {
                it == MainNavigationListLocation.STORIES || it == MainNavigationListLocation.ARCHIVE
            }
        }
    }
    val selectedDestination = if (state.currentListLocation == MainNavigationListLocation.ARCHIVE) {
        MainNavigationListLocation.CHATS
    } else {
        state.currentListLocation
    }

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(40.dp).weight(1f, fill = false))
        mainFloatingActionButtons()
        Spacer(modifier = Modifier.height(40.dp).weight(1f, fill = false))

        entries.forEachIndexed { idx, destination ->
            val selected = selectedDestination == destination
            Box {
                NavigationRailItem(
                    modifier = Modifier.padding(
                        bottom = if (idx == entries.lastIndex) 0.dp else 16.dp
                    ),
                    icon = {
                        NavigationDestinationIcon(destination = destination, selected = selected)
                    },
                    label = { Text(stringResource(destination.label)) },
                    selected = selected,
                    onClick = { onDestinationSelected(destination) }
                )
                // Count indicator badge overlay
                val count = when (destination) {
                    MainNavigationListLocation.CHATS -> state.chatsCount
                    MainNavigationListLocation.CALLS -> state.callsCount
                    MainNavigationListLocation.STORIES -> state.storiesCount
                    else -> 0
                }
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 42.dp)
                            .height(16.dp)
                            .defaultMinSize(minWidth = 16.dp)
                            .background(
                                color = colorResource(R.color.badge_unread),
                                shape = RoundedCornerShape(percent = 50)
                            )
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = formatCount(count),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
```

**ListAndNavigation.kt** — This is now part of `AppScaffold.kt` (M6). The `ListAndNavigation` composable in M6 handles BAR vs RAIL selection and integrates `NavigationType.rememberNavigationType()`. The previous ViewModel-coupled version (`ListAndNavigation(mainNavigationViewModel, paneExpansionState)`) is **replaced** by the generic lambda-based version in M6:

```kotlin
// See M6: ListAndNavigation(listContent, navRailContent, bottomNavContent)
// This generic version uses NavigationType internally.
```

The caller (e.g., `MainActivity`) wires the ViewModel to the lambdas:

```kotlin
AppScaffold(
    navigator = navigator,
    secondaryContent = {
        ConversationListScreen(
            onConversationClick = { args ->
                mainNavigationViewModel.goToDetail(
                    MainNavigationDetailLocation.Conversation(args)
                )
            }
        )
    },
    navRailContent = {
        MainNavigationRail(
            state = mainNavigationState,
            mainFloatingActionButtons = { MainFloatingActionButtons(...) },
            onDestinationSelected = { mainNavigationViewModel.goTo(it) }
        )
    },
    bottomNavContent = {
        MainNavigationBar(
            state = mainNavigationState,
            onDestinationSelected = { mainNavigationViewModel.goTo(it) }
        )
    }
)
```

**Tests to write:**

- `MainNavigationBar` renders correct items based on `isStoriesFeatureEnabled`
- `MainNavigationBar` compact mode sets height to 48.dp
- `MainNavigationBar` hides labels when compact is true
- `drawNavigationBarBadge` draws pill when count > 0, no-op when count <= 0
- `MainNavigationRail` renders `NavigationRailItem`s for each visible destination
- `MainNavigationRail` FAB slot renders FAB content
- `NavigationDestinationIcon` animates progress from 0 to 1 on selection
- `formatCount` returns "99+" for counts > 99
- Clicking a tab calls `onDestinationSelected` with the correct tab

---

### M7b: MainFloatingActionButtons.kt

**Full path:** `app/src/main/java/org/enchant/main/MainFloatingActionButtons.kt`

**Package:** `org.enchant.main`

**Single responsibility:** Provide animated floating action buttons (FABs) shown in the navigation rail area on tablet/foldable layouts. Each tab can have a different FAB: Chats → new chat, Calls → new call, Stories → new story. Uses animated content transitions when switching between tabs.

**Imports required:**

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationListLocation
import org.enchant.window.NavigationType
```

**Structure:**

```kotlin
interface MainFloatingActionButtonsCallback {
    fun onNewChatClick()
    fun onNewCallClick()
    fun onCameraClick(destination: MainNavigationListLocation)

    object Empty : MainFloatingActionButtonsCallback {
        override fun onNewChatClick() = Unit
        override fun onNewCallClick() = Unit
        override fun onCameraClick(destination: MainNavigationListLocation) = Unit
    }
}

@Composable
fun MainFloatingActionButtons(
    destination: MainNavigationListLocation,
    callback: MainFloatingActionButtonsCallback,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            slideInVertically { it } togetherWith slideOutVertically { -it }
        },
        label = "FABContent",
        modifier = modifier
    ) { tab ->
        when (tab) {
            MainNavigationListLocation.CHATS,
            MainNavigationListLocation.ARCHIVE -> NewChatFab(onClick = callback::onNewChatClick)
            MainNavigationListLocation.CALLS -> NewCallFab(onClick = callback::onNewCallClick)
            MainNavigationListLocation.STORIES -> CameraFab(
                onClick = { callback.onCameraClick(tab) }
            )
        }
    }
}
```

**Key implementation details:**
- Uses `AnimatedContent` with vertical slide transitions when switching between tabs
- `MainFloatingActionButtonsCallback` interface allows decoupling from ViewModel — the Activity wires the concrete implementation
- `Empty` singleton provides a no-op implementation for previews/testing
- Each tab shows a different FAB: pencil icon for new chat, phone icon for new call, camera icon for new story
- The rail variant's FAB is positioned at the top of the navigation rail content, above the tab items
- On compact (phone) layouts, FABs are typically absent or rendered as a `FloatingActionButton` overlaid on the list content

**Tests to write:**
- FAB renders correct icon for CHATS tab
- FAB renders correct icon for CALLS tab
- FAB renders correct icon for STORIES tab
- `AnimatedContent` transitions between tabs with vertical slide
- `Empty` callback does not crash when invoked
- Click events propagate to the callback

---

### M8: MainActivityComponents.kt

**Full path:** `app/src/main/java/org/enchant/main/MainActivityComponents.kt`

**Package:** `org.enchant.main`

**Single responsibility:** Set up per-tab `NavHostController`s, wire the `detailLocation` Flow from `MainNavigationViewModel` to the correct NavHost, and provide `MainNavigationDetailLocationEffect` for pane focus coordination. Contains `rememberDetailNavHostController()` which creates a shared-ViewModelStore NavHost per tab, and `navigateToDetailLocation()` for type-safe navigation to detail routes.

**Imports required:**

```kotlin
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationViewModel
import org.thoughtcrime.securesms.R
```

**Structure:**

```kotlin
/**
 * Displayed when the user has not selected content for a given tab.
 */
@Composable
fun EmptyDetailScreen() {
    Box(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_signal_logo_large),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.Center)
        )
    }
}

/**
 * Observes [MainNavigationViewModel.detailLocation] Flow and focuses the appropriate
 * pane (Primary for detail content, Secondary for Empty).
 * Persists the latest value via rememberSaveable so it survives Activity re-creation.
 */
@Composable
fun MainNavigationDetailLocationEffect(
    mainNavigationViewModel: MainNavigationViewModel,
    onWillFocusPrimary: suspend () -> Unit = {}
) {
    val state = rememberSaveable(
        stateSaver = MainNavigationDetailLocation.Saver(
            mainNavigationViewModel.earlyNavigationDetailLocationRequested
        )
    ) {
        mutableStateOf(
            mainNavigationViewModel.earlyNavigationDetailLocationRequested
                ?: MainNavigationDetailLocation.Empty
        )
    }

    LaunchedEffect(Unit) {
        mainNavigationViewModel.detailLocation.collect {
            if (state.value == it) {
                mainNavigationViewModel.setFocusedPane(
                    if (it == MainNavigationDetailLocation.Empty) {
                        ThreePaneScaffoldRole.Secondary
                    } else {
                        if (it.isContentRoot) {
                            onWillFocusPrimary()
                        }
                        ThreePaneScaffoldRole.Primary
                    }
                )
            }
            state.value = it
        }
    }
}

/**
 * Creates and remembers a [NavHostController] for a per-tab detail nav graph.
 * Shares the parent [ViewModelStore] so ViewModels survive tab switches.
 * Automatically requests Primary pane focus when a non-Empty destination is active.
 */
@Composable
fun rememberDetailNavHostController(
    onRequestFocus: (ThreePaneScaffoldRole) -> Unit,
    builder: NavGraphBuilder.(NavHostController) -> Unit
): NavHostController {
    val navHostController = rememberNavController()
    val viewModelStore = LocalViewModelStoreOwner.current!!.viewModelStore

    remember {
        val graph = navHostController.createGraph(
            startDestination = MainNavigationDetailLocation.Empty,
            builder = { builder(navHostController) }
        )
        navHostController.setViewModelStore(viewModelStore)
        navHostController.setGraph(graph, null)
        graph
    }

    val entry by navHostController.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        if (entry != null &&
            entry?.destination?.route != MainNavigationDetailLocation.Empty::class.qualifiedName
        ) {
            onRequestFocus(ThreePaneScaffoldRole.Primary)
        } else {
            onRequestFocus(ThreePaneScaffoldRole.Secondary)
        }
    }

    return navHostController
}

/**
 * Navigates to a [MainNavigationDetailLocation] inside a per-tab NavHost.
 * If the location is a content root (Conversation, Empty, Settings), the
 * entire back stack is cleared before navigating.
 */
fun NavHostController.navigateToDetailLocation(location: MainNavigationDetailLocation) {
    navigate(location) {
        launchSingleTop = true
        if (location.isContentRoot) {
            popUpTo(graph.id) { inclusive = true }
        }
    }
}

/**
 * Renders the NavHost for the currently active tab's detail pane.
 * Animations are disabled (handled by AppScaffold's outer scaffold instead).
 */
@Composable
fun DetailsScreenNavHost(
    navHostController: NavHostController,
    contentLayoutData: MainContentLayoutData,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navHostController,
        graph = navHostController.graph,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        modifier = modifier
            .padding(end = contentLayoutData.detailPaddingEnd)
            .clip(contentLayoutData.shape)
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
    )
}
```

**Per-tab NavGraph builders (example for Chats tab):**

```kotlin
// In your module's scope, build the chat detail nav graph:
val chatsNavHostController = rememberDetailNavHostController(
    onRequestFocus = { mainNavigationViewModel.setFocusedPane(it) },
    builder = { navController ->
        composable<MainNavigationDetailLocation.Conversation> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.Conversation>()
            ConversationScreen(
                conversationArgs = route.conversationArgs,
                onBack = { navController.popBackStack() },
                onMessageDetails = { recipientId, messageId ->
                    navController.navigate(
                        MainNavigationDetailLocation.Chats.MessageDetails(recipientId, messageId)
                    )
                }
            )
        }

        composable<MainNavigationDetailLocation.Chats.MessageDetails> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.Chats.MessageDetails>()
            MessageDetailsScreen(
                recipientId = route.recipientId,
                messageId = route.messageId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<MainNavigationDetailLocation.Chats.ConversationSettings> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.Chats.ConversationSettings>()
            ConversationSettingsScreen(
                recipientId = route.recipientId,
                onBack = { navController.popBackStack() }
            )
        }

        composable<MainNavigationDetailLocation.Empty> { EmptyDetailScreen() }
        composable<MainNavigationDetailLocation.Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
)
```

**Key implementation details:**
- `MainNavigationDetailLocationEffect` subscribes to the `detailLocation` SharedFlow and sets `ThreePaneScaffoldRole.Primary`/`Secondary` focus. The `rememberSaveable` with `MainNavigationDetailLocation.Saver` survives configuration changes.
- `rememberDetailNavHostController` creates a NavHost with shared `ViewModelStore` — ViewModels inside per-tab NavHosts persist across tab switches.
- `navigateToDetailLocation()` clears the back stack for content roots (Conversation, Empty, Settings) and uses `launchSingleTop` to avoid duplicates.
- `DetailsScreenNavHost` disables animation (the outer `AppScaffold` handles transitions via `AppScaffoldAnimationState`).
- `contentLayoutData.shape` and `detailPaddingEnd` drive adaptive rounded corners and padding in tablet mode.

**Tests to write:**
- `MainNavigationDetailLocationEffect` focuses Primary when non-Empty location emitted
- `MainNavigationDetailLocationEffect` focuses Secondary when Empty emitted
- `rememberDetailNavHostController` sets shared ViewModelStore
- `navigateToDetailLocation(Empty)` clears back stack
- `navigateToDetailLocation(Conversation)` uses launchSingleTop
- `DetailsScreenNavHost` renders NavHost with disabled animations

---

### M9: WindowSizeClassExtensions.kt

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/WindowSizeClassExtensions.kt`

> **Note:** In Signal, `WindowSizeClassExtensions` lives in `core/ui` (not `app/util`) because it depends on Compose UI primitives and is shared across features. The app module references it via the `core.ui` package.

**Package:** `org.enchant.core.ui`

**Single responsibility:** Detect window breakpoints and determine whether the app should be in split-pane (tablet) or single-pane (phone) mode. Used by `AppScaffold` and `AppScaffoldNavigator` to decide which layout to use.

**Imports required:**

```kotlin
import android.content.res.Resources
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.enchant.core.ui.util.LocalResources
```

**Structure:**

```kotlin
enum class WindowBreakpoint {
    SMALL,   // Phone (compact)
    MEDIUM,  // Foldable / medium tablet
    LARGE    // Tablet landscape
}

fun Resources.getWindowBreakpoint(): WindowBreakpoint {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
        return WindowBreakpoint.SMALL
    }

    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium) {
        return WindowBreakpoint.MEDIUM
    }

    return WindowBreakpoint.LARGE
}

fun Resources.isSplitPane(forceSplitPane: Boolean = false): Boolean {
    if (forceSplitPane) return true

    val breakpoint = getWindowBreakpoint()
    if (breakpoint == WindowBreakpoint.SMALL) return false

    if (breakpoint == WindowBreakpoint.LARGE && displayMetrics.widthPixels < displayMetrics.heightPixels) {
        return false  // Large but portrait = phone mode
    }

    return true  // MEDIUM or LARGE in landscape = split pane
}

fun WindowSizeClass.Companion.horizontalPartitionDefaultSpacerSize(): Dp {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 0.dp
        WindowWidthSizeClass.Medium -> 8.dp
        WindowWidthSizeClass.Expanded -> 16.dp
        else -> 8.dp
    }
}

fun WindowSizeClass.Companion.listPaneDefaultPreferredWidth(): Dp {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 400.dp
        WindowWidthSizeClass.Medium -> 400.dp
        WindowWidthSizeClass.Expanded -> 400.dp
        else -> 400.dp
    }
}
```

**Tests to write:**

- `SMALL` breakpoint when `widthSizeClass == Compact`
- `MEDIUM` breakpoint when `widthSizeClass == Medium`
- `LARGE` breakpoint when `widthSizeClass == Expanded`
- `isSplitPane` returns false for `SMALL`
- `isSplitPane` returns false for `LARGE` in portrait (width < height)
- `isSplitPane` returns true for `MEDIUM` or `LARGE` in landscape
- `isSplitPane` returns true when `forceSplitPane = true`

---

### M10: MainActivity Integration

**Full path:** `app/src/main/java/org/enchant/MainActivity.kt`

**Package:** `org.enchant`

**Single responsibility:** Assemble all the main navigation components, create per-tab NavHostControllers, wire `AppScaffold` using the generic lambda API, and handle deep link intents. This is the top-level entry point.

**Imports required:**

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
```

**Structure:**

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class MainActivity : ComponentActivity() {

    private val mainNavigationViewModel: MainNavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLinkIntent(intent)
        setContent { MainContent() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    @Composable
    private fun MainContent() {
        val scope = rememberCoroutineScope()
        val navigator = rememberAppScaffoldNavigator()
        val paneExpansionState = rememberPaneExpansionState()
        val mainNavigationState by mainNavigationViewModel.mainNavigationState
            .collectAsStateWithLifecycle()

        // Wire navigator into ViewModel (sets up Nav inner class for lockPaneToSecondary)
        LaunchedEffect(Unit) {
            mainNavigationViewModel.wrapNavigator(scope, navigator)
        }

        // Create per-tab detail NavHostControllers
        val chatsNavHostController = rememberDetailNavHostController(
            onRequestFocus = { mainNavigationViewModel.setFocusedPane(it) },
            builder = { chatsNavGraph(this@MainActivity) }
        )
        val callsNavHostController = rememberDetailNavHostController(
            onRequestFocus = { mainNavigationViewModel.setFocusedPane(it) },
            builder = { callsNavGraph(this@MainActivity) }
        )

        // Observe detailLocation to drive per-tab NavHost navigation
        MainNavigationDetailLocationEffect(
            mainNavigationViewModel = mainNavigationViewModel
        )

        AppScaffold(
            navigator = navigator,
            modifier = Modifier.fillMaxSize(),
            secondaryContent = {
                // List pane content — switches based on active tab
                when (mainNavigationState.currentListLocation) {
                    MainNavigationListLocation.CHATS,
                    MainNavigationListLocation.ARCHIVE -> ConversationListScreen(
                        onConversationClick = { args ->
                            mainNavigationViewModel.goToDetail(
                                MainNavigationDetailLocation.Conversation(args)
                            )
                        }
                    )
                    MainNavigationListLocation.CALLS -> CallLogScreen()
                    MainNavigationListLocation.STORIES -> StoriesLandingScreen()
                }
            },
            primaryContent = {
                // Detail pane content — uses per-tab NavHost
                when (mainNavigationState.currentListLocation) {
                    MainNavigationListLocation.CHATS,
                    MainNavigationListLocation.ARCHIVE -> DetailsScreenNavHost(
                        navHostController = chatsNavHostController,
                        contentLayoutData = MainContentLayoutData(...)
                    )
                    MainNavigationListLocation.CALLS -> DetailsScreenNavHost(
                        navHostController = callsNavHostController,
                        contentLayoutData = MainContentLayoutData(...)
                    )
                    MainNavigationListLocation.STORIES -> StoriesFullScreenContent()
                }
            },
            navRailContent = {
                MainNavigationRail(
                    state = mainNavigationState,
                    mainFloatingActionButtons = {
                        MainFloatingActionButtons(
                            destination = mainNavigationState.currentListLocation,
                            callback = mainFloatingActionButtonsCallback
                        )
                    },
                    onDestinationSelected = { mainNavigationViewModel.goTo(it) }
                )
            },
            bottomNavContent = {
                MainNavigationBar(
                    state = mainNavigationState,
                    onDestinationSelected = { mainNavigationViewModel.goTo(it) }
                )
            },
            paneExpansionState = paneExpansionState,
            paneExpansionDragHandle = {
                AppPaneDragHandle(paneExpansionState = it)
            }
        )
    }
}
```

**Key implementation details:**
- `wrapNavigator()` is called in `LaunchedEffect(Unit)` — this hooks the `Nav` inner class (with `lockPaneToSecondary`) before any navigation events fire.
- `rememberDetailNavHostController` creates independent NavHostControllers per tab with shared `ViewModelStore`.
- `MainNavigationDetailLocationEffect` subscribes to the ViewModel's `detailLocation` Flow and drives per-tab NavHost navigation.
- Deep link handling fires in `onCreate`/`onNewIntent` before `setContent`, so `earlyNavigation*Requested` captures intent destinations before the navigator exists.

**Tests to write:**
- `wrapNavigator` is called before any navigation occurs
- Per-tab NavHostControllers are independent
- Deep link in `onCreate` sets `earlyNavigationDetailLocationRequested`
- `AppScaffold` renders with correct lambdas based on `currentListLocation`

---

## 9. Phase 5: Migration of Current Code

**File: `core/navigation/src/main/java/org/enchant/navigation/NavRoute.kt` → DELETE**

What's currently there: `sealed class NavRoute` with 45+ `data object`/`data class` entries, all using `override val route: String`.

What replaces it: Per-feature `sealed interface NavKey` files (P2-01). Each feature defines its own routes.

Migration rule: every `NavRoute.X` becomes `FeatureNavKey.X`. String routes are gone.

**File: `core/navigation/src/main/java/org/enchant/navigation/NavHost.kt` → DELETE**

What's currently there: `fun NavRoute.navigate(controller: NavHostController)` and `fun NavRoute.navigateAndClearStack(controller: NavHostController)` — extension functions that produce route strings and call `controller.navigate()`.

What replaces it: `backStack.add(key)` and `backStack.clear(); backStack.add(key)`.

Migration rule:
- `route.navigate(controller)` → `backStack.add(route)`
- `route.navigateAndClearStack(controller)` → `backStack.clear(); backStack.add(route)`
- Controller parameter is gone — use `rememberNavBackStack` or ViewModel backStack instead.

---

## 10. Phase S: Startup Routing (Pre-Main State Machine)

> **Why:** Before the user ever reaches `MainActivity`, the app must route through a sequence of pre-conditions: lock screen/registration, onboarding PIN, profile creation, data transfer, backup restore. Signal implements this as a state machine in `PassphraseRequiredActivity`. Enchant needs the same.

**Architecture overview:**

```
App Launch
  └─> AppStartupRouter (determines current state)
        ├─ STATE_LOCK_SCREEN        → LockScreenActivity
        ├─ STATE_REGISTRATION       → RegistrationActivity
        ├─ STATE_PIN_CREATE         → CreatePinActivity
        ├─ STATE_PIN_RESTORE        → PinRestoreActivity
        ├─ STATE_PROFILE_CREATE     → CreateProfileActivity
        ├─ STATE_TRANSFER_OLD       → OldDeviceTransferActivity
        ├─ STATE_TRANSFER_RESTORE   → RestoreActivity
        ├─ STATE_CHANGE_NUMBER_LOCK → ChangeNumberLockActivity
        └─ STATE_NORMAL             → MainActivity (no redirect)
```

---

### S1: AppStartupState.kt

**Full path:** `app/src/main/java/org/enchant/navigation/AppStartupState.kt`

**Package:** `org.enchant.navigation`

**Single responsibility:** Define all possible pre-MainActivity states that the app can be in. The `AppStartupRouter` uses this to determine which Activity to launch.

**Imports required:**

```kotlin
import android.content.Context
import android.content.Intent
```

**Structure:**

```kotlin
sealed interface AppStartupState {

    /** User must enter their lock screen passphrase/PIN. */
    data object LockScreen : AppStartupState

    /** User must complete registration (phone number, verification code, etc.). */
    data object Registration : AppStartupState

    /** User must create a new Signal PIN (first time). */
    data object PinCreate : AppStartupState

    /** User must restore their PIN from SVR. */
    data object PinRestore : AppStartupState

    /** User must create their profile name/avatar. */
    data object ProfileCreate : AppStartupState

    /** Device-to-device transfer is in progress (old device). */
    data object TransferOldDevice : AppStartupState

    /** User must choose between transfer and backup restore. */
    data object TransferOrRestore : AppStartupState

    /** User is locked out during number change. */
    data object ChangeNumberLock : AppStartupState

    /** Normal app state — proceed to MainActivity. */
    data object Normal : AppStartupState
}
```

---

### S2: AppStartupRouter.kt

**Full path:** `app/src/main/java/org/enchant/navigation/AppStartupRouter.kt`

**Package:** `org.enchant.navigation`

**Single responsibility:** Determine the current `AppStartupState` from app preferences/data, and route to the correct Activity. Implemented as a singleton (or injectable) that `SplashActivity`/`BaseActivity` calls before setting content.

**Imports required:**

```kotlin
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
```

**Structure:**

```kotlin
object AppStartupRouter {

    fun getStartupState(context: Context): AppStartupState {
        return when {
            !AppDependencies.keyStore.hasPassphrase() -> AppStartupState.LockScreen
            !AppDependencies.registrationState.isRegistered() -> AppStartupState.Registration
            !AppDependencies.pinState.isPinSet() && AppDependencies.pinState.isPinRequired() ->
                AppStartupState.PinCreate
            AppDependencies.pinState.isPinRestoreRequired() -> AppStartupState.PinRestore
            !AppDependencies.profileState.isProfileCreated() -> AppStartupState.ProfileCreate
            AppDependencies.transferState.isTransferOngoing() -> AppStartupState.TransferOldDevice
            AppDependencies.restoreState.isRestoreRequired() -> AppStartupState.TransferOrRestore
            AppDependencies.changeNumberState.isLocked() -> AppStartupState.ChangeNumberLock
            else -> AppStartupState.Normal
        }
    }

    fun createIntent(context: Context, state: AppStartupState): Intent {
        return when (state) {
            AppStartupState.LockScreen -> Intent(context, LockScreenActivity::class.java)
            AppStartupState.Registration -> Intent(context, RegistrationActivity::class.java)
            AppStartupState.PinCreate -> Intent(context, CreatePinActivity::class.java)
            AppStartupState.PinRestore -> Intent(context, PinRestoreActivity::class.java)
            AppStartupState.ProfileCreate -> Intent(context, CreateProfileActivity::class.java)
            AppStartupState.TransferOldDevice -> Intent(context, OldDeviceTransferActivity::class.java)
            AppStartupState.TransferOrRestore -> Intent(context, RestoreActivity::class.java)
            AppStartupState.ChangeNumberLock -> Intent(context, ChangeNumberLockActivity::class.java)
            AppStartupState.Normal -> Intent(context, MainActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }

    /**
     * Called from [SplashActivity] or the app's launcher Activity.
     * Routes immediately without intermediate UI.
     */
    fun route(context: Context) {
        val state = getStartupState(context)
        if (state != AppStartupState.Normal) {
            context.startActivity(createIntent(context, state))
            (context as? Activity)?.finish()
        }
    }
}

/**
 * Base activity for all pre-main flows. Ensures that after completing
 * the pre-condition, the user is rechecked (in case multiple pre-conditions exist).
 */
abstract class BasePreMainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = AppStartupRouter.getStartupState(this)
        if (state == AppStartupState.Normal) {
            // All pre-conditions satisfied — proceed to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        // Subclass handles its own UI
    }
}
```

**Key implementation details:**
- `getStartupState()` evaluates conditions in priority order — lock screen → registration → PIN → profile → transfer → restore → normal.
- `createIntent()` maps each state to the correct Activity with `FLAG_ACTIVITY_NEW_TASK or CLEAR_TASK` to prevent back-stack escape.
- `route()` is the entry point for the launcher Activity — redirects immediately if not in NORMAL state.
- `BasePreMainActivity` is an optional base class for each pre-main Activity that re-checks startup state on creation.

**Tests to write:**
- `getStartupState` returns `LockScreen` when no passphrase is set
- `getStartupState` returns `Registration` when unregistered
- `getStartupState` returns `PinCreate` when PIN not set but required
- `getStartupState` returns `PinRestore` when PIN restore is required
- `getStartupState` returns `Normal` when all conditions satisfied
- `createIntent` creates the correct Intent for each state
- `route` finishes source Activity when redirecting

---

## 11. Phase D: Deep Link Handling

> **Why:** The app must handle inbound URLs and intents: opening conversations from notifications, joining group links, accepting call links, resolving proxy settings, processing Signal.me username links, handling Stripe return URLs, and initiating quick restore from backup.

### D1: DeepLinkHandler.kt

**Full path:** `app/src/main/java/org/enchant/navigation/DeepLinkHandler.kt`

**Package:** `org.enchant.navigation`

**Single responsibility:** Parse incoming intents and convert them to `MainNavigationDetailLocation` or `AppStartupState` navigation requests. Called from `MainActivity.onCreate` and `onNewIntent` before the composable tree is set up.

**Imports required:**

```kotlin
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationListLocation
import org.enchant.MainNavigationViewModel
```

**Structure:**

```kotlin
object DeepLinkHandler {

    /**
     * Handle a deep link intent. Returns a [DeepLinkResult] that the caller
     * (MainActivity) uses to set [MainNavigationViewModel.earlyNavigation*Requested].
     */
    fun handleIntent(intent: Intent): DeepLinkResult? {
        return when {
            isConversationIntent(intent) -> handleConversationIntent(intent)
            isGroupLinkIntent(intent) -> handleGroupLinkIntent(intent)
            isCallLinkIntent(intent) -> handleCallLinkIntent(intent)
            isProxyIntent(intent) -> handleProxyIntent(intent)
            isSignalMeIntent(intent) -> handleSignalMeIntent(intent)
            isDonateReturnIntent(intent) -> handleDonateReturnIntent(intent)
            isQuickRestoreIntent(intent) -> handleQuickRestoreIntent(intent)
            else -> null
        }
    }

    /**
     * Opens a specific conversation from notification tap or share intent.
     */
    private fun handleConversationIntent(intent: Intent): DeepLinkResult {
        val conversationArgs = ConversationArgs.fromIntent(intent)
        return DeepLinkResult.DetailLocation(
            MainNavigationDetailLocation.Conversation(conversationArgs)
        )
    }

    /**
     * Opens a group invite link (sgnl://group/... or https://signal.group/...).
     */
    private fun handleGroupLinkIntent(intent: Intent): DeepLinkResult {
        val uri = intent.data ?: return DeepLinkResult.None
        val groupLink = GroupLink.fromUri(uri)
        return if (groupLink != null) {
            DeepLinkResult.DetailLocation(
                MainNavigationDetailLocation.GroupLinkJoin(groupLink)
            )
        } else {
            DeepLinkResult.None
        }
    }

    /**
     * Opens a call link (sgnl://callLink/...).
     */
    private fun handleCallLinkIntent(intent: Intent): DeepLinkResult {
        val uri = intent.data ?: return DeepLinkResult.None
        val roomId = CallLinkRoomId.fromUri(uri)
        return DeepLinkResult.DetailLocation(
            MainNavigationDetailLocation.CallLinkDetails(roomId)
        )
    }

    /**
     * Resolves a proxy setting from environment or intent extra.
     */
    private fun handleProxyIntent(intent: Intent): DeepLinkResult {
        val proxyUrl = intent.getStringExtra("proxy_url")
            ?: return DeepLinkResult.None
        return DeepLinkResult.ProxySettings(proxyUrl)
    }

    /**
     * Opens a Signal.me username profile link.
     */
    private fun handleSignalMeIntent(intent: Intent): DeepLinkResult {
        val uri = intent.data ?: return DeepLinkResult.None
        val username = SignalMeUri.parseUsername(uri)
            ?: return DeepLinkResult.None
        return DeepLinkResult.DetailLocation(
            MainNavigationDetailLocation.UserProfile(username)
        )
    }

    /**
     * Handles Stripe SCA return URL after donation.
     */
    private fun handleDonateReturnIntent(intent: Intent): DeepLinkResult {
        return DeepLinkResult.ListLocation(MainNavigationListLocation.SETTINGS)
    }

    /**
     * Initiates quick restore from a backup file intent.
     */
    private fun handleQuickRestoreIntent(intent: Intent): DeepLinkResult {
        return DeepLinkResult.StartupOverride(AppStartupState.TransferOldDevice)
    }

    private fun isConversationIntent(intent: Intent): Boolean =
        ConversationIntents.isConversationIntent(intent)

    private fun isGroupLinkIntent(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        return uri.scheme in GROUP_LINK_SCHEMES && uri.host in GROUP_LINK_HOSTS
    }

    private fun isCallLinkIntent(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        return uri.scheme == "sgnl" && uri.host == "callLink"
    }

    private fun isProxyIntent(intent: Intent): Boolean =
        intent.hasExtra("proxy_url")

    private fun isSignalMeIntent(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        return uri.scheme in SIGNAL_ME_SCHEMES && uri.host in SIGNAL_ME_HOSTS
    }

    private fun isDonateReturnIntent(intent: Intent): Boolean =
        intent.action == "donate_return" || intent.data?.toString()?.contains("donate") == true

    private fun isQuickRestoreIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_VIEW && intent.type == "application/octet-stream"

    private companion object {
        private val GROUP_LINK_SCHEMES = setOf("sgnl", "https")
        private val GROUP_LINK_HOSTS = setOf("group", "signal.group")
        private val SIGNAL_ME_SCHEMES = setOf("sgnl", "https")
        private val SIGNAL_ME_HOSTS = setOf("signal.me", "signal.me")
    }
}

/**
 * Sealed result type for deep link handling.
 * The caller uses this to set [MainNavigationViewModel] early navigation fields.
 */
sealed interface DeepLinkResult {
    /** Navigate to a tab. */
    data class ListLocation(val location: MainNavigationListLocation) : DeepLinkResult

    /** Navigate to a detail destination. */
    data class DetailLocation(val location: MainNavigationDetailLocation) : DeepLinkResult

    /** Override the startup routing state. */
    data class StartupOverride(val state: AppStartupState) : DeepLinkResult

    /** Set a proxy URL. */
    data class ProxySettings(val url: String) : DeepLinkResult

    /** No action needed. */
    data object None : DeepLinkResult
}
```

**Integration with MainActivity:**

```kotlin
// In MainActivity.onCreate:
private fun handleDeepLinkIntent(intent: Intent?) {
    val result = intent?.let { DeepLinkHandler.handleIntent(it) } ?: return
    when (result) {
        is DeepLinkResult.DetailLocation -> {
            mainNavigationViewModel.goToDetail(result.location)
            // If navigator isn't wired yet, this sets earlyNavigationDetailLocationRequested
        }
        is DeepLinkResult.ListLocation -> {
            mainNavigationViewModel.goTo(result.location)
        }
        is DeepLinkResult.StartupOverride -> {
            AppStartupRouter.route(this)
        }
        is DeepLinkResult.ProxySettings -> {
            AppDependencies.proxyManager.setProxy(result.url)
        }
        is DeepLinkResult.None -> { /* no-op */ }
    }
}
```

**Tests to write:**
- `handleIntent` with conversation intent returns `DeepLinkResult.DetailLocation`
- `handleIntent` with group link URI returns `DeepLinkResult.DetailLocation`
- `handleIntent` with call link URI returns `DeepLinkResult.DetailLocation`
- `handleIntent` with proxy extra returns `DeepLinkResult.ProxySettings`
- `handleIntent` with unknown intent returns `null`
- `handleConversationIntent` correctly extracts `ConversationArgs`
- Each `is*Intent` helper returns the correct boolean

---

## 12. Phase F: Fragment Integration (Compose + Fragments)

> **Why:** Despite migrating to Compose, some screens remain as Fragments (legacy conversation list, media viewer, etc.). Enchant must support embedding Fragments within Compose seamlessly, and embedding Nav3 within a Fragment for gradual migration.

### F1: ComposeFragment.kt (Enhanced)

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/compose/ComposeFragment.kt`

**Package:** `org.enchant.core.ui.compose`

**Single responsibility:** Abstract base Fragment that hosts Compose content. Reduces boilerplate for Fragments that want to use Compose.

**Imports required:**

```kotlin
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
```

**Structure:**

```kotlin
abstract class ComposeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setContent {
            FragmentContent()
        }
    }

    @Composable
    abstract fun FragmentContent()
}
```

**Tests to write:**
- `ComposeFragment` creates and returns a `ComposeView`
- `FragmentContent` is called during composition

---

### F2: AndroidFragment.kt (Embed Fragment in Compose)

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/compose/AndroidFragment.kt`

**Package:** `org.enchant.core.ui.compose`

**Single responsibility:** A Compose composable that embeds a legacy Fragment inside a Compose hierarchy. Preserves Fragment state across recomposition using `key()`.

**Imports required:**

```kotlin
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
```

**Structure:**

```kotlin
/**
 * Embeds a [Fragment] inside a Compose tree.
 *
 * @param fragmentManager the [FragmentManager] to use (typically childFragmentManager)
 * @param containerId a unique ID for the FragmentContainerView. Must be stable across recomposition.
 * @param fragmentTag a unique tag for the Fragment. Must be stable across recomposition.
 * @param fragmentClass the class of the Fragment to embed.
 * @param fragmentArgs optional arguments to pass to the Fragment.
 * @param onFragmentCreated called after the Fragment is attached, for wiring callbacks.
 */
@Composable
fun <T : Fragment> AndroidFragment(
    fragmentManager: FragmentManager,
    containerId: Int,
    fragmentTag: String,
    fragmentClass: Class<T>,
    fragmentArgs: Bundle? = null,
    onFragmentCreated: (T) -> Unit = {},
    modifier: Modifier = Modifier
) {
    key(containerId, fragmentTag) {
        AndroidView(
            factory = { context ->
                FragmentContainerView(context).also { containerView ->
                    containerView.id = containerId
                    containerView.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    containerView.tag = fragmentTag
                }
            },
            modifier = modifier
        )
    }

    DisposableEffect(containerId, fragmentTag) {
        val fragment = fragmentManager.findFragmentByTag(fragmentTag)
        val isAlreadyAdded = fragment != null

        if (!isAlreadyAdded) {
            val transaction = fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(containerId, fragmentClass, fragmentArgs, fragmentTag)
            transaction.commit()
        }

        onDispose {
            if (isAlreadyAdded) {
                fragmentManager.beginTransaction()
                    .remove(fragmentManager.findFragmentByTag(fragmentTag)!!)
                    .commit()
            }
        }
    }

    // Re-find fragment after attachment and wire callbacks
    LaunchedEffect(containerId, fragmentTag) {
        while (fragmentManager.findFragmentByTag(fragmentTag) == null) {
            delay(16) // wait for next frame
        }
        @Suppress("UNCHECKED_CAST")
        val attachedFragment = fragmentManager.findFragmentByTag(fragmentTag) as T
        onFragmentCreated(attachedFragment)
    }
}
```

**Usage example:**

```kotlin
@Composable
fun ConversationListWrapper(fragmentManager: FragmentManager) {
    AndroidFragment(
        fragmentManager = fragmentManager,
        containerId = R.id.conversation_list_container,
        fragmentTag = "conversation_list",
        fragmentClass = ConversationListFragment::class.java,
        onFragmentCreated = { fragment ->
            fragment.setOnConversationClickListener { args ->
                // react to fragment callback
            }
        }
    )
}
```

**Tests to write:**
- `AndroidFragment` renders a `FragmentContainerView`
- Fragment is added to FragmentManager with correct tag
- Fragment is removed on dispose
- `onFragmentCreated` callback fires after fragment is attached
- `fragmentArgs` are passed correctly to the Fragment
- `key(containerId, fragmentTag)` prevents unnecessary recreation

---

### F3: Nav3InFragment.kt (Embed Nav3 Inside a Fragment)

**Full path:** `core/ui/src/main/java/org/enchant/core/ui/compose/Nav3InFragment.kt`

**Package:** `org.enchant.core.ui.compose`

**Single responsibility:** Demonstrate how to embed a Navigation3 `NavDisplay` inside a Fragment for gradual migration. The Fragment hosts a ComposeView whose content is a `NavDisplay` with `rememberNavBackStack`.

**Structure (pattern, not a separate class):**

```kotlin
class FeatureFragment : ComposeFragment() {
    @Composable
    override fun FragmentContent() {
        val backStack = rememberNavBackStack(FeatureNavKey.FirstScreen)

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.safePop() },
            transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
            popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
            entryProvider = entryProvider {
                entry<FeatureNavKey.FirstScreen> {
                    FirstScreen(
                        onNavigate = { backStack.add(FeatureNavKey.SecondScreen) }
                    )
                }
                entry<FeatureNavKey.SecondScreen> {
                    SecondScreen(
                        onDone = { backStack.clear() }
                    )
                }
            }
        )
    }
}
```

**Tests to write:**
- Fragment renders NavDisplay with first screen
- Navigating within NavDisplay works in Fragment context
- Fragment survives configuration change with state preservation

---

## Summary: File Assignment Table

| # | File (in enchant) | What it contains | Depends on |
|---|---|---|---|
| — | `gradle/libs.versions.toml` | add `navigation3` (3 libs) + `lifecycle-viewmodel-navigation3` version refs | — |
| — | `core/navigation/build.gradle.kts` | add 4 implementation deps: nav3 runtime, nav3 ui, nav3 event compose, lifecycle-viewmodel-nav3 | — |
| P1-01 | `core/ui/navigation/TransitionSpecs.kt` | `object TransitionSpecs` with `HorizontalSlide` + `VerticalSlide`, 3 specs each | nav3-core |
| P1-02 | `core/ui/navigation/ResultEventBus.kt` | `class ResultEventBus` + `object LocalResultEventBus` | compose, coroutines |
| P1-03 | `core/ui/navigation/ResultEffect.kt` | `inline fun <reified T> ResultEffect()` | P1-02 |
| P1-04 | `core/ui/navigation/BottomSheetSceneStrategy.kt` | `class BottomSheetSceneStrategy`, `class BottomSheetScene`, `val LocalBottomSheetDismiss` | nav3-scene, material3 |
| P1-05 | `core/ui/navigation/NavBackStackExtensions.kt` | `fun navigateOrPopTo()`, `fun safePop()`, `private fun popTo()` | nav3-core |
| — | `core/ui/compose/ComposeFragment.kt` | `abstract class ComposeFragment : Fragment()` with `abstract fun FragmentContent()` | fragment-compose |
| P2-01 | `feature/*/<Feature>NavKey.kt` | `sealed interface : NavKey` (+ `@Parcelize` on complex routes) | nav3-core, serialization, parcelize |
| P2-02 | `feature/*/<Feature>NavDisplay.kt` | `@Composable fun <Feature>NavDisplay()` — DSL or lambda entryProvider | P1-01, P1-04, P1-05, nav3 |
| P2-03 | `feature/*/<Feature>NavBackStackExtensions.kt` | Feature-specific `goTo*()` and `pop()` extensions | P1-05 |
| P3-01 | `feature/*/RegistrationFlowEvent.kt` | `sealed interface RegistrationFlowEvent` | — |
| P3-02 | `feature/*/screens/util/EmitterExtensions.kt` | `fun navigateTo()`, `fun navigateBack()` on event lambdas | P3-01 |
| P3-03 | `feature/*/RegistrationNavigation.kt` | `sealed interface RegistrationNavKey` + `fun RegistrationNavHost()` | P1-01, P1-02, P1-03, P1-04, P3-01, P3-02 |
| M1 | `app/MainNavigationDetailLocation.kt` | `sealed interface MainNavigationDetailLocation` — all detail pane destinations | serialization, parcelize |
| M2 | `app/MainNavigationListLocation.kt` | `enum MainNavigationListLocation` — 4 tabs with labels/icons | R class |
| M3 | `app/MainNavigationViewModel.kt` | ViewModel with per-tab detail stacks, `lockPaneToSecondary`, `wrapNavigator()` | M1, M2, M4 |
| M4 | `app/MainNavigationRouter.kt` | `interface MainNavigationRouter` — `goTo()`, `goToDetail()`, `setFocusedPane()` | M1, M2 |
| M5 | `app/window/AppScaffoldNavigator.kt` | wraps `ThreePaneScaffoldNavigator` with `NavigationState` + open for subclassing | adaptive, material3 |
| M5a | `app/window/AppScaffoldAnimationState.kt` | per-pane alpha/offset/scale/cornerRadius animated properties | compose-animation |
| M5b | `app/window/NavigationType.kt` | `enum NavigationType` with `rememberNavigationType()` (BAR vs RAIL) | M9, M6 |
| M5c | `app/window/AppPaneDragHandle.kt` | draggable pane divider pill for adaptive scaffold | adaptive, material3 |
| M6 | `app/window/AppScaffold.kt` | generic `AppScaffold` with lambda slots, `ListAndNavigation` with `NavigationType` | M5, M5a, M5b, M5c |
| M7 | `app/main/MainNavigationBar.kt` + `MainNavigationRail.kt` | bar/rail with badge drawing, Lottie animations, compact mode | M2 |
| M8 | `app/main/MainActivityComponents.kt` | `rememberDetailNavHostController`, `navigateToDetailLocation`, `DetailsScreenNavHost` | M1, M3 |
| M9 | `app/util/WindowSizeClassExtensions.kt` | `WindowBreakpoint` detection + `isSplitPane` logic | adaptive |
| S1 | `app/navigation/AppStartupState.kt` | sealed interface of 9 pre-main states | — |
| S2 | `app/navigation/AppStartupRouter.kt` | state machine: `getStartupState()` + `route()` + `createIntent()` | S1 |
| D1 | `app/navigation/DeepLinkHandler.kt` | parse 7 deep link types → `DeepLinkResult` | M1, S1 |
| F1 | `core/ui/compose/ComposeFragment.kt` | abstract Fragment with `@Composable FragmentContent()` | fragment-compose |
| F2 | `core/ui/compose/AndroidFragment.kt` | `@Composable fun AndroidFragment` — embed Fragment in Compose tree | fragment-compose |
| F3 | `core/ui/compose/Nav3InFragment.kt` | pattern: NavDisplay inside ComposeFragment | P1-01, P2-01, P2-02 |
| — | `core/navigation/NavRoute.kt` | DELETE | — |
| — | `core/navigation/NavHost.kt` | DELETE | — |

## Build Order

1. **Dependencies** — Update `libs.versions.toml` and `core/navigation/build.gradle.kts` with navigation3 and lifecycle-viewmodel-navigation3
2. **Core infrastructure** (P1-01 through P1-05) — no feature dependencies
3. **Startup routing** (S1, S2) — needed before any Activity launches
4. **ComposeFragment base** (F1) — if using Fragment embedding
5. **AndroidFragment composable** (F2) — for embedding legacy Fragments in Compose
6. **One simple feature NavKey + NavDisplay** (P2-01, P2-02) — validates core infra works
7. **Main navigation shell** — M1, M2, M4, M3 (in that order) — establishes the top-level structure before adding features
8. **Window and scaffold** — M9, M5, M5a, M5b, M5c, M6, M7, M8 — the adaptive layout, animations, navigation bar/rail, and drag handle
9. **Deep link handling** (D1) — needed by MainActivity
10. **Remaining features** (P2-nnn)
11. **Complex flows** (P3-nnn) — only after core infra is solid
12. **MainActivity integration** — wire everything together in MainActivity (M10)
13. **Nav3InFragment pattern** (F3) — for gradual migration of Fragment-based features
14. **Delete old NavRoute.kt + NavHost.kt** — only after ALL features have migrated

**Phase M key enhancement:** The reference app locks to the secondary pane on tab switch (`lockPaneToSecondary = true`), discarding detail state. enchant-native's `MainNavigationViewModel` maintains **independent detail stacks per tab**, preserving conversation state when switching between tabs. This is the central design decision that makes the architecture superior for a real messenger use case.

**New phases summary:** Phases S (Startup Routing), D (Deep Link Handling), and F (Fragment Integration) bring enchant-native to parity with Signal's navigation architecture and add the `lockPaneToSecondary` UX guard, `NavigationType` selection, `AppPaneDragHandle`, and full `AppScaffoldAnimationState` that were missing from the original rewrite spec.
