package com.hyperscopic.motix

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hyperscopic.motix.data.Area
import com.hyperscopic.motix.data.Booking
import com.hyperscopic.motix.data.Film
import com.hyperscopic.motix.data.FilmList
import com.hyperscopic.motix.data.Geo
import com.hyperscopic.motix.data.GoogleListings
import com.hyperscopic.motix.data.Images
import com.hyperscopic.motix.data.LineupStore
import com.hyperscopic.motix.data.ListingsRepository
import com.hyperscopic.motix.data.Market
import com.hyperscopic.motix.data.Order
import com.hyperscopic.motix.data.Seat
import com.hyperscopic.motix.data.Showing
import com.hyperscopic.motix.data.SimulatedTicketingPartner
import com.hyperscopic.motix.data.Theater
import com.hyperscopic.motix.data.TicketingPartner
import com.hyperscopic.motix.platform.CarPosition
import com.hyperscopic.motix.platform.DrivingState
import com.hyperscopic.motix.platform.RouteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

enum class Screen { LINEUP, TRAILER, THEATERS, BUY, TICKET, SEARCH }

/** What is being bought. */
data class Cart(val adults: Int = 2, val youths: Int = 0, val seats: List<Seat> = emptyList()) {
    val tickets: Int get() = adults + youths
}

/** A theater with what is left of today for a film there (or tomorrow). */
data class TheaterShowings(val theater: Theater, val metres: Double?, val showings: List<Showing>)

/**
 * The app's state: the listings for where the car is, the screen stack, and the booking.
 *
 * Loading follows one rule: show the last good data at once, fetch behind it, and show a spinner only on a first load
 * ever. The film list of the last launch comes off the head unit before the car has reported a position; the theaters
 * (never stored: Google Places terms) arrive about a second after it does.
 */
class MoTixViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MoTixApp
    private val main = Handler(Looper.getMainLooper())
    private val store = LineupStore(application)
    private val listings: ListingsRepository = GoogleListings(
        application,
        GoogleListings.Keys(
            gemini = BuildConfig.GEMINI_API_KEY, vertexProject = BuildConfig.GEMINI_VERTEX_PROJECT, vertexLocation = BuildConfig.GEMINI_VERTEX_LOCATION,
            google = BuildConfig.GOOGLE_API_KEY, tmdbKey = BuildConfig.TMDB_API_KEY, tmdbToken = BuildConfig.TMDB_READ_TOKEN,
        ),
    )
    val partner: TicketingPartner = SimulatedTicketingPartner

    val driving: StateFlow<DrivingState> = app.driving.state
    val route: StateFlow<RouteState> = app.navigator.state
    val configured: Boolean get() = listings.configured

    // ---- Listings ------------------------------------------------------------------------------------------------

    var market by mutableStateOf(store.lastMarket)
        private set
    var filmList by mutableStateOf(store.lastMarket?.let { store.load(it) })
        private set

    /** Null until the first answer for this spot. */
    var theaters by mutableStateOf<List<Theater>?>(null)
        private set
    var area by mutableStateOf<Area?>(null)
        private set
    var position by mutableStateOf<CarPosition?>(null)
        private set
    var filmsFailed by mutableStateOf(false)
        private set
    var theatersFailed by mutableStateOf(false)
        private set
    var now: Instant by mutableStateOf(Instant.now())
        private set

    /** The UI language (set by the UI from the system locale): the neighbourhood name is asked for in it. */
    var korean: Boolean = java.util.Locale.getDefault().language == "ko"
        set(value) {
            if (field != value) {
                field = value
                area = null
                origin?.let { loadArea(it.lat, it.lon) }
            }
        }

    private var origin: CarPosition? = null
    private var filmsJob: Job? = null
    private var theatersJob: Job? = null

    // ---- Screens -------------------------------------------------------------------------------------------------

    val stack = mutableStateListOf(Screen.LINEUP)
    val screen: Screen get() = stack.last()

    var filmId by mutableStateOf<String?>(null)
    var showing by mutableStateOf<Showing?>(null)
    var cart by mutableStateOf(Cart())
    var buyStep by mutableStateOf(0)
    var creditsOpen by mutableStateOf(false)

    /** The Google results page behind a tapped Search suggestion. */
    var searchUrl by mutableStateOf<String?>(null)
        private set
    var booking by mutableStateOf(store.loadTicket()?.takeIf { !it.expired(Instant.now()) })
        private set

    val film: Film? get() = filmList?.films?.let { films -> films.firstOrNull { it.id == filmId } ?: films.firstOrNull() }

    init {
        Images.prune(application)
        viewModelScope.launch { app.location.position.collect { p -> if (p != null) onPosition(p) } }
        viewModelScope.launch {
            while (true) {
                now = Instant.now()
                if (booking?.expired(now) == true) clearBooking()
                delay(20_000)
            }
        }
        // The trailer never survives the car leaving Park: off the stack, so it cannot resume by itself either.
        viewModelScope.launch { driving.collect { if (!it.videoAllowed) stack.removeAll { s -> s == Screen.TRAILER || s == Screen.SEARCH } } }
    }

    private fun onPosition(p: CarPosition) {
        position = p
        val m = Market.at(p.lat, p.lon)
        if (m != market) {
            market = m
            store.lastMarket = m
            filmList = store.load(m)
            theaters = null
            area = null
            origin = null
            filmId = null
            showing = null
        }
        // The same place within about a kilometre keeps its theaters.
        val last = origin
        if (last == null || Geo.metres(last.lat, last.lon, p.lat, p.lon) > 1_000) {
            origin = p
            loadTheaters(p.lat, p.lon)
            loadArea(p.lat, p.lon)
        }
        ensureFilms()
    }

    /** Fetch the film list when there is none or it has gone stale; always behind what is on screen. */
    fun ensureFilms(force: Boolean = false) {
        val m = market ?: return
        if (filmsJob?.isActive == true) return
        val shown = filmList
        if (!force && shown != null && System.currentTimeMillis() - shown.fetchedAtMillis < LineupStore.FRESH_MS) return
        filmsFailed = false
        filmsJob = viewModelScope.launch {
            try {
                val result = listings.films(m, shown?.films.orEmpty()) { partial ->
                    // A first load fills in as it arrives; a refresh swaps in whole, so the screen never flickers.
                    if (shown == null) main.post { if (market == m && filmsJob?.isActive == true) filmList = partial }
                }
                if (market == m) filmList = result
                store.save(result)
            } catch (e: Exception) {
                Log.w(TAG, "films: ${e.message}")
                if (filmList == null) filmsFailed = true
            }
        }
    }

    private fun loadTheaters(lat: Double, lon: Double) {
        val m = market ?: return
        theatersJob?.cancel()
        theatersFailed = false
        theatersJob = viewModelScope.launch {
            try {
                theaters = listings.theaters(m, lat, lon)
            } catch (e: Exception) {
                Log.w(TAG, "theaters: ${e.message}")
                if (theaters == null) theatersFailed = true
            }
        }
    }

    private fun loadArea(lat: Double, lon: Double) {
        val m = market ?: return
        val ko = korean
        viewModelScope.launch {
            runCatching { listings.area(m, lat, lon, ko) }.getOrNull()?.let { if (korean == ko) area = it }
        }
    }

    fun retry() {
        ensureFilms(force = filmList == null)
        origin?.let { if (theaters == null) loadTheaters(it.lat, it.lon) }
    }

    // ---- Showtimes -----------------------------------------------------------------------------------------------

    /** The most popular films play at every theater near the car; the rest at most of them. */
    private fun featured(f: Film): Boolean = (filmList?.films?.indexOfFirst { it.id == f.id } ?: 99) in 0..2

    fun metresTo(t: Theater): Double? = (origin ?: position)?.let { Geo.metres(it.lat, it.lon, t.lat, t.lon) }

    /** The theaters showing [f], nearest first. */
    fun nearby(f: Film): List<TheaterShowings> {
        val m = market ?: return emptyList()
        return theaters.orEmpty().sortedBy { metresTo(it) ?: Double.MAX_VALUE }.mapNotNull { t ->
            partner.showings(f, featured(f), t, m, now).takeIf { it.isNotEmpty() }?.let { TheaterShowings(t, metresTo(t), it) }
        }
    }

    fun theater(id: String?): Theater? = theaters?.firstOrNull { it.id == id }

    // ---- Moving between screens ----------------------------------------------------------------------------------

    fun push(s: Screen) {
        if (stack.last() != s) stack.add(s)
    }

    fun back(): Boolean {
        if (creditsOpen) {
            creditsOpen = false
            return true
        }
        if (screen == Screen.BUY && buyStep in 1..2) {
            buyStep -= 1
            return true
        }
        if (stack.size <= 1) return false
        val left = stack.removeAt(stack.lastIndex)
        if (left == Screen.BUY && buyStep == 3) home() // a finished booking does not go back into the purchase
        return true
    }

    fun home() {
        stack.clear()
        stack.add(Screen.LINEUP)
        showing = null
        buyStep = 0
    }

    fun select(f: Film) {
        filmId = f.id
        showing = null
    }

    fun openTrailer() {
        if (driving.value.videoAllowed && film?.trailer != null) push(Screen.TRAILER)
    }

    /** A tapped Google Search suggestion: its results page, while parked. */
    fun openSearch(url: String) {
        if (!driving.value.parked) return
        searchUrl = url
        push(Screen.SEARCH)
    }

    fun openTheaters() {
        showing = null
        push(Screen.THEATERS)
    }

    fun startBuying() {
        if (!driving.value.paymentAllowed || showing == null) return
        cart = Cart()
        buyStep = 0
        push(Screen.BUY)
    }

    /** Pay: the partner holds the seats, charges the saved card and issues the ticket. Only while parked. */
    suspend fun pay(): Boolean {
        val f = film ?: return false
        val s = showing ?: return false
        val t = theater(s.theaterId) ?: return false
        val m = market ?: return false
        if (!driving.value.paymentAllowed) return false
        val made = partner.book(Order(f, t, s, cart.adults, cart.youths, cart.seats, m))
        if (!driving.value.paymentAllowed) return false // the car left Park while the card was being charged: nothing is booked
        booking = made
        store.saveTicket(made)
        buyStep = 3
        return true
    }

    fun clearBooking() {
        booking = null
        store.saveTicket(null)
        stack.removeAll { it == Screen.TICKET }
        if (stack.isEmpty()) stack.add(Screen.LINEUP)
    }

    // ---- Navigation in the car -----------------------------------------------------------------------------------

    /** One tap: the theater becomes the destination in the car navigation, which comes to the front. */
    fun routeTo(t: Theater) = app.navigator.routeTo(t.lat, t.lon, t.name(korean), t.address(korean))

    fun routeToBooking() {
        val b = booking ?: return
        app.navigator.routeTo(b.theaterLat, b.theaterLon, b.theaterName(korean), b.theaterAddress(korean))
        // What stays on screen beside the map is the ticket.
        stack.clear()
        stack.add(Screen.LINEUP)
        stack.add(Screen.TICKET)
    }

    fun showNavigation() = app.navigator.showNavigation()
    fun clearRouteMessage() = app.navigator.clear()

    private companion object {
        const val TAG = "MoTix"
    }
}
