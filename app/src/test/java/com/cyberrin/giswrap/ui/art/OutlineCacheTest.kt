package com.cyberrin.giswrap.ui.art

import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class OutlineCacheTest {
    private fun key(
        size: Size = Size(300f, 120f),
        squareness: Float = 5f,
        depth: Float = 12f,
        seed: Int = 7,
        lobeLength: Float = 62f,
        drift: Float = 4f,
        unevenness: Float = 0.75f,
        perLobe: Int = 10,
    ) = outlineKey(size, squareness, depth, seed, lobeLength, drift, unevenness, perLobe)

    @Test
    fun `an identical request is the same key`() {
        assertEquals(key(), key())
    }

    @Test
    fun `the seed alone separates the cards on a page`() {
        assertNotEquals(key(seed = 1), key(seed = 2))
    }

    @Test
    fun `every dial takes part`() {
        val base = key()
        assertNotEquals(base, key(squareness = 3f))
        assertNotEquals(base, key(depth = 20f))
        assertNotEquals(base, key(lobeLength = 40f))
        assertNotEquals(base, key(drift = 9f))
        assertNotEquals(base, key(unevenness = 0.2f))
        assertNotEquals(base, key(perLobe = 6))
    }

    @Test
    fun `a different size is a different outline`() {
        assertNotEquals(key(), key(size = Size(301f, 120f)))
        assertNotEquals(key(), key(size = Size(300f, 121f)))
    }

    @Test
    fun `sub-pixel differences are the same outline`() {
        assertEquals(key(), key(size = Size(300.004f, 119.997f)))
    }
}
