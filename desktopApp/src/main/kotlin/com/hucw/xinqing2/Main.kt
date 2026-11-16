package com.hucw.xinqing2

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.hucw.xinqing2.data.security.WindowsCredentialStore
import com.hucw.xinqing2.time.DesktopTimeProvider
import java.io.File

fun main() {
    val dataDir = System.getenv("APPDATA")
        ?.let { File(it, "Xinqing2") }
        ?: File(System.getProperty("user.home"), ".xinqing2")
    dataDir.mkdirs()

    val config = AppConfig(
        databasePath = File(dataDir, "xinqing.db").absolutePath,
        credentialStore = WindowsCredentialStore(dataDir.toPath()),
        timeProvider = DesktopTimeProvider(),
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "心晴 2.0",
            state = rememberWindowState(width = 1200.dp, height = 800.dp),
        ) {
            App(config)
        }
    }
}
