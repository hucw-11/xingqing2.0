package com.hucw.xinqing2.domain.schedule

import kotlinx.datetime.LocalDate

internal fun LocalDate.plusDays(days: Int): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() + days)

internal fun LocalDate.minusDays(days: Int): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() - days)

internal fun LocalDate.isoWeekday(): Int = dayOfWeek.ordinal + 1

internal fun LocalDate.mondayOfWeek(): LocalDate = minusDays(isoWeekday() - 1)

internal fun LocalDate.daysUntilDate(other: LocalDate): Int =
    (other.toEpochDays() - toEpochDays()).toInt()

internal fun LocalDate.lastDayOfMonth(): Int = when (month.number) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 30
}

internal fun monthsBetween(start: LocalDate, end: LocalDate): Int =
    (end.year * 12 + end.month.number) - (start.year * 12 + start.month.number)

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
