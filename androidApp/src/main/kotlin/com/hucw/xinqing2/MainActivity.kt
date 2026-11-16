package com.hucw.xinqing2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hucw.xinqing2.data.security.AndroidCredentialStore
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataDir = File(filesDir, "xinqing").apply { mkdirs() }
        val config = AppConfig(
            databasePath = File(dataDir, "xinqing.db").absolutePath,
            credentialStore = AndroidCredentialStore(applicationContext),
        )

        setContent {
            App(config)
        }
    }
}
