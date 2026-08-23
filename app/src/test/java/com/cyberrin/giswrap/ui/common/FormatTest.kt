package com.cyberrin.giswrap.ui.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FormatTest {

    private val RU = java.util.Locale.forLanguageTag("ru")
    private val EN = java.util.Locale.ENGLISH
    @Test
    fun `lowercase conditions are capitalised`() {
        assertEquals(conditions("облачно, дождь"), "Облачно, дождь")
        assertEquals(conditions("малооблачно"), "Малооблачно")
    }

    @Test
    fun `already capitalised conditions are left alone`() {
        assertEquals(conditions("Облачно, дождь"), "Облачно, дождь")

        assertEquals(conditions("Пасмурно, ДОЖДЬ"), "Пасмурно, ДОЖДЬ")
    }

    @Test
    fun `both sources agree once normalised`() {
        assertEquals(conditions("Облачно, дождь"), conditions("облачно, дождь"))
    }

    @Test
    fun `missing conditions stay missing rather than becoming empty text`() {
        assertNull(conditions(null, RU))
        assertNull(conditions("", RU))
        assertNull(conditions("   ", RU))
    }

    @Test
    fun `today carries its date like every other row`() {
        val first = LocalDate.of(2026, 8, 9)
        assertEquals(dayLabel(first, first, "сегодня", RU), "Сегодня, 9 Августа")
    }

    @Test
    fun `other days are a capitalised weekday and a date`() {
        val first = LocalDate.of(2026, 8, 9)
        assertEquals(dayLabel(LocalDate.of(2026, 8, 10), first, "сегодня", RU), "Понедельник, 10 Августа")
        assertEquals(dayLabel(LocalDate.of(2026, 8, 11), first, "сегодня", RU), "Вторник, 11 Августа")
    }

    @Test
    fun `weekdays are spelled out rather than abbreviated`() {
        val first = LocalDate.of(2026, 8, 9)
        val names = (12..14).map { day ->
            dayLabel(LocalDate.of(2026, 8, day), first, "сегодня", RU).substringBefore(',')
        }
        assertEquals(listOf("Среда", "Четверг", "Пятница"), names)
    }

    @Test
    fun `a single-digit day is not padded now that the month is a word`() {
        val first = LocalDate.of(2026, 1, 2)
        assertEquals(dayLabel(first, first, "сегодня", RU), "Сегодня, 2 Января")
    }

    @Test
    fun `the heading capitalises both month and weekday`() {
        assertEquals(dateHeading(LocalDate.of(2026, 8, 9), RU), "Воскресенье, 9 Августа")
    }

    @Test
    fun `the heading uses the genitive month that follows a day number`() {
        val heading = dateHeading(LocalDate.of(2026, 8, 9), RU)
        assertEquals(true, heading.contains("Августа"))
        assertEquals(false, heading.contains("Август,"))
    }

    @Test
    fun `a reading from today is stamped with the time alone`() {
        val today = LocalDate.of(2026, 8, 11)
        assertEquals(retrievedStamp(LocalDateTime.of(2026, 8, 11, 14, 35), today), "14:35")

        assertEquals(retrievedStamp(LocalDateTime.of(2026, 8, 11, 9, 5), today), "09:05")
        assertEquals(retrievedStamp(LocalDateTime.of(2026, 8, 11, 0, 0), today), "00:00")
    }

    @Test
    fun `a reading from another day carries its date`() {
        val today = LocalDate.of(2026, 8, 11)

        assertEquals(retrievedStamp(LocalDateTime.of(2026, 8, 10, 23, 58), today), "10.08, 23:58")
        assertEquals(retrievedStamp(LocalDateTime.of(2026, 1, 1, 14, 35), today), "01.01, 14:35")
    }

    @Test
    fun `the date appears the moment the day turns, not an hour later`() {
        val today = LocalDate.of(2026, 8, 11)
        assertEquals(retrievedStamp(LocalDateTime.of(2026, 8, 11, 0, 1), today), "00:01")
        assertEquals(retrievedStamp(LocalDateTime.of(2026, 8, 10, 23, 59), today), "10.08, 23:59")
    }

    @Test
    fun `temperatures keep an explicit sign`() {
        assertEquals(signed(14.0), "+14°")
        assertEquals(signed(-3.0), "-3°")
        assertEquals(signed(0.0), "0°")
        assertEquals(signed(null), "—")
    }

    @Test
    fun `the same dates render in English when the locale asks`() {
        val first = LocalDate.of(2026, 8, 9)
        assertEquals("Today, 9 August", dayLabel(first, first, "today", EN))
        assertEquals("Monday, 10 August", dayLabel(LocalDate.of(2026, 8, 10), first, "today", EN))
        assertEquals("Sunday, 9 August", dateHeading(first, EN))
    }

    // The bug this guards: a hardcoded Locale in Format.kt meant English users
    // read English labels wrapped around Russian weekday names.
    @Test
    fun `no Cyrillic survives an English render`() {
        val first = LocalDate.of(2026, 1, 2)
        val rendered = dayLabel(first, first, "today", EN) + dateHeading(first, EN)
        assertTrue(rendered.none { it in 'а'..'я' || it in 'А'..'Я' }, rendered)
    }
}
