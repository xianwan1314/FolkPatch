package me.bmax.apatch.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import me.bmax.apatch.APApplication

object LauncherIconUtils {
    private const val MAIN_ACTIVITY = ".ui.MainActivityDefault"
    private const val ALIAS_ACTIVITY = ".ui.MainActivityAlias"
    private const val ALIAS_ACTIVITY_SU = ".ui.MainActivityAliasSu"
    private const val ALIAS_ACTIVITY_ALT_SU = ".ui.MainActivityAliasAltSu"
    private const val ALIAS_ACTIVITY_ANIME = ".ui.MainActivityAliasAnime"
    private const val ALIAS_ACTIVITY_ANIME_SU = ".ui.MainActivityAliasAnimeSu"

    const val PREF_ICON_STYLE = "launcher_icon_style"
    const val ICON_STYLE_ANIME = "anime"
    const val ICON_STYLE_GEOMETRY = "geometry"
    const val ICON_STYLE_APATCH = "apatch"

    /**
     * Resolves the active icon style. Falls back to the legacy use_alt_icon
     * toggle for users upgrading from the two-icon build, defaulting fresh
     * installs to the anime icon.
     */
    fun currentStyle(context: Context): String {
        val prefs = APApplication.sharedPreferences
        prefs.getString(PREF_ICON_STYLE, null)?.let { return it }
        val style = if (prefs.getBoolean("use_alt_icon", false)) ICON_STYLE_APATCH else ICON_STYLE_ANIME
        prefs.edit().putString(PREF_ICON_STYLE, style).apply()
        return style
    }

    fun setStyle(context: Context, style: String) {
        APApplication.sharedPreferences.edit().putString(PREF_ICON_STYLE, style).apply()
        updateLauncherState(context)
    }

    /** ComponentName of the launcher alias that is currently enabled. */
    fun enabledLauncherComponent(context: Context): ComponentName {
        val prefs = APApplication.sharedPreferences
        val style = currentStyle(context)
        val appName = prefs.getString("desktop_app_name", "FolkPatch")
        val isSu = appName == "FPatch"
        return componentFor(style, isSu, context)
    }

    private fun componentFor(style: String, isSu: Boolean, context: Context): ComponentName {
        val basePackage = APApplication::class.java.`package`?.name ?: "me.bmax.apatch"
        fun alias(name: String) = ComponentName(context.packageName, basePackage + name)
        return when {
            style == ICON_STYLE_ANIME && isSu -> alias(ALIAS_ACTIVITY_ANIME_SU)
            style == ICON_STYLE_ANIME -> alias(ALIAS_ACTIVITY_ANIME)
            style == ICON_STYLE_APATCH && isSu -> alias(ALIAS_ACTIVITY_ALT_SU)
            style == ICON_STYLE_APATCH -> alias(ALIAS_ACTIVITY)
            isSu -> alias(ALIAS_ACTIVITY_SU)
            else -> alias(MAIN_ACTIVITY)
        }
    }

    fun updateLauncherState(context: Context) {
        val style = currentStyle(context)
        val pm = context.packageManager
        val basePackage = APApplication::class.java.`package`?.name ?: "me.bmax.apatch"

        val allComponents = listOf(
            MAIN_ACTIVITY, ALIAS_ACTIVITY, ALIAS_ACTIVITY_SU,
            ALIAS_ACTIVITY_ALT_SU, ALIAS_ACTIVITY_ANIME, ALIAS_ACTIVITY_ANIME_SU
        ).map { name -> ComponentName(context.packageName, basePackage + name) }

        val targetComponent = enabledLauncherComponent(context)

        try {
            // Enable target
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable others
            allComponents.filter { it != targetComponent }.forEach {
                pm.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applySaved(context: Context) {
        updateLauncherState(context)
    }
}
