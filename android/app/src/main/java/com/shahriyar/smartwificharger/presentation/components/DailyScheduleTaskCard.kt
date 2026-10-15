package com.shahriyar.smartwificharger.presentation.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.shahriyar.smartwificharger.presentation.theme.AmberAlert
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DailyScheduleTaskCard(
    isMcuOnline: Boolean,
    scheduleTodayCompleted: Boolean,
    scheduleTodayDate: Int,
    scheduleTodayStatus: String,
    scheduleCompensating: Boolean,
    scheduleRemainingSeconds: Int,
    scheduleChargedSecs: Int,
    scheduleTotalSecs: Int,
    startHour: Int,
    startMinute: Int,
    stopHour: Int,
    stopMinute: Int,
    isBn: Boolean,
    onResetSchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Pulse animation for active compensating/charging
    val infiniteTransition = rememberInfiniteTransition(label = "taskPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Format calendar date
    val formattedDate = formatCalendarDateString(scheduleTodayDate, isBn)

    // Determine normalized status
    val isCompleted = scheduleTodayCompleted || scheduleTodayStatus == "COMPLETED"
    val isCompensating = (scheduleCompensating || scheduleTodayStatus == "COMPENSATING") && !isCompleted
    val isInProgress = scheduleTodayStatus == "IN_PROGRESS" && !isCompleted
    val isPending = scheduleTodayStatus == "PENDING" && !isCompleted
    val isDisabled = scheduleTodayStatus == "DISABLED" || scheduleTodayStatus == "NOT_SCHEDULED_TODAY"

    val statusColor = when {
        !isMcuOnline -> TextMuted
        isCompleted -> ElectricGreen
        isCompensating -> AmberAlert
        isInProgress -> NeonCyan
        isPending -> Color(0xFF818CF8)
        else -> TextSecondary
    }

    val statusBg = statusColor.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .border(
                width = 1.2.dp,
                color = if (isMcuOnline && (isCompensating || isInProgress)) statusColor.copy(alpha = pulseAlpha) else BorderDark,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            // Header Row: Calendar Date & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isBn) "আজকের শিডিউল টাস্ক" else "DAILY SCHEDULE TASK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = formattedDate,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                // Status Badge Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusBg)
                        .border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ElectricGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (isCompensating) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = AmberAlert,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (isInProgress) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (isPending) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Text(
                            text = when {
                                !isMcuOnline -> if (isBn) "অফলাইন (N/A)" else "OFFLINE (N/A)"
                                isCompleted -> if (isBn) "টাস্ক সম্পন্ন (১০০%)" else "COMPLETED (100%)"
                                isCompensating -> if (isBn) "পাওয়ার রিকভারি" else "RECOVERY ACTIVE"
                                isInProgress -> if (isBn) "চার্জিং চলমান" else "IN PROGRESS"
                                isPending -> if (isBn) "অপেক্ষমান" else "PENDING"
                                else -> if (isBn) "নিষ্ক্রিয়" else "INACTIVE"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Content based on status
            if (!isMcuOnline) {
                Text(
                    text = if (isBn) "কন্ট্রোলার অফলাইনে রয়েছে। লাইভ শিডিউল টাস্ক স্ট্যাটাস উপলব্ধ নেই।"
                    else "Charger controller is offline. Live schedule task status is unavailable.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            } else if (isCompleted) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ElectricGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isBn) "আজকের শিডিউল চার্জিং সফলভাবে সম্পন্ন হয়েছে!"
                                else "Today's scheduled charging session is 100% completed!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricGreen
                            )
                            val chargedMins = (scheduleChargedSecs + 59) / 60
                            val totalMins = (scheduleTotalSecs + 59) / 60
                            Text(
                                text = if (isBn) "ব্যাটারি ফুল অথবা নির্ধারিত সময় পূরণ হয়েছে ($chargedMins মিনিট চার্জ সম্পন্ন)।"
                                else "Battery full or configured duration reached ($chargedMins min charged).",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (onResetSchedule != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onResetSchedule,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricGreen.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ElectricGreen.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = ElectricGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBn) "আজকের শিডিউল রিসেট করুন (আবার চালু করতে)" else "Reset Today's Schedule (Run Again)",
                                color = ElectricGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (isCompensating) {
                val scSec = scheduleRemainingSeconds
                val scMins = (scSec + 59) / 60
                val totalMins = (scheduleTotalSecs + 59) / 60
                val chargedMins = (scheduleChargedSecs + 59) / 60

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = AmberAlert,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isBn) "⚡ লোডশেডিং পাওয়ার রিকভারি চলছে"
                                else "⚡ Power Recovery: Compensating Missed Schedule",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberAlert
                            )
                            Text(
                                text = if (isBn) "বিদ্যুৎ ফিরে এসেছে! অপূর্ণ $scMins মিনিট (${scSec}s) চার্জ সম্পন্ন করা হচ্ছে।"
                                else "Electricity returned! Continuing charge for remaining $scMins min (${scSec}s).",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val progress = if (scheduleTotalSecs > 0) {
                        (scheduleChargedSecs.toFloat() / scheduleTotalSecs.toFloat()).coerceIn(0.05f, 1.0f)
                    } else 0.5f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AmberAlert,
                        trackColor = SurfaceDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBn) "চার্জ হয়েছে: $chargedMins মিনিট" else "Charged: $chargedMins min",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = if (isBn) "অবশিষ্ট: $scMins মিনিট (${scSec}s)" else "Remaining: $scMins min (${scSec}s)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberAlert
                        )
                    }
                }
            } else if (isInProgress) {
                val scSec = scheduleRemainingSeconds
                val scMins = (scSec + 59) / 60
                val totalMins = (scheduleTotalSecs + 59) / 60
                val chargedMins = (scheduleChargedSecs + 59) / 60

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isBn) "শিডিউল উইন্ডোতে চার্জিং চলমান"
                                else "Charging Inside Today's Schedule Window",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(
                                text = if (isBn) "অবশিষ্ট সময়: $scMins মিনিট (${scSec}s)। নির্ধারিত সময় পূরণ হলে অটো বন্ধ হবে।"
                                else "Remaining time: $scMins min (${scSec}s). Auto cutoff when duration fulfilled.",
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val progress = if (scheduleTotalSecs > 0) {
                        (scheduleChargedSecs.toFloat() / scheduleTotalSecs.toFloat()).coerceIn(0.05f, 1.0f)
                    } else 0.5f

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan,
                        trackColor = SurfaceDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBn) "চার্জ হয়েছে: $chargedMins মিনিট" else "Charged: $chargedMins min",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = if (isBn) "অবশিষ্ট: $scMins মিনিট (${scSec}s)" else "Remaining: $scMins min (${scSec}s)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }
            } else if (isPending) {
                val startStr = String.format("%02d:%02d", startHour, startMinute)
                val stopStr = String.format("%02d:%02d", stopHour, stopMinute)
                val durMins = (stopHour * 60 + stopMinute) - (startHour * 60 + startMinute)
                val totalMins = if (durMins > 0) durMins else (1440 + durMins)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isBn) "আজকের শিডিউল অপেক্ষমান ($startStr - $stopStr)"
                            else "Today's Schedule Pending ($startStr - $stopStr)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8)
                        )
                        Text(
                            text = if (isBn) "নির্ধারিত সময়ে কন্ট্রোলার স্বয়ংক্রিয়ভাবে $totalMins মিনিট চার্জ করবে।"
                            else "Controller will automatically charge for $totalMins min when time arrives.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Text(
                    text = if (isBn) "আজকের জন্য কোনো শিডিউল চার্জিং নির্ধারিত নেই।"
                    else "No scheduled charging task configured for today.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

private fun formatCalendarDateString(dateCode: Int, isBn: Boolean): String {
    return try {
        if (dateCode >= 20200101) {
            val year = dateCode / 10000
            val month = (dateCode % 10000) / 100
            val day = dateCode % 100
            val cal = Calendar.getInstance()
            cal.set(year, month - 1, day)
            val pattern = if (isBn) "dd MMMM yyyy, EEEE" else "EEEE, dd MMM yyyy"
            val locale = if (isBn) Locale("bn", "BD") else Locale.ENGLISH
            SimpleDateFormat(pattern, locale).format(cal.time)
        } else {
            val pattern = if (isBn) "dd MMMM yyyy, EEEE" else "EEEE, dd MMM yyyy"
            val locale = if (isBn) Locale("bn", "BD") else Locale.ENGLISH
            SimpleDateFormat(pattern, locale).format(Date())
        }
    } catch (_: Exception) {
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date())
    }
}
