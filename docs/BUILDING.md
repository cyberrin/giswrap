# Building GisWrap

    ./gradlew :app:assembleDebug        # or installDebug with a device attached

An Android SDK is the only thing you need installed. Gradle fetches the rest.

| Need | How it is satisfied |
|---|---|
| Gradle 9.6.1 | the wrapper downloads it — use `./gradlew`, never a system `gradle` |
| A JDK to **run** Gradle | yours; AGP 9 wants **17–21**, see [below](#the-jdk-is-two-different-knobs) |
| JDK 21 to **compile** | `jvmToolchain(21)`, auto-provisioned by the foojay resolver |
| Android SDK platform 37 | your SDK manager; point at it with an untracked `local.properties` |
| Every library | downloaded by Gradle from Google's Maven repo |

The debug build carries an `applicationIdSuffix` of `.debug`, so it installs
beside a release build rather than replacing it.

## Quirks worth knowing before you start

**The version numbers are not preferences.** M3 Expressive does not exist in
stable material3 1.4.0, and `material3-1.5.0-alpha25.aar` declares:

    minCompileSdk=37
    minAndroidGradlePluginVersion=9.1.0

AGP 9 then requires Gradle 9, which requires a newer Kotlin. One constraint
propagating, not four independent upgrades. There is no Compose BOM here
because a BOM carries no alphas.

**Versions are pinned exactly, on purpose.** Four dependencies are pre-release —
`material3-1.5.0-alpha25`, `compose-ui-1.12.0-beta01`, `glance-1.3.0-alpha02`,
`navigation-2.10.0-rc01` — and an alpha renames and deletes API between builds.
A floating range (`1.+`, `latest.release`) would break the build on a day nobody
touched it, with nothing in the history to explain why. Bump them deliberately
and together. Pinning costs you nothing to clone: Gradle downloads these, so
nothing has to match on your machine.

**AGP 9 has two breaking changes** that bite immediately: `android.kotlinOptions`
is gone, and AGP 9 has built-in Kotlin support, so applying the
`org.jetbrains.kotlin.android` plugin is now a hard error.

**KSP lags Kotlin, and this toolchain is far enough ahead that it matters.**
KSP 2.3.11 works against Kotlin 2.4.10 only because KSP decoupled its version
from the compiler's at 2.3.0. Check KSP, Hilt and Room before any Kotlin bump.

## The JDK is two different knobs

Easy to confuse, and they fail differently.

`jvmToolchain(21)` in `app/build.gradle.kts` pins the JDK that **compiles** the
code. `settings.gradle.kts` applies the foojay resolver, so a clone provisions it
rather than failing.

Gradle **itself** runs on whatever JDK launched it, and AGP 9 wants 17–21. If
your default is newer, point Gradle at one — in your *user* file, so the pin does
not follow the project onto other machines:

    # ~/.gradle/gradle.properties
    org.gradle.java.home=/usr/lib/jvm/java-21-openjdk

## Android SDK

Pointed at by an untracked `local.properties`. Needs `platforms/android-37.0`.

Build-tools is pinned to `37.0.0` to stay level with `compileSdk`. Without the
pin AGP 9.3.1 falls back to its own default of `36.0.0`, which builds but pairs
an older `aapt2` with API 37 resources.

## Release signing

Signed with a real key if `keystore.properties` exists, and falls back to the
debug key with a loud warning if it does not — so the project builds anywhere,
but a debug-signed "release" cannot ship unnoticed.

Generate the key once, from the repo root:

    keytool -genkeypair -v \
      -keystore release.jks \
      -alias gismeteo \
      -keyalg RSA -keysize 4096 -validity 10000 \
      -dname "CN=yourname, O=yourname, C=XX"

Then `keystore.properties` (untracked, alongside the `.jks`):

    storeFile=release.jks
    storePassword=…
    keyAlias=gismeteo
    keyPassword=…

**Keep both forever, and back them up somewhere off the machine.** The signing
key *is* the app's identity: Play Protect keys on package name + certificate, so
a new key makes every future build look like a brand-new app and restarts its
reputation from zero.

**Judge performance on a release build.** Debug runs with `debuggable=true`,
which disables ART optimisations, and the Compose compiler leaves composition
tracing and live-literal indirection in. A 2x difference is normal.

## Testing

    ./gradlew :app:testDebugUnitTest                       # 165 tests, offline
    ./gradlew :app:testDebugUnitTest -Pgismeteo.live=true  # +3 against the real site

`SourcesTest` is the one that matters. It runs the Kotlin parsers over pages
captured from gismeteo.ru and compares every field against what the Python
parsers produced from the same bytes — the check that Jsoup's selectors pick the
same elements BeautifulSoup's did.

Fixtures live in `app/src/test/resources` for two cities, chosen deliberately:
**surgut** carries `ru` + `kk` translations so it exercises the language
fallback, and **london** is in `Europe/London`, which observes DST.

What the golden tests do *not* prove: that either implementation still matches
the live site. They pass happily against stale bytes while the app breaks. That
is what `-Pgismeteo.live=true` is for.

They also cannot see a difference between the desktop JVM and Android. That gap
shipped a real bug once: `disallow-doctype-decl` is a Xerces feature name, the
JVM's parser accepted it, and Android's Expat-backed one threw — so the suite was
green and the 3-day range failed on the phone. XML hardening is best-effort now,
and *“a feature this parser does not have is not fatal”* asserts the rule rather
than the outcome.
