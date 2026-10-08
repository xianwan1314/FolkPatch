package me.bmax.apatch.ui.wallpaper

import androidx.annotation.StringRes
import me.bmax.apatch.R

enum class WallpaperDevice(val key: String, @StringRes val labelRes: Int) {
    PHONE("phone", R.string.wallpaper_device_phone),
    TABLET("tablet", R.string.wallpaper_device_tablet),
    MIXED("mixed", R.string.wallpaper_device_mixed)
}

/**
 * A provider-declared, API-free source of images.
 *
 * The provider stores a URL pattern containing `{id}` plus an inclusive id range,
 * so a client can pick a random id locally and build a direct image URL without
 * touching the provider's (rate-limited) dynamic API.
 */
data class DirectSource(
    val imagePattern: String,
    val minId: Int,
    val maxId: Int
)

data class WallpaperProvider(
    val id: String,
    val name: String,
    val homepage: String,
    val baseUrl: String,
    val devicePaths: Map<String, String>,
    val responseType: String,
    val urlField: String,
    val query: Map<String, String>,
    val idParam: String?,
    val direct: Map<String, DirectSource> = emptyMap()
) {
    fun directFor(deviceKey: String): DirectSource? = direct[deviceKey]
}

data class WallpaperItem(
    val id: String,
    val url: String,
    val providerId: String,
    val deviceKey: String,
    val width: Int? = null,
    val height: Int? = null,
    val localUri: String? = null
) {
    val imageUri: String get() = localUri ?: url

    val aspectRatio: Float
        get() = if (width != null && height != null && width > 0 && height > 0) {
            width.toFloat() / height.toFloat()
        } else {
            1f
        }

    val fileName: String
        get() {
            val ext = url.substringAfterLast('/', "")
                .substringBefore('?')
                .substringAfterLast('.', "webp")
                .ifBlank { "webp" }
            return "FolkPatch_${deviceKey}_${id}.$ext"
        }
}
