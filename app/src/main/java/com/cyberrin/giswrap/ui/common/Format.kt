package com.cyberrin.giswrap.ui.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val MISSING = "—"


fun signed(value: Double?): String {
    if (value == null) return MISSING
    val rounded = value.roundToInt()

    return if (rounded == 0) "0°" else String.format(Locale.US, "%+d°", rounded)
}

fun plain(value: Double?, unit: String = "", digits: Int = 0): String {
    if (value == null) return MISSING
    val number = if (digits > 0) {
        String.format(Locale.US, "%.${digits}f", value)
    } else {
        value.roundToInt().toString()
    }
    return "$number$unit"
}

fun plain(value: Int?, unit: String = ""): String =
    if (value == null) MISSING else "$value$unit"

private fun String.capitalizedFirst(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

fun conditions(text: String?, locale: Locale = Locale.getDefault()): String? =
    text?.takeIf { it.isNotBlank() }?.capitalizedFirst(locale)

fun dayLabel(date: LocalDate, first: LocalDate, todayLabel: String, locale: Locale): String {
    val name = if (date == first) {
        todayLabel
    } else {
        date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }

    val month = date.month.getDisplayName(TextStyle.FULL, locale)
    return "${name.capitalizedFirst(locale)}, ${date.dayOfMonth} ${month.capitalizedFirst(locale)}"
}

fun retrievedStamp(at: LocalDateTime, today: LocalDate = LocalDate.now()): String {
    val clock = String.format(Locale.US, "%02d:%02d", at.hour, at.minute)
    if (at.toLocalDate() == today) return clock
    val date = String.format(Locale.US, "%02d.%02d", at.dayOfMonth, at.monthValue)
    return "$date, $clock"
}

fun dateHeading(date: LocalDate, locale: Locale): String {
    val month = date.month.getDisplayName(TextStyle.FULL, locale).capitalizedFirst(locale)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale).capitalizedFirst(locale)
    return "$weekday, ${date.dayOfMonth} $month"
}
