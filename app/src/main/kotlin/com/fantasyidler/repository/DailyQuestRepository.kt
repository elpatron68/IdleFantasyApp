package com.fantasyidler.repository

import com.fantasyidler.data.json.DailyQuestTemplate
import com.fantasyidler.data.model.PlayerFlags
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed class DailyReward {
    data class CoinsReward(val amount: Int = 2000) : DailyReward()
    data class DwarvenItemReward(val itemKey: String) : DailyReward()
}

data class DailyQuestWithProgress(
    val template: DailyQuestTemplate,
    val progress: Int,
    val claimed: Boolean,
)

@Singleton
class DailyQuestRepository @Inject constructor(
    private val gameData: GameDataRepository,
) {

    private val dwarvenDropPool = listOf(
        "dwarven_sword", "dwarven_scimitar", "dwarven_warhammer",
        "dwarven_pickaxe", "dwarven_axe", "dwarven_fishing_rod", "dwarven_hoe",
        "dwarven_helm", "dwarven_platebody", "dwarven_platelegs",
        "dwarven_shield", "dwarven_boots",
    )

    /** Returns epoch ms of the next 6am in local time after [fromMs]. */
    fun nextResetMs(fromMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMs }
        cal.set(Calendar.HOUR_OF_DAY, 6)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= fromMs) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun shouldRefresh(generatedAt: Long): Boolean {
        if (generatedAt == 0L) return true
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = generatedAt }
        cal.set(Calendar.HOUR_OF_DAY, 6)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= generatedAt) cal.add(Calendar.DAY_OF_YEAR, 1)
        return now >= cal.timeInMillis
    }

    /** Pick 3 distinct quest IDs from the pool using a date-seeded RNG (same quests all day). */
    fun selectThreeQuests(): List<String> {
        val today = Calendar.getInstance().let {
            it.get(Calendar.YEAR) * 10000 + it.get(Calendar.MONTH) * 100 + it.get(Calendar.DAY_OF_MONTH)
        }
        val rng = Random(today.toLong())
        val pool = gameData.dailyQuestPool.map { it.id }.shuffled(rng)
        return pool.take(3)
    }

    fun refreshFlags(flags: PlayerFlags): PlayerFlags {
        val ids = selectThreeQuests()
        return flags.copy(
            dailyQuestIds = ids,
            dailyQuestProgress = emptyMap(),
            dailyQuestClaimed = emptyList(),
            dailyQuestGeneratedAt = System.currentTimeMillis(),
        )
    }

    fun getActiveDailyQuests(flags: PlayerFlags): List<DailyQuestWithProgress> {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        return flags.dailyQuestIds.mapNotNull { id ->
            val template = pool[id] ?: return@mapNotNull null
            DailyQuestWithProgress(
                template = template,
                progress = flags.dailyQuestProgress[id] ?: 0,
                claimed = id in flags.dailyQuestClaimed,
            )
        }
    }

    fun recordProgress(
        flags: PlayerFlags,
        type: String,
        target: String,
        amount: Int,
    ): PlayerFlags {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        val activeUnclaimed = flags.dailyQuestIds.filter { it !in flags.dailyQuestClaimed }
        if (activeUnclaimed.isEmpty()) return flags

        var updated = flags.dailyQuestProgress.toMutableMap()
        var changed = false

        for (id in activeUnclaimed) {
            val quest = pool[id] ?: continue
            if (quest.type != type) continue
            if (quest.type == "kill_enemy" && quest.target != target) continue
            if (quest.type in listOf("gather", "craft") && quest.target != target) continue
            val current = updated[id] ?: 0
            val max = quest.amount
            if (current >= max) continue
            updated[id] = minOf(current + amount, max)
            changed = true
        }

        return if (changed) flags.copy(dailyQuestProgress = updated) else flags
    }

    fun recordPrayerProgress(flags: PlayerFlags, amount: Int): PlayerFlags {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        val activeUnclaimed = flags.dailyQuestIds.filter { it !in flags.dailyQuestClaimed }
        if (activeUnclaimed.isEmpty()) return flags

        var updated = flags.dailyQuestProgress.toMutableMap()
        var changed = false

        for (id in activeUnclaimed) {
            val quest = pool[id] ?: continue
            if (quest.type != "prayer") continue
            val current = updated[id] ?: 0
            val max = quest.amount
            if (current >= max) continue
            updated[id] = minOf(current + amount, max)
            changed = true
        }

        return if (changed) flags.copy(dailyQuestProgress = updated) else flags
    }

    fun claimQuest(flags: PlayerFlags, templateId: String): Pair<PlayerFlags, DailyReward> {
        val pool = gameData.dailyQuestPool.associateBy { it.id }
        val template = pool[templateId] ?: return flags to DailyReward.CoinsReward()
        val progress = flags.dailyQuestProgress[templateId] ?: 0
        check(progress >= template.amount) { "Quest not complete yet" }
        check(templateId !in flags.dailyQuestClaimed) { "Quest already claimed" }

        val reward: DailyReward = if (Random.nextInt(500) == 0) {
            DailyReward.DwarvenItemReward(dwarvenDropPool.random())
        } else {
            DailyReward.CoinsReward()
        }

        val newFlags = flags.copy(
            dailyQuestClaimed = flags.dailyQuestClaimed + templateId,
        )
        return newFlags to reward
    }
}
