package com.cyberrin.giswrap.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccentShareTest {
    @Test
    fun `the accent starts at the bottom of the range the others move in`() {
        assertEquals(ACCENT_SHARE, CuteDials().amounts[CuteColour.ACCENT])
        assertEquals(0f, ACCENT_SHARE, "the minimum of the sliders' 0..1 range")
    }

    @Test
    fun `the three grounds keep their own share`() {
        val dials = CuteDials()
        listOf(CuteColour.PRIMARY, CuteColour.SECONDARY, CuteColour.TERTIARY).forEach {
            assertEquals(1f, dials.amounts[it], "$it lost its default")
        }
    }

    @Test
    fun `every colour still has an entry`() {
        assertTrue(CuteDials().amounts.keys.containsAll(CuteColour.entries.toSet()))
    }
}
