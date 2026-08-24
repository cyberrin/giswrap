package com.cyberrin.giswrap.ui.theme

import androidx.compose.ui.text.font.FontFamily
import com.cyberrin.giswrap.domain.model.AppFont
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class TypographyTest {

    // The bug this stands for: SansSerif is the sans-serif *alias*, so it resolves
    // to Roboto and ignores a face the user or OEM set as the system font. Only
    // FontFamily.Default follows it, and the two are indistinguishable on a stock
    // device -- which is why it shipped.
    @Test
    fun `the system font is the platform default, not the sans-serif alias`() {
        assertEquals(FontFamily.Default, fontFamilyFor(AppFont.SYSTEM))
        assertNotEquals(FontFamily.SansSerif, fontFamilyFor(AppFont.SYSTEM))
    }

    @Test
    fun `the other two map to what their names say`() {
        assertEquals(FontFamily.Serif, fontFamilyFor(AppFont.SERIF))
        assertNotEquals(FontFamily.Default, fontFamilyFor(AppFont.DOODLE))
    }

    @Test
    fun `every font resolves to something`() {
        AppFont.entries.forEach { font -> fontFamilyFor(font) }
    }
}
