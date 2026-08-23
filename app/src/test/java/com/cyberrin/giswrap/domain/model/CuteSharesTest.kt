package com.cyberrin.giswrap.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CuteSharesTest {
    private val dials = CuteDials()

    @Test
    fun `the third ground is gone at night and kept by day`() {
        assertEquals(0f, dials.shares(dark = true)[CuteColour.TERTIARY])
        assertEquals(1f, dials.shares(dark = false)[CuteColour.TERTIARY])
    }

    @Test
    fun `the accent is pinned in either flavour`() {
        listOf(true, false).forEach { dark ->
            assertEquals(ACCENT_SHARE, dials.shares(dark)[CuteColour.ACCENT], "dark=$dark")
        }
    }

    @Test
    fun `the two grounds that remain keep what was set`() {
        val set = CuteDials(amounts = mapOf(
            CuteColour.PRIMARY to 0.3f,
            CuteColour.SECONDARY to 0.7f,
            CuteColour.TERTIARY to 1f,
            CuteColour.ACCENT to 1f,
        ))
        val night = set.shares(dark = true)
        assertEquals(0.3f, night[CuteColour.PRIMARY])
        assertEquals(0.7f, night[CuteColour.SECONDARY])
    }

    @Test
    fun `something is always left to paint with`() {
        listOf(true, false).forEach { dark ->
            val shares = dials.shares(dark)
            assertTrue(shares.values.any { it > 0f }, "nothing left to paint with, dark=$dark")
        }
    }

    @Test
    fun `every colour still has an entry`() {
        listOf(true, false).forEach { dark ->
            assertTrue(dials.shares(dark).keys.containsAll(CuteColour.entries.toSet()))
        }
    }
}
