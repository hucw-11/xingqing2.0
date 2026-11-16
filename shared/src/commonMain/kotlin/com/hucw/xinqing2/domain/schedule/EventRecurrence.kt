package com.hucw.xinqing2.domain.schedule

import kotlinx.datetime.LocalDate

object EventRecurrence {
    fun occursOn(event: EventSeries, date: LocalDate): Boolean {
        val rule = event.recurrence
        if (date < event.startDate) return false
        if (rule.untilDate != null && date > rule.untilDate) return false
        if (!matchesPattern(event, date)) return false
        return rule.occurrenceCount == null || ordinalOf(event, date) <= rule.occurrenceCount
    }

    private fun matchesPattern(event: EventSeries, date: LocalDate): Boolean {
        val rule = event.recurrence
        if (rule.frequency == RecurrenceFrequency.NONE) return date == event.startDate

        return when (rule.frequency) {
            RecurrenceFrequency.DAILY ->
                event.startDate.daysUntilDate(date) % rule.interval == 0

            RecurrenceFrequency.WEEKLY -> {
                val selected = rule.weekdays.ifEmpty { setOf(event.startDate.isoWeekday()) }
                if (date.isoWeekday() !in selected) return false
                val weeks = event.startDate.mondayOfWeek().daysUntilDate(date.mondayOfWeek()) / 7
                weeks % rule.interval == 0
            }

            RecurrenceFrequency.MONTHLY -> {
                val months = monthsBetween(event.startDate, date)
                if (months % rule.interval != 0) return false
                val targetDay = event.startDate.day
                val lastDay = date.lastDayOfMonth()
                when {
                    targetDay <= lastDay -> date.day == targetDay
                    rule.monthEndPolicy == MonthEndPolicy.LAST_DAY -> date.day == lastDay
                    else -> false
                }
            }

            RecurrenceFrequency.NONE -> date == event.startDate
        }
    }

    private fun ordinalOf(event: EventSeries, date: LocalDate): Int {
        var count = 0
        var cursor = event.startDate
        var guard = 0
        while (cursor <= date && guard < 366 * 100) {
            if (matchesPattern(event, cursor)) count += 1
            cursor = cursor.plusDays(1)
            guard += 1
        }
        return count
    }
}
