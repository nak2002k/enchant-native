package org.enchant.calls

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import org.enchant.calls.calllinks.CallLinkManager
import org.enchant.calls.calllinks.CallLinkScreen
import org.enchant.calls.screens.*
import org.enchant.core.calls.CallLinkData
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun CallsNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(CallsNavKey.CallLog),
    modifier: Modifier = Modifier,
    callViewModel: CallViewModel = viewModel(),
    callLogViewModel: CallLogViewModel = viewModel(),
    callLinkManager: CallLinkManager? = null
) {
    val scope = rememberCoroutineScope()

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec,
        entryProvider = entryProvider {
            entry<CallsNavKey.CallLog> {
                val state by callLogViewModel.uiState.collectAsStateWithLifecycle()
                CallLogScreen(
                    entries = state.filteredEntries,
                    filter = state.filter,
                    isLoading = state.isLoading,
                    isSelectionMode = state.isSelectionMode,
                    selectedIds = state.selectedIds,
                    onFilterChange = callLogViewModel::setFilter,
                    onEntryClick = { entryId ->
                        callViewModel.navigateToConversation(entryId)
                    },
                    onStartSelection = callLogViewModel::startSelection,
                    onEndSelection = callLogViewModel::endSelection,
                    onToggleSelected = callLogViewModel::toggleSelected,
                    onSelectAll = callLogViewModel::selectAll,
                    onDelete = { callLogViewModel.stageDeletion() }
                )
            }

            entry<CallsNavKey.OutgoingCall> { key ->
                val state by callViewModel.uiState.collectAsStateWithLifecycle()
                OutgoingCallScreen(
                    remoteName = state.callState.remoteName ?: "User ${key.recipientId}",
                    isVideoCall = state.callState.isVideoCall,
                    callStatus = state.callState.status.name,
                    onEndCall = {
                        callViewModel.endCall()
                        if (backStack.size > 0) backStack.removeAt(backStack.size - 1)
                    },
                    onToggleSpeaker = callViewModel::toggleSpeaker,
                    onSwitchToVideo = callViewModel::toggleVideo
                )
            }

            entry<CallsNavKey.IncomingCall> { key ->
                val state by callViewModel.uiState.collectAsStateWithLifecycle()
                IncomingCallScreen(
                    callerName = state.callState.remoteName ?: "User ${key.callerId}",
                    callerId = key.callerId.toString(),
                    isVideoCall = state.callState.isVideoCall,
                    callStatus = state.callState.status.name,
                    onAcceptAudio = { callViewModel.acceptCall(false) },
                    onAcceptVideo = { callViewModel.acceptCall(true) },
                    onDecline = {
                        callViewModel.denyCall()
                        if (backStack.size > 0) backStack.removeAt(backStack.size - 1)
                    }
                )
            }

            entry<CallsNavKey.ActiveCall> {
                val state by callViewModel.uiState.collectAsStateWithLifecycle()
                if (state.callState.isVideoCall) {
                    ActiveVideoCallScreen(
                        remoteUserId = state.callState.remoteUserId ?: "",
                        durationSeconds = state.callState.durationSeconds,
                        isMuted = state.callState.isMuted,
                        isSpeakerOn = state.callState.isSpeakerOn,
                        onToggleMute = callViewModel::toggleMute,
                        onFlipCamera = callViewModel::flipCamera,
                        onToggleSpeaker = callViewModel::toggleSpeaker,
                        onEndCall = {
                            callViewModel.endCall()
                            if (backStack.size > 0) backStack.removeAt(backStack.size - 1)
                        }
                    )
                } else {
                    ActiveVoiceCallScreen(
                        remoteName = state.callState.remoteName ?: "Unknown",
                        durationSeconds = state.callState.durationSeconds,
                        isMuted = state.callState.isMuted,
                        isSpeakerOn = state.callState.isSpeakerOn,
                        signalStrength = state.callState.signalStrength?.ordinal?.let { 3 - it } ?: 0,
                        onToggleMute = callViewModel::toggleMute,
                        onToggleSpeaker = callViewModel::toggleSpeaker,
                        onEndCall = {
                            callViewModel.endCall()
                            if (backStack.size > 0) backStack.removeAt(backStack.size - 1)
                        },
                        onShowKeypad = { },
                        onSwitchToVideo = callViewModel::toggleVideo,
                        onShowSafetyNumber = { }
                    )
                }
            }

            entry<CallsNavKey.GroupCall> {
                val state by callViewModel.uiState.collectAsStateWithLifecycle()
                GroupCallScreen(
                    participants = emptyList(),
                    isAdmin = false,
                    durationSeconds = state.callState.durationSeconds,
                    isMuted = state.callState.isMuted,
                    onToggleMute = callViewModel::toggleMute,
                    onRaiseHand = { callViewModel.raiseHand(true) },
                    onSendReaction = { },
                    onMuteParticipant = { },
                    onRemoveParticipant = { },
                    onEndCall = {
                        callViewModel.endCall()
                        if (backStack.size > 0) backStack.removeAt(backStack.size - 1)
                    }
                )
            }

            entry<CallsNavKey.CallLink> { key ->
                var callLinkData by remember { mutableStateOf<CallLinkData?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(key.linkRoomId) {
                    if (callLinkManager != null) {
                        isLoading = true
                        error = null
                        callLinkManager.getCallLink(key.linkRoomId).fold(
                            onSuccess = { callLinkData = it; isLoading = false },
                            onFailure = { error = it.message; isLoading = false }
                        )
                    }
                }

                CallLinkScreen(
                    callLink = callLinkData,
                    isOwner = false,
                    isLoading = isLoading,
                    error = error,
                    onJoinCall = {
                        scope.launch {
                            callLinkManager?.joinCallLink(key.linkRoomId)
                        }
                    },
                    onEditName = { },
                    onDelete = {
                        scope.launch {
                            callLinkManager?.deleteCallLink(key.linkRoomId)
                            if (backStack.size > 0) backStack.removeAt(backStack.size - 1)
                        }
                    },
                    onNavigateBack = {
                        if (backStack.size > 0) backStack.removeAt(backStack.size - 1)
                    }
                )
            }
        }
    )
}
