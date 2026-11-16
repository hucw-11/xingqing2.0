package com.hucw.xinqing2

import com.hucw.xinqing2.data.security.CredentialStore
import com.hucw.xinqing2.time.TimeProvider

data class AppConfig(
    val databasePath: String,
    val credentialStore: CredentialStore,
    val timeProvider: TimeProvider,
)
