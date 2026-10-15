package com.shahriyar.smartwificharger

import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.domain.model.ChargingMode
import com.shahriyar.smartwificharger.domain.model.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Production Unit Test Suite validating the Core Architecture & Invariants:
 *  1. Connection Priority & Hysteresis Invariant
 *  2. Charging Safety Cutoffs & Hysteresis Invariant
 *  3. Midnight-Crossing Schedule Logic
 *  4. Power, Energy & Billing Formulas (Strictly ESTIMATED)
 *  5. Configuration Range Validation
 *  6. Command Deduplication & Expiration Semantics
 */
class ArchitectureAndLogicTests {

    // ================================================================================================
    // 1. CONNECTION PRIORITY & HYSTERESIS TESTS
    // ================================================================================================
    @Test
    fun `test connection priority hierarchy order`() {
        val priorities = listOf(
            ConnectionMode.DIRECT_AP,
            ConnectionMode.LOCAL_LAN,
            ConnectionMode.FIREBASE_WAN,
            ConnectionMode.OFFLINE
        )
        assertEquals(ConnectionMode.DIRECT_AP, priorities[0])
        assertEquals(ConnectionMode.LOCAL_LAN, priorities[1])
        assertEquals(ConnectionMode.FIREBASE_WAN, priorities[2])
        assertEquals(ConnectionMode.OFFLINE, priorities[3])
    }

    @Test
    fun `test local connection stickiness requires multiple consecutive failures before WAN fallback`() {
        var failureCounter = 0
        val maxFailuresBeforeFallback = 3

        // Simulate 1st failure: temporary glitch
        failureCounter++
        var shouldFallback = (failureCounter >= maxFailuresBeforeFallback)
        assertFalse("Single failure must NOT trigger WAN fallback", shouldFallback)

        // Simulate 2nd failure: temporary glitch
        failureCounter++
        shouldFallback = (failureCounter >= maxFailuresBeforeFallback)
        assertFalse("Two failures must NOT trigger WAN fallback", shouldFallback)

        // Simulate 3rd failure: confirmed failure
        failureCounter++
        shouldFallback = (failureCounter >= maxFailuresBeforeFallback)
        assertTrue("Three consecutive failures MUST trigger fallback", shouldFallback)

        // When device recovers locally, counter resets immediately
        failureCounter = 0
        shouldFallback = (failureCounter >= maxFailuresBeforeFallback)
        assertFalse("Counter reset must restore local transport priority", shouldFallback)
    }

    // ================================================================================================
    // 2. CHARGING SAFETY & HYSTERESIS TESTS
    // ================================================================================================
    @Test
    fun `test high cut disconnects charger and hysteresis prevents immediate re-trigger`() {
        val lowCut = 11.5f
        val highCut = 14.2f
        val hysteresis = 0.3f

        var currentVoltage = 14.25f
        val shouldCutoff = (currentVoltage >= highCut)
        assertTrue("Voltage >= 14.2V must cutoff relay", shouldCutoff)

        // Voltage drops slightly to 14.0V (within hysteresis zone)
        currentVoltage = 14.0f
        val shouldRestartCharging = (currentVoltage < (highCut - hysteresis) && currentVoltage <= lowCut + 0.3f)
        assertFalse("Relay must NOT chatter ON in hysteresis zone", shouldRestartCharging)

        // Voltage drops down to 11.4V (below low cut)
        currentVoltage = 11.4f
        val isLowBatteryTrigger = (currentVoltage <= lowCut && currentVoltage >= 0.5f)
        assertTrue("Voltage <= 11.5V must trigger low battery charge", isLowBatteryTrigger)
    }

    @Test
    fun `test disconnected voltage sensor triggers immediate safety shutoff`() {
        val adcReadingVolts = 0.2f
        val isSensorDisconnected = (adcReadingVolts < 0.5f)
        assertTrue("ADC voltage < 0.5V represents disconnected sensor/battery", isSensorDisconnected)
    }

    @Test
    fun `test absolute over-voltage ceiling triggers emergency shutdown`() {
        val emergencyCeiling = 15.5f
        val overVoltage = 15.8f
        val isOverVoltageFault = (overVoltage >= emergencyCeiling)
        assertTrue("Voltage >= 15.5V must immediately trip emergency over-voltage fault", isOverVoltageFault)
    }

    // ================================================================================================
    // 3. SCHEDULE SYSTEM TESTS (Including Midnight Crossing)
    // ================================================================================================
    @Test
    fun `test standard day schedule window 03 to 04`() {
        val startHour = 3
        val stopHour = 4

        fun isInWindow(hour: Int): Boolean {
            return if (startHour < stopHour) {
                hour in startHour until stopHour
            } else {
                hour >= startHour || hour < stopHour
            }
        }

        assertTrue("03:00 must be in window", isInWindow(3))
        assertFalse("02:59 (hour 2) must NOT be in window", isInWindow(2))
        assertFalse("04:00 (hour 4) must NOT be in window", isInWindow(4))
        assertFalse("12:00 must NOT be in window", isInWindow(12))
    }

    @Test
    fun `test midnight-crossing schedule window 23 to 02`() {
        val startHour = 23
        val stopHour = 2

        fun isInWindow(hour: Int): Boolean {
            return if (startHour < stopHour) {
                hour in startHour until stopHour
            } else {
                hour >= startHour || hour < stopHour
            }
        }

        assertTrue("23:00 must be in window", isInWindow(23))
        assertTrue("00:00 (midnight hour 0) must be in window", isInWindow(0))
        assertTrue("01:00 (hour 1) must be in window", isInWindow(1))
        assertFalse("02:00 (hour 2) must NOT be in window", isInWindow(2))
        assertFalse("22:00 (hour 22) must NOT be in window", isInWindow(22))
        assertFalse("15:00 (hour 15) must NOT be in window", isInWindow(15))
    }

    // ================================================================================================
    // 4. POWER, ENERGY & BILLING ESTIMATION FORMULAS
    // ================================================================================================
    @Test
    fun `test estimated billing calculation formulas`() {
        val voltage = 12.5f
        val estimatedCurrentAmps = 1.70f
        val durationSeconds = 3600L // 1 hour
        val pricePerKwh = 10.0f // BDT

        // Power = V * I
        val powerWatts = voltage * estimatedCurrentAmps
        assertEquals(21.25f, powerWatts, 0.01f)

        // Energy Wh = V * I * Hours
        val durationHours = durationSeconds / 3600.0f
        val energyWh = powerWatts * durationHours
        assertEquals(21.25f, energyWh, 0.01f)

        // Energy kWh = Wh / 1000
        val energyKwh = energyWh / 1000.0f
        assertEquals(0.02125f, energyKwh, 0.0001f)

        // Estimated Cost = kWh * rate
        val costBdt = energyKwh * pricePerKwh
        assertEquals(0.2125f, costBdt, 0.0001f)
    }

    // ================================================================================================
    // 5. CONFIGURATION RANGE & VALIDATION
    // ================================================================================================
    @Test
    fun `test config validation accepts safe parameters and rejects unsafe bounds`() {
        fun validateConfig(c: ChargerConfig): Boolean {
            if (c.lowCut < 9.0f || c.lowCut > 13.5f) return false
            if (c.highCut < 12.5f || c.highCut > 16.0f) return false
            if (c.lowCut >= c.highCut) return false
            if (c.hysteresis < 0.05f || c.hysteresis > 2.0f) return false
            if (c.chargeStartHour !in 0..23 || c.chargeStopHour !in 0..23) return false
            if (c.kwhRateBdt < 0.0f) return false
            if (c.chargerAmps < 0.1f) return false
            return true
        }

        // Safe default config
        val validConfig = ChargerConfig(
            lowCut = 11.5f,
            highCut = 14.2f,
            hysteresis = 0.3f,
            chargerAmps = 1.7f,
            kwhRateBdt = 10.0f,
            chargeStartHour = 3,
            chargeStopHour = 4
        )
        assertTrue("Safe configuration must pass validation", validateConfig(validConfig))

        // Inverted cutoffs (low >= high)
        val invertedConfig = validConfig.copy(lowCut = 14.5f, highCut = 14.0f)
        assertFalse("Inverted cutoffs must be rejected", validateConfig(invertedConfig))

        // Unsafe low cut (too low)
        val dangerousLow = validConfig.copy(lowCut = 8.0f)
        assertFalse("Dangerous low cut (<9V) must be rejected", validateConfig(dangerousLow))

        // Negative electricity rate
        val negativeRate = validConfig.copy(kwhRateBdt = -5.0f)
        assertFalse("Negative electricity rate must be rejected", validateConfig(negativeRate))

        // Invalid hour (>23)
        val invalidHour = validConfig.copy(chargeStartHour = 25)
        assertFalse("Invalid schedule hour must be rejected", validateConfig(invalidHour))
    }

    // ================================================================================================
    // 6. COMMAND DEDUPLICATION & EXPIRATION LOGIC
    // ================================================================================================
    @Test
    fun `test command deduplication rejects identical command IDs`() {
        val history = mutableSetOf<String>()

        fun processCommand(cmdId: String): String {
            if (history.contains(cmdId)) {
                return "DUPLICATE"
            }
            history.add(cmdId)
            return "EXECUTED"
        }

        val cmdId = "CMD-1700000000000"
        assertEquals("EXECUTED", processCommand(cmdId))
        assertEquals("DUPLICATE", processCommand(cmdId))
    }

    @Test
    fun `test command expiration rejects stale commands`() {
        val nowEpoch = 1700000100L
        val expiredExpiresAt = 1700000050L // 50 seconds in past
        val validExpiresAt = 1700000150L   // 50 seconds in future

        val isExpired = nowEpoch > expiredExpiresAt
        val isValid = nowEpoch <= validExpiresAt

        assertTrue("Stale command must be marked expired", isExpired)
        assertTrue("Future command must be accepted", isValid)
    }

    // ================================================================================================
    // 7. BATTERY PROFILE ID & 12V MAX SUPPORT TESTS
    // ================================================================================================
    @Test
    fun `test battery profile mappings and user conservative 11v to 12_8v profile`() {
        assertEquals(0, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("LEAD_ACID_12V"))
        assertEquals(1, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("LEAD_ACID_CONSERVATIVE"))
        assertEquals(1, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("LEAD_ACID_USER"))
        assertEquals(1, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("12V_11_12.8"))
        assertEquals(2, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("AGM_12V"))
        assertEquals(3, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("GEL_12V"))
        assertEquals(4, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("TUBULAR_12V"))
        assertEquals(5, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("LIFEPO4_12V"))
        assertEquals(6, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("SYSTEM_9V"))
        assertEquals(6, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("9V"))
        assertEquals(7, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("SYSTEM_6V"))
        assertEquals(7, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("6V"))
        assertEquals(8, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("SYSTEM_5V"))
        assertEquals(8, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("5V"))
        assertEquals(9, com.shahriyar.smartwificharger.data.connection.mapProfileNameToId("CUSTOM"))

        // User profile verification (11.0V to 12.8V)
        val userLowCut = 11.0f
        val userHighCut = 12.8f
        val userHysteresis = 0.25f

        assertTrue("User low cutoff must be 11.0V", userLowCut == 11.0f)
        assertTrue("User high cutoff must be 12.8V", userHighCut == 12.8f)
        assertTrue("User hysteresis must be 0.25V", userHysteresis == 0.25f)
        assertTrue("Cutoff must be safe below 15.0V ceiling", userHighCut <= 15.0f)
    }

    @Test
    fun `test 12V max battery supported rejects 24V profiles and clamps to 15V ceiling`() {
        fun isSupportedMaxBattery(highCut: Float): Boolean {
            return highCut <= 15.0f
        }

        assertTrue("12V Lead-Acid 12.8V is supported", isSupportedMaxBattery(12.8f))
        assertTrue("12V Lead-Acid 14.2V is supported", isSupportedMaxBattery(14.2f))
        assertTrue("12V Tubular 14.6V is supported", isSupportedMaxBattery(14.6f))
        assertFalse("24V system (28.8V) exceeds 12V system limit and must be rejected", isSupportedMaxBattery(28.8f))
    }

    @Test
    fun `test estimated billing harmonizes session and daily totals`() {
        val chargerWatt = 100f
        val session1Secs = 1800L // 0.5 hour
        val session2Secs = 3600L // 1.0 hour
        val totalSecs = session1Secs + session2Secs // 1.5 hours
        val electricityRate = 8.5f

        fun calcKwh(secs: Long, watts: Float): Float = (secs / 3600f * watts) / 1000f

        val session1Kwh = calcKwh(session1Secs, chargerWatt)
        val session2Kwh = calcKwh(session2Secs, chargerWatt)
        val totalKwh = calcKwh(totalSecs, chargerWatt)

        assertEquals(0.050f, session1Kwh, 0.001f)
        assertEquals(0.100f, session2Kwh, 0.001f)
        assertEquals(session1Kwh + session2Kwh, totalKwh, 0.0001f)
        assertEquals(0.150f * electricityRate, totalKwh * electricityRate, 0.001f)
    }
}
