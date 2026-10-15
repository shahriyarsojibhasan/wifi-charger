package com.shahriyar.smartwificharger.presentation.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.BillingStats
import com.shahriyar.smartwificharger.presentation.localization.AppLanguage
import com.shahriyar.smartwificharger.presentation.localization.LocalizationManager
import com.shahriyar.smartwificharger.presentation.theme.AmberAlert
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import java.util.Locale

@Composable
fun EstimatedCostCard(
    stats: BillingStats,
    modifier: Modifier = Modifier
) {
    val isBn = (LocalizationManager.currentLanguage == AppLanguage.BANGLA)

    Box(
        modifier = modifier
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
                    text = if (isBn) "আনুমানিক বিদ্যুৎ বিল ও খরচ" else "ESTIMATED ELECTRICITY BILL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x26FFB300))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isBn) "হিসাবকৃত" else "ESTIMATED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberAlert
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isBn) "আজকের খরচ" else "Today's Cost",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = String.format(Locale.US, "৳ %.2f", stats.todayEstimatedBdt),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Text(
                        text = String.format(Locale.US, "%.3f kWh", stats.todayEstimatedKwh),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isBn) "এই মাসের মোট" else "This Month",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = String.format(Locale.US, "৳ %.2f", stats.totalEstimatedBdt),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = String.format(Locale.US, "%.3f kWh", stats.totalEstimatedKwh),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x0DFFFFFF))
                    .padding(8.dp)
            ) {
                Text(
                    text = if (isBn) "⚠ চার্জারের নির্ধারিত ওয়াট ও ব্যবহারের সময়ের ভিত্তিতে প্রস্তুতকৃত আনুমানিক হিসাব।"
                    else "⚠ Calculated from configured charger wattage and runtime. No physical AC current sensor installed.",
                    fontSize = 10.sp,
                    color = AmberAlert,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
