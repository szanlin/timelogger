package com.y3lc.timelogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.y3lc.timelogger.ui.MainTab
import com.y3lc.timelogger.ui.TimeLoggerUiState
import com.y3lc.timelogger.ui.TimeLoggerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: TimeLoggerViewModel by viewModels {
        TimeLoggerViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MaterialTheme {
                TimeLoggerApp(uiState, viewModel::selectTab)
            }
        }
    }
}

@Composable
private fun TimeLoggerApp(uiState: TimeLoggerUiState, onTabSelected: (MainTab) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Text(tab.label.take(1)) },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("nav-${tab.name.lowercase()}"),
                    )
                }
            }
        },
    ) { padding ->
        MainScreen(uiState, padding)
    }
}

@Composable
private fun MainScreen(uiState: TimeLoggerUiState, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Text(
            uiState.selectedTab.label,
            modifier = Modifier.testTag("screen-title"),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else if (uiState.errorMessage != null) {
            Text(uiState.errorMessage)
        }
    }
}
