package com.shangkeschedule

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.shangkeschedule.data.db.main.MIGRATION_16_17
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v16 → v17 迁移的真实执行测试（不是只检查迁移对象是否存在）。
 *
 * 构造一个带课程数据的最小 v16 库，执行新迁移，验证：
 * - 原有课程行原样保留；
 * - 六张日记相关表与唯一/普通索引确实建好；
 * - 没有任何破坏性语句（DROP/重建课程表）。
 */
class DiaryMigrationTest {

    private val tempDir: File = Files.createTempDirectory("xinqing-diary-migration").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `v16 升级到 v17 保留课程数据并创建日记表`() = runBlocking {
        val dbFile = File(tempDir, "v16.db")
        val driver = BundledSQLiteDriver()
        val connection = driver.open(dbFile.absolutePath)
        try {
            connection.execSQL("PRAGMA user_version = 16")
            connection.execSQL(
                """
                CREATE TABLE `course_tables` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `isCouple` INTEGER NOT NULL DEFAULT 0,
                    `pairedCourseTableId` TEXT DEFAULT NULL,
                    PRIMARY KEY(`id`)
                )
                """
            )
            connection.execSQL(
                "INSERT INTO course_tables (id, name, createdAt, isCouple, pairedCourseTableId) " +
                    "VALUES ('t1', '2026 秋', 1700000000000, 0, NULL)"
            )

            MIGRATION_16_17.migrate(connection)

            val courseCount = queryLong(connection, "SELECT COUNT(*) FROM course_tables")
            assertEquals(1L, courseCount, "迁移不得删除或重建既有课程数据")
            assertEquals(
                "2026 秋",
                queryText(connection, "SELECT name FROM course_tables WHERE id = 't1'")
            )

            listOf(
                "diary_entries",
                "mood_records",
                "diary_tags",
                "diary_tag_cross_ref",
                "mood_tag_cross_ref",
                "diary_attachments"
            ).forEach { table ->
                assertTrue(tableExists(connection, table), "迁移后缺少表：$table")
            }

            assertTrue(
                indexExists(connection, "index_mood_records_diaryId"),
                "一篇日记最多一条关联心情的唯一索引缺失"
            )
            assertTrue(
                indexExists(connection, "index_diary_tags_normalizedName"),
                "标签规范化名称的唯一索引缺失"
            )
        } finally {
            connection.close()
        }
    }

    private fun tableExists(
        connection: androidx.sqlite.SQLiteConnection,
        name: String
    ): Boolean = connection.prepare(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1"
    ).use { statement ->
        statement.bindText(1, name)
        statement.step()
    }

    private fun indexExists(
        connection: androidx.sqlite.SQLiteConnection,
        name: String
    ): Boolean = connection.prepare(
        "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ? LIMIT 1"
    ).use { statement ->
        statement.bindText(1, name)
        statement.step()
    }

    private fun queryLong(connection: androidx.sqlite.SQLiteConnection, sql: String): Long =
        connection.prepare(sql).use { statement ->
            check(statement.step())
            statement.getLong(0)
        }

    private fun queryText(connection: androidx.sqlite.SQLiteConnection, sql: String): String =
        connection.prepare(sql).use { statement ->
            check(statement.step())
            statement.getText(0)
        }
}
