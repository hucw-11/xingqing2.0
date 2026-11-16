package com.hucw.xinqing2.data.security

interface CredentialStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}
