package com.hucw.xinqing2.domain.schedule

import kotlin.random.Random

private const val HEX = "0123456789abcdef"

fun randomId(): String = buildString(32) {
    repeat(32) {
        append(HEX[Random.nextInt(HEX.length)])
    }
}
