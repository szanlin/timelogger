package com.y3lc.timelogger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.y3lc.timelogger.R
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecordScreen(state: TimeLoggerUiState, onToggle: (String) -> Unit, onSessionSaved: (SessionTimeEdit, (String?) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    if (historyVisible) {
        HistoryDialog(state, onSessionSaved) { historyVisible = false }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = modifier.testTag("record-list"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今天", style = MaterialTheme.typography.headlineSmall)
                    Text(state.today.dateLabel, style = MaterialTheme.typography.bodyMedium)
                    Text("统计时区 ${state.statisticsZoneId.id}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    onClick = { historyVisible = true },
                    enabled = !state.isSaving && state.errorMessage == null,
                    modifier = Modifier.size(48.dp).testTag("history-open"),
                ) {
                    Icon(painterResource(R.drawable.ic_tab_record), contentDescription = "查看历史记录")
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("覆盖时长", style = MaterialTheme.typography.labelLarge)
                    Text(formatDuration(state.today.coverageDuration), style = MaterialTheme.typography.displaySmall)
                    Text("类型累计时长 ${formatDuration(state.today.totalDuration)}", style = MaterialTheme.typography.bodyMedium)
                    Text("并行活动分别累计，覆盖时长只计算一次", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.activityTypes.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text("暂无活动类型，请前往设置创建首个类型") }
        }
        gridItems(state.activityTypes, key = ActivityTypeItem::id) { type ->
            ActivityCard(type, !state.isSaving && state.errorMessage == null, { onToggle(type.id) }, Modifier.heightIn(min = 144.dp))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("当日时间轴", style = MaterialTheme.typography.titleMedium)
                    if (state.today.timeline.isEmpty()) {
                        Text("点击活动图标，开始记录今天", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Timeline(state.today, state.statisticsZoneId)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ActivityCard(type: ActivityTypeItem, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint = Color(type.colorArgb)
    val status = if (type.isRunning) "进行中" else "空闲"
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.testTag("activity-${type.id}").semantics {
            contentDescription = if (type.isRunning) "${type.name}，进行中，时长 ${formatDuration(type.runningDuration)}" else "${type.name}，$status"
        },
        colors = CardDefaults.cardColors(containerColor = if (type.isRunning) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                if (type.isRunning) CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(52.dp), color = tint, strokeWidth = 2.dp)
                Icon(painterResource(activityIcon(type.iconKey)), null, tint = tint, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(type.name, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.labelMedium)
            if (type.isRunning) {
                Text(formatDuration(type.runningDuration), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun activityIcon(iconKey: String): Int = when (iconKey) {
    "sleep" -> R.drawable.ic_sleep
    "walk" -> R.drawable.ic_walk
    "cycle" -> R.drawable.ic_cycle
    else -> R.drawable.ic_meeting
}

@Composable
fun StatisticsScreen(state: TimeLoggerUiState, onRangeSelected: (StatisticsRange) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.testTag("statistics-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StatisticsRange.entries.forEachIndexed { index, range ->
                        SegmentedButton(
                            selected = state.statisticsRange == range,
                            onClick = { onRangeSelected(range) },
                            shape = SegmentedButtonDefaults.itemShape(index, StatisticsRange.entries.size),
                            modifier = Modifier.weight(1f).testTag("period-${range.name.lowercase()}"),
                            label = { Text(range.label) },
                        )
                    }
                }
                Text(state.statisticsRange.periodLabel, modifier = Modifier.testTag("statistics-period-label"), style = MaterialTheme.typography.titleLarge)
                Text(state.statistics.dateLabel, style = MaterialTheme.typography.bodyMedium)
                Text("统计时区 ${state.statisticsZoneId.id}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.statistics.ranking.isEmpty()) {
            item { Text("这个周期还没有记录，去记录页开始一项活动吧", modifier = Modifier.testTag("statistics-empty")) }
        } else {
            item { DurationSummary(state.statistics) }
            if (state.statisticsRange == StatisticsRange.WEEK) {
                item { WeeklyStackedChart(state.statistics) }
            }
            if (state.statisticsRange == StatisticsRange.MONTH) {
                item { MonthlyTrendChart(state.statistics) }
            }
            item { Text("类型累计排行", style = MaterialTheme.typography.titleMedium) }
            items(state.statistics.ranking, key = { it.typeId }) { type ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(10.dp)) { drawCircle(Color(type.colorArgb)) }
                            Text(type.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(formatDuration(type.duration), style = MaterialTheme.typography.titleSmall)
                        }
                        val fraction = (type.duration.toMillis().toDouble() / state.statistics.totalDuration.toMillis().coerceAtLeast(1)).toFloat()
                        LinearProgressIndicator(progress = { fraction }, color = Color(type.colorArgb), modifier = Modifier.fillMaxWidth())
                        Text("占类型累计 ${"%.1f".format(fraction * 100)}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (state.statisticsRange == StatisticsRange.DAY) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("当日时间轴", style = MaterialTheme.typography.titleMedium)
                            Timeline(state.statistics, state.statisticsZoneId)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyStackedChart(summary: PeriodSummary) {
    val days = summary.dailyBreakdown
    if (days.isEmpty()) return
    val maxMillis = days.maxOf { it.totalDuration.toMillis() }.coerceAtLeast(1)
    val colorsByTypeId = summary.ranking.associate { it.typeId to Color(it.colorArgb) }
    Card(Modifier.fillMaxWidth().testTag("statistics-week-chart"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("每日类型堆叠时长", style = MaterialTheme.typography.titleMedium)
        Text("单日最高 ${formatDuration(Duration.ofMillis(maxMillis))}", style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { day ->
                val description = "${day.date}，类型累计 ${formatDuration(day.totalDuration)}" +
                    day.durationByTypeId.entries.joinToString(separator = "", prefix = "，") { (typeId, duration) ->
                        "${summary.ranking.firstOrNull { it.typeId == typeId }?.name ?: "未知类型"} ${formatDuration(duration)}，"
                    }
                Column(Modifier.weight(1f).semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                        val barWidth = 18.dp.toPx().coerceAtMost(size.width)
                        val barLeft = (size.width - barWidth) / 2f
                        drawRoundRect(
                            color = Color.LightGray.copy(alpha = 0.25f),
                            topLeft = Offset(barLeft, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                        var bottom = size.height
                        summary.ranking.forEach { type ->
                            val millis = day.durationByTypeId[type.typeId]?.toMillis() ?: 0L
                            if (millis > 0) {
                                val barHeight = size.height * millis.toFloat() / maxMillis
                                bottom -= barHeight
                                drawRect(colorsByTypeId.getValue(type.typeId), Offset(barLeft, bottom), Size(barWidth, barHeight))
                            }
                        }
                    }
                    Text("周${"一二三四五六日"[day.date.dayOfWeek.value - 1]}", style = MaterialTheme.typography.labelSmall)
                    Text("${day.date.monthValue}/${day.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        summary.ranking.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(10.dp)) { drawCircle(Color(type.colorArgb)) }
                Text(type.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    }
}

@Composable
private fun MonthlyTrendChart(summary: PeriodSummary) {
    val days = summary.dailyBreakdown
    if (days.isEmpty()) return
    val maxMillis = days.maxOf { maxOf(it.coverageDuration.toMillis(), it.totalDuration.toMillis()) }.coerceAtLeast(1)
    val coverageColor = MaterialTheme.colorScheme.primary
    val totalColor = MaterialTheme.colorScheme.tertiary
    val description = days.joinToString("；") { day ->
        "${day.date.dayOfMonth}日，覆盖 ${formatDuration(day.coverageDuration)}，类型累计 ${formatDuration(day.totalDuration)}"
    }
    Card(Modifier.fillMaxWidth().testTag("statistics-month-chart"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("每日时长趋势", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TrendLegend("覆盖时长", coverageColor)
            TrendLegend("类型累计", totalColor)
        }
        Text("单日最高 ${formatDuration(Duration.ofMillis(maxMillis))}", style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
            val chartHeight = size.height - 8.dp.toPx()
            for (step in 0..2) {
                val y = chartHeight * step / 2f
                drawLine(Color.LightGray.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            fun drawTrend(values: List<Duration>, color: Color, dashed: Boolean) {
                val points = values.mapIndexed { index, duration ->
                    Offset(
                        size.width * index / (values.size - 1).coerceAtLeast(1),
                        chartHeight * (1f - duration.toMillis().toFloat() / maxMillis),
                    )
                }
                val path = Path().apply {
                    points.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
                drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())) else null))
                points.forEach { point -> drawCircle(color, radius = 2.dp.toPx(), center = point) }
            }
            drawTrend(days.map(DailyBreakdown::totalDuration), totalColor, false)
            drawTrend(days.map(DailyBreakdown::coverageDuration), coverageColor, true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${days.first().date.dayOfMonth}日", style = MaterialTheme.typography.labelSmall)
            if (days.size > 2) Text("${days[days.size / 2].date.dayOfMonth}日", style = MaterialTheme.typography.labelSmall)
            if (days.size > 1) Text("${days.last().date.dayOfMonth}日", style = MaterialTheme.typography.labelSmall)
        }
    }
    }
}

@Composable
private fun TrendLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(16.dp).height(3.dp)) { drawRect(color) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DurationSummary(summary: PeriodSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("覆盖时长", style = MaterialTheme.typography.labelLarge)
                Text(formatDuration(summary.coverageDuration), style = MaterialTheme.typography.headlineMedium)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("类型累计时长", style = MaterialTheme.typography.labelLarge)
                Text(formatDuration(summary.totalDuration), style = MaterialTheme.typography.titleLarge)
            }
        }
        Text("并行活动分别累计，覆盖时长只计算一次", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Timeline(summary: PeriodSummary, zoneId: ZoneId) {
    val period = summary.interval ?: return
    val periodMillis = Duration.between(period.start, period.endExclusive).toMillis().toDouble()
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zoneId)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelSmall)
            Text("24:00", style = MaterialTheme.typography.labelSmall)
        }
        summary.timeline.forEach { item ->
            val start = (Duration.between(period.start, item.interval.start).toMillis() / periodMillis).toFloat()
            val width = (Duration.between(item.interval.start, item.interval.endExclusive).toMillis() / periodMillis).toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${item.name} · ${formatter.format(item.interval.start)} — ${if (item.isRunning) "进行中" else if (item.interval.endExclusive == period.endExclusive) "24:00" else formatter.format(item.interval.endExclusive)}", style = MaterialTheme.typography.bodySmall)
                val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    drawRoundRect(trackColor, cornerRadius = CornerRadius(4.dp.toPx()))
                    drawRoundRect(Color(item.colorArgb), topLeft = Offset(size.width * start, 0f), size = Size((size.width * width).coerceAtLeast(2.dp.toPx()).coerceAtMost(size.width * (1 - start)), size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                }
            }
        }
    }
}
