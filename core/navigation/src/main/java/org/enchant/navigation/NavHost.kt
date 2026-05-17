package org.enchant.navigation

import androidx.navigation.NavHostController

fun NavRoute.navigate(controller: NavHostController) {
    val routeString = when (this) {
        is NavRoute.Conversation -> "conversation/${conversationId}"
        is NavRoute.Search -> "search"
        is NavRoute.IncomingCall -> "incoming_call/${callId}"
        is NavRoute.OutgoingCall -> "outgoing_call/${userId}"
        is NavRoute.ActiveVoiceCall -> "active_voice_call/${callId}"
        is NavRoute.ActiveVideoCall -> "active_video_call/${callId}"
        is NavRoute.GroupCall -> "group_call/${callId}"
        is NavRoute.GroupInfo -> "group_info/${groupId}"
        is NavRoute.StatusViewer -> "status_viewer/${statusId}"
        is NavRoute.PollCreate -> "poll_create/${conversationId}"
        is NavRoute.MediaViewer -> "media_viewer/${conversationId}"
        else -> route
    }
    controller.navigate(routeString)
}

fun NavRoute.navigateAndClearStack(controller: NavHostController) {
    val routeString = when (this) {
        is NavRoute.Conversation -> "conversation/${conversationId}"
        is NavRoute.Search -> "search"
        is NavRoute.IncomingCall -> "incoming_call/${callId}"
        is NavRoute.OutgoingCall -> "outgoing_call/${userId}"
        is NavRoute.ActiveVoiceCall -> "active_voice_call/${callId}"
        is NavRoute.ActiveVideoCall -> "active_video_call/${callId}"
        is NavRoute.GroupCall -> "group_call/${callId}"
        is NavRoute.GroupInfo -> "group_info/${groupId}"
        is NavRoute.StatusViewer -> "status_viewer/${statusId}"
        is NavRoute.PollCreate -> "poll_create/${conversationId}"
        is NavRoute.MediaViewer -> "media_viewer/${conversationId}"
        else -> route
    }
    controller.navigate(routeString) {
        popUpTo(0) { inclusive = true }
    }
}


