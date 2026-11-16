package com.hucw.xinqing2.data.db

import androidx.sqlite.execSQL
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseTest {
    private fun tempDatabasePath(): String =
        Files.createTempFile("xinqing2-", ".db").toAbsolutePath().toString()

    @Test
    fun dataSurvivesCloseAndReopen() {
        val path = tempDatabasePath()

        XinqingDatabase.open(path).use { handle ->
            AppMetaRepository(handle.connection).put("probe", "value")
        }

        XinqingDatabase.open(path).use { handle ->
            assertEquals("value", AppMetaRepository(handle.connection).get("probe"))
        }
    }

    @Test
    fun migrationChainRunsInOrderAndPreservesData() {
        val path = tempDatabasePath()
        val connection = createDatabaseDriver().open(path)
        try {
            connection.execSQL("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
            connection.execSQL("INSERT INTO sample (id, name) VALUES (1, 'before')")
            connection.execSQL("PRAGMA user_version = 1")

            val migrations = listOf(
                Migration(1, 2) { conn ->
                    conn.execSQL("ALTER TABLE sample ADD COLUMN note TEXT")
                },
                Migration(2, 3) { conn ->
                    conn.execSQL("ALTER TABLE sample ADD COLUMN flag INTEGER NOT NULL DEFAULT 0")
                },
            )

            XinqingDatabase.migrate(connection, 1, 3, migrations)

            assertEquals(3, XinqingDatabase.readUserVersion(connection))

            val statement = connection.prepare("SELECT name, note, flag FROM sample WHERE id = 1")
            try {
                assertTrue(statement.step())
                assertEquals("before", statement.getText(0))
                assertTrue(statement.isNull(1))
                assertEquals(0L, statement.getLong(2))
            } finally {
                statement.close()
            }
        } finally {
            connection.close()
            Files.deleteIfExists(java.nio.file.Path.of(path))
        }
    }
}
