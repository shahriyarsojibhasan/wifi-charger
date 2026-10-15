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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.theme.TextSecondary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState

@Composable
fun DiagnosticsScreen(
    state: ChargerUiState,
    onTestRelay: () -> Unit = {},
    onManualProbe: () -> Unit = {},
    onToggleRelay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val diag = state.diagnostics
    val uptimeSecs = state.telemetry.uptimeSecs
    val days    = uptimeSecs / 86400
    val hours   = (uptimeSecs % 86400) / 3600
    val minutes = (uptimeSecs % 3600) / 60
    val secs    = uptimeSecs % 60
    val uptimeFormatted = if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m ${secs}s"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "Developer Diagnostics", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "Forensic Network & Hardware Telemetry", fontSize = 12.sp, color = TextMuted)
            }
            if (diag.isLocalTransportDebug) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberAlert.copy(alpha = 0.2f))
                        .border(1.dp, AmberAlert, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "LOCAL DEBUG", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberAlert)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1: Network & Android Interface Binding
        DiagnosticsSectionCard(title = "NETWORK (ANDROID INTERFACE)") {
            DiagRow(label = "Wi-Fi SSID", value = diag.wifiSsid)
            DiagRow(label = "Wi-Fi BSSID", value = diag.wifiBssid)
            DiagRow(label = "Android Local IP", value = diag.androidIp)
            DiagRow(label = "Default Gateway", value = diag.androidGateway)
            DiagRow(label = "Android Network ID", value = diag.androidNetworkId)
            DiagRow(
                label = "Process Binding",
                value = if (diag.androidGateway == "192.168.4.1" || diag.wifiSsid.contains("SMART", ignoreCase = true)) {
                    "PINNED TO WI-FI (AP ROUTE)"
                } else if (diag.isLocalTransportDebug) {
                    "PINNED TO WI-FI (LOCAL DEBUG)"
                } else {
                    "SYSTEM DEFAULT ROUTE"
                },
                valueColor = ElectricGreen
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2: Device Identification
        DiagnosticsSectionCard(title = "DEVICE & ENDPOINT") {
            DiagRow(label = "Device ID", value = diag.deviceId)
            DiagRow(label = "Reported Device IP", value = diag.discoveredDeviceIp)
            DiagRow(label = "Active Endpoint", value = state.activeBaseUrl ?: "NONE")
            DiagRow(label = "Configured LAN IP", value = state.localIpSetting)
            DiagRow(label = "mDNS Hostname", value = "http://smartcontroller.local")
            DiagRow(label = "Firmware Version", value = "5.2.0-PROD")
            DiagRow(label = "MCU Uptime", value = uptimeFormatted)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 3: Discovery Engine Telemetry
        DiagnosticsSectionCard(title = "MULTI-MECHANISM DISCOVERY") {
            DiagRow(
                label = "Direct AP (192.168.4.1)",
                value = if (diag.espApDetected) "FOUND & VERIFIED" else "PROBING",
                valueColor = if (diag.espApDetected) ElectricGreen else TextMuted
            )
            DiagRow(
                label = "UDP Broadcast (Port 8266)",
                value = diag.udpResult,
                valueColor = if (diag.udpResult.contains("FOUND")) ElectricGreen else TextMuted
            )
            DiagRow(
                label = "mDNS Resolution",
                value = diag.mdnsResult,
                valueColor = if (diag.mdnsResult.contains("FOUND")) ElectricGreen else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 4: Health Probing & Latency
        DiagnosticsSectionCard(title = "HEALTH & LATENCY") {
            DiagRow(
                label = "HTTP Health Probe",
                value = diag.httpHealthResult,
                valueColor = if (diag.httpStatusCode == 200) ElectricGreen else CoralRed
            )
            DiagRow(label = "HTTP Status Code", value = diag.httpStatusCode.toString())
            DiagRow(label = "Health Latency", value = "${diag.latencyMs} ms", valueColor = AmberAlert)
            DiagRow(label = "Consecutive Failure Count", value = "${diag.failureCount} / 3")
            DiagRow(label = "Last Successful Request", value = diag.lastSuccessfulRequest)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 5: Authoritative Transport State
        DiagnosticsSectionCard(title = "TRANSPORT & CLOUD STATE") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Selected Transport", fontSize = 12.5.sp, color = TextSecondary)
                ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
            }
            Spacer(modifier = Modifier.height(6.dp))
            DiagRow(
                label = "Hardware Life State",
                value = state.hardwareState.name,
                valueColor = if (state.hardwareState.name == "ONLINE") ElectricGreen else CoralRed
            )
            DiagRow(
                label = "Firebase Sync State",
                value = if (diag.isLocalTransportDebug) "ISOLATED (LOCAL DEBUG ACTIVE)" else state.cloudSyncState.name,
                valueColor = if (diag.isLocalTransportDebug) AmberAlert else NeonCyan
            )
            DiagRow(
                label = "Internet Required for Local",
                value = "NO (Android No-Internet Ignored)",
                valueColor = ElectricGreen
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 6: Manual Action Testing
        DiagnosticsSectionCard(title = "ACTION CONTROLS (TESTING)") {
            Button(
                onClick = onManualProbe,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
            ) {
                Text(text = "Trigger Multi-Mechanism Network Probe", fontSize = 12.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleRelay,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                ) {
                    Text(
                        text = if (state.telemetry.relayState) "Turn OFF Relay" else "Turn ON Relay",
                        fontSize = 11.sp,
                        color = if (state.telemetry.relayState) CoralRed else ElectricGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = onTestRelay,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                ) {
                    Text(text = "Relay Diagnostic", fontSize = 11.sp, color = AmberAlert, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 7: Connection Decision Audit Log
        DiagnosticsSectionCard(title = "CONNECTION DECISION AUDIT LOG") {
            Text(text = "Authoritative transitions with strict priority & hysteresis", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(10.dp))

            if (state.decisionLogs.isEmpty()) {
                Text(text = "No transitions logged yet. Transport is stable.", fontSize = 12.sp, color = TextMuted)
            } else {
                state.decisionLogs.takeLast(8).reversed().forEach { log ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${log.timeFormatted} • ${log.fromMode.name} → ${log.toMode.name}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(text = "${log.latencyMs}ms", fontSize = 11.sp, color = AmberAlert)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = log.reason, fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 8: Sensor & Electrical Diagnostics
        DiagnosticsSectionCard(title = "SENSORS & ELECTRICAL") {
            val (sensorLabel, sensorColor) = when (state.telemetry.sensorStatus) {
                SensorDiagnosticStatus.VOLTAGE_SENSOR_OK      -> "VOLTAGE SENSOR OK" to ElectricGreen
                SensorDiagnosticStatus.VOLTAGE_SENSOR_WARNING -> "VOLTAGE SENSOR WARNING" to AmberAlert
                SensorDiagnosticStatus.VOLTAGE_SENSOR_ERROR   -> "VOLTAGE SENSOR ERROR" to CoralRed
                SensorDiagnosticStatus.PROFILE_MISMATCH       -> "PROFILE MISMATCH" to AmberAlert
            }
            DiagRow(label = "Voltage Sensor", value = sensorLabel, valueColor = sensorColor)
            DiagRow(label = "Measured Voltage", value = "${state.telemetry.batteryVoltage} V")
            DiagRow(label = "Relay State", value = if (state.telemetry.relayState) "ENERGIZED (ON)" else "RELEASED (OFF)")

            val (diagLabel, diagColor) = when (state.telemetry.relayDiagnostic) {
                RelayDiagnosticStatus.RELAY_OK               -> "RELAY OK - Response verified" to ElectricGreen
                RelayDiagnosticStatus.RELAY_WARNING          -> "RELAY WARNING - Sluggish" to AmberAlert
                RelayDiagnosticStatus.RELAY_ERROR            -> "RELAY ERROR - Check wiring" to CoralRed
                RelayDiagnosticStatus.NO_ELECTRICAL_RESPONSE -> "NO RESPONSE - Electrical fault" to CoralRed
                RelayDiagnosticStatus.UNKNOWN                -> "Not tested" to TextMuted
            }
            DiagRow(label = "Relay Diagnostic", value = diagLabel, valueColor = diagColor)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosticsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DiagRow(
    label: String,
    value: String,
    valueColor: Color = NeonCyan
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
