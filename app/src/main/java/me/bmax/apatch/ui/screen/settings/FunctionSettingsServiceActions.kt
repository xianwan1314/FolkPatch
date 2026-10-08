package me.bmax.apatch.ui.screen.settings

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import com.ramcosta.composedestinations.generated.destinations.ShizukuManagementScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.util.ShizukuServiceManager
import me.bmax.apatch.util.installJailbreak
import me.bmax.apatch.util.restartFramework
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.ui.showToast

/**
 * Jailbreak and Shizuku service handlers for [FunctionSettingsScreen].
 */
@Stable
internal class FunctionSettingsServiceActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val navigator: DestinationsNavigator,
    private val state: FunctionSettingsState,
) {
    fun onJailbreakChange(enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            if (enabled) {
                val success = installJailbreak()
                withContext(Dispatchers.Main) {
                    if (success) {
                        state.jailbreakEnabled.value = true
                        APApplication.sharedPreferences.edit()
                            .putBoolean("jailbreak_enabled", true)
                            .apply()
                        showToast(context, R.string.jailbreak_triggered)
                        state.showJailbreakSoftRebootDialog.value = true
                    } else {
                        showToast(context, R.string.settings_jailbreak_failed)
                    }
                }
            } else {
                rootShellForResult("rm -f ${APApplication.JAILBREAK_FILE}")
                APApplication.sharedPreferences.edit()
                    .putBoolean("jailbreak_enabled", false)
                    .apply()
                withContext(Dispatchers.Main) {
                    state.jailbreakEnabled.value = false
                }
            }
        }
    }

    fun onShizukuToggle(enabled: Boolean) {
        if (enabled) {
            scope.launch(Dispatchers.IO) {
                val success = ShizukuServiceManager.start(context)
                if (success) {
                    ShizukuServiceManager.setEnabled(true)
                }
                withContext(Dispatchers.Main) {
                    state.isShizukuEnabled.value = success
                    snackBarHost.showSnackbar(
                        context.getString(
                            if (success) R.string.settings_shizuku_started
                            else R.string.settings_shizuku_start_failed
                        )
                    )
                    refreshShizukuState()
                }
            }
        } else {
            scope.launch(Dispatchers.IO) {
                val success = ShizukuServiceManager.stop()
                if (success) {
                    ShizukuServiceManager.setEnabled(false)
                }
                withContext(Dispatchers.Main) {
                    state.isShizukuEnabled.value = !success
                    snackBarHost.showSnackbar(
                        context.getString(
                            if (success) R.string.settings_shizuku_stopped
                            else R.string.settings_shizuku_stop_failed
                        )
                    )
                    refreshShizukuState()
                }
            }
        }
    }

    fun onShizukuManage() {
        navigator.navigate(ShizukuManagementScreenDestination)
    }

    fun onJailbreakSoftRebootConfirm() {
        state.showJailbreakSoftRebootDialog.value = false
        restartFramework()
    }

    fun onJailbreakSoftRebootDismiss() {
        state.showJailbreakSoftRebootDialog.value = false
    }

    private fun refreshShizukuState() {
        scope.launch(Dispatchers.IO) {
            val running = ShizukuServiceManager.isServerRunning()
            withContext(Dispatchers.Main) {
                state.isShizukuRunning.value = running
            }
        }
    }
}
