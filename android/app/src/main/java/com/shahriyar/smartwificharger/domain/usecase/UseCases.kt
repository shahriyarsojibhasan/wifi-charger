package com.shahriyar.smartwificharger.domain.usecase

import com.shahriyar.smartwificharger.data.connection.DeviceCommandResult
import com.shahriyar.smartwificharger.data.repository.ChargerDataBundle
import com.shahriyar.smartwificharger.data.repository.ChargerRepository
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.domain.model.ChargingMode

class GetChargerStateUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(): ChargerDataBundle {
        return repository.refreshData()
    }
}

class SendRelayCommandUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(turnOn: Boolean): DeviceCommandResult {
        return repository.sendRelayCommand(turnOn)
    }
}

class SetOperatingModeUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(mode: ChargingMode): DeviceCommandResult {
        return repository.setOperatingMode(mode)
    }
}

class UpdateConfigUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(config: ChargerConfig): DeviceCommandResult {
        // Pre-flight client-side validation
        if (config.lowCut < 3.0f || config.lowCut > 30.0f) {
            return DeviceCommandResult(false, "", repository.connectionState.value, ack = "VALIDATION_FAILED", message = "Low cut must be between 3.0V and 30.0V")
        }
        if (config.highCut < 4.0f || config.highCut > 32.0f) {
            return DeviceCommandResult(false, "", repository.connectionState.value, ack = "VALIDATION_FAILED", message = "High cut must be between 4.0V and 32.0V")
        }
        if (config.lowCut >= config.highCut) {
            return DeviceCommandResult(false, "", repository.connectionState.value, ack = "VALIDATION_FAILED", message = "Low cut must be strictly less than high cut")
        }
        return repository.updateConfig(config)
    }
}

class SetScheduleUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int = 127): DeviceCommandResult {
        return repository.setSchedule(startH, startM, stopH, stopM, enabled, tzOffset, daysBitmask)
    }
}

class EmergencyStopUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(stop: Boolean = true): DeviceCommandResult {
        return repository.emergencyStop(stop)
    }
}

class SetMaintenanceModeUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(enable: Boolean): DeviceCommandResult {
        return repository.setMaintenanceMode(enable)
    }
}

class SetCountdownTimerUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(minutes: Int): DeviceCommandResult {
        return repository.setTimer(minutes)
    }
}

class TriggerDiagnosticsUseCase(
    private val repository: ChargerRepository
) {
    suspend operator fun invoke(): DeviceCommandResult {
        return repository.triggerRelayDiagnostic()
    }
}
