package com.hyperscopic.motix.data

import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

/*
 * Showtimes, seats and payment.
 *
 * Mo-Tix's sources have no showtimes service and no ticketing partner is connected yet, so this part of the app is
 * SIMULATED: [SimulatedTicketingPartner] makes a representative daily schedule and a plausible seat map, takes no
 * payment and issues no ticket. The screens run the flow as the finished product would. A real partner (a multiplex
 * or a booking agency API) implements [TicketingPartner] and replaces it; nothing in the UI changes.
 */

data class Showing(val id: String, val filmId: String, val theaterId: String, val startsAt: ZonedDateTime)

/** A seat: the row from the screen back (0 = A, the front) and its index across the row (0 = seat 1). */
data class Seat(val row: Int, val index: Int) {
    val rowLetter: Char get() = 'A' + row
    val number: Int get() = index + 1
    val label: String get() = "$rowLetter$number"
}

/** A seat map: rows, the seats per section (aisles between: 3 | 8 | 3) and the seats already sold. */
data class SeatPlan(val rows: Int, val sections: List<Int>, val taken: Set<Seat>)

/** Ticket prices where the theater is. Amounts are in won in Korea and in cents in the US. */
data class Prices(val adult: Int, val youth: Int, val market: Market) {
    fun format(amount: Int): String =
        if (market == Market.KR) String.format(Locale.US, "₩%,d", amount) else String.format(Locale.US, "$%.2f", amount / 100.0)

    fun total(adults: Int, youths: Int): Int = adults * adult + youths * youth
}

data class Order(val film: Film, val theater: Theater, val showing: Showing, val adults: Int, val youths: Int, val seats: List<Seat>, val market: Market)

/** A booked showing: everything the ticket card needs, kept on the head unit until the film is over. */
data class Booking(
    val ref: String,
    val market: Market,
    val filmId: String,
    val filmTitle: String,
    val filmTitleKo: String?,
    val posterUrl: String?,
    val runtimeMinutes: Int?,
    val theaterName: String,
    val theaterNameKo: String?,
    val theaterAddress: String,
    val theaterAddressKo: String?,
    val theaterLat: Double,
    val theaterLon: Double,
    val startsAt: ZonedDateTime,
    val adults: Int,
    val youths: Int,
    val seats: List<Seat>,
    val total: Int,
    /** What the ticket QR code holds. */
    val qrPayload: String,
) {
    val tickets: Int get() = adults + youths
    fun filmTitle(korean: Boolean) = if (korean) filmTitleKo ?: filmTitle else filmTitle
    fun filmSecondTitle(korean: Boolean) = (if (korean) filmTitle else filmTitleKo)?.takeIf { it != filmTitle(korean) }
    fun theaterName(korean: Boolean) = if (korean) theaterNameKo ?: theaterName else theaterName
    fun theaterAddress(korean: Boolean) = if (korean) theaterAddressKo?.takeIf { it.isNotBlank() } ?: theaterAddress else theaterAddress

    /** The ticket is kept until the film has finished. */
    fun expired(now: Instant): Boolean = now.isAfter(startsAt.plusMinutes((runtimeMinutes ?: 150).toLong() + 30).toInstant())
}

interface TicketingPartner {
    val name: String

    /** True while this is a stand-in: representative showtimes, no payment taken, no ticket issued. */
    val simulated: Boolean

    /** What is left of today for [film] at [theater], else tomorrow. [featured] films play everywhere, more often. */
    fun showings(film: Film, featured: Boolean, theater: Theater, market: Market, now: Instant): List<Showing>

    fun prices(market: Market): Prices

    fun seatPlan(showing: Showing): SeatPlan

    /** Hold the seats, charge the saved card, issue the ticket. */
    suspend fun book(order: Order): Booking
}

/** The stand-in partner. Deterministic: the same film, theater and day always give the same times and seat map. */
object SimulatedTicketingPartner : TicketingPartner {
    override val name = "Simulated ticketing"
    override val simulated = true

    private val SECTIONS = listOf(3, 8, 3)
    private const val ROWS = 12

    override fun showings(film: Film, featured: Boolean, theater: Theater, market: Market, now: Instant): List<Showing> {
        val zone = market.zone
        val local = now.atZone(zone)
        fun day(date: LocalDate): List<Showing> {
            val seed = ("${film.id}|${theater.id}|$date").hashCode().toLong() and 0x7FFFFFFF
            if (!featured && seed % 4 == 0L) return emptyList() // not every film at every theater
            // A first show in the morning, then the film's runtime plus cleaning, until late.
            val cycle = ((film.runtimeMinutes ?: 115) + 20 + (seed % 3) * 5 + 4) / 5 * 5
            val first = date.atTime(9, 30).plusMinutes((seed / 7 % 10) * 10)
            val second = if (featured && seed % 2 == 0L) first.plusMinutes(cycle / 2 / 5 * 5 + 5) else null // a second screen
            return listOfNotNull(first, second).flatMap { start ->
                generateSequence(start) { it.plusMinutes(cycle) }.takeWhile { it.toLocalDate() == date && it.hour <= 23 }.toList()
            }.sorted().map { t -> Showing("mtx-${film.id}-${theater.id}-$t", film.id, theater.id, t.atZone(zone)) }
        }
        val today = day(local.toLocalDate()).filter { it.startsAt.toInstant().isAfter(now.plusSeconds(10 * 60)) }
        return today.ifEmpty { day(local.toLocalDate().plusDays(1)) }
    }

    /** Representative prices: a standard 2D evening at a Korean multiplex, Los Angeles prices in the US. */
    override fun prices(market: Market): Prices = if (market == Market.KR) Prices(15_000, 12_000, market) else Prices(1_999, 1_599, market)

    override fun seatPlan(showing: Showing): SeatPlan {
        val per = SECTIONS.sum()
        var seed = showing.id.hashCode().toLong() and 0xFFFFFFFFL
        fun rnd(): Double {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 33) and 0xFFFFFF).toDouble() / 0xFFFFFF
        }
        val taken = mutableSetOf<Seat>()
        for (r in 0 until ROWS) for (s in 0 until per) {
            // Busier in the middle and toward the back-centre, like a real evening showing.
            val centre = 1.0 - abs(s - (per - 1) / 2.0) / (per / 2.0)
            val depth = 1.0 - abs(r - 7.5) / 7.5
            if (rnd() < 0.18 + 0.35 * centre * depth) taken += Seat(r, s)
        }
        return SeatPlan(ROWS, SECTIONS, taken)
    }

    override suspend fun book(order: Order): Booking {
        delay(1_600) // the time a card authorisation takes
        val ref = "MTX-" + (order.showing.id + order.seats).hashCode().toUInt().toString(36).uppercase(Locale.US).take(6).padStart(6, '7')
        val f = order.film
        val t = order.theater
        return Booking(
            ref = ref, market = order.market, filmId = f.id, filmTitle = f.title, filmTitleKo = f.titleKo, posterUrl = f.posterUrl,
            runtimeMinutes = f.runtimeMinutes, theaterName = t.name, theaterNameKo = t.nameKo, theaterAddress = t.address,
            theaterAddressKo = t.addressKo, theaterLat = t.lat, theaterLon = t.lon, startsAt = order.showing.startsAt,
            adults = order.adults, youths = order.youths, seats = order.seats.sortedWith(compareBy({ it.row }, { it.index })),
            total = prices(order.market).total(order.adults, order.youths), qrPayload = phoneLink(order),
        )
    }

    /**
     * What the QR code opens on a phone. With no partner connected there is no real ticket to encode, so the code
     * leads somewhere true: that theater's showtimes for that film (Naver in Korea, where people check them; Google
     * elsewhere).
     */
    private fun phoneLink(order: Order): String =
        if (order.market == Market.KR) {
            "https://search.naver.com/search.naver?query=" +
                URLEncoder.encode("${order.theater.nameKo ?: order.theater.name} ${order.film.titleKo ?: order.film.title} 상영시간표", "UTF-8")
        } else {
            "https://www.google.com/search?q=" + URLEncoder.encode("${order.film.title} showtimes ${order.theater.name}", "UTF-8")
        }
}
