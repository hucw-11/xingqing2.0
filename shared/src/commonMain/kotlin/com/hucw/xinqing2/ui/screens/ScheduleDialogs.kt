package com.hucw.xinqing2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hucw.xinqing2.domain.schedule.CourseSeries
import com.hucw.xinqing2.domain.schedule.EventSeries
import com.hucw.xinqing2.domain.schedule.ItemKind
import com.hucw.xinqing2.domain.schedule.Occurrence
import com.hucw.xinqing2.domain.schedule.Semester
import com.hucw.xinqing2.domain.schedule.randomId
import kotlinx.datetime.LocalDate

internal fun parseClock(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun formatClock(minutes: Int): String {
    val hour = minutes / 60
    val minute = minutes % 60
    return hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}

internal fun weekdayLabel(weekday: Int): String = when (weekday) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周?"
}

private fun parseWeeks(text: String, totalWeeks: Int): Set<Int>? {
    if (text.isBlank()) return null
    val result = linkedSetOf<Int>()
    text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
        val range = token.split('-', '~').map { it.trim() }
        if (range.size == 2) {
            val from = range[0].toIntOrNull() ?: return null
            val to = range[1].toIntOrNull() ?: return null
            if (from > to) return null
            result += from..to
        } else {
            result += token.toIntOrNull() ?: return null
        }
    }
    val valid = result.filter { it in 1..totalWeeks }.toSet()
    return valid.ifEmpty { null }
}

@Composable
fun SemesterEditorDialog(
    existing: Semester?,
    defaultMonday: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Semester) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "默认学期") }
    var startText by remember { mutableStateOf((existing?.startDate ?: defaultMonday).toString()) }
    var weeksText by remember { mutableStateOf((existing?.totalWeeks ?: 20).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "设置学期" else "编辑学期") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("开始日期（周一，YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weeksText,
                    onValueChange = { weeksText = it },
                    label = { Text("总周数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                if (start == null) {
                    error = "日期格式应为 YYYY-MM-DD"
                    return@TextButton
                }
                if (start.dayOfWeek.ordinal != 0) {
                    error = "开始日期必须是周一"
                    return@TextButton
                }
                val totalWeeks = weeksText.trim().toIntOrNull()
                if (totalWeeks == null || totalWeeks !in 1..30) {
                    error = "总周数应在 1..30"
                    return@TextButton
                }
                val trimmedName = name.trim().ifEmpty { "默认学期" }
                onSave(Semester(existing?.id ?: randomId(), trimmedName, start, totalWeeks))
            }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun EventEditorDialog(
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (EventSeries) -> Unit,
) {
    var kind by remember { mutableStateOf(ItemKind.EVENT) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var hasTime by remember { mutableStateOf(true) }
    var dateText by remember { mutableStateOf(defaultDate.toString()) }
    var startText by remember { mutableStateOf("09:00") }
    var endText by remember { mutableStateOf("10:00") }
    var error by remember { mutableStateOf<String?>(null) }

    val wantsTime = if (kind == ItemKind.EVENT) !allDay else hasTime

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增日程/待办") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind == ItemKind.EVENT) {
                        Button(onClick = { kind = ItemKind.EVENT }) { Text("日程") }
                    } else {
                        OutlinedButton(onClick = { kind = ItemKind.EVENT }) { Text("日程") }
                    }
                    if (kind == ItemKind.TODO) {
                        Button(onClick = { kind = ItemKind.TODO }) { Text("待办") }
                    } else {
                        OutlinedButton(onClick = { kind = ItemKind.TODO }) { Text("待办") }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("日期（YYYY-MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (kind == ItemKind.EVENT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                        Text("全天")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasTime, onCheckedChange = { hasTime = it })
                        Text("有具体时间")
                    }
                }

                if (wantsTime) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("开始时间（HH:mm）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("结束时间（HH:mm）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    text = "重复规则编辑器将在下一步加入；本次只创建单次事项。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                if (date == null) {
                    error = "日期格式应为 YYYY-MM-DD"
                    return@TextButton
                }
                val trimmedTitle = title.trim()
                if (trimmedTitle.isEmpty()) {
                    error = "标题不能为空"
                    return@TextButton
                }

                var start: Int? = null
                var duration: Int? = null
                val isAllDay = kind == ItemKind.EVENT && allDay

                if (wantsTime) {
                    val parsedStart = parseClock(startText)
                    val parsedEnd = parseClock(endText)
                    if (parsedStart == null || parsedEnd == null) {
                        error = "时间格式应为 HH:mm"
                        return@TextButton
                    }
                    if (parsedEnd <= parsedStart) {
                        error = "结束时间必须晚于开始时间"
                        return@TextButton
                    }
                    start = parsedStart
                    duration = parsedEnd - parsedStart
                } else if (isAllDay) {
                    duration = 1440
                }

                onSave(
                    EventSeries(
                        id = randomId(),
                        kind = kind,
                        title = trimmedTitle,
                        note = note.trim(),
                        isAllDay = isAllDay,
                        startDate = date,
                        startMinute = start,
                        durationMinutes = duration,
                    )
                )
            }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun CourseEditorDialog(
    semester: Semester,
    defaultWeekday: Int,
    onDismiss: () -> Unit,
    onSave: (CourseSeries) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var weekday by remember { mutableStateOf(defaultWeekday) }
    var startText by remember { mutableStateOf("08:00") }
    var endText by remember { mutableStateOf("09:40") }
    var weeksText by remember { mutableStateOf("1-${semester.totalWeeks}") }
    var weekdayExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增课程") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Box {
                    OutlinedButton(
                        onClick = { weekdayExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(weekdayLabel(weekday))
                    }
                    DropdownMenu(
                        expanded = weekdayExpanded,
                        onDismissRequest = { weekdayExpanded = false },
                    ) {
                        (1..7).forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(weekdayLabel(candidate)) },
                                onClick = {
                                    weekday = candidate
                                    weekdayExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text("开始时间（HH:mm）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text("结束时间（HH:mm）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = weeksText,
                    onValueChange = { weeksText = it },
                    label = { Text("周次（如 1-16 或 1,3,5）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmedName = name.trim()
                if (trimmedName.isEmpty()) {
                    error = "课程名称不能为空"
                    return@TextButton
                }
                val start = parseClock(startText)
                val end = parseClock(endText)
                if (start == null || end == null) {
                    error = "时间格式应为 HH:mm"
                    return@TextButton
                }
                if (end <= start) {
                    error = "结束时间必须晚于开始时间"
                    return@TextButton
                }
                val weeks = parseWeeks(weeksText, semester.totalWeeks)
                if (weeks == null) {
                    error = "周次格式不正确，且需落在 1..${semester.totalWeeks}"
                    return@TextButton
                }

                onSave(
                    CourseSeries(
                        id = randomId(),
                        semesterId = semester.id,
                        name = trimmedName,
                        teacher = teacher.trim(),
                        location = location.trim(),
                        weekday = weekday,
                        startMinute = start,
                        endMinute = end,
                        color = "",
                        weeks = weeks,
                    )
                )
            }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun OccurrenceDetailDialog(
    occurrence: Occurrence,
    onDismiss: () -> Unit,
    onToggleCompleted: ((Boolean) -> Unit)?,
) {
    val kindLabel = when (occurrence.kind) {
        ItemKind.COURSE -> "课程"
        ItemKind.EVENT -> "日程"
        ItemKind.TODO -> "待办"
    }
    val timeText = when {
        occurrence.isAllDay -> "全天"
        occurrence.startMinute == null -> "无时间"
        else -> {
            val start = occurrence.startMinute ?: 0
            val end = start + (occurrence.durationMinutes ?: 0)
            if (end > 1440) {
                "${formatClock(start)}–次日 ${formatClock(end - 1440)}"
            } else {
                "${formatClock(start)}–${formatClock(end)}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(occurrence.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$kindLabel · $timeText")
                Text("日期：${occurrence.date}")
                if (occurrence.location.isNotBlank()) {
                    Text("地点：${occurrence.location}")
                }
                if (occurrence.crossesMidnight) {
                    Text("跨日：当前显示的是当天可见片段")
                }
                if (occurrence.kind == ItemKind.TODO) {
                    Text(if (occurrence.completed) "状态：已完成" else "状态：未完成")
                }
                if (occurrence.kind == ItemKind.COURSE) {
                    Text(
                        text = "课程编辑将在后续切片加入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = if (onToggleCompleted != null && occurrence.kind == ItemKind.TODO) {
            {
                TextButton(onClick = { onToggleCompleted?.invoke(!occurrence.completed) }) {
                    Text(if (occurrence.completed) "撤销完成" else "标记完成")
                }
            }
        } else {
            null
        },
    )
}
