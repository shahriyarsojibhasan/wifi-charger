package com.shahriyar.smartwificharger.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.presentation.components.ConnectionBadge
import com.shahriyar.smartwificharger.presentation.theme.AmberAlert
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState

@Composable
fun DevicesScreen(
    state: ChargerUiState,
    onDiscoverLocal: () -> Unit,
    onSwitchDevice: (String) -> Unit = {},
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
                    text = "Paired Devices",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
                Text(
                    text = "Manage and pair smart charger hardware",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Device Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = NeonCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.config.deviceName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricGreen.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Device ID", fontSize = 12.sp, color = TextMuted)
                    Text(text = state.config.deviceId, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "IP Address", fontSize = 12.sp, color = TextMuted)
                    Text(text = state.telemetry.deviceIp, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Wi-Fi RSSI", fontSize = 12.sp, color = TextMuted)
                    Text(text = "${state.telemetry.wifiRssi} dBm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Firmware Version", fontSize = 12.sp, color = TextMuted)
                    Text(text = "v5.1.0-PROD", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricGreen)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Uptime", fontSize = 12.sp, color = TextMuted)
                    Text(text = "${state.telemetry.uptimeSecs / 60} minutes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-Detected Device Alert Banner (if different from active)
        if (state.detectedDeviceId != null && state.detectedDeviceId != state.config.deviceId) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmberAlert.copy(alpha = 0.15f))
                    .border(1.5.dp, AmberAlert, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = AmberAlert)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Charger Detected on Network!",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAlert
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Found device '${state.detectedDeviceId}'. Tap below to switch active connection.",
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onSwitchDevice(state.detectedDeviceId) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAlert)
                    ) {
                        Text("SWITCH TO ${state.detectedDeviceId}", fontWeight = FontWeight.Bold, color = DarkBackground, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Auto Scan / Discover Button
        Button(
            onClick = onDiscoverLocal,
            enabled = !state.isScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = DarkBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isScanning) "AUTO-DETECTING CHARGER..." else "AUTO-DETECT & SCAN CHARGER",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBackground
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Registered & Available Chargers Switcher
        Text(
            text = "Registered Chargers (${state.availableDevices.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))

        for (devId in state.availableDevices) {
            val isActive = (devId == state.config.deviceId)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) NeonCyan.copy(alpha = 0.12f) else SurfaceCard)
                    .border(1.dp, if (isActive) NeonCyan else BorderDark, RoundedCornerShape(12.dp))
                    .clickable { onSwitchDevice(devId) }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Devices,
                            contentDescription = null,
                            tint = if (isActive) ElectricGreen else TextMuted
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = devId,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isActive) "Currently Active • Auto Syncing" else "Tap to switch to this device",
                                fontSize = 11.sp,
                                color = if (isActive) ElectricGreen else TextMuted
                            )
                        }
                    }
                    if (!isActive) {
                        OutlinedButton(
                            onClick = { onSwitchDevice(devId) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("SELECT", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Pairing Instructions Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "How to Pair & Auto-Detect Smart Charger",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Connect phone's Wi-Fi to 'SMART-CHARGER-CONTROLLER' (Password: 12345678, IP: 192.168.4.1).\n" +
                            "2. The app will auto-detect the charger's Device ID immediately.\n" +
                            "3. Open 'Network & Wi-Fi' tab and tap 'SCAN WI-FI' to connect your charger to home router.\n" +
                            "4. Once connected to router, the charger automatically syncs with Firebase cloud.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
