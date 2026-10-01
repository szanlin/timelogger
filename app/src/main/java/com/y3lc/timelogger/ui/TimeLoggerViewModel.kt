package com.y3lc.timelogger.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import com.y3lc.timelogger.data.repository.RoomActivityRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimeLoggerViewModel(private val database: TimeLoggerDatabase) : ViewModel() {
    private val repository = RoomActivityRepository(database)
    private val mutableUiState = MutableStateFlow(TimeLoggerUiState())
    val uiState: StateFlow<TimeLoggerUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: MainTab) {
        mutableUiState.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val activityTypes = withContext(Dispatchers.IO) {
                    repository.seedDefaultActivityTypes(Instant.now())
                    mapActivityTypes(repository.getActivityTypes(), repository.getActiveSessions())
                }
                mutableUiState.update { it.copy(activityTypes = activityTypes, isLoading = false, errorMessage = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableUiState.update {
                    it.copy(isLoading = false, errorMessage = "无法加载活动类型")
                }
            }
        }
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TimeLoggerViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return TimeLoggerViewModel(TimeLoggerDatabase.open(context)) as T
        }
    }
}
