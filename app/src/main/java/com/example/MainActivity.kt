package com.example

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.banner.BannerDialog
import com.example.banner.BannerManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MartiViewModel
import com.example.ui.screens.AnalysisScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LockScreenGate
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.WeeklyReportScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        
        enableEdgeToEdge()
        setContent {
            val viewModel: MartiViewModel = viewModel()
            val profile by viewModel.profile.collectAsState()
            val themeMode = profile?.themeMode ?: "AUTO"

            val isDarkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> { // AUTO (GPS dynamic color transition - time-based)
                    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    hour < 7 || hour >= 19
                }
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()
                val isUnlocked by viewModel.isUnlocked.collectAsState()
                
                val activeBanner by BannerManager.activeBanner.collectAsState()

                LaunchedEffect(Unit) {
                    BannerManager.checkAndFetchBanner(this@MainActivity)
                }

                LaunchedEffect(profile) {
                    val p = profile
                    if (p != null) {
                        if (p.appPassword.isEmpty()) {
                            viewModel.setUnlocked(true)
                        } else {
                            viewModel.setUnlocked(false)
                        }
                    }
                }

                val items = listOf(
                    NavigationItem.Dashboard,
                    NavigationItem.WeeklyReport,
                    NavigationItem.Analysis,
                    NavigationItem.Profile
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "KANATLI SÜRÜCÜ ASİSTANI",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp
                                    )
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route

                            items.forEach { item ->
                                NavigationBarItem(
                                    icon = { Icon(item.icon, contentDescription = item.title) },
                                    label = { 
                                        Text(
                                            text = item.title, 
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp,
                                                letterSpacing = (-0.3).sp
                                            ),
                                            maxLines = 2,
                                            minLines = 2,
                                            textAlign = TextAlign.Center,
                                            softWrap = true,
                                            overflow = TextOverflow.Visible
                                        ) 
                                    },
                                    alwaysShowLabel = true,
                                    selected = currentRoute == item.route,
                                    onClick = {
                                        if (currentRoute != item.route) {
                                            if (item == NavigationItem.Dashboard || item == NavigationItem.Profile) {
                                                val p = profile
                                                if (p != null && p.appPassword.isNotEmpty()) {
                                                    viewModel.setUnlocked(false)
                                                }
                                            }
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("nav_item_${item.route}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavigationItem.Dashboard.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(NavigationItem.Dashboard.route) {
                            DashboardScreen(viewModel = viewModel)
                        }
                        composable(NavigationItem.WeeklyReport.route) {
                            if (profile != null && profile!!.appPassword.isNotEmpty() && !isUnlocked) {
                                LockScreenGate(viewModel = viewModel, profile = profile!!)
                            } else {
                                WeeklyReportScreen(viewModel = viewModel)
                            }
                        }
                        composable(NavigationItem.Analysis.route) {
                            if (profile != null && profile!!.appPassword.isNotEmpty() && !isUnlocked) {
                                LockScreenGate(viewModel = viewModel, profile = profile!!)
                            } else {
                                AnalysisScreen(viewModel = viewModel)
                            }
                        }
                        composable(NavigationItem.Profile.route) {
                            ProfileScreen(viewModel = viewModel)
                        }
                    }
                }

                activeBanner?.let { banner ->
                    BannerDialog(
                        banner = banner,
                        onDismiss = { BannerManager.dismissBanner() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, com.example.services.MartiBackgroundService::class.java).apply {
            action = com.example.services.MartiBackgroundService.ACTION_HIDE_OVERLAY
        }
        try { startService(intent) } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        val intent = Intent(this, com.example.services.MartiBackgroundService::class.java).apply {
            action = com.example.services.MartiBackgroundService.ACTION_SHOW_OVERLAY
        }
        try { startService(intent) } catch (e: Exception) {}
    }
}

sealed class NavigationItem(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : NavigationItem("dashboard", "Ana Sayfa", Icons.Default.Dashboard)
    object WeeklyReport : NavigationItem("weekly_report", "Seyir Raporu", Icons.AutoMirrored.Filled.Assignment)
    object Analysis : NavigationItem("analysis", "Analiz", Icons.Default.BarChart)
    object Profile : NavigationItem("profile", "Profil", Icons.Default.Person)
}
