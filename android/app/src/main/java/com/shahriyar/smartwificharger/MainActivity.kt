package com.shahriyar.smartwificharger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahriyar.smartwificharger.presentation.components.ConnectionBadge
import com.shahriyar.smartwificharger.presentation.localization.AppLanguage
import com.shahriyar.smartwificharger.presentation.localization.LocalizationManager
import com.shahriyar.smartwificharger.presentation.screens.AboutScreen
import com.shahriyar.smartwificharger.presentation.screens.AlertsScreen
import com.shahriyar.smartwificharger.presentation.screens.AutomationScreen
import com.shahriyar.smartwificharger.presentation.screens.BillingScreen
import com.shahriyar.smartwificharger.presentation.screens.ChargingScreen
import com.shahriyar.smartwificharger.presentation.screens.SplashScreen
import com.shahriyar.smartwificharger.domain.model.ConnectionMode
import com.shahriyar.smartwificharger.presentation.screens.DashboardScreen
import com.shahriyar.smartwificharger.presentation.screens.DevicesScreen
import com.shahriyar.smartwificharger.presentation.screens.DiagnosticsScreen
import com.shahriyar.smartwificharger.presentation.screens.NetworkScreen
import com.shahriyar.smartwificharger.presentation.screens.SchedulerScreen
import com.shahriyar.smartwificharger.presentation.screens.SettingsScreen
import com.shahriyar.smartwificharger.presentation.screens.StatisticsScreen
import com.shahriyar.smartwificharger.presentation.screens.SyncScreen
import com.shahriyar.smartwificharger.presentation.screens.WeatherScreen
import com.shahriyar.smartwificharger.presentation.theme.CoralRed
import com.shahriyar.smartwificharger.presentation.theme.DarkBackground
import com.shahriyar.smartwificharger.presentation.theme.ElectricGreen
import com.shahriyar.smartwificharger.presentation.theme.NeonCyan
import com.shahriyar.smartwificharger.presentation.theme.SmartChargerTheme
import com.shahriyar.smartwificharger.presentation.theme.SurfaceDark
import com.shahriyar.smartwificharger.presentation.theme.SurfaceCard
import com.shahriyar.smartwificharger.presentation.theme.TextMuted
import com.shahriyar.smartwificharger.presentation.theme.TextPrimary
import com.shahriyar.smartwificharger.presentation.viewmodel.ChargerViewModel
import kotlinx.coroutines.launch

sealed class Screen(val titleKey: String, val icon: ImageVector) {
    object Dashboard : Screen("Dashboard", Icons.Default.Home)
    object Statistics : Screen("Stats", Icons.Default.BarChart)
    object Charging : Screen("Modes", Icons.Default.FlashOn)
    object Schedule : Screen("Schedule", Icons.Default.CalendarMonth)
    object Devices : Screen("Devices", Icons.Default.Devices)

    // Secondary / Drawer Screens
    object Automation : Screen("Profiles", Icons.Default.Tune)
    object Network : Screen("Network", Icons.Default.Wifi)
    object Settings : Screen("Settings", Icons.Default.Settings)
    object Billing : Screen("Billing", Icons.Default.Payments)
    object Weather : Screen("Weather", Icons.Default.Cloud)
    object Diagnostics : Screen("Diagnostics", Icons.Default.Assessment)
    object Alerts : Screen("Alerts", Icons.Default.Warning)
    object Sync : Screen("Sync", Icons.Default.CloudSync)
    object About : Screen("About", Icons.Default.Info)
}

class MainActivity : ComponentActivity() {
    private val viewModel: ChargerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartChargerTheme {
                val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val crashLog = prefs.getString("last_crash_log", null)
                var showCrashDialog by remember { mutableStateOf(crashLog != null) }

                if (showCrashDialog && crashLog != null) {
                    AlertDialog(
                        onDismissRequest = {
                            showCrashDialog = false
                            prefs.edit().remove("last_crash_log").apply()
                        },
                        title = { Text("App Crashed Previously") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                Text("Please screenshot this to the developer:", color = Color.Red, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(crashLog, fontSize = 10.sp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showCrashDialog = false
                                prefs.edit().remove("last_crash_log").apply()
                            }) { Text("OK") }
                        }
                    )
                }

                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: ChargerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalizationManager.strings

    // Splash and Onboarding Setup State
    var isAppReady by remember { mutableStateOf(false) }
    var isSetupBypassed by remember { mutableStateOf(false) }
    val isSetupRequired = state.connectionMode == ConnectionMode.DIRECT_AP &&
            !isSetupBypassed &&
            (state.telemetry.isSetupMode || state.telemetry.apSsid.contains("SETUP", ignoreCase = true))

    // Drawer and Screen Navigation State
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var showFactoryResetDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (!isAppReady) {
        SplashScreen(
            connectionMode = state.connectionMode,
            isInitialSyncComplete = state.telemetry.lastUpdatedEpoch > 0L,
            onReady = {
                if (state.connectionMode == ConnectionMode.DIRECT_AP &&
                    (state.telemetry.isSetupMode || state.telemetry.apSsid.contains("SETUP", ignoreCase = true))
                ) {
                    currentScreen = Screen.Network
                }
                isAppReady = true
            }
        )
        return
    }

    // Intercept back button to return to Dashboard first or close Drawer
    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.Dashboard) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen != Screen.Dashboard) {
            currentScreen = Screen.Dashboard
        }
    }

    val primaryBottomScreens = listOf(
        Screen.Dashboard,
        Screen.Statistics,
        Screen.Charging,
        Screen.Schedule,
        Screen.Devices
    )

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Refresh Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "refreshSpinTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (state.isScanning) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "refreshSpin"
    )

    // Factory Reset Confirmation Dialog
    if (showFactoryResetDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CoralRed)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.drawerFactoryReset, color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(strings.drawerResetWarning, color = TextPrimary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFactoryResetDialog = false
                        viewModel.factoryResetDevice()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text(strings.confirmReset, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetDialog = false }) {
                    Text(strings.cancel, color = TextMuted)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Language Selection and Confirmation Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = NeonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) "ভাষা পরিবর্তন করুন" else "Change Language",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) NeonCyan.copy(alpha = 0.15f) else SurfaceCard)
                            .clickable {
                                viewModel.setLanguage(AppLanguage.BANGLA)
                                showLanguageDialog = false
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("বাংলা (Bengali)", fontWeight = FontWeight.Bold, color = if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) NeonCyan else TextPrimary)
                        if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) {
                            Text("✓", color = NeonCyan, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (LocalizationManager.currentLanguage == AppLanguage.ENGLISH) NeonCyan.copy(alpha = 0.15f) else SurfaceCard)
                            .clickable {
                                viewModel.setLanguage(AppLanguage.ENGLISH)
                                showLanguageDialog = false
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("English", fontWeight = FontWeight.Bold, color = if (LocalizationManager.currentLanguage == AppLanguage.ENGLISH) NeonCyan else TextPrimary)
                        if (LocalizationManager.currentLanguage == AppLanguage.ENGLISH) {
                            Text("✓", color = NeonCyan, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) "ঠিক আছে" else "Close", color = NeonCyan)
                }
            },
            containerColor = SurfaceDark
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard)
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚡",
                                fontSize = 24.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.appTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonCyan
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isMcuOnline) ElectricGreen else CoralRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isMcuOnline) strings.deviceOnline else strings.deviceOffline,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isMcuOnline) ElectricGreen else CoralRed
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "IP: ${state.activeBaseUrl ?: "Auto Discovered"}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                HorizontalDivider(color = SurfaceCard)

                // Drawer Navigation Items (Organized & Grouped)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val chargingGroup = listOf(
                        Screen.Automation to strings.drawerProfiles,
                        Screen.Weather to strings.drawerWeather,
                        Screen.Billing to strings.drawerBilling
                    )

                    val toolsGroup = listOf(
                        Screen.Network to strings.drawerWifi,
                        Screen.Diagnostics to strings.drawerDiagnostics,
                        Screen.Sync to strings.drawerSync,
                        Screen.Alerts to strings.drawerAlerts
                    )

                    val systemGroup = listOf(
                        Screen.Settings to strings.drawerSettings,
                        Screen.About to strings.drawerAbout
                    )

                    // Helper to render drawer category
                    @Composable
                    fun DrawerSection(title: String, items: List<Pair<Screen, String>>) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan.copy(alpha = 0.85f),
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp, top = 6.dp)
                        )
                        items.forEach { (screen, label) ->
                            val isSelected = currentScreen == screen
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                },
                                icon = {
                                    Icon(
                                        screen.icon,
                                        contentDescription = label,
                                        tint = if (isSelected) NeonCyan else TextMuted
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    if (isSetupRequired && screen != Screen.Network) {
                                        viewModel.showMessage("⚡ Wi-Fi Setup Required: Please configure home Wi-Fi first.")
                                    } else {
                                        currentScreen = screen
                                        scope.launch { drawerState.close() }
                                    }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = SurfaceCard,
                                    selectedTextColor = NeonCyan,
                                    unselectedTextColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }

                    DrawerSection(strings.groupCharging, chargingGroup)
                    DrawerSection(strings.groupTools, toolsGroup)
                    DrawerSection(strings.groupSystem, systemGroup)
                }

                HorizontalDivider(color = SurfaceCard)

                // Language Selector and Factory Reset in Drawer Footer
                Column(modifier = Modifier.padding(16.dp)) {
                    // Language Switcher Row (Opens language selection modal)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .clickable {
                                scope.launch { drawerState.close() }
                                showLanguageDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(strings.drawerLanguage, fontSize = 13.sp, color = TextPrimary)
                        }
                        Text(
                            text = if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) "বাংলা" else "English",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Factory Reset Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CoralRed.copy(alpha = 0.12f))
                            .clickable {
                                scope.launch { drawerState.close() }
                                showFactoryResetDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = CoralRed, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(strings.drawerFactoryReset, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "⚡ ${strings.appTitle}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonCyan
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (state.isMcuOnline) ElectricGreen else CoralRed)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (state.isMcuOnline) strings.deviceOnline else strings.deviceOffline,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.isMcuOnline) ElectricGreen else CoralRed
                                    )

                                    val connectedWifi = if (state.connectionMode == com.shahriyar.smartwificharger.domain.model.ConnectionMode.FIREBASE_WAN ||
                                        state.connectionMode == com.shahriyar.smartwificharger.domain.model.ConnectionMode.OFFLINE ||
                                        state.connectionMode == com.shahriyar.smartwificharger.domain.model.ConnectionMode.ERROR) {
                                        null
                                    } else if (state.connectionMode == com.shahriyar.smartwificharger.domain.model.ConnectionMode.DIRECT_AP) {
                                        state.telemetry.apSsid.ifBlank { "SMART CHARGE CONTROLLER" }
                                    } else if (state.connectionMode == com.shahriyar.smartwificharger.domain.model.ConnectionMode.LOCAL_LAN &&
                                        state.isMcuOnline &&
                                        state.telemetry.staSsid.isNotBlank() &&
                                        state.telemetry.staSsid != "None") {
                                        state.telemetry.staSsid
                                    } else {
                                        null
                                    }
                                    if (connectedWifi != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "•", fontSize = 10.sp, color = TextMuted)
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Icon(Icons.Default.Wifi, contentDescription = null, tint = ElectricGreen, modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = connectedWifi,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElectricGreen,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            ConnectionBadge(mode = state.connectionMode, isMcuOnline = state.isMcuOnline)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer Menu",
                                tint = NeonCyan
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark
                    ),
                    actions = {
                        // Refresh / Discovery Action with Animation
                        IconButton(onClick = { viewModel.triggerManualProbe() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Network",
                                tint = NeonCyan,
                                modifier = Modifier.rotate(if (state.isScanning) rotation else 0f)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = NeonCyan,
                    modifier = Modifier.navigationBarsPadding(),
                    tonalElevation = 8.dp
                ) {
                    primaryBottomScreens.forEach { screen ->
                        val isSelected = currentScreen == screen
                        val labelText = when (screen) {
                            Screen.Dashboard -> strings.navDashboard
                            Screen.Charging -> strings.navModes
                            Screen.Schedule -> strings.navSchedule
                            Screen.Statistics -> strings.navStats
                            Screen.Settings -> strings.navSettings
                            Screen.Devices -> strings.navDevices
                            else -> screen.titleKey
                        }

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (isSetupRequired && screen != Screen.Network) {
                                    viewModel.showMessage("⚡ Wi-Fi Setup Required: Please configure home Wi-Fi first.")
                                } else {
                                    currentScreen = screen
                                }
                            },
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = labelText,
                                    tint = if (isSelected) NeonCyan else TextMuted
                                )
                            },
                            label = {
                                Text(
                                    text = labelText,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) NeonCyan else TextMuted
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = SurfaceCard,
                                selectedIconColor = NeonCyan,
                                unselectedIconColor = TextMuted,
                                selectedTextColor = NeonCyan,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            val modifier = Modifier.padding(innerPadding)
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    state = state,
                    onToggleRelay = { viewModel.toggleRelay() },
                    onToggleAuto = { viewModel.toggleAutoMode(it) },
                    onSetTimer = { viewModel.setTimer(it) },
                    onResetSchedule = { viewModel.resetDailySchedule() },
                    modifier = modifier
                )
                Screen.Statistics -> StatisticsScreen(
                    state = state,
                    onResetBilling = { viewModel.resetBillingStats() },
                    modifier = modifier
                )
                Screen.Charging -> ChargingScreen(
                    state = state,
                    onToggleAuto = { viewModel.toggleAutoMode(it) },
                    onToggleSchedule = { viewModel.toggleScheduleMode(it) },
                    onToggleSolar = { viewModel.toggleSolarBackup(it) },
                    onToggleRelay = { viewModel.toggleRelay() },
                    modifier = modifier
                )
                Screen.Schedule -> SchedulerScreen(
                    state = state,
                    onSaveConfig = { viewModel.saveConfiguration(it) },
                    onSaveSchedule = { startH, startM, stopH, stopM, enabled, days ->
                        val tz = state.config.timezoneOffsetHours.takeIf { it != 0 } ?: 6
                        viewModel.saveSchedule(startH, startM, stopH, stopM, enabled, tz, days)
                    },
                    onResetSchedule = { viewModel.resetDailySchedule() },
                    modifier = modifier
                )
                Screen.Devices -> DevicesScreen(
                    state = state,
                    onDiscoverLocal = { viewModel.discoverLocalEsp() },
                    onSwitchDevice = { viewModel.switchDevice(it) },
                    modifier = modifier
                )
                Screen.Automation -> AutomationScreen(
                    state = state,
                    onSaveConfig = { viewModel.saveConfiguration(it) },
                    modifier = modifier
                )
                Screen.Billing -> BillingScreen(
                    state = state,
                    onSaveConfig = { viewModel.saveConfiguration(it) },
                    modifier = modifier
                )
                Screen.Weather -> WeatherScreen(
                    state = state,
                    onSyncWeather = { lat, lon, name -> viewModel.syncWeather(lat, lon, name) },
                    onToggleSolar = { viewModel.toggleSolarBackup(it) },
                    onSetWeatherMode = { viewModel.setWeatherMode(it) },
                    modifier = modifier
                )
                Screen.Alerts -> AlertsScreen(
                    state = state,
                    modifier = modifier
                )
                Screen.Network -> NetworkScreen(
                    state = state,
                    onSaveConfig = { viewModel.saveConfiguration(it) },
                    onScanWifi = { viewModel.scanWifiNetworks() },
                    onConnectWifi = { ssid, pass -> viewModel.connectDeviceToWifi(ssid, pass) },
                    isSetupMode = isSetupRequired,
                    onBypassSetup = {
                        isSetupBypassed = true
                        currentScreen = Screen.Dashboard
                    },
                    modifier = modifier
                )
                Screen.Diagnostics -> DiagnosticsScreen(
                    state = state,
                    onTestRelay = { viewModel.testRelayDiagnostic() },
                    onManualProbe = { viewModel.triggerManualProbe() },
                    onToggleRelay = { viewModel.toggleRelay() },
                    modifier = modifier
                )
                Screen.Sync -> SyncScreen(
                    state = state,
                    modifier = modifier
                )
                Screen.Settings -> SettingsScreen(
                    onFactoryReset = { showFactoryResetDialog = true },
                    state = state,
                    onSaveConfig = { viewModel.saveConfiguration(it) },
                    onUpdateConnection = { ip, fb, dev -> viewModel.updateConnectionSettings(ip, fb, dev) },
                    onDiscoverLocal = { viewModel.discoverLocalEsp() },
                    onReboot = { viewModel.rebootDevice() },
                    modifier = modifier
                )
                Screen.About -> AboutScreen(
                    state = state,
                    modifier = modifier
                )
            }
        }
    }
}
