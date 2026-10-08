package me.bmax.apatch.ui.screen.module

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.ui.component.LoadingDialogHandle
import me.bmax.apatch.ui.viewmodel.APModuleViewModel
import me.bmax.apatch.util.isJailbreakMode
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.toggleModule

/**
 * One module list entry. Owns its own toggle state and the per-item action
 * wiring so the three list layouts can share a single row implementation.
 */
@Composable
fun ModuleRow(
    navigator: DestinationsNavigator,
    viewModel: APModuleViewModel,
    module: APModuleViewModel.ModuleInfo,
    showMoreModuleInfo: Boolean,
    foldSystemModule: Boolean,
    simpleListBottomBar: Boolean,
    enableModuleShortcutAdd: Boolean,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    snackBarHost: SnackbarHostState,
    context: Context,
    checkStrongBiometric: suspend () -> Boolean,
    loadingDialog: LoadingDialogHandle,
    failedEnable: String,
    failedDisable: String,
    rebootLabel: String,
    rebootToApply: String,
    onModuleUpdate: suspend (APModuleViewModel.ModuleInfo, String, String, String) -> Unit,
    onModuleUninstall: suspend (APModuleViewModel.ModuleInfo) -> Unit,
    onModuleUndoUninstall: suspend (APModuleViewModel.ModuleInfo) -> Unit,
    onClickModule: (id: String, name: String, hasWebUi: Boolean) -> Unit,
) {
    var isChecked by rememberSaveable(module) { mutableStateOf(module.enabled) }
    val scope = rememberCoroutineScope()
    val updatedModule = viewModel.getCachedUpdate(module.id)

    ModuleItem(
        navigator,
        module,
        isChecked,
        updatedModule.first,
        showMoreModuleInfo = showMoreModuleInfo,
        foldSystemModule = foldSystemModule,
        simpleListBottomBar = simpleListBottomBar,
        enableModuleShortcutAdd = enableModuleShortcutAdd,
        expanded = expanded,
        onExpandToggle = onExpandToggle,
        onUninstall = {
            scope.launch { onModuleUninstall(module) }
        },
        onUndoUninstall = {
            scope.launch { onModuleUndoUninstall(module) }
        },
        onCheckChanged = { checked ->
            scope.launch {
                if (!checkStrongBiometric()) return@launch
                val success = loadingDialog.withLoading {
                    withContext(Dispatchers.IO) {
                        toggleModule(module.id, !isChecked)
                    }
                }
                if (success) {
                    isChecked = checked
                    viewModel.fetchModuleList()

                    // In jailbreak mode a full reboot would unload the
                    // runtime-loaded module, so apply without the prompt.
                    if (!withContext(Dispatchers.IO) { isJailbreakMode() }) {
                        val result = snackBarHost.showSnackbar(
                            message = rebootToApply,
                            actionLabel = rebootLabel,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            reboot()
                        }
                    }
                } else {
                    val message = if (isChecked) failedDisable else failedEnable
                    snackBarHost.showSnackbar(message.format(module.name))
                }
            }
        },
        onUpdate = {
            scope.launch {
                onModuleUpdate(
                    module,
                    updatedModule.third,
                    updatedModule.first,
                    "${module.name}-${updatedModule.second}.zip"
                )
            }
        },
        onClick = { clickedModule ->
            onClickModule(clickedModule.id, clickedModule.name, clickedModule.hasWebUi)
        })
}
