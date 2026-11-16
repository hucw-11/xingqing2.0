package com.shangkeschedule

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() {
    val dependencies = startDesktopDependencies()
    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = "心晴 2.0",
            ) {
                App()
            }
        }
    } finally {
        dependencies.close()
    }
}
