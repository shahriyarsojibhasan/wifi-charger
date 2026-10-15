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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
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
fun AutomationScreen(
    state: ChargerUiState,
    onSaveConfig: (ChargerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var selectedProfile by remember(state.config.batteryProfile) {
        val prof = com.shahriyar.smartwificharger.data.connection.normalizeBatteryProfileName(state.config.batteryProfile)
        mutableStateOf(prof)
    }
    var lowCut by remember(state.config.lowCut) { mutableStateOf(state.config.lowCut.toString()) }
    var highCut by remember(state.config.highCut) { mutableStateOf(state.config.highCut.toString()) }
    var hysteresis by remember(state.config.hysteresis) { mutableStateOf(state.config.hysteresis.toString()) }

    val presets = listOf(
        Triple("LEAD_ACID_CONSERVATIVE", "Lead-Acid 12V (11.0V - 12.8V)", Triple(11.0f, 12.8f, 0.25f)),
        Triple("LEAD_ACID_12V", "Standard Lead-Acid 12V (11.5V - 14.2V)", Triple(11.5f, 14.2f, 0.3f)),
        Triple("GEL_12V", "Gel Battery (12V)", Triple(11.8f, 14.1f, 0.25f)),
        Triple("TUBULAR_12V", "Tubular Deep Cycle (12V)", Triple(11.2f, 14.6f, 0.35f)),
        Triple("LIFEPO4_12V", "LiFePO4 4S (12V)", Triple(11.0f, 14.4f, 0.2f)),
        Triple("AGM_12V", "AGM / Power (12V)", Triple(11.8f, 14.4f, 0.3f)),
        Triple("9V", "9V Emergency Pack", Triple(8.0f, 10.5f, 0.25f)),
        Triple("6V", "6V Emergency Pack", Triple(5.5f, 7.2f, 0.2f)),
        Triple("5V", "5V Power Pack", Triple(4.2f, 5.5f, 0.15f)),
        Triple("CUSTOM", "Custom Profile", Triple(11.0f, 12.8f, 0.25f))
    )

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
                    text = "Battery Profile & Automation",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
                Text(
                    text = "Active Profile: $selectedProfile",
                    fontSize = 12.sp,
                    color = ElectricGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Profiles Selection Grid
        Text(text = "Select Battery Chemistry & Preset", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.chunked(3).forEach { rowPresets ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPresets.forEach { (id, label, values) ->
                        val isSelected = selectedProfile == id || (id == "6V" && selectedProfile == "SYSTEM_6V") || (id == "5V" && selectedProfile == "SYSTEM_5V")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) NeonCyan else BorderDark,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedProfile = id
                                    if (id != "CUSTOM") {
                                        lowCut = values.first.toString()
                                        highCut = values.second.toString()
                                        hysteresis = values.third.toString()
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NeonCyan else TextPrimary,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${values.first}V - ${values.second}V",
                                    fontSize = 9.sp,
                                    color = if (isSelected) ElectricGreen else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                OutlinedTextField(
                    value = lowCut,
                    onValueChange = { lowCut = it },
                    label = { Text("Low Cutoff Voltage (V)", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = highCut,
                    onValueChange = { highCut = it },
                    label = { Text("High Cutoff Voltage (V)", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hysteresis,
                    onValueChange = { hysteresis = it },
                    label = { Text("Anti-Chatter Hysteresis (V)", color = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val l = (lowCut.toFloatOrNull() ?: state.config.lowCut).coerceIn(3.0f, 14.5f)
                val rawH = (highCut.toFloatOrNull() ?: state.config.highCut).coerceIn(4.0f, 15.0f)
                val h = if (rawH <= l) (l + 0.5f).coerceAtMost(15.0f) else rawH
                val hy = (hysteresis.toFloatOrNull() ?: state.config.hysteresis).coerceIn(0.1f, 1.0f)
                onSaveConfig(state.config.copy(lowCut = l, highCut = h, hysteresis = hy, batteryProfile = selectedProfile))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = DarkBackground)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SAVE BATTERY PROFILE",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBackground
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                    text = "Relay Safety & Hysteresis Logic",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• LOW CUT triggers charging to prevent deep discharge.\n" +
                            "• HIGH CUT disconnects charger to prevent electrolyte boiling.\n" +
                            "• Anti-Chatter: Relay enforces a minimum 15-second stabilization period before any state transition.\n" +
                            "• Emergency safety ceiling: Immediate cutoff at 15.5V.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
