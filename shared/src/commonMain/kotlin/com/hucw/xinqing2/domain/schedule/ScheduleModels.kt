package com.hucw.xinqing2.domain.schedule

import kotlinx.datetime.LocalDate

enum class ItemKind { COURSE, EVENT, TODO }

enum class SeriesKind { COURSE, EVENT }

enum class ExceptionType { CANCELLED, MOVED }

enum class RecurrenceFrequency { NONE, DAILY, WEEKLY, MONTHLY }

enum class MonthEndPolicy { SKIP, LAST_DAY }

data class Semester(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
) {
    init {
        require(totalWeeks >= 1) { "学期周数必须大于 0" }
    }
}

data class CourseSeries(
    val id: String,
    val semesterId: String,
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val color: String,
    val weeks: Set<Int>,
    val reminderMinutes: Int = 0,
) {
    init {
        require(weekday in 1..7) { "星期必须在 1..7" }
        require(startMinute in 0..1439) { "开始分钟越界" }
        require(endMinute in 1..1440) { "结束分钟越界" }
        require(endMinute > startMinute) { "结束时间必须晚于开始时间" }
    }
}

data class RecurrenceRule(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(),
    val untilDate: LocalDate? = null,
    val occurrenceCount: Int? = null,
    val monthEndPolicy: MonthEndPolicy = MonthEndPolicy.SKIP,
) {
    init {
        require(interval >= 1) { "重复间隔必须大于 0" }
        require(weekdays.all { it in 1..7 }) { "重复星期必须在 1..7" }
        require(occurrenceCount == null || occurrenceCount > 0) { "重复次数必须大于 0" }
    }
}

data class EventSeries(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val note: String,
    val isAllDay: Boolean,
    val startDate: LocalDate,
    val startMinute: Int?,
    val durationMinutes: Int?,
    val recurrence: RecurrenceRule = RecurrenceRule(),
    val reminderMinutes: Int = 0,
) {
    init {
        require(kind != ItemKind.COURSE) { "日程/待办不能使用课程类型" }
        require(isAllDay || startMinute != null || kind == ItemKind.TODO) {
            "非全天日程必须有开始时间"
        }
        require(startMinute == null || startMinute in 0..1439) { "开始分钟越界" }
        require(durationMinutes == null || durationMinutes > 0) { "持续时间必须大于 0" }
        require(startMinute == null || durationMinutes != null) {
            "有时间的日程/待办必须有持续时间"
        }
    }
}

data class OccurrenceException(
    val seriesKind: SeriesKind,
    val seriesId: String,
    val originalDate: LocalDate,
    val type: ExceptionType,
    val newDate: LocalDate? = null,
    val newStartMinute: Int? = null,
    val newEndMinute: Int? = null,
    val newDurationMinutes: Int? = null,
) {
    init {
        require(type != ExceptionType.MOVED || newDate != null) { "调期必须提供新日期" }
    }
}

data class Occurrence(
    val seriesId: String,
    val kind: ItemKind,
    val instanceDate: LocalDate,
    val date: LocalDate,
    val title: String,
    val startMinute: Int?,
    val durationMinutes: Int?,
    val isAllDay: Boolean,
    val location: String,
    val color: String,
    val completed: Boolean,
    val crossesMidnight: Boolean = false,
)

data class CompletionKey(
    val seriesId: String,
    val instanceDate: LocalDate,
)

data class PositionedOccurrence(
    val occurrence: Occurrence,
    val column: Int,
    val columnCount: Int,
)

data class DayTimeline(
    val date: LocalDate,
    val timed: List<PositionedOccurrence>,
    val allDay: List<Occurrence>,
    val noTimeTodos: List<Occurrence>,
)
