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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.presentation.components.ConnectionBadge
import com.shahriyar.smartwificharger.presentation.localization.AppLanguage
import com.shahriyar.smartwificharger.presentation.localization.LocalizationManager
import com.shahriyar.smartwificharger.presentation.theme.AmberAlert
import com.shahriyar.smartwificharger.presentation.theme.BorderDark
import com.shahriyar.smartwificharger.presentation.theme.CoralRed
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerUiState

@Composable
fun NetworkScreen(
    state: ChargerUiState,
    onSaveConfig: (ChargerConfig) -> Unit,
    onScanWifi: () -> Unit = {},
    onConnectWifi: (String, String) -> Unit = { _, _ -> },
    isSetupMode: Boolean = false,
    onBypassSetup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isBn = (LocalizationManager.currentLanguage == AppLanguage.BANGLA)

    var apSsid by remember(state.config.apSsid) { mutableStateOf(state.config.apSsid) }
    var apPassword by remember(state.config.apPassword) { mutableStateOf(state.config.apPassword) }

    var isHiddenNetwork by remember { mutableStateOf(false) }
    var hiddenSsid by remember { mutableStateOf("") }
    var selectedSsid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isBn) "ওয়াইফাই ও নেটওয়ার্ক" else "Network & Wi-Fi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
                Text(
                    text = if (isBn) "অগ্রাধিকার: SoftAP > লোকাল ল্যান > ক্লাউড" else "Priority: AP > Local LAN > Firebase WAN",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isSetupMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AmberAlert.copy(alpha = 0.12f))
                    .border(1.5.dp, AmberAlert, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = if (isBn) "⚡ প্রথমবার চার্জার সেটআপ" else "⚡ FIRST-TIME CHARGER SETUP",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberAlert
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBn) "নিচের তালিকা থেকে আপনার রাউটার নির্বাচন করে পাসওয়ার্ড দিন এবং কানেক্ট করুন।"
                               else "Select your home Wi-Fi network below and enter password to connect.",
                        fontSize = 11.5.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onBypassSetup) {
                            Text(
                                text = if (isBn) "অফলাইন AP মোডে ব্যবহার করুন" else "Use in Offline AP Mode",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 1. Wi-Fi Scanner Card (PLACED AT THE VERY TOP AS REQUESTED)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "বাসার ওয়াইফাই নেটওয়ার্ক কানেক্ট করুন" else "Connect Device to Home Wi-Fi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    if (state.isScanning) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onScanWifi() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBn) "ওয়াইফাই স্ক্যান করুন" else "SCAN WI-FI",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkBackground
                        )
                    }

                    TextButton(
                        onClick = { isHiddenNetwork = !isHiddenNetwork }
                    ) {
                        Text(
                            text = if (isHiddenNetwork)
                                (if (isBn) "তালিকা থেকে নির্বাচন" else "Select from List")
                            else
                                (if (isBn) "লুকানো নেটওয়ার্ক (Hidden)" else "Hidden Network"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isHiddenNetwork) {
                    OutlinedTextField(
                        value = hiddenSsid,
                        onValueChange = { hiddenSsid = it },
                        label = { Text(if (isBn) "লুকানো ওয়াইফাই নাম (SSID)" else "Hidden SSID Name", fontSize = 11.sp, color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (state.scannedNetworks.isNotEmpty()) {
                    Text(
                        text = if (isBn) "উপলব্ধ নেটওয়ার্কসমূহ (${state.scannedNetworks.size} টি পাওয়া গেছে):" else "Available Networks (${state.scannedNetworks.size}):",
                        fontSize = 11.5.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(modifier = Modifier.fillMaxWidth().height(140.dp).verticalScroll(rememberScrollState())) {
                        for (net in state.scannedNetworks) {
                            val isSelected = (selectedSsid == net.ssid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkBackground)
                                    .border(1.dp, if (isSelected) NeonCyan else BorderDark, RoundedCornerShape(8.dp))
                                    .clickable { selectedSsid = net.ssid }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(net.ssid, color = TextPrimary, fontSize = 12.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                Text("${net.quality}%", color = ElectricGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val effectiveSsid = if (isHiddenNetwork) hiddenSsid else selectedSsid

                if (effectiveSsid.isNotBlank()) {
                    Text(
                        text = if (isBn) "নির্বাচিত ওয়াইফাই: $effectiveSsid" else "Selected: $effectiveSsid",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wifiPass,
                        onValueChange = { wifiPass = it },
                        label = { Text(if (isBn) "ওয়াইফাই পাসওয়ার্ড" else "Wi-Fi Password", fontSize = 11.sp, color = TextMuted) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onConnectWifi(effectiveSsid, wifiPass) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricGreen)
                    ) {
                        Text(
                            text = if (isBn) "ওয়াইফাই সংযোগ করুন (CONNECT)" else "CONNECT TO WI-FI",
                            color = DarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Active Network Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, if (state.isMcuOnline) ElectricGreen.copy(alpha = 0.5f) else BorderDark, RoundedCornerShape(16.dp))
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
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (state.isMcuOnline) ElectricGreen else CoralRed)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.isMcuOnline) (if (isBn) "ডিভাইস অনলাইন" else "DEVICE ONLINE") else (if (isBn) "ডিভাইস অফলাইন" else "DEVICE OFFLINE"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isMcuOnline) ElectricGreen else CoralRed
                        )
                    }
                    Text(
                        text = state.connectionMode.displayName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val connectedWifiName = if (state.connectionMode.name == "FIREBASE_WAN") {
                    if (state.isMcuOnline) state.telemetry.staSsid.ifBlank { "Cloud WAN" } else if (isBn) "সংযোগ বিচ্ছিন্ন / অফলাইন" else "Disconnected / Offline"
                } else if (state.telemetry.staSsid.isNotBlank() && state.telemetry.staSsid != "None") {
                    state.telemetry.staSsid
                } else if (state.connectionMode.name == "DIRECT_AP") {
                    state.telemetry.apSsid.ifBlank { "SMART CHARGE CONTROLLER" }
                } else {
                    "None"
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = if (isBn) "সংযুক্ত রাউটার ওয়াইফাই" else "Connected Wi-Fi SSID", fontSize = 11.5.sp, color = TextMuted)
                    Text(text = connectedWifiName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ElectricGreen)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = if (isBn) "সক্রিয় আইপি / গেটওয়ে" else "Active Base URL", fontSize = 11.5.sp, color = TextMuted)
                    Text(text = state.activeBaseUrl ?: "Auto Discovering", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = if (isBn) "সিগন্যাল ক্ষমতা (RSSI)" else "Wi-Fi Signal (RSSI)", fontSize = 11.5.sp, color = TextMuted)
                    Text(text = "${state.telemetry.wifiRssi} dBm", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ElectricGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Control SoftAP Credentials Card
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
                    text = if (isBn) "কন্ট্রোল হটস্পট (SoftAP) কনফিগারেশন" else "Control AP Configuration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Text(
                    text = if (isBn) "চার্জারের নিজস্ব সরাসরি হটস্পট নাম ও পাসওয়ার্ড।" else "Device fallback SoftAP credentials for direct connection.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apSsid,
                    onValueChange = { apSsid = it },
                    label = { Text(if (isBn) "হটস্পট নাম (AP SSID)" else "AP SSID", fontSize = 11.sp, color = TextMuted) },
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
                    value = apPassword,
                    onValueChange = { apPassword = it },
                    label = { Text(if (isBn) "হটস্পট পাসওয়ার্ড (কমপক্ষে ৮ অক্ষর)" else "AP Password (Min 8 chars)", fontSize = 11.sp, color = TextMuted) },
                    visualTransformation = PasswordVisualTransformation(),
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
                        onSaveConfig(state.config.copy(apSsid = apSsid, apPassword = apPassword))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBn) "হটস্পট সেটিংস আপডেট করুন" else "UPDATE AP CREDENTIALS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Connection Priority Overview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = if (isBn) "কানেকশন অগ্রাধিকার ক্রম" else "Connection Priority Hierarchy",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                PriorityRow("1", "DIRECT AP", if (isBn) "সরাসরি নোডএমসিইউ হটস্পট (192.168.4.1)" else "Direct SoftAP socket (192.168.4.1)", state.connectionMode.name == "DIRECT_AP")
                Spacer(modifier = Modifier.height(6.dp))
                PriorityRow("2", "LOCAL LAN", if (isBn) "বাসার লোকাল রাউটার (mDNS / UDP)" else "Local Wi-Fi router (mDNS / UDP)", state.connectionMode.name == "LOCAL_LAN")
                Spacer(modifier = Modifier.height(6.dp))
                PriorityRow("3", "FIREBASE WAN", if (isBn) "ক্লাউড রিয়েলটাইম ডেটাবেস" else "Cloud Realtime Database", state.connectionMode.name == "FIREBASE_WAN")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PriorityRow(
    tier: String,
    title: String,
    desc: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) ElectricGreen.copy(alpha = 0.12f) else DarkBackground)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isActive) ElectricGreen else SurfaceCard)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = "P$tier", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isActive) DarkBackground else TextMuted)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (isActive) ElectricGreen else TextPrimary)
                Text(text = desc, fontSize = 9.5.sp, color = TextMuted)
            }
        }
        if (isActive) {
            Text(text = "ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = ElectricGreen)
        }
    }
}
