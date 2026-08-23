package com.cyberrin.giswrap.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.abs

class CuteSeedTest {
    @Test
    fun `the same thing always gets the same edge`() {
        val date = LocalDate.of(2026, 8, 9)
        assertEquals(cuteSeed(date), cuteSeed(date))
        assertEquals(cuteSeed("current-detail"), cuteSeed("current-detail"))
    }

    @Test
    fun `different things get different edges`() {
        val seeds = listOf(
            cuteSeed(LocalDate.of(2026, 8, 9)),
            cuteSeed(LocalDate.of(2026, 8, 10)),
            cuteSeed("current-detail"),
            cuteSeed("range-bar"),
            cuteSeed("range-chip"),
        )
        assertEquals(seeds.size, seeds.toSet().size, "every element wants its own outline")
    }

    @Test
    fun `neighbours are not merely one apart`() {
        val days = (1..14).map { cuteSeed(LocalDate.of(2026, 8, it)) }
        days.zipWithNext().forEach { (a, b) ->
            val gap = abs(a.toLong() - b.toLong())
            assertTrue(gap > 1_000_000L, "consecutive days landed $gap apart")
        }
    }

    @Test
    fun `the seeds spread across the whole range rather than clustering`() {
        val seeds = (1..28).map { cuteSeed(LocalDate.of(2026, 8, 1).plusDays(it.toLong())) }
        assertTrue(seeds.any { it < 0 }, "no negative seeds means half the range is unused")
        assertTrue(seeds.any { it > 0 })

        val quarters = seeds.map { ((it.toLong() - Int.MIN_VALUE) * 4 / 4_294_967_296L).toInt() }
        assertTrue(quarters.toSet().size >= 3, "seeds clustered into ${quarters.toSet()}")
    }

    @Test
    fun `a card with nothing to identify it still gets a seed`() {
        assertEquals(cuteSeed(null), cuteSeed(null))
        assertNotEquals(cuteSeed(null), cuteSeed("range-bar"))
    }
}
