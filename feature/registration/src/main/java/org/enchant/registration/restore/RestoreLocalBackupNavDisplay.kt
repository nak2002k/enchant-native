package org.enchant.registration.restore

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.view.WindowManager
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import org.enchant.core.ui.navigation.BottomSheetSceneStrategy
import org.enchant.core.ui.navigation.LocalBottomSheetDismiss

data class RestoreState(
    val dialog: RestoreDialog? = null,
    val isLoadingBackupDirectory: Boolean = false
)

sealed interface RestoreDialog

sealed interface RestoreCallback {
    fun selectBackup(backup: Any)
    fun displaySkipRestoreWarning()
    fun clearDialog()
    fun onDialogConfirmed(dialog: RestoreDialog)
}

data class EnterBackupKeyState(
    val key: String = "",
    val isValid: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreNavDisplay(
    state: RestoreState,
    callback: RestoreCallback,
    isRegistrationInProgress: Boolean,
    enterBackupKeyState: EnterBackupKeyState,
    backupKey: String
) {
    val backStack = rememberNavBackStack(RestoreNavKey.SelectRestoreType)
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides
            rememberNavigationEventDispatcherOwner(parent = null)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                sceneStrategy = bottomSheetStrategy,
                entryProvider = entryProvider {

                    entry<RestoreNavKey.SelectRestoreType> {
                        SelectRestoreTypeScreen(
                            onSelectFolder = { backStack.add(RestoreNavKey.FolderInstructionSheet) },
                            onSelectFile = { backStack.add(RestoreNavKey.FileInstructionSheet) }
                        )
                    }

                    entry<RestoreNavKey.FolderInstructionSheet>(
                        metadata = BottomSheetSceneStrategy.bottomSheet()
                    ) {
                        FolderInstructionSheetContent(
                            onContinue = { }
                        )
                    }

                    entry<RestoreNavKey.FileInstructionSheet>(
                        metadata = BottomSheetSceneStrategy.bottomSheet()
                    ) {
                        FileInstructionSheetContent(
                            onContinue = { }
                        )
                    }

                    entry<RestoreNavKey.SelectBackup> {
                        SelectBackupScreen(
                            onRestore = { backStack.add(RestoreNavKey.EnterBackupKey) },
                            onSelectDifferent = { backStack.add(RestoreNavKey.SelectBackupSheet) }
                        )
                    }

                    entry<RestoreNavKey.SelectBackupSheet>(
                        metadata = BottomSheetSceneStrategy.bottomSheet()
                    ) {
                        val dismissSheet = LocalBottomSheetDismiss.current
                        SelectBackupSheetContent(
                            onBackupSelected = {
                                callback.selectBackup(it)
                                dismissSheet()
                            }
                        )
                    }

                    entry<RestoreNavKey.EnterBackupKey> {
                        EnterBackupKeyScreen(
                            enterBackupKeyState = enterBackupKeyState,
                            onNoKey = { backStack.add(RestoreNavKey.NoRecoveryKeySheet) }
                        )
                    }

                    entry<RestoreNavKey.NoRecoveryKeySheet>(
                        metadata = BottomSheetSceneStrategy.bottomSheet()
                    ) {
                        val dismissSheet = LocalBottomSheetDismiss.current
                        NoRecoveryKeySheetContent(
                            onSkip = {
                                dismissSheet()
                                callback.displaySkipRestoreWarning()
                            }
                        )
                    }
                }
            )

            if (state.dialog != null) {
                RestoreDialogContent(
                    dialog = state.dialog,
                    onConfirm = { callback.onDialogConfirmed(state.dialog) },
                    onDismiss = callback::clearDialog
                )
            }

            if (state.isLoadingBackupDirectory) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SelectRestoreTypeScreen(
    onSelectFolder: () -> Unit,
    onSelectFile: () -> Unit
) {
}

@Composable
private fun FolderInstructionSheetContent(
    onContinue: () -> Unit
) {
}

@Composable
private fun FileInstructionSheetContent(
    onContinue: () -> Unit
) {
}

@Composable
private fun SelectBackupScreen(
    onRestore: () -> Unit,
    onSelectDifferent: () -> Unit
) {
}

@Composable
private fun SelectBackupSheetContent(
    onBackupSelected: (Any) -> Unit
) {
}

@Composable
private fun EnterBackupKeyScreen(
    enterBackupKeyState: EnterBackupKeyState,
    onNoKey: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
private fun NoRecoveryKeySheetContent(
    onSkip: () -> Unit
) {
}

@Composable
private fun RestoreDialogContent(
    dialog: RestoreDialog,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
}
