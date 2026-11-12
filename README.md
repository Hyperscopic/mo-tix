# Mo-Tix

**영화가 있는 곳으로 · Be where the movies are.**

[한국어](#한국어) · [English](#english)

![Pleos Connect 에뮬레이터에서 실행한 Mo-Tix 상영작 화면 / The Mo-Tix lineup on the Pleos Connect emulator](docs/screenshots/ko/04_lineup_film_selected.jpg)

| 예고편(주차 중) · Trailer, parked | 극장과 상영 시간 · Theaters and showtimes | 좌석 선택 · Seats |
|---|---|---|
| ![Trailer](docs/screenshots/ko/05_trailer_parked.jpg) | ![Theaters](docs/screenshots/ko/09_showtime_panel.jpg) | ![Seats](docs/screenshots/ko/12_seats.jpg) |

| 예매 완료와 수령 QR · Booked, with the QR | 차량 내비게이션 경로 안내 · Route in the native navigation | 주행 중: 정적인 티켓 카드 · Driving: the static ticket card |
|---|---|---|
| ![Booked](docs/screenshots/ko/15_booked_qr.jpg) | ![Half window](docs/screenshots/ko/17_half_window_ticket_beside_navigation.jpg) | ![Driving](docs/screenshots/ko/18_driving_static_ticket_card.jpg) |

전체 화면 · All screens: [docs/SCREENS.md](docs/SCREENS.md)

---

## 한국어

Mo-Tix는 **Pleos Connect**(Android Automotive OS 14)용 위치 기반 엔터테인먼트 커머스 앱입니다. 차량의 위치와 대화면을
활용해 지금 가까운 극장에서 상영 중인 영화를 보여 주고, 주차 중에는 공식 예고편을 재생하며, 좌석을 예매하고, 한 번의
터치로 극장을 차량 내비게이션의 목적지로 보냅니다. 주행 중에는 정적인 티켓 카드만 표시합니다.

Kotlin과 Jetpack Compose로 만든 네이티브 앱입니다. 별도의 서버나 PC 서비스 없이 헤드 유닛이 데이터 제공처와 직접
통신하며, 차량과는 Pleos Connect SDK와 표준 AAOS Car API로 연동합니다.

### 제출 정보

| 항목 | 내용 |
|---|---|
| 앱 이름 | Mo-Tix |
| 한 줄 소개 | 차량의 위치와 대화면으로 가까운 극장의 상영작을 찾고, 예고편 감상부터 예매, 극장까지의 길 안내를 차 안에서 한 번에 끝내는 앱 |
| APK 위치 | [`apk/Hyperscopic_MoTix_release.apk`](apk/Hyperscopic_MoTix_release.apk) (v1.1.1, release 서명, API 키 미포함) |
| 실행 방법 | 아래 [실행 방법](#실행-방법) 참고: APK 설치 후 Pleos 앱 목록에서 Mo-Tix 실행 |
| 개발·테스트 환경 | **Pleos Connect Emulator v2.1.0** (AVD 'Pleos Connect v2.1.0', 시스템 이미지 `android-34` / `pleos_car_emulator` / `x86_64`, Android 14) |
| 화면 규격 | **2560 × 1440 px, 160 dpi** (Connect-S). 앱 창: 전체 2:0 = 약 1682 × 1210 dp, 분할 1:1 = 약 841 × 1210 dp. 두 크기 모두 지원 |
| 필요한 설정 | 없음. API 키와 위치 권한 없이 실행됩니다(캐시 모드). 포스터와 예고편을 받아 오기 위한 네트워크 연결만 필요합니다 |
| 사용한 SDK | Pleos NaviHelper 3.0.5, Pleos FusedLocation 1.1.3, AAOS Car API(CarUxRestrictionsManager, CarPropertyManager) |
| 언어 | 한국어, 영어(차량 언어 설정을 따름) |

> **API 키 없이도 실행됩니다.** Mo-Tix는 기본적으로 *캐시 모드*로 동작합니다. 앱에 함께 저장된 서울 상영 정보(성수동,
> 2026년 11월 12일 기준: 영화 8편, 극장 8곳)를 보여 주므로 새로 빌드한 상태에서도 전체 흐름을 확인할 수 있습니다.
> 상영작 화면의 **저장 데이터 / 실시간** 전환 버튼으로, 직접 발급한 키를 넣어 빌드한 경우 차량 위치 기준의 실시간 상영
> 정보를 켤 수 있습니다. [캐시 모드](#캐시-모드)를 참고하세요.

> **예매는 시뮬레이션입니다.** Mo-Tix의 데이터 출처에는 상영 시간표 서비스가 없고, 아직 예매 파트너가 연동되어 있지
> 않습니다. 상영 시간은 대표 시간표이고 좌석 배치는 생성된 것이며, **실제 결제는 이루어지지 않고 티켓도 발급되지
> 않습니다.** 화면은 완성된 제품과 같은 흐름으로 동작합니다. [예매 시뮬레이션](#예매-시뮬레이션)을 참고하세요.

### 실행 방법

1. 42dot의 **Pleos Connect 에뮬레이터 v2.1.0**을 설치하고 AVD를 만듭니다
   ([설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)).
   **반드시 콜드 부팅**하세요. 퀵 부트 스냅샷으로 시작하면 차량 HAL이 동작하지 않습니다.
2. APK를 설치합니다.
   ```bash
   adb -s <serial> install -r apk/Hyperscopic_MoTix_release.apk
   ```
3. Pleos 앱 목록에서 **Mo-Tix**를 엽니다. 저장된 서울(성수동) 상영작 화면으로 바로 시작합니다.
4. **주행 상태**는 에뮬레이터가 제공하는 *Pleos Connect Virtual Console*(기어, 속도)로 바꿉니다. 터미널에서는 다음과
   같이 할 수 있습니다.
   ```bash
   adb -s <serial> forward tcp:18088 tcp:8088
   curl -X PUT "http://127.0.0.1:18088/vehicle/gear?gear=Drive"
   curl -X PUT "http://127.0.0.1:18088/vehicle/controls?speed=30&accelerator=20&brake=0"
   curl -X PUT "http://127.0.0.1:18088/vehicle/controls?speed=0&accelerator=0&brake=60"
   curl -X PUT "http://127.0.0.1:18088/vehicle/gear?gear=Park"
   ```
5. **분할 창**: 창 가장자리의 핸들(점 세 개)을 밀면 Mo-Tix가 내비게이션 옆에 배치됩니다.
6. (실시간 모드, 키를 넣은 빌드) 차량 위치를 지정한 뒤 **실시간**으로 전환합니다.
   ```bash
   adb -s <serial> emu geo fix 127.0557 37.5446      # 경도가 먼저입니다: 서울 성수동
   ```
   차량 위치가 필요한 이유를 안내한 뒤, Pleos가 권한을 두 번 묻습니다(Android 위치, Pleos 융합 위치).

언어는 차량 설정을 따릅니다(차량 설정 › 일반 설정 › 언어).

### 주요 기능

1. **위치 기반 상영작.** 차량 위치를 기준으로 현재 상영 중인 영화를 포스터와 스틸, 한국어·영어 제목과 함께 보여
   줍니다. 한국에서는 가까운 CGV·메가박스·롯데시네마 지점을, 미국에서는 가까운 영화관을 거리와 함께 표시합니다.
2. **대화면 예고편.** 공식 예고편을 YouTube 플레이어로 전체 창에서 사운드와 함께 재생합니다. **주차 중에만** 가능하며,
   차량이 P단을 벗어나는 순간 플레이어가 종료되고 스스로 다시 재생되지 않습니다.
3. **차 안에서 예매.** 극장과 시간, 인원과 좌석을 고르고 등록된 카드로 결제합니다. **주차 중에만** 가능합니다.
   (시뮬레이션입니다. 아래 참고)
4. **수령 QR.** 예매가 끝나면 QR 코드와 예매 번호가 담긴 티켓 카드가 표시됩니다.
5. **원탭 경로 안내.** '경로 안내'를 누르면 극장이 Pleos 내비게이션의 목적지로 전달되고 내비게이션이 앞으로
   나옵니다. 지도 옆 분할 창(1:1)에서는 Mo-Tix가 포스터·시간·좌석·QR이 담긴 티켓 카드로 표시됩니다.
6. **주행을 고려한 설계.** 정차·주행 중에는 정적인 티켓 카드만(티켓이 없으면 안내 문구만) 표시합니다. 영상도,
   결제도, 움직이는 요소도 없습니다.

### Pleos Connect 연동

| 기능 | 사용한 것 | 위치 |
|---|---|---|
| 극장까지 경로 안내 | **Pleos NaviHelper SDK**(`ai.pleos.playground:NaviHelper:3.0.5`): `requestRoute` 후 `requestNavigationScreen(Foreground)`. 소요 시간과 거리는 `onRouteStarted` / `onDestinationInfo`로 받습니다. Mo-Tix는 지도를 그리거나 경로를 계산하지 않습니다. | [`platform/CarNavigator.kt`](app/src/main/java/com/hyperscopic/motix/platform/CarNavigator.kt) |
| 차량 위치 | **Pleos FusedLocation SDK**(`ai.pleos.playground:FusedLocation:1.1.3`). 서비스가 지원되지 않거나 응답이 없으면 Android `LocationManager`를 사용합니다. | [`platform/CarLocation.kt`](app/src/main/java/com/hyperscopic/motix/platform/CarLocation.kt) |
| 주행 제한 | Pleos 정책("표준 우선")에 따라 표준 AAOS Car API를 사용합니다. `CarUxRestrictionsManager`(`isRequiresDistractionOptimization`, `UX_RESTRICTIONS_NO_VIDEO`)를 기준으로 하고, `CarPropertyManager`의 기어(`GEAR_SELECTION`)는 제한을 더 엄격하게만 만듭니다. 액티비티는 `distractionOptimized`를 선언하고 스스로 제한을 적용합니다. | [`platform/DrivingMonitor.kt`](app/src/main/java/com/hyperscopic/motix/platform/DrivingMonitor.kt) |
| 윈도우 정책 | 크기 조절이 가능한 하나의 액티비티가 전체 앱 영역(2:0)과 분할 창(1:1) 모두에 맞게 배치됩니다. | [`ui/MoTixRoot.kt`](app/src/main/java/com/hyperscopic/motix/ui/MoTixRoot.kt) |
| 디자인 가이드 | Asta Sans 서체, 가이드의 타이포그래피 스케일과 기본 색(#596BFF), 큰 터치 영역, 주행 중 정적 콘텐츠만 표시 | [`ui/Theme.kt`](app/src/main/java/com/hyperscopic/motix/ui/Theme.kt) |

구성 요소의 관계, 로딩·캐시 규칙, 주행 로직은 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)(영문)에 정리했습니다.

### 데이터 출처

| 항목 | 출처 |
|---|---|
| 현재 상영작 | Google Gemini 2.5 Flash의 **Google 검색 그라운딩** |
| 극장 | **Google Places API (New)**: 한국에서는 CGV·메가박스·롯데시네마 텍스트 검색, 미국에서는 주변 검색 |
| 포스터, 스틸, 줄거리, 러닝타임, 공식 예고편 ID | **TMDB API** |
| 예고편 | **YouTube 임베디드 플레이어**의 공식 예고편(YouTube oEmbed로 확인) |
| 경로와 길 안내 | 차량 내비게이션(Pleos NaviHelper) |

출처 표기와 라이선스는 [NOTICE.md](NOTICE.md)를 참고하세요. *This product uses the TMDB API but is not endorsed or
certified by TMDB.* (이 제품은 TMDB API를 사용하지만 TMDB의 보증이나 인증을 받지 않았습니다.)

### 캐시 모드

`cachedMode`는 기본으로 켜져 있습니다. 이 모드에서는 영화, 이미지 주소, 예고편 ID, 극장 정보를
[`app/src/main/assets/snapshot/seoul.json`](app/src/main/assets/snapshot/seoul.json)에서 읽습니다. 2026년 11월 12일에
서울 성수동을 기준으로 실제 데이터 출처에서 받아 온 결과를 저장한 것이며, 상영작 화면에 '11월 12일 (목) 기준'으로
표시됩니다.

- **키도, 위치 권한도 필요 없습니다.** 차량이 스냅숏을 저장한 위치에 있는 것으로 간주합니다.
- 상영 정보 이후의 동작은 실시간 모드와 같습니다. 상영 시간은 오늘 날짜로 만들어지고, 포스터와 예고편은 TMDB와
  YouTube에서 스트리밍되며(네트워크 연결은 필요합니다), '경로 안내'는 실제 Pleos 내비게이션으로 전달됩니다.
- 상영작 화면의 **저장 데이터 / 실시간** 버튼으로 모드를 바꿉니다. 실시간 모드에는 빌드에 포함된 키(아래 참고)와 차량
  위치가 필요하며, 키가 없는 빌드는 그 사실을 안내하고 저장 데이터를 계속 보여 줍니다.
- 스냅숏을 갱신하는 방법은 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#cached-mode-and-the-snapshot)(영문)에 있습니다.

### 예매 시뮬레이션

상영 시간 선택부터 QR 코드까지는 모두 하나의 인터페이스,
[`TicketingPartner`](app/src/main/java/com/hyperscopic/motix/data/Ticketing.kt) 뒤에 있습니다. 현재 구현은
`SimulatedTicketingPartner` 하나입니다.

- **상영 시간**은 영화와 극장별 대표 시간표입니다(항상 같은 결과가 나오며 러닝타임 간격으로 배치됩니다). 실제 극장의
  상영 시간이 아닙니다.
- **좌석**은 생성된 좌석 배치이며 실제로 선점되지 않습니다.
- **결제**는 약 1초가 걸리지만 아무것도 청구하지 않습니다. '등록된 카드 ···· 4821'은 실제 카드가 아닙니다.
- **QR 코드**는 티켓이 아닙니다. 휴대폰으로 스캔하면 해당 극장의 해당 영화 실제 상영 시간표 검색 결과가 열립니다
  (한국은 네이버, 그 외는 Google).

멀티플렉스나 예매 대행사 API를 연동할 때는 같은 인터페이스를 구현해(실시간 상영 정보, 좌석 선점, 결제, 티켓 발급)
시뮬레이션을 교체하면 되며, 화면은 바뀌지 않습니다. 앱 화면에는 시뮬레이션이라는 표시가 없습니다.

### 빌드

필요 환경: JDK 17, Android SDK Platform 34. Pleos SDK는 42dot의 공개 Maven 저장소
(`https://nexus-playground.pleos.ai/repository/maven-releases/`)에서 받으며, 이미 설정되어 있습니다.

```bash
cp local.properties.example local.properties   # sdk.dir을 채웁니다(키는 선택 사항)
./gradlew assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                       # local.properties에 MOTIX_KEYSTORE가 있으면 서명됩니다
./gradlew assembleRelease -PnoKeys              # 같은 빌드이지만 APK에 API 키를 넣지 않습니다(캐시 모드 전용)
```

키는 선택 사항입니다. 키가 없으면 앱은 캐시 모드로 동작합니다. 키는 `local.properties`(git에서 제외)에 두며,
[`local.properties.example`](local.properties.example)을 참고하세요.

| 키 | 용도 | 제한 권장 |
|---|---|---|
| `GEMINI_API_KEY` (Vertex AI express 키는 `GEMINI_VERTEX_PROJECT`도 설정) | 상영작 검색 | Gemini / Vertex AI API만 허용 |
| `GOOGLE_API_KEY` | Places API (New) | Places API (New)만 허용, Android 앱 제한(패키지 `com.hyperscopic.motix` + 서명 인증서 SHA-1) |
| `TMDB_API_KEY` 또는 `TMDB_READ_TOKEN` | 포스터, 스틸, 예고편 | (TMDB 키는 제한할 수 없습니다) |

키는 빌드할 때 읽어 APK에 포함됩니다.

### 프로젝트 구조

```
apk/          Hyperscopic_MoTix_release.apk (release, API 키 미포함)
app/src/main/java/com/hyperscopic/motix/
  MainActivity.kt, MoTixApp.kt, MoTixViewModel.kt   상태, 화면 스택, 로딩 규칙
  platform/   CarNavigator (NaviHelper) · CarLocation (FusedLocation) · DrivingMonitor (CarUxRestrictions, 기어)
  data/       ListingsRepository + GoogleListings (Gemini, Places, TMDB, YouTube) · LineupStore · Images
              Ticketing (TicketingPartner, SimulatedTicketingPartner) · QrCode
  ui/         MoTixRoot · Lineup · Trailer · Theaters · Buy · Ticket · Theme (Pleos 타이포그래피 스케일)
app/src/main/assets/snapshot/seoul.json             캐시 모드용 저장 데이터
docs/         ARCHITECTURE.md · SCREENS.md · screenshots/
```

### 알려진 제한 사항

- 상영 시간, 좌석, 결제, 티켓은 시뮬레이션입니다(위 참고).
- 저장된 상영 정보는 특정 날짜(2026년 11월 12일)의 예시 데이터이므로 시간이 지나면 실제 상영작과 달라집니다.
- 예고편은 YouTube 기본 임베디드 플레이어로 재생되며, YouTube가 예고편 앞에 광고를 표시할 수 있습니다.
- 상영작 목록에 붙는 Google 검색 제안 표시는 이 빌드에서 꺼 두었습니다(`ui/TrailerScreen.kt`의
  `SHOW_SEARCH_SUGGESTIONS`). Google 검색 그라운딩 약관은 그라운딩 결과와 함께 검색 제안을 표시하도록 요구하므로,
  공개 배포 전에는 이 옵션을 켜거나 상영작 목록의 출처를 바꿔야 합니다.
- 실시간 모드의 상영작 목록은 그날 Google 검색 그라운딩이 돌려준 결과입니다. 첫 검색에는 15~20초 정도 걸리며,
  이후에는 마지막 목록을 바로 보여 주고 뒤에서 새로 고칩니다.
- 에뮬레이터의 내비게이션 앱은 자체 가상 위치를 사용하므로 경로가 `geo fix` 위치가 아닌 그 위치에서 시작합니다.
  실제 차량에서는 둘 다 차량의 위치를 사용합니다.
- 실제 차량에서는 테스트하지 않았습니다(에뮬레이터에서만 검증).
- 아직 연동하지 않은 것: 예고편용 미디어 세션, 음성(Gleo AI / SpeechToText).

---

## English

Mo-Tix is a location-aware entertainment commerce app for **Pleos Connect** (Android Automotive OS 14). It uses the
car's position and the big screen to show what is playing at the theaters nearby, plays the official trailer while the
car is parked, books seats, and hands the theater to the car's own navigation in one tap. While the car is moving it
shows only a static ticket card.

It is a native Kotlin / Jetpack Compose app. There is no companion server and no PC service: the head unit talks to the
data providers directly, and to the car through the Pleos Connect SDK and the standard AAOS Car API.

### Submission details

| Item | |
|---|---|
| App name | Mo-Tix |
| One-line description | Finds the films playing at theaters near the car, and takes you from the trailer through booking to navigation to the theater, all on the car's big screen |
| APK | [`apk/Hyperscopic_MoTix_release.apk`](apk/Hyperscopic_MoTix_release.apk) (v1.1.1, release-signed, no API keys inside) |
| How to run | See [How to run](#how-to-run): install the APK, open Mo-Tix from the Pleos app library |
| Development and test environment | **Pleos Connect Emulator v2.1.0** (AVD "Pleos Connect v2.1.0", system image `android-34` / `pleos_car_emulator` / `x86_64`, Android 14) |
| Screen | **2560 × 1440 px at 160 dpi** (Connect-S). App window: full 2:0 = about 1682 × 1210 dp, half 1:1 = about 841 × 1210 dp. Both are supported |
| Required settings | None. It runs without API keys or location permission (cached mode). It only needs a network connection for posters and trailers |
| SDKs | Pleos NaviHelper 3.0.5, Pleos FusedLocation 1.1.3, AAOS Car API (CarUxRestrictionsManager, CarPropertyManager) |
| Languages | Korean and English (follows the car's language) |

> **It runs without any API key.** Out of the box Mo-Tix is in *cached mode*: it shows a Seoul lineup saved with the
> app (Seongsu-dong, captured 2026-11-12: 8 films, 8 theaters), so the whole flow works on a fresh build. The
> **Saved / Live** switch on the lineup turns on live listings for wherever the car is, once the build has your own
> keys. See [Cached mode](#cached-mode).

> **Ticketing is simulated.** Mo-Tix's data sources have no showtimes service, and no ticketing partner is connected
> yet. Showtimes are a representative schedule, the seat map is generated, **no payment is taken and no ticket is
> issued**. The screens run the purchase as the finished product would. See [Simulated ticketing](#simulated-ticketing).

### How to run

1. Install 42dot's **Pleos Connect emulator v2.1.0** and create its AVD
   ([setup guide](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)).
   **Cold boot it**: a quick-boot snapshot disables the vehicle HAL.
2. Install the APK:
   ```bash
   adb -s <serial> install -r apk/Hyperscopic_MoTix_release.apk
   ```
3. Open **Mo-Tix** from the Pleos app library. It opens on the saved Seoul (Seongsu-dong) lineup.
4. **Driving states**: use the *Pleos Connect Virtual Console* that the emulator serves (gear, speed). From a terminal:
   ```bash
   adb -s <serial> forward tcp:18088 tcp:8088
   curl -X PUT "http://127.0.0.1:18088/vehicle/gear?gear=Drive"
   curl -X PUT "http://127.0.0.1:18088/vehicle/controls?speed=30&accelerator=20&brake=0"
   curl -X PUT "http://127.0.0.1:18088/vehicle/controls?speed=0&accelerator=0&brake=60"
   curl -X PUT "http://127.0.0.1:18088/vehicle/gear?gear=Park"
   ```
5. **Half window**: swipe the window handle (the three dots on the window's edge) to put Mo-Tix beside the navigation.
6. (Live mode, a build with keys) Place the car, then switch to **Live**:
   ```bash
   adb -s <serial> emu geo fix 127.0557 37.5446      # longitude first: Seongsu-dong, Seoul
   ```
   Mo-Tix explains why it wants the car's position, then Pleos asks twice (Android location, and the Pleos fused
   position).

Language follows the car: Vehicle Settings › General › Language.

### What it does

1. **Location-aware lineup.** The films in theaters now where the car is, with posters, stills and both Korean and
   English titles; the nearest CGV, Megabox and Lotte Cinema branches in Korea (movie theaters in the US), with distances.
2. **Big-screen trailers.** The official trailer in the YouTube player, full window with sound, **only while parked**.
   The moment the car leaves Park the player is destroyed; it does not resume by itself.
3. **Booking in the car.** Pick a theater and a time, the number of tickets and the seats, and pay with the saved card,
   **only while parked**. (Simulated, see below.)
4. **Collection QR.** A booked showing gives a QR code and a booking reference on a ticket card.
5. **One-tap route.** "Get route" sends the theater to the Pleos navigation and brings the navigation forward. In the
   half window (1:1) beside the map, Mo-Tix is the ticket card: poster, time, seats, QR.
6. **Designed for driving.** While the car is idling or moving, Mo-Tix shows only the static ticket card (or, with no
   ticket, a plain notice). No video, no payment, nothing that moves.

### Pleos Connect integration

| Need | What Mo-Tix uses | Where |
|---|---|---|
| Route to the theater | **Pleos NaviHelper SDK** (`ai.pleos.playground:NaviHelper:3.0.5`): `requestRoute`, then `requestNavigationScreen(Foreground)`; time and distance from `onRouteStarted` / `onDestinationInfo`. Mo-Tix draws no map and computes no route. | [`platform/CarNavigator.kt`](app/src/main/java/com/hyperscopic/motix/platform/CarNavigator.kt) |
| The car's position | **Pleos FusedLocation SDK** (`ai.pleos.playground:FusedLocation:1.1.3`), with Android `LocationManager` as the fallback when the service is not supported or stays silent. | [`platform/CarLocation.kt`](app/src/main/java/com/hyperscopic/motix/platform/CarLocation.kt) |
| Driving restrictions | The standard AAOS Car API, as Pleos asks ("standard first"): `CarUxRestrictionsManager` is the authority (`isRequiresDistractionOptimization`, `UX_RESTRICTIONS_NO_VIDEO`); the gear from `CarPropertyManager` (`GEAR_SELECTION`) only makes it stricter. The activity declares `distractionOptimized` and restricts itself. | [`platform/DrivingMonitor.kt`](app/src/main/java/com/hyperscopic/motix/platform/DrivingMonitor.kt) |
| Window policy | One resizable activity that lays out for the full app area (2:0) and the half window (1:1). | [`ui/MoTixRoot.kt`](app/src/main/java/com/hyperscopic/motix/ui/MoTixRoot.kt) |
| Design guide | Asta Sans, the guide's type scale and primary colour (#596BFF), large touch targets; static content only while driving. | [`ui/Theme.kt`](app/src/main/java/com/hyperscopic/motix/ui/Theme.kt) |

How the pieces fit, the loading and caching rules, and the driving logic: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Data sources

| What | Source |
|---|---|
| Films in theaters now | Google Gemini 2.5 Flash with **Grounding with Google Search** |
| Theaters | **Google Places API (New)**: a text search for CGV, Megabox and Lotte Cinema in Korea, a nearby search in the US |
| Posters, stills, synopses, running times, official trailer ids | **TMDB API** |
| Trailers | The official trailer in the **YouTube embedded player**, confirmed with YouTube oEmbed |
| Routes and guidance | The car's navigation (Pleos NaviHelper) |

Attribution and licences: [NOTICE.md](NOTICE.md). *This product uses the TMDB API but is not endorsed or certified by TMDB.*

### Cached mode

`cachedMode` is on by default. In it, the films, artwork addresses, trailer ids and theaters come from
[`app/src/main/assets/snapshot/seoul.json`](app/src/main/assets/snapshot/seoul.json), a snapshot of what the live
sources returned for Seongsu-dong, Seoul, on 2026-11-12. The lineup says so ("as of Thu, Nov 12").

- **No keys and no location permission are needed.** The car is treated as being where the snapshot was captured.
- Everything after the listings is the same as live: showtimes are made for today, posters and trailers stream from
  TMDB and YouTube (so a network connection is still needed), and Get route goes to the real Pleos navigation.
- The **Saved / Live** switch on the lineup changes mode. Live needs keys in the build (below) and the car's position;
  a build without keys says so and stays on the saved listings.
- To refresh the snapshot, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#cached-mode-and-the-snapshot).

### Simulated ticketing

Everything from the showtime chips to the QR code sits behind one interface,
[`TicketingPartner`](app/src/main/java/com/hyperscopic/motix/data/Ticketing.kt). The only implementation today is
`SimulatedTicketingPartner`:

- **Showtimes** are a representative daily schedule per film and theater (deterministic, spaced by the film's running
  time). They are not the theater's real showtimes.
- **Seats** are a generated seat map. Nothing is held.
- **Payment** takes about a second and charges nothing. "Saved card ···· 4821" is not a real card.
- **The QR code** is not a ticket. It opens that theater's real showtimes for that film on a phone (Naver in Korea,
  Google elsewhere).

A multiplex or booking-agency integration implements the same interface (real showtimes, seat holds, payment, ticket
delivery) and replaces the simulation; no screen changes. The app's screens do not label this as a simulation.

### Build

Requirements: JDK 17, Android SDK Platform 34. The Pleos SDKs come from 42dot's public Maven repository
(`https://nexus-playground.pleos.ai/repository/maven-releases/`), already configured.

```bash
cp local.properties.example local.properties   # fill in sdk.dir (keys are optional)
./gradlew assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                       # signed when MOTIX_KEYSTORE is set in local.properties
./gradlew assembleRelease -PnoKeys              # the same, with no API keys in the APK (cached mode only)
```

Keys are optional: with none, the app runs in cached mode. They live in `local.properties` (git-ignored); see
[`local.properties.example`](local.properties.example).

| Key | For | Restrict it to |
|---|---|---|
| `GEMINI_API_KEY` (+ `GEMINI_VERTEX_PROJECT` for a Vertex AI express key) | the film search | the Gemini / Vertex AI API only |
| `GOOGLE_API_KEY` | Places API (New) | the Places API (New), and this Android app (package `com.hyperscopic.motix` + your signing certificate SHA-1) |
| `TMDB_API_KEY` or `TMDB_READ_TOKEN` | posters, stills, trailers | (TMDB keys cannot be restricted) |

The keys are read at build time and compiled into the APK you build.

### Project layout

```
apk/          Hyperscopic_MoTix_release.apk (release, no API keys inside)
app/src/main/java/com/hyperscopic/motix/
  MainActivity.kt, MoTixApp.kt, MoTixViewModel.kt   state, screen stack, the loading rule
  platform/   CarNavigator (NaviHelper) · CarLocation (FusedLocation) · DrivingMonitor (CarUxRestrictions, gear)
  data/       ListingsRepository + GoogleListings (Gemini, Places, TMDB, YouTube) · LineupStore · Images
              Ticketing (TicketingPartner, SimulatedTicketingPartner) · QrCode
  ui/         MoTixRoot · Lineup · Trailer · Theaters · Buy · Ticket · Theme (Pleos type scale)
app/src/main/assets/snapshot/seoul.json             the saved listings for cached mode
docs/         ARCHITECTURE.md · SCREENS.md · screenshots/
```

### Known limits

- Showtimes, seats, payment and tickets are simulated (above).
- The saved listings are a dated sample (2026-11-12); the films in it will go out of date.
- The trailer is the stock YouTube embedded player; YouTube may show an ad before a trailer.
- Google's Search suggestions strip for the grounded film list is switched off in this build
  (`SHOW_SEARCH_SUGGESTIONS` in `ui/TrailerScreen.kt`). Google's terms for Grounding with Google Search ask for it to
  be displayed with grounded results: switch it on, or take the film list from another source, before a public release.
- In live mode the film list is whatever Google Search grounding returns that day; a first search takes about 15 to 20
  seconds (later launches show the last list at once and refresh behind it).
- On the emulator the navigation app keeps its own simulated position, so its route starts from there, not from
  `geo fix`; in a car both come from the vehicle.
- Not tested in a car (verified on the emulator only).
- Not yet integrated: a media session for the trailer, voice (Gleo AI / SpeechToText).

---

## Credits

Mo-Tix by Hyperscopic. 영화 이미지와 예고편의 권리는 각 권리자에게 있습니다. Film artwork and trailers belong to their
rights holders. [NOTICE.md](NOTICE.md)
