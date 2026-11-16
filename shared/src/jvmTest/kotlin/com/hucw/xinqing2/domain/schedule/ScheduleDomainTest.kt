package com.hucw.xinqing2.domain.schedule

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleDomainTest {
    private val semester = Semester(
        id = "s1",
        name = "测试学期",
        startDate = LocalDate.parse("2026-09-07"),
        totalWeeks = 20,
    )

    @Test
    fun a01_mixesCoursesEventsAndUntimedTodos() {
        val date = LocalDate.parse("2026-09-16")
        val courses = listOf(
            course("c1", weekday = 3, start = 480, end = 580),
            course("c2", weekday = 3, start = 840, end = 940),
        )
        val events = listOf(
            event("e1", date, start = 600, duration = 60),
            event("e2", date, start = 1140, duration = 60),
            todo("t1", date, start = null, duration = null),
        )

        val timeline = TimelineBuilder.build(
            date = date,
            semester = semester,
            courses = courses,
            events = events,
            exceptions = emptyList(),
            completions = emptySet(),
        )

        assertEquals(4, timeline.timed.size)
        assertEquals(1, timeline.noTimeTodos.size)
        assertEquals(0, timeline.allDay.size)
        assertEquals(listOf(480, 600, 840, 1140), timeline.timed.map { it.occurrence.startMinute!! })
        assertTrue(timeline.timed.all { it.columnCount == 1 })
        assertNull(timeline.noTimeTodos.single().startMinute)
    }

    @Test
    fun a02_overlappingAndCrossMidnightEventsAreVisibleOnBothDays() {
        val day1 = LocalDate.parse("2026-09-16")
        val day2 = LocalDate.parse("2026-09-17")
        val longEvent = event("long", day1, start = 23 * 60, duration = 180)
        val shortEvent = event("short", day1, start = 23 * 60 + 30, duration = 60)

        val firstDay = TimelineBuilder.build(
            date = day1,
            semester = semester,
            courses = emptyList(),
            events = listOf(longEvent, shortEvent),
            exceptions = emptyList(),
            completions = emptySet(),
        )
        assertEquals(2, firstDay.timed.size)
        assertEquals(2, firstDay.timed.first().columnCount)
        assertTrue(firstDay.timed.all { it.occurrence.crossesMidnight })
        assertEquals(60, firstDay.timed.first { it.occurrence.seriesId == "long" }.occurrence.durationMinutes)
        assertEquals(30, firstDay.timed.first { it.occurrence.seriesId == "short" }.occurrence.durationMinutes)

        val secondDay = TimelineBuilder.build(
            date = day2,
            semester = semester,
            courses = emptyList(),
            events = listOf(longEvent, shortEvent),
            exceptions = emptyList(),
            completions = emptySet(),
        )
        assertEquals(2, secondDay.timed.size)
        assertEquals(0, secondDay.timed.first { it.occurrence.seriesId == "long" }.occurrence.startMinute)
        assertEquals(120, secondDay.timed.first { it.occurrence.seriesId == "long" }.occurrence.durationMinutes)
        assertEquals(30, secondDay.timed.first { it.occurrence.seriesId == "short" }.occurrence.durationMinutes)
        assertEquals(2, secondDay.timed.first().columnCount)
    }

    @Test
    fun a03_oddEvenWeeksAndSingleReschedule() {
        val oddWeeks = setOf(1, 3, 5, 7)
        val oddCourse = course("odd", weekday = 3, start = 480, end = 580, weeks = oddWeeks)
        assertFalse(CourseSchedule.occursOn(oddCourse, LocalDate.parse("2026-09-16"), semester))
        assertTrue(CourseSchedule.occursOn(oddCourse, LocalDate.parse("2026-09-23"), semester))

        val fullCourse = course("c2", weekday = 3, start = 480, end = 580)
        val originalDate = LocalDate.parse("2026-09-16")
        val movedDate = LocalDate.parse("2026-09-18")
        val moved = OccurrenceException(
            seriesKind = SeriesKind.COURSE,
            seriesId = fullCourse.id,
            originalDate = originalDate,
            type = ExceptionType.MOVED,
            newDate = movedDate,
            newStartMinute = 600,
            newEndMinute = 700,
        )

        assertTrue(
            CourseSchedule.occurrencesForDay(fullCourse, originalDate, semester, listOf(moved)).isEmpty()
        )

        val movedDay = CourseSchedule.occurrencesForDay(fullCourse, movedDate, semester, listOf(moved))
        assertEquals(1, movedDay.size)
        assertEquals(originalDate, movedDay.single().instanceDate)
        assertEquals(600, movedDay.single().startMinute)
        assertEquals(100, movedDay.single().durationMinutes)
    }

    @Test
    fun a06_completingOneRecurringTodoDoesNotCompleteFutureInstances() {
        val start = LocalDate.parse("2026-09-16")
        val recurringTodo = EventSeries(
            id = "t1",
            kind = ItemKind.TODO,
            title = "交作业",
            note = "",
            isAllDay = false,
            startDate = start,
            startMinute = 600,
            durationMinutes = 30,
            recurrence = RecurrenceRule(frequency = RecurrenceFrequency.WEEKLY),
        )
        val completed = setOf(CompletionKey("t1", start))

        val firstDay = TimelineBuilder.build(
            date = start,
            semester = semester,
            courses = emptyList(),
            events = listOf(recurringTodo),
            exceptions = emptyList(),
            completions = completed,
        )
        assertTrue(firstDay.timed.single().occurrence.completed)

        val nextDay = TimelineBuilder.build(
            date = LocalDate.parse("2026-09-23"),
            semester = semester,
            courses = emptyList(),
            events = listOf(recurringTodo),
            exceptions = emptyList(),
            completions = completed,
        )
        assertFalse(nextDay.timed.single().occurrence.completed)
    }

    @Test
    fun monthly31stSkipsShortMonthsUnlessLastDayPolicy() {
        val start = LocalDate.parse("2026-01-31")
        val skip = EventSeries(
            id = "monthly-skip",
            kind = ItemKind.EVENT,
            title = "月结",
            note = "",
            isAllDay = false,
            startDate = start,
            startMinute = 600,
            durationMinutes = 30,
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                monthEndPolicy = MonthEndPolicy.SKIP,
            ),
        )
        val last = skip.copy(
            id = "monthly-last",
            recurrence = RecurrenceRule(
                frequency = RecurrenceFrequency.MONTHLY,
                monthEndPolicy = MonthEndPolicy.LAST_DAY,
            ),
        )

        assertFalse(EventRecurrence.occursOn(skip, LocalDate.parse("2026-02-28")))
        assertTrue(EventRecurrence.occursOn(last, LocalDate.parse("2026-02-28")))
        assertTrue(EventRecurrence.occursOn(skip, LocalDate.parse("2026-03-31")))
        assertFalse(EventRecurrence.occursOn(skip, LocalDate.parse("2026-04-30")))
        assertTrue(EventRecurrence.occursOn(last, LocalDate.parse("2026-04-30")))
    }

    @Test
    fun allDayEventIsSeparatedFromTheTimedTimeline() {
        val date = LocalDate.parse("2026-09-16")
        val allDay = EventSeries(
            id = "all-day",
            kind = ItemKind.EVENT,
            title = "全天",
            note = "",
            isAllDay = true,
            startDate = date,
            startMinute = null,
            durationMinutes = 1440,
        )

        val timeline = TimelineBuilder.build(
            date = date,
            semester = semester,
            courses = emptyList(),
            events = listOf(allDay),
            exceptions = emptyList(),
            completions = emptySet(),
        )

        assertEquals(1, timeline.allDay.size)
        assertTrue(timeline.timed.isEmpty())
        assertTrue(timeline.noTimeTodos.isEmpty())
    }

    @Test
    fun adjacentEventsDoNotShareLanes() {
        val date = LocalDate.parse("2026-09-16")
        val first = event("e1", date, start = 600, duration = 60)
        val second = event("e2", date, start = 660, duration = 60)

        val timeline = TimelineBuilder.build(
            date = date,
            semester = semester,
            courses = emptyList(),
            events = listOf(first, second),
            exceptions = emptyList(),
            completions = emptySet(),
        )

        assertEquals(2, timeline.timed.size)
        assertTrue(timeline.timed.all { it.columnCount == 1 })
    }

    private fun course(
        id: String,
        weekday: Int,
        start: Int,
        end: Int,
        weeks: Set<Int> = (1..20).toSet(),
    ): CourseSeries = CourseSeries(
        id = id,
        semesterId = semester.id,
        name = id,
        teacher = "",
        location = "",
        weekday = weekday,
        startMinute = start,
        endMinute = end,
        color = "",
        weeks = weeks,
    )

    private fun event(id: String, date: LocalDate, start: Int?, duration: Int?): EventSeries =
        EventSeries(
            id = id,
            kind = ItemKind.EVENT,
            title = id,
            note = "",
            isAllDay = false,
            startDate = date,
            startMinute = start,
            durationMinutes = duration,
        )

    private fun todo(
        id: String,
        date: LocalDate,
        start: Int?,
        duration: Int?,
        recurrence: RecurrenceRule = RecurrenceRule(),
    ): EventSeries = EventSeries(
        id = id,
        kind = ItemKind.TODO,
        title = id,
        note = "",
        isAllDay = false,
        startDate = date,
        startMinute = start,
        durationMinutes = duration,
        recurrence = recurrence,
    )
}
