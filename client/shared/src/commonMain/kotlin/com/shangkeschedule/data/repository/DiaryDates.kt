package com.shangkeschedule.data.repository

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * 日期筛选的粗粒度 UTC 区间。
 *
 * 每条记录自带发生时的时区，同一“本地日期”在不同时区对应的 UTC 区间不同。
 * SQL 先用 UTC 前后各放宽一天的范围取出候选，真正的一天边界再按记录自己的时区
 * 在内存中判定（见 [matchesLocalDateRange]），避免跨时区补记被错误归到相邻日期。
 */
internal fun widenedUtcRange(startDate: LocalDate?, endDate: LocalDate?): Pair<Long, Long> {
    val start = startDate
        ?.atStartOfDayIn(TimeZone.UTC)
        ?.toEpochMilliseconds()
        ?.let { it - DAY_MILLIS }
        ?: 0L
    val end = endDate
        ?.plus(1, DateTimeUnit.DAY)
        ?.atStartOfDayIn(TimeZone.UTC)
        ?.toEpochMilliseconds()
        ?.let { it + DAY_MILLIS }
        ?: Long.MAX_VALUE
    return start to end
}

internal fun localDateOf(epochMillis: Long, timeZoneId: String): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(runCatching { TimeZone.of(timeZoneId) }.getOrElse { TimeZone.UTC })
        .date

internal fun matchesLocalDateRange(
    epochMillis: Long,
    timeZoneId: String,
    startDate: LocalDate?,
    endDate: LocalDate?
): Boolean {
    if (startDate == null && endDate == null) return true
    val date = localDateOf(epochMillis, timeZoneId)
    if (startDate != null && date < startDate) return false
    if (endDate != null && date > endDate) return false
    return true
}
