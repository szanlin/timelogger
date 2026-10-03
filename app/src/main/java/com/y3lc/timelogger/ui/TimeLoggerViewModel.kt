package com.y3lc.timelogger.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import com.y3lc.timelogger.data.repository.RoomActivityRepository
import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import com.y3lc.timelogger.domain.usecase.StartActivitySessionUseCase
import com.y3lc.timelogger.domain.usecase.StartActivitySessionResult
import com.y3lc.timelogger.domain.usecase.StopActivitySessionUseCase
import com.y3lc.timelogger.domain.usecase.StopActivitySessionResult
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimeLoggerViewModel(private val openDatabase: () -> TimeLoggerDatabase) : ViewModel() {
    private var database: TimeLoggerDatabase? = null
    private var repository: RoomActivityRepository? = null
    private data class PendingOperation(val errorMessage: String, val action: (RoomActivityRepository) -> Unit)
    private var failedOperation: PendingOperation? = null
    private var types: List<ActivityType> = emptyList()
    private var sessions: List<ActivitySession> = emptyList()
    private val mutableUiState = MutableStateFlow(TimeLoggerUiState())
    val uiState: StateFlow<TimeLoggerUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: MainTab) {
        mutableUiState.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        failedOperation?.let {
            runOperation(it.errorMessage, it.action)
            return
        }
        runOperation("无法加载记录，请重试") { repository ->
            repository.seedDefaultActivityTypes(Instant.now())
        }
    }

    fun onForeground() {
        if (failedOperation == null) refresh()
    }

    fun selectStatisticsRange(range: StatisticsRange) {
        mutableUiState.update { it.copy(statisticsRange = range) }
        updateClock()
    }

    fun toggleActivity(activityTypeId: String) {
        if (uiState.value.isSaving || uiState.value.isLoading || failedOperation != null) return
        val type = uiState.value.activityTypes.firstOrNull { it.id == activityTypeId } ?: return
        val shouldStop = type.isRunning
        runOperation("记录未能保存，请重试") { repository ->
            val now = Instant.now()
            repository.inTransaction {
                if (!shouldStop) {
                    val result = StartActivitySessionUseCase(repository)(activityTypeId, now, ZoneId.systemDefault())
                    check(result is StartActivitySessionResult.Started || result == StartActivitySessionResult.AlreadyActive)
                } else {
                    val result = StopActivitySessionUseCase(repository)(activityTypeId, now)
                    check(result is StopActivitySessionResult.Stopped || result == StopActivitySessionResult.NotActive)
                }
            }
        }
    }

    private fun runOperation(errorMessage: String, operation: (RoomActivityRepository) -> Unit) {
        if (uiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val currentRepository = getRepository()
                    operation(currentRepository)
                    currentRepository.inTransaction {
                        currentRepository.getActivityTypes() to currentRepository.getSessions()
                    }
                }
                types = snapshot.first
                sessions = snapshot.second
                failedOperation = null
                mutableUiState.update { it.copy(isLoading = false, errorMessage = null) }
                updateClock()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                failedOperation = PendingOperation(errorMessage, operation)
                mutableUiState.update { it.copy(isLoading = false, errorMessage = errorMessage) }
            } finally {
                mutableUiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun getRepository(): RoomActivityRepository {
        repository?.let { return it }
        val openedDatabase = openDatabase()
        database = openedDatabase
        return RoomActivityRepository(openedDatabase).also { repository = it }
    }

    fun updateClock() {
        if (uiState.value.isLoading) return
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val date = now.atZone(zone).toLocalDate()
        val activeByType = sessions.filter { it.endedAtUtc == null }.associateBy { it.activityTypeId }
        mutableUiState.update { state ->
            state.copy(
                statisticsZoneId = zone,
                activityTypes = mapActivityTypes(types, sessions).map { item ->
                    item.copy(runningDuration = activeByType[item.id]?.let {
                        Duration.between(it.startedAtUtc, now).coerceAtLeast(Duration.ZERO)
                    } ?: Duration.ZERO)
                },
                today = buildPeriodSummary(types, sessions, StatisticsRange.DAY, date, zone, now),
                statistics = buildPeriodSummary(types, sessions, state.statisticsRange, date, zone, now),
            )
        }
    }

    override fun onCleared() {
        database?.close()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TimeLoggerViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return TimeLoggerViewModel { TimeLoggerDatabase.open(context.applicationContext) } as T
        }
    }
}
