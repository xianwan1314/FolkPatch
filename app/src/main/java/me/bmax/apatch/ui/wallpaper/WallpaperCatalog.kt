package me.bmax.apatch.ui.wallpaper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.bmax.apatch.apApp
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Produces [WallpaperItem]s for a provider.
 *
 * If the provider declares a [DirectSource] for the device, items are built locally
 * from a random id (zero API calls, immune to rate limiting). Otherwise the
 * provider's dynamic endpoint is queried in parallel (the fallback path).
 */
class WallpaperCatalog(
    private val client: OkHttpClient = apApp.okhttpClient,
    private val cache: WallpaperGalleryCache = WallpaperGalleryCache(File(apApp.filesDir, "wallpaper-gallery")),
) {

    suspend fun clearCache() = withContext(Dispatchers.IO) { cache.withLock { cache.clear() } }

    suspend fun load(
        provider: WallpaperProvider,
        device: WallpaperDevice,
        count: Int,
        exclude: Set<String>
    ): List<WallpaperItem> = withContext(Dispatchers.IO) {
        cache.withLock { loadLocked(provider, device, count, exclude) }
    }

    private suspend fun loadLocked(
        provider: WallpaperProvider,
        device: WallpaperDevice,
        count: Int,
        exclude: Set<String>
    ): List<WallpaperItem> {
        if (count <= 0) return emptyList()
        if (device == WallpaperDevice.MIXED) {
            val phoneCount = count / 2
            val phoneItems = loadLocked(provider, WallpaperDevice.PHONE, phoneCount, exclude)
            val tabletItems = loadLocked(
                provider,
                WallpaperDevice.TABLET,
                count - phoneCount,
                exclude + phoneItems.map { it.url }
            )
            return phoneItems + tabletItems
        }
        val saved = cache.read(provider.id, device.key)
        val available = saved.filter { it.url !in exclude }.take(count)
        if (available.isNotEmpty()) return available
        val seen = exclude + saved.map { it.url }
        val items = when {
            provider.responseType.equals("redirect", ignoreCase = true) ->
                loadRedirect(provider, device, count, seen)
            provider.directFor(device.key) != null ->
                storeImages(loadDirect(provider.directFor(device.key)!!, provider, device, count, seen))
            else -> storeImages(loadRemote(provider, device, count, seen))
        }
        currentCoroutineContext().ensureActive()
        if (items.isNotEmpty()) cache.append(provider.id, device.key, items)
        return items
    }

    private suspend fun storeImages(items: List<WallpaperItem>): List<WallpaperItem> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENCY)
        items.map { item ->
            async {
                semaphore.withPermit {
                    attempt {
                        client.newCall(Request.Builder().url(item.url).build()).execute().use { response ->
                            currentCoroutineContext().ensureActive()
                            check(response.isSuccessful) { "HTTP ${response.code}" }
                            cache.storeImage(item, response.body.byteStream())
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /**
     * Resolve each random endpoint once, before exposing an item. Reusing the
     * endpoint itself would select a new image for previews and downloads.
     */
    private suspend fun loadRedirect(
        provider: WallpaperProvider,
        device: WallpaperDevice,
        count: Int,
        exclude: Set<String>
    ): List<WallpaperItem> = coroutineScope {
        val path = provider.devicePaths[device.key] ?: return@coroutineScope emptyList()
        if (count <= 0) return@coroutineScope emptyList()
        val semaphore = Semaphore(MAX_CONCURRENCY)
        val items = (0 until count).map {
            async {
                semaphore.withPermit {
                    attempt {
                        val nonce = System.nanoTime().toString(36) + Random.nextInt(0x1000000).toString(36)
                        val endpoint = Request.Builder().url(provider.baseUrl + path).build().url
                            .newBuilder()
                            .apply { provider.query.forEach { (key, value) -> addQueryParameter(key, value) } }
                            .addQueryParameter("_", nonce)
                            .build()
                        client.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
                            currentCoroutineContext().ensureActive()
                            // A non-redirecting random endpoint has no stable image identity.
                            if (!response.isSuccessful || response.request.url == endpoint) return@use null
                            val item = WallpaperItem(
                                id = nonce,
                                url = response.request.url.toString(),
                                providerId = provider.id,
                                deviceKey = device.key
                            )
                            if (item.url in exclude) null else cache.storeImage(item, response.body.byteStream())
                        }
                    }
                }
            }
        }.awaitAll()
        val seen = HashSet(exclude)
        items.filterNotNull().filter { seen.add(it.url) }
    }

    /** API-free path: pick random ids and build direct image URLs. */
    private fun loadDirect(
        source: DirectSource,
        provider: WallpaperProvider,
        device: WallpaperDevice,
        count: Int,
        exclude: Set<String>
    ): List<WallpaperItem> {
        val range = source.maxId - source.minId + 1
        if (range <= 0 || count <= 0) return emptyList()
        val seen = HashSet(exclude)
        val items = ArrayList<WallpaperItem>(count)
        val maxAttempts = count * 4 + 8
        var attempts = 0
        while (items.size < count && attempts < maxAttempts) {
            attempts++
            val idNum = Random.nextInt(source.minId, source.maxId + 1)
            val id = "img$idNum"
            val url = source.imagePattern.replace("{id}", idNum.toString())
            if (!seen.add(url)) continue
            items += WallpaperItem(
                id = id,
                url = url,
                providerId = provider.id,
                deviceKey = device.key
            )
        }
        return items
    }

    /** Fallback path: query the provider's dynamic endpoint concurrently. */
    private suspend fun loadRemote(
        provider: WallpaperProvider,
        device: WallpaperDevice,
        count: Int,
        exclude: Set<String>
    ): List<WallpaperItem> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENCY)
        val urls = (0 until count).map {
            async {
                semaphore.withPermit {
                    attempt { fetchUrl(provider, device) }
                }
            }
        }.awaitAll()
        val seen = HashSet<String>()
        urls.asSequence()
            .filterNotNull()
            .map { it.trim() }
            .filter { it.startsWith("http") && seen.add(it) && it !in exclude }
            .map {
                WallpaperItem(
                    id = it.substringAfterLast('/').substringBefore('?').substringBeforeLast('.', it),
                    url = it,
                    providerId = provider.id,
                    deviceKey = device.key
                )
            }
            .toList()
    }

    private fun fetchUrl(provider: WallpaperProvider, device: WallpaperDevice): String? {
        val path = provider.devicePaths[device.key] ?: return null
        val builder = StringBuilder(provider.baseUrl).append(path)
        if (provider.query.isNotEmpty()) {
            builder.append('?')
            builder.append(
                provider.query.entries.joinToString("&") { (key, value) ->
                    "$key=${URLEncoder.encode(value, "UTF-8")}"
                }
            )
        }
        val request = Request.Builder()
            .url(builder.toString())
            .header("Accept", "text/plain,*/*")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.string().trim()
            if (body.isEmpty()) return null
            when (provider.responseType.lowercase()) {
                "json" -> runCatching { JSONObject(body).optString(provider.urlField) }.getOrNull()
                else -> body
            }
        }
    }

    companion object {
        private const val MAX_CONCURRENCY = 3
    }
}
