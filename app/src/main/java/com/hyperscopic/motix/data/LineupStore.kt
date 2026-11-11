package com.hyperscopic.motix.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.ZonedDateTime

/**
 * What Mo-Tix keeps on the head unit, so a launch shows something at once and fetches behind it.
 *
 * Kept, per market: the last good film list with its Google Search credit (at most [MAX_AGE_MS], the limit Google sets
 * for stored grounded results), and the current ticket.
 *
 * Not kept: anything from Google Places. Theater names and addresses are asked for again on every launch (it takes
 * about a second); the Places terms do not allow storing them.
 */
/**
 * A dated sample of the listings for one place (Seongsu-dong, Seoul), bundled with the app. It is what Mo-Tix shows in
 * cached mode: films, artwork addresses, trailer ids and theaters as they were on [capturedAtMillis]. The showtimes on
 * top of it are still made for today.
 */
data class Snapshot(
    val market: Market,
    val lat: Double,
    val lon: Double,
    val areaKo: String,
    val areaEn: String,
    val capturedAtMillis: Long,
    val films: FilmList,
    val theaters: List<Theater>,
)

class LineupStore(context: Context) {
    private val dir: File = context.filesDir
    private val assets = context.assets
    private val prefs = context.getSharedPreferences("motix", Context.MODE_PRIVATE)

    /**
     * Cached mode: show the bundled [Snapshot] instead of asking the providers. On by default, so a build with no API
     * keys shows the whole app.
     */
    var cachedMode: Boolean
        get() = prefs.getBoolean("cachedMode", true)
        set(value) = prefs.edit().putBoolean("cachedMode", value).apply()

    /** The sample listings bundled with the app (assets/snapshot/seoul.json), or null when there are none. */
    fun loadSnapshot(): Snapshot? = runCatching {
        val o = JSONObject(assets.open(SNAPSHOT_ASSET).use { it.readBytes().toString(Charsets.UTF_8) })
        val market = Market.of(o.optString("market")) ?: return null
        val at = o.optLong("capturedAt")
        val films = o.optJSONArray("films").objects().map { it.toFilm() }
        if (films.isEmpty()) return null
        val area = o.obj("area")
        Snapshot(
            market, o.getDouble("lat"), o.getDouble("lon"), area?.str("ko").orEmpty(), area?.str("en").orEmpty(), at,
            FilmList(market, films, SearchCredit(), at), o.optJSONArray("theaters").objects().map { it.toTheater() },
        )
    }.getOrNull()

    /**
     * Debug builds only: what a live load produced, written to files/snapshot_export.json in the snapshot format, so
     * the bundled sample can be refreshed (see docs/ARCHITECTURE.md).
     */
    fun exportSnapshot(films: FilmList, theaters: List<Theater>, lat: Double, lon: Double, area: String?) {
        runCatching {
            File(dir, "snapshot_export.json").writeText(
                JSONObject().put("market", films.market.code).put("lat", lat).put("lon", lon)
                    .put("area", JSONObject().put("ko", area).put("en", area))
                    .put("capturedAt", films.fetchedAtMillis)
                    .put("films", JSONArray(films.films.map { it.toJson() }))
                    .put("theaters", JSONArray(theaters.map { it.toJson() })).toString(1),
            )
        }
    }

    private fun Theater.toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("nameKo", nameKo).put("address", address).put("addressKo", addressKo)
        .put("lat", lat).put("lon", lon).put("rating", rating)

    private fun JSONObject.toTheater(): Theater = Theater(
        id = getString("id"), name = getString("name"), nameKo = str("nameKo"), address = optString("address"),
        addressKo = str("addressKo"), lat = getDouble("lat"), lon = getDouble("lon"), rating = optDouble("rating").takeIf { !it.isNaN() },
    )

    /** The market of the last launch: its list shows before the car has even reported a position. */
    var lastMarket: Market?
        get() = Market.of(prefs.getString("market", null))
        set(value) = prefs.edit().putString("market", value?.code).apply()

    private fun file(market: Market) = File(dir, "films_${market.code}.json")

    fun load(market: Market): FilmList? = runCatching {
        val f = file(market).takeIf { it.exists() } ?: return null
        val o = JSONObject(f.readText())
        val at = o.optLong("fetchedAt")
        if (System.currentTimeMillis() - at > MAX_AGE_MS) {
            f.delete()
            return null
        }
        val films = o.optJSONArray("films").objects().map { it.toFilm() }
        if (films.isEmpty()) return null
        val s = o.obj("search")
        FilmList(market, films, SearchCredit(s?.optJSONArray("queries").strings(), s?.str("entryPoint")), at)
    }.getOrNull()

    fun save(list: FilmList) {
        runCatching {
            val o = JSONObject()
                .put("fetchedAt", list.fetchedAtMillis)
                .put("films", JSONArray(list.films.map { it.toJson() }))
                .put("search", JSONObject().put("queries", JSONArray(list.search.queries)).put("entryPoint", list.search.entryPointHtml))
            val tmp = File(dir, "films_${list.market.code}.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file(list.market))
        }
    }

    // ---- The ticket ------------------------------------------------------------------------------------------

    private val ticketFile get() = File(dir, "ticket.json")

    fun loadTicket(): Booking? = runCatching {
        val f = ticketFile.takeIf { it.exists() } ?: return null
        val o = JSONObject(f.readText())
        Booking(
            ref = o.getString("ref"), market = Market.of(o.optString("market")) ?: return null, filmId = o.getString("filmId"),
            filmTitle = o.getString("filmTitle"), filmTitleKo = o.str("filmTitleKo"), posterUrl = o.str("posterUrl"),
            runtimeMinutes = o.optInt("runtime").takeIf { it > 0 },
            theaterName = o.getString("theaterName"), theaterNameKo = o.str("theaterNameKo"),
            theaterAddress = o.optString("theaterAddress"), theaterAddressKo = o.str("theaterAddressKo"),
            theaterLat = o.getDouble("lat"), theaterLon = o.getDouble("lon"),
            startsAt = ZonedDateTime.parse(o.getString("startsAt")),
            adults = o.optInt("adults"), youths = o.optInt("youths"),
            seats = o.optJSONArray("seats").objects().map { Seat(it.optInt("r"), it.optInt("i")) },
            total = o.optInt("total"), qrPayload = o.getString("qr"),
        )
    }.getOrNull()

    fun saveTicket(b: Booking?) {
        runCatching {
            if (b == null) {
                ticketFile.delete()
                return
            }
            ticketFile.writeText(
                JSONObject().put("ref", b.ref).put("market", b.market.code).put("filmId", b.filmId).put("filmTitle", b.filmTitle)
                    .put("filmTitleKo", b.filmTitleKo).put("posterUrl", b.posterUrl).put("runtime", b.runtimeMinutes ?: 0)
                    .put("theaterName", b.theaterName).put("theaterNameKo", b.theaterNameKo).put("theaterAddress", b.theaterAddress)
                    .put("theaterAddressKo", b.theaterAddressKo).put("lat", b.theaterLat).put("lon", b.theaterLon)
                    .put("startsAt", b.startsAt.toString()).put("adults", b.adults).put("youths", b.youths)
                    .put("seats", JSONArray(b.seats.map { JSONObject().put("r", it.row).put("i", it.index) }))
                    .put("total", b.total).put("qr", b.qrPayload).toString(),
            )
        }
    }

    private fun Film.toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("titleKo", titleKo).put("year", year)
        .put("genres", JSONArray(genres)).put("genresKo", JSONArray(genresKo)).put("runtime", runtimeMinutes).put("rating", rating)
        .put("synopsis", synopsis).put("synopsisKo", synopsisKo).put("releaseDate", releaseDate).put("tmdbId", tmdbId)
        .put("poster", posterUrl).put("still", stillUrl).put("artCheckedAt", artCheckedAtMillis)
        .put("trailer", trailer?.let { JSONObject().put("id", it.videoId).put("title", it.title).put("channel", it.channel).put("thumb", it.thumbUrl) })

    private fun JSONObject.toFilm(): Film = Film(
        id = getString("id"), title = getString("title"), titleKo = str("titleKo"), year = optInt("year").takeIf { it > 0 },
        genres = optJSONArray("genres").strings(), genresKo = optJSONArray("genresKo").strings(),
        runtimeMinutes = optInt("runtime").takeIf { it > 0 }, rating = str("rating"), synopsis = optString("synopsis"),
        synopsisKo = str("synopsisKo"), releaseDate = str("releaseDate"), tmdbId = optInt("tmdbId").takeIf { it > 0 },
        posterUrl = str("poster"), stillUrl = str("still"), artCheckedAtMillis = optLong("artCheckedAt"),
        trailer = obj("trailer")?.let { t -> t.str("id")?.let { Trailer(it, t.str("title"), t.str("channel"), t.str("thumb") ?: "https://i.ytimg.com/vi/$it/hqdefault.jpg") } },
    )

    companion object {
        const val SNAPSHOT_ASSET = "snapshot/seoul.json"

        /** Google allows grounded results to be stored for up to thirty days. */
        const val MAX_AGE_MS = 30L * 24 * 3600_000

        /** A film list older than this is searched again, behind the old one. */
        const val FRESH_MS = 6L * 3600_000
    }
}
