package com.fantasyidler.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the Dwarven gear pity math in [DailyQuestRepository]'s companion. Every claimed
 * daily without a drop narrows the 1-in-N odds by 2, from 1/100 down to a guaranteed 1/1.
 */
class DailyQuestPityTest {

    @Test
    fun baseOddsWithNoPity() {
        assertEquals(100, DailyQuestRepository.dwarvenDropDenominator(0))
    }

    @Test
    fun eachDryClaimNarrowsByTwo() {
        assertEquals(98, DailyQuestRepository.dwarvenDropDenominator(1))
        assertEquals(96, DailyQuestRepository.dwarvenDropDenominator(2))
        assertEquals(50, DailyQuestRepository.dwarvenDropDenominator(25))
        assertEquals(2, DailyQuestRepository.dwarvenDropDenominator(49))
    }

    @Test
    fun floorsAtGuaranteedDrop() {
        assertEquals(1, DailyQuestRepository.dwarvenDropDenominator(50))
        assertEquals(1, DailyQuestRepository.dwarvenDropDenominator(51))
        assertEquals(1, DailyQuestRepository.dwarvenDropDenominator(1000))
    }

    @Test
    fun negativePityTreatedAsBase() {
        assertEquals(100, DailyQuestRepository.dwarvenDropDenominator(-3))
    }
}
