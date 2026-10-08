package me.bmax.apatch.ui.screen.settings

import android.content.Context
import android.system.Os
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.UmountConfig
import me.bmax.apatch.ui.component.UmountConfigManager
import me.bmax.apatch.util.normalizePathHidePaths
import me.bmax.apatch.util.removeUtsSpoofConfig
import me.bmax.apatch.util.setNetIsolateEnabled
import me.bmax.apatch.util.setPathHideEnabled
import me.bmax.apatch.util.setPathHideFilterSystem
import me.bmax.apatch.util.setPathHideUidMode
import me.bmax.apatch.util.setUtsSpoofEnabled
import me.bmax.apatch.util.ui.showToast
import me.bmax.apatch.util.writeNetIsolateUids
import me.bmax.apatch.util.writePathHidePaths
import me.bmax.apatch.util.writePathHideUids
import me.bmax.apatch.util.writeUtsSpoofConfig

/**
 * Feature-toggle handlers for [FunctionSettingsScreen] (hide service, kernel spoof,
 * path hide, umount, net isolate). Bodies are the same side effects that previously
 * lived in the `FunctionSettingsContent` argument lambdas.
 */
@Stable
internal class FunctionSettingsActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackBarHost: SnackbarHostState,
    private val state: FunctionSettingsState,
) {
    fun onHideServiceChange(enabled: Boolean) {
        state.isHideServiceEnabled.value = enabled
    }

    fun onKernelSpoofChange(enabled: Boolean) {
        state.isKernelSpoofEnabled.value = enabled
        scope.launch(Dispatchers.IO) {
            val prefs = APApplication.sharedPreferences
            prefs.edit().putBoolean(APApplication.PREF_UTS_SPOOF_ENABLED, enabled).apply()
            if (enabled) {
                setUtsSpoofEnabled(true)
                var release = state.kernelSpoofVersion.value
                var buildTime = state.kernelSpoofBuildTime.value
                if (release.isBlank() && buildTime.isBlank()) {
                    val uname = Os.uname()
                    release = uname.release
                    buildTime = uname.version
                    state.kernelSpoofVersion.value = release
                    state.kernelSpoofBuildTime.value = buildTime
                }
                writeUtsSpoofConfig(release, buildTime)
                Natives.utsSet(release.ifBlank { null }, buildTime.ifBlank { null })
                withContext(Dispatchers.Main) {
                    snackBarHost.showSnackbar(context.getString(R.string.kernel_spoof_enabled))
                }
            } else {
                Natives.utsReset()
                setUtsSpoofEnabled(false)
                removeUtsSpoofConfig()
                withContext(Dispatchers.Main) {
                    snackBarHost.showSnackbar(context.getString(R.string.kernel_spoof_disabled_restored))
                }
            }
        }
    }

    fun onKernelSpoofVersionChange(value: String) {
        state.kernelSpoofVersion.value = value
    }

    fun onKernelSpoofBuildTimeChange(value: String) {
        state.kernelSpoofBuildTime.value = value
    }

    fun onKernelSpoofSave() {
        val currentEnabled = state.isKernelSpoofEnabled.value
        val currentVersion = state.kernelSpoofVersion.value
        val currentBuildTime = state.kernelSpoofBuildTime.value
        scope.launch(Dispatchers.IO) {
            val prefs = APApplication.sharedPreferences
            prefs.edit()
                .putBoolean(APApplication.PREF_UTS_SPOOF_ENABLED, currentEnabled)
                .putString(APApplication.PREF_UTS_SPOOF_RELEASE, currentVersion)
                .putString(APApplication.PREF_UTS_SPOOF_VERSION, currentBuildTime)
                .apply()

            if (currentEnabled) {
                setUtsSpoofEnabled(true)
                writeUtsSpoofConfig(currentVersion, currentBuildTime)
                val rc = Natives.utsSet(
                    currentVersion.ifBlank { null },
                    currentBuildTime.ifBlank { null }
                )
                withContext(Dispatchers.Main) {
                    if (rc < 0) {
                        snackBarHost.showSnackbar(context.getString(R.string.kernel_spoof_failed, rc))
                    } else {
                        snackBarHost.showSnackbar(context.getString(R.string.kernel_spoof_applied))
                    }
                }
            } else {
                Natives.utsReset()
                setUtsSpoofEnabled(false)
                removeUtsSpoofConfig()
                withContext(Dispatchers.Main) {
                    snackBarHost.showSnackbar(context.getString(R.string.kernel_spoof_disabled_restored))
                }
            }
        }
    }

    fun onKernelSpoofRestore() {
        scope.launch(Dispatchers.IO) {
            Natives.utsReset()
            val uname = Os.uname()
            val realRelease = uname.release
            val realVersion = uname.version
            withContext(Dispatchers.Main) {
                state.kernelSpoofVersion.value = realRelease
                state.kernelSpoofBuildTime.value = realVersion
            }
            if (state.isKernelSpoofEnabled.value) {
                val prefs = APApplication.sharedPreferences
                val savedRelease = prefs.getString(APApplication.PREF_UTS_SPOOF_RELEASE, "") ?: ""
                val savedVersion = prefs.getString(APApplication.PREF_UTS_SPOOF_VERSION, "") ?: ""
                if (savedRelease.isNotBlank() || savedVersion.isNotBlank()) {
                    Natives.utsSet(
                        savedRelease.ifBlank { null },
                        savedVersion.ifBlank { null }
                    )
                }
            }
        }
    }

    fun onPathHideChange(enabled: Boolean) {
        state.isPathHideEnabled.value = enabled
        scope.launch(Dispatchers.IO) {
            setPathHideEnabled(enabled)
            val rc = Natives.pathHideEnable(enabled)
            withContext(Dispatchers.Main) {
                if (rc < 0) {
                    snackBarHost.showSnackbar(context.getString(R.string.path_hide_failed, rc.toInt()))
                } else {
                    snackBarHost.showSnackbar(
                        context.getString(if (enabled) R.string.path_hide_enabled else R.string.path_hide_disabled)
                    )
                }
            }
        }
    }

    fun onPathHidePathsChange(value: String) {
        state.pathHidePaths.value = value
    }

    fun onPathHideSave() {
        val currentPaths = normalizePathHidePaths(state.pathHidePaths.value)
        scope.launch(Dispatchers.IO) {
            // Save to config file for persistence
            writePathHidePaths(currentPaths)
            // Clear existing kernel paths and re-add
            Natives.pathHideClear()
            if (currentPaths.isNotBlank()) {
                currentPaths.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { path ->
                        Natives.pathHideAdd(path)
                    }
            }
            withContext(Dispatchers.Main) {
                snackBarHost.showSnackbar(context.getString(R.string.path_hide_applied))
            }
        }
    }

    fun onPathHideUidModeChange(enabled: Boolean) {
        state.isPathHideUidMode.value = enabled
        scope.launch(Dispatchers.IO) {
            setPathHideUidMode(enabled)
            Natives.pathHideUidMode(enabled)
            withContext(Dispatchers.Main) {
                snackBarHost.showSnackbar(
                    context.getString(if (enabled) R.string.path_hide_uid_mode_enabled else R.string.path_hide_uid_mode_disabled)
                )
            }
        }
    }

    fun onPathHideFilterSystemChange(enabled: Boolean) {
        if (enabled) {
            state.showFilterSystemWarningDialog.value = true
        } else {
            state.isPathHideFilterSystem.value = false
            scope.launch(Dispatchers.IO) {
                setPathHideFilterSystem(false)
                Natives.pathHideFilterSystem(false)
                withContext(Dispatchers.Main) {
                    snackBarHost.showSnackbar(
                        context.getString(R.string.path_hide_filter_system_disabled)
                    )
                }
            }
        }
    }

    fun onConfirmFilterSystem() {
        state.isPathHideFilterSystem.value = true
        scope.launch(Dispatchers.IO) {
            setPathHideFilterSystem(true)
            Natives.pathHideFilterSystem(true)
            withContext(Dispatchers.Main) {
                snackBarHost.showSnackbar(
                    context.getString(R.string.path_hide_filter_system_enabled)
                )
            }
        }
    }

    fun onUidToggle(uid: Int) {
        scope.launch(Dispatchers.IO) {
            val current = state.selectedUids.value
            val newSet = if (uid in current) {
                Natives.pathHideUidRemove(uid)
                current - uid
            } else {
                Natives.pathHideUidAdd(uid)
                current + uid
            }
            writePathHideUids(newSet.joinToString("\n"))
            withContext(Dispatchers.Main) {
                state.selectedUids.value = newSet
            }
        }
    }

    fun onUmountEnabledChange(enabled: Boolean) {
        state.isUmountEnabled.value = enabled
        scope.launch(Dispatchers.IO) {
            val config = UmountConfig(enabled = enabled, paths = state.umountPaths.value)
            val success = UmountConfigManager.saveConfig(context, config)
            withContext(Dispatchers.Main) {
                if (success) {
                    showToast(context, context.getString(R.string.umount_config_save_success))
                } else {
                    showToast(context, context.getString(R.string.umount_config_save_failed))
                }
            }
        }
    }

    fun onUmountPathsChange(value: String) {
        state.umountPaths.value = value
    }

    fun onUmountSave() {
        val currentEnabled = state.isUmountEnabled.value
        val currentPaths = state.umountPaths.value
        scope.launch(Dispatchers.IO) {
            val config = UmountConfig(enabled = currentEnabled, paths = currentPaths)
            val success = UmountConfigManager.saveConfig(context, config)
            withContext(Dispatchers.Main) {
                if (success) {
                    showToast(context, context.getString(R.string.umount_config_save_success))
                } else {
                    showToast(context, context.getString(R.string.umount_config_save_failed))
                }
            }
        }
    }

    fun onNetIsolateChange(enabled: Boolean) {
        state.isNetIsolateEnabled.value = enabled
        scope.launch(Dispatchers.IO) {
            setNetIsolateEnabled(enabled)
            Natives.netIsolateEnable(enabled)
            withContext(Dispatchers.Main) {
                snackBarHost.showSnackbar(
                    context.getString(if (enabled) R.string.netisolate_enable else R.string.netisolate_disable)
                )
            }
        }
    }

    fun onNiUidToggle(uid: Int) {
        scope.launch(Dispatchers.IO) {
            val current = state.niSelectedUids.value
            val newSet = if (uid in current) {
                Natives.netIsolateUidRemove(uid)
                current - uid
            } else {
                Natives.netIsolateUidAdd(uid)
                current + uid
            }
            writeNetIsolateUids(newSet.joinToString("\n"))
            withContext(Dispatchers.Main) {
                state.niSelectedUids.value = newSet
            }
        }
    }
}
