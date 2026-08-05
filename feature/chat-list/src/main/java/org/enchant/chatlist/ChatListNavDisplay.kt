package org.enchant.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.enchant.chat.data.ConversationFilter
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun ChatListNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(ChatListNavKey.ConversationList),
    viewModel: ConversationListViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onNavigateToConversation: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onNewGroup: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec,
        entryProvider = entryProvider {
            entry<ChatListNavKey.ConversationList> {
                LaunchedEffect(Unit) {
                    viewModel.selectFilter(ConversationFilter.ALL)
                }
                ConversationListScreen(
                    viewModel = viewModel,
                    onConversationClick = { conversationId ->
                        onNavigateToConversation(conversationId)
                    },
                    onNewChat = onNewChat,
                    onNewGroup = onNewGroup,
                    onProfileClick = onProfileClick,
                    onSettingsClick = onSettingsClick
                )
            }

            entry<ChatListNavKey.ArchiveList> {
                LaunchedEffect(Unit) {
                    viewModel.selectFilter(ConversationFilter.ARCHIVED)
                }
                ConversationListScreen(
                    viewModel = viewModel,
                    onConversationClick = { conversationId ->
                        onNavigateToConversation(conversationId)
                    },
                    onNewChat = onNewChat,
                    onNewGroup = onNewGroup,
                    onProfileClick = onProfileClick,
                    onSettingsClick = onSettingsClick
                )
            }
        }
    )
}
