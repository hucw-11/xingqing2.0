package com.hucw.xinqing2.data

internal expect fun <T> withSyncLock(lock: Any, block: () -> T): T
