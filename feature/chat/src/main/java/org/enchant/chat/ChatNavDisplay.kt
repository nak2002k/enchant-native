package org.enchant.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.enchant.chat.components.MediaViewerScreen
import org.enchant.core.ui.navigation.TransitionSpecs

@Composable
fun ChatNavDisplay(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey> = rememberNavBackStack(),
    modifier: Modifier = Modifier
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
        popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
        predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec,
        entryProvider = entryProvider {
            entry<ChatNavKey.Conversation> { key ->
                ConversationScreen(
                    threadId = key.threadId,
                    onMediaClick = { messageId, attachmentId ->
                        backStack.add(ChatNavKey.MediaViewer(messageId = messageId, attachmentId = attachmentId))
                    },
                    onBack = { if (backStack.size > 0) backStack.removeAt(backStack.size - 1) }
                )
            }

            entry<ChatNavKey.MediaViewer> { key ->
                MediaViewerScreen(
                    messageId = key.messageId,
                    attachmentId = key.attachmentId,
                    onDismiss = { if (backStack.size > 0) backStack.removeAt(backStack.size - 1) }
                )
            }
        }
    )
}
