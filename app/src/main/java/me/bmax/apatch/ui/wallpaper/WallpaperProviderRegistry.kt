package me.bmax.apatch.ui.wallpaper

import android.content.Context
import org.json.JSONObject

/**
 * Loads the data-driven wallpaper provider manifest from assets.
 *
 * Adding or changing a provider is a JSON-only change; no Kotlin edits are needed.
 */
object WallpaperProviderRegistry {

    private const val ASSET = "wallpaper/providers.json"

    fun load(context: Context): List<WallpaperProvider> {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val array = JSONObject(text).optJSONArray("providers") ?: return emptyList()
        val result = ArrayList<WallpaperProvider>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            if (id.isEmpty()) continue
            result += WallpaperProvider(
                id = id,
                name = obj.optString("name").ifBlank { id },
                homepage = obj.optString("homepage"),
                baseUrl = obj.optString("baseUrl").trimEnd('/'),
                devicePaths = obj.optJSONObject("devicePaths").toStringMap(),
                responseType = obj.optString("responseType", "text"),
                urlField = obj.optString("urlField", "url"),
                query = obj.optJSONObject("query").toStringMap(),
                idParam = obj.optString("idParam").ifBlank { null },
                direct = obj.optJSONObject("direct").toDirectMap()
            )
        }
        return result
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val map = LinkedHashMap<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = optString(key)
        }
        return map
    }

    private fun JSONObject?.toDirectMap(): Map<String, DirectSource> {
        if (this == null) return emptyMap()
        val map = LinkedHashMap<String, DirectSource>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = optJSONObject(key) ?: continue
            val pattern = obj.optString("imagePattern").trim()
            if (pattern.isEmpty() || !pattern.contains("{id}")) continue
            val maxId = obj.optInt("maxId", 0)
            if (maxId <= 0) continue
            val minId = obj.optInt("minId", 1).coerceAtLeast(1)
            if (minId > maxId) continue
            map[key] = DirectSource(pattern, minId, maxId)
        }
        return map
    }
}
