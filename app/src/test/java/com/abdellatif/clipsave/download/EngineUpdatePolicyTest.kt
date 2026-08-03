package com.abdellatif.clipsave.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUpdatePolicyTest {

    @Test
    fun refreshesWhenNoSuccessfulUpdateExists() {
        assertTrue(EngineUpdatePolicy.shouldRefresh(lastSuccessfulUpdate = 0, now = 1_000))
    }

    @Test
    fun skipsRefreshWithinOneDay() {
        val lastUpdate = 10_000L

        assertFalse(
            EngineUpdatePolicy.shouldRefresh(
                lastSuccessfulUpdate = lastUpdate,
                now = lastUpdate + EngineUpdatePolicy.AUTO_UPDATE_INTERVAL_MS - 1
            )
        )
    }

    @Test
    fun refreshesAtOneDayAndAfterClockRollback() {
        val lastUpdate = 100_000L

        assertTrue(
            EngineUpdatePolicy.shouldRefresh(
                lastSuccessfulUpdate = lastUpdate,
                now = lastUpdate + EngineUpdatePolicy.AUTO_UPDATE_INTERVAL_MS
            )
        )
        assertTrue(
            EngineUpdatePolicy.shouldRefresh(
                lastSuccessfulUpdate = lastUpdate,
                now = lastUpdate - 1
            )
        )
    }
}
