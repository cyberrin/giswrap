package com.cyberrin.giswrap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DisplayModeTest {
    private val fhd60 = ScreenMode(id = 1, width = 1080, height = 2400, refreshRate = 60f)
    private val fhd120 = ScreenMode(id = 2, width = 1080, height = 2400, refreshRate = 120f)
    private val qhd60 = ScreenMode(id = 3, width = 1440, height = 3200, refreshRate = 60f)
    private val qhd120 = ScreenMode(id = 4, width = 1440, height = 3200, refreshRate = 120f)

    private val all = listOf(fhd60, fhd120, qhd60, qhd120)

    @Test
    fun `it takes the fastest mode at the resolution already in use`() {
        assertEquals(fhd120, fastestModeLike(all, current = fhd60))
    }

    @Test
    fun `it will not trade resolution for rate`() {
        assertEquals(qhd120, fastestModeLike(all, current = qhd60))
    }

    @Test
    fun `a screen already at its ceiling is left alone`() {
        assertNull(fastestModeLike(all, current = fhd120))
        assertNull(fastestModeLike(all, current = qhd120))
    }

    @Test
    fun `a rate a hair above the current one is not an upgrade`() {
        val almost = ScreenMode(id = 9, width = 1080, height = 2400, refreshRate = 60.0002f)
        assertNull(fastestModeLike(listOf(fhd60, almost), current = fhd60))
    }

    @Test
    fun `a display that reports nothing usable asks for nothing`() {
        assertNull(fastestModeLike(emptyList(), current = fhd60))

        assertNull(fastestModeLike(listOf(qhd120), current = fhd60))
    }
}
