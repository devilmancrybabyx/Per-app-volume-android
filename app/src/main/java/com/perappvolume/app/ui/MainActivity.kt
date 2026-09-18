package com.perappvolume.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.perappvolume.app.service.OverlayService
import com.perappvolume.app.ui.theme.PerAppVolumeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start (or resume) the background pipeline. The service itself is a
        // no-op for anything sensitive until the user has granted the
        // relevant permissions via the onboarding flow.
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            PerAppVolumeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "onboarding") {
        composable("onboarding") { OnboardingScreen(onFinished = { navController.navigate("settings") }) }
        composable("settings") {
            SettingsScreen(
                onOpenAppList = { navController.navigate("app_list") },
                onOpenDiagnostics = { navController.navigate("diagnostics") },
                onOpenFloatingButtons = { navController.navigate("floating_buttons") }
            )
        }
        composable("app_list") { AppListScreen(onBack = { navController.popBackStack() }) }
        composable("diagnostics") { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
        composable("floating_buttons") { FloatingButtonSettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
