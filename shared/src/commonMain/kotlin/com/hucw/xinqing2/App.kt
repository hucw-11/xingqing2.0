package com.hucw.xinqing2

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hucw.xinqing2.data.db.AppMetaRepository
import com.hucw.xinqing2.data.db.XinqingDatabase
import com.hucw.xinqing2.data.security.CredentialStore
import com.hucw.xinqing2.ui.Destination
import com.hucw.xinqing2.ui.screens.PlaceholderScreen
import com.hucw.xinqing2.ui.screens.TodayScreen
import com.hucw.xinqing2.ui.theme.XinqingTheme

@Composable
fun App(config: AppConfig) {
    XinqingTheme {
        val openResult = remember(config.databasePath) {
            runCatching { XinqingDatabase.open(config.databasePath) }
        }
        val handle = openResult.getOrNull()
        if (handle == null) {
            DatabaseErrorScreen(openResult.exceptionOrNull())
        } else {
            DisposableEffect(handle) {
                onDispose { handle.close() }
            }
            val repository = remember(handle) { AppMetaRepository(handle.connection) }
            XinqingShell(repository, config.credentialStore)
        }
    }
}

@Composable
private fun DatabaseErrorScreen(error: Throwable?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("数据库打开失败", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = error?.message ?: "未知错误",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "P1 骨架不自动重建数据库；请检查数据目录权限后重试。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun XinqingShell(
    repository: AppMetaRepository,
    credentialStore: CredentialStore,
) {
    var destination by remember { mutableStateOf(Destination.TODAY) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp
        if (isWide) {
            Row(Modifier.fillMaxSize()) {
                DesktopNavigation(
                    selected = destination,
                    onSelect = { destination = it },
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    ScreenContent(destination, repository, credentialStore)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ScreenContent(destination, repository, credentialStore)
                }
                MobileNavigation(
                    selected = destination,
                    onSelect = { destination = it },
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    destination: Destination,
    repository: AppMetaRepository,
    credentialStore: CredentialStore,
) {
    when (destination) {
        Destination.TODAY -> TodayScreen(repository, credentialStore)
        else -> PlaceholderScreen(destination)
    }
}

@Composable
private fun DesktopNavigation(
    selected: Destination,
    onSelect: (Destination) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxHeight(),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "心晴 2.0",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
            )
            Destination.entries.forEach { destination ->
                TextButton(
                    onClick = { onSelect(destination) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = destination.label,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (destination == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileNavigation(
    selected: Destination,
    onSelect: (Destination) -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                TextButton(onClick = { onSelect(destination) }) {
                    Text(
                        text = destination.label,
                        color = if (destination == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
