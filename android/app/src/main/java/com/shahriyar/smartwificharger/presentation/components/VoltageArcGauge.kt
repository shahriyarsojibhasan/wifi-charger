package com.shahriyar.smartwificharger.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.presentation.theme.CoralRed
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.GaugeBlue
import com.shahriyar.smartwificharger.presentation.theme.GaugeGreen
import com.shahriyar.smartwificharger.presentation.theme.GaugeRed
import com.shahriyar.smartwificharger.presentation.theme.GaugeYellow
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import java.util.Locale

@Composable
fun VoltageArcGauge(
    voltage: Float,
    isSensorDisconnected: Boolean,
    isCharging: Boolean = false,
    chargingTime: String = "00:00:00",
    lowCut: Float = 11.5f,
    highCut: Float = 14.4f,
    isMcuOnline: Boolean = true,
    modifier: Modifier = Modifier
) {
    val effectiveIsCharging = isMcuOnline && isCharging

    // Dynamic sweep fraction based on configured battery profile
    val rangeSpan = (highCut - lowCut).coerceAtLeast(1.0f)
    val minDisplay = (lowCut - (rangeSpan * 0.3f)).coerceAtLeast(0.5f)
    val maxDisplay = highCut + (rangeSpan * 0.2f)

    val targetFraction = if (!isMcuOnline || voltage < 0.5f) 0f else {
        ((voltage - minDisplay) / (maxDisplay - minDisplay)).coerceIn(0.0f, 1.0f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 600),
        label = "VoltageSweep"
    )

    // Pulse animation when charging
    val infiniteTransition = rememberInfiniteTransition(label = "chargingAura")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (effectiveIsCharging) 1.03f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    // Half-circle arc charging flow (strictly confined within 240° arc from 150° to 390°)
    val chargingPulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chargingPulse"
    )

    // Dynamic battery percent based on low/high cut
    val batteryPercent = if (!isMcuOnline || voltage < 0.5f) 0 else {
        (((voltage - lowCut) / rangeSpan) * 100f).toInt().coerceIn(0, 100)
    }

    // Dynamic gauge color based on battery percentage
    val gaugeColor = when {
        !isMcuOnline || voltage < 0.5f -> GaugeRed
        batteryPercent < 25 -> GaugeRed
        batteryPercent < 55 -> GaugeYellow
        batteryPercent < 90 -> GaugeGreen
        else -> GaugeBlue
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(250.dp)
            .scale(pulseScale)
    ) {
        Canvas(modifier = Modifier.size(230.dp)) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val arcSize = Size(diameter, diameter)

            // Background Track Arc (240 degrees sweep from 150 to 390)
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Progress Arc
            if (animatedFraction > 0.01f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to GaugeRed,
                        0.35f to GaugeYellow,
                        0.7f to GaugeGreen,
                        1.0f to if (effectiveIsCharging) ElectricGreen else NeonCyan
                    ),
                    startAngle = 150f,
                    sweepAngle = 240f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Animated Charging Energy Beam (confined strictly inside the 240° arc, never crossing the bottom gap)
            if (effectiveIsCharging) {
                val totalSweep = (240f * animatedFraction).coerceIn(40f, 240f)
                val pulseLength = (totalSweep * 0.35f).coerceIn(25f, 60f)
                val pulseStart = 150f + (totalSweep - pulseLength) * chargingPulseProgress

                // Outer Neon Aura
                drawArc(
                    color = ElectricGreen.copy(alpha = 0.35f),
                    startAngle = pulseStart,
                    sweepAngle = pulseLength,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 1.35f, cap = StrokeCap.Round)
                )

                // High-intensity Core Beam
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ElectricGreen.copy(alpha = 0.2f),
                            ElectricGreen,
                            Color.White
                        )
                    ),
                    startAngle = pulseStart,
                    sweepAngle = pulseLength,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 0.85f, cap = StrokeCap.Round)
                )
            }
        }

        // Center Content: Percentage, Status, Voltage, and Charging Time
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val isZeroVoltage = voltage < 0.5f
            val isBn = (com.shahriyar.smartwificharger.presentation.localization.LocalizationManager.currentLanguage == com.shahriyar.smartwificharger.presentation.localization.AppLanguage.BANGLA)

            if (!isMcuOnline) {
                // MCU IS OFFLINE: Display clean "--" and "N/A" instead of misleading cached values
                Text(
                    text = "--%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x26FF5252))
                        .border(1.dp, CoralRed.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CoralRed)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBn) "অফলাইন" else "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "-- V",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isBn) "লাইভ ডেটা N/A" else "LIVE DATA N/A",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CoralRed
                )
            } else if (isZeroVoltage) {
                Text(
                    text = if (voltage <= 0.05f) "0.00 V" else String.format(Locale.US, "%.2f V", voltage),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GaugeRed
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isSensorDisconnected) (if (isBn) "ব্যাটারি সংযোগ বিচ্ছিন্ন" else "NO BATTERY DETECTED") else (if (isBn) "স্ট্যান্ডবাই" else "BATTERY STANDBY"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isBn) "০% (স্ট্যান্ডবাই)" else "0% (Standby)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
            } else {
                // Top: Battery Percentage (e.g. 78%)
                Text(
                    text = "$batteryPercent%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = if (effectiveIsCharging) ElectricGreen else NeonCyan
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Middle: Realtime Status Badge (↻ CHARGING or ⚪ STANDBY)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (effectiveIsCharging) Color(0x2E00E676) else Color(0x2664748B))
                        .border(
                            1.dp,
                            if (effectiveIsCharging) ElectricGreen.copy(alpha = 0.7f) else Color(0xFF334155),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (effectiveIsCharging) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = ElectricGreen,
                                modifier = Modifier
                                    .size(11.dp)
                                    .rotate(chargingPulseProgress * 360f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBn) "চার্জ হচ্ছে" else "CHARGING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElectricGreen
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF94A3B8))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBn) "স্ট্যান্ডবাই" else "STANDBY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Voltage: 12.60 V
                Text(
                    text = String.format(Locale.US, "%.2f V", voltage),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (effectiveIsCharging) Color.White else gaugeColor
                )

                // Bottom: Charging Time (e.g. 01:24:36)
                if (effectiveIsCharging || chargingTime != "00:00:00") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "⏱ $chargingTime",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (effectiveIsCharging) ElectricGreen else TextMuted
                    )
                }
            }
        }
    }
}
