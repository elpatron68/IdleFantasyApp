package com.fantasyidler.ui.theme

import androidx.compose.ui.graphics.Color
import com.fantasyidler.data.json.ColourSchemeParameter
import com.fantasyidler.data.json.ColourSchemeParameter.BACKGROUND
import com.fantasyidler.data.json.ColourSchemeParameter.ON_BACKGROUND
import com.fantasyidler.data.json.ColourSchemeParameter.ON_SURFACE
import com.fantasyidler.data.json.ColourSchemeParameter.ON_SURFACE_VARIANT
import com.fantasyidler.data.json.ColourSchemeParameter.SURFACE
import com.fantasyidler.data.json.ColourSchemeParameter.SURFACE_VARIANT
import com.fantasyidler.data.json.ThemeData
import com.fantasyidler.util.ColorContrast
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrast checks against the official theme definitions in
 * `assets/data/official_themes.json`, so palette regressions are caught in the
 * data that actually drives [com.fantasyidler.repository.ThemeRepository].
 */
class ThemeContrastTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val themes: Map<String, ThemeData> by lazy {
        json.decodeFromString(dataFile("official_themes.json").readText())
    }

    private val midnight: ThemeData get() = themes.getValue("midnight")
    private val dark: ThemeData get() = themes.getValue("dark")

    @Test
    fun `midnight background and surface support enhanced contrast text`() {
        assertContrastAtLeast(midnight.color(ON_BACKGROUND), midnight.color(BACKGROUND), 7.0)
        assertContrastAtLeast(midnight.color(ON_SURFACE), midnight.color(SURFACE), 7.0)
    }

    @Test
    fun `midnight surface variant supports normal contrast muted text`() {
        assertContrastAtLeast(
            midnight.color(ON_SURFACE_VARIANT),
            midnight.color(SURFACE_VARIANT),
            4.5,
        )
    }

    @Test
    fun `midnight containers remain visually distinct`() {
        assertEquals(
            3,
            setOf(
                midnight.color(BACKGROUND),
                midnight.color(SURFACE),
                midnight.color(SURFACE_VARIANT),
            ).size,
        )
    }

    @Test
    fun `midnight palette remains separate from dark palette`() {
        assertNotEquals(dark.color(BACKGROUND), midnight.color(BACKGROUND))
        assertNotEquals(dark.color(SURFACE), midnight.color(SURFACE))
        assertNotEquals(dark.color(SURFACE_VARIANT), midnight.color(SURFACE_VARIANT))
        assertNotEquals(dark.color(ON_SURFACE_VARIANT), midnight.color(ON_SURFACE_VARIANT))
    }

    @Test
    fun `every official theme resolves core text roles to at least AA contrast`() {
        for ((name, theme) in themes) {
            assertContrastAtLeast(
                theme.color(ON_BACKGROUND),
                theme.color(BACKGROUND),
                4.5,
                label = "$name on_background/background",
            )
            assertContrastAtLeast(
                theme.color(ON_SURFACE),
                theme.color(SURFACE),
                4.5,
                label = "$name on_surface/surface",
            )
            assertContrastAtLeast(
                theme.color(ON_SURFACE_VARIANT),
                theme.color(SURFACE_VARIANT),
                4.5,
                label = "$name on_surface_variant/surface_variant",
            )
        }
    }

    private fun ThemeData.color(param: ColourSchemeParameter): Color {
        val colourName = schemes[param]
            ?: error("Theme is missing colour_scheme role '${param.name.lowercase()}'")
        val hex = colours[colourName]
            ?: error("Theme is missing colour '$colourName' for role '${param.name.lowercase()}'")
        return Color(ColorContrast.parseArgb(hex))
    }

    private fun dataFile(name: String): File =
        listOf("src/main/assets/data/$name", "app/src/main/assets/data/$name")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")

    private fun assertContrastAtLeast(
        foreground: Color,
        background: Color,
        minimum: Double,
        label: String = "",
    ) {
        val ratio = ColorContrast.contrastRatio(foreground, background)
        val detail = if (label.isEmpty()) "" else " ($label)"
        assertTrue(
            "Expected contrast of at least $minimum$detail, but was $ratio",
            ratio >= minimum,
        )
    }
}
