package com.shahriyar.smartwificharger.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shahriyar.smartwificharger.data.prefs.AppPreferences
import com.shahriyar.smartwificharger.presentation.localization.AppLanguage
import com.shahriyar.smartwificharger.presentation.localization.LocalizationManager
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

data class WeatherLocationPreset(
    val nameEn: String,
    val nameBn: String,
    val lat: Double,
    val lon: Double
)

val BOGURA_UPAZILAS = listOf(
    WeatherLocationPreset("Bogura Sadar", "বগুড়া সদর", 24.8465, 89.3777),
    WeatherLocationPreset("Shajahanpur", "শাজাহানপুর", 24.7645, 89.3951),
    WeatherLocationPreset("Sherpur", "শেরপুর", 24.6755, 89.4328),
    WeatherLocationPreset("Dhunat", "ধুনট", 24.6833, 89.5333),
    WeatherLocationPreset("Gabtali", "গাবতলী", 24.8833, 89.5167),
    WeatherLocationPreset("Shibganj", "শিবগঞ্জ", 25.0167, 89.3167),
    WeatherLocationPreset("Kahaloo", "কাহালু", 24.8250, 89.2667),
    WeatherLocationPreset("Nandigram", "নন্দীগ্রাম", 24.6667, 89.2167),
    WeatherLocationPreset("Dupchanchia", "দুপচাঁচিয়া", 24.8667, 89.1667),
    WeatherLocationPreset("Santahar", "সান্তাহার", 24.7833, 88.9833),
    WeatherLocationPreset("Sariakandi", "সারিয়াকান্দি", 24.8833, 89.6500),
    WeatherLocationPreset("Sonatala", "সোনাতলা", 25.0500, 89.5000)
)

val BANGLADESH_DIVISIONS_AND_DISTRICTS = listOf(
    WeatherLocationPreset("Dhaka", "ঢাকা", 23.8103, 90.4125),
    WeatherLocationPreset("Chattogram", "চট্টগ্রাম", 22.3569, 91.7832),
    WeatherLocationPreset("Rajshahi", "রাজশাহী", 24.3636, 88.6241),
    WeatherLocationPreset("Khulna", "খুলনা", 22.8456, 89.5403),
    WeatherLocationPreset("Barishal", "বরিশাল", 22.7010, 90.3535),
    WeatherLocationPreset("Sylhet", "সিলেট", 24.8949, 91.8687),
    WeatherLocationPreset("Rangpur", "রংপুর", 25.7439, 89.2752),
    WeatherLocationPreset("Mymensingh", "ময়মনসিংহ", 24.7471, 90.4203),
    WeatherLocationPreset("Gazipur", "গাজীপুর", 23.9999, 90.4203),
    WeatherLocationPreset("Narayanganj", "নারায়ণগঞ্জ", 23.6238, 90.5000),
    WeatherLocationPreset("Cumilla", "কুমিল্লা", 23.4682, 91.1788),
    WeatherLocationPreset("Sirajganj", "সিরাজগঞ্জ", 24.4534, 89.7008),
    WeatherLocationPreset("Pabna", "পাবনা", 24.0064, 89.2372),
    WeatherLocationPreset("Naogaon", "নওগাঁ", 24.7936, 88.9318),
    WeatherLocationPreset("Joypurhat", "জয়পুরহাট", 25.0947, 89.0277),
    WeatherLocationPreset("Dinajpur", "দিনাজপুর", 25.6279, 88.6332),
    WeatherLocationPreset("Cox's Bazar", "কক্সবাজার", 21.4272, 92.0058),
    WeatherLocationPreset("Jashore", "যশোর", 23.1664, 89.2081)
)

@Composable
fun WeatherScreen(
    state: ChargerUiState,
    onSyncWeather: (Double, Double, String) -> Unit,
    onToggleSolar: (Boolean) -> Unit = {},
    onSetWeatherMode: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences.getInstance(context) }
    val isBn = (LocalizationManager.currentLanguage == AppLanguage.BANGLA)
    val strings = LocalizationManager.strings

    // Persistent selected location name
    var selectedLocationName by remember {
        mutableStateOf(prefs.weatherLocationName)
    }

    var currentLat by remember {
        mutableDoubleStateOf(prefs.weatherLat.toDouble())
    }
    var currentLon by remember {
        mutableDoubleStateOf(prefs.weatherLon.toDouble())
    }

    var showCustomCoords by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(false) }
    var isSyncingNow by remember { mutableStateOf(false) }

    // Smooth spinning transition for refresh icon
    val infiniteTransition = rememberInfiniteTransition(label = "weatherSyncSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isSyncingNow || isDetectingLocation) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing)
        ),
        label = "spinAngle"
    )

    // Helper to extract location and reverse-geocode
    val grabCurrentLocation: () -> Unit = {
        isDetectingLocation = true
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            var bestLoc: Location? = null
            if (lm != null) {
                val providers = lm.getProviders(true)
                for (p in providers) {
                    try {
                        val l = lm.getLastKnownLocation(p) ?: continue
                        if (bestLoc == null || l.accuracy < bestLoc.accuracy) {
                            bestLoc = l
                        }
                    } catch (_: SecurityException) {}
                }
            }

            if (bestLoc != null) {
                val lat = bestLoc.latitude
                val lon = bestLoc.longitude
                currentLat = lat
                currentLon = lon

                // Geocode for Area Name
                var areaTitle = "Lat: ${"%.4f".format(lat)}, Lon: ${"%.4f".format(lon)}"
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val subLoc = addr.subLocality ?: addr.subAdminArea ?: addr.locality
                        val loc = addr.locality ?: addr.adminArea ?: "Bangladesh"
                        areaTitle = if (subLoc != null && subLoc != loc) "$subLoc, $loc" else (subLoc ?: loc)
                    }
                } catch (_: Exception) {}

                selectedLocationName = areaTitle
                prefs.weatherLocationName = areaTitle
                prefs.weatherLat = lat.toFloat()
                prefs.weatherLon = lon.toFloat()
                onSyncWeather(lat, lon, areaTitle)

                Toast.makeText(
                    context,
                    if (isBn) "সঠিক জিপিএস লোকেশন সনাক্ত: $areaTitle" else "GPS location acquired: $areaTitle",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    if (isBn) "জিপিএস চালু করুন অথবা তালিকা থেকে এলাকা নির্বাচন করুন" else "Please enable GPS or select from area presets",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Location error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        } finally {
            isDetectingLocation = false
        }
    }

    // Permission launcher for Location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            grabCurrentLocation()
        } else {
            Toast.makeText(
                context,
                if (isBn) "সঠিক আবহাওয়ার জন্য লোকেশন পারমিশন প্রয়োজন" else "Location permission needed for exact local weather",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val requestLocationAndGrab = {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            grabCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "🌤 ${strings.weatherTitle}",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NeonCyan
        )
        Text(
            text = strings.weatherSubtitle,
            fontSize = 11.5.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Live Weather Metric Card
        val cloud = state.cloudCoverPercent ?: (if (state.telemetry.cloudCover > 0) state.telemetry.cloudCover else 15)
        val isCloudy = cloud > 60
        val isRainy = cloud > 80

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, if (isCloudy) AmberAlert.copy(alpha = 0.5f) else BorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBn) "বর্তমান এলাকা: $selectedLocationName" else "Location: $selectedLocationName",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Lat: ${"%.4f".format(currentLat)}, Lon: ${"%.4f".format(currentLon)}",
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                    }
                    Icon(
                        imageVector = if (isRainy) Icons.Default.Thunderstorm else if (isCloudy) Icons.Default.Cloud else Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = if (isCloudy) AmberAlert else ElectricGreen,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.cloudCover,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "${cloud}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCloudy) AmberAlert else NeonCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "সোলার উৎপাদন সম্ভাবনা" else "Solar Generation Potential",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = if (isCloudy) (if (isBn) "কম (গ্রিড চার্জ প্রয়োজন)" else "POOR (Boost Needed)")
                        else (if (isBn) "উত্তম (১০০% সোলার চার্জিং)" else "OPTIMAL (100% Solar)"),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCloudy) AmberAlert else ElectricGreen
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // GPS Auto-Grab Button
                Button(
                    onClick = { requestLocationAndGrab() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isDetectingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBn) "📍 জিপিএস অটো ডিটেক্ট (Auto GPS)" else "📍 AUTO DETECT GPS LOCATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBackground
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sync Weather Button with Spin Animation
                Button(
                    onClick = {
                        isSyncingNow = true
                        onSyncWeather(currentLat, currentLon, selectedLocationName)
                        context.let {
                            Toast.makeText(it, if (isBn) "আবহাওয়া আপডেট হচ্ছে..." else "Updating weather...", Toast.LENGTH_SHORT).show()
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            isSyncingNow = false
                        }, 1200)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(spinAngle)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.syncWeatherNow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceDark)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBn) "৩-ওয়ে সিঙ্ক: অ্যাপ, ইএসপি মেমোরি ও ক্লাউডে রিয়েলটাইমে সংরক্ষিত।" else "3-Way Sync: Location synchronized to MCU & Cloud.",
                        fontSize = 10.5.sp,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weather Mode Override Selector Card
        val effectiveMode = state.weatherStatus.uppercase()
        val isManual = state.weatherManualOverride

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, if (isManual) AmberAlert.copy(alpha = 0.6f) else BorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBn) "⚙️ আবহাওয়া মোড নিয়ন্ত্রণ (Weather Mode)" else "⚙️ Weather Mode Control",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isManual) AmberAlert else NeonCyan
                        )
                        Text(
                            text = if (isManual)
                                (if (isBn) "ম্যানুয়াল ওভাররাইড চালু (ESP মেমোরিতে সংরক্ষিত)" else "Manual Override Active (Saved in MCU)")
                            else
                                (if (isBn) "স্বয়ংক্রিয় Open-Meteo সিঙ্ক (অ্যাপ বন্ধ থাকলেও কাজ করবে)" else "Auto Open-Meteo Sync (Works even if app closed)"),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    if (isManual) {
                        TextButton(onClick = { onSetWeatherMode("AUTO") }) {
                            Text(if (isBn) "অটো রিসেট" else "Reset Auto", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        Triple("AUTO", if (isBn) "🤖 অটো" else "Auto", ElectricGreen),
                        Triple("CLEAR", if (isBn) "☀️ রোদ" else "Clear", ElectricGreen),
                        Triple("CLOUDY", if (isBn) "☁️ মেঘলা" else "Cloudy", AmberAlert),
                        Triple("FOGGY", if (isBn) "🌫️ কুয়াশা" else "Foggy", NeonCyan),
                        Triple("WINTER", if (isBn) "❄️ শীত" else "Winter", NeonCyan)
                    )

                    modes.forEach { (mKey, mLabel, mColor) ->
                        val isCurrent = if (mKey == "AUTO") !isManual else (effectiveMode.contains(mKey))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCurrent) mColor.copy(alpha = 0.22f) else SurfaceDark)
                                .border(
                                    1.dp,
                                    if (isCurrent) mColor else BorderDark,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onSetWeatherMode(mKey)
                                }
                                .padding(vertical = 10.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = mLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isCurrent) mColor else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Bogura Sub-Districts / Upazilas Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBn) "বগুড়া জেলা ও উপজেলাসমূহ" else "Bogura District & Upazilas",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isBn) "আপনার নির্দিষ্ট উপজেলা নির্বাচন করুন:" else "Select your installation Upazila:",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BOGURA_UPAZILAS.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { preset ->
                                val displayName = if (isBn) preset.nameBn else preset.nameEn
                                val isSelected = selectedLocationName.contains(preset.nameEn) || selectedLocationName.contains(preset.nameBn)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else SurfaceDark)
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonCyan else BorderDark,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedLocationName = displayName
                                            prefs.weatherLocationName = displayName
                                            prefs.weatherLat = preset.lat.toFloat()
                                            prefs.weatherLon = preset.lon.toFloat()
                                            currentLat = preset.lat
                                            currentLon = preset.lon
                                            showCustomCoords = false
                                            onSyncWeather(preset.lat, preset.lon, displayName)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) NeonCyan else TextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Bangladesh Divisions & Major Districts
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBn) "প্রধান বিভাগীয় শহর ও অন্যান্য জেলা" else "Divisions & Other Districts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BANGLADESH_DIVISIONS_AND_DISTRICTS.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { preset ->
                                val displayName = if (isBn) preset.nameBn else preset.nameEn
                                val isSelected = selectedLocationName.contains(preset.nameEn) || selectedLocationName.contains(preset.nameBn)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) ElectricGreen.copy(alpha = 0.2f) else SurfaceDark)
                                        .border(
                                            1.dp,
                                            if (isSelected) ElectricGreen else BorderDark,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedLocationName = displayName
                                            prefs.weatherLocationName = displayName
                                            prefs.weatherLat = preset.lat.toFloat()
                                            prefs.weatherLon = preset.lon.toFloat()
                                            currentLat = preset.lat
                                            currentLon = preset.lon
                                            showCustomCoords = false
                                            onSyncWeather(preset.lat, preset.lon, displayName)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) ElectricGreen else TextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Custom Coordinates
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomCoords = !showCustomCoords }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.customCoordinates,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Text(
                        text = if (showCustomCoords) "▲" else "▼",
                        fontSize = 12.sp,
                        color = NeonCyan
                    )
                }

                if (showCustomCoords) {
                    Spacer(modifier = Modifier.height(8.dp))
                    var latStr by remember { mutableStateOf(currentLat.toString()) }
                    var lonStr by remember { mutableStateOf(currentLon.toString()) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latStr,
                            onValueChange = {
                                latStr = it
                                it.toDoubleOrNull()?.let { l -> currentLat = l }
                            },
                            label = { Text(strings.latitude, fontSize = 11.sp, color = TextMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                        OutlinedTextField(
                            value = lonStr,
                            onValueChange = {
                                lonStr = it
                                it.toDoubleOrNull()?.let { l -> currentLon = l }
                            },
                            label = { Text(strings.longitude, fontSize = 11.sp, color = TextMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = BorderDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val name = "Custom (${"%.3f".format(currentLat)}, ${"%.3f".format(currentLon)})"
                            selectedLocationName = name
                            prefs.weatherLocationName = name
                            prefs.weatherLat = currentLat.toFloat()
                            prefs.weatherLon = currentLon.toFloat()
                            onSyncWeather(currentLat, currentLon, name)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isBn) "কাস্টম লোকেশন সেট ও সিঙ্ক করুন" else "SET & SYNC CUSTOM COORDINATES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkBackground
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Solar Autonomous Backup Toggle Card
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
                        text = if (isBn) "স্বয়ংক্রিয় সোলার ব্যাকআপ মোড" else "Automated Solar Backup Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isBn) "মেঘলা দিনে (মেঘ > ৬০%) ব্যাটারি বাঁচাতে চার্জিং সক্রিয় হবে" else "Suspends charging during clear sunlight; charges from AC grid on heavy clouds",
                        fontSize = 10.5.sp,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = state.telemetry.solarBackup,
                    onCheckedChange = { onToggleSolar(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
