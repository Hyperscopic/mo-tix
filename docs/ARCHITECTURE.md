# Mo-Tix architecture

One activity, one view model, three small packages. No server.

```
                       ┌──────────────────────────── ui/ (Jetpack Compose) ────────────────────────────┐
                       │ MoTixRoot ── Lineup · Trailer · Theaters · Buy · Ticket · SearchPage · Sources │
                       └──────────────────────────────────────▲────────────────────────────────────────┘
                                                              │ state
                                                     MoTixViewModel
                                    (listings, screen stack, cart, booking, the loading rule)
                          ┌───────────────────────────────────┴───────────────────────────────────┐
                          ▼                                                                       ▼
        platform/  (the car)                                               data/  (the listings)
        ├ DrivingMonitor   AAOS CarUxRestrictionsManager, CarPropertyManager      ├ ListingsRepository  (interface)
        ├ CarLocation      Pleos FusedLocation SDK → Android LocationManager      │   └ GoogleListings   Gemini · Places · TMDB · YouTube oEmbed
        └ CarNavigator     Pleos NaviHelper SDK                                   ├ LineupStore         last good film list, the ticket
                                                                                  ├ Images              poster / still cache
                                                                                  └ TicketingPartner    (interface)
                                                                                      └ SimulatedTicketingPartner
```

## The car

### Driving restrictions (`platform/DrivingMonitor.kt`)

Pleos policy is "standard first", so this uses only the AAOS Car API.

- `CarUxRestrictionsManager` is the authority. The platform derives the restrictions from gear, speed and region; the
  app applies what it is told. On the Pleos Connect emulator, Park gives `requiresDistractionOptimization = false`;
  idling and moving give `true` with `UX_RESTRICTIONS_NO_VIDEO | UX_RESTRICTIONS_NO_SETUP` (0x50).
- `CarPropertyManager` → `GEAR_SELECTION` (permission `android.car.permission.CAR_POWERTRAIN`) can only make Mo-Tix
  stricter: `parked` is "no restriction active **and** the gear, when known, is Park".

What follows from `DrivingState`:

| State | Mo-Tix |
|---|---|
| `parked` | everything |
| not `parked` | the root composable shows only `TicketScreen(static = true)`, or `DrivingNotice` with no ticket |
| `videoAllowed` turns false | the view model removes the trailer (and the search page) from the screen stack: the WebView is destroyed, so playback stops and cannot resume when the car parks again |
| `paymentAllowed` false | `startBuying()` and `pay()` refuse; a payment in flight is cancelled with its screen, and a booking that completes after the car left Park is discarded |

The activity declares `distractionOptimized`, because an app that does not is terminated by Pleos whenever the car is
not parked, and the point is to keep the ticket card beside the map. Declaring it makes the app responsible for the
restrictions, which is what the table above is.

### Position (`platform/CarLocation.kt`)

`FusedLocation.isSupported()` → `initialize()` → `getLastKnownLocation` + `registerFusedLocationCallback`, behind the
runtime permission `pleos.car.permission.FUSED_LOCATION`. When the service is not supported, the permission is refused
or nothing arrives, the Android `LocationManager` (last known fix, then GPS / network updates) answers instead. A fresh
fused position wins over an Android one. With no fix at all the position stays null and the app says so; it never
invents one. Updates stop when the activity stops.

### Navigation (`platform/CarNavigator.kt`)

"Get route" is `NaviHelper.requestRoute(RouteInfo(latitude, longitude, poiName, address, …))`. When the navigation
answers (`onRouteStarted`, `onRoutingCompleted`), Mo-Tix calls `requestNavigationScreen(Foreground)` and keeps the
reported time and distance for its status line. Errors (`NaviErrorCode`) and a navigation that never answers (20 s)
become a plain message. Mo-Tix has no map and no router of its own.

### Windows (`ui/MoTixRoot.kt`)

Pleos decides the window: the full app area (2:0) when Mo-Tix is the only app showing, half of it (1:1) beside another
app. Apps cannot ask for one or the other; the driver does, with the window handle or three-finger gestures. Mo-Tix
therefore lays every screen out twice, switching on its own width (under 1000 dp is the half window):

| Screen | 2:0, landscape | 1:1, portrait |
|---|---|---|
| Lineup | text and trailer still side by side, posters underneath | still on top, text, two actions, posters |
| Theaters | poster column + two columns of theaters, showing panel from the right | one column, showing sheet from the bottom |
| Buy | order summary column + step | summary strip + step |
| Ticket | full-height poster + details + QR | poster and details, QR card at the bottom |

After "Get route" the ticket is the top screen, so it is what sits beside the navigation.

## The listings

### One interface (`data/ListingsRepository.kt`)

```kotlin
interface ListingsRepository {
    val configured: Boolean
    suspend fun films(market: Market, known: List<Film>, onPartial: (FilmList) -> Unit): FilmList
    suspend fun theaters(market: Market, lat: Double, lon: Double): List<Theater>
    suspend fun area(market: Market, lat: Double, lon: Double, korean: Boolean): Area?
}
```

`GoogleListings` implements it on the device with keys from `BuildConfig`. A hosted implementation (keys on a server,
one request per launch) can replace it without touching the view model or a screen: that is the production plan.

### The market

`Market.at(lat, lon)`: South Korea or the US, from the car's position. It sets the film search wording, the theater
search (the three Korean chains by name; a nearby search in the US), the TMDB language, the currency and the units.

### Where each thing comes from (`data/GoogleListings.kt`)

1. **Films**: one Gemini 2.5 Flash call with the Google Search tool, asking for the ten biggest films in the market's
   theaters this week as JSON (titles in both languages, genres, running time, synopsis, release date). Thinking is
   off: it is a lookup, and it answers in about 15 s. A "featured" film listed in `assets/trailers.json` is checked
   with a second call and listed when it is showing.
2. **Artwork and film details**: TMDB, matched by title, never by "first result". A result counts when (1) its Korean
   title is the film's Korean title (or that title with a subtitle, the year agreeing), (2) its title or original
   title is the English title, (3) its Korean title is nearly the same with the year agreeing, or (4) TMDB's English
   title is the film's English title. Posters prefer the market language; stills are the ones with no text. The
   matched film's page also gives the running time and the distributor synopsis (used in place of the search
   answer's one-line summary), and the Korean title when the search answer had none. A "Korean title" that has no
   Hangul in it (the search sometimes returns a Japanese original title there) is not accepted as one.
3. **Trailers**: the hand-checked id in `assets/trailers.json`, else the official trailer TMDB lists (market language
   first), else a YouTube Data API search when the key allows it. Every id is confirmed with YouTube oEmbed.
4. **Theaters**: Places API (New). Korea: `searchText` for CGV, Megabox and Lotte Cinema in a box around the car, in
   English and Korean, open branches with at least 50 ratings, the eight nearest. US: `searchNearby` for
   `movie_theater`, open, at least 200 ratings, the five nearest.
5. **The neighbourhood name** in the header: the address components of the nearest place.

The film search answers with a couple more films than are shown. Candidates TMDB cannot confirm are dropped when at
least five films are confirmed: an unmatched title is most often one the search got wrong, and it would show with no
poster.

### Cached mode and the snapshot

`cachedMode` (a stored setting, on by default) makes the view model take everything above from the bundled
`assets/snapshot/seoul.json` instead: films with their artwork addresses and trailer ids, theaters, the place they are
for and the date. No provider is called, no key and no location permission is needed, and the car is placed where the
snapshot was captured. Showtimes, booking, the trailer player and the route work exactly as in live mode. The lineup
shows the snapshot's date and a Saved / Live switch; Live needs keys in the build, and says so when there are none.

To refresh the snapshot: run a **debug** build with keys in Live mode at the place you want (for example
`adb emu geo fix 127.0557 37.5446`). When the load completes, the debug build writes the result in the snapshot format:

```bash
adb shell "run-as com.hyperscopic.motix --user 10 cat files/snapshot_export.json" > app/src/main/assets/snapshot/seoul.json
```

Then set `area.ko` and `area.en` in the file (the export only knows the name in the current language). The film
search varies from run to run, so look the list over before committing it.

### The loading rule (`MoTixViewModel`)

> Show the last good data at once, fetch behind it, and show a spinner only on a first load ever.

- The film list of the last launch is read from `LineupStore` in the view model's initialiser, before the car has
  reported a position.
- A list older than six hours is searched again behind the one on screen and swapped in whole when it is complete.
  Artwork already known is carried over, not fetched again.
- On a first load ever the list fills in as it arrives: titles first, artwork as each film's lookup lands.
- A failed refresh leaves the old list up. Only a failed first load shows an error, with "Try again".
- Theaters are asked for when the car has moved more than a kilometre.

### What is stored, and for how long

| Data | Kept | Why |
|---|---|---|
| Film list and its Google Search credit | `files/films_<market>.json`, at most 30 days | Google's limit for stored grounded results |
| Theater names, addresses | never (memory only) | Places API terms |
| Posters, stills, trailer thumbnails | `cache/img`, at most 7 days | TMDB and YouTube terms: no long-term copies |
| The ticket | `files/ticket.json`, until the film has ended | so the ticket card survives a restart while driving |
| The bundled snapshot | `assets/snapshot/seoul.json`, in the APK and the repository | a dated sample so the app runs with no keys (cached mode) |
| API keys | compiled into the APK (`BuildConfig`), or none with `-PnoKeys` | see README: restrict them; production uses a hosted repository |

## Ticketing (`data/Ticketing.kt`)

```kotlin
interface TicketingPartner {
    val simulated: Boolean
    fun showings(film: Film, featured: Boolean, theater: Theater, market: Market, now: Instant): List<Showing>
    fun prices(market: Market): Prices
    fun seatPlan(showing: Showing): SeatPlan
    suspend fun book(order: Order): Booking
}
```

`SimulatedTicketingPartner` is the only implementation: a deterministic representative schedule, a generated seat map,
a booking that charges nothing. See "Simulated ticketing" in the README. The QR encoder (`data/QrCode.kt`) is a small
byte-mode, ECC-M implementation written for this app.

## Google Search suggestions

Grounding with Google Search requires showing the returned Search suggestions with the grounded content.
`SearchSuggestions` renders `searchEntryPoint.renderedContent` as provided in a small WebView under the film list. A
tapped suggestion opens `SearchPage`: Google's results page and nothing else (other hosts are not followed, sign-in is
not reachable), parked only.

**This is switched off in the current build** (`SHOW_SEARCH_SUGGESTIONS = false` in `ui/TrailerScreen.kt`): the strip
is hidden from the lineup. The code and the stored suggestions are still there. Before a public release either switch
it back on or stop using grounded results for the film list (TMDB's now-playing list by region would also remove the
15-second first search).

## Not here yet

- A real `TicketingPartner` and a hosted `ListingsRepository`.
- A media session for the trailer (the stock YouTube player handles its own audio focus).
- Voice: Gleo AI / the Pleos SpeechToText SDK.
