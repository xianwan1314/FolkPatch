package me.bmax.apatch.ui.wallpaper

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WallpaperCatalogTest {
    private lateinit var server: HttpServer
    private lateinit var client: OkHttpClient
    private lateinit var provider: WallpaperProvider
    private lateinit var cacheRoot: File
    private val selections = AtomicInteger()
    private val requests = AtomicInteger()
    private var redirect = true
    private var failImage = false
    private var sameImage = false
    private var blockImage = false
    private val imageStarted = CountDownLatch(1)
    private val releaseImage = CountDownLatch(1)

    @Before
    fun setUp() {
        cacheRoot = Files.createTempDirectory("wallpaper-gallery-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            exchange.use {
                when (exchange.requestURI.path) {
                    "/random" -> {
                        assertTrue(exchange.requestURI.query.contains("pool=phone"))
                        val choice = if (sameImage) 1 else selections.incrementAndGet()
                        if (redirect) {
                            exchange.responseHeaders.add("Location", "/redirect/$choice")
                            exchange.sendResponseHeaders(302, -1)
                        } else {
                            val bytes = "random image $choice".toByteArray()
                            exchange.sendResponseHeaders(200, bytes.size.toLong())
                            exchange.responseBody.write(bytes)
                        }
                    }
                    else -> {
                        val choice = exchange.requestURI.path.substringAfterLast('/')
                            .substringBefore('.')
                        if (exchange.requestURI.path.startsWith("/redirect/")) {
                            exchange.responseHeaders.add("Location", "/images/$choice.png")
                            exchange.sendResponseHeaders(302, -1)
                        } else if (failImage) {
                            exchange.sendResponseHeaders(503, -1)
                        } else {
                            if (blockImage && choice == "1") {
                                imageStarted.countDown()
                                releaseImage.await(5, TimeUnit.SECONDS)
                            }
                            val bytes = "fixed image $choice".toByteArray()
                            exchange.responseHeaders.add("Content-Type", "image/png")
                            exchange.sendResponseHeaders(200, bytes.size.toLong())
                            exchange.responseBody.write(bytes)
                        }
                    }
                }
            }
        }
        server.start()
        client = OkHttpClient()
        provider = WallpaperProvider(
            id = "random", name = "Random", homepage = "",
            baseUrl = "http://127.0.0.1:${server.address.port}",
            devicePaths = mapOf("phone" to "/random", "tablet" to "/random"),
            responseType = "redirect", urlField = "", query = mapOf("pool" to "phone"),
            idParam = null,
        )
    }

    @After
    fun tearDown() {
        releaseImage.countDown()
        server.stop(0)
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        cacheRoot.deleteRecursively()
    }

    private fun catalog() = WallpaperCatalog(client, WallpaperGalleryCache(cacheRoot))

    @Test
    fun thumbnailPreviewAndDownloadKeepTheSameImageAfterRelativeRedirects() = runBlocking {
        val item = catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()).single()

        assertEquals("${provider.baseUrl}/images/1.png", item.url)
        assertTrue(item.fileName.endsWith(".png"))
        repeat(3) {
            assertArrayEquals("fixed image 1".toByteArray(), File(URI(item.imageUri)).readBytes())
        }
        assertEquals(1, selections.get())
        assertEquals(3, requests.get())
    }

    @Test
    fun deduplicatesAndExcludesResolvedImagesInsteadOfRandomEndpointNonces() = runBlocking {
        sameImage = true
        val catalog = catalog()
        val items = catalog.load(provider, WallpaperDevice.PHONE, 4, emptySet())

        assertEquals(1, items.size)
        assertTrue(catalog.load(provider, WallpaperDevice.PHONE, 4, setOf(items.single().url)).isEmpty())
    }

    @Test
    fun failedImageDoesNotExposeRandomEndpointAsFallback() = runBlocking {
        failImage = true
        assertTrue(catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()).isEmpty())
    }

    @Test
    fun nonRedirectingRandomResponseDoesNotExposeAnUnstableImageUrl() = runBlocking {
        redirect = false
        assertTrue(catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()).isEmpty())
    }

    @Test
    fun recreatedCatalogReadsListAndFullImageWithoutNetworkEvenWhenOffline() = runBlocking {
        val original = catalog().load(provider, WallpaperDevice.PHONE, 2, emptySet())
        assertEquals(2, original.size)
        val requestCount = requests.get()
        server.stop(0)

        val restored = catalog().load(provider, WallpaperDevice.PHONE, 2, emptySet())
        assertEquals(original, restored)
        assertEquals(requestCount, requests.get())
        restored.forEach { assertTrue(File(URI(it.imageUri)).length() > 0) }
    }

    @Test
    fun pagingUsesStoredPagesBeforeRequestingNewImages() = runBlocking {
        val first = catalog().load(provider, WallpaperDevice.PHONE, 2, emptySet())
        val second = catalog().load(provider, WallpaperDevice.PHONE, 2, first.map { it.url }.toSet())
        assertEquals(2, first.size)
        assertEquals(2, second.size)
        val requestCount = requests.get()

        assertEquals(first, catalog().load(provider, WallpaperDevice.PHONE, 2, emptySet()))
        assertEquals(second, catalog().load(provider, WallpaperDevice.PHONE, 2, first.map { it.url }.toSet()))
        assertEquals(requestCount, requests.get())
        assertEquals(4, selections.get())
    }

    @Test
    fun manualRefreshDeletesImagesAndFetchesANewSelection() = runBlocking {
        val catalog = catalog()
        val original = catalog.load(provider, WallpaperDevice.PHONE, 1, emptySet()).single()

        catalog.clearCache()
        assertTrue(!File(URI(original.imageUri)).exists())
        val refreshed = catalog.load(provider, WallpaperDevice.PHONE, 1, emptySet()).single()
        assertEquals("${provider.baseUrl}/images/2.png", refreshed.url)
        assertEquals(2, selections.get())
    }

    @Test
    fun phoneTabletAndMixedPoolsReuseTheirOwnPersistentEntries() = runBlocking {
        val phone = catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()).single()
        val tablet = catalog().load(provider, WallpaperDevice.TABLET, 1, emptySet()).single()
        val requestCount = requests.get()

        assertEquals(listOf(phone, tablet), catalog().load(provider, WallpaperDevice.MIXED, 2, emptySet()))
        assertEquals(requestCount, requests.get())
        assertEquals("tablet", tablet.deviceKey)
    }

    @Test
    fun overlappingCatalogLoadsShareTheFirstCachedSelection() = runBlocking {
        val results = List(3) {
            async { catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()).single() }
        }.awaitAll()

        assertTrue(results.all { it == results.first() })
        assertEquals(1, selections.get())
    }

    @Test
    fun truncatedCachedImageIsFetchedAgainInsteadOfReturningAnInvalidLocalFile() = runBlocking {
        val original = catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()).single()
        File(URI(original.imageUri)).writeBytes(byteArrayOf())

        val replacement = catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()).single()
        assertEquals("${provider.baseUrl}/images/2.png", replacement.url)
        assertTrue(File(URI(replacement.imageUri)).length() > 0)
    }

    @Test
    fun refreshWaitsForCancelledLoadAndPreventsOldImagesFromReappearing() = runBlocking {
        blockImage = true
        val loading = async(Dispatchers.Default) {
            catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet())
        }
        assertTrue(imageStarted.await(5, TimeUnit.SECONDS))
        loading.cancel()
        val clearing = async(Dispatchers.Default) { catalog().clearCache() }
        releaseImage.countDown()
        loading.join()
        clearing.await()

        assertTrue(!cacheRoot.exists())
        val newItems = catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet())
        assertEquals("${provider.baseUrl}/images/2.png", newItems.single().url)
        assertEquals(newItems, catalog().load(provider, WallpaperDevice.PHONE, 1, emptySet()))
    }
}
