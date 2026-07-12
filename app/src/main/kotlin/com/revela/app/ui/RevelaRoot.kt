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
            onOpenDebugLog = { screen = Screen.DebugLog },
        )
        Screen.DebugLog -> DebugLogScreen(
            container = container,
            onBack = { screen = Screen.Home },
        )
    }
}
