package com.cyberrin.giswrap.ui.theme

import com.cyberrin.giswrap.ui.settings.parseHex
import com.cyberrin.giswrap.ui.settings.toHex

import com.cyberrin.giswrap.domain.model.CuteColour
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class HctTest {
    private fun hex(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)

    @Test
    fun `the sRGB primaries read as their published HCT values`() {
        val red = hctOf(Color(0xFFFF0000))
        assertEquals(27.408, red.hue, 0.001)
        assertEquals(113.357, red.chroma, 0.001)
        assertEquals(53.233, red.tone, 0.001)

        val blue = hctOf(Color(0xFF0000FF))
        assertEquals(282.788, blue.hue, 0.001)
        assertEquals(87.230, blue.chroma, 0.001)
        assertEquals(32.303, blue.tone, 0.001)
    }

    @Test
    fun `a seed comes back out of its own primary ramp`() {
        val seed = Color(0xFF6750A4)
        val hct = hctOf(seed)
        assertEquals(hex(hctColor(hct.hue, hct.chroma, 40.0)), "#6750A4")
    }

    @Test
    fun `the baseline palette lands within one step of the published one`() {
        val hct = hctOf(Color(0xFF6750A4))
        val ramp = TonalPalette(hct.hue, maxOf(hct.chroma, 48.0))
        val published = mapOf(
            10 to 0x21005D, 30 to 0x4F378B, 40 to 0x6750A4,
            80 to 0xD0BCFF, 90 to 0xEADDFF, 100 to 0xFFFFFF,
        )
        published.forEach { (tone, expected) ->
            val got = ramp.tone(tone).toArgb() and 0xFFFFFF
            listOf(16, 8, 0).forEach { shift ->
                val delta = abs(((got shr shift) and 0xFF) - ((expected shr shift) and 0xFF))
                assertTrue(
                    delta <= 1,
                    "tone $tone gave ${hex(ramp.tone(tone))}, expected #%06X".format(expected),
                )
            }
        }
    }

    @Test
    fun `tones are exact even where the ramp runs out of colour`() {
        val ramp = TonalPalette(hctOf(Color(0xFF00AAFF)).hue, 120.0)
        listOf(4, 6, 10, 20, 40, 60, 90, 98).forEach { tone ->
            val got = hctOf(ramp.tone(tone)).tone
            assertEquals(tone.toDouble(), got, 1.0, "tone $tone drifted")
        }
    }

    @Test
    fun `near-black tones are not crushed to black`() {
        val ramp = TonalPalette(hctOf(Color(0xFF00AAFF)).hue, 8.0)
        listOf(4, 6).forEach { tone ->
            val luminance = ramp.tone(tone).toArgb() and 0xFF
            assertTrue(luminance > 8, "tone $tone came out as ${hex(ramp.tone(tone))}")
        }
        assertTrue(listOf(4, 6, 10, 12, 17, 22, 24).zipWithNext().all { (lo, hi) ->
                (ramp.tone(lo).toArgb() and 0xFF) < (ramp.tone(hi).toArgb() and 0xFF)
            }, "the dark surface ladder must ascend")
    }

    @Test
    fun `a scheme keeps its error colours whatever the accent`() {
        val green = seededColorScheme(Color(0xFF00FF00), dark = false)
        val blue = seededColorScheme(Color(0xFF0000FF), dark = false)
        assertEquals(green.error, blue.error)
        assertTrue(green.primary != blue.primary, "the accent must actually reach the scheme")
    }

    @Test
    fun `light and dark schemes put their surfaces on opposite ends`() {
        val light = seededColorScheme(DefaultSeed, dark = false)
        val dark = seededColorScheme(DefaultSeed, dark = true)
        assertTrue(light.surface.toArgb() and 0xFF > 0xD0)
        assertTrue(dark.surface.toArgb() and 0xFF < 0x40)
        assertTrue(light.onSurface.toArgb() and 0xFF < 0x40)
        assertTrue(dark.onSurface.toArgb() and 0xFF > 0xD0)
    }
}

class HexFieldTest {
    @Test
    fun `six digits name an opaque colour`() {
        assertEquals(Color(0xFF00AAFF), parseHex("#00AAFF"))
        assertEquals(0xFF, (parseHex("#000000")!!.toArgb() ushr 24) and 0xFF)
    }

    @Test
    fun `the hash is optional and the case does not matter`() {
        assertEquals(parseHex("#00AAFF"), parseHex("00aaff"))
    }

    @Test
    fun `anything short of six digits is not yet a colour`() {
        listOf("", "#", "#0", "#00AAF", "#00AAFFF").forEach {
            assertNull(parseHex(it), "'$it' should not parse")
        }
    }

    @Test
    fun `a colour round trips through its own text`() {
        listOf(0xFF00AAFF, 0xFF000000, 0xFFFFFFFF, 0xFFB33A2E).forEach { argb ->
            val color = Color(argb)
            assertEquals(color, parseHex(color.toHex()))
        }
    }

    @Test
    fun `the text is always six digits behind a hash`() {
        assertEquals("#00AAFF", Color(0xFF00AAFF).toHex())
        assertEquals("#000A0B", Color(0xFF000A0B).toHex(), "the leading zeroes must survive")
    }
}

class CutePaletteTest {
    private val all = CuteColour.entries

    @Test
    fun `the palette is the one that was asked for`() {
        val light = cuteColorScheme(dark = false)
        assertEquals(Color(0xFFFCDCE1), light.cute(CuteColour.PRIMARY))
        assertEquals(Color(0xFFF0D9EF), light.cute(CuteColour.SECONDARY))
        assertEquals(Color(0xFFE9ECCE), light.cute(CuteColour.TERTIARY))
        assertEquals(Color(0xFFF4849D), light.cute(CuteColour.ACCENT))
    }

    @Test
    fun `the dark palette is its own, not the light one dimmed`() {
        val light = cuteColorScheme(dark = false)
        val dark = cuteColorScheme(dark = true)
        assertTrue(hctOf(light.surface).tone > 90)
        assertTrue(hctOf(dark.surface).tone < 25)
        assertTrue(hctOf(dark.surface).chroma > 3, "a dark ground still wants some warmth")
    }

    @Test
    fun `an element always gets the same colour`() {
        val weights = all.associateWith { 1f }
        repeat(3) {
            assertEquals(
                cuteColourFor("current-detail", weights),
                cuteColourFor("current-detail", weights),
            )
        }
    }

    @Test
    fun `a colour at zero is never drawn`() {
        val weights = all.associateWith { 1f } + (CuteColour.ACCENT to 0f)
        val drawn = (1..400).map { cuteColourFor("card-$it", weights) }.toSet()
        assertTrue(CuteColour.ACCENT !in drawn, "accent was excluded")
        assertEquals(3, drawn.size, "the other three still share it")
    }

    @Test
    fun `weights set roughly how much of the interface each colour covers`() {
        val onlyPrimary = all.associateWith { 0f } + (CuteColour.PRIMARY to 1f)
        assertTrue((1..200).all { cuteColourFor("card-$it", onlyPrimary) == CuteColour.PRIMARY })

        val even = all.associateWith { 1f }
        val counts = (1..800).groupingBy { cuteColourFor("card-$it", even) }.eachCount()
        all.forEach { colour ->
            val share = (counts[colour] ?: 0) / 800.0
            assertTrue(share in 0.15..0.35, "$colour took $share of the interface")
        }
    }

    @Test
    fun `all four at zero still yields a colour`() {
        assertEquals(CuteColour.PRIMARY, cuteColourFor("x", all.associateWith { 0f }))
    }

    @Test
    fun `a scheme is built once per seed and mode`() {
        val seed = Color(0xFF3F51B5)
        assertSame(seededColorScheme(seed, dark = false), seededColorScheme(seed, dark = false))
        assertNotSame(seededColorScheme(seed, dark = false), seededColorScheme(seed, dark = true))
        assertNotSame(
            seededColorScheme(seed, dark = false),
            seededColorScheme(Color(0xFFB53F51), dark = false),
        )
    }

    @Test
    fun `a cached scheme is still the right scheme`() {
        val seed = Color(0xFF2E7D32)
        val fresh = seededColorScheme(seed, dark = false).primary
        repeat(16) { seededColorScheme(Color(0xFF000000L or (it * 0x010101L)), false) }
        assertEquals(fresh, seededColorScheme(seed, dark = false).primary)
    }
}
