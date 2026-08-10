package com.fantasyidler.data.json

import com.fantasyidler.data.model.ThemeBase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** All adjustable colour roles on Material 3 [androidx.compose.material3.ColorScheme]. */
@Serializable
enum class ColourSchemeParameter {
    @SerialName("primary") PRIMARY,
    @SerialName("on_primary") ON_PRIMARY,
    @SerialName("primary_container") PRIMARY_CONTAINER,
    @SerialName("on_primary_container") ON_PRIMARY_CONTAINER,
    @SerialName("inverse_primary") INVERSE_PRIMARY,
    @SerialName("secondary") SECONDARY,
    @SerialName("on_secondary") ON_SECONDARY,
    @SerialName("secondary_container") SECONDARY_CONTAINER,
    @SerialName("on_secondary_container") ON_SECONDARY_CONTAINER,
    @SerialName("tertiary") TERTIARY,
    @SerialName("on_tertiary") ON_TERTIARY,
    @SerialName("tertiary_container") TERTIARY_CONTAINER,
    @SerialName("on_tertiary_container") ON_TERTIARY_CONTAINER,
    @SerialName("background") BACKGROUND,
    @SerialName("on_background") ON_BACKGROUND,
    @SerialName("surface") SURFACE,
    @SerialName("on_surface") ON_SURFACE,
    @SerialName("surface_variant") SURFACE_VARIANT,
    @SerialName("on_surface_variant") ON_SURFACE_VARIANT,
    @SerialName("surface_tint") SURFACE_TINT,
    @SerialName("inverse_surface") INVERSE_SURFACE,
    @SerialName("inverse_on_surface") INVERSE_ON_SURFACE,
    @SerialName("error") ERROR,
    @SerialName("on_error") ON_ERROR,
    @SerialName("error_container") ERROR_CONTAINER,
    @SerialName("on_error_container") ON_ERROR_CONTAINER,
    @SerialName("outline") OUTLINE,
    @SerialName("outline_variant") OUTLINE_VARIANT,
    @SerialName("scrim") SCRIM,
    @SerialName("surface_bright") SURFACE_BRIGHT,
    @SerialName("surface_dim") SURFACE_DIM,
    @SerialName("surface_container") SURFACE_CONTAINER,
    @SerialName("surface_container_high") SURFACE_CONTAINER_HIGH,
    @SerialName("surface_container_highest") SURFACE_CONTAINER_HIGHEST,
    @SerialName("surface_container_low") SURFACE_CONTAINER_LOW,
    @SerialName("surface_container_lowest") SURFACE_CONTAINER_LOWEST,
}

@Serializable
data class ThemeData(
    val base: ThemeBase,
    @SerialName("display_name") val displayName: String = "",
    /** Named palette entries; values are ARGB hex strings such as `"0xFFC9A94D"`. */
    val colours: Map<String, String>,
    @SerialName("colour_scheme") val schemes: Map<ColourSchemeParameter, String>,
)
