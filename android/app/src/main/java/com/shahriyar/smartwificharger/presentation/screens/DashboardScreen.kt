package com.shahriyar.smartwificharger.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.RelayDiagnosticStatus
import com.shahriyar.smartwificharger.domain.model.SensorDiagnosticStatus
import com.shahriyar.smartwificharger.presentation.components.DailyScheduleTaskCard
import com.shahriyar.smartwificharger.presentation.components.EstimatedCostCard
import com.shahriyar.smartwificharger.presentation.components.FaultBanner
import com.shahriyar.smartwificharger.presentation.components.QuickTimerRow
import com.shahriyar.smartwificharger.presentation.components.VoltageArcGauge
import com.shahriyar.smartwificharger.presentation.localization.AppLanguage
import com.shahriyar.smartwificharger.presentation.localization.LocalizationManager
import com.shahriyar.smartwificharger.presentation.theme.AmberAlert
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.CoralRed
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.theme.TextSecondary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState
import com.shahriyar.smartwificharger.presentation.viewmodel.RelaySwitchState
import java.util.Locale

@Composable
fun DashboardScreen(
    state: ChargerUiState,
    onToggleRelay: () -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onSetTimer: (Int) -> Unit,
    onResetSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isRelayOn = state.telemetry.relayState
    val strings = LocalizationManager.strings
    val isBn = (LocalizationManager.currentLanguage == AppLanguage.BANGLA)

    // Pulse animation for active charging state
    val infiniteTransition = rememberInfiniteTransition(label = "chargingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRelayOn) 1.03f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Format session duration HH:MM:SS
    val durationSeconds = state.billing.currentSessionSeconds
    val hours = durationSeconds / 3600
    val minutes = (durationSeconds % 3600) / 60
    val seconds = durationSeconds % 60
    val formattedDuration = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    // Check battery profile mismatch condition
    val isMismatch = state.fault.isProfileMismatch || state.telemetry.isProfileMismatch ||
            state.fault.sensorStatus == SensorDiagnosticStatus.PROFILE_MISMATCH

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Battery Profile Mismatch Alert Card (Clear, actionable, no false "sensor unreachable")
        if (isMismatch) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmberAlert.copy(alpha = 0.15f))
                    .border(1.5.dp, AmberAlert, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = AmberAlert,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.profileMismatchTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAlert
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBn)
                                "সংযুক্ত ব্যাটারির ভোল্টেজ (${"%.2f".format(state.telemetry.batteryVoltage)}V) বর্তমান প্রোফাইলের (${state.config.batteryProfile}) চেয়ে কম। সঠিক চার্জিং নিশ্চিত করতে ড্রয়ার থেকে ৫V বা ৬V প্রোফাইল নির্বাচন করুন।"
                            else
                                "Connected battery (${"%.2f".format(state.telemetry.batteryVoltage)}V) is lower than ${state.config.batteryProfile} minimum. Switch battery profile in Settings.",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // MCU Hardware Offline Alert Banner
        if (!state.isMcuOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FF5252))
                    .border(1.dp, CoralRed, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CoralRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isBn) "⚠️ ডিভাইস অফলাইন / ডিসকানেক্টেড" else "⚠️ Device Offline / Disconnected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralRed
                        )
                        val elapsedSec = (System.currentTimeMillis() - state.telemetry.lastUpdatedEpoch) / 1000
                        val lastSeenText = if (state.telemetry.lastUpdatedEpoch > 0L && elapsedSec >= 0) {
                            val timeStr = when {
                                elapsedSec < 60 -> "${elapsedSec}s ago"
                                elapsedSec < 3600 -> "${elapsedSec / 60}m ago"
                                else -> "${elapsedSec / 3600}h ago"
                            }
                            if (isBn) "সর্বশেষ আপডেট: $timeStr • ক্যাশের পরিবর্তে লাইভ ডেটা '--' / N/A প্রদর্শিত হচ্ছে" else "Last seen: $timeStr • Live telemetry shown as '--' / N/A"
                        } else {
                            if (isBn) "ডিভাইস সংযোগের জন্য অপেক্ষা করা হচ্ছে..." else "Waiting for device reconnect..."
                        }
                        Text(
                            text = lastSeenText,
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Fault and Diagnostic Banners (Only when truly disconnected and not a profile mismatch)
        if (state.fault.isSensorDisconnected && !isMismatch) {
            FaultBanner(message = if (isBn) "ব্যাটারি সংযোগ পাওয়া যায়নি! ব্যাটারি ক্লিপ দৃঢ়ভাবে যুক্ত করুন।" else "No Battery Detected! Please attach battery clips firmly.")
            Spacer(modifier = Modifier.height(10.dp))
        } else if (state.fault.isOverVoltage) {
            FaultBanner(message = if (isBn) "জরুরি ওভার-ভোল্টেজ কাটঅফ সক্রিয়! ভোল্টেজ ১৫.৫V এর বেশি।" else "Emergency Over-Voltage cutoff triggered! Battery >= 15.5V.")
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (state.telemetry.relayDiagnostic == RelayDiagnosticStatus.NO_ELECTRICAL_RESPONSE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x26FF5252))
                    .border(1.dp, CoralRed, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CoralRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "রিলে ডায়াগনস্টিক: কোনো বৈদ্যুতিক প্রতিক্রিয়া পাওয়া যায়নি। রিলে বা চার্জারের তার পরীক্ষা করুন।"
                        else "Relay Diagnostic: No electrical response detected. Check relay or wiring.",
                        fontSize = 12.sp,
                        color = CoralRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Circular Voltage Gauge with live voltage display & charging aura
        VoltageArcGauge(
            voltage = state.telemetry.batteryVoltage,
            isSensorDisconnected = state.telemetry.sensorDisconnected && !isMismatch,
            isCharging = isRelayOn,
            chargingTime = formattedDuration,
            lowCut = state.config.lowCut,
            highCut = state.config.highCut,
            isMcuOnline = state.isMcuOnline
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Master Charger Power Section with Clear Status and High-Visibility Action Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCard)
                .border(
                    1.5.dp,
                    if (isRelayOn) ElectricGreen.copy(alpha = 0.8f) else BorderDark,
                    RoundedCornerShape(18.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                // Prominent Status Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (!state.isMcuOnline) CoralRed else if (isRelayOn) ElectricGreen else Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (!state.isMcuOnline) (if (isBn) "⚠️ ডিভাইস অফলাইন (N/A)" else "⚠️ MCU OFFLINE (N/A)")
                            else if (isRelayOn) (if (isBn) "⚡ চার্জিং চালু আছে" else "⚡ CHARGER POWER: ACTIVE")
                            else (if (isBn) "⚪ স্ট্যান্ডবাই (বন্ধ)" else "⚪ CHARGER POWER: STANDBY"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (!state.isMcuOnline) CoralRed else if (isRelayOn) ElectricGreen else TextSecondary
                        )
                    }
                    Text(
                        text = if (!state.isMcuOnline) "--" else if (isRelayOn) (if (isBn) "রিলে অন" else "RELAY ON") else (if (isBn) "রিলে অফ" else "RELAY OFF"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!state.isMcuOnline) CoralRed else if (isRelayOn) ElectricGreen else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Multi-phase Relay Control Button with authoritative ACK progression
                val (btnColor, btnText, isBusy) = when {
                    !state.isMcuOnline -> Triple(Color(0xFF1E293B), if (isBn) "ডিভাইস অফলাইন (N/A)" else "DEVICE OFFLINE (N/A)", false)
                    state.relaySwitchState == RelaySwitchState.TURNING_ON -> Triple(Color(0xFF334155), if (isBn) "অন হচ্ছে..." else "TURNING ON...", true)
                    state.relaySwitchState == RelaySwitchState.TURNING_OFF -> Triple(Color(0xFF334155), if (isBn) "বন্ধ হচ্ছে..." else "TURNING OFF...", true)
                    state.relaySwitchState == RelaySwitchState.ERROR -> Triple(AmberAlert, if (isBn) "কমান্ড ব্যর্থ হয়েছে" else "COMMAND FAILED", false)
                    state.relaySwitchState == RelaySwitchState.ON -> Triple(CoralRed, strings.stopCharging, false)
                    state.relaySwitchState == RelaySwitchState.OFF -> Triple(ElectricGreen, strings.startCharging, false)
                    else -> Triple(Color(0xFF334155), if (isBn) "অবস্থা অজানা" else "RELAY STATE UNKNOWN", false)
                }

                Button(
                    onClick = onToggleRelay,
                    enabled = state.isMcuOnline && !isBusy && !state.isSwitchingRelay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = btnColor
                    )
                ) {
                    if (isBusy || state.isSwitchingRelay) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = NeonCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = btnText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (!state.isMcuOnline) Icons.Default.Warning else if (isRelayOn) Icons.Default.PowerSettingsNew else Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (!state.isMcuOnline) TextMuted else if (isRelayOn || state.relaySwitchState == RelaySwitchState.ERROR) Color.White else DarkBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = btnText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (!state.isMcuOnline) TextMuted else if (isRelayOn || state.relaySwitchState == RelaySwitchState.ERROR) Color.White else DarkBackground
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Safety Quick Countdown Timer (Prominently placed right under Manual ON/OFF)
        QuickTimerRow(
            timerActive = state.isMcuOnline && state.telemetry.timerActive,
            remainingSeconds = if (state.isMcuOnline) state.telemetry.timerRemainingSeconds else 0,
            onSetTimer = onSetTimer
        )

        Spacer(modifier = Modifier.height(14.dp))

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

        // Live Charging Telemetry Card (Pulsing when charging)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(if (state.isMcuOnline && isRelayOn) pulseScale else 1.0f)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceCard)
                .border(
                    1.dp,
                    if (state.isMcuOnline && isRelayOn) ElectricGreen.copy(alpha = 0.6f) else BorderDark,
                    RoundedCornerShape(18.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!state.isMcuOnline) (if (isBn) "মেট্রিক্স অনুপলব্ধ (অফলাইন)" else "METRICS UNAVAILABLE (OFFLINE)")
                        else if (isRelayOn) (if (isBn) "⚡ লাইভ চার্জিং মেট্রিক্স" else "⚡ ACTIVE CHARGING METRICS")
                        else (if (isBn) "স্ট্যান্ডবাই মেট্রিক্স" else "STANDBY METRICS"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (!state.isMcuOnline) CoralRed else if (isRelayOn) ElectricGreen else TextMuted
                    )
                    Text(
                        text = (if (isBn) "মোড: " else "Mode: ") + if (!state.isMcuOnline) "N/A" else if (state.telemetry.autoMode) "AUTO" else "MANUAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isMcuOnline) NeonCyan else TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = if (isBn) "সেন্সরের অবস্থা" else "Sensor Status", fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (!state.isMcuOnline) "--" else when (state.telemetry.sensorStatus) {
                                SensorDiagnosticStatus.VOLTAGE_SENSOR_OK -> if (isBn) "সেন্সর সঠিক আছে" else "SENSOR OK"
                                SensorDiagnosticStatus.VOLTAGE_SENSOR_WARNING -> if (isBn) "সেন্সর সতর্কতা" else "SENSOR WARNING"
                                SensorDiagnosticStatus.VOLTAGE_SENSOR_ERROR -> if (isBn) "সেন্সর ত্রুটি" else "SENSOR ERROR"
                                SensorDiagnosticStatus.PROFILE_MISMATCH -> if (isBn) "প্রোফাইল অমিল" else "PROFILE MISMATCH"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!state.isMcuOnline) TextMuted else when (state.telemetry.sensorStatus) {
                                SensorDiagnosticStatus.VOLTAGE_SENSOR_OK -> ElectricGreen
                                SensorDiagnosticStatus.VOLTAGE_SENSOR_WARNING -> AmberAlert
                                SensorDiagnosticStatus.VOLTAGE_SENSOR_ERROR -> CoralRed
                                SensorDiagnosticStatus.PROFILE_MISMATCH -> AmberAlert
                            }
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = strings.current, fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (!state.isMcuOnline) "-- A" else if (isRelayOn) "${state.config.chargerAmps} A" else "0.0 A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!state.isMcuOnline) TextMuted else if (isRelayOn) ElectricGreen else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = strings.sessionTime, fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (!state.isMcuOnline) "--:--:--" else formattedDuration,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!state.isMcuOnline) TextMuted else TextPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = strings.todayEnergy, fontSize = 11.sp, color = TextMuted)
                        Text(
                            text = if (!state.isMcuOnline) "-- kWh" else String.format(Locale.US, "%.3f kWh", state.billing.todayEstimatedKwh),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!state.isMcuOnline) TextMuted else NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.todayCost,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                    Text(
                        text = if (!state.isMcuOnline) "--" else String.format(Locale.US, "৳ %.2f", state.billing.todayEstimatedBdt),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (!state.isMcuOnline) TextMuted else AmberAlert
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Control Panel Card (Smart Auto Charging, Schedule, Manual Lock)
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
                            text = strings.autoCharging,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = (if (isBn) "কাটঅফ লিমিট: " else "Cutoff Limits: ") + "${state.config.lowCut}V - ${state.config.highCut}V",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = state.isMcuOnline && state.telemetry.autoMode,
                        enabled = state.isMcuOnline,
                        onCheckedChange = onToggleAuto,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val format12: (Int, Int) -> String = { h, m ->
                        val isPm = h >= 12
                        val h12 = when {
                            h == 0 -> 12
                            h > 12 -> h - 12
                            else -> h
                        }
                        String.format("%02d:%02d %s", h12, m, if (isPm) "PM" else "AM")
                    }
                    Text(text = if (isBn) "চার্জিং শিডিউল" else "Charging Schedule", fontSize = 13.sp, color = TextMuted)
                    Text(
                        text = if (!state.isMcuOnline) "--" else if (state.config.scheduleEnabled) {
                            "${format12(state.config.chargeStartHour, state.config.chargeStartMinute)} - ${format12(state.config.chargeStopHour, state.config.chargeStopMinute)}"
                        } else {
                            if (isBn) "বন্ধ আছে" else "Disabled"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.config.scheduleEnabled) NeonCyan else TextMuted
                    )
                }

                if (state.telemetry.manualLockSecs > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = if (isBn) "ম্যানুয়াল লক সময়" else "Manual Override Lock", fontSize = 12.sp, color = TextMuted)
                        Text(
                            text = "${state.telemetry.manualLockSecs}s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricGreen
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Estimated Cost & Energy Overview Card
        EstimatedCostCard(stats = state.billing)

        Spacer(modifier = Modifier.height(24.dp))
    }
}
