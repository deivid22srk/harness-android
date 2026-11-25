package dev.harness

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: HarnessViewModel by viewModels { HarnessViewModel.factory(application) }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Non-blocking: the agent runs regardless. */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val windowSize = calculateWindowSizeClass(this)
            val wideScreen = windowSize.widthSizeClass >= WindowWidthSizeClass.Medium

            // Deep link harness://settings/{categoria} (configurações).
            androidx.compose.runtime.LaunchedEffect(intent?.data) {
                intent?.data?.let { uri ->
                    if (uri.scheme == "harness" && uri.host == "settings") {
                        uri.lastPathSegment?.let { section ->
                            runCatching {
                                viewModel.openSettingsSection(
                                    dev.harness.SettingsSection.valueOf(section.uppercase()),
                                )
                            }
                        }
                    }
                }
            }

            // Resolve the theme from the user preference (system/light/dark).
            val darkTheme = when (uiState.themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            dev.harness.ui.theme.HarnessThemeProvider(
                darkTheme = darkTheme,
                dynamicColor = uiState.dynamicColor,
                fontScale = uiState.fontScale,
            ) {
                Box(Modifier.fillMaxSize()) {
                    HarnessRoot(
                        uiState = uiState,
                        viewModel = viewModel,
                        wideScreen = wideScreen,
                        onRequestBatteryExemption = ::openBatterySettings,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openBatterySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:$packageName")),
            )
        }
    }
}
