package org.enchant.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun ChatListNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(ChatListNavKey.ConversationList),
    viewModel: ConversationListViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToConversation: (String) -> Unit = {}
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec,
        entryProvider = entryProvider {
            entry<ChatListNavKey.ConversationList> {
                ConversationListScreen(
                    viewModel = viewModel,
                    onConversationClick = { conversationId ->
                        onNavigateToConversation(conversationId)
                    },
                    onNewChat = {},
                    onNewGroup = {}
                )
            }

            entry<ChatListNavKey.ArchiveList> {
                ConversationListScreen(
                    viewModel = viewModel,
                    onConversationClick = { conversationId ->
                        onNavigateToConversation(conversationId)
                    },
                    onNewChat = {},
                    onNewGroup = {}
                )
            }
        }
    )
}
