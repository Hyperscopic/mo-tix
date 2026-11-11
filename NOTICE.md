# Notices and attribution

Mo-Tix shows data and media from the services below. Each is used under its own terms; nothing here is endorsed by any
of them.

## TMDB

Posters, stills, running times, synopses where the search answer had none, and the ids of official trailers come from
the TMDB API.

> This product uses the TMDB API but is not endorsed or certified by TMDB.

The TMDB logo (`app/src/main/res/drawable/ic_tmdb.xml`, TMDB's "blue_short" logo, unmodified) is shown in the app's
Sources panel next to that notice, as TMDB's attribution terms ask. Images are streamed from TMDB's image server and
kept only in the app's cache directory, for at most seven days. <https://www.themoviedb.org/about/logos-attribution>

## Google

- **Films in theaters now**: Google Gemini with Grounding with Google Search. The Google Search suggestions returned
  with a grounded answer (`searchEntryPoint`) are shown with the film list, unmodified, and open Google's results page.
  A film list is kept on the device for at most thirty days.
- **Theaters**: Google Maps Platform, Places API (New). Theater names and addresses are shown with the "Google Maps"
  attribution and are not stored between launches.

Google, Google Maps and Gemini are trademarks of Google LLC.

## YouTube

Trailers play in the YouTube embedded player (IFrame Player API), unmodified, streamed from YouTube. Trailer thumbnails
come from YouTube's image server. Nothing is downloaded or re-hosted. YouTube is a trademark of Google LLC.

## Pleos Connect

Mo-Tix links 42dot's Pleos Connect SDKs (`ai.pleos.playground:NaviHelper`, `ai.pleos.playground:FusedLocation`),
downloaded at build time from 42dot's Maven repository and used under 42dot's SDK licence. They are not part of this
repository. Pleos and Pleos Connect are trademarks of 42dot / Hyundai Motor Group. Mo-Tix is an independent app and is
not endorsed by them.

## Typeface

Asta Sans (`app/src/main/res/font/asta_sans.ttf`), Copyright 2024 The Asta Sans Project Authors
(<https://github.com/42dot/Asta-Sans>), licensed under the SIL Open Font License, Version 1.1. The licence text is in
`app/src/main/assets/licenses/AstaSans-OFL.txt`.

## Libraries

- AndroidX, Jetpack Compose and Material 3: Apache License 2.0, The Android Open Source Project.
- Kotlin and kotlinx.coroutines: Apache License 2.0, JetBrains.

## Film artwork, titles and trailers

Film posters, stills, titles and trailers are the property of their respective rights holders (studios and
distributors). They appear in Mo-Tix, and in the screenshots in this repository, only as served by TMDB and YouTube, to
identify the films.

## Cinema names

CGV, Megabox, Lotte Cinema, AMC and other cinema names are trademarks of their owners. Mo-Tix has no partnership with
them: the showtimes, seats and bookings in the app are simulated (see README).
