package com.hucw.xinqing2.time

import kotlinx.datetime.LocalDate

interface TimeProvider {
    fun today(): LocalDate
    fun nowMinute(): Int
}
