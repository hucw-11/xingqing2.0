package com.shangkeschedule

import com.shangkeschedule.data.repository.matchesLocalDateRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 日期筛选用记录自己的时区判定本地日期，而不是假设每天固定 24 小时。
 */
class DiaryDateFilterTest {

    @Test
    fun `同一 UTC 时刻在不同时区属于不同日期`() {
        val shanghai = TimeZone.of("Asia/Shanghai")
        // 上海 2026-11-16 00:30 = UTC 2026-11-15 16:30
        val epoch = LocalDateTime(2026, 11, 16, 0, 30)
            .toInstant(shanghai)
            .toEpochMilliseconds()

        assertTrue(
            matchesLocalDateRange(
                epoch,
                shanghai.id,
                LocalDate(2026, 11, 16),
                LocalDate(2026, 11, 16)
            )
        )
        assertFalse(
            matchesLocalDateRange(
                epoch,
                TimeZone.UTC.id,
                LocalDate(2026, 11, 16),
                LocalDate(2026, 11, 16)
            )
        )
        assertTrue(
            matchesLocalDateRange(
                epoch,
                TimeZone.UTC.id,
                LocalDate(2026, 11, 15),
                LocalDate(2026, 11, 15)
            )
        )
    }

    @Test
    fun `夏令时切换日仍按本地日期判断`() {
        val newYork = TimeZone.of("America/New_York")
        val early = LocalDateTime(2026, 3, 8, 0, 30).toInstant(newYork).toEpochMilliseconds()
        val late = LocalDateTime(2026, 3, 8, 23, 30).toInstant(newYork).toEpochMilliseconds()
        listOf(early, late).forEach { epoch ->
            assertTrue(
                matchesLocalDateRange(
                    epoch,
                    newYork.id,
                    LocalDate(2026, 3, 8),
                    LocalDate(2026, 3, 8)
                )
            )
        }
    }
}
