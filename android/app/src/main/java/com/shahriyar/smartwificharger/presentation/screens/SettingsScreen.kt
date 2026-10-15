package com.shahriyar.smartwificharger.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.CoralRed
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState

@Composable
fun SettingsScreen(
    onFactoryReset: () -> Unit = {},
    state: ChargerUiState,
    onSaveConfig: (ChargerConfig) -> Unit,
    onUpdateConnection: (String, String, String) -> Unit,
    onDiscoverLocal: () -> Unit,
    onReboot: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lowCutText by remember(state.config.lowCut) { mutableStateOf(state.config.lowCut.toString()) }
    var highCutText by remember(state.config.highCut) { mutableStateOf(state.config.highCut.toString()) }
    var hysteresisText by remember(state.config.hysteresis) { mutableStateOf(state.config.hysteresis.toString()) }
    var calText by remember(state.config.voltageCalibration) { mutableStateOf(state.config.voltageCalibration.toString()) }

    var localIpText by remember(state.localIpSetting) { mutableStateOf(state.localIpSetting) }
    var firebaseDbText by remember(state.firebaseDbUrlSetting) { mutableStateOf(state.firebaseDbUrlSetting) }
    var deviceIdText by remember(state.deviceIdSetting) { mutableStateOf(state.deviceIdSetting) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "⚙ Hardware & System Configuration",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Battery voltage thresholds, ADC calibration & endpoints",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Voltage Thresholds Card
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
                    text = "Hardware Calibration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))



                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = calText,
                    onValueChange = { calText = it },
                    label = { Text("ADC Calibration Multiplier (Default 1.000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedLabelColor = NeonCyan,
                        unfocusedContainerColor = SurfaceDark,
                        focusedContainerColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val cal = calText.toFloatOrNull() ?: 1.000f
                        onSaveConfig(
                            state.config.copy(
                                voltageCalibration = cal
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text(
                        text = "SAVE CALIBRATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection & Cloud Sync Card
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
                    text = "Local & Cloud Connectivity",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = localIpText,
                    onValueChange = { localIpText = it },
                    label = { Text("Device Local IP / URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedLabelColor = NeonCyan,
                        unfocusedContainerColor = SurfaceDark,
                        focusedContainerColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDiscoverLocal,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark)
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text = "🔍 Auto-Discover Device on Local Wi-Fi", fontSize = 12.sp, color = NeonCyan)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = firebaseDbText,
                    onValueChange = { firebaseDbText = it },
                    label = { Text("Firebase Realtime Database URL") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedLabelColor = NeonCyan,
                        unfocusedContainerColor = SurfaceDark,
                        focusedContainerColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = deviceIdText,
                    onValueChange = { deviceIdText = it },
                    label = { Text("Device ID") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedLabelColor = NeonCyan,
                        unfocusedContainerColor = SurfaceDark,
                        focusedContainerColor = SurfaceDark
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onUpdateConnection(localIpText, firebaseDbText, deviceIdText)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text(
                        text = "UPDATE CONNECTION SETTINGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Action Card
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
                    text = "System Maintenance",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onReboot,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed.copy(alpha = 0.2f))
                ) {
                    Text(text = "🔄 Safe Reboot Device", fontSize = 12.sp, color = CoralRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
