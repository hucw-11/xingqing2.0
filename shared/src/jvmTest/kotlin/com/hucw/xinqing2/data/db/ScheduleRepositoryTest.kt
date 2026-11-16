package com.hucw.xinqing2.data.db

import androidx.sqlite.execSQL
import com.hucw.xinqing2.domain.schedule.CompletionKey
import com.hucw.xinqing2.domain.schedule.CourseSeries
import com.hucw.xinqing2.domain.schedule.EventSeries
import com.hucw.xinqing2.domain.schedule.ExceptionType
import com.hucw.xinqing2.domain.schedule.ItemKind
import com.hucw.xinqing2.domain.schedule.OccurrenceException
import com.hucw.xinqing2.domain.schedule.RecurrenceFrequency
import com.hucw.xinqing2.domain.schedule.RecurrenceRule
import com.hucw.xinqing2.domain.schedule.Semester
import com.hucw.xinqing2.domain.schedule.SeriesKind
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleRepositoryTest {
    private fun tempDatabasePath(): String =
        Files.createTempFile("xinqing2-schedule-", ".db").toAbsolutePath().toString()

    @Test
    fun migrationV1ToV2PreservesMetaAndCreatesScheduleTables() {
        val path = tempDatabasePath()
        val connection = createDatabaseDriver().open(path)
        try {
            connection.execSQL(
                "CREATE TABLE app_meta (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
            )
            connection.execSQL("INSERT INTO app_meta (key, value) VALUES ('probe', 'kept')")
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }

        XinqingDatabase.open(path).use { handle ->
            assertEquals(2, XinqingDatabase.readUserVersion(handle.connection))
            assertEquals("kept", AppMetaRepository(handle.connection).get("probe"))

            val repository = ScheduleRepository(handle.connection)
            repository.upsertSemester(
                Semester("s1", "测试学期", LocalDate.parse("2026-09-07"), 20),
                nowMillis = 1,
            )
            assertEquals("s1", repository.loadSemester()?.id)
        }
    }

    @Test
    fun writesScheduleAndKeepsCompletionPerInstance() {
        val path = tempDatabasePath()
        val day = LocalDate.parse("2026-09-16")

        XinqingDatabase.open(path).use { handle ->
            val repository = ScheduleRepository(handle.connection)
            repository.upsertSemester(
                Semester("s1", "测试学期", LocalDate.parse("2026-09-07"), 20),
                nowMillis = 1,
            )
            repository.upsertCourse(
                CourseSeries(
                    id = "c1",
                    semesterId = "s1",
                    name = "高等数学",
                    teacher = "张老师",
                    location = "教三 101",
                    weekday = 3,
                    startMinute = 480,
                    endMinute = 580,
                    color = "",
                    weeks = setOf(1, 2, 3),
                ),
                nowMillis = 2,
            )
            repository.upsertEvent(
                EventSeries(
                    id = "t1",
                    kind = ItemKind.TODO,
                    title = "交作业",
                    note = "",
                    isAllDay = false,
                    startDate = day,
                    startMinute = 600,
                    durationMinutes = 30,
                    recurrence = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
                ),
                nowMillis = 3,
            )
            repository.upsertException(
                OccurrenceException(
                    seriesKind = SeriesKind.EVENT,
                    seriesId = "t1",
                    originalDate = LocalDate.parse("2026-09-30"),
                    type = ExceptionType.CANCELLED,
                )
            )

            assertEquals(1, repository.loadCourses().size)
            assertEquals(setOf(1, 2, 3), repository.loadCourses().single().weeks)
            assertEquals(1, repository.loadEvents().size)
            assertEquals(1, repository.loadExceptions().size)

            val before = repository.loadDay(day)
            assertEquals(2, before.timed.size)
            assertTrue(before.timed.none { it.occurrence.seriesId == "t1" && it.occurrence.completed })

            repository.setCompleted("t1", day, completed = true, nowMillis = 4)
            assertEquals(
                setOf(CompletionKey("t1", day)),
                repository.loadCompletions(),
            )

            val after = repository.loadDay(day)
            assertTrue(
                after.timed.first { it.occurrence.seriesId == "t1" }.occurrence.completed
            )
        }
    }
}
