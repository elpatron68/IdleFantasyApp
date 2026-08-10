package com.fantasyidler.ui.viewmodel

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.ColourSchemeParameter
import com.fantasyidler.data.json.ThemeData
import com.fantasyidler.data.model.ThemeBase
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.ThemeRepository
import com.fantasyidler.util.ColorContrast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject

/** Editable draft of a theme. Palette values are ARGB hex strings like "0xFFC9A94D". */
data class ThemeEditorState(
    val displayName: String = "",
    val base: ThemeBase = ThemeBase.DARK,
    val colours: Map<String, String> = emptyMap(),
    val schemes: Map<ColourSchemeParameter, String> = emptyMap(),
    val loaded: Boolean = false,
) {
    val slug: String get() = ThemeRepository.slugify(displayName)

    /** Roles driven by one palette entry, in declaration order. */
    fun rolesFor(colourName: String): List<ColourSchemeParameter> =
        ColourSchemeParameter.entries.filter { schemes[it] == colourName }

    /**
     * Roles that can be given their own palette entry: roles the theme does not
     * map yet, plus roles driven by an entry shared with other roles (e.g.
     * `on_primary` reusing the background colour), which can be detached so
     * they become editable on their own.
     */
    val detachableRoles: List<ColourSchemeParameter>
        get() = ColourSchemeParameter.entries.filter { role ->
            val entry = schemes[role] ?: return@filter true
            ColourSchemeParameter.entries.count { schemes[it] == entry } > 1
        }
}

/** A foreground/background role pair whose contrast is below WCAG AA. */
data class ContrastWarning(
    val foreground: ColourSchemeParameter,
    val background: ColourSchemeParameter,
    val ratio: Double,
)

@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    private val themeRepo: ThemeRepository,
    private val playerRepo: PlayerRepository,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(ThemeEditorState())
    val state: StateFlow<ThemeEditorState> = _state

    val officialThemeKeys: Set<String> = themeRepo.getOfficialThemes().toSet()

    /** Custom theme keys, used to hint when saving will replace an existing theme. */
    val customThemeKeys: StateFlow<Set<String>> = themeRepo.observeCustomThemes()
        .map { themes -> themes.map { it.name }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Seeds the draft from [source] once; later calls are no-ops so edits survive
     * recomposition. [blankName] starts the name empty so saving creates a new
     * theme instead of replacing [source].
     *
     * The source palette is flattened to one entry per mapped role, so every
     * role the theme uses (On Primary, On Surface, ...) is a default entry that
     * can be edited independently, even when the source file shares one colour
     * across several roles. Roles the theme leaves unmapped stay on the base
     * scheme's colours and are available under the add-roles chips. Colours
     * resolve identically, so nothing changes visually until edited.
     */
    fun load(source: String, blankName: Boolean = false) {
        if (_state.value.loaded) return
        viewModelScope.launch {
            val data = themeRepo.getThemeData(source) ?: themeRepo.getThemeData("dark") ?: return@launch
            val colours = LinkedHashMap<String, String>()
            val schemes = LinkedHashMap<ColourSchemeParameter, String>()
            for (role in ColourSchemeParameter.entries) {
                val hex = data.schemes[role]?.let { data.colours[it] } ?: continue
                val key = role.name.lowercase()
                colours[key] = hex
                schemes[role] = key
            }
            _state.value = ThemeEditorState(
                displayName = if (blankName) "" else data.displayName,
                base = data.base,
                colours = colours,
                schemes = schemes,
                loaded = true,
            )
        }
    }

    fun setDisplayName(name: String) = _state.update { it.copy(displayName = name) }

    fun setBase(base: ThemeBase) = _state.update { it.copy(base = base) }

    fun setColour(colourName: String, colour: Color) = _state.update {
        it.copy(colours = it.colours + (colourName to colour.toHex()))
    }

    /**
     * Maps [role] to a new palette entry named after it, seeded from the draft's
     * current effective colour for that role. For a role sharing an entry with
     * other roles this detaches it: the shared entry keeps its other roles, and
     * [role] becomes independently editable with no visual change until edited.
     */
    fun addRole(role: ColourSchemeParameter) = _state.update { state ->
        val key = role.name.lowercase()
        val seed = colorFor(buildScheme(state), role)
        state.copy(
            colours = state.colours + (key to seed.toHex()),
            schemes = state.schemes + (role to key),
        )
    }

    /**
     * Removes a palette entry and unmaps every role it drives; those roles fall
     * back to the base Material scheme and reappear under the add-roles chips.
     */
    fun removeColour(colourName: String) = _state.update { state ->
        state.copy(
            colours = state.colours - colourName,
            schemes = state.schemes.filterValues { it != colourName },
        )
    }

    fun buildScheme(state: ThemeEditorState): ColorScheme =
        themeRepo.buildColourScheme(state.toThemeData())

    /** Foreground/background role pairs mapped by the draft that fall below AA contrast. */
    fun contrastWarnings(state: ThemeEditorState): List<ContrastWarning> =
        CONTRAST_PAIRS.mapNotNull { (fg, bg) ->
            val fgColor = state.resolve(fg) ?: return@mapNotNull null
            val bgColor = state.resolve(bg) ?: return@mapNotNull null
            val ratio = ColorContrast.contrastRatio(fgColor, bgColor)
            if (ratio < ColorContrast.AA_NORMAL_TEXT) ContrastWarning(fg, bg, ratio) else null
        }

    /** Saves the draft as a custom theme, applies it, and reports success. */
    fun save(onDone: (success: Boolean) -> Unit) {
        val state = _state.value
        viewModelScope.launch {
            try {
                themeRepo.saveCustomTheme(state.slug, state.toThemeData())
                val flags = playerRepo.getFlags()
                playerRepo.updateFlags(flags.copy(themePreference = state.slug))
                onDone(true)
            } catch (_: Exception) {
                onDone(false)
            }
        }
    }

    /** The draft serialized in the shape `importTheme` reads, keyed by its slug. */
    fun exportJson(): String {
        val state = _state.value
        return json.encodeToString(
            json.serializersModule.serializer<Map<String, ThemeData>>(),
            mapOf(state.slug to state.toThemeData()),
        )
    }

    private fun ThemeEditorState.toThemeData() = ThemeData(
        base = base,
        displayName = displayName.trim(),
        colours = colours,
        schemes = schemes,
    )

    private fun ThemeEditorState.resolve(role: ColourSchemeParameter): Color? {
        val raw = colours[schemes[role] ?: return null] ?: return null
        return try { Color(ColorContrast.parseArgb(raw)) } catch (_: Exception) { null }
    }

    companion object {
        private val CONTRAST_PAIRS = listOf(
            ColourSchemeParameter.ON_BACKGROUND to ColourSchemeParameter.BACKGROUND,
            ColourSchemeParameter.ON_SURFACE to ColourSchemeParameter.SURFACE,
            ColourSchemeParameter.ON_SURFACE_VARIANT to ColourSchemeParameter.SURFACE_VARIANT,
            ColourSchemeParameter.ON_PRIMARY to ColourSchemeParameter.PRIMARY,
            ColourSchemeParameter.ON_PRIMARY_CONTAINER to ColourSchemeParameter.PRIMARY_CONTAINER,
            ColourSchemeParameter.ON_SECONDARY to ColourSchemeParameter.SECONDARY,
            ColourSchemeParameter.ON_SECONDARY_CONTAINER to ColourSchemeParameter.SECONDARY_CONTAINER,
            ColourSchemeParameter.ON_TERTIARY to ColourSchemeParameter.TERTIARY,
            ColourSchemeParameter.ON_TERTIARY_CONTAINER to ColourSchemeParameter.TERTIARY_CONTAINER,
            ColourSchemeParameter.ON_ERROR to ColourSchemeParameter.ERROR,
            ColourSchemeParameter.ON_ERROR_CONTAINER to ColourSchemeParameter.ERROR_CONTAINER,
        )

        fun Color.toHex(): String = "0x%08X".format(toArgb())

        /** The [ColorScheme] property that [role] drives. */
        fun colorFor(scheme: ColorScheme, role: ColourSchemeParameter): Color = when (role) {
            ColourSchemeParameter.PRIMARY -> scheme.primary
            ColourSchemeParameter.ON_PRIMARY -> scheme.onPrimary
            ColourSchemeParameter.PRIMARY_CONTAINER -> scheme.primaryContainer
            ColourSchemeParameter.ON_PRIMARY_CONTAINER -> scheme.onPrimaryContainer
            ColourSchemeParameter.INVERSE_PRIMARY -> scheme.inversePrimary
            ColourSchemeParameter.SECONDARY -> scheme.secondary
            ColourSchemeParameter.ON_SECONDARY -> scheme.onSecondary
            ColourSchemeParameter.SECONDARY_CONTAINER -> scheme.secondaryContainer
            ColourSchemeParameter.ON_SECONDARY_CONTAINER -> scheme.onSecondaryContainer
            ColourSchemeParameter.TERTIARY -> scheme.tertiary
            ColourSchemeParameter.ON_TERTIARY -> scheme.onTertiary
            ColourSchemeParameter.TERTIARY_CONTAINER -> scheme.tertiaryContainer
            ColourSchemeParameter.ON_TERTIARY_CONTAINER -> scheme.onTertiaryContainer
            ColourSchemeParameter.BACKGROUND -> scheme.background
            ColourSchemeParameter.ON_BACKGROUND -> scheme.onBackground
            ColourSchemeParameter.SURFACE -> scheme.surface
            ColourSchemeParameter.ON_SURFACE -> scheme.onSurface
            ColourSchemeParameter.SURFACE_VARIANT -> scheme.surfaceVariant
            ColourSchemeParameter.ON_SURFACE_VARIANT -> scheme.onSurfaceVariant
            ColourSchemeParameter.SURFACE_TINT -> scheme.surfaceTint
            ColourSchemeParameter.INVERSE_SURFACE -> scheme.inverseSurface
            ColourSchemeParameter.INVERSE_ON_SURFACE -> scheme.inverseOnSurface
            ColourSchemeParameter.ERROR -> scheme.error
            ColourSchemeParameter.ON_ERROR -> scheme.onError
            ColourSchemeParameter.ERROR_CONTAINER -> scheme.errorContainer
            ColourSchemeParameter.ON_ERROR_CONTAINER -> scheme.onErrorContainer
            ColourSchemeParameter.OUTLINE -> scheme.outline
            ColourSchemeParameter.OUTLINE_VARIANT -> scheme.outlineVariant
            ColourSchemeParameter.SCRIM -> scheme.scrim
            ColourSchemeParameter.SURFACE_BRIGHT -> scheme.surfaceBright
            ColourSchemeParameter.SURFACE_DIM -> scheme.surfaceDim
            ColourSchemeParameter.SURFACE_CONTAINER -> scheme.surfaceContainer
            ColourSchemeParameter.SURFACE_CONTAINER_HIGH -> scheme.surfaceContainerHigh
            ColourSchemeParameter.SURFACE_CONTAINER_HIGHEST -> scheme.surfaceContainerHighest
            ColourSchemeParameter.SURFACE_CONTAINER_LOW -> scheme.surfaceContainerLow
            ColourSchemeParameter.SURFACE_CONTAINER_LOWEST -> scheme.surfaceContainerLowest
        }
    }
}
