package com.fantasyidler.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure Builder's Workshop discount helpers in [TownRepository]'s companion.
 * These back both the upgrade transaction and the Builder card display, so the two must agree.
 */
class TownRepositoryDiscountTest {

    @Test
    fun noDiscountAtLevelZero() {
        assertEquals(0f, TownRepository.builderDiscount(0), 0.0001f)
        assertEquals(100_000L, TownRepository.discountedCoins(100_000L, 0))
        assertEquals(500, TownRepository.discountedQty(500, 0))
    }

    @Test
    fun negativeLevelTreatedAsZero() {
        assertEquals(0f, TownRepository.builderDiscount(-5), 0.0001f)
    }

    @Test
    fun halfPercentPerLevel() {
        assertEquals(0.05f, TownRepository.builderDiscount(10), 0.0001f)
        assertEquals(0.25f, TownRepository.builderDiscount(50), 0.0001f)
    }

    @Test
    fun maxDiscountAtNinetyNine() {
        assertEquals(0.495f, TownRepository.builderDiscount(99), 0.0001f)
        // Anything above 99 stays capped
        assertEquals(0.495f, TownRepository.builderDiscount(120), 0.0001f)
    }

    @Test
    fun coinsRoundDown() {
        // Level 50 = 25% off: 75,000 exactly
        assertEquals(75_000L, TownRepository.discountedCoins(100_000L, 50))
        // Level 99 = 49.5% off 1,000,000 = 505,000
        assertEquals(505_000L, TownRepository.discountedCoins(1_000_000L, 99))
    }

    @Test
    fun materialsRoundUpAndNeverBelowOne() {
        // Level 50 = 25% off: 10 → 7.5 → ceil 8
        assertEquals(8, TownRepository.discountedQty(10, 50))
        // A single required item never discounts to zero
        assertEquals(1, TownRepository.discountedQty(1, 99))
        // Level 99 = 49.5% off 200 = 101
        assertEquals(101, TownRepository.discountedQty(200, 99))
    }

    @Test
    fun discountedMaterialsMapsEveryEntry() {
        val materials = mapOf("magic_plank" to 800, "stone_block" to 1000, "mithril_nail" to 3000)
        val discounted = TownRepository.discountedMaterials(materials, 99)
        assertEquals(mapOf("magic_plank" to 404, "stone_block" to 505, "mithril_nail" to 1515), discounted)
    }
}
