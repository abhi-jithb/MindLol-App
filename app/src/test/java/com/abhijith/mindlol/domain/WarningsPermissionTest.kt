package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningsPermissionTest {

    @Test
    fun android13_notGranted_asks() {
        assertEquals(WarningsAccess.ASK, WarningsPermission.access(33, permissionGranted = false, notificationsEnabled = false, channelEnabled = true))
        assertEquals(WarningsAccess.ASK, WarningsPermission.access(36, permissionGranted = false, notificationsEnabled = true, channelEnabled = true))
    }

    @Test
    fun olderAndroid_hasNoRuntimePermission_onlyTheSettingsSwitch() {
        assertEquals(WarningsAccess.ALLOWED, WarningsPermission.access(32, permissionGranted = false, notificationsEnabled = true, channelEnabled = true))
        assertEquals(WarningsAccess.OFF_IN_SETTINGS, WarningsPermission.access(26, permissionGranted = false, notificationsEnabled = false, channelEnabled = true))
    }

    @Test
    fun grantedButTurnedOff_appOrChannel_isOffInSettings() {
        assertEquals(WarningsAccess.OFF_IN_SETTINGS, WarningsPermission.access(34, permissionGranted = true, notificationsEnabled = false, channelEnabled = true))
        assertEquals(WarningsAccess.OFF_IN_SETTINGS, WarningsPermission.access(34, permissionGranted = true, notificationsEnabled = true, channelEnabled = false))
        assertEquals(WarningsAccess.ALLOWED, WarningsPermission.access(34, permissionGranted = true, notificationsEnabled = true, channelEnabled = true))
    }

    @Test
    fun deniedForGood_opensSettings_deniedOnce_doesNot() {
        assertTrue(WarningsPermission.openSettingsAfterDenial(canAskAgain = false))
        assertFalse(WarningsPermission.openSettingsAfterDenial(canAskAgain = true))
    }

    @Test
    fun statusWords() {
        assertEquals("Allowed", WarningsPermission.status(WarningsAccess.ALLOWED))
        assertEquals("Tap to allow", WarningsPermission.status(WarningsAccess.ASK))
        assertEquals("Off in settings", WarningsPermission.status(WarningsAccess.OFF_IN_SETTINGS))
    }
}
