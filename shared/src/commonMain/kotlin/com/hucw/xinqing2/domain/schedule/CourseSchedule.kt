package com.hucw.xinqing2.domain.schedule

import kotlinx.datetime.LocalDate

object CourseSchedule {
    fun weekNumber(date: LocalDate, semesterStart: LocalDate): Int {
        if (date < semesterStart) return 0
        return semesterStart.daysUntilDate(date) / 7 + 1
    }

    fun occursOn(course: CourseSeries, date: LocalDate, semester: Semester): Boolean =
        date.isoWeekday() == course.weekday &&
            weekNumber(date, semester.startDate) in course.weeks

    fun occurrencesForDay(
        course: CourseSeries,
        date: LocalDate,
        semester: Semester,
        exceptions: List<OccurrenceException>,
    ): List<Occurrence> {
        val mine = exceptions.filter {
            it.seriesKind == SeriesKind.COURSE && it.seriesId == course.id
        }
        val suppressed = mine.any {
            it.originalDate == date &&
                (it.type == ExceptionType.CANCELLED || it.type == ExceptionType.MOVED)
        }

        val result = mutableListOf<Occurrence>()
        if (!suppressed && occursOn(course, date, semester)) {
            result += occurrence(course, instanceDate = date, date = date, start = course.startMinute, end = course.endMinute)
        }

        mine.filter { it.type == ExceptionType.MOVED && it.newDate == date }.forEach { moved ->
            val start = moved.newStartMinute ?: course.startMinute
            val end = moved.newEndMinute ?: (start + (course.endMinute - course.startMinute))
            result += occurrence(
                course = course,
                instanceDate = moved.originalDate,
                date = date,
                start = start,
                end = maxOf(start + 1, end),
            )
        }

        return result.distinctBy { it.instanceDate }
    }

    private fun occurrence(
        course: CourseSeries,
        instanceDate: LocalDate,
        date: LocalDate,
        start: Int,
        end: Int,
    ): Occurrence = Occurrence(
        seriesId = course.id,
        kind = ItemKind.COURSE,
        instanceDate = instanceDate,
        date = date,
        title = course.name,
        startMinute = start,
        durationMinutes = end - start,
        isAllDay = false,
        location = course.location,
        color = course.color,
        completed = false,
        crossesMidnight = end > 1440,
    )
}
