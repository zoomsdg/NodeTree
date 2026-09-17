package com.example.nodechain.ui.editor

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nodechain.data.AnswerOption
import com.example.nodechain.data.ChainStore
import com.example.nodechain.data.NodeChain
import com.example.nodechain.data.Outcome
import com.example.nodechain.data.QuestionNode
import com.example.nodechain.data.ResultNode
import com.example.nodechain.data.displayLabel
import com.example.nodechain.data.label
import com.example.nodechain.data.nodeById
import com.example.nodechain.ui.common.AutoSaveTextField
import com.example.nodechain.ui.common.ConfirmDialog
import com.example.nodechain.ui.common.ExplanationSection
import com.example.nodechain.ui.common.OutcomeBadge
import com.example.nodechain.ui.common.TypeChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorScreen(
    chainId: String,
    nodeId: String,
    depth: Int,
    onBack: () -> Unit,
    onBackToChain: () -> Unit,
    onOpenNode: (String) -> Unit,
) {
    val chains by ChainStore.chains.collectAsStateWithLifecycle()
    val chain = chains.firstOrNull { it.id == chainId }
    val node = chain?.nodeById(nodeId)
    var confirmDelete by remember { mutableStateOf(false) }

    if (chain == null || node == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (node is ResultNode) "编辑结果节点" else "编辑问题节点") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除节点")
                    }
                },
            )
        },
        bottomBar = {
            // 顺着"新建节点并连接"往下建好几层之后，靠返回键要一层层退，
            // 所以这里直接给一个回到链编辑页的出口
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (depth > 0) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("上一节点")
                        }
                    }
                    Button(
                        onClick = onBackToChain,
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("返回节点链")
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (chain.startNodeId == nodeId) {
                item {
                    TypeChip(
                        "这是链路的起点",
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            when (node) {
                is QuestionNode -> {
                    item {
                        AutoSaveTextField(
                            initial = node.question,
                            identity = node.id + ":q",
                            onChange = { ChainStore.updateQuestion(chainId, nodeId, it) },
                            label = "问题",
                            placeholder = "例如：学生是否满 12 周岁？",
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { SectionTitle("答案选项", "选中某个答案后，测试会跳到它指定的下一个节点") }
                    items(node.options.size, key = { node.options[it].id }) { index ->
                        OptionCard(
                            chain = chain,
                            nodeId = nodeId,
                            option = node.options[index],
                            index = index,
                            onOpenNode = onOpenNode,
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { ChainStore.addOption(chainId, nodeId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("添加答案")
                        }
                    }
                }

                is ResultNode -> {
                    item {
                        AutoSaveTextField(
                            initial = node.title,
                            // identity 带上性质：切换性质时默认标题会被 store 改掉，
                            // 不重新取值的话输入框还显示旧标题，再打一个字就把改动顶回去了
                            identity = node.id + ":t:" + node.outcome.name,
                            onChange = { ChainStore.updateResult(chainId, nodeId, it, node.outcome) },
                            label = "结果标题",
                            placeholder = "例如：合格，可以入学",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { SectionTitle("结果性质", "决定结果页的配色，也用于历史记录的统计") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Outcome.entries.forEach { outcome ->
                                OutcomeChip(outcome, node.outcome) {
                                    ChainStore.updateResult(chainId, nodeId, node.title, it)
                                }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item { SectionTitle("解释性文字", "测试时显示在答案下方，默认显示前 3 行，超出部分点「展开」看全文") }
            item {
                ExplanationSection(
                    blocks = node.explanations,
                    editable = true,
                    onChange = { id, t, b -> ChainStore.updateExplanation(chainId, nodeId, id, t, b) },
                    onDelete = { ChainStore.deleteExplanation(chainId, nodeId, it) },
                    onAdd = { ChainStore.addExplanation(chainId, nodeId) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除节点",
            text = "指向这个节点的答案会变成未连接状态。",
            onConfirm = { ChainStore.deleteNode(chainId, nodeId); onBack() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 下拉菜单里的分组小标题，不可点。 */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun OutcomeChip(
    value: Outcome,
    selected: Outcome,
    onSelect: (Outcome) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(value.label()) },
    )
}

@Composable
private fun OptionCard(
    chain: NodeChain,
    nodeId: String,
    option: AnswerOption,
    index: Int,
    onOpenNode: (String) -> Unit,
) {
    val target = chain.nodeById(option.nextNodeId)
    var menu by remember { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "答案 ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { ChainStore.deleteOption(chain.id, nodeId, option.id) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除这个答案",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AutoSaveTextField(
                initial = option.text,
                identity = option.id + ":text",
                onChange = { ChainStore.updateOptionText(chain.id, nodeId, option.id, it) },
                label = "答案内容",
                placeholder = "例如：是",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text(
                    text = "选了它之后 →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(
                        onClick = { menu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = target?.displayLabel() ?: "未连接 —— 点这里选下一个节点",
                            maxLines = 1,
                            color = if (target == null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        // 问题节点和结果节点分组列出：混在一起时两者长得一模一样，
                        // 光看文字分不出点过去是继续提问还是直接出结论
                        val candidates = chain.nodes.filter { it.id != nodeId }
                        val questions = candidates.filterIsInstance<QuestionNode>()
                        val results = candidates.filterIsInstance<ResultNode>()

                        // 第一组不画分隔线，否则菜单会以一条横线开头
                        if (questions.isNotEmpty()) {
                            MenuSectionLabel("接到已有问题节点")
                            questions.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.displayLabel(), maxLines = 1) },
                                    onClick = {
                                        menu = false
                                        ChainStore.setOptionTarget(chain.id, nodeId, option.id, candidate.id)
                                    },
                                )
                            }
                        }

                        if (results.isNotEmpty()) {
                            if (questions.isNotEmpty()) HorizontalDivider()
                            MenuSectionLabel("接到已有结果节点")
                            results.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.displayLabel(), maxLines = 1) },
                                    trailingIcon = { OutcomeBadge(candidate.outcome) },
                                    onClick = {
                                        menu = false
                                        ChainStore.setOptionTarget(chain.id, nodeId, option.id, candidate.id)
                                    },
                                )
                            }
                        }
                        if (option.nextNodeId != null) {
                            if (candidates.isNotEmpty()) HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("断开连接") },
                                onClick = {
                                    menu = false
                                    ChainStore.setOptionTarget(chain.id, nodeId, option.id, null)
                                },
                            )
                        }

                        // 新建放在最后：链搭到一半时，多数情况是接到已经建好的节点上，
                        // 让已有节点先露出来，不被这四项顶下去
                        if (candidates.isNotEmpty() || option.nextNodeId != null) HorizontalDivider()
                        MenuSectionLabel("新建并连接")
                        DropdownMenuItem(
                            text = { Text("新建问题节点并连接") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                menu = false
                                onOpenNode(ChainStore.createAndLink(chain.id, nodeId, option.id, null))
                            },
                        )
                        Outcome.entries.forEach { outcome ->
                            DropdownMenuItem(
                                text = { Text("新建「${outcome.label()}」结果并连接") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = {
                                    menu = false
                                    ChainStore.createAndLink(chain.id, nodeId, option.id, outcome)
                                },
                            )
                        }
                    }
                }
                if (target != null) {
                    TextButton(onClick = { onOpenNode(target.id) }) { Text("打开这个节点") }
                }
            }
        }
    }
}
