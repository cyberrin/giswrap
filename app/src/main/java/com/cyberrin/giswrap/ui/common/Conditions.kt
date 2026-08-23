package com.cyberrin.giswrap.ui.common

import com.cyberrin.giswrap.ui.art.Sky

// Gismeteo sends condition text in Russian whatever the app language, so English
// is built from the decoded Sky rather than translated. Russian keeps the
// upstream prose: it is written by people and reads better than any table.
//
// Pure, so it is testable without a device; the resource lookup that chooses
// between this and the upstream text lives in conditionText().
fun englishConditions(sky: Sky): String? {
    val cloud = when {
        sky.cloud >= 4 -> "Overcast"
        sky.cloud == 3 -> "Cloudy"
        sky.cloud == 2 -> "Partly cloudy"
        sky.cloud == 1 -> "Mostly clear"
        else -> "Clear"
    }

    val water = when {
        sky.storm -> "thunderstorm"
        sky.snow >= 3 -> "heavy snow"
        sky.snow == 2 -> "snow"
        sky.snow == 1 -> "light snow"
        sky.rain >= 3 -> "downpour"
        sky.rain == 2 -> "rain"
        sky.rain == 1 -> "light rain"
        else -> null
    }

    val parts = listOfNotNull(cloud, water, if (sky.fog) "fog" else null)
    return parts.firstOrNull()?.let { head ->
        (listOf(head) + parts.drop(1)).joinToString(", ")
    }
}

// Which of the two a locale wants is a resource qualifier's job, not a locale
// comparison in code: values-ru/ says upstream, everything else says generated.
// Pure and flag-taking so the caller can read the resource once and memoise.
fun conditionText(fromUpstream: Boolean, icon: String?, description: String?,
                  locale: java.util.Locale = java.util.Locale.getDefault()): String? =
    if (fromUpstream) conditions(description, locale) else englishConditions(Sky.of(icon, description))
