package com.fantasyidler.simulator

import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EnemySpawn

/**
 * Single source of truth for Infinite Tower enemy tiers and floor scaling, shared by the
 * session simulation (TowerViewModel, QueuedSessionStarter) and the live combat banner.
 * The banner must use the same scaled HP the simulator fought against, or its mid-minute
 * kill estimate counts kills ~9x too fast on high floors and visibly sawtooths (issue #1494).
 */
object TowerScaling {

    val FLOOR_TIERS: List<Pair<IntRange, List<EnemySpawn>>> = listOf(
        (1..20)              to listOf(EnemySpawn("goblin", 40), EnemySpawn("skeleton", 30), EnemySpawn("zombie", 30)),
        (21..40)             to listOf(EnemySpawn("orc_warrior", 40), EnemySpawn("dark_wizard", 30), EnemySpawn("bandit", 30)),
        (41..60)             to listOf(EnemySpawn("cave_troll", 35), EnemySpawn("shadow_beast", 35), EnemySpawn("demon", 30)),
        (61..80)             to listOf(EnemySpawn("forge_demon", 35), EnemySpawn("shadow_assassin", 35), EnemySpawn("abyssal_leech", 30)),
        (81..100)            to listOf(EnemySpawn("void_stalker", 35), EnemySpawn("void_guardian", 35), EnemySpawn("abyssal_lord", 30)),
        (101..Int.MAX_VALUE) to listOf(EnemySpawn("void_archon", 35), EnemySpawn("eternal_sentinel", 35), EnemySpawn("abyssal_lord", 30)),
    )

    fun tierFor(floor: Int): List<EnemySpawn> =
        FLOOR_TIERS.firstOrNull { (range, _) -> floor in range }?.second
            ?: FLOOR_TIERS.last().second

    /** 0 at floor <= 100 (no scaling applied yet), ramping to 1 at floor 250. */
    private fun scalingProgress(floor: Int): Float =
        if (floor <= 100) 0f else (floor.coerceIn(101, 250) - 100) / 150f

    fun hpScalingMult(floor: Int): Float = 1f + scalingProgress(floor) * 9f

    private fun statScalingMult(floor: Int): Float = 1f + scalingProgress(floor) * 0.3f

    /**
     * Floors 1-100 use the fixed tier stats from FLOOR_TIERS as-is. Beyond floor 100, the
     * (fixed) 101+ tier's enemies keep scaling smoothly up to floor 250: hp grows toward
     * ~10x (landing near void_sovereign, the game's current hardest raid boss) while
     * attack/defense only grow up to +30%, so higher floors take longer to clear rather
     * than becoming more lethal.
     */
    fun scaledEnemies(floor: Int, enemies: Map<String, EnemyData>): Map<String, EnemyData> {
        if (floor <= 100) return enemies
        val hpMult = hpScalingMult(floor)
        val statMult = statScalingMult(floor)
        val relevantKeys = tierFor(floor).map { it.enemy }.toSet()
        return enemies.mapValues { (key, enemy) ->
            if (key !in relevantKeys) return@mapValues enemy
            enemy.copy(
                hp = (enemy.hp * hpMult).toInt().coerceAtLeast(1),
                combatStats = enemy.combatStats.copy(
                    attackBonus   = (enemy.combatStats.attackBonus   * statMult).toInt(),
                    strengthBonus = (enemy.combatStats.strengthBonus * statMult).toInt(),
                ),
                defensiveStats = enemy.defensiveStats.copy(
                    attackDefense   = (enemy.defensiveStats.attackDefense   * statMult).toInt(),
                    strengthDefense = (enemy.defensiveStats.strengthDefense * statMult).toInt(),
                    rangedDefense   = (enemy.defensiveStats.rangedDefense   * statMult).toInt(),
                    magicDefense    = (enemy.defensiveStats.magicDefense    * statMult).toInt(),
                ),
            )
        }
    }
}
