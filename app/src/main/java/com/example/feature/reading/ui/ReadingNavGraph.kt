package com.example.feature.reading.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.common.UiState
import com.example.feature.reading.PassageReaderViewModel
import com.example.feature.reading.ReadingAdminViewModel
import com.example.feature.reading.ReadingDashboardViewModel
import com.example.feature.reading.ReadingSettingsViewModel

private object ReadingRoutes {
    const val DASHBOARD = "reading_dashboard"
    const val READER = "reading_reader"
    const val SETTINGS = "reading_settings"
    const val ADMIN = "reading_admin"
}

@Composable
fun ReadingRoute(
    isAdmin: Boolean,
    onBack: () -> Unit
) {
    val navController = rememberNavController()
    val dashboardVm: ReadingDashboardViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = ReadingRoutes.DASHBOARD
    ) {
        composable(ReadingRoutes.DASHBOARD) {
            ReadingDashboardScreen(
                viewModel = dashboardVm,
                isAdmin = isAdmin,
                onBack = onBack,
                onOpenPassage = { passageId ->
                    navController.navigate("${ReadingRoutes.READER}/${Uri.encode(passageId)}")
                },
                onOpenSettings = {
                    navController.navigate(ReadingRoutes.SETTINGS)
                },
                onOpenAdmin = {
                    if (isAdmin) navController.navigate(ReadingRoutes.ADMIN)
                }
            )
        }

        composable(
            route = "${ReadingRoutes.READER}/{passageId}",
            arguments = listOf(navArgument("passageId") { type = NavType.StringType })
        ) { entry ->
            val passageId = entry.arguments?.getString("passageId").orEmpty()
            val readerVm: PassageReaderViewModel = viewModel(key = "reader_$passageId")
            val dashState by dashboardVm.dashboardState.collectAsStateWithLifecycle()
            val stats = (dashState as? UiState.Success)?.data?.stats
            LaunchedEffect(passageId, stats?.todayMinutes, stats?.dailyGoalMinutes) {
                stats?.let { readerVm.setDailyGoalContext(it.todayMinutes, it.dailyGoalMinutes) }
            }
            PassageReaderScreen(
                passageId = passageId,
                viewModel = readerVm,
                onBack = {
                    navController.popBackStack()
                    dashboardVm.loadAll(silent = true)
                }
            )
        }

        composable(ReadingRoutes.SETTINGS) {
            val settingsVm: ReadingSettingsViewModel = viewModel()
            ReadingSettingsScreen(
                viewModel = settingsVm,
                onBack = {
                    navController.popBackStack()
                    dashboardVm.loadAll(silent = true)
                }
            )
        }

        composable(ReadingRoutes.ADMIN) {
            if (isAdmin) {
                val adminVm: ReadingAdminViewModel = viewModel()
                ReadingAdminScreen(
                    vm = adminVm,
                    onBack = {
                        navController.popBackStack()
                        dashboardVm.loadAll(silent = true)
                    }
                )
            }
        }
    }
}
