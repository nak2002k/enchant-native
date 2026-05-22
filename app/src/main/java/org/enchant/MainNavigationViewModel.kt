package org.enchant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
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
import org.enchant.main.MainNavigationRepository
import org.enchant.window.AppScaffoldNavigator

class MainNavigationViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: MainNavigationRepository = MainNavigationRepository
) : ViewModel(), MainNavigationRouter {

    private val _mainNavigationState = MutableStateFlow(
        MainNavigationState(
            currentListLocation = MainNavigationListLocation.CHATS,
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
    val detailLocation: SharedFlow<MainNavigationDetailLocation> = _internalDetailLocation.asSharedFlow()

    private val _paneFocusRequests = MutableSharedFlow<String?>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val paneFocusRequests: SharedFlow<String?> = _paneFocusRequests.asSharedFlow()

    private val _fullScreenPane = MutableStateFlow(false)
    val fullScreenPane: StateFlow<Boolean> = _fullScreenPane.asStateFlow()
    private val _isSplitPane = MutableStateFlow(true)
    val isSplitPane: StateFlow<Boolean> = _isSplitPane.asStateFlow()

    private var lockPaneToSecondary: Boolean = savedStateHandle["lock_pane_to_secondary"] ?: true

    private var navigator: AppScaffoldNavigator<Any>? = null
    private var navigatorScope: CoroutineScope? = null

    init {
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

    private var earlyFocusedPaneRequested: String? = null

    fun clearEarlyDetailLocation() {
        earlyNavigationDetailLocationRequested = null
    }

    fun wrapNavigator(
        composeScope: CoroutineScope,
        navigator: AppScaffoldNavigator<Any>
    ): AppScaffoldNavigator<Any> {
        this.navigatorScope = composeScope
        this.navigator = navigator

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
        viewModelScope.launch {
            _paneFocusRequests.tryEmit("Secondary")
        }
        _isSplitPane.value = true
    }

    override fun goTo(location: MainNavigationDetailLocation) {
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

    override fun setFocusedPane(role: String) {
        val effectiveRole = if (lockPaneToSecondary) "Secondary" else role
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