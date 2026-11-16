package com.hucw.xinqing2.domain.schedule

import kotlinx.datetime.LocalDate

object EventSchedule {
    fun occurrencesForDay(
        event: EventSeries,
        date: LocalDate,
        exceptions: List<OccurrenceException>,
        completions: Set<CompletionKey>,
    ): List<Occurrence> {
        val mine = exceptions.filter {
            it.seriesKind == SeriesKind.EVENT && it.seriesId == event.id
        }
        val maxSpanDays = maxSpanDays(event, mine)
        val result = mutableListOf<Occurrence>()

        var candidate = date.minusDays(maxSpanDays - 1)
        while (candidate <= date) {
            startingOccurrences(event, candidate, mine).forEach { occurrence ->
                visibleOn(event, occurrence, date, completions)?.let { result += it }
            }
            candidate = candidate.plusDays(1)
        }

        return result.distinctBy { Triple(it.instanceDate, it.date, it.startMinute) }
    }

    private fun startingOccurrences(
        event: EventSeries,
        date: LocalDate,
        exceptions: List<OccurrenceException>,
    ): List<Occurrence> {
        val suppressed = exceptions.any {
            it.originalDate == date &&
                (it.type == ExceptionType.CANCELLED || it.type == ExceptionType.MOVED)
        }

        val result = mutableListOf<Occurrence>()
        if (!suppressed && EventRecurrence.occursOn(event, date)) {
            result += occurrence(
                event = event,
                instanceDate = date,
                date = date,
                startMinute = event.startMinute,
                durationMinutes = event.durationMinutes,
            )
        }

        exceptions.filter { it.type == ExceptionType.MOVED && it.newDate == date }.forEach { moved ->
            result += occurrence(
                event = event,
                instanceDate = moved.originalDate,
                date = date,
                startMinute = moved.newStartMinute ?: event.startMinute,
                durationMinutes = moved.newDurationMinutes ?: event.durationMinutes,
            )
        }

        return result
    }

    private fun visibleOn(
        event: EventSeries,
        occurrence: Occurrence,
        date: LocalDate,
        completions: Set<CompletionKey>,
    ): Occurrence? {
        val completed = event.kind == ItemKind.TODO &&
            completions.contains(CompletionKey(event.id, occurrence.instanceDate))

        if (occurrence.isAllDay) {
            val days = maxOf(1, ((occurrence.durationMinutes ?: 1440) + 1439) / 1440)
            val endDate = occurrence.date.plusDays(days - 1)
            if (date < occurrence.date || date > endDate) return null
            return occurrence.copy(
                startMinute = null,
                durationMinutes = null,
                completed = completed,
                crossesMidnight = false,
            )
        }

        val startMinute = occurrence.startMinute ?: run {
            if (occurrence.date != date) return null
            return occurrence.copy(completed = completed)
        }

        val duration = maxOf(1, occurrence.durationMinutes ?: 1)
        val dayOffset = occurrence.date.daysUntilDate(date)
        val startOnDay = startMinute - dayOffset * 1440
        val endOnDay = startOnDay + duration

        if (endOnDay <= 0 || startOnDay >= 1440) return null
        val visibleStart = maxOf(0, startOnDay)
        val visibleEnd = minOf(1440, endOnDay)
        if (visibleEnd <= visibleStart) return null

        return occurrence.copy(
            startMinute = visibleStart,
            durationMinutes = visibleEnd - visibleStart,
            completed = completed,
            crossesMidnight = startOnDay < 0 || endOnDay > 1440,
        )
    }

    private fun occurrence(
        event: EventSeries,
        instanceDate: LocalDate,
        date: LocalDate,
        startMinute: Int?,
        durationMinutes: Int?,
    ): Occurrence = Occurrence(
        seriesId = event.id,
        kind = event.kind,
        instanceDate = instanceDate,
        date = date,
        title = event.title,
        startMinute = startMinute,
        durationMinutes = durationMinutes,
        isAllDay = event.isAllDay,
        location = "",
        color = "",
        completed = false,
        crossesMidnight = false,
    )

    private fun maxSpanDays(
        event: EventSeries,
        exceptions: List<OccurrenceException>,
    ): Int {
        val base = spanDays(event.isAllDay, event.startMinute, event.durationMinutes)
        val moved = exceptions
            .filter { it.type == ExceptionType.MOVED }
            .map {
                spanDays(
                    isAllDay = event.isAllDay,
                    startMinute = it.newStartMinute ?: event.startMinute,
                    durationMinutes = it.newDurationMinutes ?: event.durationMinutes,
                )
            }
        return maxOf(base, moved.maxOrNull() ?: 1)
    }

    private fun spanDays(isAllDay: Boolean, startMinute: Int?, durationMinutes: Int?): Int {
        if (isAllDay) {
            return maxOf(1, ((durationMinutes ?: 1440) + 1439) / 1440)
        }
        val start = startMinute ?: return 1
        val duration = durationMinutes ?: return 1
        return maxOf(1, (start + duration + 1439) / 1440)
    }
}
