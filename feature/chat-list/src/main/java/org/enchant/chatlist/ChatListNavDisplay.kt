package org.enchant.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.enchant.chatlist.ConversationListViewModel.Companion.NavigationEvent
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun ChatListNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(ChatListNavKey.ConversationList),
    viewModel: ConversationListViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToConversation: (Long) -> Unit = {}
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec,
        entryProvider = entryProvider {
            entry<ChatListNavKey.ConversationList> {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ConversationListScreen(
                    state = state,
                    onConversationClick = { threadId ->
                        onNavigateToConversation(threadId)
                    },
                    onEvent = viewModel::onEvent
                )
            }

            entry<ChatListNavKey.ArchiveList> {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ConversationListScreen(
                    state = state,
                    onConversationClick = { threadId ->
                        onNavigateToConversation(threadId)
                    },
                    onEvent = viewModel::onEvent
                )
            }
        }
    )
}
