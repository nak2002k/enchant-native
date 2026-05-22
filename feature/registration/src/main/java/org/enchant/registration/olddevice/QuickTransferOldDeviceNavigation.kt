package org.enchant.registration.olddevice

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
sealed interface TransferNavKey : NavKey {
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
        transitionSpec = {
            org.enchant.core.ui.navigation.TransitionSpecs.HorizontalSlide.transitionSpec(this)
        },
        popTransitionSpec = {
            org.enchant.core.ui.navigation.TransitionSpecs.HorizontalSlide.popTransitionSpec(this)
        },
        predictivePopTransitionSpec = {
            org.enchant.core.ui.navigation.TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec(this, it)
        }
    )
}
