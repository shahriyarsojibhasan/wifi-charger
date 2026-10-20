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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.domain.model.DailyBill
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
import java.text.SimpleDateFormat
import java.util.Date
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

    // 30-Day Dropdown selection state
    var selectedBillIndex by remember { mutableIntStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var showAllRecords by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "৳ Electricity Billing",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Accurate math model (P = V × I, Wh = V × I × t)",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Timeframe Selector: Today / This Week / This Month / All Time
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
                    text = "${tabs[selectedTab]} Overview",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = "Estimated Energy", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "%.3f kWh", kwhVal),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricGreen
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Estimated Cost", fontSize = 11.sp, color = TextMuted)
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
                    Text(text = "Charging Duration", fontSize = 12.sp, color = TextMuted)
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
                    text = "Mode-Specific Cost Breakdown",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                BillingRow(label = "Smart Auto Low-Cut", cost = state.billing.autoCost)
                BillingRow(label = "Scheduled Charging", cost = state.billing.scheduledCost)
                BillingRow(label = "Manual Mode", cost = state.billing.manualCost)
                BillingRow(label = "Countdown Timer", cost = state.billing.timerCost)
                BillingRow(label = "Weather Backup", cost = state.billing.weatherCost)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ══════════════════════════════════════════════════════════════════════
        // 30-DAY BILLING HISTORY (READ FROM FIREBASE RTDB WITH DROPDOWN SELECTOR)
        // ══════════════════════════════════════════════════════════════════════
        val reversedBills = remember(state.dailyBills) { state.dailyBills.reversed() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeonCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "30-Day Bill History",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${state.dailyBills.size} Days (Cloud)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (reversedBills.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No 30-day archive recorded yet. Daily records automatically save at midnight.",
                            fontSize = 11.5.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    // Safe index clamping
                    val safeIdx = selectedBillIndex.coerceIn(0, reversedBills.size - 1)
                    val selectedBill = reversedBills[safeIdx]
                    val selectedDateStr = if (selectedBill.dateEpoch > 0) {
                        dateFormat.format(Date(selectedBill.dateEpoch * 1000L))
                    } else "Recent Day #$safeIdx"

                    // Dropdown Trigger Field
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable { dropdownExpanded = !dropdownExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$selectedDateStr • ৳${String.format(Locale.US, "%.2f", selectedBill.costBdt)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                            Icon(
                                imageVector = if (dropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = NeonCyan
                            )
                        }

                        // Dropdown Menu List
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .background(SurfaceDark)
                                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                        ) {
                            reversedBills.forEachIndexed { idx, bill ->
                                val dateLabel = if (bill.dateEpoch > 0) {
                                    dateFormat.format(Date(bill.dateEpoch * 1000L))
                                } else "Day #${reversedBills.size - idx}"

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dateLabel,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (idx == safeIdx) FontWeight.Bold else FontWeight.Normal,
                                                color = if (idx == safeIdx) NeonCyan else TextPrimary
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = "৳${String.format(Locale.US, "%.2f", bill.costBdt)}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElectricGreen
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedBillIndex = idx
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Selected Day Detailed Breakdown Card
                    DailyBillDetailCard(bill = selectedBill, dateStr = selectedDateStr)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Expand / Collapse Full 30-Day List
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllRecords = !showAllRecords }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showAllRecords) "Hide Full 30-Day List ▲" else "View All ${reversedBills.size} Days ▼",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }

                    if (showAllRecords) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            reversedBills.forEachIndexed { i, bill ->
                                val dStr = if (bill.dateEpoch > 0) dateFormat.format(Date(bill.dateEpoch * 1000L)) else "Day #${reversedBills.size - i}"
                                val dHours = bill.chargingSeconds / 3600
                                val dMins = (bill.chargingSeconds % 3600) / 60

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceDark)
                                        .border(1.dp, if (i == safeIdx) NeonCyan.copy(alpha = 0.5f) else BorderDark, RoundedCornerShape(8.dp))
                                        .clickable { selectedBillIndex = i }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = dStr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Text(text = "${dHours}h ${dMins}m • ${String.format(Locale.US, "%.3f kWh", bill.kwh)}", fontSize = 10.5.sp, color = TextMuted)
                                        }
                                        Text(
                                            text = "৳${String.format(Locale.US, "%.2f", bill.costBdt)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElectricGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Estimation Parameters Config Card
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
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

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
                Spacer(modifier = Modifier.height(8.dp))

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
                Spacer(modifier = Modifier.height(8.dp))

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
                Spacer(modifier = Modifier.height(12.dp))

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
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text(
                        text = "SAVE ESTIMATION PARAMETERS",
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
private fun DailyBillDetailCard(bill: DailyBill, dateStr: String) {
    val dHours = bill.chargingSeconds / 3600
    val dMins = (bill.chargingSeconds % 3600) / 60

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = dateStr, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Duration: ${dHours}h ${dMins}m", fontSize = 11.sp, color = TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "৳ ${String.format(Locale.US, "%.2f", bill.costBdt)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElectricGreen
                    )
                    Text(
                        text = String.format(Locale.US, "%.3f kWh", bill.kwh),
                        fontSize = 11.sp,
                        color = NeonCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-breakdowns
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "• Auto Low-Cut", fontSize = 11.sp, color = TextSecondary)
                Text(text = "৳${String.format(Locale.US, "%.2f", bill.autoCost)}", fontSize = 11.sp, color = TextPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "• Scheduled", fontSize = 11.sp, color = TextSecondary)
                Text(text = "৳${String.format(Locale.US, "%.2f", bill.scheduleCost)}", fontSize = 11.sp, color = TextPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "• Manual Mode", fontSize = 11.sp, color = TextSecondary)
                Text(text = "৳${String.format(Locale.US, "%.2f", bill.manualCost)}", fontSize = 11.sp, color = TextPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "• Weather Backup", fontSize = 11.sp, color = TextSecondary)
                Text(text = "৳${String.format(Locale.US, "%.2f", bill.solarCost)}", fontSize = 11.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun BillingRow(label: String, cost: Float? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = TextSecondary)
        Text(text = String.format(Locale.US, "৳ %.2f", cost ?: 0f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
    }
}
