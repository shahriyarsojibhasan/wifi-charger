package com.shahriyar.smartwificharger.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.RelayDiagnosticStatus
import com.shahriyar.smartwificharger.domain.model.SensorDiagnosticStatus
import com.shahriyar.smartwificharger.presentation.components.ConnectionBadge
import com.shahriyar.smartwificharger.presentation.theme.AmberAlert
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.CoralRed
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState

@Composable
fun AlertsScreen(
    state: ChargerUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "System Alerts & Safety",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
                Text(
                    text = "Real-time hardware safety diagnostics",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voltage Sensor Diagnostic Card
        AlertCard(
            title = "0-25V Voltage Sensor Diagnostic",
            statusText = when (state.telemetry.sensorStatus) {
                SensorDiagnosticStatus.VOLTAGE_SENSOR_OK -> "VOLTAGE SENSOR OK"
                SensorDiagnosticStatus.VOLTAGE_SENSOR_WARNING -> "VOLTAGE SENSOR WARNING"
                SensorDiagnosticStatus.VOLTAGE_SENSOR_ERROR -> "VOLTAGE SENSOR ERROR"
                SensorDiagnosticStatus.PROFILE_MISMATCH -> "BATTERY PROFILE MISMATCH"
            },
            statusColor = when (state.telemetry.sensorStatus) {
                SensorDiagnosticStatus.VOLTAGE_SENSOR_OK -> ElectricGreen
                SensorDiagnosticStatus.VOLTAGE_SENSOR_WARNING -> AmberAlert
                SensorDiagnosticStatus.VOLTAGE_SENSOR_ERROR -> CoralRed
                SensorDiagnosticStatus.PROFILE_MISMATCH -> AmberAlert
            },
            description = when {
                state.telemetry.sensorStatus == SensorDiagnosticStatus.PROFILE_MISMATCH ->
                    "Connected battery voltage is below selected profile minimum threshold. Please select a matching 5V or 6V profile."
                state.telemetry.sensorDisconnected ->
                    "Sensor signal line is floating (< 1.2V). Possible loose wiring or disconnected battery."
                else ->
                    "Analog-to-Digital reading active. 20-sample oversampled moving average filter operating within normal limits."
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Relay Electrical Response Diagnostic Card
        AlertCard(
            title = "Relay & Charger Electrical Diagnostic",
            statusText = state.telemetry.relayDiagnostic.displayName,
            statusColor = when (state.telemetry.relayDiagnostic) {
                RelayDiagnosticStatus.RELAY_OK -> ElectricGreen
                RelayDiagnosticStatus.RELAY_WARNING -> AmberAlert
                RelayDiagnosticStatus.RELAY_ERROR,
                RelayDiagnosticStatus.NO_ELECTRICAL_RESPONSE -> CoralRed
                RelayDiagnosticStatus.UNKNOWN -> TextMuted
            },
            description = state.telemetry.relayDiagnostic.message +
                    "\nNote: Diagnosis is based on voltage response delta. Possible relay, charger or wiring problem."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Anti-Chatter Protection Card
        AlertCard(
            title = "Anti-Chatter & Stabilization Guard",
            statusText = if (state.telemetry.manualLockSecs > 0) "MANUAL LOCK ACTIVE (${state.telemetry.manualLockSecs}s)" else "STABILIZATION OK",
            statusColor = if (state.telemetry.manualLockSecs > 0) AmberAlert else ElectricGreen,
            description = "Enforces a minimum 15-second rest period between relay switching cycles to protect the relay coil and charger transformer from rapid chatter."
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Emergency Over-Voltage Safety Card
        AlertCard(
            title = "Emergency Over-Voltage Cutoff",
            statusText = if (state.fault.isOverVoltage) "OVER-VOLTAGE CUTOFF TRIGGERED!" else "SAFE CEILING ENFORCED (15.5V)",
            statusColor = if (state.fault.isOverVoltage) CoralRed else ElectricGreen,
            description = "Absolute autonomous firmware ceiling. If voltage reaches 15.5V, charger disconnects immediately regardless of app or cloud commands."
        )
    }
}

@Composable
fun AlertCard(
    title: String,
    statusText: String,
    statusColor: Color,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 11.5.sp,
                color = TextMuted,
                lineHeight = 16.sp
            )
        }
    }
}
