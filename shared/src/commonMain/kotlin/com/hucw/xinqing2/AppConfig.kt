package com.hucw.xinqing2

import com.hucw.xinqing2.data.security.CredentialStore

data class AppConfig(
    val databasePath: String,
    val credentialStore: CredentialStore,
)
