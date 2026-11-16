package com.hucw.xinqing2.data

internal actual fun <T> withSyncLock(lock: Any, block: () -> T): T = synchronized(lock, block)
