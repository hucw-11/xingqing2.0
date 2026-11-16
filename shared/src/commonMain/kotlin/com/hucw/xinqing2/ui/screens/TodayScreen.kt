package com.hucw.xinqing2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hucw.xinqing2.data.db.ScheduleRepository
import com.hucw.xinqing2.domain.schedule.CourseSchedule
import com.hucw.xinqing2.domain.schedule.DayTimeline
import com.hucw.xinqing2.domain.schedule.ItemKind
import com.hucw.xinqing2.domain.schedule.Occurrence
import com.hucw.xinqing2.domain.schedule.Semester
import com.hucw.xinqing2.time.TimeProvider
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val MinuteHeight = 1.2.dp
private val TimeLabelWidth = 52.dp
private val DayHeight = MinuteHeight * (24 * 60).toFloat()

@Composable
fun TodayScreen(
    repository: ScheduleRepository,
    timeProvider: TimeProvider,
) {
    val today = remember { timeProvider.today() }
    var selectedDate by remember { mutableStateOf(today) }
    var reloadToken by remember { mutableStateOf(0) }
    var selectedOccurrence by remember { mutableStateOf<Occurrence?>(null) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showCourseDialog by remember { mutableStateOf(false) }
    var showSemesterDialog by remember { mutableStateOf(false) }

    val semester = remember(reloadToken) { repository.loadSemester() }
    val timeline = remember(selectedDate, reloadToken) { repository.loadDay(selectedDate) }
    val nowMinute = remember(selectedDate, reloadToken) { timeProvider.nowMinute() }
    val defaultMonday = remember(today) {
        today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateHeader(
            date = selectedDate,
            semester = semester,
            onPrevious = { selectedDate = selectedDate.minus(1, DateTimeUnit.DAY) },
            onToday = { selectedDate = today },
            onNext = { selectedDate = selectedDate.plus(1, DateTimeUnit.DAY) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showEventDialog = true }) {
                Text("＋ 日程/待办")
            }
            OutlinedButton(
                onClick = {
                    if (semester == null) showSemesterDialog = true else showCourseDialog = true
                }
            ) {
                Text("＋ 课程")
            }
            TextButton(onClick = { showSemesterDialog = true }) {
                Text(if (semester == null) "设置学期" else "学期设置")
            }
        }

        if (semester == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("还没有学期", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "设置学期开始日期后才能显示和添加课程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { showSemesterDialog = true }) {
                        Text("设置学期")
                    }
                }
            }
        }

        if (timeline.allDay.isNotEmpty()) {
            Text("全天", style = MaterialTheme.typography.titleSmall)
            timeline.allDay.forEach { item ->
                AllDayCard(item = item, onClick = { selectedOccurrence = item })
            }
        }

        if (timeline.noTimeTodos.isNotEmpty()) {
            Text("无时间待办", style = MaterialTheme.typography.titleSmall)
            timeline.noTimeTodos.forEach { item ->
                NoTimeTodoCard(
                    item = item,
                    onToggle = { checked ->
                        repository.setCompleted(item.seriesId, item.instanceDate, checked)
                        reloadToken += 1
                    },
                    onClick = { selectedOccurrence = item },
                )
            }
        }

        TimedTimeline(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            timeline = timeline,
            today = today,
            nowMinute = nowMinute,
            onClick = { selectedOccurrence = it },
        )
    }

    selectedOccurrence?.let { occurrence ->
        OccurrenceDetailDialog(
            occurrence = occurrence,
            onDismiss = { selectedOccurrence = null },
            onToggleCompleted = if (occurrence.kind == ItemKind.TODO) {
                { completed ->
                    repository.setCompleted(occurrence.seriesId, occurrence.instanceDate, completed)
                    selectedOccurrence = null
                    reloadToken += 1
                }
            } else {
                null
            },
        )
    }

    if (showEventDialog) {
        EventEditorDialog(
            defaultDate = selectedDate,
            onDismiss = { showEventDialog = false },
            onSave = { event ->
                repository.upsertEvent(event)
                showEventDialog = false
                reloadToken += 1
            },
        )
    }

    if (showCourseDialog && semester != null) {
        CourseEditorDialog(
            semester = semester,
            defaultWeekday = selectedDate.dayOfWeek.ordinal + 1,
            onDismiss = { showCourseDialog = false },
            onSave = { course ->
                repository.upsertCourse(course)
                showCourseDialog = false
                reloadToken += 1
            },
        )
    }

    if (showSemesterDialog) {
        SemesterEditorDialog(
            existing = semester,
            defaultMonday = defaultMonday,
            onDismiss = { showSemesterDialog = false },
            onSave = { value ->
                repository.upsertSemester(value)
                showSemesterDialog = false
                reloadToken += 1
            },
        )
    }
}

@Composable
private fun DateHeader(
    date: LocalDate,
    semester: Semester?,
    onPrevious: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${date.month.ordinal + 1}月${date.day}日",
                style = MaterialTheme.typography.headlineSmall,
            )
            val weekText = if (semester == null) {
                ""
            } else {
                val week = CourseSchedule.weekNumber(date, semester.startDate)
                if (week in 1..semester.totalWeeks) " · 第${week}周" else " · 不在学期内"
            }
            Text(
                text = weekdayLabel(date.dayOfWeek.ordinal + 1) + weekText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onPrevious) { Text("‹") }
        TextButton(onClick = onToday) { Text("今天") }
        TextButton(onClick = onNext) { Text("›") }
    }
}

@Composable
private fun AllDayCard(
    item: Occurrence,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.date != item.instanceDate) "全天 · 跨日" else "全天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoTimeTodoCard(
    item: Occurrence,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.completed, onCheckedChange = onToggle)
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                    color = if (item.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = "待办",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimedTimeline(
    modifier: Modifier,
    timeline: DayTimeline,
    today: LocalDate,
    nowMinute: Int,
    onClick: (Occurrence) -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(timeline.date, timeline.timed.size) {
        val focus = if (timeline.date == today) {
            nowMinute
        } else {
            timeline.timed.firstOrNull()?.occurrence?.startMinute ?: (8 * 60)
        }
        val target = with(density) {
            (MinuteHeight * (focus - 60).coerceAtLeast(0).toFloat()).roundToPx()
        }
        scrollState.scrollTo(target)
    }

    BoxWithConstraints(modifier) {
        val contentWidth = (maxWidth - TimeLabelWidth).coerceAtLeast(1.dp)

        Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Box(modifier = Modifier.fillMaxWidth().height(DayHeight)) {
                for (hour in 0..24) {
                    val y = MinuteHeight * (hour * 60).toFloat()
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = y)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    if (hour < 24) {
                        Text(
                            text = formatClock(hour * 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .offset(x = 2.dp, y = y - 6.dp)
                                .width(TimeLabelWidth),
                        )
                    }
                }

                if (timeline.date == today) {
                    val y = MinuteHeight * nowMinute.toFloat()
                    Box(
                        modifier = Modifier
                            .offset(x = TimeLabelWidth, y = y)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Text(
                        text = "现在",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .offset(x = 0.dp, y = y - 6.dp)
                            .width(TimeLabelWidth),
                    )
                }

                timeline.timed.forEach { positioned ->
                    val occurrence = positioned.occurrence
                    val start = occurrence.startMinute ?: 0
                    val duration = occurrence.durationMinutes ?: 30
                    val columnWidth = contentWidth / positioned.columnCount.toFloat()
                    val left = TimeLabelWidth + columnWidth * positioned.column.toFloat()
                    val width = maxOf(columnWidth - 4.dp, 24.dp)
                    val height = maxOf(MinuteHeight * duration.toFloat(), 28.dp)

                    Surface(
                        modifier = Modifier
                            .offset(x = left, y = MinuteHeight * start.toFloat())
                            .width(width)
                            .height(height)
                            .alpha(if (occurrence.completed) 0.55f else 1f)
                            .clickable { onClick(occurrence) },
                        shape = RoundedCornerShape(8.dp),
                        color = occurrenceColor(occurrence),
                        contentColor = occurrenceContentColor(occurrence),
                        tonalElevation = 2.dp,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Text(
                                text = occurrence.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (occurrence.completed) {
                                    TextDecoration.LineThrough
                                } else {
                                    null
                                },
                            )
                            val end = start + duration
                            val endText = if (end > 1440) {
                                "次日 " + formatClock(end - 1440)
                            } else {
                                formatClock(end)
                            }
                            Text(
                                text = formatClock(start) + "–" + endText +
                                    if (occurrence.crossesMidnight) " · 跨日" else "",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (timeline.timed.isEmpty()) {
                    Text(
                        text = "当天没有带时间的课程或日程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun occurrenceColor(item: Occurrence): Color = when (item.kind) {
    ItemKind.COURSE -> MaterialTheme.colorScheme.primaryContainer
    ItemKind.EVENT -> MaterialTheme.colorScheme.secondaryContainer
    ItemKind.TODO -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun occurrenceContentColor(item: Occurrence): Color = when (item.kind) {
    ItemKind.COURSE -> MaterialTheme.colorScheme.onPrimaryContainer
    ItemKind.EVENT -> MaterialTheme.colorScheme.onSecondaryContainer
    ItemKind.TODO -> MaterialTheme.colorScheme.onTertiaryContainer
}
