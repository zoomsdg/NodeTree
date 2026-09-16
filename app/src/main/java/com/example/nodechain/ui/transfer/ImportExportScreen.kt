package com.example.nodechain.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nodechain.data.ChainStore
import com.example.nodechain.data.ImportResult
import com.example.nodechain.data.questionCount
import com.example.nodechain.data.resultCount
import com.example.nodechain.ui.common.EmptyState
import com.example.nodechain.ui.common.OutcomeBadge
import com.example.nodechain.ui.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chains by ChainStore.chains.collectAsStateWithLifecycle()
    val history by ChainStore.history.collectAsStateWithLifecycle()

    val pickedChains = remember { mutableStateListOf<String>() }
    val pickedRuns = remember { mutableStateListOf<String>() }
    var pendingPayload by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var exportDone by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = pendingPayload
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(payload.toByteArray())
                    } ?: error("打不开目标文件")
                }.isSuccess
            }
            exportDone = if (ok) {
                "已导出 ${pickedChains.size} 条节点链、${pickedRuns.size} 条测试记录。"
            } else {
                "写入失败，可能是所选位置不可写，换个位置再试。"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            result = if (text.isNullOrBlank()) {
                ImportResult(error = "读不出这个文件的内容。")
            } else {
                ChainStore.importJson(text)
            }
        }
    }

    val totalPicked = pickedChains.size + pickedRuns.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入与导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("导入文件") }
                    Button(
                        onClick = {
                            pendingPayload = ChainStore.exportJson(
                                pickedChains.toSet(),
                                pickedRuns.toSet(),
                            )
                            exportLauncher.launch(defaultFileName())
                        },
                        enabled = totalPicked > 0,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(if (totalPicked > 0) "导出所选（$totalPicked）" else "导出所选")
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = "勾选要导出的内容，两类可以混在同一个文件里。导入的内容一律新建，不会覆盖现有数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            item {
                SectionHeader(
                    title = "节点链",
                    total = chains.size,
                    picked = pickedChains.size,
                    onToggleAll = {
                        if (pickedChains.size == chains.size) pickedChains.clear()
                        else {
                            pickedChains.clear()
                            pickedChains.addAll(chains.map { it.id })
                        }
                    },
                )
            }
            if (chains.isEmpty()) {
                item { HintRow("还没有节点链") }
            } else {
                items(chains, key = { "c" + it.id }) { chain ->
                    PickRow(
                        checked = chain.id in pickedChains,
                        onToggle = {
                            if (chain.id in pickedChains) pickedChains.remove(chain.id)
                            else pickedChains.add(chain.id)
                        },
                        title = chain.title.ifBlank { "未命名节点链" },
                        subtitle = "${chain.questionCount} 个问题 · ${chain.resultCount} 个结果 · ${formatTime(chain.updatedAt)}",
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(
                    title = "测试记录",
                    total = history.size,
                    picked = pickedRuns.size,
                    onToggleAll = {
                        if (pickedRuns.size == history.size) pickedRuns.clear()
                        else {
                            pickedRuns.clear()
                            pickedRuns.addAll(history.map { it.id })
                        }
                    },
                )
            }
            if (history.isEmpty()) {
                item { HintRow("还没有测试记录") }
            } else {
                items(history, key = { "r" + it.id }) { run ->
                    PickRow(
                        checked = run.id in pickedRuns,
                        onToggle = {
                            if (run.id in pickedRuns) pickedRuns.remove(run.id)
                            else pickedRuns.add(run.id)
                        },
                        title = run.resultTitle.ifBlank { "未命名结果" },
                        subtitle = "${run.chainTitle} · ${formatTime(run.finishedAt)}",
                        trailing = { OutcomeBadge(run.outcome) },
                    )
                }
            }

            if (chains.isEmpty() && history.isEmpty()) {
                item {
                    EmptyState(
                        title = "暂无可导出的内容",
                        description = "先建一条节点链，或者直接用下面的「导入文件」把别人给你的链导进来。",
                    )
                }
            }
        }
    }

    result?.let { r ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(if (r.ok) "导入完成" else "导入失败") },
            text = {
                Column {
                    if (r.ok) {
                        Text("新增 ${r.chains} 条节点链、${r.runs} 条测试记录。")
                        if (r.renamed.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "有 ${r.renamed.size} 条链重名，已自动编号：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            r.renamed.take(5).forEach {
                                Text("· $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text(r.error.orEmpty())
                    }
                }
            },
            confirmButton = { TextButton(onClick = { result = null }) { Text("知道了") } },
        )
    }

    exportDone?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportDone = null },
            title = { Text("导出") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { exportDone = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    total: Int,
    picked: Int,
    onToggleAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title（$total）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (total > 0) {
            TextButton(onClick = onToggleAll) {
                Text(if (picked == total) "全不选" else "全选")
            }
        }
    }
}

@Composable
private fun PickRow(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun HintRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
    )
}

private fun defaultFileName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())
    return "nodechain-$stamp.json"
}
