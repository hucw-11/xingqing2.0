package com.hucw.xinqing2.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

data class Migration(
    val fromVersion: Int,
    val toVersion: Int,
    val migrate: (SQLiteConnection) -> Unit,
)

object XinqingSchema {
    const val VERSION = 1

    val migrations: List<Migration> = listOf(
        Migration(0, 1) { connection ->
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_meta (
                    key TEXT NOT NULL PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    )
}

class DatabaseHandle internal constructor(
    val connection: SQLiteConnection,
) : AutoCloseable {
    override fun close() = connection.close()
}

object XinqingDatabase {
    fun open(path: String, targetVersion: Int = XinqingSchema.VERSION): DatabaseHandle {
        val connection = createDatabaseDriver().open(path)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
            val currentVersion = readUserVersion(connection)
            require(currentVersion <= targetVersion) {
                "数据库版本 $currentVersion 高于当前支持的 $targetVersion，请升级应用后再打开"
            }
            if (currentVersion < targetVersion) {
                migrate(connection, currentVersion, targetVersion)
            }
            return DatabaseHandle(connection)
        } catch (error: Throwable) {
            connection.close()
            throw error
        }
    }

    fun migrate(
        connection: SQLiteConnection,
        fromVersion: Int,
        targetVersion: Int,
        migrations: List<Migration> = XinqingSchema.migrations,
    ) {
        if (fromVersion == targetVersion) return
        require(fromVersion < targetVersion) {
            "不支持把数据库从 $fromVersion 降到 $targetVersion"
        }

        connection.execSQL("BEGIN IMMEDIATE")
        try {
            var currentVersion = fromVersion
            while (currentVersion < targetVersion) {
                val migration = migrations.firstOrNull { it.fromVersion == currentVersion }
                    ?: error("缺少从版本 $currentVersion 开始的数据库迁移")
                check(migration.toVersion > currentVersion) {
                    "迁移 ${migration.fromVersion} -> ${migration.toVersion} 的版本号不前进"
                }
                migration.migrate(connection)
                currentVersion = migration.toVersion
            }
            connection.execSQL("PRAGMA user_version = $targetVersion")
            connection.execSQL("COMMIT")
        } catch (error: Throwable) {
            runCatching { connection.execSQL("ROLLBACK") }
            throw error
        }
    }

    fun readUserVersion(connection: SQLiteConnection): Int {
        val statement = connection.prepare("PRAGMA user_version")
        return try {
            if (statement.step()) statement.getLong(0).toInt() else 0
        } finally {
            statement.close()
        }
    }
}
