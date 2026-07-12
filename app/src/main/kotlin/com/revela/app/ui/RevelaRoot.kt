package com.revela.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.revela.app.AppContainer

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data object Dashboard : Screen
    data object Insights : Screen
    data object Modes : Screen
    data object Chat : Screen
    data object Settings : Screen
    data object Sources : Screen
    data object ManageData : Screen
    data object Audit : Screen
    data object DebugLog : Screen
}

@Composable
fun RevelaRoot(container: AppContainer) {
    var screen: Screen by remember {
        mutableStateOf(if (container.settings.onboardingComplete) Screen.Home else Screen.Onboarding)
    }

    when (screen) {
        Screen.Onboarding -> OnboardingScreen(
            container = container,
            onFinished = { screen = Screen.Home },
        )
        Screen.Home -> HomeScreen(
            container = container,
            onOpenDashboard = { screen = Screen.Dashboard },
            onOpenInsights = { screen = Screen.Insights },
            onOpenModes = { screen = Screen.Modes },
            onOpenChat = { screen = Screen.Chat },
            onOpenSettings = { screen = Screen.Settings },
            onOpenDebugLog = { screen = Screen.DebugLog },
        )
        Screen.Dashboard -> DashboardScreen(
            container = container,
            onBack = { screen = Screen.Home },
        )
        Screen.Insights -> InsightsScreen(
            container = container,
            onBack = { screen = Screen.Home },
        )
        Screen.Modes -> ModesScreen(
            container = container,
            onBack = { screen = Screen.Home },
        )
        Screen.Chat -> ChatScreen(
            container = container,
            onBack = { screen = Screen.Home },
        )
        Screen.Settings -> SettingsScreen(
            container = container,
            onBack = { screen = Screen.Home },
            onOpenAudit = { screen = Screen.Audit },
            onOpenSources = { screen = Screen.Sources },
            onOpenManageData = { screen = Screen.ManageData },
        )
        Screen.Sources -> SourcesScreen(
            container = container,
            onBack = { screen = Screen.Settings },
        )
        Screen.ManageData -> ManageDataScreen(
            container = container,
            onBack = { screen = Screen.Settings },
        )
        Screen.Audit -> AuditScreen(
            container = container,
            onBack = { screen = Screen.Settings },
        )
        Screen.DebugLog -> DebugLogScreen(
            container = container,
            onBack = { screen = Screen.Home },
        )
    }
}
