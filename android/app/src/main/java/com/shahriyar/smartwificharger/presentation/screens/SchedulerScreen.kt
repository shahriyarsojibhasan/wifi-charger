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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import com.shahriyar.smartwificharger.presentation.components.DailyScheduleTaskCard
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
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

@Composable
fun SchedulerScreen(
    state: ChargerUiState,
    onSaveConfig: (ChargerConfig) -> Unit,
    onSaveSchedule: ((startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, daysBitmask: Int) -> Unit)? = null,
    onResetSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 12-Hour format conversion helpers
    fun to24Hour(hour12: Int, minute: Int, isPm: Boolean): Pair<Int, Int> {
        val clampedH = hour12.coerceIn(1, 12)
        val clampedM = minute.coerceIn(0, 59)
        val h24 = when {
            isPm && clampedH < 12 -> clampedH + 12
            !isPm && clampedH == 12 -> 0
            else -> clampedH
        }
        return Pair(h24, clampedM)
    }

    fun from24Hour(hour24: Int, minute24: Int): Triple<Int, Int, Boolean> {
        val h24 = hour24.coerceIn(0, 23)
        val isPm = h24 >= 12
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return Triple(h12, minute24.coerceIn(0, 59), isPm)
    }

    var enabled by remember(state.config.scheduleEnabled) { mutableStateOf(state.config.scheduleEnabled) }

    // Start Time (12-hour: 1-12, 0-59, AM/PM)
    val initStart = remember(state.config.chargeStartHour, state.config.chargeStartMinute) {
        from24Hour(state.config.chargeStartHour, state.config.chargeStartMinute)
    }
    var startHour12 by remember(initStart) { mutableIntStateOf(initStart.first) }
    var startMinute by remember(initStart) { mutableIntStateOf(initStart.second) }
    var startIsPm by remember(initStart) { mutableStateOf(initStart.third) }

    // Stop Time (12-hour: 1-12, 0-59, AM/PM)
    val initStop = remember(state.config.chargeStopHour, state.config.chargeStopMinute) {
        from24Hour(state.config.chargeStopHour, state.config.chargeStopMinute)
    }
    var stopHour12 by remember(initStop) { mutableIntStateOf(initStop.first) }
    var stopMinute by remember(initStop) { mutableIntStateOf(initStop.second) }
    var stopIsPm by remember(initStop) { mutableStateOf(initStop.third) }

    var daysBitmask by remember(state.config.scheduleDaysBitmask) {
        mutableIntStateOf(if (state.config.scheduleDaysBitmask == 0) 127 else state.config.scheduleDaysBitmask)
    }

    LaunchedEffect(state.config.updatedAt, state.config.scheduleEnabled) {
        enabled = state.config.scheduleEnabled
        val (sH, sM, sPm) = from24Hour(state.config.chargeStartHour, state.config.chargeStartMinute)
        startHour12 = sH
        startMinute = sM
        startIsPm = sPm
        val (eH, eM, ePm) = from24Hour(state.config.chargeStopHour, state.config.chargeStopMinute)
        stopHour12 = eH
        stopMinute = eM
        stopIsPm = ePm
        daysBitmask = if (state.config.scheduleDaysBitmask == 0) 127 else state.config.scheduleDaysBitmask
    }

    // Dynamic 24-hour equivalents and duration
    val (startH24, startM24) = to24Hour(startHour12, startMinute, startIsPm)
    val (stopH24, stopM24) = to24Hour(stopHour12, stopMinute, stopIsPm)

    val startTotalMins = startH24 * 60 + startM24
    val stopTotalMins = stopH24 * 60 + stopM24
    val durationMins = if (stopTotalMins >= startTotalMins) {
        stopTotalMins - startTotalMins
    } else {
        (1440 - startTotalMins) + stopTotalMins
    }
    val durHours = durationMins / 60
    val durMinutes = durationMins % 60
    val isOvernight = stopTotalMins < startTotalMins

    val startFormatted = String.format("%02d:%02d %s", startHour12, startMinute, if (startIsPm) "PM" else "AM")
    val stopFormatted = String.format("%02d:%02d %s", stopHour12, stopMinute, if (stopIsPm) "PM" else "AM")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Screen Header
        Text(
            text = "📅 Smart Charging Schedule",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Autonomous, power-cut persistent off-peak charging",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Master Schedule Switch Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(
                    1.5.dp,
                    if (enabled) ElectricGreen.copy(alpha = 0.8f) else BorderDark,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (enabled) ElectricGreen.copy(alpha = 0.15f) else SurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (enabled) ElectricGreen else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Schedule Charging Mode",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (enabled) "Active • Runs inside configured window" else "Disabled • Manual/Auto mode governs",
                            fontSize = 11.sp,
                            color = if (enabled) ElectricGreen else TextMuted
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { isChecked ->
                        enabled = isChecked
                        val effDays = if (daysBitmask == 0) 127 else daysBitmask
                        if (onSaveSchedule != null) {
                            onSaveSchedule.invoke(startH24, startM24, stopH24, stopM24, isChecked, effDays)
                        } else {
                            onSaveConfig(
                                state.config.copy(
                                    scheduleEnabled = isChecked,
                                    chargeStartHour = startH24,
                                    chargeStartMinute = startM24,
                                    chargeStopHour = stopH24,
                                    chargeStopMinute = stopM24,
                                    scheduleDaysBitmask = effDays
                                )
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ElectricGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val isBn = (com.shahriyar.smartwificharger.presentation.localization.LocalizationManager.currentLanguage == com.shahriyar.smartwificharger.presentation.localization.AppLanguage.BANGLA)

        // Daily Calendar Schedule Task System Card
        DailyScheduleTaskCard(
            isMcuOnline = state.isMcuOnline,
            scheduleTodayCompleted = state.telemetry.scheduleTodayCompleted,
            scheduleTodayDate = state.telemetry.scheduleTodayDate,
            scheduleTodayStatus = state.telemetry.scheduleTodayStatus,
            scheduleCompensating = state.telemetry.scheduleCompensating,
            scheduleRemainingSeconds = state.telemetry.scheduleRemainingSeconds,
            scheduleChargedSecs = state.telemetry.scheduleChargedSecs,
            scheduleTotalSecs = state.telemetry.scheduleTotalSecs,
            startHour = state.config.chargeStartHour,
            startMinute = state.config.chargeStartMinute,
            stopHour = state.config.chargeStopHour,
            stopMinute = state.config.chargeStopMinute,
            isBn = isBn,
            onResetSchedule = onResetSchedule
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Time Picker 1: START TIME (12-Hour)
        TimePicker12HourCard(
            title = "START CHARGING TIME",
            icon = Icons.Default.AccessTime,
            iconTint = NeonCyan,
            hour12 = startHour12,
            minute = startMinute,
            isPm = startIsPm,
            onHourChange = { startHour12 = it },
            onMinuteChange = { startMinute = it },
            onAmPmChange = { startIsPm = it },
            militaryPreview = String.format("%02d:%02d", startH24, startM24)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Time Picker 2: STOP TIME (12-Hour)
        TimePicker12HourCard(
            title = "STOP CHARGING TIME",
            icon = Icons.Default.AccessTime,
            iconTint = Color(0xFFFFB74D),
            hour12 = stopHour12,
            minute = stopMinute,
            isPm = stopIsPm,
            onHourChange = { stopHour12 = it },
            onMinuteChange = { stopMinute = it },
            onAmPmChange = { stopIsPm = it },
            militaryPreview = String.format("%02d:%02d", stopH24, stopM24)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Window Summary & Active Window Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Window: $startFormatted ➔ $stopFormatted",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${durHours}h ${durMinutes}m Total",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyan
                    )
                }
                if (isOvernight) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🌙 Overnight Window: Crosses midnight (e.g. late night to morning)",
                        fontSize = 11.sp,
                        color = Color(0xFFFFD54F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Presets
        Text(
            text = "⚡ Quick Schedule Presets",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetButton(
                title = "Off-Peak Night",
                subtitle = "12:00 AM - 06:00 AM",
                modifier = Modifier.weight(1f),
                onClick = {
                    startHour12 = 12; startMinute = 0; startIsPm = false // 00:00 AM
                    stopHour12 = 6; stopMinute = 0; stopIsPm = false     // 06:00 AM
                    enabled = true
                }
            )
            PresetButton(
                title = "Late Night",
                subtitle = "01:00 AM - 05:00 AM",
                modifier = Modifier.weight(1f),
                onClick = {
                    startHour12 = 1; startMinute = 0; startIsPm = false  // 01:00 AM
                    stopHour12 = 5; stopMinute = 0; stopIsPm = false     // 05:00 AM
                    enabled = true
                }
            )
            PresetButton(
                title = "Morning Routine",
                subtitle = "06:00 AM - 09:00 AM",
                modifier = Modifier.weight(1f),
                onClick = {
                    startHour12 = 6; startMinute = 0; startIsPm = false  // 06:00 AM
                    stopHour12 = 9; stopMinute = 0; stopIsPm = false     // 09:00 AM
                    enabled = true
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetButton(
                title = "Daytime Work",
                subtitle = "10:00 AM - 04:00 PM",
                modifier = Modifier.weight(1f),
                onClick = {
                    startHour12 = 10; startMinute = 0; startIsPm = false // 10:00 AM
                    stopHour12 = 4; stopMinute = 0; stopIsPm = true      // 04:00 PM
                    enabled = true
                }
            )
            PresetButton(
                title = "Evening Window",
                subtitle = "06:00 PM - 10:00 PM",
                modifier = Modifier.weight(1f),
                onClick = {
                    startHour12 = 6; startMinute = 0; startIsPm = true   // 06:00 PM
                    stopHour12 = 10; stopMinute = 0; stopIsPm = true     // 10:00 PM
                    enabled = true
                }
            )
            PresetButton(
                title = "Full Day 24h",
                subtitle = "12:00 AM - 11:59 PM",
                modifier = Modifier.weight(1f),
                onClick = {
                    startHour12 = 12; startMinute = 0; startIsPm = false // 00:00 AM
                    stopHour12 = 11; stopMinute = 59; stopIsPm = true    // 11:59 PM
                    enabled = true
                }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Active Days of the Week
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
                            text = "Active Days of Week",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (daysBitmask == 127) "All 7 Days Enabled" else "Custom Active Days",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    TextButton(
                        onClick = { daysBitmask = if (daysBitmask == 127) 0 else 127 }
                    ) {
                        Text(
                            text = if (daysBitmask == 127) "Deselect All" else "Select All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayNames.forEachIndexed { index, name ->
                        val isDaySelected = (daysBitmask and (1 shl index)) != 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDaySelected) NeonCyan else SurfaceDark)
                                .border(
                                    1.dp,
                                    if (isDaySelected) NeonCyan else BorderDark,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    daysBitmask = daysBitmask xor (1 shl index)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDaySelected) DarkBackground else TextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Power-cut & Reboot Persistence Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x1A00E676))
                .border(1.dp, ElectricGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = ElectricGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Power Loss Auto-Recovery Guarantee",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Config is stored in flash memory. If electricity goes out and returns, the charger checks the time and automatically turns on the relay if inside schedule hours until end time!",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Master Save Button
        Button(
            onClick = {
                val effectiveDays = if (daysBitmask == 0) 127 else daysBitmask
                if (onSaveSchedule != null) {
                    onSaveSchedule.invoke(
                        startH24, startM24, stopH24, stopM24,
                        enabled,
                        effectiveDays
                    )
                } else {
                    onSaveConfig(
                        state.config.copy(
                            scheduleEnabled = enabled,
                            chargeStartHour = startH24,
                            chargeStartMinute = startM24,
                            chargeStopHour = stopH24,
                            chargeStopMinute = stopM24,
                            scheduleDaysBitmask = effectiveDays
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SAVE SCHEDULE ($startFormatted - $stopFormatted)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

/**
 * High-precision 12-Hour Time Picker Card with Hour/Minute steppers and AM/PM segmented pills.
 */
@Composable
private fun TimePicker12HourCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    hour12: Int,
    minute: Int,
    isPm: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onAmPmChange: (Boolean) -> Unit,
    militaryPreview: String
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = iconTint
                    )
                }
                Text(
                    text = "$militaryPreview (24h)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hour Selector (1-12)
                NumberStepperBox(
                    label = "HOUR",
                    value = hour12,
                    minValue = 1,
                    maxValue = 12,
                    onValueChange = onHourChange,
                    modifier = Modifier.weight(1.2f)
                )

                Text(
                    text = ":",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Minute Selector (00-59)
                NumberStepperBox(
                    label = "MINUTE",
                    value = minute,
                    minValue = 0,
                    maxValue = 59,
                    padZero = true,
                    onValueChange = onMinuteChange,
                    modifier = Modifier.weight(1.2f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // AM / PM Segmented Toggle Buttons
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    // AM Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isPm) NeonCyan else Color.Transparent)
                            .clickable { onAmPmChange(false) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (!isPm) DarkBackground else TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // PM Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPm) NeonCyan else Color.Transparent)
                            .clickable { onAmPmChange(true) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPm) DarkBackground else TextMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stepper box with plus and minus buttons and clear numeric display.
 */
@Composable
private fun NumberStepperBox(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    padZero: Boolean = false,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val next = if (value - 1 < minValue) maxValue else value - 1
                    onValueChange(next)
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = if (padZero) String.format("%02d", value) else value.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    val next = if (value + 1 > maxValue) minValue else value + 1
                    onValueChange(next)
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Compact quick preset button.
 */
@Composable
private fun PresetButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
