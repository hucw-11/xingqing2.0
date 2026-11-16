package com.hucw.xinqing2.data.db

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun createDatabaseDriver(): SQLiteDriver = BundledSQLiteDriver()
