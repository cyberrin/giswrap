package com.cyberrin.giswrap.ui.common

import com.cyberrin.giswrap.ui.art.Sky
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ConditionsTest {

    @Test
    fun `cloud cover maps onto the whole scale`() {
        assertEquals("Clear", englishConditions(Sky(cloud = 0)))
        assertEquals("Mostly clear", englishConditions(Sky(cloud = 1)))
        assertEquals("Partly cloudy", englishConditions(Sky(cloud = 2)))
        assertEquals("Cloudy", englishConditions(Sky(cloud = 3)))
        assertEquals("Overcast", englishConditions(Sky(cloud = 4)))
    }

    @Test
    fun `precipitation follows the cloud, heaviest form winning`() {
        assertEquals("Overcast, rain", englishConditions(Sky(cloud = 4, rain = 2)))
        assertEquals("Cloudy, light rain", englishConditions(Sky(cloud = 3, rain = 1)))
        assertEquals("Overcast, downpour", englishConditions(Sky(cloud = 4, rain = 3)))
        assertEquals("Cloudy, snow", englishConditions(Sky(cloud = 3, snow = 2)))
        // A storm outranks whatever is falling; Gismeteo sends both.
        assertEquals("Overcast, thunderstorm", englishConditions(Sky(cloud = 4, rain = 2, storm = true)))
    }

    @Test
    fun `fog is additive, not a replacement`() {
        assertEquals("Clear, fog", englishConditions(Sky(cloud = 0, fog = true)))
        assertEquals("Overcast, rain, fog", englishConditions(Sky(cloud = 4, rain = 2, fog = true)))
    }

    // The real path: an upstream code and its Russian text, as the fixtures carry
    // them. Fog reaches Sky only through the text, so both have to be read.
    @Test
    fun `builds English from what the fixtures actually send`() {
        assertEquals("Overcast, rain", englishConditions(Sky.of("c4.r2", "Пасмурно, дождь")))
        assertEquals("Cloudy, rain", englishConditions(Sky.of("d.c3.r2", "Облачно, дождь")))
        assertEquals("Cloudy", englishConditions(Sky.of("n.c3", "Облачно")))
        assertEquals("Clear, fog", englishConditions(Sky.of("d", "Безоблачно, туман")))
        assertEquals("Overcast, light rain", englishConditions(Sky.of("c4.r1", "Пасмурно, небольшой дождь")))
    }

    @Test
    fun `the switch picks upstream prose only where a locale asked for it`() {
        val icon = "c4.r2"
        val ru = "Пасмурно, дождь"
        assertEquals(ru, conditionText(fromUpstream = true, icon = icon, description = ru))
        assertEquals("Overcast, rain", conditionText(fromUpstream = false, icon = icon, description = ru))
    }

    @Test
    fun `a missing description still yields English, and no Russian leaks through`() {
        assertEquals("Clear", conditionText(fromUpstream = false, icon = null, description = null))
        assertNull(conditionText(fromUpstream = true, icon = null, description = null))
    }
}
