package me.bmax.apatch.ui.screen.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.component.folk.FolkSettingsGroup
import me.bmax.apatch.ui.component.folk.FolkSettingsSection
import me.bmax.apatch.ui.component.folk.FolkSwitchPreference
import me.bmax.apatch.ui.component.folk.FolkValuePreference
import me.bmax.apatch.ui.theme.refreshTheme

@Composable
fun GeneralInterfaceSection(
    flat: Boolean,
    highlightKey: String?,
    folkXEngineTitle: String,
    folkXEngineSummary: String,
    folkXEngineEnabled: Boolean,
    onFolkXEngineEnabledChange: (Boolean) -> Unit,
    currentType: String,
    currentSpeed: Float,
    predictiveBackTitle: String,
    predictiveBackSummary: String,
    predictiveBackEnabled: Boolean,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    showFolkXAnimationTypeDialog: MutableState<Boolean>,
    showFolkXAnimationSpeedDialog: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val prefs = APApplication.sharedPreferences

    FolkSettingsSection(title = stringResource(R.string.settings_section_general_interface)) {
     FolkSettingsGroup(flat = flat, highlightKey = highlightKey) {

        item(key = "general_folkx_engine") {
            FolkSwitchPreference(
                icon = Icons.Outlined.AutoAwesome,
                title = folkXEngineTitle,
                summary = folkXEngineSummary,
                checked = folkXEngineEnabled,
                onCheckedChange = {
                    onFolkXEngineEnabledChange(it)
                    prefs.edit().putBoolean("folkx_engine_enabled", it).apply()
                },
            )
        }

        item(key = "general_folkx_animation_type", visible = folkXEngineEnabled) {
            val animationTypeLabel = when (currentType) {
                "linear" -> R.string.settings_folkx_animation_linear
                "spatial" -> R.string.settings_folkx_animation_spatial
                "fade" -> R.string.settings_folkx_animation_fade
                "vertical" -> R.string.settings_folkx_animation_vertical
                "diagonal" -> R.string.settings_folkx_animation_diagonal
                else -> R.string.settings_folkx_animation_linear
            }

            FolkValuePreference(
                icon = Icons.Outlined.Animation,
                title = stringResource(R.string.settings_folkx_animation_type),
                summary = stringResource(animationTypeLabel),
                onClick = { showFolkXAnimationTypeDialog.value = true },
            )
        }

        item(key = "general_folkx_animation_speed", visible = folkXEngineEnabled) {
            FolkValuePreference(
                icon = Icons.Outlined.Speed,
                title = stringResource(R.string.settings_folkx_animation_speed),
                summary = "${currentSpeed}x",
                onClick = { showFolkXAnimationSpeedDialog.value = true },
            )
        }

        item(key = "general_predictive_back", visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            FolkSwitchPreference(
                icon = Icons.Outlined.ArrowBack,
                title = predictiveBackTitle,
                summary = predictiveBackSummary,
                checked = predictiveBackEnabled,
                onCheckedChange = {
                    onPredictiveBackEnabledChange(it)
                    prefs.edit { putBoolean("predictive_back_enabled", it) }
                    (context as? Activity)?.recreate()
                },
            )
        }

        item(key = "general_expressive_motion") {
            var expressiveMotion by remember { mutableStateOf(prefs.getBoolean("expressive_motion", false)) }
            FolkSwitchPreference(
                icon = Icons.Outlined.Waves,
                title = stringResource(R.string.settings_expressive_motion),
                summary = stringResource(R.string.settings_expressive_motion_summary),
                checked = expressiveMotion,
                onCheckedChange = {
                    expressiveMotion = it
                    prefs.edit { putBoolean("expressive_motion", it) }
                    refreshTheme.value = true
                },
            )
        }

     }
    }
}
