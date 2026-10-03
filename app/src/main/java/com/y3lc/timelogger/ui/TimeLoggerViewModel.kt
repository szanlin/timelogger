package com.y3lc.timelogger.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.y3lc.timelogger.data.local.TimeLoggerDatabase
import com.y3lc.timelogger.data.repository.RoomActivityRepository
import com.y3lc.timelogger.domain.model.ActivitySession
import com.y3lc.timelogger.domain.model.ActivityType
import com.y3lc.timelogger.domain.usecase.ArchiveActivityTypeResult
import com.y3lc.timelogger.domain.usecase.ArchiveActivityTypeUseCase
import com.y3lc.timelogger.domain.usecase.StartActivitySessionUseCase
import com.y3lc.timelogger.domain.usecase.StartActivitySessionResult
import com.y3lc.timelogger.domain.usecase.StopActivitySessionUseCase
import com.y3lc.timelogger.domain.usecase.StopActivitySessionResult
import com.y3lc.timelogger.domain.usecase.EditActivitySessionUseCase
import com.y3lc.timelogger.domain.usecase.EditActivitySessionResult
import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimeLoggerViewModel(
    private val openDatabase: () -> TimeLoggerDatabase,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    constructor(openDatabase: () -> TimeLoggerDatabase) : this(openDatabase, InMemorySettingsStore())

    private var database: TimeLoggerDatabase? = null
    private var repository: RoomActivityRepository? = null
    private data class PendingOperation(val errorMessage: String, val action: ((RoomActivityRepository) -> Unit)?)
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

    fun saveStatisticsZone(zoneId: String?) {
        val normalized = zoneId?.trim()
        if (normalized != null && runCatching { ZoneId.of(normalized) }.isFailure) {
            mutableUiState.update { it.copy(errorMessage = "请输入有效的 IANA 时区") }
            return
        }
        runOperation("统计时区未能保存，请重试") {
            settingsStore.save(settingsStore.load().copy(fixedZoneId = normalized))
        }
    }

    fun setWeekStart(dayOfWeek: DayOfWeek) {
        if (dayOfWeek != DayOfWeek.MONDAY && dayOfWeek != DayOfWeek.SUNDAY) return
        runOperation("每周起始日未能保存，请重试") {
            settingsStore.save(settingsStore.load().copy(firstDayOfWeek = dayOfWeek))
        }
    }

    fun createActivityType(name: String, iconKey: String, colorArgb: Long, onResult: (String?) -> Unit = {}) {
        val normalized = validateActivityType(name, iconKey) ?: run {
            onResult("请输入类型名称并选择图标")
            return
        }
        val id = UUID.randomUUID().toString()
        runOperation("类型未能新增，请重试", onResult) { repository ->
            repository.inTransaction {
                val now = Instant.now()
                val sortOrder = (repository.getActivityTypes().maxOfOrNull { it.sortOrder } ?: -1) + 1
                repository.insertActivityType(ActivityType(id, normalized, iconKey, colorArgb, false, sortOrder, now, now))
            }
        }
    }

    fun updateActivityType(id: String, name: String, iconKey: String, colorArgb: Long, onResult: (String?) -> Unit = {}) {
        val normalized = validateActivityType(name, iconKey) ?: run {
            onResult("请输入类型名称并选择图标")
            return
        }
        runOperation("类型未能更新，请重试", onResult) { repository ->
            repository.inTransaction {
                val existing = repository.getActivityTypeById(id)
                check(existing != null && !existing.isArchived) { "类型不存在或已归档" }
                repository.updateActivityType(existing.copy(name = normalized, iconKey = iconKey, colorArgb = colorArgb, updatedAtUtc = Instant.now()))
            }
        }
    }

    fun moveActivityType(id: String, direction: Int) {
        if (direction != -1 && direction != 1) return
        runOperation("类型排序未能保存，请重试") { repository ->
            repository.inTransaction {
                val active = repository.getActivityTypes().filterNot { it.isArchived }
                    .sortedWith(compareBy<ActivityType> { it.sortOrder }.thenBy { it.id })
                val index = active.indexOfFirst { it.id == id }
                val otherIndex = index + direction
                if (index >= 0 && otherIndex in active.indices) {
                    val now = Instant.now()
                    repository.updateActivityType(active[index].copy(sortOrder = active[otherIndex].sortOrder, updatedAtUtc = now))
                    repository.updateActivityType(active[otherIndex].copy(sortOrder = active[index].sortOrder, updatedAtUtc = now))
                }
            }
        }
    }

    fun archiveActivityType(id: String) {
        runOperation("类型未能归档，请重试") { repository ->
            check(ArchiveActivityTypeUseCase(repository)(id, Instant.now()) == ArchiveActivityTypeResult.Archived)
        }
    }

    private fun validateActivityType(name: String, iconKey: String): String? {
        val normalized = name.trim()
        if (normalized.isEmpty() || iconKey !in setOf("sleep", "walk", "cycle", "meeting")) {
            mutableUiState.update { it.copy(errorMessage = "请输入类型名称并选择图标") }
            return null
        }
        return normalized
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

    fun saveSessionTimes(edit: SessionTimeEdit, onResult: (String?) -> Unit) {
        if (uiState.value.isSaving) {
            onResult("正在保存，请稍候")
            return
        }
        if (failedOperation != null) {
            onResult("请先处理当前错误后再保存")
            return
        }
        mutableUiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            var errorMessage: String? = null
            try {
                val refreshedSessions = withContext(Dispatchers.IO) {
                    val currentRepository = getRepository()
                    when (EditActivitySessionUseCase(currentRepository)(edit.id, edit.startedAtUtc, edit.endedAtUtc, Instant.now())) {
                        EditActivitySessionResult.InvalidEndTime -> errorMessage = "结束时间必须晚于开始时间"
                        EditActivitySessionResult.NotFound -> errorMessage = "记录已不存在，请重新打开历史记录"
                        EditActivitySessionResult.StillRunning -> errorMessage = "请先结束进行中的记录"
                        is EditActivitySessionResult.Updated -> Unit
                    }
                    currentRepository.getSessions()
                }
                sessions = refreshedSessions
                updateClock()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                errorMessage = "记录未能保存或刷新，请重试"
            } finally {
                mutableUiState.update { it.copy(isSaving = false) }
            }
            onResult(errorMessage)
        }
    }

    private fun runOperation(errorMessage: String, operation: ((RoomActivityRepository) -> Unit)?) {
        runOperation(errorMessage, {}, operation)
    }

    private fun runOperation(errorMessage: String, onResult: (String?) -> Unit, operation: ((RoomActivityRepository) -> Unit)?) {
        if (uiState.value.isSaving) {
            onResult("正在保存，请稍候")
            return
        }
        mutableUiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            var operationCompleted = false
            var resultError: String? = null
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val currentRepository = getRepository()
                    operation?.invoke(currentRepository)
                    operationCompleted = true
                    currentRepository.inTransaction {
                        Triple(currentRepository.getActivityTypes(), currentRepository.getSessions(), settingsStore.load())
                    }
                }
                types = snapshot.first
                sessions = snapshot.second
                failedOperation = null
                mutableUiState.update {
                    it.copy(isLoading = false, errorMessage = null, fixedStatisticsZoneId = snapshot.third.fixedZoneId, firstDayOfWeek = snapshot.third.firstDayOfWeek)
                }
                updateClock()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val retryErrorMessage = if (operationCompleted) "无法刷新记录，请重试" else errorMessage
                // 写入完成后只重试读取，避免重复新增、归档或移动类型。
                failedOperation = PendingOperation(retryErrorMessage, if (operationCompleted) null else operation)
                mutableUiState.update { it.copy(isLoading = false, errorMessage = retryErrorMessage) }
                resultError = retryErrorMessage
            } finally {
                mutableUiState.update { it.copy(isSaving = false) }
            }
            onResult(resultError)
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
        val stateSettings = uiState.value
        val zone = stateSettings.fixedStatisticsZoneId?.let(ZoneId::of) ?: ZoneId.systemDefault()
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
                managedActivityTypes = mapManagedActivityTypes(types, sessions),
                historySessions = sessions.mapNotNull { session ->
                    session.endedAtUtc?.let { end ->
                        HistorySessionItem(session.id, types.firstOrNull { it.id == session.activityTypeId }?.name ?: "未知类型", session.startedAtUtc, end)
                    }
                }.sortedWith(compareByDescending<HistorySessionItem> { it.startedAtUtc }.thenBy { it.id }),
                today = buildPeriodSummary(types, sessions, StatisticsRange.DAY, date, zone, now),
                statistics = buildPeriodSummary(types, sessions, state.statisticsRange, date, zone, now, state.firstDayOfWeek),
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
            return TimeLoggerViewModel(
                { TimeLoggerDatabase.open(context.applicationContext) },
                PreferencesSettingsStore(context.applicationContext),
            ) as T
        }
    }
}
