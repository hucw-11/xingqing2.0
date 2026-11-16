package com.hucw.xinqing2.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.hucw.xinqing2.data.withSyncLock
import com.hucw.xinqing2.domain.schedule.CompletionKey
import com.hucw.xinqing2.domain.schedule.CourseSeries
import com.hucw.xinqing2.domain.schedule.DayTimeline
import com.hucw.xinqing2.domain.schedule.EventSeries
import com.hucw.xinqing2.domain.schedule.ExceptionType
import com.hucw.xinqing2.domain.schedule.ItemKind
import com.hucw.xinqing2.domain.schedule.MonthEndPolicy
import com.hucw.xinqing2.domain.schedule.OccurrenceException
import com.hucw.xinqing2.domain.schedule.RecurrenceFrequency
import com.hucw.xinqing2.domain.schedule.RecurrenceRule
import com.hucw.xinqing2.domain.schedule.Semester
import com.hucw.xinqing2.domain.schedule.SeriesKind
import com.hucw.xinqing2.domain.schedule.TimelineBuilder
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

class ScheduleRepository(private val connection: SQLiteConnection) {
    private val lock = Any()

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    fun loadSemester(): Semester? = withSyncLock(lock) {
        query(
            "SELECT id, name, start_date, total_weeks FROM semester ORDER BY start_date DESC LIMIT 1"
        ) { statement ->
            Semester(
                id = statement.getText(0),
                name = statement.getText(1),
                startDate = LocalDate.parse(statement.getText(2)),
                totalWeeks = statement.getLong(3).toInt(),
            )
        }.firstOrNull()
    }

    fun loadCourses(): List<CourseSeries> = withSyncLock(lock) {
        val weeksByCourse = mutableMapOf<String, MutableSet<Int>>()
        query("SELECT course_id, week_number FROM course_week") { statement ->
            weeksByCourse.getOrPut(statement.getText(0)) { mutableSetOf() }
                .add(statement.getLong(1).toInt())
        }

        query(
            """
            SELECT id, semester_id, name, teacher, location, weekday,
                   start_minute, end_minute, color, reminder_minutes
            FROM course_series
            """.trimIndent()
        ) { statement ->
            val id = statement.getText(0)
            CourseSeries(
                id = id,
                semesterId = statement.getText(1),
                name = statement.getText(2),
                teacher = statement.getText(3),
                location = statement.getText(4),
                weekday = statement.getLong(5).toInt(),
                startMinute = statement.getLong(6).toInt(),
                endMinute = statement.getLong(7).toInt(),
                color = statement.getText(8),
                weeks = weeksByCourse[id]?.toSet() ?: emptySet(),
                reminderMinutes = statement.getLong(9).toInt(),
            )
        }
    }

    fun loadEvents(): List<EventSeries> = withSyncLock(lock) {
        query(
            """
            SELECT id, type, title, note, is_all_day, start_date, start_minute,
                   duration_minutes, freq, interval_value, weekdays, until_date,
                   occurrence_count, month_end_policy, reminder_minutes
            FROM event_series
            """.trimIndent()
        ) { statement ->
            EventSeries(
                id = statement.getText(0),
                kind = ItemKind.valueOf(statement.getText(1)),
                title = statement.getText(2),
                note = statement.getText(3),
                isAllDay = statement.getLong(4) != 0L,
                startDate = LocalDate.parse(statement.getText(5)),
                startMinute = statement.intOrNull(6),
                durationMinutes = statement.intOrNull(7),
                recurrence = RecurrenceRule(
                    frequency = RecurrenceFrequency.valueOf(statement.getText(8)),
                    interval = statement.getLong(9).toInt(),
                    weekdays = parseWeekdays(statement.getText(10)),
                    untilDate = statement.textOrNull(11)?.let { LocalDate.parse(it) },
                    occurrenceCount = statement.intOrNull(12),
                    monthEndPolicy = MonthEndPolicy.valueOf(statement.getText(13)),
                ),
                reminderMinutes = statement.getLong(14).toInt(),
            )
        }
    }

    fun loadExceptions(): List<OccurrenceException> = withSyncLock(lock) {
        query(
            """
            SELECT series_kind, series_id, original_date, type, new_date,
                   new_start_minute, new_end_minute, new_duration_minutes
            FROM occurrence_exception
            """.trimIndent()
        ) { statement ->
            OccurrenceException(
                seriesKind = SeriesKind.valueOf(statement.getText(0)),
                seriesId = statement.getText(1),
                originalDate = LocalDate.parse(statement.getText(2)),
                type = ExceptionType.valueOf(statement.getText(3)),
                newDate = statement.textOrNull(4)?.let { LocalDate.parse(it) },
                newStartMinute = statement.intOrNull(5),
                newEndMinute = statement.intOrNull(6),
                newDurationMinutes = statement.intOrNull(7),
            )
        }
    }

    fun loadCompletions(): Set<CompletionKey> = withSyncLock(lock) {
        query("SELECT event_series_id, occurrence_date FROM task_completion") { statement ->
            CompletionKey(
                seriesId = statement.getText(0),
                instanceDate = LocalDate.parse(statement.getText(1)),
            )
        }.toSet()
    }

    fun loadDay(date: LocalDate): DayTimeline = TimelineBuilder.build(
        date = date,
        semester = loadSemester(),
        courses = loadCourses(),
        events = loadEvents(),
        exceptions = loadExceptions(),
        completions = loadCompletions(),
    )

    fun upsertSemester(semester: Semester, nowMillis: Long = nowMillis()): Unit = withSyncLock(lock) {
        val statement = connection.prepare(
            """
            INSERT INTO semester (id, name, start_date, total_weeks, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                start_date = excluded.start_date,
                total_weeks = excluded.total_weeks,
                updated_at = excluded.updated_at
            """.trimIndent()
        )
        try {
            statement.bindText(1, semester.id)
            statement.bindText(2, semester.name)
            statement.bindText(3, semester.startDate.toString())
            statement.bindLong(4, semester.totalWeeks.toLong())
            statement.bindLong(5, nowMillis)
            statement.bindLong(6, nowMillis)
            statement.step()
        } finally {
            statement.close()
        }
        Unit
    }

    fun upsertCourse(course: CourseSeries, nowMillis: Long = nowMillis()): Unit = withSyncLock(lock) {
        connection.execSQL("BEGIN IMMEDIATE")
        try {
            val statement = connection.prepare(
                """
                INSERT INTO course_series (
                    id, semester_id, name, teacher, location, weekday,
                    start_minute, end_minute, color, reminder_minutes, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    semester_id = excluded.semester_id,
                    name = excluded.name,
                    teacher = excluded.teacher,
                    location = excluded.location,
                    weekday = excluded.weekday,
                    start_minute = excluded.start_minute,
                    end_minute = excluded.end_minute,
                    color = excluded.color,
                    reminder_minutes = excluded.reminder_minutes,
                    updated_at = excluded.updated_at
                """.trimIndent()
            )
            try {
                statement.bindText(1, course.id)
                statement.bindText(2, course.semesterId)
                statement.bindText(3, course.name)
                statement.bindText(4, course.teacher)
                statement.bindText(5, course.location)
                statement.bindLong(6, course.weekday.toLong())
                statement.bindLong(7, course.startMinute.toLong())
                statement.bindLong(8, course.endMinute.toLong())
                statement.bindText(9, course.color)
                statement.bindLong(10, course.reminderMinutes.toLong())
                statement.bindLong(11, nowMillis)
                statement.bindLong(12, nowMillis)
                statement.step()
            } finally {
                statement.close()
            }

            val deleteWeeks = connection.prepare("DELETE FROM course_week WHERE course_id = ?")
            try {
                deleteWeeks.bindText(1, course.id)
                deleteWeeks.step()
            } finally {
                deleteWeeks.close()
            }

            course.weeks.sorted().forEach { week ->
                val insertWeek = connection.prepare(
                    "INSERT OR IGNORE INTO course_week (course_id, week_number) VALUES (?, ?)"
                )
                try {
                    insertWeek.bindText(1, course.id)
                    insertWeek.bindLong(2, week.toLong())
                    insertWeek.step()
                } finally {
                    insertWeek.close()
                }
            }

            connection.execSQL("COMMIT")
        } catch (error: Throwable) {
            runCatching { connection.execSQL("ROLLBACK") }
            throw error
        }
    }

    fun upsertEvent(event: EventSeries, nowMillis: Long = nowMillis()): Unit = withSyncLock(lock) {
        val statement = connection.prepare(
            """
            INSERT INTO event_series (
                id, type, title, note, is_all_day, start_date, start_minute,
                duration_minutes, freq, interval_value, weekdays, until_date,
                occurrence_count, month_end_policy, reminder_minutes, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                type = excluded.type,
                title = excluded.title,
                note = excluded.note,
                is_all_day = excluded.is_all_day,
                start_date = excluded.start_date,
                start_minute = excluded.start_minute,
                duration_minutes = excluded.duration_minutes,
                freq = excluded.freq,
                interval_value = excluded.interval_value,
                weekdays = excluded.weekdays,
                until_date = excluded.until_date,
                occurrence_count = excluded.occurrence_count,
                month_end_policy = excluded.month_end_policy,
                reminder_minutes = excluded.reminder_minutes,
                updated_at = excluded.updated_at
            """.trimIndent()
        )
        try {
            statement.bindText(1, event.id)
            statement.bindText(2, event.kind.name)
            statement.bindText(3, event.title)
            statement.bindText(4, event.note)
            statement.bindLong(5, if (event.isAllDay) 1 else 0)
            statement.bindText(6, event.startDate.toString())
            statement.bindNullableInt(7, event.startMinute)
            statement.bindNullableInt(8, event.durationMinutes)
            statement.bindText(9, event.recurrence.frequency.name)
            statement.bindLong(10, event.recurrence.interval.toLong())
            statement.bindText(11, weekdaysToText(event.recurrence.weekdays))
            statement.bindNullableText(12, event.recurrence.untilDate?.toString())
            statement.bindNullableInt(13, event.recurrence.occurrenceCount)
            statement.bindText(14, event.recurrence.monthEndPolicy.name)
            statement.bindLong(15, event.reminderMinutes.toLong())
            statement.bindLong(16, nowMillis)
            statement.bindLong(17, nowMillis)
            statement.step()
        } finally {
            statement.close()
        }
        Unit
    }

    fun upsertException(exception: OccurrenceException): Unit = withSyncLock(lock) {
        val statement = connection.prepare(
            """
            INSERT OR REPLACE INTO occurrence_exception (
                series_kind, series_id, original_date, type, new_date,
                new_start_minute, new_end_minute, new_duration_minutes
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )
        try {
            statement.bindText(1, exception.seriesKind.name)
            statement.bindText(2, exception.seriesId)
            statement.bindText(3, exception.originalDate.toString())
            statement.bindText(4, exception.type.name)
            statement.bindNullableText(5, exception.newDate?.toString())
            statement.bindNullableInt(6, exception.newStartMinute)
            statement.bindNullableInt(7, exception.newEndMinute)
            statement.bindNullableInt(8, exception.newDurationMinutes)
            statement.step()
        } finally {
            statement.close()
        }
        Unit
    }

    fun setCompleted(
        seriesId: String,
        instanceDate: LocalDate,
        completed: Boolean,
        nowMillis: Long = nowMillis(),
    ): Unit = withSyncLock(lock) {
        if (completed) {
            val statement = connection.prepare(
                """
                INSERT OR REPLACE INTO task_completion (event_series_id, occurrence_date, completed_at)
                VALUES (?, ?, ?)
                """.trimIndent()
            )
            try {
                statement.bindText(1, seriesId)
                statement.bindText(2, instanceDate.toString())
                statement.bindLong(3, nowMillis)
                statement.step()
            } finally {
                statement.close()
            }
        } else {
            val statement = connection.prepare(
                "DELETE FROM task_completion WHERE event_series_id = ? AND occurrence_date = ?"
            )
            try {
                statement.bindText(1, seriesId)
                statement.bindText(2, instanceDate.toString())
                statement.step()
            } finally {
                statement.close()
            }
        }
        Unit
    }

    fun deleteCourse(courseId: String): Unit = withSyncLock(lock) {
        val statement = connection.prepare("DELETE FROM course_series WHERE id = ?")
        try {
            statement.bindText(1, courseId)
            statement.step()
        } finally {
            statement.close()
        }
        val exceptions = connection.prepare(
            "DELETE FROM occurrence_exception WHERE series_kind = 'COURSE' AND series_id = ?"
        )
        try {
            exceptions.bindText(1, courseId)
            exceptions.step()
        } finally {
            exceptions.close()
        }
        Unit
    }

    fun deleteEvent(eventId: String): Unit = withSyncLock(lock) {
        val statement = connection.prepare("DELETE FROM event_series WHERE id = ?")
        try {
            statement.bindText(1, eventId)
            statement.step()
        } finally {
            statement.close()
        }
        val exceptions = connection.prepare(
            "DELETE FROM occurrence_exception WHERE series_kind = 'EVENT' AND series_id = ?"
        )
        try {
            exceptions.bindText(1, eventId)
            exceptions.step()
        } finally {
            exceptions.close()
        }
        Unit
    }

    private fun <T> query(sql: String, mapper: (SQLiteStatement) -> T): List<T> {
        val statement = connection.prepare(sql)
        val result = mutableListOf<T>()
        try {
            while (statement.step()) {
                result += mapper(statement)
            }
        } finally {
            statement.close()
        }
        return result
    }

    private fun SQLiteStatement.textOrNull(index: Int): String? =
        if (isNull(index)) null else getText(index)

    private fun SQLiteStatement.intOrNull(index: Int): Int? =
        if (isNull(index)) null else getLong(index).toInt()

    private fun SQLiteStatement.bindNullableInt(index: Int, value: Int?) {
        if (value == null) bindNull(index) else bindLong(index, value.toLong())
    }

    private fun SQLiteStatement.bindNullableText(index: Int, value: String?) {
        if (value == null) bindNull(index) else bindText(index, value)
    }

    private fun parseWeekdays(raw: String): Set<Int> =
        raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    private fun weekdaysToText(weekdays: Set<Int>): String =
        weekdays.sorted().joinToString(",")
}
