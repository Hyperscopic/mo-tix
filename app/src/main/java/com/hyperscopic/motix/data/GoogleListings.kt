package com.hyperscopic.motix.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Listings straight from the providers, on the head unit (no PC service, no proxy):
 *
 *  - Films in theaters now: Gemini 2.5 Flash with Grounding with Google Search, asked for the market the car is in.
 *    Korean and English titles, genres and synopses in Korea.
 *  - Theaters: Google Places API (New). Korea: a text search for CGV, Megabox and Lotte Cinema around the car, the
 *    eight nearest, with Korean names. US: a nearby search for movie theaters.
 *  - Posters, stills and official trailers: TMDB (matched by Korean title first in Korea).
 *  - Trailers: the hand-checked list in assets/trailers.json, then the studio trailer TMDB lists, then YouTube search
 *    when the key allows it. Every trailer is confirmed with YouTube oEmbed before it is offered.
 *
 * This is a Kotlin port of the PC service the Pleos Lab build used (tools/motix_live.py).
 */
class GoogleListings(private val context: Context, private val keys: Keys) : ListingsRepository {

    data class Keys(
        val gemini: String,
        val vertexProject: String,
        val vertexLocation: String,
        val google: String,
        val tmdbKey: String,
        val tmdbToken: String,
    )

    override val configured: Boolean get() = keys.gemini.isNotBlank() && keys.google.isNotBlank()

    private val tmdbConfigured: Boolean get() = keys.tmdbKey.isNotBlank() || keys.tmdbToken.isNotBlank()

    /** assets/trailers.json: {"pin": [titles], "trailers": {title: {market: YouTube id}}}. */
    private val handPicked: Pair<Map<String, Map<String, String>>, List<String>> by lazy {
        runCatching {
            val c = JSONObject(context.assets.open("trailers.json").use { it.readBytes().toString(Charsets.UTF_8) })
            val trailers = buildMap {
                val t = c.optJSONObject("trailers") ?: JSONObject()
                for (title in t.keys()) {
                    val per = t.optJSONObject(title) ?: continue
                    put(title, per.keys().asSequence().associateWith { per.optString(it) })
                }
            }
            trailers to c.optJSONArray("pin").strings()
        }.getOrDefault(emptyMap<String, Map<String, String>>() to emptyList())
    }

    // ---- Films: Gemini with Google Search -----------------------------------------------------------------------

    override suspend fun films(market: Market, known: List<Film>, onPartial: (FilmList) -> Unit): FilmList = withContext(Dispatchers.IO) {
        if (!configured) throw ListingsException("no keys")
        val today = LocalDate.now(market.zone)
        val fields = filmFields(market)
        val listed = grounded(
            "Today is ${today.format(LONG_DATE)}. List the ${FILMS + 2} biggest films playing in ${market.where} this week, " +
                "most popular first. JSON array only, each object: $fields. Leave a field as an empty string when you are not sure of it.",
        )
        var films = (listed.json as? JSONArray).objects().mapNotNull { it.toFilm(market) }.distinctBy { it.id }
        if (films.isEmpty()) throw ListingsException("no films in the answer")
        val credit = SearchCredit(
            queries = listed.meta.optJSONArray("webSearchQueries").strings(),
            entryPointHtml = listed.meta.obj("searchEntryPoint")?.str("renderedContent"),
        )
        val now = System.currentTimeMillis()
        // What is already known about these films (artwork, trailers) carries over.
        fun List<Film>.withKnown() = map { f -> known.firstOrNull { it.id == f.id }?.let { f.carry(it) } ?: f }
        onPartial(FilmList(market, films.take(FILMS).withKnown(), credit, now))

        // Featured films: listed when they are showing in this market, even outside the top films.
        for (pin in handPicked.second) {
            if (films.any { norm(it.title) == norm(pin) }) continue
            val check = runCatching {
                grounded(
                    "Today is ${today.format(SHORT_DATE)}. Is the film \"$pin\" showing in ${market.where} now? JSON object only: " +
                        "{\"showing\": true or false, \"film\": $fields}",
                ).json as? JSONObject
            }.getOrNull() ?: continue
            if (!check.optBoolean("showing")) continue
            val film = check.obj("film")?.put("title", pin)?.toFilm(market) ?: continue
            films = films.toMutableList().apply { add(minOf(2, size), film) }
        }
        // Every candidate (the answer has a couple more than are shown) is looked up, a few at a time; the list on
        // screen fills in as each one lands.
        val current = films.withKnown().toMutableList()
        onPartial(FilmList(market, current.take(FILMS), credit, now))
        val gate = Semaphore(4)
        coroutineScope {
            current.indices.map { i ->
                async {
                    gate.withPermit {
                        val done = runCatching { withArt(current[i], market) }.getOrElse { current[i] }
                        val snapshot = synchronized(current) { current[i] = done; current.toList() }
                        onPartial(FilmList(market, snapshot.take(FILMS), credit, now))
                    }
                }
            }.awaitAll()
        }
        // A title TMDB does not know is most often one the search got wrong (a series, a film not out yet, a mistaken
        // name), and it would show with no poster. When enough films are confirmed, only those are listed.
        val all = synchronized(current) { current.toList() }
        val confirmed = all.filter { it.tmdbId != null }
        FilmList(market, (if (tmdbConfigured && confirmed.size >= MIN_CONFIRMED) confirmed else all).take(FILMS), credit, now)
    }

    private fun filmFields(market: Market): String = buildString {
        append("{\"title\": \"the English title\", \"title_ko\": \"the Korean title\", \"year\", \"genres\": [..], ")
        if (market == Market.KR) append("\"genres_ko\": [\"the genres, in Korean\"], ")
        append("\"runtime_minutes\", \"rating\": \"the local age rating\", \"synopsis\": \"one or two sentences, in English\", ")
        if (market == Market.KR) append("\"synopsis_ko\": \"the same one or two sentences, in Korean\", ")
        append("\"release_date\": \"YYYY-MM-DD, the local release\"}")
    }

    private class Grounded(val json: Any, val meta: JSONObject)

    /** Gemini with Google Search: the parsed JSON answer and its grounding metadata. */
    private suspend fun grounded(prompt: String): Grounded {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("tools", JSONArray().put(JSONObject().put("googleSearch", JSONObject())))
            // No thinking budget: the answer is a lookup, and it comes back in about 15 s instead of about 50.
            .put("generationConfig", JSONObject().put("temperature", 0).put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        var reply = Http.Reply(0, "")
        for (attempt in 0 until 4) {
            reply = Http.call(modelUrl(MODEL, "generateContent"), mapOf("x-goog-api-key" to keys.gemini), body, timeoutMs = 120_000)
            if (reply.code != 429 && reply.code in 1..499) break
            delay(4_000L * (attempt + 1)) // rate limited, busy or offline: back off and try again
        }
        if (!reply.ok) throw ListingsException("Gemini ${reply.code}")
        val candidate = reply.json.optJSONArray("candidates")?.optJSONObject(0) ?: throw ListingsException("Gemini: no candidate")
        val text = candidate.obj("content")?.optJSONArray("parts").objects().joinToString("") { it.optString("text") }
        val raw = JSON_BLOCK.find(text)?.value ?: throw ListingsException("Gemini: no JSON in the answer")
        val json: Any = try {
            if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
        } catch (e: JSONException) {
            throw ListingsException("Gemini: unreadable JSON")
        }
        return Grounded(json, candidate.obj("groundingMetadata") ?: JSONObject())
    }

    /** Vertex AI (an express key with a Cloud project) or the Gemini Developer API. */
    private fun modelUrl(model: String, method: String): String {
        val project = keys.vertexProject
        if (project.isBlank()) return "https://generativelanguage.googleapis.com/v1beta/models/$model:$method"
        val location = keys.vertexLocation.ifBlank { "us-central1" }
        val host = if (location == "global") "aiplatform.googleapis.com" else "$location-aiplatform.googleapis.com"
        return "https://$host/v1/projects/$project/locations/$location/publishers/google/models/$model:$method"
    }

    private fun JSONObject.toFilm(market: Market): Film? {
        val title = str("title") ?: return null
        val slug = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        // A Korean title is written in Hangul: the search sometimes puts a film's Japanese original title here,
        // which is no use to a Korean reader. Without one, TMDB's Korean title is used (see withArt).
        val titleKo = str("title_ko")?.takeIf { market != Market.US && norm(it) != norm(title) && HANGUL.containsMatchIn(it) }
        // Age ratings only where they are checkable at a glance (US MPA); Korea's from one search answer were unreliable.
        val rating = (str("rating") ?: str("mpaa_rating"))?.takeIf {
            market == Market.US && it.lowercase(Locale.US) !in setOf("not rated", "nr", "none", "null", "n/a")
        }
        return Film(
            id = "gx-" + slug.ifEmpty { Integer.toHexString(title.hashCode()) },
            title = title,
            titleKo = titleKo,
            year = (if (isNull("year")) null else optString("year")).orEmpty().take(4).toIntOrNull(),
            genres = optJSONArray("genres").strings(),
            genresKo = optJSONArray("genres_ko").strings(),
            runtimeMinutes = (if (isNull("runtime_minutes")) null else optString("runtime_minutes"))?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 },
            rating = rating,
            synopsis = realSynopsis(str("synopsis")).orEmpty(),
            synopsisKo = realSynopsis(str("synopsis_ko")),
            releaseDate = str("release_date")?.takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) },
        )
    }

    private fun Film.carry(old: Film) = copy(
        tmdbId = old.tmdbId, posterUrl = old.posterUrl, stillUrl = old.stillUrl, trailer = old.trailer, artCheckedAtMillis = old.artCheckedAtMillis,
        // A new answer sometimes drops the Korean side; keep what the last one had.
        titleKo = titleKo ?: old.titleKo, synopsisKo = synopsisKo ?: old.synopsisKo, genresKo = genresKo.ifEmpty { old.genresKo },
        runtimeMinutes = runtimeMinutes ?: old.runtimeMinutes,
    )

    // ---- Artwork and trailers: TMDB and YouTube -----------------------------------------------------------------

    private fun withArt(f: Film, market: Market): Film {
        val listedId = handPicked.first[f.title]?.let { it[market.code] ?: it["US"] }
        val complete = f.posterUrl != null && f.trailer != null && (listedId == null || f.trailer.videoId == listedId)
        val checkedLately = System.currentTimeMillis() - f.artCheckedAtMillis < RETRY_MS && (listedId == null || f.trailer?.videoId == listedId)
        if (complete || checkedLately) return f
        var out = f
        if (out.posterUrl == null || out.tmdbId == null) {
            val art = tmdbArt(out, market)
            out = out.copy(
                tmdbId = art.id ?: out.tmdbId, posterUrl = art.poster ?: out.posterUrl, stillUrl = art.backdrop ?: out.stillUrl,
                runtimeMinutes = out.runtimeMinutes ?: art.runtime,
                // The distributor synopsis and the local title TMDB carries, where the search answer was thin.
                synopsis = art.overviewEn ?: out.synopsis,
                synopsisKo = art.overviewKo ?: out.synopsisKo,
                titleKo = out.titleKo ?: art.titleKo?.takeIf { norm(it) != norm(out.title) },
            )
        }
        if (out.trailer == null || (listedId != null && out.trailer?.videoId != listedId)) {
            out = out.copy(trailer = trailerFor(out, market, listedId) ?: out.trailer)
        }
        return out.copy(artCheckedAtMillis = System.currentTimeMillis())
    }

    private fun tmdb(path: String, query: Map<String, String>): Http.Reply {
        val q = if (keys.tmdbKey.isNotBlank()) query + ("api_key" to keys.tmdbKey) else query
        val headers = if (keys.tmdbKey.isBlank() && keys.tmdbToken.isNotBlank()) mapOf("Authorization" to "Bearer ${keys.tmdbToken}") else emptyMap()
        return Http.call("https://api.themoviedb.org/3$path?" + Http.query(q), headers)
    }

    private class Art(
        val id: Int?, val poster: String?, val backdrop: String?, val runtime: Int? = null,
        val overviewEn: String? = null, val overviewKo: String? = null, val titleKo: String? = null,
    )

    /**
     * A film's poster and still from TMDB (the market's language first), or nothing without a key or a sure match.
     * Never a "first result" guess: a result counts only when its title is the film's title, in one of four ways.
     */
    private fun tmdbArt(f: Film, market: Market): Art {
        if (!tmdbConfigured) return Art(null, null, null)
        val years = f.year?.let { setOf("$it", "${it - 1}", "${it + 1}") }.orEmpty()
        val aroundThen = { m: JSONObject -> years.isEmpty() || m.str("release_date").orEmpty().take(4) in years }

        fun search(query: String, language: String): List<JSONObject> {
            val r = tmdb("/search/movie", mapOf("query" to query, "language" to language, "include_adult" to "false"))
            if (!r.ok) Log.w(TAG, "TMDB ${r.code} for a title search")
            return r.json.optJSONArray("results").objects()
        }

        var pick: JSONObject? = null
        val titleKo = f.titleKo?.takeIf { market == Market.KR && it.isNotBlank() }
        val ko = koreanKey(titleKo)
        // 1. Korea: the Korean title (TMDB keeps Korean films in Korean, and Korean names for the others).
        if (titleKo != null) {
            val results = search(titleKo, market.language)
            fun keys(m: JSONObject) = listOf(koreanKey(m.str("title")), koreanKey(m.str("original_title"))).filter { it.isNotEmpty() }
            // The same title, or the same title with a subtitle, when the year confirms it...
            pick = results.filter(aroundThen).firstOrNull { m -> keys(m).any { it == ko || it.startsWith(ko) || ko.startsWith(it) } }
                // ...or exactly the same title from any year (a re-release): the newest.
                ?: results.filter { m -> ko in keys(m) }.maxByOrNull { it.str("release_date").orEmpty() }
        }
        for (query in linkedSetOf(f.title, f.title.substringBefore(":").trim())) {
            if (pick != null) break
            if (norm(query).isEmpty()) continue
            val results = search(query, market.language).filter(aroundThen)
            // 2. The English title, as TMDB has it in the market listing (title or original title).
            pick = results.firstOrNull { m ->
                val titles = listOf(norm(m.str("title")), norm(m.str("original_title")))
                norm(query) in titles || norm(f.title) in titles
            }
            // 3. Korea: nearly the same Korean title ("영화 치이카와" for "극장판 치이카와"), the year confirming it.
            if (pick == null && titleKo != null && years.isNotEmpty()) {
                pick = results.firstOrNull { m -> similar(koreanKey(m.str("title")), ko) >= 0.6 }
            }
        }
        // 4. Outside the US: the English title TMDB has is the English title of the film.
        if (pick == null && market != Market.US && norm(f.title).length >= 6) {
            val mine = norm(f.title)
            pick = search(f.title, "en-US").filter(aroundThen).firstOrNull { m ->
                val theirs = norm(m.str("title"))
                theirs.length >= 6 && (theirs == mine || theirs.startsWith(mine) || mine.startsWith(theirs))
            }
        }
        val hit = pick ?: return Art(null, null, null)
        val id = hit.optInt("id")
        var poster = hit.str("poster_path")
        var backdrop = hit.str("backdrop_path")
        val langs = if (market == Market.KR) listOf("ko", "en", "null") else listOf("en", "null")
        val images = tmdb("/movie/$id/images", mapOf("include_image_language" to langs.joinToString(",")))
        if (images.ok) {
            fun best(items: List<JSONObject>): String? = items.sortedWith(
                compareBy<JSONObject>({ langs.indexOf(it.str("iso_639_1") ?: "null").let { i -> if (i < 0) 9 else i } }, { -it.optDouble("vote_average", 0.0) }),
            ).firstOrNull()?.str("file_path")
            poster = best(images.json.optJSONArray("posters").objects()) ?: poster
            // A clean still (no title on it) for the full-bleed backgrounds.
            backdrop = best(images.json.optJSONArray("backdrops").objects().filter { it.str("iso_639_1") == null }) ?: backdrop
        }
        // The film page in the market language: the running time (the showtimes are spaced by it), the local title
        // and the distributor synopsis, which reads better than a search answer's one-line summary. English too,
        // outside the US, for the English side of the UI.
        val local = tmdb("/movie/$id", mapOf("language" to market.language)).json
        val english = if (market == Market.US) local else tmdb("/movie/$id", mapOf("language" to "en-US")).json
        return Art(
            id, poster?.let { "https://image.tmdb.org/t/p/w780$it" }, backdrop?.let { "https://image.tmdb.org/t/p/w1280$it" },
            runtime = local.optInt("runtime").takeIf { it > 0 },
            overviewEn = english.str("overview")?.takeIf { it.length >= 40 && !HANGUL.containsMatchIn(it) },
            overviewKo = local.str("overview")?.takeIf { market == Market.KR && it.length >= 20 && HANGUL.containsMatchIn(it) },
            titleKo = local.str("title")?.takeIf { market == Market.KR && HANGUL.containsMatchIn(it) },
        )
    }

    /** The film's official YouTube trailer as TMDB lists it (the studio upload): the market's language, then English. */
    private fun tmdbTrailer(tmdbId: Int?, market: Market): String? {
        if (tmdbId == null || !tmdbConfigured) return null
        for (lang in linkedSetOf(market.language, "en-US")) {
            val r = tmdb("/movie/$tmdbId/videos", mapOf("language" to lang))
            val videos = r.json.optJSONArray("results").objects()
                .filter { it.str("site") == "YouTube" && it.str("type") == "Trailer" && it.str("key") != null }
            if (videos.isEmpty()) continue
            // Official first, then a full "Official Trailer" over teasers, then the newest.
            return videos.sortedByDescending { it.str("published_at").orEmpty() }.sortedWith(
                compareBy(
                    { !it.optBoolean("official") },
                    { "official trailer" !in it.str("name").orEmpty().lowercase(Locale.US) },
                    { "teaser" in it.str("name").orEmpty().lowercase(Locale.US) },
                ),
            ).first().str("key")
        }
        return null
    }

    /** YouTube Data API search, when it is allowed on the key: the studio upload over trailer channels. */
    private fun youtubeSearch(f: Film): String? {
        val r = Http.call(
            "https://www.googleapis.com/youtube/v3/search?" + Http.query(
                mapOf("part" to "snippet", "q" to "${f.title} ${f.year ?: ""} official trailer", "type" to "video", "videoEmbeddable" to "true", "maxResults" to "5", "key" to keys.google),
            ),
            androidHeaders,
        )
        if (!r.ok) return null
        val studios = listOf("pictures", "studios", "entertainment", "films", "warner", "disney", "universal", "paramount", "sony", "lionsgate", "a24", "neon", "focus")
        return r.json.optJSONArray("items").objects().sortedWith(
            compareBy(
                { i -> val ch = i.obj("snippet")?.str("channelTitle").orEmpty().lowercase(Locale.US); if (studios.any { it in ch }) 0 else 1 },
                { i -> val t = i.obj("snippet")?.str("title").orEmpty().lowercase(Locale.US); if ("official" in t && "trailer" in t) 0 else 1 },
            ),
        ).firstOrNull()?.obj("id")?.str("videoId")
    }

    private fun trailerFor(f: Film, market: Market, listedId: String?): Trailer? {
        val id = listedId ?: tmdbTrailer(f.tmdbId, market) ?: youtubeSearch(f) ?: return null
        // YouTube confirms the video exists and can be embedded.
        val embed = Http.call("https://www.youtube.com/oembed?format=json&url=" + Http.enc("https://www.youtube.com/watch?v=$id"), timeoutMs = 10_000)
        if (!embed.ok) return null
        val o = embed.json
        var thumb = "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
        if (!Http.call(thumb, method = "HEAD", timeoutMs = 8_000).ok) thumb = o.str("thumbnail_url") ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        return Trailer(id, o.str("title"), o.str("author_name"), thumb)
    }

    // ---- Theaters: Google Places --------------------------------------------------------------------------------

    /** Lets the Google key be restricted to this Android app (package name and signing certificate). */
    private val androidHeaders: Map<String, String> by lazy {
        val cert = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val der = info.signingInfo!!.apkContentsSigners.first().toByteArray()
            MessageDigest.getInstance("SHA-1").digest(der).joinToString("") { "%02X".format(it) }
        }.getOrDefault("")
        mapOf("X-Android-Package" to context.packageName, "X-Android-Cert" to cert)
    }

    private fun places(method: String, fields: String, body: JSONObject): Http.Reply =
        Http.call("https://places.googleapis.com/v1/places:$method", androidHeaders + mapOf("X-Goog-Api-Key" to keys.google, "X-Goog-FieldMask" to fields), body)

    override suspend fun theaters(market: Market, lat: Double, lon: Double): List<Theater> = withContext(Dispatchers.IO) {
        if (keys.google.isBlank()) throw ListingsException("no keys")
        fun JSONObject.metres(): Double = obj("location")?.let { Geo.metres(lat, lon, it.optDouble("latitude"), it.optDouble("longitude")) } ?: Double.MAX_VALUE
        fun JSONObject.open(minRatings: Int) = (str("businessStatus") ?: "OPERATIONAL") == "OPERATIONAL" && optInt("userRatingCount") >= minRatings
        fun JSONObject.name() = obj("displayName")?.str("text").orEmpty()
        fun JSONObject.address() = str("shortFormattedAddress") ?: str("formattedAddress").orEmpty()

        if (market == Market.KR) {
            // The chains by name around the car, in English and in Korean.
            val d = 0.08
            val box = JSONObject().put(
                "rectangle",
                JSONObject().put("low", JSONObject().put("latitude", lat - d).put("longitude", lon - d * 1.25))
                    .put("high", JSONObject().put("latitude", lat + d).put("longitude", lon + d * 1.25)),
            )
            val replies = coroutineScope {
                market.chains.flatMap { chain -> listOf("en", "ko").map { lang -> chain to lang } }.map { (chain, lang) ->
                    async {
                        lang to places(
                            "searchText", PLACE_FIELDS,
                            JSONObject().put("textQuery", chain).put("includedType", "movie_theater").put("strictTypeFiltering", true)
                                .put("pageSize", 20).put("languageCode", lang).put("locationRestriction", box),
                        )
                    }
                }.awaitAll()
            }
            replies.firstOrNull { !it.second.ok }?.let { throw ListingsException("Places ${it.second.code}") }
            val english = LinkedHashMap<String, JSONObject>()
            val korean = HashMap<String, JSONObject>()
            for ((lang, reply) in replies) for (p in reply.json.optJSONArray("places").objects()) {
                val id = p.str("id") ?: continue
                (if (lang == "en") english else korean)[id] = p
            }
            // Open branches the public actually rates (closed and phantom listings drop out), nearest first.
            val nearest = english.values.sortedBy { it.metres() }.filter { it.open(50) }.take(market.theaterCount)
            // A branch the Korean search left out still gets its Korean name: asked for by its place id.
            coroutineScope {
                nearest.map { it.optString("id") }.filter { it !in korean }.map { id ->
                    async {
                        val r = Http.call(
                            "https://places.googleapis.com/v1/places/$id?languageCode=ko",
                            androidHeaders + mapOf("X-Goog-Api-Key" to keys.google, "X-Goog-FieldMask" to "id,displayName,shortFormattedAddress,formattedAddress"),
                        )
                        if (r.ok) id to r.json else null
                    }
                }.awaitAll().filterNotNull().forEach { (id, place) -> korean[id] = place }
            }
            nearest.mapNotNull { p ->
                val loc = p.obj("location") ?: return@mapNotNull null
                val ko = korean[p.optString("id")]
                val nameKo = ko?.name()?.takeIf { it.isNotEmpty() }
                Theater(
                    id = p.optString("id"), name = englishName(p.name(), nameKo), nameKo = nameKo,
                    address = p.address(), addressKo = ko?.address(),
                    lat = loc.optDouble("latitude"), lon = loc.optDouble("longitude"),
                    rating = p.optDouble("rating").takeIf { !it.isNaN() },
                )
            }
        } else {
            val r = places(
                "searchNearby", PLACE_FIELDS,
                JSONObject().put("includedPrimaryTypes", JSONArray().put("movie_theater")).put("maxResultCount", 20).put("rankPreference", "DISTANCE")
                    .put("languageCode", "en")
                    .put("locationRestriction", JSONObject().put("circle", JSONObject().put("center", JSONObject().put("latitude", lat).put("longitude", lon)).put("radius", 20_000))),
            )
            if (!r.ok) throw ListingsException("Places ${r.code}")
            // Real cinemas only: a movie theater first (malls and venues that also list one drop out), open, and
            // rated by the public (screening clubs and event spaces drop out).
            r.json.optJSONArray("places").objects().filter { it.str("primaryType") == "movie_theater" && it.open(200) }
                .take(market.theaterCount).mapNotNull { p ->
                    val loc = p.obj("location") ?: return@mapNotNull null
                    Theater(
                        id = p.optString("id"), name = p.name(), address = p.address(),
                        lat = loc.optDouble("latitude"), lon = loc.optDouble("longitude"), rating = p.optDouble("rating").takeIf { !it.isNaN() },
                    )
                }
        }
    }

    /** The English name, unless it has no branch in Latin letters ("LOTTE CINEMA", "CGV 압구정"): then the Korean one. */
    private fun englishName(en: String, ko: String?): String {
        if (ko != null && (HANGUL.containsMatchIn(en) || norm(en) in setOf("lottecinema", "megabox", "cgv"))) return ko
        return en.replace(Regex("^LOTTE CINEMA"), "Lotte Cinema").replace(Regex(",\\s*Seoul$"), "")
    }

    override suspend fun area(market: Market, lat: Double, lon: Double, korean: Boolean): Area? = withContext(Dispatchers.IO) {
        if (keys.google.isBlank()) return@withContext null
        for (radius in listOf(300, 1_500)) {
            val r = places(
                "searchNearby", "places.addressComponents",
                JSONObject().put("maxResultCount", 1).put("rankPreference", "DISTANCE").put("languageCode", if (korean) "ko" else "en")
                    .put("locationRestriction", JSONObject().put("circle", JSONObject().put("center", JSONObject().put("latitude", lat).put("longitude", lon)).put("radius", radius))),
            )
            val parts = r.json.optJSONArray("places")?.optJSONObject(0)?.optJSONArray("addressComponents").objects()
            if (parts.isEmpty()) continue
            fun part(type: String) = parts.firstOrNull { type in it.optJSONArray("types").strings() }?.str("longText")
            val wanted = if (market == Market.KR) listOf("sublocality_level_2", "sublocality_level_1", "locality") else listOf("locality", "neighborhood", "sublocality_level_1")
            val name = wanted.firstNotNullOfOrNull { part(it) } ?: continue
            // "성수동2가" and "Seongsu-dong 2(i)-ga" are both simply Seongsu-dong.
            val tidy = name.replace(Regex("\\s*\\d+가$"), "").replace(Regex("\\s+\\d+\\([a-z]+\\)-ga$", RegexOption.IGNORE_CASE), "")
            return@withContext Area(tidy.ifBlank { name }, lat, lon)
        }
        null
    }

    companion object {
        private const val TAG = "MoTixListings"
        private const val MODEL = "gemini-2.5-flash"
        const val FILMS = 8
        private const val MIN_CONFIRMED = 5
        private const val RETRY_MS = 6 * 3600_000L // a film with no artwork or trailer found is looked up again after this
        private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy", Locale.US)
        private val SHORT_DATE = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.US)
        private val JSON_BLOCK = Regex("(\\[.*]|\\{.*\\})", RegexOption.DOT_MATCHES_ALL)
        /** "Details are not readily available": an answer that says it has no answer is no synopsis. */
        private val NON_ANSWER = Regex("not (readily |currently |yet |easily )?(available|found)|no (synopsis|information|details)|unknown|unavailable|찾을 수 없|찾기 어렵|정보(가|는|를)? ?(없|제공되지)|알려지지 않|확인되지 않", RegexOption.IGNORE_CASE)

        private fun realSynopsis(text: String?): String? = text?.trim()?.takeIf { it.length >= 12 && !NON_ANSWER.containsMatchIn(it) }

        private val HANGUL = Regex("[가-힣]")
        private const val PLACE_FIELDS = "places.id,places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount," +
            "places.shortFormattedAddress,places.businessStatus,places.primaryType"

        /** Titles compare on their letters and digits alone. */
        fun norm(title: String?): String = title.orEmpty().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")

        /** Korean titles compare on their letters and digits (no spaces, punctuation or symbols). */
        private fun koreanKey(title: String?): String = title.orEmpty().replace(Regex("[^\\p{L}\\p{N}]+"), "")

        /** How alike two titles are, from 0 to 1 (shared letter pairs: the Dice coefficient). */
        private fun similar(a: String, b: String): Double {
            if (a.length < 2 || b.length < 2) return 0.0
            val pairs = a.windowed(2).groupingBy { it }.eachCount().toMutableMap()
            var shared = 0
            for (pair in b.windowed(2)) {
                val left = pairs[pair] ?: 0
                if (left > 0) {
                    pairs[pair] = left - 1
                    shared++
                }
            }
            return 2.0 * shared / (a.length - 1 + b.length - 1)
        }
    }
}
