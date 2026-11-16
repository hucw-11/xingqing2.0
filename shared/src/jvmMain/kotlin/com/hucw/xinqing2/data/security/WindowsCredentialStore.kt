package com.hucw.xinqing2.data.security

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

class WindowsCredentialStore(private val dataDir: Path) : CredentialStore {
    private val file = dataDir.resolve("credentials.dat")
    private val lock = Any()

    init {
        require(System.getProperty("os.name").lowercase().contains("win")) {
            "桌面凭据存储当前仅支持 Windows（DPAPI）"
        }
        Files.createDirectories(dataDir)
    }

    override fun put(key: String, value: String) {
        synchronized(lock) {
            val entries = readEntries().toMutableMap()
            val encrypted = Crypt32Util.cryptProtectData(value.toByteArray(Charsets.UTF_8))
            entries[encodeKey(key)] = Base64.getEncoder().encodeToString(encrypted)
            writeEntries(entries)
        }
    }

    override fun get(key: String): String? = synchronized(lock) {
        val encoded = readEntries()[encodeKey(key)]
        if (encoded == null) {
            null
        } else {
            val decrypted = Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(encoded))
            String(decrypted, Charsets.UTF_8)
        }
    }

    override fun remove(key: String) {
        synchronized(lock) {
            val entries = readEntries().toMutableMap()
            if (entries.remove(encodeKey(key)) != null) {
                writeEntries(entries)
            }
        }
    }

    private fun encodeKey(key: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray(Charsets.UTF_8))

    private fun readEntries(): Map<String, String> {
        if (!Files.exists(file)) return emptyMap()
        return Files.readAllLines(file, Charsets.UTF_8)
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
    }

    private fun writeEntries(entries: Map<String, String>) {
        val content = entries.entries.joinToString("\n") { "${it.key}=${it.value}" }
        val temporary = dataDir.resolve("credentials.dat.tmp")
        Files.write(temporary, content.toByteArray(Charsets.UTF_8))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
    }
}
