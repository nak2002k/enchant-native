# Done

Completed navigation rewrite implementation.

## navigation_rewrite.md

The full specification document for the adaptive three-pane navigation architecture (Phase M).

**What was implemented:**
- Phase 1: TransitionSpecs, ResultEventBus, ResultEffect, BottomSheetSceneStrategy, NavBackStackExtensions
- Phase 2: Per-feature NavKey + NavDisplay (auth, chat, chat-list, calls)
- Phase 3: Registration flow with EventDrivenViewModel
- Phase M: Adaptive main shell with AppScaffold, MainNavigationViewModel, MainNavigationBar/Rail, AppScaffoldNavigator

**Key files created:**
- `app/src/main/java/org/enchant/MainNavigationDetailLocation.kt` — sealed interface with Chats/Calls sub-interfaces
- `app/src/main/java/org/enchant/MainNavigationListLocation.kt` — enum with CHATS, CALLS, STORIES, ARCHIVE
- `app/src/main/java/org/enchant/MainNavigationViewModel.kt` — full ViewModel with detail stacks
- `app/src/main/java/org/enchant/MainNavigationRouter.kt` — router interfaces
- `app/src/main/java/org/enchant/MainNavDisplay.kt` — integrated AppScaffold-based display
- `app/src/main/java/org/enchant/window/AppScaffold.kt` — adaptive scaffold
- `app/src/main/java/org/enchant/window/AppScaffoldNavigator.kt` — scaffold navigator interface
- `app/src/main/java/org/enchant/window/AppScaffoldAnimationState.kt` — animation state
- `app/src/main/java/org/enchant/window/NavigationType.kt` — BAR/RAIL enum
- `app/src/main/java/org/enchant/window/AppPaneDragHandle.kt` — draggable divider
- `app/src/main/java/org/enchant/main/MainNavigationBar.kt` — bottom navigation bar
- `app/src/main/java/org/enchant/main/MainNavigationRail.kt` — navigation rail
- `app/src/main/java/org/enchant/main/MainActivityComponents.kt` — EmptyDetailScreen, DetailLocationEffect
- `app/src/main/java/org/enchant/main/MainFloatingActionButtons.kt` — FAB components
- `core/ui/src/main/java/org/enchant/core/ui/WindowSizeClassExtensions.kt` — breakpoint utilities

**Tests created:**
- `app/src/test/java/org/enchant/MainNavigationDetailLocationTest.kt`
- `app/src/test/java/org/enchant/MainNavigationRouterTest.kt`
- `app/src/test/java/org/enchant/MainNavigationListLocationTest.kt`
- `app/src/test/java/org/enchant/window/AppScaffoldNavigatorTest.kt`
- `core/ui/src/test/java/org/enchant/core/ui/WindowBreakpointTest.kt`