package com.y3lc.timelogger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import com.y3lc.timelogger.domain.time.LocalWallTimeResult
import com.y3lc.timelogger.domain.time.StatisticsPeriod
import com.y3lc.timelogger.domain.time.resolveLocalWallTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collect

private val editTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS")
private val historyDisplayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")

@Composable
fun StatisticsMonthHistory(
    state: TimeLoggerUiState,
    onAnchorDateSelected: (LocalDate) -> Unit,
    selectedDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val today = LocalDate.now(state.statisticsZoneId)
    val anchorMonth = YearMonth.from(state.statisticsAnchorDate ?: today)
    val anchorPage = getMonthPage(anchorMonth)
    val pagerState = rememberPagerState(initialPage = anchorPage, pageCount = { 9999 * 12 })
    LaunchedEffect(anchorMonth, pagerState) {
        pagerState.scrollToPage(anchorPage)
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != anchorPage) onAnchorDateSelected(getPageMonth(page).atDay(1))
        }
    }
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(14.dp))
            .background(colorScheme.surfaceContainer).border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .testTag("history-month-pager"),
    ) { page ->
        val month = getPageMonth(page)
        HistoryCalendarPage(
            month = month,
            today = today,
            firstDayOfWeek = state.firstDayOfWeek,
            cells = buildHistoryMonthActivityCells(state.historySessions, month, state.statisticsZoneId, state.firstDayOfWeek),
            isCurrentPage = page == pagerState.currentPage,
            selectedDate = selectedDate,
            onDateSelected = onDateSelected,
        )
    }
}

@Composable
fun HistoryRecordRow(session: HistorySessionItem, index: Int, zone: ZoneId, enabled: Boolean, onEdit: () -> Unit) {
    val displayFormatter = historyDisplayFormatter.withZone(zone)
    Card(
        onClick = onEdit,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("history-edit-$index"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(session.name)
            Text(displayFormatter.format(session.startedAtUtc))
            Text("至 ${displayFormatter.format(session.endedAtUtc)}")
        }
    }
}

private fun getMonthPage(month: YearMonth): Int = (month.year - 1) * 12 + month.monthValue - 1

private fun getPageMonth(page: Int): YearMonth = YearMonth.of(page / 12 + 1, page % 12 + 1)

internal fun getSessionsOverlappingDay(sessions: List<HistorySessionItem>, date: LocalDate, zone: ZoneId): List<HistorySessionItem> {
    val interval = StatisticsPeriod.day(date, zone).interval
    return sessions.filter { it.startedAtUtc < interval.endExclusive && it.endedAtUtc > interval.start }
}

@Composable
private fun HistoryCalendarPage(
    month: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    cells: List<HistoryActivityCell>,
    isCurrentPage: Boolean,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
) {
    val cellsByDate = cells.associateBy { it.date }
    val colorScheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    val activeColor = if (isDark) Color(0xFF674535) else Color(0xFFFFE7D9)
    val activeBorderColor = if (isDark) Color(0xFFE7AC8A) else Color(0xFFC7663D)
    val activeTextColor = if (isDark) Color(0xFFFFEADB) else Color(0xFF74371F)
    val emptyColor = colorScheme.onSurfaceVariant
    val futureColor = colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val firstDayOffset = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val calendarWidth = maxWidth.coerceAtLeast(360.dp)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("history-month-horizontal-scroll")) {
            Column(
                modifier = Modifier.width(calendarWidth).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "${month.year}年${month.monthValue}月",
                        modifier = if (isCurrentPage) Modifier.testTag("history-month-label") else Modifier,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { dayIndex ->
                        val dayOfWeek = DayOfWeek.of((firstDayOfWeek.value - 1 + dayIndex) % 7 + 1)
                        Box(Modifier.weight(1f).height(20.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "一二三四五六日"[dayOfWeek.value - 1].toString(),
                                modifier = if (isCurrentPage) Modifier.testTag("history-weekday-$dayIndex") else Modifier,
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                repeat(6) { weekIndex ->
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { dayIndex ->
                            val dayOfMonth = weekIndex * 7 + dayIndex - firstDayOffset + 1
                            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                                if (dayOfMonth in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(dayOfMonth)
                                    val count = cellsByDate[date]?.recordCount ?: 0
                                    val isFuture = date.isAfter(today)
                                    val description = when {
                                        count > 0 -> "$date，$count 条记录" + if (isFuture) "，未来" else ""
                                        isFuture -> "$date，未来"
                                        else -> "$date，无记录"
                                    }
                                    val shape = RoundedCornerShape(9.dp)
                                    val modifier = Modifier.fillMaxWidth().height(48.dp)
                                        .then(if (count > 0 && isCurrentPage) Modifier.clickable { onDateSelected(date) } else Modifier)
                                        .then(if (isCurrentPage) Modifier.testTag("history-date-$date") else Modifier)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = description
                                            if (count > 0) selected = date == selectedDate
                                        }
                                    Box(modifier, contentAlignment = Alignment.Center) {
                                        Box(
                                            Modifier.size(32.dp).clip(shape)
                                                .then(if (count > 0) Modifier.background(activeColor).border(if (date == selectedDate) 2.dp else 1.dp, activeBorderColor, shape) else Modifier),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                dayOfMonth.toString(),
                                                color = when {
                                                    count > 0 -> activeTextColor
                                                    isFuture -> futureColor
                                                    else -> emptyColor
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SessionEditor(session: HistorySessionItem, zone: ZoneId, isSaving: Boolean, onSave: (SessionTimeEdit, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var startText by rememberSaveable(session.id) { mutableStateOf(editTimeFormatter.format(session.startedAtUtc.atZone(zone))) }
    var endText by rememberSaveable(session.id) { mutableStateOf(editTimeFormatter.format(session.endedAtUtc.atZone(zone))) }
    var startOffset by rememberSaveable(session.id) { mutableStateOf<String?>(session.startedAtUtc.atZone(zone).offset.id) }
    var endOffset by rememberSaveable(session.id) { mutableStateOf<String?>(session.endedAtUtc.atZone(zone).offset.id) }
    var saveError by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    val startLocal = parseWallTime(startText)
    val endLocal = parseWallTime(endText)
    val startResult = startLocal?.let { resolveLocalWallTime(it, zone, startOffset?.let(ZoneOffset::of)) }
    val endResult = endLocal?.let { resolveLocalWallTime(it, zone, endOffset?.let(ZoneOffset::of)) }
    val start = (startResult as? LocalWallTimeResult.Resolved)?.instant
    val end = (endResult as? LocalWallTimeResult.Resolved)?.instant
    val invalidRange = start != null && end != null && end.toEpochMilli() <= start.toEpochMilli()
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("编辑${session.name}记录") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("时区：${zone.id}")
                Text("格式：年-月-日 时:分:秒，可带毫秒")
                WallTimeField("开始", "start", startText, startLocal, startResult, startOffset, zone, !isSaving,
                    { startText = it; startOffset = null; saveError = null }, { startOffset = it })
                WallTimeField("结束", "end", endText, endLocal, endResult, endOffset, zone, !isSaving,
                    { endText = it; endOffset = null; saveError = null }, { endOffset = it })
                if (invalidRange) Text("结束时间必须晚于开始时间", modifier = Modifier.testTag("session-range-error"))
                saveError?.let { Text(it, modifier = Modifier.testTag("session-save-error")) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (start != null && end != null) onSave(SessionTimeEdit(session.id, start, end)) { error ->
                        saveError = error
                        if (error == null) onDismiss()
                    }
                },
                enabled = !isSaving && start != null && end != null && !invalidRange,
                modifier = Modifier.testTag("session-save"),
            ) { Text(if (isSaving) "保存中" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("取消") } },
    )
}

private fun parseWallTime(text: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(text.trim().replace(' ', 'T')) }.getOrNull()

@Composable
private fun WallTimeField(
    label: String,
    tag: String,
    text: String,
    local: LocalDateTime?,
    result: LocalWallTimeResult?,
    selectedOffset: String?,
    zone: ZoneId,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onOffsetSelected: (String) -> Unit,
) {
    val message = when (result) {
        null -> "请输入有效日期时间，例如 2026-10-01 09:00:00"
        LocalWallTimeResult.Nonexistent -> "此时刻因夏令时跳变不存在，请重新选择"
        LocalWallTimeResult.InvalidOffset -> "请选择此时刻有效的 UTC 偏移量"
        LocalWallTimeResult.OutOfRange -> "此时刻超出本地存储支持范围，请重新选择"
        is LocalWallTimeResult.Ambiguous -> "此时刻出现两次，请选择 UTC 偏移量"
        is LocalWallTimeResult.Resolved -> null
    }
    OutlinedTextField(value = text, onValueChange = onChange, label = { Text("${label}时间") }, enabled = enabled,
        singleLine = true, isError = message != null, modifier = Modifier.fillMaxWidth().testTag("session-$tag-input"))
    message?.let { Text(it, modifier = Modifier.testTag("session-$tag-error")) }
    val offsets = local?.let { zone.rules.getValidOffsets(it) }.orEmpty()
    if (offsets.size > 1) {
        offsets.forEach { offset ->
            FilterChip(selected = selectedOffset == offset.id, onClick = { onOffsetSelected(offset.id) }, enabled = enabled,
                label = { Text("UTC${offset.id}") }, modifier = Modifier.testTag("session-$tag-offset-${offset.id}"))
        }
    }
}
