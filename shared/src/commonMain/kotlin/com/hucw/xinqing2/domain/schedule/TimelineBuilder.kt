package com.hucw.xinqing2.domain.schedule

import kotlinx.datetime.LocalDate

object TimelineBuilder {
    fun build(
        date: LocalDate,
        semester: Semester?,
        courses: List<CourseSeries>,
        events: List<EventSeries>,
        exceptions: List<OccurrenceException>,
        completions: Set<CompletionKey>,
    ): DayTimeline {
        val courseOccurrences = if (semester == null) {
            emptyList()
        } else {
            courses.flatMap { CourseSchedule.occurrencesForDay(it, date, semester, exceptions) }
        }
        val eventOccurrences = events.flatMap {
            EventSchedule.occurrencesForDay(it, date, exceptions, completions)
        }

        val all = (courseOccurrences + eventOccurrences)
            .distinctBy { Triple(it.seriesId, it.instanceDate, it.startMinute) }

        val allDay = all.filter { it.isAllDay }
        val noTimeTodos = all.filter { !it.isAllDay && it.startMinute == null }
        val timed = all.filter { !it.isAllDay && it.startMinute != null }

        return DayTimeline(
            date = date,
            timed = layout(timed),
            allDay = allDay,
            noTimeTodos = noTimeTodos,
        )
    }

    private fun layout(items: List<Occurrence>): List<PositionedOccurrence> {
        val sorted = items.sortedWith(
            compareBy<Occurrence> { it.startMinute ?: 0 }
                .thenBy { (it.startMinute ?: 0) + (it.durationMinutes ?: 0) }
        )

        val result = mutableListOf<PositionedOccurrence>()
        val cluster = mutableListOf<Pair<Occurrence, Int>>()
        val columnEnds = mutableListOf<Int>()
        var clusterEnd = -1

        fun flush() {
            if (cluster.isEmpty()) return
            val columnCount = cluster.maxOf { it.second } + 1
            cluster.forEach { (occurrence, column) ->
                result += PositionedOccurrence(occurrence, column, columnCount)
            }
            cluster.clear()
            columnEnds.clear()
            clusterEnd = -1
        }

        sorted.forEach { occurrence ->
            val start = occurrence.startMinute ?: 0
            val end = start + (occurrence.durationMinutes ?: 0)

            if (cluster.isNotEmpty() && start >= clusterEnd) flush()

            var column = columnEnds.indexOfFirst { it <= start }
            if (column == -1) {
                column = columnEnds.size
                columnEnds.add(end)
            } else {
                columnEnds[column] = end
            }

            cluster += occurrence to column
            clusterEnd = maxOf(clusterEnd, end)
        }
        flush()

        return result
    }
}
