package com.fantasyidler.repository

import android.content.Context
import com.fantasyidler.R
import com.fantasyidler.data.db.dao.QuestProgressDao
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.ui.viewmodel.TitleCatalog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TitleRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val questProgressDao: QuestProgressDao,
    private val guildRepo: GuildRepository,
) {
    companion object {
        private val SKILL_TITLES = mapOf(
            "smithing"     to "master_smith",
            "cooking"      to "head_chef",
            "mining"       to "master_miner",
            "fishing"      to "master_angler",
            "woodcutting"  to "master_woodcutter",
            "fletching"    to "master_fletcher",
            "crafting"     to "master_artisan",
            "runecrafting" to "runemaster",
            "herblore"     to "master_herbalist",
            "construction" to "master_builder",
            "prayer"       to "devout",
            "thieving"     to "master_thief",
            "firemaking"   to "flamekeeper",
            "slayer"       to "slayer",
        )
        private val GUILD_TITLES = mapOf(
            "warriors"   to "warlord",
            "archers"    to "marksman",
            "mages"      to "archmage",
            "mercantile" to "merchant_prince",
            "agility"    to "pathfinder",
            "farming"    to "master_farmer",
        )
    }

    /** Pure: which title ids the player currently qualifies for, given their state. */
    fun computeUnlocked(flags: PlayerFlags, completedQuestIds: Set<String>): Set<String> {
        val unlocked = mutableSetOf<String>()
        for ((skill, titleId) in SKILL_TITLES) {
            val chainIds = gameData.quests.values.filter { it.skill == skill }.map { it.id }
            if (chainIds.isNotEmpty() && chainIds.all { it in completedQuestIds }) unlocked += titleId
        }
        if (gameData.bosses.keys.isNotEmpty() && gameData.bosses.keys.all { (flags.enemyKills[it] ?: 0) > 0 }) {
            unlocked += "godslayer"
        }
        for (banner in flags.seasonalBannersEarned) unlocked += "seasonal_${banner.eventId}"
        for ((guild, titleId) in GUILD_TITLES) {
            val rep = flags.guildReputation[guild] ?: 0L
            if (guildRepo.guildLevel(guild, rep, completedQuestIds) >= GuildRepository.REP_THRESHOLDS.size) {
                unlocked += titleId
            }
        }
        return unlocked
    }

    /** Ratchets [PlayerFlags.unlockedTitles] forward with any newly-qualifying titles; never removes any. */
    suspend fun syncUnlockedTitles() {
        val completedQuestIds = questProgressDao.getAllProgress().filter { it.completed }.map { it.questId }.toSet()
        playerRepo.updateFlagsAtomically { flags ->
            val merged = flags.unlockedTitles + computeUnlocked(flags, completedQuestIds)
            if (merged.size != flags.unlockedTitles.size) flags.copy(unlockedTitles = merged) else flags
        }
    }

    /** Resolves an equipped title id to its localized display name, or null (e.g. "None" equipped). */
    fun displayName(context: Context, id: String?, flags: PlayerFlags): String? {
        if (id == null) return null
        TitleCatalog.ALL.find { it.id == id }?.let { return context.getString(it.nameRes) }
        if (id.startsWith("seasonal_")) {
            val eventId = id.removePrefix("seasonal_")
            val earned = flags.seasonalBannersEarned.find { it.eventId == eventId } ?: return null
            val shortName = earned.eventDisplayName.ifBlank { gameData.seasonalEvents[eventId]?.displayName ?: return null }
            return context.getString(R.string.title_seasonal_champion_of, shortName)
        }
        return null
    }

    suspend fun equipTitle(id: String?) {
        playerRepo.updateFlagsAtomically { flags ->
            flags.copy(equippedTitle = id?.takeIf { it in flags.unlockedTitles })
        }
    }
}
