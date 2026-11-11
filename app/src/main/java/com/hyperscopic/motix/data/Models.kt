package com.hyperscopic.motix.data

import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What "films now" and "theaters" mean where the car is. The market is picked from the car's position, never from a
 * setting: a car in Seoul gets Korean cinemas and Korean titles, a car in Los Angeles gets US theaters.
 */
enum class Market(
    val code: String,
    /** How the film search names the market. */
    val where: String,
    /** TMDB and Places language for this market's own titles and names. */
    val language: String,
    /** How many theaters "See it nearby" lists. */
    val theaterCount: Int,
    /** Chains searched by name (Korea: a nearby search fills up with private screening rooms). */
    val chains: List<String>,
    val metric: Boolean,
) {
    US("US", "US movie theaters (wide release)", "en-US", 5, emptyList(), metric = false),
    KR("KR", "South Korean cinemas (CGV, Lotte Cinema, Megabox)", "ko-KR", 8, listOf("CGV", "Megabox", "Lotte Cinema"), metric = true);

    /** Showtimes are told in the market's own time: Seoul time in Korea, the car's clock elsewhere. */
    val zone: ZoneId get() = if (this == KR) ZoneId.of("Asia/Seoul") else ZoneId.systemDefault()

    companion object {
        fun at(lat: Double, lon: Double): Market = if (lat in 33.0..38.7 && lon in 124.5..131.0) KR else US
        fun of(code: String?): Market? = entries.firstOrNull { it.code == code }
    }
}

/** A film's official trailer on YouTube, confirmed with YouTube's oEmbed lookup before it is offered. */
data class Trailer(val videoId: String, val title: String?, val channel: String?, val thumbUrl: String)

data class Film(
    val id: String,
    val title: String,
    /** The Korean title, in the Korean market. */
    val titleKo: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val genresKo: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    /** An age rating only where it is checkable at a glance (US MPA). */
    val rating: String? = null,
    val synopsis: String = "",
    val synopsisKo: String? = null,
    val releaseDate: String? = null,
    val tmdbId: Int? = null,
    val posterUrl: String? = null,
    val stillUrl: String? = null,
    val trailer: Trailer? = null,
    /** When the artwork and trailer were last looked up (a film with none is looked up again after six hours). */
    val artCheckedAtMillis: Long = 0,
) {
    fun title(korean: Boolean): String = if (korean) titleKo ?: title else title

    /** The other language's title, shown under the main one. */
    fun secondTitle(korean: Boolean): String? = (if (korean) title else titleKo)?.takeIf { it != title(korean) }

    fun genres(korean: Boolean): List<String> = if (korean && genresKo.isNotEmpty()) genresKo else genres
    fun synopsis(korean: Boolean): String = if (korean) synopsisKo?.takeIf { it.isNotBlank() } ?: synopsis else synopsis

    /** The image behind the hero: the trailer's own thumbnail (what plays), else the still. */
    val heroUrl: String? get() = trailer?.thumbUrl ?: stillUrl
}

data class Theater(
    /** The Google Places id. */
    val id: String,
    val name: String,
    val nameKo: String? = null,
    val address: String = "",
    val addressKo: String? = null,
    val lat: Double,
    val lon: Double,
    val rating: Double? = null,
) {
    fun name(korean: Boolean): String = if (korean) nameKo ?: name else name
    fun address(korean: Boolean): String = if (korean) addressKo?.takeIf { it.isNotBlank() } ?: address else address
}

/**
 * Google's credit for a grounded answer. [entryPointHtml] is the "Search suggestions" chip Google returns
 * (`searchEntryPoint.renderedContent`); it is shown, unmodified, wherever the film list is shown.
 */
data class SearchCredit(val queries: List<String> = emptyList(), val entryPointHtml: String? = null)

data class FilmList(val market: Market, val films: List<Film>, val search: SearchCredit, val fetchedAtMillis: Long)

/** Where a booked film is shown as "near": the neighbourhood name of the car's position ("성수동", "Culver City"). */
data class Area(val name: String, val lat: Double, val lon: Double)

object Geo {
    /** Great-circle distance in metres: straight-line, never a drive distance. */
    fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
