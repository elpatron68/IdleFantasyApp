package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThievingLootEntry(
    val item: String,
    val chance: Double,
    @SerialName("min_qty") val minQty: Int = 1,
    @SerialName("max_qty") val maxQty: Int = 1,
)

@Serializable
data class ThievingNpcData(
    val key: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("base_xp") val baseXp: Int,
    @SerialName("coins_min") val coinsMin: Int,
    @SerialName("coins_max") val coinsMax: Int,
    @SerialName("loot_table") val lootTable: List<ThievingLootEntry> = emptyList(),
)
