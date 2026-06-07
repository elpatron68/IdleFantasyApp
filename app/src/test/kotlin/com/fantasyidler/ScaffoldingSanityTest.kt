package com.fantasyidler

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test confirming the JVM unit-test source set is wired up and runs.
 *
 * This is intentionally trivial — it exists to validate the test toolchain
 * (JUnit + kotlin-test on the `test` source set) introduced by the scaffolding
 * PR. Real coverage of game logic is added in subsequent PRs.
 */
class ScaffoldingSanityTest {

    @Test
    fun `test toolchain runs`() {
        assertEquals(4, 2 + 2)
    }
}
