package com.hucw.xinqing2.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

data class Migration(
    val fromVersion: Int,
    val toVersion: Int,
    val migrate: (SQLiteConnection) -> Unit,
)

object XinqingSchema {
    const val VERSION = 2

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
        },
        Migration(1, 2) { connection ->
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS semester (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    start_date TEXT NOT NULL,
                    total_weeks INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS course_series (
                    id TEXT NOT NULL PRIMARY KEY,
                    semester_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    teacher TEXT NOT NULL DEFAULT '',
                    location TEXT NOT NULL DEFAULT '',
                    weekday INTEGER NOT NULL,
                    start_minute INTEGER NOT NULL,
                    end_minute INTEGER NOT NULL,
                    color TEXT NOT NULL DEFAULT '',
                    reminder_minutes INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (semester_id) REFERENCES semester(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS course_week (
                    course_id TEXT NOT NULL,
                    week_number INTEGER NOT NULL,
                    PRIMARY KEY (course_id, week_number),
                    FOREIGN KEY (course_id) REFERENCES course_series(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS event_series (
                    id TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    is_all_day INTEGER NOT NULL DEFAULT 0,
                    start_date TEXT NOT NULL,
                    start_minute INTEGER,
                    duration_minutes INTEGER,
                    freq TEXT NOT NULL DEFAULT 'NONE',
                    interval_value INTEGER NOT NULL DEFAULT 1,
                    weekdays TEXT NOT NULL DEFAULT '',
                    until_date TEXT,
                    occurrence_count INTEGER,
                    month_end_policy TEXT NOT NULL DEFAULT 'SKIP',
                    reminder_minutes INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS occurrence_exception (
                    series_kind TEXT NOT NULL,
                    series_id TEXT NOT NULL,
                    original_date TEXT NOT NULL,
                    type TEXT NOT NULL,
                    new_date TEXT,
                    new_start_minute INTEGER,
                    new_end_minute INTEGER,
                    new_duration_minutes INTEGER,
                    PRIMARY KEY (series_kind, series_id, original_date)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS task_completion (
                    event_series_id TEXT NOT NULL,
                    occurrence_date TEXT NOT NULL,
                    completed_at INTEGER NOT NULL,
                    PRIMARY KEY (event_series_id, occurrence_date),
                    FOREIGN KEY (event_series_id) REFERENCES event_series(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_course_series_semester_weekday ON course_series(semester_id, weekday)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_event_series_start_date ON event_series(start_date)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_occurrence_exception_new_date ON occurrence_exception(new_date)"
            )
        },
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
