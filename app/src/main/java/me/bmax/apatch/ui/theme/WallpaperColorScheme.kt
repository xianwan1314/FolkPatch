package me.bmax.apatch.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import me.bmax.apatch.ui.component.themeColorOptions
import me.bmax.apatch.ui.theme.tokens.FolkThemeCatalog

// 壁纸有效亮度低于该阈值时，内容采用深色主题的中性色（浅色文字）
private const val WALLPAPER_DARK_THRESHOLD = 0.5f

/**
 * Builds the raw Material3 [ColorScheme] for the requested light/dark mode, following the
 * user's color-generation mode (custom MaterialKolor, system dynamic, or classic catalog).
 */
internal fun generateColorScheme(
    context: Context,
    darkTheme: Boolean,
    colorGenerationMode: String?,
    dynamicColor: Boolean,
    customColorScheme: String?,
    colorStandard: String?,
    colorStyle: String?,
    contrastLevel: Double,
): ColorScheme {
    return when {
        // Custom dynamic generation (MaterialKolor) with system wallpaper seed
        colorGenerationMode == "custom" && dynamicColor -> {
            val standard = ColorStandard.fromName(colorStandard)
            val style = ColorStyle.fromName(colorStyle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ColorSchemeGenerator.generateFromContext(
                    context, darkTheme, style.paletteStyle, standard.specVersion, contrastLevel
                )
            } else {
                // Fallback: system dynamic color not available, use selected color as seed
                val seedOption = themeColorOptions.find { it.key == (customColorScheme ?: "indigo") }
                val seedColor = if (darkTheme) {
                    seedOption?.darkPrimary ?: Color(0xFFBAC3FF)
                } else {
                    seedOption?.lightPrimary ?: Color(0xFF4355B9)
                }
                ColorSchemeGenerator.generate(
                    seedColor, darkTheme, style.paletteStyle, standard.specVersion, contrastLevel
                )
            }
        }
        // Custom dynamic generation (MaterialKolor) with selected color seed
        colorGenerationMode == "custom" -> {
            val seedOption = themeColorOptions.find { it.key == (customColorScheme ?: "indigo") }
            val seedColor = if (darkTheme) {
                seedOption?.darkPrimary ?: Color(0xFFBAC3FF)
            } else {
                seedOption?.lightPrimary ?: Color(0xFF4355B9)
            }
            val standard = ColorStandard.fromName(colorStandard)
            val style = ColorStyle.fromName(colorStyle)
            ColorSchemeGenerator.generate(
                seedColor, darkTheme, style.paletteStyle, standard.specVersion, contrastLevel
            )
        }
        // System dynamic color (standard Material3 wallpaper extraction)
        dynamicColor -> {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                else -> FolkThemeCatalog.scheme("blue", darkTheme)
            }
        }
        // Classic themes, from the catalog.
        else -> FolkThemeCatalog.scheme(customColorScheme, darkTheme)
    }
}

/**
 * Applies the custom-background adaptation (transparent background + wallpaper-aware neutral
 * content colors) or the AMOLED override on top of [baseColorScheme].
 *
 * 在自定义壁纸模式下，中性色/容器色会跟随壁纸的有效明暗（亮底→深字，暗底→浅字），
 * 以保证半透明容器与其上文字的对比度；强调色及其不透明容器仍沿用 [baseColorScheme]。
 *
 * 返回 [WallpaperThemeResult]：适配后的方案，以及为保证文字对比度而可能需要提高的
 * 壁纸遮罩 dim（仅在选用浅色文字时需要）。
 */
internal fun adaptColorScheme(
    context: Context,
    baseColorScheme: ColorScheme,
    darkTheme: Boolean,
    amoledTheme: Boolean,
    useCustomBackground: Boolean,
    activeBackgroundUri: String?,
    colorGenerationMode: String?,
    dynamicColor: Boolean,
    customColorScheme: String?,
    colorStandard: String?,
    colorStyle: String?,
    contrastLevel: Double,
): WallpaperThemeResult {
    if (!useCustomBackground) {
        val scheme = if (darkTheme && amoledTheme) baseColorScheme.toAmoled() else baseColorScheme
        return WallpaperThemeResult(scheme, null)
    }

    val wallpaperDim = BackgroundConfig.getEffectiveBackgroundDim(darkTheme)
    val effectiveLuminance = BackgroundConfig.wallpaperLuminanceFor(activeBackgroundUri)
        ?.let { it * (1f - wallpaperDim) }
    // 夜间模式始终保持深色中性方案：不因壁纸偏亮而把整套 UI 翻成浅色（避免「发白」），
    // 可读性交给下面的对比度保护抬高遮罩。浅色模式仍按壁纸明暗决定，以照顾暗壁纸。
    val useLightContent = if (darkTheme) {
        true
    } else {
        effectiveLuminance?.let { it < WALLPAPER_DARK_THRESHOLD } ?: false
    }

    val neutralScheme = if (useLightContent == darkTheme) {
        baseColorScheme
    } else {
        generateColorScheme(
            context = context,
            darkTheme = useLightContent,
            colorGenerationMode = colorGenerationMode,
            dynamicColor = dynamicColor,
            customColorScheme = customColorScheme,
            colorStandard = colorStandard,
            colorStyle = colorStyle,
            contrastLevel = contrastLevel,
        )
    }

    val opacity = BackgroundConfig.customBackgroundOpacity
    val adapted = baseColorScheme.copy(
        background = Color.Transparent,
        surface = neutralScheme.surface.copy(alpha = opacity),
        surfaceDim = neutralScheme.surfaceDim,
        surfaceBright = neutralScheme.surfaceBright,
        surfaceContainer = neutralScheme.surfaceContainer.copy(alpha = opacity),
        surfaceContainerLow = neutralScheme.surfaceContainerLow,
        surfaceContainerLowest = neutralScheme.surfaceContainerLowest,
        surfaceContainerHigh = neutralScheme.surfaceContainerHigh,
        surfaceContainerHighest = neutralScheme.surfaceContainerHighest,
        surfaceVariant = neutralScheme.surfaceVariant,
        onBackground = neutralScheme.onBackground,
        onSurface = neutralScheme.onSurface,
        onSurfaceVariant = neutralScheme.onSurfaceVariant,
        outline = neutralScheme.outline,
        outlineVariant = neutralScheme.outlineVariant,
        inverseSurface = neutralScheme.inverseSurface,
        inverseOnSurface = neutralScheme.inverseOnSurface,
        // secondaryContainer 保持 base（不透明）：它由 onSecondaryContainer 配对使用，
        // 一旦降 alpha 就会让选中态文字直接压在壁纸上而失去对比度。
    )

    // 浅色模式：浅色中性内容压在暗壁纸上时，用对比度保护兜底（通常已是暗壁纸，基本不改动）。
    // 夜间模式：UI 已固定为深色，遮罩完全交给用户的夜间暗度滑块，不做自动加深。
    val renderDim = if (useLightContent && !darkTheme) {
        guardedDim(wallpaperDim, adapted) ?: wallpaperDim
    } else {
        wallpaperDim
    }

    return WallpaperThemeResult(adapted, renderDim)
}
