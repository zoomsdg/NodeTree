package com.example.nodechain.ui.about

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 一行环境信息。 */
data class EnvRow(val label: String, val value: String)

/**
 * 把 SDK 版本号翻成人能读的名字。
 * 纯函数，不依赖 Android 运行时，所以能在 JVM 单元测试里直接验证。
 */
fun sdkLabel(sdkInt: Int): String = when {
    sdkInt >= 37 -> "Android 17"
    sdkInt == 36 -> "Android 16"
    sdkInt == 35 -> "Android 15"
    sdkInt == 34 -> "Android 14"
    sdkInt == 33 -> "Android 13"
    sdkInt == 32 -> "Android 12L"
    sdkInt == 31 -> "Android 12"
    else -> "Android (API $sdkInt)"
}

fun collectEnvRows(): List<EnvRow> = listOf(
    EnvRow("系统版本", "${Build.VERSION.RELEASE}（${sdkLabel(Build.VERSION.SDK_INT)}）"),
    EnvRow("API Level", Build.VERSION.SDK_INT.toString()),
    EnvRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}"),
    EnvRow("主 ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"),
    EnvRow("指纹", Build.FINGERPRINT.take(40)),
    EnvRow("最低支持", "API 31 / Android 12"),
)

/**
 * 开发期的环境自检页，从首页右上角菜单进入。
 * 跑通这一屏说明编译、打包、安装、运行全链路没问题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvCheckScreen(onBack: () -> Unit) {
    val rows = remember { collectEnvRows() }
    var count by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("环境自检") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = "环境自检通过",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Kotlin + Compose + Gradle 构建链路正常",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = row.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = row.value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { count++ }) { Text("重组测试") }
                Text(
                    text = "  已点击 $count 次",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
