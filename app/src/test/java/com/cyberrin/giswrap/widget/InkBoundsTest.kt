package com.cyberrin.giswrap.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InkBoundsTest {
    private fun grid(size: Int, vararg lit: Pair<Int, Int>): IntArray {
        val pixels = IntArray(size * size)
        lit.forEach { (x, y) -> pixels[y * size + x] = 0xFF112233.toInt() }
        return pixels
    }

    @Test
    fun `the box is inclusive of the outermost lit pixels`() {
        assertEquals(listOf(2, 3, 5, 6), inkBounds(grid(8, 2 to 3, 5 to 6), 8))
    }

    @Test
    fun `a single pixel is a one by one box, not an empty one`() {
        assertEquals(listOf(4, 4, 4, 4), inkBounds(grid(8, 4 to 4), 8))
    }

    @Test
    fun `barely visible pixels still count`() {
        val pixels = IntArray(64)
        pixels[3 * 8 + 3] = 0x01000000
        assertEquals(listOf(3, 3, 3, 3), inkBounds(pixels, 8))
    }

    @Test
    fun `nothing painted has no box`() {
        assertNull(inkBounds(IntArray(64), 8))
    }
}
