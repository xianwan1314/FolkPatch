package me.bmax.apatch.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 壁纸模式下随壁纸有效明暗自适应后的内容色。
 *
 * 半透明容器（透明度来自 [BackgroundConfig.customBackgroundOpacity]）会让壁纸透出，
 * 此时容器语义色（如 onPrimary）不再保证对比度：卡片实际背景是壁纸，而不是原来的
 * 强调色容器。该 CompositionLocal 提供按壁纸明暗取反的中性内容色（亮底深字、暗底浅字）。
 *
 * 非壁纸模式下为 null，调用方应回退到原有语义色。
 */
val LocalWallpaperContentColor = compositionLocalOf<Color?> { null }

/** 同 [LocalWallpaperContentColor]，用于次要文字/标签。非壁纸模式为 null。 */
val LocalWallpaperContentVariant = compositionLocalOf<Color?> { null }
