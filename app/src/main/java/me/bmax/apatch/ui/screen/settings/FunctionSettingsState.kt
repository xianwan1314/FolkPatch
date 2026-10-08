package me.bmax.apatch.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import me.bmax.apatch.APApplication
import me.bmax.apatch.util.ShizukuServiceManager

/**
 * State bundle for [FunctionSettingsScreen].
 *
 * The fields are plain [MutableState] instances created through rememberSaveable, so
 * the save/restore behaviour is unchanged; callers can read/write them with `by` delegation.
 */
@Stable
internal class FunctionSettingsState(
    val isHideServiceEnabled: MutableState<Boolean>,
    val isKernelSpoofEnabled: MutableState<Boolean>,
    val kernelSpoofVersion: MutableState<String>,
    val kernelSpoofBuildTime: MutableState<String>,
    val isUmountEnabled: MutableState<Boolean>,
    val umountPaths: MutableState<String>,
    val isNetIsolateEnabled: MutableState<Boolean>,
    val niSelectedUids: MutableState<Set<Int>>,
    val isPathHideEnabled: MutableState<Boolean>,
    val pathHidePaths: MutableState<String>,
    val isPathHideUidMode: MutableState<Boolean>,
    val isPathHideFilterSystem: MutableState<Boolean>,
    val showFilterSystemWarningDialog: MutableState<Boolean>,
    val selectedUids: MutableState<Set<Int>>,
    val jailbreakEnabled: MutableState<Boolean>,
    val showJailbreakSoftRebootDialog: MutableState<Boolean>,
    val isShizukuEnabled: MutableState<Boolean>,
    val isShizukuRunning: MutableState<Boolean>,
)

@Composable
internal fun rememberFunctionSettingsState(): FunctionSettingsState = FunctionSettingsState(
    isHideServiceEnabled = rememberSaveable { mutableStateOf(false) },
    isKernelSpoofEnabled = rememberSaveable { mutableStateOf(false) },
    kernelSpoofVersion = rememberSaveable { mutableStateOf("") },
    kernelSpoofBuildTime = rememberSaveable { mutableStateOf("") },
    isUmountEnabled = rememberSaveable { mutableStateOf(false) },
    umountPaths = rememberSaveable { mutableStateOf("") },
    isNetIsolateEnabled = rememberSaveable { mutableStateOf(false) },
    niSelectedUids = rememberSaveable { mutableStateOf(emptySet<Int>()) },
    isPathHideEnabled = rememberSaveable { mutableStateOf(false) },
    pathHidePaths = rememberSaveable { mutableStateOf("") },
    isPathHideUidMode = rememberSaveable { mutableStateOf(false) },
    isPathHideFilterSystem = rememberSaveable { mutableStateOf(false) },
    showFilterSystemWarningDialog = rememberSaveable { mutableStateOf(false) },
    selectedUids = rememberSaveable { mutableStateOf(emptySet<Int>()) },
    jailbreakEnabled = rememberSaveable {
        mutableStateOf(APApplication.sharedPreferences.getBoolean("jailbreak_enabled", false))
    },
    showJailbreakSoftRebootDialog = rememberSaveable { mutableStateOf(false) },
    isShizukuEnabled = rememberSaveable { mutableStateOf(ShizukuServiceManager.isEnabled()) },
    isShizukuRunning = rememberSaveable { mutableStateOf(false) },
)
