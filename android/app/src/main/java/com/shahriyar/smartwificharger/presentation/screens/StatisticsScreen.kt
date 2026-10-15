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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.theme.TextSecondary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState
import java.util.Locale

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import com.shahriyar.smartwificharger.presentation.theme.CoralRed

@Composable
fun StatisticsScreen(
    state: ChargerUiState,
    onResetBilling: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedRangeTab by remember { mutableIntStateOf(0) }
    val rangeTabs = listOf("Today", "This Week", "This Month", "Custom Range")

    val kwh = when (selectedRangeTab) {
        0 -> state.billing.todayEstimatedKwh
        1 -> state.billing.weekEstimatedKwh
        2 -> state.billing.monthEstimatedKwh
        else -> state.billing.totalEstimatedKwh
    }

    val cost = when (selectedRangeTab) {
        0 -> state.billing.todayEstimatedBdt
        1 -> state.billing.weekEstimatedBdt
        2 -> state.billing.monthEstimatedBdt
        else -> state.billing.totalEstimatedBdt
    }

    val durationSecs = when (selectedRangeTab) {
        0 -> state.billing.todaySeconds
        else -> state.billing.totalSeconds
    }
    val hours = durationSecs / 3600
    val minutes = (durationSecs % 3600) / 60
    val formattedDuration = String.format(Locale.US, "%dh %02dm", hours, minutes)

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
                    text = "📊 Historical Statistics",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Energy consumption & session analytics",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mandatory Estimated Label / Hardware Invariant Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AmberAlert.copy(alpha = 0.12f))
                .border(1.dp, AmberAlert.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AmberAlert,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ALL METRICS ARE ESTIMATED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberAlert
                    )
                    Text(
                        text = "Calculated from ${state.config.chargerWatt.toInt()}W rating. No physical AC shunt/sensor is installed.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Range Selector Tabs
        TabRow(
            selectedTabIndex = selectedRangeTab,
            containerColor = SurfaceDark,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedRangeTab]),
                    color = NeonCyan
                )
            },
            modifier = Modifier.clip(RoundedCornerShape(10.dp))
        ) {
            rangeTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedRangeTab == index,
                    onClick = { selectedRangeTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedRangeTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedRangeTab == index) NeonCyan else TextMuted
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 KPI Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.Bolt,
                iconColor = NeonCyan,
                label = "TOTAL ENERGY",
                value = String.format(Locale.US, "%.3f", kwh),
                unit = "kWh (Est.)",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.Payments,
                iconColor = ElectricGreen,
                label = "ESTIMATED COST",
                value = String.format(Locale.US, "৳%.2f", cost),
                unit = "BDT (Est.)",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.Schedule,
                iconColor = Color(0xFF60A5FA),
                label = "CHARGING TIME",
                value = formattedDuration,
                unit = "Active Relay",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.TrendingUp,
                iconColor = Color(0xFFA78BFA),
                label = "TOTAL SESSIONS",
                value = "${state.billing.sessionCount}",
                unit = "Sessions Recorded",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Billing Category Breakdown (Section 12.4)
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
                    Text(
                        text = "Category Breakdown",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "ESTIMATED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberAlert,
                        modifier = Modifier
                            .background(AmberAlert.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                CategoryStatRow(
                    name = "Auto Mode",
                    cost = state.billing.autoCost,
                    badgeColor = NeonCyan
                )
                CategoryStatRow(
                    name = "Scheduled Hours",
                    cost = state.billing.scheduledCost,
                    badgeColor = Color(0xFF60A5FA)
                )
                CategoryStatRow(
                    name = "Manual Override",
                    cost = state.billing.manualCost,
                    badgeColor = ElectricGreen
                )
                CategoryStatRow(
                    name = "Weather / Solar Backup",
                    cost = state.billing.weatherCost,
                    badgeColor = AmberAlert
                )
                CategoryStatRow(
                    name = "Other Charging",
                    cost = 0.0f,
                    badgeColor = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Offline Session Sync Status (Section 12.5)
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
                    text = "Offline Sync & De-Duplication",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sessions recorded offline on the ESP8266 or phone are cached with unique UUIDs. When internet or local Wi-Fi reconnects, sessions sync once and are deduplicated automatically.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ElectricGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "De-duplication engine active (Room + Firebase)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clear Billing / Energy Counters Card
        var showResetConfirm by remember { mutableStateOf(false) }
        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("⚠️ Reset Billing & Energy Counters?", color = CoralRed, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "This will reset Today's and Total energy consumption (kWh) and estimated bill back to ৳0.00 across the app, MCU device, and Firebase cloud.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetConfirm = false
                            onResetBilling()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                    ) {
                        Text("RESET TO ZERO", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text("CANCEL", color = TextMuted)
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 30-Day Billing History (Circular LittleFS Rolling Archive)
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
                    Column {
                        Text(
                            text = "📅 30-Day Daily Bill History",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Text(
                            text = "Archived in MCU LittleFS (Auto-clears >30 days)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${state.dailyBills.size} / 30 Days",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (state.dailyBills.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No daily history archived yet. Records automatically save at midnight.",
                            fontSize = 11.5.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    val sdf = remember { java.text.SimpleDateFormat("dd MMM yyyy", Locale.US) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.dailyBills.reversed().forEach { bill ->
                            val dateStr = if (bill.dateEpoch > 0) sdf.format(java.util.Date(bill.dateEpoch * 1000L)) else "Previous Day"
                            val dHours = bill.chargingSeconds / 3600
                            val dMins = (bill.chargingSeconds % 3600) / 60
                            val durationFormatted = "${dHours}h ${dMins}m"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceDark)
                                    .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dateStr,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "৳${"%.2f".format(bill.costBdt)}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElectricGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Duration: $durationFormatted  |  Energy: ${"%.3f".format(bill.kwh)} kWh",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    if (bill.autoCost > 0 || bill.scheduleCost > 0 || bill.manualCost > 0 || bill.solarCost > 0) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (bill.autoCost > 0) {
                                                Text(text = "Auto: ৳${"%.1f".format(bill.autoCost)}", fontSize = 10.sp, color = NeonCyan)
                                            }
                                            if (bill.scheduleCost > 0) {
                                                Text(text = "Sched: ৳${"%.1f".format(bill.scheduleCost)}", fontSize = 10.sp, color = AmberAlert)
                                            }
                                            if (bill.manualCost > 0) {
                                                Text(text = "Manual: ৳${"%.1f".format(bill.manualCost)}", fontSize = 10.sp, color = TextMuted)
                                            }
                                            if (bill.solarCost > 0) {
                                                Text(text = "Weather: ৳${"%.1f".format(bill.solarCost)}", fontSize = 10.sp, color = ElectricGreen)
                                            }
                                        }
                                    }
                                }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset Billing & Energy Stats",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Zero out daily and lifetime kWh & BDT metrics",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, CoralRed)
                ) {
                    Text(text = "CLEAR STATS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = unit,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun CategoryStatRow(
    name: String,
    cost: Float,
    badgeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = name, fontSize = 13.sp, color = TextSecondary)
        }
        Text(
            text = String.format(Locale.US, "৳%.2f (Est.)", cost),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
