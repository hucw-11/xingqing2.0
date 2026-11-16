package com.hucw.xinqing2.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hucw.xinqing2.data.db.AppMetaRepository
import com.hucw.xinqing2.data.db.XinqingSchema
import com.hucw.xinqing2.data.security.CredentialStore

private const val SELF_CHECK_KEY = "p1_self_check"
private const val CREDENTIAL_PROBE_KEY = "p1_credential_probe"

@Composable
fun SettingsScreen(
    repository: AppMetaRepository,
    credentialStore: CredentialStore,
) {
    var selfCheckCount by remember { mutableStateOf(repository.getInt(SELF_CHECK_KEY)) }
    var credentialResult by remember { mutableStateOf("尚未测试") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "P1 自检与凭据存储",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("数据持久化", style = MaterialTheme.typography.titleMedium)
                Text("数据库 schema 版本：${XinqingSchema.VERSION}")
                Text("自检记录：$selfCheckCount")
                Button(onClick = { selfCheckCount = repository.incrementInt(SELF_CHECK_KEY) }) {
                    Text("写入一条自检记录")
                }
                Text(
                    text = "关闭并重新打开应用后，数字应保持不变。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("安全凭据存储", style = MaterialTheme.typography.titleMedium)
                Text("当前结果：$credentialResult")
                Button(
                    onClick = {
                        val probe = "p1-probe-$selfCheckCount"
                        val result = runCatching {
                            credentialStore.put(CREDENTIAL_PROBE_KEY, probe)
                            credentialStore.get(CREDENTIAL_PROBE_KEY)
                        }
                        credentialResult = result.fold(
                            onSuccess = { readBack ->
                                if (readBack == probe) "读回成功" else "读回内容不一致"
                            },
                            onFailure = { error -> "失败：${error.message ?: "未知错误"}" },
                        )
                    }
                ) {
                    Text("写入并读回测试值")
                }
                Text(
                    text = "安卓使用 Keystore，Windows 使用 DPAPI；测试值只留在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("后续设置", style = MaterialTheme.typography.titleMedium)
                Text("课程表、提醒、AI、同步与导出设置将在后续阶段加入。")
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
