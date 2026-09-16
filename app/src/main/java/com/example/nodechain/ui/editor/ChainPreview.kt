package com.example.nodechain.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nodechain.data.ChainNode
import com.example.nodechain.data.NodeChain
import com.example.nodechain.data.Outcome
import com.example.nodechain.data.QuestionNode
import com.example.nodechain.data.ResultNode
import com.example.nodechain.data.displayLabel
import com.example.nodechain.data.nodeById
import com.example.nodechain.data.reachableIds
import com.example.nodechain.ui.common.EmptyState
import com.example.nodechain.ui.theme.outcomeColors

private const val MAX_DEPTH = 40

/**
 * 只读的链路预览：从起点展开成一棵缩进树，一眼能看出分支结构和断头路。
 *
 * 链路允许成环（比如"回到上一题"），所以递归时带着当前路径做环检测，
 * 遇到已经在路径里的节点就画成"↩ 回到 …"并停下，不会栈溢出。
 */
@Composable
fun ChainPreview(
    chain: NodeChain,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = chain.nodeById(chain.startNodeId)
    if (start == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                title = "还没法预览",
                description = "先在「节点」页添加节点，并指定一个起始节点。",
            )
        }
        return
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "起点",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        NodeSubtree(
            chain = chain,
            node = start,
            path = listOf(start.id),
            depth = 0,
            onNodeClick = onNodeClick,
        )
        Spacer(Modifier.height(24.dp))

        val orphans = chain.nodes.filter { it.id !in chain.reachableIds() }
        if (orphans.isNotEmpty()) {
            Text(
                text = "从起点走不到的节点",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(6.dp))
            orphans.forEach { node ->
                Box(Modifier.padding(vertical = 3.dp)) {
                    NodeChip(node = node, onClick = { onNodeClick(node.id) })
                }
            }
        }
    }
}

@Composable
private fun NodeSubtree(
    chain: NodeChain,
    node: ChainNode,
    path: List<String>,
    depth: Int,
    onNodeClick: (String) -> Unit,
) {
    Column {
        NodeChip(node = node, onClick = { onNodeClick(node.id) })

        if (node is QuestionNode && depth < MAX_DEPTH) {
            Column(
                modifier = Modifier.padding(start = 10.dp),
            ) {
                node.options.forEach { option ->
                    Row(Modifier.padding(top = 6.dp)) {
                        // 竖向连接线
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = option.text.ifBlank { "未命名答案" },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            val target = chain.nodeById(option.nextNodeId)
                            when {
                                target == null -> DeadEndChip()
                                target.id in path -> LoopChip(target.displayLabel())
                                else -> NodeSubtree(
                                    chain = chain,
                                    node = target,
                                    path = path + target.id,
                                    depth = depth + 1,
                                    onNodeClick = onNodeClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeChip(node: ChainNode, onClick: () -> Unit) {
    val colors = outcomeColors()
    val (bg, fg) = when (node) {
        is QuestionNode -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer

        is ResultNode -> when (node.outcome) {
            Outcome.PASS -> colors.passContainer to colors.pass
            Outcome.FAIL -> colors.failContainer to colors.fail
            Outcome.NEUTRAL -> colors.neutralContainer to colors.neutral
        }
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (node is ResultNode) "◆" else "●",
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
            Text(
                text = node.displayLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DeadEndChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "⚠ 未连接",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun LoopChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "↩ 回到「$label」",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}
