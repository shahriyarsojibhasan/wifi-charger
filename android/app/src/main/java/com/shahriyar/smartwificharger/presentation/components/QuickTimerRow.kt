package com.shahriyar.smartwificharger.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.shahriyar.smartwificharger.presentation.localization.AppLanguage
import com.shahriyar.smartwificharger.presentation.localization.LocalizationManager
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.CoralRed
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import java.util.Locale

@Composable
fun QuickTimerRow(
    timerActive: Boolean,
    remainingSeconds: Int,
    onSetTimer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("") }
    val strings = LocalizationManager.strings
    val isBn = (LocalizationManager.currentLanguage == AppLanguage.BANGLA)

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "কাস্টম টাইমার সেট করুন" else "Set Custom Timer",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = if (isBn) "কত মিনিট চার্জ করতে চান তা লিখুন। সময় শেষ হলে চার্জার স্বয়ংক্রিয়ভাবে বন্ধ হয়ে যাবে।"
                        else "Enter charging duration in minutes. The charger will automatically switch off when time expires.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { customMinutesText = it.filter { char -> char.isDigit() } },
                        label = { Text(if (isBn) "মিনিট (১ - ৭২০)" else "Minutes (1 - 720)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            focusedLabelColor = NeonCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = customMinutesText.toIntOrNull() ?: 0
                        if (mins in 1..720) {
                            onSetTimer(mins)
                            showCustomDialog = false
                            customMinutesText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text(if (isBn) "টাইমার চালু করুন" else "SET TIMER", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(strings.cancel, color = TextMuted)
                }
            },
            containerColor = SurfaceCard
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(
                1.dp,
                if (timerActive) ElectricGreen.copy(alpha = 0.6f) else BorderDark,
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AvTimer,
                        contentDescription = null,
                        tint = if (timerActive) ElectricGreen else NeonCyan,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.quickTimers,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.5.sp
                    )
                }

                if (timerActive && remainingSeconds > 0) {
                    val hrs = remainingSeconds / 3600
                    val mins = (remainingSeconds % 3600) / 60
                    val secs = remainingSeconds % 60
                    val timeStr = if (hrs > 0) {
                        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                    } else {
                        String.format(Locale.US, "%02d:%02d", mins, secs)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⏱️ ${strings.timerRemaining}: $timeStr",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CoralRed.copy(alpha = 0.2f))
                                .clickable { onSetTimer(0) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = CoralRed, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(strings.cancel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (isBn) "নিষ্ক্রিয়" else "Inactive",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = if (isBn) {
                    listOf(15 to "১৫ মি.", 30 to "৩০ মি.", 60 to "১ ঘণ্টা", 120 to "২ ঘণ্টা")
                } else {
                    listOf(15 to "15M", 30 to "30M", 60 to "1H", 120 to "2H")
                }

                presets.forEach { (mins, label) ->
                    val isPresetActive = timerActive && ((remainingSeconds + 59) / 60 in (mins - 1)..(mins + 1))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPresetActive) NeonCyan.copy(alpha = 0.2f) else SurfaceDark)
                            .border(
                                1.dp,
                                if (isPresetActive) NeonCyan else BorderDark,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSetTimer(mins) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPresetActive) NeonCyan else TextPrimary
                        )
                    }
                }

                // Custom Time Button
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                        .clickable { showCustomDialog = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBn) "কাস্টম" else "CUSTOM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }
        }
    }
}
