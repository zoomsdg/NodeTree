package com.example.nodechain.ui.editor

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nodechain.data.ChainIssue
import com.example.nodechain.data.ChainStore
import com.example.nodechain.data.NodeChain
import com.example.nodechain.data.QuestionNode
import com.example.nodechain.data.ResultNode
import com.example.nodechain.data.displayLabel
import com.example.nodechain.data.isRunnable
import com.example.nodechain.data.issues
import com.example.nodechain.data.nodeById
import com.example.nodechain.ui.common.AutoSaveTextField
import com.example.nodechain.ui.common.ConfirmDialog
import com.example.nodechain.ui.common.ExpandArrow
import com.example.nodechain.ui.common.EmptyState
import com.example.nodechain.ui.common.OutcomeBadge
import com.example.nodechain.ui.common.TypeChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainEditorScreen(
    chainId: String,
    onBack: () -> Unit,
    onEditNode: (String) -> Unit,
    onRun: () -> Unit,
) {
    val chains by ChainStore.chains.collectAsStateWithLifecycle()
    val chain = chains.firstOrNull { it.id == chainId }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    if (chain == null) {
        // 链被删掉了（比如在别处删除后返回），直接退出，不要停在空白页
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chain.title.ifBlank { "未命名节点链" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRun, enabled = chain.isRunnable()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "试运行")
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
                        onClick = { onEditNode(ChainStore.addQuestionNode(chainId)) },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("问题节点")
                    }
                    OutlinedButton(
                        onClick = { onEditNode(ChainStore.addResultNode(chainId)) },
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("结果节点")
                    }
                }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("节点") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("链路预览") })
            }
            when (tab) {
                0 -> NodeListTab(chain, onEditNode)
                else -> ChainPreview(
                    chain = chain,
                    onNodeClick = onEditNode,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun NodeListTab(chain: NodeChain, onEditNode: (String) -> Unit) {
    val problems = remember(chain) { chain.issues() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AutoSaveTextField(
                        initial = chain.title,
                        identity = chain.id + ":title",
                        onChange = { ChainStore.updateChainMeta(chain.id, it, chain.description) },
                        label = "节点链名称",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AutoSaveTextField(
                        initial = chain.description,
                        identity = chain.id + ":desc",
                        onChange = { ChainStore.updateChainMeta(chain.id, chain.title, it) },
                        label = "说明（可选）",
                        placeholder = "这条链是用来测什么的",
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StartNodePicker(chain)
                }
            }
        }

        if (problems.isNotEmpty()) {
            item { IssueCard(problems, onEditNode) }
        }

        if (chain.nodes.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有节点",
                    description = "从底部添加第一个问题节点，填好题干和答案，" +
                        "再给每个答案指定下一个节点，链路就连起来了。",
                )
            }
        } else {
            item {
                Text(
                    text = "节点（${chain.nodes.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(chain.nodes, key = { it.id }) { node ->
                NodeCard(
                    chain = chain,
                    nodeId = node.id,
                    onClick = { onEditNode(node.id) },
                )
            }
        }
    }
}

@Composable
private fun StartNodePicker(chain: NodeChain) {
    var open by remember { mutableStateOf(false) }
    val current = chain.nodeById(chain.startNodeId)
    Column {
        Text(
            text = "起始节点",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = chain.nodes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = current?.displayLabel() ?: "未指定 —— 测试无法开始",
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                chain.nodes.forEach { node ->
                    DropdownMenuItem(
                        text = { Text(node.displayLabel(), maxLines = 1) },
                        trailingIcon = {
                            if (node.id == chain.startNodeId) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = { open = false; ChainStore.setStartNode(chain.id, node.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issues: List<ChainIssue>, onNodeClick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) issues else issues.take(5)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "还有 ${issues.size} 处没配置完",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // 超过 5 条才需要折叠，箭头跟在标题后面，不另起一行
                if (issues.size > 5) {
                    ExpandArrow(
                        expanded = expanded,
                        onToggle = { expanded = !expanded },
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            shown.forEach { issue ->
                val target = issue.nodeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (target != null) Modifier.clickable { onNodeClick(target) }
                            else Modifier
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = issue.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // 同名节点靠文字分不清，所以给一个直接跳过去的入口
                    if (target != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "定位",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeCard(chain: NodeChain, nodeId: String, onClick: () -> Unit) {
    val node = chain.nodeById(nodeId) ?: return
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isStart = chain.startNodeId == nodeId

    Card(onClick = onClick) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (node) {
                    is QuestionNode -> TypeChip("问题")
                    is ResultNode -> OutcomeBadge(node.outcome)
                }
                if (isStart) {
                    Spacer(Modifier.width(6.dp))
                    TypeChip(
                        "起点",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "节点操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!isStart) {
                            DropdownMenuItem(
                                text = { Text("设为起点") },
                                onClick = { menu = false; ChainStore.setStartNode(chain.id, nodeId) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = { menu = false; onClick() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = node.displayLabel(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp),
            )

            if (node is QuestionNode) {
                Spacer(Modifier.height(8.dp))
                if (node.options.isEmpty()) {
                    Text(
                        text = "还没有答案",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    node.options.forEach { opt ->
                        val target = chain.nodeById(opt.nextNodeId)
                        Row(Modifier.padding(end = 12.dp, top = 2.dp)) {
                            Text(
                                text = opt.text.ifBlank { "未命名答案" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "  →  " + (target?.displayLabel() ?: "未连接"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (target == null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除节点",
            text = "指向这个节点的答案会变成未连接状态。",
            onConfirm = { ChainStore.deleteNode(chain.id, nodeId) },
            onDismiss = { confirmDelete = false },
        )
    }
}
