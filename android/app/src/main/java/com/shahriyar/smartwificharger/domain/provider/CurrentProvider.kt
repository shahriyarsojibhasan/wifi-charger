package com.shahriyar.smartwificharger.domain.provider

/**
 * Current Provider Abstraction.
 *
 * Current hardware has NO physical current sensor.
 * All current, power, energy, and cost are ESTIMATED.
 *
 * This abstraction allows swapping in physical current sensors
 * (ACS712, INA219, INA226, AC Energy Meter) without redesigning
 * the domain model, database schema, or UI presentation.
 */
interface CurrentProvider {
    fun getCurrentAmps(voltage: Float): Float
    fun getPowerWatts(voltage: Float): Float
    fun getProviderName(): String
    fun isEstimated(): Boolean
}

/**
 * Active Default Provider: Mathematical model estimation based on configured charger ratings.
 */
class EstimatedCurrentProvider(
    private val configuredWatt: Float = 100.0f,
    private val configuredAmps: Float = 1.7f
) : CurrentProvider {
    override fun getCurrentAmps(voltage: Float): Float {
        return if (voltage > 0.5f) {
            configuredWatt / voltage
        } else {
            configuredAmps
        }
    }

    override fun getPowerWatts(voltage: Float): Float {
        return configuredWatt
    }

    override fun getProviderName(): String = "ESTIMATED_MATH_MODEL"

    override fun isEstimated(): Boolean = true
}

/**
 * Drop-in Provider for Allegro ACS712 Hall-Effect AC/DC Current Sensor.
 */
class Acs712CurrentProvider(
    private val sensitivityMvPerA: Float = 66.0f // 66 mV/A for 30A module
) : CurrentProvider {
    override fun getCurrentAmps(voltage: Float): Float = 0.0f
    override fun getPowerWatts(voltage: Float): Float = 0.0f
    override fun getProviderName(): String = "ACS712_CURRENT_SENSOR"
    override fun isEstimated(): Boolean = false
}

/**
 * Drop-in Provider for Texas Instruments INA219 I2C High-Side Current & Power Sensor.
 */
class Ina219CurrentProvider(
    private val i2cAddress: Int = 0x40
) : CurrentProvider {
    override fun getCurrentAmps(voltage: Float): Float = 0.0f
    override fun getPowerWatts(voltage: Float): Float = 0.0f
    override fun getProviderName(): String = "INA219_I2C_SENSOR"
    override fun isEstimated(): Boolean = false
}

/**
 * Drop-in Provider for Texas Instruments INA226 Precision 36V I2C Power Monitor.
 */
class Ina226CurrentProvider(
    private val i2cAddress: Int = 0x40
) : CurrentProvider {
    override fun getCurrentAmps(voltage: Float): Float = 0.0f
    override fun getPowerWatts(voltage: Float): Float = 0.0f
    override fun getProviderName(): String = "INA226_I2C_PRECISION"
    override fun isEstimated(): Boolean = false
}

/**
 * Drop-in Provider for Dedicated AC Energy Meters (e.g. PZEM-004T UART).
 */
class EnergyMeterProvider(
    private val port: String = "UART0"
) : CurrentProvider {
    override fun getCurrentAmps(voltage: Float): Float = 0.0f
    override fun getPowerWatts(voltage: Float): Float = 0.0f
    override fun getProviderName(): String = "AC_ENERGY_METER_UART"
    override fun isEstimated(): Boolean = false
}
