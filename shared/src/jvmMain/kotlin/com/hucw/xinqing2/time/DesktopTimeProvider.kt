package com.hucw.xinqing2.time

import kotlinx.datetime.LocalDate

class DesktopTimeProvider : TimeProvider {
    override fun today(): LocalDate = LocalDate.parse(java.time.LocalDate.now().toString())

    override fun nowMinute(): Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
}
