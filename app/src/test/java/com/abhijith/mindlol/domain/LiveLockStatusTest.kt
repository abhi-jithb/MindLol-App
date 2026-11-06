package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLockStatusTest {

    private val ours = "com.abhijith.mindlol/com.abhijith.mindlol.service.LockAccessibilityService"

    @Test
    fun state_fromEnabledAndRunning() {
        assertEquals(LiveLockState.ON, LiveLockStatus.state(enabledInSettings = true, running = true))
        assertEquals(LiveLockState.NOT_RUNNING, LiveLockStatus.state(enabledInSettings = true, running = false))
        assertEquals(LiveLockState.OFF, LiveLockStatus.state(enabledInSettings = false, running = false))
        // Switching off: the setting changes first, the service stops a moment later.
        assertEquals(LiveLockState.OFF, LiveLockStatus.state(enabledInSettings = false, running = true))
    }

    @Test
    fun offIsLoud_andTellsYouWhatToDo() {
        assertEquals("OFF", LiveLockStatus.status(LiveLockState.OFF))
        assertTrue(LiveLockStatus.reason(LiveLockState.OFF).contains("Tap to turn it on"))
    }

    @Test
    fun isEnabled_findsUsAmongOtherServices_inFullOrShortForm_anyCase() {
        // The real value read from the phone on 2026-11-06.
        val phone = "com.coderGtm.yantra/com.coderGtm.yantra.services.YantraAccessibilityService:" +
            "com.brainrot.android/com.brainrot.android.services.ReelsAccessibilityService:$ours"
        assertTrue(LiveLockStatus.isEnabled(phone, ours))
        assertTrue(LiveLockStatus.isEnabled("com.abhijith.mindlol/.service.LockAccessibilityService", ours))
        assertTrue(LiveLockStatus.isEnabled(ours.uppercase(), ours))
    }

    @Test
    fun isEnabled_falseWhenMissingEmptyOrOnlySimilar() {
        assertFalse(LiveLockStatus.isEnabled(null, ours))
        assertFalse(LiveLockStatus.isEnabled("", ours))
        assertFalse(LiveLockStatus.isEnabled("com.coderGtm.yantra/com.coderGtm.yantra.services.YantraAccessibilityService", ours))
        assertFalse(LiveLockStatus.isEnabled("com.abhijith.mindlol/com.abhijith.mindlol.service.Other", ours))
        assertFalse(LiveLockStatus.isEnabled("garbage:/:a/", ours))
    }

    @Test
    fun picker_hidesNeverLockableApps_andAppsThatAlreadyHaveARule() {
        val installed = listOf(
            InstalledApp("com.instagram.android", "Instagram"),
            InstalledApp("com.abhijith.mindlol", "MindLol"),
            InstalledApp("com.android.settings", "Settings"),
            InstalledApp("com.google.android.dialer", "Phone"),
            InstalledApp("com.google.android.youtube", "YouTube")
        )
        val never = setOf("com.abhijith.mindlol", "com.android.settings", "com.google.android.dialer")
        val addable = LiveLockStatus.addableApps(installed, alreadyShown = listOf("com.google.android.youtube"), neverLockable = never)
        assertEquals(listOf("com.instagram.android"), addable.map { it.packageName })
    }
}
