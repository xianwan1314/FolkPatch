package me.bmax.apatch.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.ramcosta.composedestinations.generated.destinations.APModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.KPModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import me.bmax.apatch.R

enum class BottomBarDestination(
    val direction: DirectionDestinationSpec,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val kPatchRequired: Boolean,
    val aPatchRequired: Boolean,
) {
    Home(
        HomeScreenDestination,
        R.string.home,
        Icons.Rounded.Cottage,
        Icons.Outlined.Cottage,
        false,
        false
    ),
    KModule(
        KPModuleScreenDestination,
        R.string.kpm,
        Icons.Rounded.Memory,
        Icons.Outlined.Memory,
        true,
        false
    ),
    SuperUser(
        SuperUserScreenDestination,
        R.string.su_title,
        Icons.Rounded.AdminPanelSettings,
        Icons.Outlined.AdminPanelSettings,
        true,
        false
    ),
    AModule(
        APModuleScreenDestination,
        R.string.apm,
        Icons.Rounded.Extension,
        Icons.Outlined.Extension,
        false,
        true
    ),
    Settings(
        SettingScreenDestination,
        R.string.settings,
        Icons.Rounded.Tune,
        Icons.Outlined.Tune,
        false,
        false
    )
}
