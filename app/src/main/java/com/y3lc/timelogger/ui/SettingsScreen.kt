package com.y3lc.timelogger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.y3lc.timelogger.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek

private val typeIcons = listOf("sleep" to "睡觉", "walk" to "走路", "cycle" to "骑车", "meeting" to "开会")
private val typeColors = listOf(0xFF5266A6, 0xFF4A8D69, 0xFFB27142, 0xFF855C91, 0xFF287C91, 0xFFA45565)
private val typeColorNames = listOf("蓝色", "绿色", "橙色", "紫色", "青色", "红色")

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
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("统计偏好", style = MaterialTheme.typography.titleLarge)
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
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("活动类型", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = {
                        editingType = null
                        showEditor = true
                    }, enabled = !state.isSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("type-add")) { Text("新增类型") }
                }
            }
        }
        items(state.managedActivityTypes, key = { it.id }) { type ->
            val activeTypes = state.managedActivityTypes.filterNot { it.isArchived }
            val index = activeTypes.indexOfFirst { it.id == type.id }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(typeIcon(type.iconKey)), contentDescription = null, tint = Color(type.colorArgb), modifier = Modifier.size(28.dp))
                        Box(Modifier.size(10.dp).background(Color(type.colorArgb), CircleShape))
                        Text(type.name, style = MaterialTheme.typography.titleSmall)
                    }
                    if (type.isArchived) {
                        Text("已归档", modifier = Modifier.testTag("type-archived-${type.name}"))
                    } else {
                        Text(if (type.isRunning) "进行中" else "可用", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { editingType = type; showEditor = true }, enabled = !state.isSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("type-edit-${type.name}")) { Text("编辑") }
                            TextButton(onClick = { onTypeMoved(type.id, -1) }, enabled = index > 0 && !state.isSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("type-move-up-${type.name}")) { Text("上移") }
                            TextButton(onClick = { onTypeMoved(type.id, 1) }, enabled = index >= 0 && index < activeTypes.lastIndex && !state.isSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("type-move-down-${type.name}")) { Text("下移") }
                            TextButton(onClick = { archivingType = type }, enabled = !state.isSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("type-archive-${type.name}")) { Text("归档") }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (key, label) ->
                            val selected = iconKey == key
                            Row(
                                Modifier.weight(1f)
                                    .heightIn(min = 48.dp)
                                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                                    .selectable(selected = selected, role = Role.Button, onClick = { iconKey = key })
                                    .testTag("type-icon-$key")
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(painterResource(typeIcon(key)), contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Text("颜色")
                typeColors.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { color ->
                            Box(
                                Modifier.size(48.dp)
                                    .selectable(selected = colorArgb == color, role = Role.Button, onClick = { colorArgb = color })
                                    .semantics { contentDescription = typeColorNames[typeColors.indexOf(color)] }
                                    .testTag("type-color-$color"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(28.dp)
                                        .background(Color(color), CircleShape)
                                        .border(2.dp, if (colorArgb == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                )
                            }
                        }
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

private fun typeIcon(key: String): Int = when (key) {
    "sleep" -> R.drawable.ic_sleep
    "walk" -> R.drawable.ic_walk
    "cycle" -> R.drawable.ic_cycle
    else -> R.drawable.ic_meeting
}
