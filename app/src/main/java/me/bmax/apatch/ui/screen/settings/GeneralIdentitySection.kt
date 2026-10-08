package me.bmax.apatch.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.folk.FolkSettingsGroup
import me.bmax.apatch.ui.component.folk.FolkSettingsSection
import me.bmax.apatch.ui.component.folk.FolkValuePreference

@Composable
fun GeneralIdentitySection(
    flat: Boolean,
    highlightKey: String?,
    launcherIconTitle: String,
    launcherIconSummary: String,
    showLauncherIconDialog: MutableState<Boolean>,
    appTitleTitle: String,
    appTitleLabel: String,
    currentAppTitle: String,
    showAppTitleDialog: MutableState<Boolean>,
    customAppTitleTitle: String,
    currentCustomAppTitle: String,
    showCustomAppTitleDialog: MutableState<Boolean>,
    desktopAppNameTitle: String,
    currentDesktopAppName: String,
    showDesktopAppNameDialog: MutableState<Boolean>,
    dpiTitle: String,
    dpiValue: String,
    showDpiDialog: MutableState<Boolean>,
) {
    FolkSettingsSection(title = stringResource(R.string.settings_section_general_identity)) {
     FolkSettingsGroup(flat = flat, highlightKey = highlightKey) {

        item(key = "general_alt_icon") {
            FolkValuePreference(
                icon = Icons.Outlined.Android,
                title = launcherIconTitle,
                summary = launcherIconSummary,
                onClick = { showLauncherIconDialog.value = true },
            )
        }

        item(key = "general_app_title") {
            FolkValuePreference(
                icon = Icons.Outlined.Label,
                title = appTitleTitle,
                summary = appTitleLabel,
                onClick = { showAppTitleDialog.value = true },
            )
        }

        item(key = "general_custom_app_title", visible = currentAppTitle == "custom") {
            FolkValuePreference(
                icon = Icons.Outlined.Edit,
                title = customAppTitleTitle,
                summary = currentCustomAppTitle,
                onClick = { showCustomAppTitleDialog.value = true },
            )
        }

        item(key = "general_desktop_app_name") {
            FolkValuePreference(
                icon = Icons.Outlined.PhoneAndroid,
                title = desktopAppNameTitle,
                summary = currentDesktopAppName,
                onClick = { showDesktopAppNameDialog.value = true },
            )
        }

        item(key = "general_dpi") {
            FolkValuePreference(
                icon = Icons.Outlined.FormatSize,
                title = dpiTitle,
                summary = dpiValue,
                onClick = { showDpiDialog.value = true },
            )
        }

     }
    }
}
