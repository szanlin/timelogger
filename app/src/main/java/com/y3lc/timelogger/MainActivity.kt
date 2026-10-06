package com.y3lc.timelogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.y3lc.timelogger.ui.MainTab
import com.y3lc.timelogger.ui.TimeLoggerUiState
import com.y3lc.timelogger.ui.TimeLoggerViewModel
import com.y3lc.timelogger.ui.TypeSaveResult
import com.y3lc.timelogger.ui.RecordScreen
import com.y3lc.timelogger.ui.StatisticsScreen
import com.y3lc.timelogger.ui.StatisticsRange
import com.y3lc.timelogger.ui.SettingsScreen
import com.y3lc.timelogger.ui.SessionTimeEdit
import com.y3lc.timelogger.ui.theme.AppPageContainer
import com.y3lc.timelogger.ui.theme.TimeLoggerTheme
import java.time.DayOfWeek
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: TimeLoggerViewModel by viewModels {
        TimeLoggerViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.onForeground()
                    while (true) {
                        viewModel.updateClock()
                        delay(1_000)
                    }
                }
            }
            TimeLoggerTheme {
                TimeLoggerApp(
                    uiState,
                    viewModel::selectTab,
                    viewModel::toggleActivity,
                    viewModel::selectStatisticsRange,
                    viewModel::selectStatisticsAnchorDate,
                    viewModel::refresh,
                    viewModel::saveStatisticsZone,
                    viewModel::setWeekStart,
                    viewModel::createActivityType,
                    viewModel::updateActivityType,
                    viewModel::moveActivityType,
                    viewModel::archiveActivityType,
                    viewModel::saveSessionTimes,
                    viewModel::deleteActivitySession,
                )
            }
        }
    }
}

@Composable
private fun TimeLoggerApp(
    uiState: TimeLoggerUiState,
    onTabSelected: (MainTab) -> Unit,
    onToggle: (String) -> Unit,
    onRangeSelected: (StatisticsRange) -> Unit,
    onAnchorDateSelected: (java.time.LocalDate) -> Unit,
    onRetry: () -> Unit,
    onZoneSaved: (String?) -> Unit,
    onWeekStartChanged: (DayOfWeek) -> Unit,
    onTypeCreated: (String, String, Long, (TypeSaveResult) -> Unit) -> Unit,
    onTypeUpdated: (String, String, String, Long, (TypeSaveResult) -> Unit) -> Unit,
    onTypeMoved: (String, Int) -> Unit,
    onTypeArchived: (String) -> Unit,
    onSessionSaved: (SessionTimeEdit, (String?) -> Unit) -> Unit,
    onSessionDeleted: (String) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(painterResource(tabIcon(tab)), contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        modifier = Modifier
                            .testTag("nav-${tab.name.lowercase()}")
                            .semantics { contentDescription = tab.label },
                    )
                }
            }
        },
    ) { padding ->
        MainScreen(uiState, padding, onToggle, onRangeSelected, onAnchorDateSelected, onRetry, onZoneSaved, onWeekStartChanged, onTypeCreated, onTypeUpdated, onTypeMoved, onTypeArchived, onSessionSaved, onSessionDeleted)
    }
}

private fun tabIcon(tab: MainTab): Int = when (tab) {
    MainTab.RECORD -> R.drawable.ic_tab_record
    MainTab.STATISTICS -> R.drawable.ic_tab_statistics
    MainTab.SETTINGS -> R.drawable.ic_tab_settings
}

@Composable
private fun MainScreen(
    uiState: TimeLoggerUiState,
    padding: PaddingValues,
    onToggle: (String) -> Unit,
    onRangeSelected: (StatisticsRange) -> Unit,
    onAnchorDateSelected: (java.time.LocalDate) -> Unit,
    onRetry: () -> Unit,
    onZoneSaved: (String?) -> Unit,
    onWeekStartChanged: (DayOfWeek) -> Unit,
    onTypeCreated: (String, String, Long, (TypeSaveResult) -> Unit) -> Unit,
    onTypeUpdated: (String, String, String, Long, (TypeSaveResult) -> Unit) -> Unit,
    onTypeMoved: (String, Int) -> Unit,
    onTypeArchived: (String) -> Unit,
    onSessionSaved: (SessionTimeEdit, (String?) -> Unit) -> Unit,
    onSessionDeleted: (String) -> Unit,
) {
    AppPageContainer(Modifier.padding(padding)) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
        }
        if (uiState.errorMessage != null) {
            Text(uiState.errorMessage)
            TextButton(onClick = onRetry, enabled = !uiState.isSaving, modifier = Modifier.testTag("retry")) { Text("重试") }
        }
        if (!uiState.isLoading) {
            when (uiState.selectedTab) {
                MainTab.RECORD -> RecordScreen(uiState, onToggle, onSessionSaved, Modifier.weight(1f).padding(top = 16.dp))
                MainTab.STATISTICS -> StatisticsScreen(uiState, onRangeSelected, onAnchorDateSelected, onSessionDeleted, Modifier.weight(1f).padding(top = 16.dp))
                MainTab.SETTINGS -> SettingsScreen(
                    uiState,
                    onZoneSaved,
                    onWeekStartChanged,
                    onTypeCreated,
                    onTypeUpdated,
                    onTypeMoved,
                    onTypeArchived,
                    Modifier.weight(1f).padding(top = 16.dp),
                )
            }
        }
    }
}
