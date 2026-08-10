package com.fantasyidler.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeBase {
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}

/**
 * Theme entity for management of application themes.
 *
 * Complex fields are stored as JSON strings. Use
 * [com.fantasyidler.repository.ThemeRepository] to read and write typed domain objects
 * rather than touching these raw columns directly.
 */
@Entity(tableName = "themes")
data class CustomTheme(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    @ColumnInfo(name = "base")
    val base: ThemeBase = ThemeBase.DARK,

    /** JSON: Map<String, String> — colour name → ARGB hex (e.g. `"0xFFC9A94D"`). */
    @ColumnInfo(name = "colours")
    val colours: String = "{}",

    /** JSON: Map<String, String> — [com.fantasyidler.data.json.ColourSchemeParameter] → colour name. */
    @ColumnInfo(name = "scheme")
    val scheme: String = "{}",
)
