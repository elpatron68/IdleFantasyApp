package com.fantasyidler.data.json

import com.fantasyidler.data.model.ThemeBase
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The theme editor's export must survive a round trip through the importer's
 * JSON shape (`Map` of theme key to [ThemeData]) without losing anything.
 */
class ThemeDataRoundTripTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `theme export and import round trip preserves all fields`() {
        val theme = ThemeData(
            base = ThemeBase.LIGHT,
            displayName = "My Forest Theme",
            colours = mapOf(
                "leaf_green" to "0xFF4CAF50",
                "bark_brown" to "0xFF8B5E3C",
            ),
            schemes = mapOf(
                ColourSchemeParameter.PRIMARY to "leaf_green",
                ColourSchemeParameter.SECONDARY to "bark_brown",
                ColourSchemeParameter.ON_PRIMARY to "bark_brown",
            ),
        )
        val exported = json.encodeToString(
            json.serializersModule.serializer<Map<String, ThemeData>>(),
            mapOf("my_forest_theme" to theme),
        )
        val imported = json.decodeFromString<Map<String, ThemeData>>(exported)
        assertEquals(mapOf("my_forest_theme" to theme), imported)
    }

    @Test
    fun `scheme parameter serial names use snake case`() {
        val encoded = json.encodeToString(
            json.serializersModule.serializer<ColourSchemeParameter>(),
            ColourSchemeParameter.ON_SURFACE_VARIANT,
        )
        assertEquals("\"on_surface_variant\"", encoded)
    }
}
