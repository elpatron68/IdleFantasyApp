package com.fantasyidler.ui.viewmodel

import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.EquipSlot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for [ShopViewModel.computeOldEquipmentToSell].
 *
 * Issue #821: Skill capes should NOT be included in "Sell Old Gear" because
 * they are rare rewards awarded for reaching level 99 in a skill — not
 * ordinary gear that gets replaced by a better upgrade.
 */
class SellOldEquipmentTest {

    private fun cape(
        key: String,
        capeSkill: String?,
        defense: Int = 5,
        capeBonus: Float = 0.1f,
    ) = EquipmentData(
        name = key,
        displayName = key.replace("_", " ").replaceFirstChar { it.uppercase() },
        slot = EquipSlot.CAPE,
        defenseBonus = defense,
        capeSkill = capeSkill,
        capeBonus = capeBonus,
    )

    private fun body(
        key: String,
        attack: Int = 0,
        strength: Int = 0,
        defense: Int = 0,
    ) = EquipmentData(
        name = key,
        displayName = key.replace("_", " ").replaceFirstChar { it.uppercase() },
        slot = EquipSlot.BODY,
        attackBonus = attack,
        strengthBonus = strength,
        defenseBonus = defense,
    )

    /** A skill cape (e.g. Prayer cape) unequipped in inventory while a combat cape is equipped. */
    @Test
    fun `skill cape is not included in sell old gear when a better cape is equipped`() {
        val prayerCape = cape("prayer_cape", capeSkill = "prayer")
        val attackCape = cape("attack_cape", capeSkill = "attack", defense = 10)

        val allEquip = mapOf(
            "prayer_cape" to prayerCape,
            "attack_cape" to attackCape,
        )
        val equipped = mapOf(EquipSlot.CAPE to "attack_cape")
        val inventory = mapOf("prayer_cape" to 1)

        val toSell = ShopViewModel.computeOldEquipmentToSell(equipped, inventory, allEquip)

        assertFalse(
            "Skill cape (prayer_cape) should NOT be in sell list",
            "prayer_cape" in toSell,
        )
    }

    /** Multiple skill capes in inventory — none should be sold. */
    @Test
    fun `multiple skill capes are all excluded from sell old gear`() {
        val allEquip = mapOf(
            "prayer_cape"   to cape("prayer_cape", capeSkill = "prayer"),
            "mining_cape"   to cape("mining_cape", capeSkill = "mining"),
            "attack_cape"   to cape("attack_cape", capeSkill = "attack", defense = 10),
        )
        val equipped = mapOf(EquipSlot.CAPE to "attack_cape")
        val inventory = mapOf("prayer_cape" to 1, "mining_cape" to 1)

        val toSell = ShopViewModel.computeOldEquipmentToSell(equipped, inventory, allEquip)

        assertFalse("prayer_cape should NOT be sold", "prayer_cape" in toSell)
        assertFalse("mining_cape should NOT be sold", "mining_cape" in toSell)
    }

    /** A skill cape in inventory with no cape equipped should still not be sold. */
    @Test
    fun `skill cape is not sold even when no cape is equipped`() {
        val prayerCape = cape("prayer_cape", capeSkill = "prayer")

        val allEquip = mapOf(
            "prayer_cape" to prayerCape,
            "bronze_platebody" to body("bronze_platebody", defense = 3),
            "runite_platebody" to body("runite_platebody", defense = 40),
        )
        // No cape equipped, runite platebody equipped, bronze platebody in inventory
        val equipped = mapOf(EquipSlot.BODY to "runite_platebody")
        val inventory = mapOf("prayer_cape" to 1, "bronze_platebody" to 1)

        val toSell = ShopViewModel.computeOldEquipmentToSell(equipped, inventory, allEquip)

        assertFalse("prayer_cape should NOT be sold", "prayer_cape" in toSell)
        assertTrue(
            "Old non-cape gear (bronze_platebody) should still be sold when dominated",
            "bronze_platebody" in toSell,
        )
    }

    /** Combat capes (capeSkill set to a combat skill) should also be excluded. */
    @Test
    fun `combat cape is not sold even when dominated by another combat cape`() {
        val allEquip = mapOf(
            "attack_cape"   to cape("attack_cape", capeSkill = "attack", defense = 10),
            "strength_cape" to cape("strength_cape", capeSkill = "strength", defense = 10),
        )
        val equipped = mapOf(EquipSlot.CAPE to "attack_cape")
        val inventory = mapOf("strength_cape" to 1)

        val toSell = ShopViewModel.computeOldEquipmentToSell(equipped, inventory, allEquip)

        assertFalse("strength_cape should NOT be sold", "strength_cape" in toSell)
    }

    /** Non-cape equipment in the cape slot (if any) should still be sellable. */
    @Test
    fun `non-skill item in cape slot is still sold when dominated`() {
        val allEquip = mapOf(
            "regular_cape" to EquipmentData(
                name = "regular_cape",
                displayName = "Regular Cape",
                slot = EquipSlot.CAPE,
                defenseBonus = 1,
            ),
            "spotted_cape" to EquipmentData(
                name = "spotted_cape",
                displayName = "Spotted Cape",
                slot = EquipSlot.CAPE,
                defenseBonus = 10,
            ),
        )
        val equipped = mapOf(EquipSlot.CAPE to "spotted_cape")
        val inventory = mapOf("regular_cape" to 1)

        val toSell = ShopViewModel.computeOldEquipmentToSell(equipped, inventory, allEquip)

        assertTrue(
            "Non-skill cape (regular_cape) should be sold when dominated",
            "regular_cape" in toSell,
        )
    }

    /** Old body armor should still be sold (regression: fix doesn't break normal behaviour). */
    @Test
    fun `old body armor is still sold when dominated`() {
        val allEquip = mapOf(
            "bronze_platebody" to body("bronze_platebody", defense = 3),
            "runite_platebody" to body("runite_platebody", defense = 40),
        )
        val equipped = mapOf(EquipSlot.BODY to "runite_platebody")
        val inventory = mapOf("bronze_platebody" to 1)

        val toSell = ShopViewModel.computeOldEquipmentToSell(equipped, inventory, allEquip)

        assertTrue("bronze_platebody should be sold", "bronze_platebody" in toSell)
    }
}