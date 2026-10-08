package me.bmax.apatch.ui.wallpaper

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistent gallery files have no expiry; only an explicit refresh clears them. */
class WallpaperGalleryCache(private val root: File) {
    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun read(providerId: String, deviceKey: String): List<WallpaperItem> {
        val index = indexFile(providerId, deviceKey)
        if (!index.isFile) return emptyList()
        return runCatching {
            DataInputStream(index.inputStream().buffered()).use { input ->
                check(input.readInt() == VERSION)
                val count = input.readInt()
                check(count in 0..100_000)
                List(count) {
                    val id = input.readUTF()
                    val url = input.readUTF()
                    WallpaperItem(id, url, providerId, deviceKey, localUri = imageFile(url).toURI().toString())
                }.filter { imageFile(it.url).let { file -> file.isFile && file.length() > 0 } }
            }
        }.getOrDefault(emptyList())
    }

    fun append(providerId: String, deviceKey: String, items: List<WallpaperItem>) {
        val combined = (read(providerId, deviceKey) + items).distinctBy { it.url }
        writeAtomically(indexFile(providerId, deviceKey)) { temporary ->
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeInt(VERSION)
                output.writeInt(combined.size)
                combined.forEach {
                    output.writeUTF(it.id)
                    output.writeUTF(it.url)
                }
            }
        }
    }

    fun storeImage(item: WallpaperItem, input: InputStream): WallpaperItem {
        val target = imageFile(item.url)
        if (!target.isFile || target.length() == 0L) {
            writeAtomically(target) { temporary ->
                temporary.outputStream().buffered().use { output ->
                    check(input.copyTo(output) > 0) { "Empty wallpaper image" }
                }
            }
        }
        return item.copy(localUri = target.toURI().toString())
    }

    fun clear() {
        check(!root.exists() || root.deleteRecursively()) { "Cannot clear wallpaper cache" }
    }

    private fun imageFile(url: String) = File(root, "images/${digest(url)}")

    private fun indexFile(providerId: String, deviceKey: String) =
        File(root, "catalogs/${digest("$providerId/$deviceKey")}.bin")

    private fun writeAtomically(target: File, write: (File) -> Unit) {
        check(target.parentFile!!.let { it.mkdirs() || it.isDirectory }) { "Cannot create wallpaper cache" }
        val temporary = File(target.parentFile, "${target.name}.${UUID.randomUUID()}.part")
        try {
            write(temporary)
            check(temporary.renameTo(target)) { "Cannot save wallpaper cache" }
        } finally {
            temporary.delete()
        }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        private const val VERSION = 1
        // Serializes catalog changes even if a new screen creates a second catalog instance.
        private val mutex = Mutex()
    }
}
