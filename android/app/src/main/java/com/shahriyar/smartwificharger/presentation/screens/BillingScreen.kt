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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.theme.TextSecondary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState
import java.util.Locale

@Composable
fun BillingScreen(
    state: ChargerUiState,
    onSaveConfig: (ChargerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var wattText by remember(state.config.chargerWatt) { mutableStateOf(state.config.chargerWatt.toInt().toString()) }
    var ampsText by remember(state.config.chargerAmps) { mutableStateOf(state.config.chargerAmps.toString()) }
    var rateText by remember(state.config.kwhRateBdt) { mutableStateOf(state.config.kwhRateBdt.toString()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Today", "This Week", "This Month", "All Time")

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
                Text(
                    text = "৳ Estimated Electricity Billing",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Accurate math model (P = V × I, Wh = V × I × t)",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Invariant Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AmberAlert.copy(alpha = 0.12f))
                .border(1.dp, AmberAlert.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "⚠ HARDWARE INVARIANT: ESTIMATED ENERGY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberAlert,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Hardware currently lacks an AC current sensor. All power (W), energy (kWh), and cost (৳ BDT) metrics are mathematically ESTIMATED.",
                    fontSize = 11.sp,
                    color = TextPrimary,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab Selector: Today / This Week / This Month / All Time
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceCard,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = NeonCyan
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) NeonCyan else TextMuted
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Metrics Summary Card based on selected tab
        val (kwhVal, costVal, durationStr) = when (selectedTab) {
            0 -> Triple(state.billing.todayEstimatedKwh, state.billing.todayEstimatedBdt, "${state.billing.todaySeconds / 60}m")
            1 -> Triple(state.billing.weekEstimatedKwh, state.billing.weekEstimatedBdt, "${state.billing.todaySeconds * 7 / 3600}h")
            2 -> Triple(state.billing.monthEstimatedKwh, state.billing.monthEstimatedBdt, "${state.billing.totalSeconds / 3600}h")
            else -> Triple(state.billing.totalEstimatedKwh, state.billing.totalEstimatedBdt, "${state.billing.totalSeconds / 3600}h")
        }

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
                    text = "${tabs[selectedTab]} Billing Overview",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "Total Estimated Energy", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "%.3f kWh", kwhVal),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricGreen
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Total Estimated Cost", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "৳ %.2f", costVal),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberAlert
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Total Charging Duration", fontSize = 12.sp, color = TextMuted)
                    Text(text = durationStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Categorized Breakdown Card
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
                    text = "Charging Category Cost Breakdown",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(12.dp))
                BillingRow(label = "Smart Auto Low-Cut Cost", cost = state.billing.autoCost)
                BillingRow(label = "Manual Mode Cost", cost = state.billing.manualCost)
                BillingRow(label = "Scheduled Charging Cost", cost = state.billing.scheduledCost)
                BillingRow(label = "Countdown Timer Cost", cost = state.billing.timerCost)
                BillingRow(label = "Weather Backup Cost", cost = state.billing.weatherCost)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Charger Specs Input Card
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
                    text = "Estimation Parameters",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = wattText,
                    onValueChange = { wattText = it },
                    label = { Text("Charger Rating (Watts, default 100W)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = ampsText,
                    onValueChange = { ampsText = it },
                    label = { Text("Estimated Current (Amps, default 1.7A)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Electricity Price per kWh (৳ BDT, default 10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val w = wattText.toFloatOrNull() ?: 100.0f
                        val a = ampsText.toFloatOrNull() ?: 1.7f
                        val r = rateText.toFloatOrNull() ?: 10.0f
                        onSaveConfig(
                            state.config.copy(
                                chargerWatt = w,
                                chargerAmps = a,
                                kwhRateBdt = r
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text(
                        text = "UPDATE ESTIMATION PARAMETERS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BillingRow(label: String, cost: Float? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = String.format(Locale.US, "৳ %.2f", cost ?: 0f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
    }
}
