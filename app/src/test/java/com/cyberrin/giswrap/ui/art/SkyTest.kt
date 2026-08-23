package com.cyberrin.giswrap.ui.art

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkyTest {
    @Test
    fun `both separator conventions parse the same`() {
        assertEquals(Sky.parse("d.c3.r2"), Sky.parse("d_c3_r2"))
        assertEquals(3, Sky.parse("d.c3.r2").cloud)
        assertEquals(2, Sky.parse("d_c3_r2").rain)
    }

    @Test
    fun `day and night`() {
        assertFalse(Sky.parse("d_c1").night)
        assertTrue(Sky.parse("n_c2").night)

        assertFalse(Sky.parse("c4.r1").night)
        assertEquals(4, Sky.parse("c4.r1").cloud)
    }

    @Test
    fun `precipitation kinds are kept apart`() {
        val rain = Sky.parse("d_c3_r2")
        assertEquals(2, rain.rain)
        assertEquals(0, rain.snow)
        assertFalse(rain.storm)

        val snow = Sky.parse("d_c4_s3")
        assertEquals(3, snow.snow)
        assertEquals(0, snow.rain)

        val storm = Sky.parse("c4_st")
        assertTrue(storm.storm)

        assertEquals(0, storm.snow)
    }

    @Test
    fun `bare and blank codes are clear skies rather than crashes`() {
        assertEquals(Sky(), Sky.parse(null))
        assertEquals(Sky(), Sky.parse(""))
        assertEquals(Sky(night = false), Sky.parse("d"))
        assertTrue(Sky.parse("n").night)

        assertEquals(Sky(), Sky.parse("xyzzy"))
    }

    @Test
    fun `rain named only in the description still reaches the icon`() {
        val lightRain = Sky.of("d_c1", "малооблачно, небольшой дождь")
        assertEquals(1, lightRain.rain)
        assertTrue(lightRain.wet, "and so the sky counts as wet")

        val rain = Sky.of("d_c3", "пасмурно, дождь")
        assertEquals(2, rain.rain)
        assertTrue(rain.wet)
    }

    @Test
    fun `a dry description leaves the sky dry`() {
        assertFalse(Sky.of("d_c1", "малооблачно").wet)
        assertFalse(Sky.of("d_c2", "облачно").wet)
        assertEquals(2, Sky.of("d_c2", "облачно").cloud)
    }

    @Test
    fun `snow and storms are recognised too`() {
        assertEquals(2, Sky.of("d_c3", "снег").snow)
        assertEquals(1, Sky.of("d_c3", "небольшой снег").snow)
        assertTrue(Sky.of("c4", "гроза").storm)
        assertEquals(0, Sky.of("c4", "гроза").snow, "a storm is not snow")
    }

    @Test
    fun `a downpour outranks plain rain`() {
        assertEquals(3, Sky.of("d_c3", "ливень").rain)
        assertEquals(1, Sky.of("d_c3", "морось").rain)
    }

    @Test
    fun `the code wins wherever it says anything`() {
        val fromCode = Sky.of("d.c3.r2", "малооблачно")
        assertEquals(2, fromCode.rain)
        assertEquals(3, fromCode.cloud)
    }

    @Test
    fun `a missing description is harmless`() {
        assertEquals(Sky.parse("d_c2"), Sky.of("d_c2", null))
        assertEquals(Sky.parse("d_c2"), Sky.of("d_c2", ""))
    }

    private fun art(code: String?, description: String? = null) = WeatherArt.of(code, description)

    @Test
    fun `clear skies get a bare light source`() {
        assertEquals(WeatherArt.SUN, art("d_c0"))

        assertEquals(WeatherArt.SUN, art("d_c1"))
        assertEquals(WeatherArt.MOON, art("n_c0"))
    }

    @Test
    fun `cover pairs with the light source, then replaces it`() {
        assertEquals(WeatherArt.SUN_CLOUD, art("d_c2"), "c2 is where the cloud joins it")
        assertEquals(WeatherArt.MOON_CLOUD, art("n_c2"))

        assertEquals(WeatherArt.LIGHT_CLOUDS, art("d_c3"))
        assertEquals(WeatherArt.LIGHT_CLOUDS, art("n_c3"))
        assertEquals(WeatherArt.HEAVY_CLOUDS, art("c4"))
    }

    @Test
    fun `anything falling outranks the cover`() {
        assertEquals(WeatherArt.HEAVY_RAIN, art("r2"))
        assertEquals(WeatherArt.SNOW, art("d_c2_s1"))

        assertEquals(WeatherArt.RAIN, art("d_c1", "малооблачно, небольшой дождь"))
    }

    @Test
    fun `intensity picks the heavier drawing`() {
        assertEquals(WeatherArt.RAIN, art("d_c3_r1"))
        assertEquals(WeatherArt.HEAVY_RAIN, art("d_c3_r2"))
        assertEquals(WeatherArt.SNOW, art("d_c3_s1"))
        assertEquals(WeatherArt.HEAVY_SNOW, art("d_c3_s2"))
    }

    @Test
    fun `a storm with rain is a different picture from a bare bolt`() {
        assertEquals(WeatherArt.LIGHTNING, art("c4_st"))
        assertEquals(WeatherArt.THUNDERSTORM, art("c4_st_r1"))
    }

    @Test
    fun `fog reaches the mist drawing, and only through the description`() {
        assertEquals(WeatherArt.MIST, art("d_c2", "туман"))
        assertEquals(WeatherArt.MIST, art("d_c3", "дымка"))

        assertEquals(WeatherArt.RAIN, art("d_c3_r1", "туман"))
    }

    @Test
    fun `night is carried through to a cloudy sky`() {
        assertTrue(Sky.parse("n_c2").night)
        assertEquals(WeatherArt.MOON_CLOUD, art("n_c2"), "and the moon is still visible at that cover")
        assertFalse(Sky.parse("d_c2").night)
    }

    @Test
    fun `every drawing in the set is reachable`() {
        val reaching = mapOf(
            WeatherArt.SUN to ("d_c0" to null),
            WeatherArt.MOON to ("n_c0" to null),
            WeatherArt.SUN_CLOUD to ("d_c2" to null),
            WeatherArt.MOON_CLOUD to ("n_c2" to null),
            WeatherArt.LIGHT_CLOUDS to ("d_c3" to null),
            WeatherArt.HEAVY_CLOUDS to ("c4" to null),
            WeatherArt.MIST to ("d_c2" to "туман"),
            WeatherArt.RAIN to ("d_c3_r1" to null),
            WeatherArt.HEAVY_RAIN to ("d_c3_r2" to null),
            WeatherArt.SNOW to ("d_c3_s1" to null),
            WeatherArt.HEAVY_SNOW to ("d_c3_s2" to null),
            WeatherArt.LIGHTNING to ("c4_st" to null),
            WeatherArt.THUNDERSTORM to ("c4_st_r1" to null),
        )
        assertEquals(WeatherArt.entries.size, reaching.size, "every drawing needs a case")
        reaching.forEach { (expected, input) ->
            val (code, description) = input
            assertEquals(expected, art(code, description), "$code / $description")
        }
    }

    @Test
    fun `the recorded ink centres stay inside their box`() {
        WeatherArt.entries.forEach { art ->
            assertTrue(art.inkCenterY in 0.40f..0.65f, "${art.name} has an implausible ink centre: ${art.inkCenterY}")
            assertEquals(0.5f - art.inkCenterY, art.centeringOffsetY, 1e-6f, "${art.name} corrects by the wrong amount")
        }
    }

    @Test
    fun `precipitation hangs low and a bare sun does not`() {
        assertEquals(0f, WeatherArt.SUN.centeringOffsetY, 0.005f)
        assertTrue(WeatherArt.HEAVY_SNOW.centeringOffsetY < -0.05f)
        assertTrue(WeatherArt.THUNDERSTORM.centeringOffsetY < WeatherArt.RAIN.centeringOffsetY)
    }

    @Test
    fun `each drawing names four distinct drawables`() {
        WeatherArt.entries.forEach { art ->
            val ids = listOf(
                art.resource(dark = false, cute = false),
                art.resource(dark = true, cute = false),
                art.resource(dark = false, cute = true),
                art.resource(dark = true, cute = true),
            )
            assertEquals(4, ids.toSet().size, "${art.name} reuses a drawable")
        }
    }

    @Test
    fun `no two drawings share a drawable`() {
        val all = WeatherArt.entries.flatMap { art ->
            listOf(
                art.resource(dark = false, cute = false),
                art.resource(dark = true, cute = false),
                art.resource(dark = false, cute = true),
                art.resource(dark = true, cute = true),
            )
        }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `every code seen in the fixtures parses`() {
        val observed = listOf(
            "c4.r1", "c4.r2", "d", "d.c2", "d.c3", "d.c3.r1", "d.c3.r2",
            "d_c0", "d_c1", "d_c2", "d_c3", "d_c3_r2", "n", "n.c2", "n.c3",
        )
        observed.forEach { code ->
            val sky = Sky.parse(code)
            assertTrue(sky.cloud in 0..4, "$code gave a nonsense cloud level")
            assertTrue(sky.rain in 0..3, "$code gave a nonsense rain level")
        }
    }
}
