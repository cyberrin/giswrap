# How GisWrap is put together

## Where the data comes from

**Directly from gismeteo.ru.** There is no server in between — no hosting, no
LAN dependency. The app works on mobile data and on someone else's Wi-Fi.

Ported from the Python fetcher in
[`gismeteo-fetcher`](https://github.com/cyberrin/gismeteo-fetcher), which carries
this code's first 70 commits. The two share no build coupling.

| Python | Kotlin |
|---|---|
| `client.py` | `data/remote/GismeteoRemoteDataSource.kt` |
| `sources.py` | `data/remote/parser/GismeteoSources.kt` |
| `cache.py` | `data/local/db/` — a Room table, not an in-memory LRU |
| `models.py` (raw shapes) | `data/remote/dto/UpstreamDto.kt` |
| `models.py` (`Clean*`) | `domain/model/Weather.kt` |
| `httpx` | Ktor |
| `BeautifulSoup` | Jsoup — same CSS selectors |
| `xml.etree` | `javax.xml.parsers.DocumentBuilder` |
| `zoneinfo` | `java.time.ZoneId` |

**One deliberate divergence.** `client.py` builds search results from
`item.name`, `item.country.name` and `item.district.name`. None of those exist
upstream — every readable name lives in `translations`. `UpstreamDto.kt` reads
that instead, so the app shows `Сургут / Россия, …` where the Python yields the
bare slug.

## Layout

```
com.cyberrin.giswrap
├── domain/          pure Kotlin — no Android, no Compose, no Room, no Ktor
│   ├── model/       City, Forecast, Appearance, Outcome, WeatherError
│   ├── repository/  the four interfaces the app is written against
│   └── usecase/     the two rules worth a name, plus the distance maths
├── data/            everything that talks to something
│   ├── remote/      Ktor + the DTOs + the Jsoup/XML scrapers
│   ├── local/       Room, DataStore, the geocoder, user-supplied files
│   ├── repository/  Repositories.kt — all four implementations
│   └── di/          AppModule.kt — every Hilt module
├── ui/
│   ├── navigation/  the @Serializable routes and the NavHost that wires them
│   ├── forecast/    ViewModel (with its contract) + Route + Screen
│   ├── search/      ViewModel + Screen
│   ├── settings/    the same, plus the dial bindings and the accent picker
│   ├── theme/       HCT, the seeded palette, the type scale
│   ├── art/         the weather set, the drawn shapes, the star field
│   └── common/      the components three screens share
└── widget/          Glance, its palette, and the WorkManager refresh
```

The dependency arrow points inward and never back out: `domain` compiles with
none of the libraries above it on its classpath. That is the only boundary the
build enforces — **everything else is grouped by what gets read together, not by
what kind of thing it is.** A screen's `XUiState` / `XEvent` / `XEffect` are the
vocabulary its ViewModel speaks, so they live in `XViewModel.kt`. The Room
entities, DAOs, `@Database` and mappers change as one unit, so they are one
`Database.kt`.

A file is split when it is genuinely large or genuinely separable —
`ForecastScreen.kt` is a thousand lines of Composables and stays apart from its
Route. Size is the reason, not category.

State is `StateFlow` collected with `collectAsStateWithLifecycle()`. Effects —
navigation, snackbars — are a `Channel`, not state: an effect kept in state fires
again on the next configuration change.

## Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.4.10 (K2) |
| Build | Gradle Kotlin DSL + `gradle/libs.versions.toml` |
| Annotation processing | KSP 2.3.11 — no KAPT anywhere |
| UI | Compose, Material 3 Expressive, single Activity |
| Navigation | Navigation Compose, type-safe `@Serializable` routes |
| DI | Dagger Hilt |
| Network | Ktor (OkHttp engine) + kotlinx.serialization |
| Relational store | Room + KSP, schemas exported to `app/schemas` |
| Preferences | DataStore Preferences |
| Background | WorkManager (`@HiltWorker`) |
| Widget | Glance, with the panel, icon crop and chosen face painted in-process |
| Images | Coil 3 |
| Tests | JUnit 5, MockK, Turbine, kotlinx-coroutines-test |

Not used, deliberately: XML layouts, KAPT, Gson, `SharedPreferences`, `LiveData`,
`AsyncTask`, string routes.

## Language

English is the default resource set; Russian lives in `values-ru/`. A Russian
device resolves to Russian, everything else falls back to English, and the
setting under **Text** overrides that.

Switching happens by providing a locale-adjusted `LocalContext` and
`LocalConfiguration` over the tree — which is what `stringResource` already
resolves against. No `androidx.appcompat` for one setting, no `LocaleManager`
(API 33, against a minSdk of 26), and no Activity restart.

Two things could not simply be translated:

- **Errors.** `WeatherError` is a type, not a sentence: `domain/` compiles
  without Android on its classpath and can never reach a string resource. It
  becomes text once, at the UI edge, where an exhaustive `when` turns a new
  variant into a compile error rather than an untranslated string on screen.
- **Condition text.** Gismeteo sends it in Russian whatever the app language.
  `Sky` already decodes it structurally, so English is *generated* from that
  rather than translated; Russian keeps the upstream prose, which people wrote.
  Which one a locale wants is decided by a resource `bool`, not by comparing
  locales in code.

The Russian string literals in `Sky.kt` are upstream's wire format and must stay:
fog and thunder appear only in the prose, never in the icon code.

## Search

Two surfaces over one query:

- **Typing** opens a suggestions overlay, debounced at 300 ms with a
  two-character floor. Per-keystroke requests would send thirteen calls to
  someone else's server for “Екатеринбург” and throw away twelve.
- **Submitting** navigates to a full results page — a destination, so back,
  restoration after process death and a surviving query all come from Navigation
  rather than from a boolean.

`runSearch` checks the query is still current before publishing, so a slow
response for an earlier prefix cannot overwrite results for a later one.

## Use my location

Gismeteo has **no lookup by coordinates** — `/mq/city/near/`, `/nearest/` and
`/geo/` all 404, and an empty query is a 400. So the only way in is a text
search: fix → geocode to a locality name → search that name → pick the *nearest*
match.

That last step is not a nicety. “Сургут” matches the town, its airport 10 km
away, and an unrelated Surgut 1500 km away in another oblast; the upstream ranks
by its own relevance and carries no distance information, so `LocateNearestCity`
computes it.

## Known gaps

- **Room's DAO is not covered by the unit suite.** Opening a database needs
  Android; the repository above it is tested against a fake DAO instead. An
  `androidTest` with `inMemoryDatabaseBuilder` is the missing piece.
- **No Compose UI tests yet.** The screens are already shaped for them —
  `XScreen(state, onEvent)` takes no ViewModel — but the runner is not wired up.
- **The widget's text does not follow the system font-size setting when a
  typeface has been chosen.** Glance's `TextStyle` carries no family and a
  `Typeface` cannot be parcelled to the launcher, so a chosen face is painted to
  a bitmap — fixed at the size it was drawn. Users on the default system font get
  a real `Text` and keep the scaling.
- **English condition text is generated, not translated.** It reads a little
  flatter than the Russian upstream prose it stands in for.
