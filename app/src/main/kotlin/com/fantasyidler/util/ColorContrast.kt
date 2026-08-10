package com.fantasyidler.util

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x contrast helpers, shared by the theme editor's live warnings and
 * the official-theme contrast tests.
 */
object ColorContrast {

    /** WCAG AA minimum contrast for normal text. */
    const val AA_NORMAL_TEXT = 4.5

    /** Contrast ratio between two colours, in the range 1.0 (identical) to 21.0. */
    fun contrastRatio(first: Color, second: Color): Double {
        val lighter = max(relativeLuminance(first), relativeLuminance(second))
        val darker = min(relativeLuminance(first), relativeLuminance(second))
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red.toDouble()) +
            0.7152 * linearize(color.green.toDouble()) +
            0.0722 * linearize(color.blue.toDouble())

    /**
     * Parses an ARGB hex string such as `"0xFFC9A94D"` or `"#FFC9A94D"`.
     * Six-digit RGB values (`"#C9A94D"`) get an implicit FF alpha.
     */
    fun parseArgb(raw: String): Long {
        val cleaned = raw.trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .removePrefix("#")
        val argb = cleaned.toULong(16).toLong()
        return if (cleaned.length == 6) 0xFF000000L or argb else argb
    }

    /**
     * Strict variant for user input: accepts only a complete 6- or 8-digit hex
     * colour (with optional `0x`/`#` prefix), so partially typed values never
     * apply as garbage colours. Returns null until the input is complete.
     */
    fun parseHexColorOrNull(raw: String): Long? {
        val cleaned = raw.trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .removePrefix("#")
        if (!cleaned.matches(Regex("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}"))) return null
        return parseArgb(cleaned)
    }

    private fun linearize(component: Double): Double =
        if (component <= 0.04045) {
            component / 12.92
        } else {
            ((component + 0.055) / 1.055).pow(2.4)
        }
}
