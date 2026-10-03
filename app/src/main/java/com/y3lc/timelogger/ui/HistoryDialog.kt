package com.y3lc.timelogger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.y3lc.timelogger.domain.time.LocalWallTimeResult
import com.y3lc.timelogger.domain.time.resolveLocalWallTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val editTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS")

@Composable
fun HistoryDialog(state: TimeLoggerUiState, onSave: (SessionTimeEdit, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingZone by rememberSaveable { mutableStateOf(state.statisticsZoneId.id) }
    val editing = state.historySessions.firstOrNull { it.id == editingId }
    if (editing != null) {
        SessionEditor(editing, ZoneId.of(editingZone), state.isSaving, onSave) { editingId = null }
        return
    }
    val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(state.statisticsZoneId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("历史记录") },
        text = {
            Column {
                Text("${state.statisticsZoneId.id} · 仅显示已结束记录")
                if (state.historySessions.isEmpty()) Text("暂无已结束记录")
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    itemsIndexed(state.historySessions, key = { _, session -> session.id }) { index, session ->
                        TextButton(
                            onClick = { editingZone = state.statisticsZoneId.id; editingId = session.id },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth().testTag("history-edit-$index"),
                        ) {
                            Column {
                                Text(session.name)
                                Text(displayFormatter.format(session.startedAtUtc))
                                Text("至 ${displayFormatter.format(session.endedAtUtc)}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SessionEditor(session: HistorySessionItem, zone: ZoneId, isSaving: Boolean, onSave: (SessionTimeEdit, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
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
