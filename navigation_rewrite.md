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
├── window/
│   ├── AppScaffoldNavigator.kt                           M5
│   └── AppScaffold.kt                                     M6
├── main/
│   ├── MainNavigationBar.kt                              M7
│   ├── MainNavigationRail.kt                             M7
│   └── MainActivityComponents.kt                         M8
└── util/
    └── WindowSizeClassExtensions.kt                       M9

core/ui/src/main/java/org/enchant/core/ui/             (shared UI + adaptive)
├── WindowSizeClassExtensions.kt                          M9
├── compose/
│   └── ComposeFragment.kt
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
```

---

## 2. Phase 0: Build Dependencies

### File: `gradle/libs.versions.toml` (add these entries)

```toml
[versions]
navigation3 = "2.0.0-rc01"

[libraries]
navigation3-runtime = { module = "androidx.navigation3:navigation-runtime", version.ref = "navigation3" }
navigation3-ui = { module = "androidx.navigation3:navigation-ui", version.ref = "navigation3" }
navigation3-event-compose = { module = "androidx.navigation3:navigation-event-compose", version.ref = "navigation3" }
lifecycle-viewmodel-navigation3 = { module = "androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }

[versions]
# also need these version references if not already present:
lifecycle = "2.8.7"
```

### File: `core/navigation/build.gradle.kts` (add to dependencies block)

```kotlin
dependencies {
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.navigation3.event.compose)
    implementation(libs.lifecycleViewmodelNavigation3)
    // existing deps stay
}
```

### File: `settings.gradle.kts`

The core/navigation module must already be included. Do not change.

---

## 3. Phase 1: Core Infrastructure (core/navigation)

---

### P1-01: TransitionSpecs.kt

**Full path:** `core/navigation/src/main/java/org/enchant/navigation/TransitionSpecs.kt`

**Package:** `org.enchant.navigation`

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

**Full path:** `core/navigation/src/main/java/org/enchant/navigation/ResultEventBus.kt`

**Package:** `org.enchant.navigation`

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

**Full path:** `core/navigation/src/main/java/org/enchant/navigation/ResultEffect.kt`

**Package:** `org.enchant.navigation`

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

**Full path:** `core/navigation/src/main/java/org/enchant/navigation/BottomSheetSceneStrategy.kt`

**Package:** `org.enchant.navigation`

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
 * NOTE: If your app has a custom `BottomSheets.BottomSheet` composable wrapper
 * (for themed bottom sheets with shared styling), replace `ModalBottomSheet` above
 * with that wrapper and update the import. This is optional — raw `ModalBottomSheet` works.
 */

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

**Full path:** `core/navigation/src/main/java/org/enchant/navigation/NavBackStackExtensions.kt`

**Package:** `org.enchant.navigation`

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
import kotlinx.serialization.Serializable
```

**Template (simple — no Parcelable):**

```kotlin
@Serializable
sealed interface FeatureNavKey : NavKey {

    @Serializable
    data object FirstScreen : FeatureNavKey

    @Serializable
    data object SecondScreen : FeatureNavKey

    @Serializable
    data class DetailScreen(
        val itemId: String,
        val parentRoute: FeatureNavKey? = null
    ) : FeatureNavKey

    @Serializable
    data class SettingsSheet(val initialTab: String = "general") : FeatureNavKey
}
```

**Template (complex — with Parcelable for route arguments that carry complex data):**

```kotlin
@Serializable
@Parcelize
sealed interface ComplexFeatureNavKey : NavKey, Parcelable {

    @Serializable
    data object Home : ComplexFeatureNavKey

    @Serializable
    data class DetailScreen(
        val itemId: String
    ) : ComplexFeatureNavKey

    @Serializable
    data class SearchResult(
        val query: String,
        val results: List<SearchItem>  // must be @Serializable
    ) : ComplexFeatureNavKey
}
```

**Rules:**
- `sealed interface` (not sealed class) so `data object` works
- Every variant is annotated `@Serializable`
- `data class` arguments must be `@Serializable` (String, Int, Long, Boolean, etc.)
- If ANY route `data class` carries complex arguments, the entire sealed interface should also be `@Parcelize` and extend `Parcelable`. This ensures safe argument passing through the nav3 stack.
- Forward references to routes are fine: `val nextRoute: FeatureNavKey`
- Routes can carry companion factories for complex construction logic
- `enum class : NavKey` works for simple cases with no arguments at all (no `@Serializable` needed for enums, but add it anyway for consistency)

**Tests to write:**

- Every route serializes and deserializes correctly
- `data object` routes compare equal
- `data class` routes with same arguments compare equal

---

### P2-02: <Feature>NavDisplay.kt Pattern

**Full path:** `feature/<feature>/src/main/java/org/enchant/<feature>/<Feature>NavDisplay.kt`

**Single responsibility:** The Compose `@Composable` function that creates a `NavDisplay` using the feature's NavKey. Owns the back stack (via `rememberNavBackStack` or ViewModel), sets up transitions, scene strategies, and maps each NavKey to its screen composable.

**Imports required:**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.enchant.navigation.TransitionSpecs
// feature-specific screen imports
```

**Simple form (no ViewModel, no result bus, no decorators):**

```kotlin
@Composable
fun FeatureNavDisplay(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(FeatureNavKey.FirstScreen)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.safePop() },
        modifier = modifier,
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
                    onDone = {
                        backStack.clear()
                        onComplete()
                    }
                )
            }
        }
    )
}
```

**With BottomSheetStrategy:**

```kotlin
@Composable
fun FeatureNavDisplay(
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(FeatureNavKey.FirstScreen)
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<FeatureNavKey>() }

    NavDisplay(
        backStack = backStack,
        sceneStrategy = bottomSheetStrategy,
        onBack = { backStack.safePop() },
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        entryProvider = entryProvider {
            entry<FeatureNavKey.FirstScreen> {
                FirstScreen(
                    onOpenSheet = { backStack.add(FeatureNavKey.SettingsSheet()) }
                )
            }

            entry<FeatureNavKey.SettingsSheet>(
                metadata = BottomSheetSceneStrategy.bottomSheet()
            ) {
                val dismiss = LocalBottomSheetDismiss.current
                SettingsSheetContent(onClose = { dismiss() })
            }
        }
    )
}
```

**With explicit entries + decorators (when ViewModel scoping needed):**

```kotlin
@Composable
fun FeatureNavDisplay(
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(FeatureNavKey.FirstScreen)

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<FeatureNavKey>(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<FeatureNavKey.FirstScreen> {
                val viewModel: FirstViewModel = viewModel()
                FirstScreen(viewModel = viewModel)
            }
        }
    )

    NavDisplay(
        entries = entries,
        onBack = { backStack.safePop() },
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec
    )
}
```

**Lambda entryProvider + lambda backStack (varargs initial stack):**

This is an alternative pattern where `entryProvider` is a lambda instead of the DSL,
and the initial back stack is built with array varargs (useful for conditional start screens):

```kotlin
class FeatureFragment : ComposeFragment() {
    @Composable
    override fun FragmentContent() {
        val initialStack = if (someCondition) {
            arrayOf(FeatureNavKey.Settings)
        } else {
            arrayOf(FeatureNavKey.FirstScreen)
        }
        val backStack = rememberNavBackStack(*initialStack)

        // Lambda form: entryProvider is { key: NavKey -> when(key) { ... } }
        // No DSL, no typed entries. Every entry is manually created via NavEntry(key) { ... }
        NavDisplay(
            backStack = backStack,
            entryProvider = { key ->
                when (key) {
                    FeatureNavKey.FirstScreen -> NavEntry(key) {
                        FirstScreen(onNavigate = { backStack.add(FeatureNavKey.SecondScreen) })
                    }
                    FeatureNavKey.SecondScreen -> NavEntry(key) {
                        SecondScreen(onBack = { backStack.safePop() })
                    }
                    FeatureNavKey.Settings -> NavEntry(key) {
                        SettingsScreen()
                    }
                    else -> error("Unknown key: $key")
                }
            }
        )
    }
}
```

Key differences from the DSL form:
- `entryProvider = { key -> when(key) { ... } }` is a plain lambda, not `entryProvider { ... }`
- Each entry is created manually: `NavEntry(key) { ... }`
- No generics on the entry — `key` is typed as `NavKey`
- The `else -> error(...)` branch is required since `when` is not exhaustive
- Back stack is created with `rememberNavBackStack(*arrayOf(...))` for conditional start

**Tests to write:**

- NavDisplay renders the first screen on initial composition
- Navigating to second screen renders second screen content
- Conditional start screen works (Settings vs FirstScreen based on condition)
- Pop returns to first screen
- Bottom sheet entry shows ModalBottomSheet
- Bottom sheet dismiss triggers onBack
- Varargs initial stack places all entries in correct order

### P2-03: <Feature>NavBackStackExtensions.kt Pattern

**Full path:** `feature/<feature>/src/main/java/org/enchant/<feature>/<Feature>NavBackStackExtensions.kt`

**Single responsibility:** Feature-specific extension functions on `NavBackStack<NavKey>` that encode the feature's navigation rules (e.g., "if Edit screen already exists, pop to it instead of pushing a new one").

**Imports required:**

```kotlin
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
```

**Template (from MediaSend example):**

```kotlin
internal fun NavBackStack<NavKey>.goToEdit() {
    if (contains(FeatureNavKey.Edit)) {
        popTo(FeatureNavKey.Edit)
    } else {
        add(FeatureNavKey.Edit)
    }
}

internal fun NavBackStack<NavKey>.goToConfirm() {
    if (contains(FeatureNavKey.Confirm)) {
        popTo(FeatureNavKey.Confirm)
    } else {
        add(FeatureNavKey.Confirm)
    }
}

internal fun NavBackStack<NavKey>.pop() {
    if (isNotEmpty()) {
        removeAt(size - 1)
    }
}

private fun NavBackStack<NavKey>.popTo(key: NavKey) {
    while (size > 1 && get(size - 1) != key) {
        removeAt(size - 1)
    }
}
```

**Tests to write:**

- `goToEdit` adds `Edit` key if not in stack
- `goToEdit` pops to existing `Edit` key if already in stack
- `pop` safely removes last element
- `pop` does nothing if stack is empty

---

## 5. Phase 3: ViewModel-Driven Complex Flows

For flows with >3 screens, conditional branching, state sharing, or saved instance restoration — hoist everything into a ViewModel.

---

### P3-01: RegistrationFlowEvent.kt (Full Flow Events)

**Full path:** `feature/registration/src/main/java/org/enchant/registration/RegistrationFlowEvent.kt`

**Package:** `org.enchant.registration`

**Single responsibility:** Define all events that can happen during a registration flow — both navigation events (NavigateToScreen, NavigateBack, ResetState) and domain events (SessionUpdated, E164Chosen, Registered, etc.). The ViewModel consumes these events and produces new state + back stack mutations.

**Imports:**

```kotlin
import org.enchant.core.model.AccountEntropyPool
```

**Structure:**

```kotlin
sealed interface RegistrationFlowEvent {
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
- `DebugLoggable` interface can be added for logging (define separately)
- `ResetState` is for irrecoverable error recovery
- `SessionUpdated` + `E164Chosen` are non-navigation events that still flow through the same event channel
- ViewModel exposes a single `onEvent(RegistrationFlowEvent)` entry point

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
import kotlinx.serialization.Serializable
import org.enchant.core.model.AccountEntropyPool
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

    @Serializable data class RemoteRestore(
        val aep: AccountEntropyPool
    ) : RegistrationNavKey

    @Serializable data object QuickRestoreQrScan : RegistrationNavKey

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
import org.enchant.navigation.ResultEffect
import org.enchant.navigation.TransitionSpecs
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

    // Provide ResultEventBus to all child entries
    CompositionLocalProvider(
        LocalResultEventBus provides viewModel.resultBus
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

**Full path:** `feature/registration/src/main/java/org/enchant/registration/QuickTransferNavDisplay.kt`

**Package:** `org.enchant.registration`

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
    }
}
```

---

## 6. Phase 4: Fragment-Based XML Graphs (If Needed)

If any screens still use Fragments, create XML nav graphs in `app/src/main/res/navigation/`.

**Single responsibility:** Define fragment destinations, typed arguments, and navigation actions with animations.

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

```kotlin
@file:JvmName("SafeNavigation")

package org.enchant.core.navigation

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
3. **Pane focus** — which pane (Secondary/Primary) currently has focus
4. **Full-screen mode** — whether a tab is in full-screen (no split) mode
5. **Megaphone state** — onboarding tips

**Imports required:**

```kotlin
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
```

**Structure:**

```kotlin
class MainNavigationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel(), MainNavigationRouter {

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

    private val _paneFocusRequests = MutableSharedFlow<ThreePaneScaffoldRole?>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val paneFocusRequests: SharedFlow<ThreePaneScaffoldRole?> = _paneFocusRequests.asSharedFlow()

    private val _fullScreenPane = MutableStateFlow(false)
    val fullScreenPane: StateFlow<Boolean> = _fullScreenPane.asStateFlow()

    private val _isSplitPane = MutableStateFlow(true)
    val isSplitPane: StateFlow<Boolean> = _isSplitPane.asStateFlow()

    var navigator: AppScaffoldNavigator<Any>? = null
        private set

    override fun goTo(location: MainNavigationListLocation) {
        viewModelScope.launch {
            _mainNavigationState.update { it.copy(currentListLocation = location) }
            _paneFocusRequests.tryEmit(ThreePaneScaffoldRole.Secondary)
            _isSplitPane.value = true
        }
    }

    override fun goToDetail(location: MainNavigationDetailLocation) {
        viewModelScope.launch {
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

            _paneFocusRequests.tryEmit(ThreePaneScaffoldRole.Primary)
        }
    }

    override fun setFocusedPane(role: ThreePaneScaffoldRole) {
        viewModelScope.launch {
            _paneFocusRequests.tryEmit(role)
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

    data class MainNavigationState(
        val currentListLocation: MainNavigationListLocation = MainNavigationListLocation.CHATS,
        val chatsCount: Int = 0,
        val callsCount: Int = 0,
        val storiesCount: Int = 0,
        val storyFailure: Boolean = false,
        val isStoriesFeatureEnabled: Boolean = true
    )
}
```

**Key implementation details:**
- **Per-tab detail stacks** — the core enhancement. Each of the 4 tabs has its own `MutableStateFlow<List<MainNavigationDetailLocation>>`. When switching tabs, the current stack is preserved and restored when switching back.
- `goToDetail` navigates within the current tab's stack. If the destination is a content root (Empty, Settings), the stack is reset. Otherwise, the destination replaces everything above it (if the same screen type exists) or is appended.
- `navigateInCurrentTab` and `goBackInCurrentTab` are called by the per-tab `NavHostController` when it navigates or pops.
- `paneFocusRequests` is a `SharedFlow` that AppScaffold consumes to animate pane focus changes.
- `navigator` is set by `MainActivity` after the `AppScaffoldNavigator` is created — used to delegate back navigation to the ThreePaneScaffoldNavigator.

**Tests to write:**

- `goTo(CHATS)` updates `currentListLocation` to CHATS and focuses Secondary pane
- `goToDetail(Conversation)` adds to the current tab's detail stack
- Switching tabs preserves the previous tab's detail stack
- `goToDetail(Empty)` clears the current tab's detail stack
- `goBackInCurrentTab` removes the last entry and returns true; returns false when stack has only Empty
- `navigateInCurrentTab` replaces same-type destinations and appends new ones
- All 4 detail stacks initialize to `[Empty]`

---

### M4: MainNavigationRouter.kt

**Full path:** `app/src/main/java/org/enchant/MainNavigationRouter.kt`

**Package:** `org.enchant`

**Single responsibility:** Interface that `MainNavigationViewModel` implements and `MainActivityComponents` uses to request navigation. Abstracts `goToList()`, `goToDetail()`, and `setFocusedPane()` so the UI layer never talks directly to the ViewModel.

**Imports required:**

```kotlin
import androidx.navigation3.adaptiveThreePane.model.ThreePaneScaffoldRole
```

**Structure:**

```kotlin
interface MainNavigationRouter {
    fun goTo(location: MainNavigationListLocation)
    fun goToDetail(location: MainNavigationDetailLocation)
    fun setFocusedPane(role: ThreePaneScaffoldRole)
}
```

**Usage in MainActivityComponents.kt:**

```kotlin
@Composable
fun MainActivityComponents(
    mainNavigationViewModel: MainNavigationViewModel,
    modifier: Modifier = Modifier
) {
    val router: MainNavigationRouter = mainNavigationViewModel
    // ... use router.goTo(), router.goToDetail(), router.setFocusedPane()
}
```

**Tests to write:**

- `MainNavigationViewModel` implements `MainNavigationRouter`
- Calling `goTo(CHATS)` twice does not duplicate or cause issues
- Calling `goToDetail(location)` with `isContentRoot = true` resets the stack

---

### M5: AppScaffoldNavigator.kt

**Full path:** `app/src/main/java/org/enchant/window/AppScaffoldNavigator.kt`

**Package:** `org.enchant`

**Single responsibility:** Wrap Material3's `ThreePaneScaffoldNavigator` with animation state coordination and custom back handling. The `state: NavigationState` property drives AnimatedContent transitions in the UI layer.

**Imports required:**

```kotlin
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose RememberInComposition
import org.enchant.core.ui.util.LocalResources
import org.enchant.core.ui.util.currentWindowAdaptiveInfo
import org.enchant.core.ui.util.WindowSizeClass
import org.enchant.core.ui.util.rememberIsSplitPane

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

    fun navigateTo(pane: ThreePaneScaffoldRole, contentKey: T?) {
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
- `state` drives `AnimatedContent` transitions in `AppScaffold` (ENTER/EXIT/SEEK/RELEASE)
- `navigateTo(pane, contentKey)` sets state to ENTER before delegating
- `seekBack` maps fraction values to NavigationState: 0→ENTER, 0-0.5→SEEK, 0.5+→RELEASE
- `rememberAppScaffoldNavigator` is the public factory — it wraps the internal delegate creation

**Tests to write:**

- `navigateTo` sets state to ENTER before delegating
- `navigateBack` that returns true sets state to EXIT
- `seekBack` with fraction=0 sets state to ENTER
- `seekBack` with fraction=0.3 sets state to SEEK
- `seekBack` with fraction=0.7 sets state to RELEASE
- Delegate's `navigateTo` is called with correct pane and contentKey

---

### M6: AppScaffold.kt

**Full path:** `app/src/main/java/org/enchant/window/AppScaffold.kt`

**Package:** `org.enchant.window`

**Single responsibility:** Top-level adaptive scaffold that branches into either `SinglePaneAppScaffold` (phone, API < 33) or `AdaptiveAppScaffold` (tablet/foldable). Renders the correct layout based on `navigator.scaffoldDirective.maxHorizontalPartitions` and Android version.

**Imports required:**

```kotlin
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PaneExpandedState
import androidx.compose.material3.PaneExpansionState
import androidx.compose.material3.adaptive.animation.AnimatedPane
import androidx.compose.material3.adaptive.animation.calculatePaneExpansionState
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.enchant.MainActivity
import org.enchant.MainNavigationViewModel
import org.enchant.main.ListAndNavigation
import org.enchant.main.MainNavigationBar
import org.enchant.main.MainNavigationRail
import org.enchant.window.AppPaneDragHandle
```

**Structure:**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    mainNavigationViewModel: MainNavigationViewModel,
    navigator: AppScaffoldNavigator<Any>,
    chatsNavHostController: NavHostController,
    callsNavHostController: NavHostController,
    storiesNavHostController: NavHostController,
    modifier: Modifier = Modifier,
    isForceSinglePane: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val paneExpansionState = calculatePaneExpansionState()
    val isSplitPane = mainNavigationViewModel.isSplitPane.value

    val useSimpleScaffold = isForceSinglePane ||
        (navigator.scaffoldDirective.maxHorizontalPartitions == 1 && Build.VERSION.SDK_INT < 33)

    if (useSimpleScaffold) {
        SinglePaneAppScaffold(
            navigator = navigator,
            mainNavigationViewModel = mainNavigationViewModel,
            chatsNavHostController = chatsNavHostController,
            callsNavHostController = callsNavHostController,
            storiesNavHostController = storiesNavHostController,
            paneExpansionState = paneExpansionState,
            modifier = modifier
        )
    } else {
        AdaptiveAppScaffold(
            navigator = navigator,
            mainNavigationViewModel = mainNavigationViewModel,
            chatsNavHostController = chatsNavHostController,
            callsNavHostController = callsNavHostController,
            storiesNavHostController = storiesNavHostController,
            paneExpansionState = paneExpansionState,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SinglePaneAppScaffold(
    navigator: AppScaffoldNavigator<Any>,
    mainNavigationViewModel: MainNavigationViewModel,
    chatsNavHostController: NavHostController,
    callsNavHostController: NavHostController,
    storiesNavHostController: NavHostController,
    paneExpansionState: PaneExpansionState,
    modifier: Modifier = Modifier
) {
    val showDetail = navigator.scaffoldValue.primary == PaneAdaptedValue.Expanded

    BackHandler(enabled = navigator.canNavigateBack()) {
        val handled = mainNavigationViewModel.goBackInCurrentTab()
        if (!handled) {
            scope.launch { navigator.navigateBack() }
        }
    }

    AnimatedContent(
        targetState = showDetail,
        modifier = modifier
    ) { isDetail ->
        if (isDetail) {
            primaryContent(
                mainNavigationViewModel = mainNavigationViewModel,
                chatsNavHostController = chatsNavHostController,
                callsNavHostController = callsNavHostController,
                storiesNavHostController = storiesNavHostController,
                paneExpansionState = paneExpansionState
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                secondaryContent(
                    mainNavigationViewModel = mainNavigationViewModel,
                    paneExpansionState = paneExpansionState
                )
                MainNavigationBar(
                    currentTab = mainNavigationViewModel.mainNavigationState.value.currentListLocation,
                    onTabSelected = { mainNavigationViewModel.goTo(it) },
                    chatsCount = mainNavigationViewModel.mainNavigationState.value.chatsCount,
                    callsCount = mainNavigationViewModel.mainNavigationState.value.callsCount,
                    storiesCount = mainNavigationViewModel.mainNavigationState.value.storiesCount
                )
            }
        }
    }
}

@Composable
private fun primaryContent(
    mainNavigationViewModel: MainNavigationViewModel,
    chatsNavHostController: NavHostController,
    callsNavHostController: NavHostController,
    storiesNavHostController: NavHostController,
    paneExpansionState: PaneExpansionState
) {
    when (mainNavigationViewModel.mainNavigationState.value.currentListLocation) {
        MainNavigationListLocation.CHATS, MainNavigationListLocation.ARCHIVE -> {
            DetailsScreenNavHost(
                navHostController = chatsNavHostController,
                detailLocation = mainNavigationViewModel.chatsDetailStack.value.lastOrNull()
                    ?: MainNavigationDetailLocation.Empty
            )
        }
        MainNavigationListLocation.CALLS -> {
            DetailsScreenNavHost(
                navHostController = callsNavHostController,
                detailLocation = mainNavigationViewModel.callsDetailStack.value.lastOrNull()
                    ?: MainNavigationDetailLocation.Empty
            )
        }
        MainNavigationListLocation.STORIES -> {
            StoriesScreen()
        }
    }
}

@Composable
private fun secondaryContent(
    mainNavigationViewModel: MainNavigationViewModel,
    paneExpansionState: PaneExpansionState
) {
    Box(modifier = Modifier.weight(1f)) {
        ListAndNavigation(
            mainNavigationViewModel = mainNavigationViewModel,
            paneExpansionState = paneExpansionState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveAppScaffold(
    navigator: AppScaffoldNavigator<Any>,
    mainNavigationViewModel: MainNavigationViewModel,
    chatsNavHostController: NavHostController,
    callsNavHostController: NavHostController,
    storiesNavHostController: NavHostController,
    paneExpansionState: PaneExpansionState,
    modifier: Modifier = Modifier
) {
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                ListAndNavigation(
                    mainNavigationViewModel = mainNavigationViewModel,
                    paneExpansionState = paneExpansionState
                )
            }
        },
        detailPane = {
            AnimatedPane {
                primaryContent(
                    mainNavigationViewModel = mainNavigationViewModel,
                    chatsNavHostController = chatsNavHostController,
                    callsNavHostController = callsNavHostController,
                    storiesNavHostController = storiesNavHostController,
                    paneExpansionState = paneExpansionState
                )
            }
        },
        paneExpansionDragHandle = {
            AppPaneDragHandle(
                paneExpansionState = paneExpansionState,
                onDragEnd = { }
            )
        },
        paneExpansionState = paneExpansionState,
        modifier = modifier
    )
}
```

**Key implementation details:**
- `useSimpleScaffold` is true when `maxHorizontalPartitions == 1` (phone mode) OR Android SDK < 33 (no predictive back support)
- `AnimatedContent` handles the transition between list-only and detail-visible in phone mode
- `NavigableListDetailPaneScaffold` provides the full three-pane layout in tablet mode
- `BackHandler` checks if the current tab's detail stack can handle the back press; if not, delegates to `navigator.navigateBack()`
- `paneExpansionState` enables the draggable divider between list and detail panes
- `DetailsScreenNavHost` (defined in M8) renders the appropriate `NavHostController`'s content in the detail pane

**Tests to write:**

- `useSimpleScaffold` is true when `maxHorizontalPartitions == 1` and SDK < 33
- `useSimpleScaffold` is false when `maxHorizontalPartitions == 2` and SDK >= 33
- `SinglePaneAppScaffold` shows `MainNavigationBar` when `showDetail == false`
- `AdaptiveAppScaffold` uses `NavigableListDetailPaneScaffold` with correct navigator
- `BackHandler` calls `goBackInCurrentTab` first; only calls navigator.navigateBack when that returns false
- `primaryContent` renders correct NavHostController based on `currentListLocation`

---

### M7: MainNavigationBar.kt + MainNavigationRail.kt

**Full path (bar):** `app/src/main/java/org/enchant/main/MainNavigationBar.kt`
**Full path (rail):** `app/src/main/java/org/enchant/main/MainNavigationRail.kt`

**Package:** `org.enchant.main`

**Single responsibility:** The bottom navigation bar (phone) and navigation rail (tablet landscape) for the 4 main tabs.

**MainNavigationBar.kt:**

```kotlin
package org.enchant.main

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.enchant.MainNavigationListLocation

@Composable
fun MainNavigationBar(
    currentTab: MainNavigationListLocation,
    onTabSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier,
    chatsCount: Int = 0,
    callsCount: Int = 0,
    storiesCount: Int = 0
) {
    NavigationBar(modifier = modifier) {
        MainNavigationListLocation.entries.forEach { tab ->
            val count = when (tab) {
                MainNavigationListLocation.CHATS -> chatsCount
                MainNavigationListLocation.CALLS -> callsCount
                MainNavigationListLocation.STORIES -> storiesCount
                MainNavigationListLocation.ARCHIVE -> 0
            }

            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = tab.icon),
                        contentDescription = null
                    )
                },
                label = { Text(text = stringResource(id = tab.label)) },
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}
```

**MainNavigationRail.kt:**

```kotlin
package org.enchant.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationListLocation

@Composable
fun MainNavigationRail(
    currentTab: MainNavigationListLocation,
    onTabSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier,
    chatsCount: Int = 0,
    callsCount: Int = 0,
    storiesCount: Int = 0
) {
    NavigationRail(modifier = modifier.fillMaxHeight()) {
        MainNavigationListLocation.entries.forEach { tab ->
            val count = when (tab) {
                MainNavigationListLocation.CHATS -> chatsCount
                MainNavigationListLocation.CALLS -> callsCount
                MainNavigationListLocation.STORIES -> storiesCount
                MainNavigationListLocation.ARCHIVE -> 0
            }

            NavigationRailItem(
                icon = {
                    Icon(
                        painter = painterResource(id = tab.icon),
                        contentDescription = null
                    )
                },
                label = { Text(text = stringResource(id = tab.label)) },
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}
```

**ListAndNavigation.kt (combines list + rail/bar):**

```kotlin
@Composable
fun ListAndNavigation(
    mainNavigationViewModel: MainNavigationViewModel,
    paneExpansionState: PaneExpansionState,
    modifier: Modifier = Modifier
) {
    val isExpanded = paneExpansionState.currentAnchor != detailOnlyAnchor

    Row(modifier = modifier.fillMaxSize()) {
        MainNavigationRail(
            currentTab = mainNavigationViewModel.mainNavigationState.value.currentListLocation,
            onTabSelected = { mainNavigationViewModel.goTo(it) },
            chatsCount = mainNavigationViewModel.mainNavigationState.value.chatsCount,
            callsCount = mainNavigationViewModel.mainNavigationState.value.callsCount,
            storiesCount = mainNavigationViewModel.mainNavigationState.value.storiesCount
        )

        ConversationListScreen(
            onConversationClick = { conversationArgs ->
                mainNavigationViewModel.goToDetail(
                    MainNavigationDetailLocation.Conversation(conversationArgs)
                )
            },
            onCallsClick = { mainNavigationViewModel.goTo(MainNavigationListLocation.CALLS) },
            onStoriesClick = { mainNavigationViewModel.goTo(MainNavigationListLocation.STORIES) },
            onArchiveClick = { mainNavigationViewModel.goTo(MainNavigationListLocation.ARCHIVE) }
        )
    }
}
```

**Tests to write:**

- `MainNavigationBar` renders 4 `NavigationBarItem`s
- `MainNavigationRail` renders 4 `NavigationRailItem`s
- Clicking a tab calls `onTabSelected` with the correct tab
- Selected tab matches `currentTab`

---

### M8: MainActivityComponents.kt

**Full path:** `app/src/main/java/org/enchant/main/MainActivityComponents.kt`

**Package:** `org.enchant.main`

**Single responsibility:** Sets up the per-tab `NavHostController`s, wires navigation state from `MainNavigationViewModel` to the NavHosts, and provides `DetailsScreenNavHost` composable that renders the active tab's detail navigation.

**Imports required:**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fromMoon
import androidx.navigation.toRoute
import androidx.navigation3.adaptiveThreePane.model.ThreePaneScaffoldRole
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationListLocation
import org.enchant.MainNavigationViewModel
import org.enchant.MainNavigationRouter
```

**Structure:**

```kotlin
class MainActivityComponents(
    private val mainNavigationViewModel: MainNavigationViewModel
) : MainNavigationRouter by mainNavigationViewModel {

    val chatsNavHostController = rememberNavController()
    val callsNavHostController = rememberNavController()
    val storiesNavHostController = rememberNavController()

    fun chatNavGraphBuilder(builder: NavGraphBuilder.() -> Unit) {
        // Build the chat detail nav graph
    }

    fun callNavGraphBuilder(builder: NavGraphBuilder.() -> Unit) {
        // Build the call detail nav graph
    }

    fun storiesNavGraphBuilder(builder: NavGraphBuilder.() -> Unit) {
        // Build the stories nav graph
    }

    @Composable
    fun RememberDetailNavHostController(
        navHostController: NavHostController,
        builder: NavGraphBuilder.() -> Unit
    ): NavHostController {
        val viewModelStore = LocalViewModelStoreOwner.current!!.viewModelStore

        LaunchedEffect(navHostController) {
            val graph = navHostController.createGraph(
                startDestination = MainNavigationDetailLocation.Empty,
                builder = { builder(navHostController) }
            )
            navHostController.setViewModelStore(viewModelStore)
            navHostController.setGraph(graph, null)
        }

        return navHostController
    }
}

@Composable
fun DetailsScreenNavHost(
    navHostController: NavHostController,
    detailLocation: MainNavigationDetailLocation,
    modifier: Modifier = Modifier
) {
    val currentEntry by navHostController.currentBackStackEntryAsState()

    LaunchedEffect(detailLocation) {
        if (detailLocation.isContentRoot) {
            navHostController.popBackStack(navHostController.graph.id, inclusive = true)
        }
        navHostController.navigate(detailLocation) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navHostController,
        startDestination = MainNavigationDetailLocation.Empty,
        modifier = modifier
    ) {
        composable<MainNavigationDetailLocation.Conversation> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.Conversation>()
            ConversationScreen(
                conversationArgs = route.conversationArgs,
                onBack = { navHostController.popBackStack() },
                onMessageDetails = { recipientId, messageId ->
                    navHostController.navigate(MainNavigationDetailLocation.Chats.MessageDetails(recipientId, messageId))
                }
            )
        }

        composable<MainNavigationDetailLocation.Chats.MessageDetails> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.Chats.MessageDetails>()
            MessageDetailsScreen(
                recipientId = route.recipientId,
                messageId = route.messageId,
                onBack = { navHostController.popBackStack() }
            )
        }

        composable<MainNavigationDetailLocation.Chats.ConversationSettings> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.Chats.ConversationSettings>()
            ConversationSettingsScreen(
                recipientId = route.recipientId,
                onBack = { navHostController.popBackStack() }
            )
        }

        composable<MainNavigationDetailLocation.CallLinkDetails> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.CallLinkDetails>()
            CallLinkDetailsScreen(
                callLinkRoomId = route.callLinkRoomId,
                onBack = { navHostController.popBackStack() }
            )
        }

        composable<MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName> { backStackEntry ->
            val route = backStackEntry.toRoute<MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName>()
            EditCallLinkNameScreen(
                callLinkRoomId = route.callLinkRoomId,
                onBack = { navHostController.popBackStack() }
            )
        }

        composable<MainNavigationDetailLocation.Empty> { _ ->
            EmptyDetailScreen()
        }

        composable<MainNavigationDetailLocation.Settings> {
            SettingsScreen(
                onBack = { navHostController.popBackStack() }
            )
        }
    }
}
```

**Key implementation details:**
- `MainActivityComponents` wraps `MainNavigationViewModel` as a `MainNavigationRouter` — so callers use `goTo()`, `goToDetail()`, `setFocusedPane()` without knowing they're talking to the ViewModel
- Each tab's `NavHostController` shares the same `ViewModelStore` (from the Fragment/Activity) so ViewModels are preserved across tab switches
- `DetailsScreenNavHost.navigate(detailLocation)` with `launchSingleTop = true` handles duplicate navigation gracefully
- When `detailLocation.isContentRoot` (Empty, Settings), the NavHost's back stack is cleared before navigating
- The `composable<T>` syntax uses kotlin.serialization to deserialize the route type from the NavBackStackEntry's handle

**Tests to write:**

- `DetailsScreenNavHost` navigates to correct route when `detailLocation` changes
- `DetailsScreenNavHost` clears back stack when `detailLocation.isContentRoot`
- `DetailsScreenNavHost` does not re-navigate when same `detailLocation` is emitted
- Each tab's NavHostController is independent (navigating in Calls does not affect Chats)
- `composable<Conversation>` receives correct `conversationArgs`

---

### M9: WindowSizeClassExtensions.kt

**Full path:** `app/src/main/java/org/enchant/util/WindowSizeClassExtensions.kt`

**Package:** `org.enchant.util`

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

**Single responsibility:** Assemble all the main navigation components and render `AppScaffold`. This is the top-level entry point that wires everything together.

**Pattern (simplified):**

```kotlin
class MainActivity : FragmentActivity() {

    private val mainNavigationViewModel: MainNavigationViewModel by viewModels()

    @Composable
    override fun Content() {
        AppTheme {
            val navigator = rememberAppScaffoldNavigator()

            val mainActivityComponents = remember(mainNavigationViewModel) {
                MainActivityComponents(mainNavigationViewModel)
            }

            LaunchedEffect(Unit) {
                mainNavigationViewModel.navigator = navigator
            }

            AppScaffold(
                mainNavigationViewModel = mainNavigationViewModel,
                navigator = navigator,
                chatsNavHostController = mainActivityComponents.chatsNavHostController,
                callsNavHostController = mainActivityComponents.callsNavHostController,
                storiesNavHostController = mainActivityComponents.storiesNavHostController
            )
        }
    }
}
```

**Note:** The actual `MainActivity` implementation will be significantly more complex — it needs to handle `LocalNavigationEventDispatcherOwner` for predictive back, `LocalOnBackPressedDispatcherOwner`, and wire up all the tab-specific nav graphs.

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
| M3 | `app/MainNavigationViewModel.kt` | ViewModel with per-tab detail stacks, pane focus, tab switching | M1, M2, M4 |
| M4 | `app/MainNavigationRouter.kt` | `interface MainNavigationRouter` — `goTo()`, `goToDetail()`, `setFocusedPane()` | M1, M2 |
| M5 | `app/window/AppScaffoldNavigator.kt` | wraps `ThreePaneScaffoldNavigator` with animation state | adaptive, material3 |
| M6 | `app/window/AppScaffold.kt` | `SinglePaneAppScaffold` + `AdaptiveAppScaffold` branches | M3, M5, M7 |
| M7 | `app/main/MainNavigationBar.kt` + `MainNavigationRail.kt` | bottom nav bar and side rail for 4 tabs | M2 |
| M8 | `app/main/MainActivityComponents.kt` | per-tab NavHostController setup + `DetailsScreenNavHost` | M1, M3 |
| M9 | `app/util/WindowSizeClassExtensions.kt` | breakpoint detection + split pane logic | adaptive |
| — | `core/navigation/NavRoute.kt` | DELETE | — |
| — | `core/navigation/NavHost.kt` | DELETE | — |

## Build Order

1. **Dependencies** — Update `libs.versions.toml` and `core/navigation/build.gradle.kts` with navigation3 and lifecycle-viewmodel-navigation3
2. **ComposeFragment base** — Create `core/ui/compose/ComposeFragment.kt` (if using Fragment embedding)
3. **Core infrastructure** (P1-01 through P1-05) — no feature dependencies
4. **One simple feature NavKey + NavDisplay** (P2-01, P2-02) — validates core infra works
5. **Main navigation shell** — M1, M2, M4, M3 (in that order) — establishes the top-level structure before adding features
6. **Window and scaffold** — M9, M5, M6, M7, M8 — the adaptive layout and navigation bar/rail
7. **Remaining features** (P2-nnn)
8. **Complex flows** (P3-nnn) — only after core infra is solid
9. **MainActivity integration** — wire everything together in MainActivity
10. **Delete old NavRoute.kt + NavHost.kt** — only after ALL features have migrated

**Phase M key enhancement:** The reference app locks to the secondary pane on tab switch (`lockPaneToSecondary = true`), discarding detail state. enchant-native's `MainNavigationViewModel` maintains **independent detail stacks per tab**, preserving conversation state when switching between tabs. This is the central design decision that makes the architecture superior for a real messenger use case.
