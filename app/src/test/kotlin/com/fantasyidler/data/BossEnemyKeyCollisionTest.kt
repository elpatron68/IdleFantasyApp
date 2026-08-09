package com.fantasyidler.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bosses and dungeon enemies share the PlayerFlags.enemyKills namespace, so a key present in
 * both enemies.json and raid_bosses.json intermixes their kill counts (issue #1313).
 */
class BossEnemyKeyCollisionTest {

    private fun dataFile(name: String): File =
        listOf("src/main/assets/data/$name", "app/src/main/assets/data/$name")
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")

    private fun topLevelKeys(name: String): Set<String> =
        Json.parseToJsonElement(dataFile(name).readText()).jsonObject.keys

    @Test
    fun `boss ids do not collide with enemy keys`() {
        val enemies = topLevelKeys("enemies.json")
        val bosses  = topLevelKeys("raid_bosses.json")
        val shared  = enemies intersect bosses
        assertTrue("Keys used by both a boss and an enemy: $shared", shared.isEmpty())
    }
}
