package org.enchant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.enchant.auth.AuthViewModel
import org.enchant.calls.CallViewModel
import org.enchant.core.calls.CallStatus
import org.enchant.main.EmptyDetailScreen
import org.enchant.main.MainNavigationBar
import org.enchant.main.MainNavigationDetailLocationEffect
import org.enchant.main.MainNavigationRail
import org.enchant.MainNavigationViewModel
import org.enchant.main.MainFloatingActionButtons
import org.enchant.main.MainFloatingActionButtonsCallback
import org.enchant.window.AppScaffold
import org.enchant.window.AppScaffoldNavigatorImpl

@Composable
fun MainNavDisplay(
    callViewModel: CallViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val mainNavViewModel: MainNavigationViewModel = viewModel()
    val mainNavState by mainNavViewModel.mainNavigationState.collectAsStateWithLifecycle()
    val callUiState by callViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val navigator = remember { AppScaffoldNavigatorImpl<Any>() }

    LaunchedEffect(Unit) {
        mainNavViewModel.wrapNavigator(scope, navigator)
    }

    LaunchedEffect(callUiState.callState.status) {
        val status = callUiState.callState.status
        when (status) {
            CallStatus.RINGING, CallStatus.CALLING, CallStatus.CONNECTED -> {
                mainNavViewModel.goTo(MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName(
                    callUiState.callState.callId ?: ""
                ))
            }
            CallStatus.IDLE -> {}
            else -> {}
        }
    }

    MainNavigationDetailLocationEffect(mainNavigationViewModel = mainNavViewModel)

    AppScaffold(
        navigator = navigator,
        modifier = Modifier.fillMaxSize(),
        secondaryContent = {
            ListPaneContent(
                currentListLocation = mainNavState.currentListLocation,
                onConversationClick = { threadId ->
                    mainNavViewModel.goTo(MainNavigationDetailLocation.Conversation(threadId))
                }
            )
        },
        primaryContent = {
            DetailPaneContent(
                detailStack = getDetailStackForLocation(mainNavState.currentListLocation, mainNavViewModel)
            )
        },
        navRailContent = {
            MainNavigationRail(
                state = mainNavState,
                mainFloatingActionButtons = {
                    MainFloatingActionButtons(
                        destination = mainNavState.currentListLocation,
                        callback = MainFloatingActionButtonsCallback.Empty
                    )
                },
                onDestinationSelected = { mainNavViewModel.goTo(it) }
            )
        },
        bottomNavContent = {
            MainNavigationBar(
                state = mainNavState,
                onDestinationSelected = { mainNavViewModel.goTo(it) }
            )
        }
    )
}

@Composable
private fun ListPaneContent(
    currentListLocation: MainNavigationListLocation,
    onConversationClick: (Long) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentListLocation) {
            MainNavigationListLocation.CHATS,
            MainNavigationListLocation.ARCHIVE -> {
                Text("Chat List - ${currentListLocation.name}")
            }
            MainNavigationListLocation.CALLS -> {
                Text("Call Log - ${currentListLocation.name}")
            }
            MainNavigationListLocation.STORIES -> {
                Text("Stories - ${currentListLocation.name}")
            }
        }
    }
}

@Composable
private fun DetailPaneContent(
    detailStack: List<MainNavigationDetailLocation>
) {
    if (detailStack.isEmpty() || detailStack.first() == MainNavigationDetailLocation.Empty) {
        EmptyDetailScreen()
    } else {
        val topDetail = detailStack.last()
        when (topDetail) {
            is MainNavigationDetailLocation.Conversation -> {
                Text("Conversation ${topDetail.threadId}")
            }
            is MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName -> {
                Text("Call Link: ${topDetail.callLinkRoomId}")
            }
            else -> EmptyDetailScreen()
        }
    }
}

private fun getDetailStackForLocation(
    location: MainNavigationListLocation,
    viewModel: MainNavigationViewModel
): List<MainNavigationDetailLocation> {
    return when (location) {
        MainNavigationListLocation.CHATS -> viewModel.chatsDetailStack.value
        MainNavigationListLocation.ARCHIVE -> viewModel.archiveDetailStack.value
        MainNavigationListLocation.CALLS -> viewModel.callsDetailStack.value
        MainNavigationListLocation.STORIES -> viewModel.storiesDetailStack.value
    }
}