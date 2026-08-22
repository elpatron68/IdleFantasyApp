package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Rect on the house atlas (assets/sprites/house_atlas.png), in pixels. */
@Serializable
data class HouseSpriteRect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/** One buildable house furnishing (assets/data/house_tiles.json "items"). */
@Serializable
data class HouseTileDef(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    @SerialName("footprint_w") val footprintW: Int = 1,
    @SerialName("footprint_h") val footprintH: Int = 1,
    /** Wall-mounted decor renders on the north wall face instead of the floor. */
    @SerialName("wall_mounted") val wallMounted: Boolean = false,
    /**
     * Sprite anchor within the footprint: "center" (default), "left_edge" (straddles the
     * left grid line, for vertical dividers), or "top_edge" (straddles the top grid line).
     */
    @SerialName("anchor") val anchor: String = "center",
    /** Key of the rotated variant of this piece, when one exists. */
    @SerialName("rotates_to") val rotatesTo: String? = null,
    /** Hidden pieces don't appear in the palette (reached by rotating their partner). */
    @SerialName("hidden") val hidden: Boolean = false,
    /** Key whose display name this piece borrows (rotation variants share a name). */
    @SerialName("name_key") val nameKey: String? = null,
    /** Table decorations can only be placed on cells covered by a placed table. */
    @SerialName("table_only") val tableOnly: Boolean = false,
    val category: String = "furniture",
    @SerialName("level_required") val levelRequired: Int = 1,
    @SerialName("coin_cost") val coinCost: Long = 0L,
    val materials: Map<String, Int> = emptyMap(),
    val xp: Long = 0L,
)

/** Cost of buying the Nth extra room, or of one expansion cell. */
@Serializable
data class HouseCostTier(
    val level: Int = 1,
    val coins: Long = 0L,
    val materials: Map<String, Int> = emptyMap(),
    val xp: Long = 0L,
)

@Serializable
data class HouseTilesData(
    val structural: Map<String, HouseSpriteRect> = emptyMap(),
    /** Outdoor ground fill textures, selectable by the player. */
    val grounds: Map<String, HouseSpriteRect> = emptyMap(),
    val items: Map<String, HouseTileDef> = emptyMap(),
    /** rooms[0] = cost of the second room (the starter room is free). */
    val rooms: List<HouseCostTier> = emptyList(),
    /** Per-new-cell expansion cost, indexed by room order (clamped to last). */
    val expansion: List<HouseCostTier> = emptyList(),
)
