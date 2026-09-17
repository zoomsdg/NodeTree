package com.example.nodechain.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nodechain.data.ChainStore
import com.example.nodechain.data.LATE_NOTE_PREFIX
import com.example.nodechain.data.TestRun
import com.example.nodechain.ui.common.ConfirmDialog
import com.example.nodechain.ui.common.EmptyState
import com.example.nodechain.ui.common.ExpandArrow
import com.example.nodechain.ui.common.ExplanationSection
import com.example.nodechain.ui.common.OutcomeBadge
import com.example.nodechain.ui.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val history by ChainStore.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("测试历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "清空历史")
                        }
                    }
                },
            )
        },
    ) { inner ->
        if (history.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner),
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyState(
                    title = "还没有测试记录",
                    description = "跑完一条节点链之后，这里会留下作答路径和结论。",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(history, key = { it.id }) { run -> RunCard(run) }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空测试历史",
            text = "全部 ${history.size} 条记录都会被删除，无法撤销。",
            confirmLabel = "清空",
            onConfirm = { ChainStore.clearHistory() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun RunCard(run: TestRun) {
    var expanded by remember { mutableStateOf(false) }

    Card(onClick = { expanded = !expanded }) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = run.chainTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = run.resultTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutcomeBadge(run.outcome)
                IconButton(onClick = { ChainStore.deleteRun(run.id) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除这条记录",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(run.finishedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "答了 ${run.steps.size} 题",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (run.noteCount > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "✎ ${run.noteCount} 条批注",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                ExpandArrow(expanded = expanded, onToggle = { expanded = !expanded })
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp, end = 12.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "作答路径与结论是只读的；批注可以事后补写。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (run.steps.isEmpty()) {
                        Text(
                            text = "这次测试没有经过任何问题，起点直接是结果节点。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        run.steps.forEachIndexed { index, step ->
                            Column(Modifier.padding(bottom = 10.dp)) {
                                Text(
                                    text = "${index + 1}. ${step.question}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 点答案这一行就直接加一条事后批注，省掉"箭头 -> 按钮"两步
                                AnswerRow(
                                    answer = step.answer,
                                    onAddNote = {
                                        ChainStore.addRunNote(run.id, index, LATE_NOTE_PREFIX)
                                    },
                                )
                                ExplanationSection(
                                    blocks = step.notes,
                                    editable = true,
                                    compact = true,
                                    onChange = { id, t, b ->
                                        ChainStore.updateRunNote(run.id, index, id, t, b)
                                    },
                                    onDelete = { ChainStore.deleteRunNote(run.id, index, it) },
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    AnswerRow(
                        answer = "结论：${run.resultTitle}",
                        onAddNote = { ChainStore.addRunNote(run.id, -1, LATE_NOTE_PREFIX) },
                    )
                    ExplanationSection(
                        blocks = run.resultNotes,
                        editable = true,
                        compact = true,
                        onChange = { id, t, b -> ChainStore.updateRunNote(run.id, -1, id, t, b) },
                        onDelete = { ChainStore.deleteRunNote(run.id, -1, it) },
                    )
                }
            }
        }
    }
}

/**
 * 作答（或结论）那一行。整行可点，点了就加一条事后批注。
 * 右侧给一个轻量提示，否则"可以点"这件事没人能猜到。
 */
@Composable
private fun AnswerRow(answer: String, onAddNote: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onAddNote)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = answer,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "＋批注",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
