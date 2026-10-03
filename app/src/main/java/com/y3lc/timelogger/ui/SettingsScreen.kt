package com.y3lc.timelogger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

private val typeIcons = listOf("sleep" to "睡觉", "walk" to "走路", "cycle" to "骑车", "meeting" to "开会")
private val typeColors = listOf(0xFF5266A6, 0xFF4A8D69, 0xFFB27142, 0xFF855C91, 0xFF287C91, 0xFFA45565)

@Composable
fun SettingsScreen(
    state: TimeLoggerUiState,
    onZoneSaved: (String?) -> Unit,
    onWeekStartChanged: (DayOfWeek) -> Unit,
    onTypeCreated: (String, String, Long) -> Unit,
    onTypeUpdated: (String, String, String, Long) -> Unit,
    onTypeMoved: (String, Int) -> Unit,
    onTypeArchived: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var useFixedZone by rememberSaveable { mutableStateOf(state.fixedStatisticsZoneId != null) }
    var zoneInput by rememberSaveable { mutableStateOf(state.fixedStatisticsZoneId.orEmpty()) }
    var editingType by remember { mutableStateOf<ActivityTypeItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var archivingType by remember { mutableStateOf<ActivityTypeItem?>(null) }
    LaunchedEffect(state.fixedStatisticsZoneId) {
        useFixedZone = state.fixedStatisticsZoneId != null
        if (state.fixedStatisticsZoneId != null) zoneInput = state.fixedStatisticsZoneId
    }

    LazyColumn(modifier.testTag("settings-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("统计时区", style = MaterialTheme.typography.titleMedium)
            Text(state.statisticsZoneId.id, modifier = Modifier.testTag("settings-zone-current"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !useFixedZone,
                    enabled = !state.isSaving,
                    onClick = {
                        useFixedZone = false
                        onZoneSaved(null)
                    },
                    label = { Text("跟随系统") },
                    modifier = Modifier.testTag("settings-zone-system"),
                )
                FilterChip(
                    selected = useFixedZone,
                    enabled = !state.isSaving,
                    onClick = { useFixedZone = true },
                    label = { Text("固定时区") },
                    modifier = Modifier.testTag("settings-zone-fixed"),
                )
            }
            if (useFixedZone) {
                OutlinedTextField(
                    value = zoneInput,
                    onValueChange = { zoneInput = it },
                    label = { Text("IANA 时区，如 Asia/Shanghai") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings-zone-input"),
                )
                Button(
                    onClick = { onZoneSaved(zoneInput) },
                    enabled = !state.isSaving,
                    modifier = Modifier.testTag("settings-zone-save"),
                ) { Text("保存时区") }
            }
        }
        item {
            Text("每周起始日", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.firstDayOfWeek == DayOfWeek.MONDAY,
                    enabled = !state.isSaving,
                    onClick = { onWeekStartChanged(DayOfWeek.MONDAY) },
                    label = { Text("周一") },
                    modifier = Modifier.testTag("settings-week-monday"),
                )
                FilterChip(
                    selected = state.firstDayOfWeek == DayOfWeek.SUNDAY,
                    enabled = !state.isSaving,
                    onClick = { onWeekStartChanged(DayOfWeek.SUNDAY) },
                    label = { Text("周日") },
                    modifier = Modifier.testTag("settings-week-sunday"),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("类型管理", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    editingType = null
                    showEditor = true
                }, enabled = !state.isSaving, modifier = Modifier.testTag("type-add")) { Text("新增类型") }
            }
        }
        items(state.managedActivityTypes, key = { it.id }) { type ->
            val activeTypes = state.managedActivityTypes.filterNot { it.isArchived }
            val index = activeTypes.indexOfFirst { it.id == type.id }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(type.name, style = MaterialTheme.typography.titleSmall)
                    if (type.isArchived) {
                        Text("已归档", modifier = Modifier.testTag("type-archived-${type.name}"))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { editingType = type; showEditor = true }, enabled = !state.isSaving, modifier = Modifier.testTag("type-edit-${type.name}")) { Text("编辑") }
                            TextButton(onClick = { onTypeMoved(type.id, -1) }, enabled = index > 0 && !state.isSaving, modifier = Modifier.testTag("type-move-up-${type.name}")) { Text("上移") }
                            TextButton(onClick = { onTypeMoved(type.id, 1) }, enabled = index >= 0 && index < activeTypes.lastIndex && !state.isSaving, modifier = Modifier.testTag("type-move-down-${type.name}")) { Text("下移") }
                            TextButton(onClick = { archivingType = type }, enabled = !state.isSaving, modifier = Modifier.testTag("type-archive-${type.name}")) { Text("归档") }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        TypeEditorDialog(
            type = editingType,
            enabled = !state.isSaving,
            onDismiss = { showEditor = false },
            onSave = { name, iconKey, colorArgb ->
                val id = editingType?.id
                if (id == null) onTypeCreated(name, iconKey, colorArgb)
                else onTypeUpdated(id, name, iconKey, colorArgb)
                showEditor = false
            },
        )
    }
    archivingType?.let { type ->
        AlertDialog(
            onDismissRequest = { archivingType = null },
            title = { Text("归档${type.name}？") },
            text = { Text(if (type.isRunning) "当前计时会立即结束，历史记录会保留。" else "历史记录会保留，归档后不能再开始计时。") },
            confirmButton = {
                TextButton(onClick = {
                    onTypeArchived(type.id)
                    archivingType = null
                }, enabled = !state.isSaving, modifier = Modifier.testTag("type-confirm-archive")) { Text("归档") }
            },
            dismissButton = { TextButton(onClick = { archivingType = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TypeEditorDialog(type: ActivityTypeItem?, enabled: Boolean, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var name by remember(type?.id) { mutableStateOf(type?.name.orEmpty()) }
    var iconKey by remember(type?.id) { mutableStateOf(type?.iconKey ?: "meeting") }
    var colorArgb by remember(type?.id) { mutableStateOf(type?.colorArgb ?: typeColors.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == null) "新增类型" else "编辑类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("type-name-input"))
                Text("图标")
                typeIcons.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (key, label) ->
                        FilterChip(selected = iconKey == key, onClick = { iconKey = key }, label = { Text(label) }, modifier = Modifier.testTag("type-icon-$key"))
                        }
                    }
                }
                Text("颜色")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    typeColors.forEach { color ->
                        Box(
                            Modifier.size(32.dp).background(Color(color)).clickable { colorArgb = color }
                                .testTag("type-color-$color")
                                .padding(if (colorArgb == color) 4.dp else 0.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, iconKey, colorArgb) }, enabled = enabled && name.isNotBlank(), modifier = Modifier.testTag("type-save")) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
