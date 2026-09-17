package com.example.nodechain.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * 解释性文字 / 批注的展示与编辑。
 *
 * 展示态默认只显示前 3 行，超出才给"展开"。一道题下面挂好几段说明时，
 * 不截断的话答案按钮会被挤到屏幕外面去。
 *
 * 新建的块不写标题，直接写正文。[ExplanationBlock.title] 只为读得懂旧数据而保留：
 * 已经有标题的照常显示、可编辑，但新建的不会再产生标题。
 *
 * [collapsibleAdd] 为 true 时，"添加"入口先收成一个下箭头，点开才露出来。
 */
@Composable
fun ExplanationSection(
    blocks: List<ExplanationBlock>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    addLabel: String = "添加解释",
    emptyHint: String? = null,
    collapsibleAdd: Boolean = false,
    onChange: (id: String, title: String, body: String) -> Unit = { _, _, _ -> },
    onDelete: (id: String) -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    // 新加的块直接进编辑态：点完"添加"就得能打字，否则会以为没加上
    val editingIds = remember { mutableStateListOf<String>() }
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
                editingIds.add(it)
            }
        }
    }

    var addOpen by remember { mutableStateOf(!collapsibleAdd) }
    val addArrow by animateFloatAsState(if (addOpen) 180f else 0f, label = "addArrow")

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (blocks.isEmpty() && emptyHint != null && addOpen) {
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        blocks.forEach { block ->
            ExplanationCard(
                block = block,
                editable = editable,
                editing = block.id in editingIds,
                onStartEdit = { editingIds.add(block.id) },
                onDoneEdit = { editingIds.remove(block.id) },
                onChange = { t, b -> onChange(block.id, t, b) },
                onDelete = {
                    editingIds.remove(block.id)
                    onDelete(block.id)
                },
            )
        }

        if (onAdd != null) {
            if (collapsibleAdd) {
                IconButton(onClick = { addOpen = !addOpen }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (addOpen) "收起批注操作" else "展开批注操作",
                        modifier = Modifier.rotate(addArrow).size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            AnimatedVisibility(visible = addOpen) {
                TextButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(addLabel)
                }
            }
        }
    }
}

private const val COLLAPSED_LINES = 3

@Composable
private fun ExplanationCard(
    block: ExplanationBlock,
    editable: Boolean,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onDoneEdit: () -> Unit,
    onChange: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(block.id) { mutableStateOf(false) }
    var overflowing by remember(block.id) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            if (editing) {
                // 只有旧数据才可能带标题，新建的不给标题框
                if (block.title.isNotBlank()) {
                    AutoSaveTextField(
                        initial = block.title,
                        identity = block.id + ":title",
                        onChange = { onChange(it, block.body) },
                        label = "标题",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                AutoSaveTextField(
                    initial = block.body,
                    identity = block.id + ":body",
                    onChange = { onChange(block.title, it) },
                    label = "内容",
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDoneEdit) { Text("完成") }
                }
            } else {
                if (block.title.isNotBlank()) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = block.body.ifBlank { "（还没有内容）" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (block.body.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
                )
                if (overflowing || expanded || editable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (overflowing || expanded) {
                            TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "收起" else "展开")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (editable) {
                            TextButton(onClick = onStartEdit) { Text("编辑") }
                        }
                    }
                }
            }
        }
    }
}
