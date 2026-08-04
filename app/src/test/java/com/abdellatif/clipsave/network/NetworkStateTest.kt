package com.abdellatif.clipsave.network

import com.abdellatif.clipsave.data.preferences.NetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStateTest {

    @Test
    fun anyPolicyStillRequiresValidatedConnectivity() {
        assertFalse(NetworkState(connected = false, unmetered = true).isEligible(NetworkPolicy.ANY))
        assertTrue(NetworkState(connected = true, unmetered = false).isEligible(NetworkPolicy.ANY))
    }

    @Test
    fun unmeteredPolicyRejectsMeteredConnections() {
        assertFalse(
            NetworkState(connected = true, unmetered = false)
                .isEligible(NetworkPolicy.UNMETERED_ONLY)
        )
        assertTrue(
            NetworkState(connected = true, unmetered = true)
                .isEligible(NetworkPolicy.UNMETERED_ONLY)
        )
    }
}
