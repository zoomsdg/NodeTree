package com.example.nodechain.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nodechain.data.ChainStore
import com.example.nodechain.data.NodeChain
import com.example.nodechain.data.isRunnable
import com.example.nodechain.data.issues
import com.example.nodechain.ui.common.ConfirmDialog
import com.example.nodechain.ui.common.EmptyState
import com.example.nodechain.ui.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onHistory: () -> Unit,
    onEnvCheck: () -> Unit,
    onTransfer: () -> Unit,
) {
    val chains by ChainStore.chains.collectAsStateWithLifecycle()
    val loaded by ChainStore.loaded.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点链") },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.DateRange, contentDescription = "测试历史")
                    }
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text("导入与导出") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { overflow = false; onTransfer() },
                        )
                        DropdownMenuItem(
                            text = { Text("环境自检") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { overflow = false; onEnvCheck() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            // notes 里要求构建入口在底部，做成整条的主按钮，最好找
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("构建节点链", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { inner ->
        if (!loaded) {
            // 数据是异步从文件读的，读完之前什么都不画，
            // 免得空状态先闪一下再被列表顶掉
            Box(Modifier.fillMaxSize().padding(inner))
        } else if (chains.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(inner),
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    title = "还没有节点链",
                    description = "节点链是一串「问题 → 答案 → 下一个问题」的分支路径，" +
                        "跑完得到通过或未通过的结论。\n\n点下方的「构建节点链」开始搭第一条。",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(chains, key = { _, chain -> chain.id }) { index, chain ->
                    ChainCard(
                        chain = chain,
                        canMoveUp = index > 0,
                        canMoveDown = index < chains.lastIndex,
                        onRun = { onRun(chain.id) },
                        onEdit = { onEdit(chain.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateChainDialog(
            onDismiss = { showCreate = false },
            onCreate = { title ->
                showCreate = false
                onEdit(ChainStore.createChain(title))
            },
        )
    }
}

@Composable
private fun ChainCard(
    chain: NodeChain,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val problems = remember(chain) { chain.issues() }
    val runnable = chain.isRunnable()

    Card(
        onClick = if (runnable) onRun else onEdit,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = chain.title.ifBlank { "未命名节点链" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // 最近编辑日期跟在标题同一行，不额外占一行
                        if (chain.updatedAt > 0L) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = formatDate(chain.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (chain.description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = chain.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("复制一份") },
                            onClick = { menu = false; ChainStore.duplicateChain(chain.id) },
                        )
                        HorizontalDivider()
                        // 首页按列表顺序显示，所以上移下移就是改显示位置。
                        // 不是常用操作，排在常用项之后、删除之前。
                        DropdownMenuItem(
                            text = { Text("上移") },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                            enabled = canMoveUp,
                            onClick = { menu = false; ChainStore.moveChain(chain.id, -1) },
                        )
                        DropdownMenuItem(
                            text = { Text("下移") },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                            enabled = canMoveDown,
                            onClick = { menu = false; ChainStore.moveChain(chain.id, 1) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }

            if (problems.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (problems.size == 1) problems.first().message
                        else "还有 ${problems.size} 处没配置完",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // 编辑入口保留在右上角的 ⋮ 菜单里，这里只留主操作
            Button(
                onClick = onRun,
                enabled = runnable,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("开始测试")
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除节点链",
            text = "「${chain.title.ifBlank { "未命名节点链" }}」及其所有节点都会被删除，无法撤销。",
            onConfirm = { ChainStore.deleteChain(chain.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun CreateChainDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建节点链") },
        text = {
            Column {
                Text(
                    text = "先给这条链起个名字，下一步再往里加节点。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：新生入学评估") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim().ifBlank { "未命名节点链" }) },
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
