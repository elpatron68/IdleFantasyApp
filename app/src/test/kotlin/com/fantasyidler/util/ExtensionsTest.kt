package com.fantasyidler.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for the pure formatting/clamping extensions in `Extensions.kt`.
 *
 * `formatXp`/`formatCoins` delegate to locale-default `String.format`, so the
 * default locale is pinned to [Locale.US] for the duration of each test to keep
 * the expected separators ("1,000", "1.5M") deterministic across machines and CI.
 * (The time-relative helpers `toCountdown`/`toRelativeTime` are intentionally
 * not covered here because they read the wall clock.)
 */
class ExtensionsTest {

    private lateinit var originalLocale: Locale

    @Before
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `formatXp switches units at the thousand and million boundaries`() {
        assertEquals("999", 999L.formatXp())
        assertEquals("1,000", 1_000L.formatXp())
        assertEquals("999,999", 999_999L.formatXp())
        assertEquals("1.0M", 1_000_000L.formatXp())
        assertEquals("1.5M", 1_500_000L.formatXp())
        assertEquals("0", 0L.formatXp())
    }

    @Test
    fun `formatCoins uses the same boundaries as formatXp`() {
        assertEquals("500", 500L.formatCoins())
        assertEquals("1,000", 1_000L.formatCoins())
        assertEquals("2.5M", 2_500_000L.formatCoins())
    }

    @Test
    fun `formatDurationMs renders hours minutes and seconds`() {
        assertEquals("45s", 45_000L.formatDurationMs())
        assertEquals("1m", 60_000L.formatDurationMs())
        assertEquals("1m", 90_000L.formatDurationMs())     // sub-minute remainder dropped
        assertEquals("1h", 3_600_000L.formatDurationMs())
        assertEquals("1h 1m", 3_660_000L.formatDurationMs())
        assertEquals("1h 30m", 5_400_000L.formatDurationMs())
        assertEquals("0s", 0L.formatDurationMs())
    }

    @Test
    fun `formatDurationMs renders months weeks and days, omitting zero units`() {
        val hour = 3_600_000L
        assertEquals("1d", 24 * hour)                        // exactly one day
        assertEquals("1d 1h 10m", 25 * hour + 600_000L)
        assertEquals("1w", 7 * 24 * hour)                    // exactly one week
        assertEquals("1w 1d 2h", 8 * 24 * hour + 2 * hour)
        assertEquals("1mo", 30 * 24 * hour)                  // months are 30 days
        assertEquals("2mo", 60 * 24 * hour)
        assertEquals("1mo 1w 1d 8h 54m", 920 * hour + 54 * 60_000L)
        assertEquals("1mo 1m", 30 * 24 * hour + 60_000L)     // interior zero units skipped
    }

    private fun assertEquals(expected: String, ms: Long) =
        assertEquals(expected, ms.formatDurationMs())

    @Test
    fun `clampLevel constrains to the 1-99 skill range`() {
        assertEquals(1, 0.clampLevel())
        assertEquals(1, (-5).clampLevel())
        assertEquals(1, 1.clampLevel())
        assertEquals(50, 50.clampLevel())
        assertEquals(99, 99.clampLevel())
        assertEquals(99, 200.clampLevel())
    }

    @Test
    fun `toSkillAbbrev maps known skills and falls back for unknown ones`() {
        assertEquals("Atk", "attack".toSkillAbbrev())
        assertEquals("Str", "strength".toSkillAbbrev())
        assertEquals("HP", "hitpoints".toSkillAbbrev())
        assertEquals("RC", "runecrafting".toSkillAbbrev())
        // Unknown keys fall back to the capitalised first four characters.
        assertEquals("Herb", "herblore".toSkillAbbrev())
        assertEquals("Slay", "slayer".toSkillAbbrev())
    }
}
