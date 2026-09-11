package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.util.GameStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class BestiaryFilter { ALL, MISSING }
enum class BestiarySort { ALPHABETICAL, BY_LOCATION }

data class BestiaryEntry(
    val key: String,
    val nameLoader: (Context, String) -> String,
    val killCount: Int,
    val locations: List<String>,
    val enemy: EnemyData? = null,
    val boss: BossData? = null,
) {
    val encountered: Boolean get() = killCount > 0
}

data class BestiaryUiState(
    val enemies: List<BestiaryEntry> = emptyList(),
    val bosses: List<BestiaryEntry> = emptyList(),
    val filter: BestiaryFilter = BestiaryFilter.ALL,
    val sort: BestiarySort = BestiarySort.ALPHABETICAL,
    val totalEncountered: Int = 0,
    val totalCount: Int = 0,
)

@HiltViewModel
class BestiaryViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    private val _filter = MutableStateFlow(BestiaryFilter.ALL)
    private val _sort = MutableStateFlow(BestiarySort.ALPHABETICAL)

    val uiState: StateFlow<BestiaryUiState> = combine(
        playerRepo.playerFlow,
        _filter,
        _sort,
    ) { player, filter, sort ->
        val flags: PlayerFlags = if (player != null)
            json.decodeFromString(player.flags) else PlayerFlags()
        val kills = flags.enemyKills

        val enemies = gameData.enemies
            .map { (key, enemy) ->
                BestiaryEntry(
                    key         = key,
                    nameLoader  = GameStrings::enemyName,
                    killCount   = kills[key] ?: 0,
                    locations   = gameData.enemyLocations[key] ?: emptyList(),
                    enemy       = enemy,
                )
            }.sortedBy { it.key }

        val bosses = gameData.bosses
            .map { (key, boss) ->
                BestiaryEntry(
                    key         = key,
                    nameLoader  = GameStrings::bossName,
                    killCount   = kills[key] ?: 0,
                    locations   = emptyList(),
                    boss        = boss,
                )
            }.sortedBy { it.key }

        BestiaryUiState(
            enemies = when (filter) {
                BestiaryFilter.ALL -> enemies
                BestiaryFilter.MISSING -> enemies.filter { !it.encountered }
            },
            bosses = when (filter) {
                BestiaryFilter.ALL -> bosses
                BestiaryFilter.MISSING -> bosses.filter { !it.encountered }
            },
            filter = filter,
            sort = sort,
            totalEncountered = enemies.count { it.encountered } + bosses.count { it.encountered },
            totalCount = enemies.size + bosses.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BestiaryUiState())

    fun setFilter(filter: BestiaryFilter) { _filter.value = filter }
    fun setSort(sort: BestiarySort) { _sort.value = sort }
}
