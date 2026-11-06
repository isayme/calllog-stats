package org.isayme.callstats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.isayme.callstats.data.SettingsRepository
import org.isayme.callstats.ui.AboutScreen
import org.isayme.callstats.ui.CallSimulationScreen
import org.isayme.callstats.ui.MainScreen
import org.isayme.callstats.ui.StatsPreviewScreen
import org.isayme.callstats.ui.theme.CallStatsTheme

private enum class Screen {
    MAIN,
    ABOUT,
    STATS_PREVIEW,
    CALL_SIMULATION,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallStatsTheme {
                val repository = remember { SettingsRepository(applicationContext) }
                var screenName by rememberSaveable { mutableStateOf(Screen.MAIN.name) }
                val screen = Screen.valueOf(screenName)

                when (screen) {
                    Screen.MAIN -> MainScreen(
                        repository = repository,
                        onOpenAbout = { screenName = Screen.ABOUT.name },
                        onOpenStatsPreview = { screenName = Screen.STATS_PREVIEW.name },
                        onOpenCallSimulation = { screenName = Screen.CALL_SIMULATION.name },
                    )

                    Screen.ABOUT -> AboutScreen(onBack = { screenName = Screen.MAIN.name })

                    Screen.STATS_PREVIEW -> StatsPreviewScreen(
                        repository = repository,
                        onBack = { screenName = Screen.MAIN.name },
                    )

                    Screen.CALL_SIMULATION -> CallSimulationScreen(
                        onBack = { screenName = Screen.MAIN.name },
                    )
                }
            }
        }
    }
}
