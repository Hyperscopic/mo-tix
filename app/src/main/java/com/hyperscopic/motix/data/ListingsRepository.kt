package com.hyperscopic.motix.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Where Mo-Tix gets its listings. The app only ever talks to this interface, so the on-device implementation
 * ([GoogleListings], which calls the providers directly) can be replaced by a hosted service that keeps the API keys
 * on a server, without touching a screen.
 */
interface ListingsRepository {
    /** False when this build has no keys for its sources. */
    val configured: Boolean

    /**
     * The films in theaters now in [market], most popular first, with artwork and trailers.
     * [known] is the previous list: its artwork is reused instead of looked up again.
     * [onPartial] gets the list as it fills in (titles first, artwork as it arrives).
     * Slow (a grounded web search): call it behind whatever is already on screen.
     */
    suspend fun films(market: Market, known: List<Film>, onPartial: (FilmList) -> Unit): FilmList

    /** The theaters nearest the car, nearest first. */
    suspend fun theaters(market: Market, lat: Double, lon: Double): List<Theater>

    /** The neighbourhood the car is in ("성수동", "Culver City"), or null. */
    suspend fun area(market: Market, lat: Double, lon: Double, korean: Boolean): Area?
}

class ListingsException(message: String) : Exception(message)

/** A small blocking HTTP client (HttpURLConnection): every call here runs on Dispatchers.IO. */
internal object Http {
    class Reply(val code: Int, val text: String) {
        val json: JSONObject get() = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        val ok: Boolean get() = code == 200
    }

    fun call(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        timeoutMs: Int = 25_000,
        method: String? = null,
    ): Reply = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = timeoutMs
            c.requestMethod = method ?: if (body != null) "POST" else "GET"
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = if (c.requestMethod == "HEAD") "" else stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            Reply(code, text)
        } finally {
            c.disconnect()
        }
    } catch (e: IOException) {
        Reply(0, "")
    } catch (e: SecurityException) {
        Reply(0, "")
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    fun query(params: Map<String, String>): String = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
}

// org.json helpers: a JSON null is "no value", never the string "null".
internal fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }
internal fun JSONObject.obj(name: String): JSONObject? = if (isNull(name)) null else optJSONObject(name)
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> if (isNull(i)) null else optString(i).trim().takeIf { it.isNotEmpty() } }
