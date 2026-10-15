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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.ChargingMode
import com.shahriyar.smartwificharger.presentation.components.ConnectionBadge
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState

@Composable
fun ChargingScreen(
    state: ChargerUiState,
    onToggleAuto: (Boolean) -> Unit,
    onToggleSchedule: (Boolean) -> Unit = {},
    onToggleSolar: (Boolean) -> Unit = {},
    onToggleRelay: () -> Unit = {},
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
                    text = "Charging Modes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
                Text(
                    text = "Autonomous firmware enforcement rules",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(16.dp))

        ChargingModeItem(
            icon = Icons.Default.SettingsSuggest,
            title = "SMART AUTO MODE (WITH RECOVERY)",
            description = "Autonomous voltage window management: automatically triggers charging at ${state.config.lowCut}V (including emergency low battery recovery) and safely disconnects at ${state.config.highCut}V.",
            enabled = state.telemetry.autoMode,
            onToggle = onToggleAuto
        )

        Spacer(modifier = Modifier.height(12.dp))

        val format12 = { h24: Int, m: Int ->
            val isPm = h24 >= 12
            val h12 = when {
                h24 == 0 -> 12
                h24 > 12 -> h24 - 12
                else -> h24
            }
            String.format("%02d:%02d %s", h12, m, if (isPm) "PM" else "AM")
        }
        val start12 = format12(state.config.chargeStartHour, state.config.chargeStartMinute)
        val stop12 = format12(state.config.chargeStopHour, state.config.chargeStopMinute)

        ChargingModeItem(
            icon = Icons.Default.CalendarMonth,
            title = "SCHEDULE CHARGING MODE",
            description = "Enforces scheduled charging between $start12 and $stop12 on selected active days.",
            enabled = state.config.scheduleEnabled,
            onToggle = onToggleSchedule
        )

        Spacer(modifier = Modifier.height(12.dp))

        ChargingModeItem(
            icon = Icons.Default.WbSunny,
            title = "SOLAR & WEATHER AUTOMATION",
            description = "Suppresses AC grid charger during daylight hours (09:00 - 16:00) for solar panels. Automatically switches on AC grid backup if cloud cover exceeds 60% (Current Cloud Cover: ${state.cloudCoverPercent ?: 0}%).",
            enabled = state.config.solarBackupEnabled,
            onToggle = onToggleSolar
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = ElectricGreen, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "MANUAL CONTROL & BILLING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricGreen
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "When you turn the charger ON/OFF manually from Dashboard, the system runs in Manual mode. Energy and electric bill for all manual charging are accurately calculated and separated in Statistics.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ChargingModeItem(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(
                1.dp,
                if (enabled) ElectricGreen.copy(alpha = 0.4f) else BorderDark,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (enabled) ElectricGreen.copy(alpha = 0.15f) else DarkBackground)
                        .padding(8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) ElectricGreen else TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonCyan,
                    checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                )
            )
        }
    }
}
