package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LockLabelsTest {

    @Test
    fun bedtimeLock_saysGoBackToSleep() {
        assertEquals("Go back to sleep", LockLabels.goBack(LockReason.BEDTIME))
    }

    @Test
    fun limitLock_saysWalkAway_andNeverMentionsSleep() {
        assertEquals("Walk away", LockLabels.goBack(LockReason.LIMIT))
        assertFalse(LockLabels.goBack(LockReason.LIMIT).contains("sleep", ignoreCase = true))
    }
}
