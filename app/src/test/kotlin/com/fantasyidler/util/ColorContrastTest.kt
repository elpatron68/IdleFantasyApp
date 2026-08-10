package com.fantasyidler.util

import androidx.compose.ui.graphics.Color
import com.fantasyidler.repository.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {

    @Test
    fun `parseArgb accepts 0x, hash, and bare hex`() {
        val expected = 0xFFC9A94DL
        assertEquals(expected, ColorContrast.parseArgb("0xFFC9A94D"))
        assertEquals(expected, ColorContrast.parseArgb("#FFC9A94D"))
        assertEquals(expected, ColorContrast.parseArgb("FFC9A94D"))
        assertEquals(expected, ColorContrast.parseArgb("  0XFFC9A94D  "))
    }

    @Test
    fun `parseArgb gives six digit hex full opacity`() {
        assertEquals(0xFFC9A94DL, ColorContrast.parseArgb("#C9A94D"))
        assertEquals(0xFFC9A94DL, ColorContrast.parseArgb("C9A94D"))
    }

    @Test
    fun `parseHexColorOrNull rejects partial or invalid input`() {
        assertEquals(null, ColorContrast.parseHexColorOrNull(""))
        assertEquals(null, ColorContrast.parseHexColorOrNull("F"))
        assertEquals(null, ColorContrast.parseHexColorOrNull("FFE8"))
        assertEquals(null, ColorContrast.parseHexColorOrNull("0xFFC9A94"))
        assertEquals(null, ColorContrast.parseHexColorOrNull("not a colour"))
        assertEquals(0xFFC9A94DL, ColorContrast.parseHexColorOrNull("C9A94D") ?: 0L)
        assertEquals(0xFFC9A94DL, ColorContrast.parseHexColorOrNull("#FFC9A94D") ?: 0L)
    }

    @Test
    fun `black on white is maximum contrast`() {
        val ratio = ColorContrast.contrastRatio(Color.Black, Color.White)
        assertEquals(21.0, ratio, 0.01)
    }

    @Test
    fun `contrast is symmetric`() {
        val a = Color(0xFFC9A94D)
        val b = Color(0xFF1A1A2E)
        assertEquals(
            ColorContrast.contrastRatio(a, b),
            ColorContrast.contrastRatio(b, a),
            1e-9,
        )
    }

    @Test
    fun `identical colours have ratio one`() {
        val colour = Color(0xFF16213E)
        assertEquals(1.0, ColorContrast.contrastRatio(colour, colour), 1e-9)
    }

    @Test
    fun `dark theme muted text meets AA on surface variant`() {
        val ratio = ColorContrast.contrastRatio(Color(0xFFAA9A82), Color(0xFF0F3460))
        assertTrue("Expected at least 4.5 but was $ratio", ratio >= ColorContrast.AA_NORMAL_TEXT)
    }

    @Test
    fun `slugify normalises names to storage keys`() {
        assertEquals("my_theme", ThemeRepository.slugify("My Theme!"))
        assertEquals("my_theme", ThemeRepository.slugify("  my   THEME  "))
        assertEquals("forest_2", ThemeRepository.slugify("Forest 2"))
        assertEquals("", ThemeRepository.slugify("!!!"))
        assertEquals("", ThemeRepository.slugify(""))
        // Unicode letters survive so localised names stay usable
        assertEquals("тёмная_тема", ThemeRepository.slugify("Тёмная тема"))
    }
}
