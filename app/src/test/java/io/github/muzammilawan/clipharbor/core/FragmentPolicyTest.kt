package io.github.muzammilawan.clipharbor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentPolicyTest {
    @Test fun autoUsesTheDefaultOnNormalPower() {
        assertEquals(4, FragmentPolicy.resolve(FragmentPolicy.AUTO, powerSave = false, batteryPercent = 80))
        assertEquals(4, FragmentPolicy.resolve(FragmentPolicy.AUTO, powerSave = false, batteryPercent = null))
        assertEquals(4, FragmentPolicy.resolve(FragmentPolicy.AUTO, powerSave = false, batteryPercent = 16))
    }

    @Test fun autoDropsToOneInPowerSaverOrAtTheLowBatteryBoundary() {
        assertEquals(1, FragmentPolicy.resolve(FragmentPolicy.AUTO, powerSave = true, batteryPercent = 90))
        assertEquals(1, FragmentPolicy.resolve(FragmentPolicy.AUTO, powerSave = false, batteryPercent = 15))
        assertEquals(1, FragmentPolicy.resolve(FragmentPolicy.AUTO, powerSave = false, batteryPercent = 3))
    }

    @Test fun explicitChoiceAlwaysWins() {
        assertEquals(2, FragmentPolicy.resolve(2, powerSave = true, batteryPercent = 2))
        assertEquals(8, FragmentPolicy.resolve(8, powerSave = false, batteryPercent = 100))
    }

    @Test fun outOfRangeStoredValuesFallBackToAutoAndValuesStayBounded() {
        assertTrue(FragmentPolicy.valid(FragmentPolicy.AUTO))
        assertTrue(FragmentPolicy.valid(1))
        assertTrue(FragmentPolicy.valid(8))
        assertFalse(FragmentPolicy.valid(-1))
        assertFalse(FragmentPolicy.valid(9))
        assertEquals(FragmentPolicy.AUTO, FragmentPolicy.sanitize(99))
        assertEquals(4, FragmentPolicy.resolve(99, powerSave = false, batteryPercent = 100))
        assertTrue(FragmentPolicy.choices.all(FragmentPolicy::valid))
        assertTrue(FragmentPolicy.explanation(FragmentPolicy.AUTO).contains("1"))
        assertEquals("Auto", FragmentPolicy.label(-5))
    }
}
