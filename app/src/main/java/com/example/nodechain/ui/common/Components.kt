package com.example.nodechain.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.nodechain.data.ExplanationBlock
import com.example.nodechain.data.Outcome
import com.example.nodechain.data.label
import com.example.nodechain.ui.theme.outcomeColors

/** 空列表时的占位，给一句说明和一个下一步动作。 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 通过 / 未通过 / 中性的小徽章。 */
@Composable
fun OutcomeBadge(outcome: Outcome, modifier: Modifier = Modifier) {
    val c = outcomeColors()
    val (fg, bg) = when (outcome) {
        Outcome.PASS -> c.pass to c.passContainer
        Outcome.FAIL -> c.fail to c.failContainer
        Outcome.NEUTRAL -> c.neutral to c.neutralContainer
    }
    val label = outcome.label()
    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 中性的类型标签，如"问题""结果""起点"。 */
@Composable
fun TypeChip(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(color = color, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 解释性文字区块。[editable] 决定是编辑态（可改、可删）还是测试时的只读态。
 * 每块独立折叠，展开状态记在这个 Composable 里，不进数据模型。
 */
@Composable
fun ExplanationSection(
    blocks: List<ExplanationBlock>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    addLabel: String = "添加解释",
    defaultExpanded: Boolean = editable,
    emptyHint: String? = null,
    onChange: (id: String, title: String, body: String) -> Unit = { _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // 新加的块自动展开——刚点完"添加"就得能直接打字，
    // 不然默认折叠的场景（历史记录里）会让人以为没加上。
    val seen = remember { mutableSetOf<String>() }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(blocks.map { it.id }) {
        val ids = blocks.map { it.id }
        if (!seeded) {
            seen.addAll(ids)
            seeded = true
        } else {
            ids.filterNot { it in seen }.forEach {
                seen.add(it)
                expanded[it] = true
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (blocks.isEmpty() && emptyHint != null) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        blocks.forEach { block ->
            val isOpen = expanded[block.id] ?: defaultExpanded
            ExplanationCard(
                block = block,
                expanded = isOpen,
                editable = editable,
                onToggle = { expanded[block.id] = !isOpen },
                onChange = { t, b -> onChange(block.id, t, b) },
                onDelete = { onDelete(block.id) },
            )
        }
        if (onAdd != null) {
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(addLabel)
            }
        }
    }
}

@Composable
private fun ExplanationCard(
    block: ExplanationBlock,
    expanded: Boolean,
    editable: Boolean,
    onToggle: () -> Unit,
    onChange: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "explanationArrow")
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (editable) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除这块说明",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.rotate(rotation).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    if (editable) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutoSaveTextField(
                                initial = block.title,
                                identity = block.id + ":title",
                                onChange = { onChange(it, block.body) },
                                label = "标题",
                                placeholder = "例如：判定依据",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            AutoSaveTextField(
                                initial = block.body,
                                identity = block.id + ":body",
                                onChange = { onChange(block.title, it) },
                                label = "内容",
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Text(
                            text = block.body.ifBlank { "（暂无内容）" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
