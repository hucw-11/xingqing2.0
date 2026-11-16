package com.hucw.xinqing2.data.db

import androidx.sqlite.SQLiteConnection
import com.hucw.xinqing2.data.withSyncLock

class AppMetaRepository(private val connection: SQLiteConnection) {
    private val lock = Any()

    fun get(key: String): String? = withSyncLock(lock) {
        val statement = connection.prepare("SELECT value FROM app_meta WHERE key = ?")
        try {
            statement.bindText(1, key)
            if (statement.step() && !statement.isNull(0)) statement.getText(0) else null
        } finally {
            statement.close()
        }
    }

    fun put(key: String, value: String): Unit = withSyncLock(lock) {
        val statement = connection.prepare(
            "INSERT OR REPLACE INTO app_meta (key, value) VALUES (?, ?)"
        )
        try {
            statement.bindText(1, key)
            statement.bindText(2, value)
            statement.step()
        } finally {
            statement.close()
        }
        Unit
    }

    fun getInt(key: String, defaultValue: Int = 0): Int = get(key)?.toIntOrNull() ?: defaultValue

    fun incrementInt(key: String, defaultValue: Int = 0): Int {
        val next = getInt(key, defaultValue) + 1
        put(key, next.toString())
        return next
    }
}
