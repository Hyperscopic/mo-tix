package com.hyperscopic.motix.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Posters and stills (TMDB) and trailer thumbnails (YouTube), streamed from the providers' own image servers.
 *
 * Bitmaps live in memory for the session. The bytes are also kept in the app's cache directory for at most
 * [MAX_AGE_MS], so a relaunch shows the last lineup with its pictures at once; the system may clear that directory at
 * any time, and nothing is stored anywhere else (TMDB and YouTube terms: no long-term copies).
 */
object Images {
    private const val MAX_AGE_MS = 7L * 24 * 3600_000
    private val HOSTS = setOf("image.tmdb.org", "i.ytimg.com")

    private val memory = object : LruCache<String, ImageBitmap>(72 * 1024) { // kilobytes
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
    }
    private val locks = HashMap<String, Mutex>()

    fun cached(url: String?): ImageBitmap? = url?.let { memory.get(it) }

    suspend fun load(context: Context, url: String): ImageBitmap? {
        memory.get(url)?.let { return it }
        val parsed = runCatching { URL(url) }.getOrNull() ?: return null
        if (parsed.protocol != "https" || parsed.host !in HOSTS) return null
        val lock = synchronized(locks) { locks.getOrPut(url) { Mutex() } }
        return lock.withLock {
            memory.get(url) ?: withContext(Dispatchers.IO) {
                val file = File(File(context.cacheDir, "img").apply { mkdirs() }, sha1(url))
                val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
                val bytes = if (fresh) runCatching { file.readBytes() }.getOrNull() else null
                val data = bytes ?: download(parsed)?.also { d -> runCatching { file.writeBytes(d) } }
                    ?: runCatching { file.takeIf { it.exists() }?.readBytes() }.getOrNull() // offline: the old copy is better than none
                data?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }?.also { memory.put(url, it) }
            }
        }
    }

    private fun download(url: URL): ByteArray? = runCatching {
        val c = url.openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 15_000
            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }.takeIf { it.size in 1..8_000_000 }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Drop cached files past their age. Called once at start. */
    fun prune(context: Context) {
        runCatching {
            File(context.cacheDir, "img").listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > MAX_AGE_MS) it.delete() }
        }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** The image at [url], once it has loaded (null until then, and for anything that is not an approved image host). */
@Composable
fun rememberImage(url: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    // Keyed on the URL, so a new film never shows the last film's picture while its own loads.
    val state = remember(url) { mutableStateOf(Images.cached(url)) }
    LaunchedEffect(url) {
        if (state.value == null && url != null) state.value = Images.load(context, url)
    }
    return state.value
}
