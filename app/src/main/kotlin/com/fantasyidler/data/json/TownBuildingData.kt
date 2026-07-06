package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TownBuildingData(
    val key: String,
    val tiers: List<TownBuildingTierData>,
)

@Serializable
data class TownBuildingTierData(
    @SerialName("construction_level_required") val constructionLevelRequired: Int,
    @SerialName("coin_cost") val coinCost: Long,
    val materials: Map<String, Int>,
    val bonuses: Map<String, Double>,
)
