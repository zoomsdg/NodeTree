package com.example.nodechain.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nodechain.data.AnswerOption
import com.example.nodechain.data.ChainStore
import com.example.nodechain.data.ExplanationBlock
import com.example.nodechain.data.NodeChain
import com.example.nodechain.data.Outcome
import com.example.nodechain.data.QuestionNode
import com.example.nodechain.data.ResultNode
import com.example.nodechain.data.RunStep
import com.example.nodechain.data.TestRun
import com.example.nodechain.data.defaultResultTitle
import com.example.nodechain.data.nodeById
import com.example.nodechain.ui.common.EmptyState
import com.example.nodechain.ui.common.ExplanationSection
import com.example.nodechain.ui.theme.outcomeColors
import kotlinx.serialization.json.Json

private val stringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

private val notesJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/**
 * 测试级批注按节点 id 存。转屏之类的重建不能把已经写的东西弄丢，
 * 所以序列化成 JSON 交给 rememberSaveable。
 */
private val notesSaver = Saver<SnapshotStateMap<String, List<ExplanationBlock>>, String>(
    save = { runCatching { notesJson.encodeToString(it.toMap()) }.getOrDefault("{}") },
    restore = { text ->
        mutableStateMapOf<String, List<ExplanationBlock>>().apply {
            runCatching {
                putAll(notesJson.decodeFromString<Map<String, List<ExplanationBlock>>>(text))
            }
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(chainId: String, onExit: () -> Unit) {
    val chains by ChainStore.chains.collectAsStateWithLifecycle()
    val chain = chains.firstOrNull { it.id == chainId }

    // 走过的节点轨迹与对应的作答，用来支持"上一题"和生成历史记录
    val trail = rememberSaveable(chainId, saver = stringListSaver) { mutableStateListOf() }
    val answers = rememberSaveable(chainId, saver = stringListSaver) { mutableStateListOf() }
    // 本次测试的批注：节点 id -> 批注列表。回退不清空，走回同一题内容还在。
    val notes = rememberSaveable(chainId, saver = notesSaver) { mutableStateMapOf() }

    if (chain == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    LaunchedEffect(chainId, chain.startNodeId) {
        if (trail.isEmpty()) chain.startNodeId?.let { trail.add(it) }
    }

    val currentId = trail.lastOrNull()
    val current = chain.nodeById(currentId)

    fun restart() {
        trail.clear()
        answers.clear()
        notes.clear()
        chain.startNodeId?.let { trail.add(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chain.title.ifBlank { "测试" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.Close, contentDescription = "退出测试")
                    }
                },
                actions = {
                    IconButton(onClick = { restart() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新开始")
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (current) {
                null -> EmptyState(
                    title = "无法开始",
                    description = "这条链还没有指定起始节点，回去编辑一下就能跑了。",
                    modifier = Modifier.align(Alignment.Center),
                )

                is QuestionNode -> QuestionView(
                    node = current,
                    chain = chain,
                    stepIndex = answers.size,
                    canGoBack = trail.size > 1,
                    notes = notes[current.id].orEmpty(),
                    onAddNote = {
                        notes[current.id] = notes[current.id].orEmpty() +
                            ExplanationBlock(ChainStore.newId())
                    },
                    onChangeNote = { id, title, body ->
                        notes[current.id] = notes[current.id].orEmpty().map {
                            if (it.id == id) it.copy(title = title, body = body) else it
                        }
                    },
                    onDeleteNote = { id ->
                        notes[current.id] = notes[current.id].orEmpty().filterNot { it.id == id }
                    },
                    onBack = {
                        trail.removeAt(trail.lastIndex)
                        if (answers.isNotEmpty()) answers.removeAt(answers.lastIndex)
                    },
                    onAnswer = { option ->
                        val next = option.nextNodeId
                        if (next != null && chain.nodeById(next) != null) {
                            answers.add(option.text.ifBlank { "未命名答案" })
                            trail.add(next)
                        }
                    },
                )

                is ResultNode -> ResultView(
                    node = current,
                    chain = chain,
                    trail = trail,
                    answers = answers,
                    notes = notes,
                    onRestart = { restart() },
                    onExit = onExit,
                )
            }
        }
    }
}

@Composable
private fun QuestionView(
    node: QuestionNode,
    chain: NodeChain,
    stepIndex: Int,
    canGoBack: Boolean,
    notes: List<ExplanationBlock>,
    onAddNote: () -> Unit,
    onChangeNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onBack: () -> Unit,
    onAnswer: (AnswerOption) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { ((stepIndex + 1).toFloat() / (stepIndex + 2)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "第 ${stepIndex + 1} 题",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = node.question.ifBlank { "（这个问题还没填题干）" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(28.dp))

            if (node.options.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = "这个问题还没有配置答案，测试走不下去了。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                node.options.forEach { option ->
                    val broken = option.nextNodeId == null || chain.nodeById(option.nextNodeId) == null
                    AnswerButton(
                        text = option.text.ifBlank { "未命名答案" },
                        broken = broken,
                        onClick = { onAnswer(option) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 节点级：链里配好的，测试时只读
            if (node.explanations.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                SectionLabel("说明", "这条链自带的，所有人测试时都一样")
                Spacer(Modifier.height(8.dp))
                ExplanationSection(blocks = node.explanations, editable = false)
            }

            // 测试级：只属于这一次测试，改它不会动到节点
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            RunNoteSection(
                notes = notes,
                onAdd = onAddNote,
                onChange = onChangeNote,
                onDelete = onDeleteNote,
            )
            Spacer(Modifier.height(24.dp))
        }

        if (canGoBack) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    TextButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("上一题")
                    }
                }
            }
        }
    }
}

/** 测试级批注区。刻意跟"说明"分开标题和配色，免得跟节点自带的搞混。 */
@Composable
private fun RunNoteSection(
    notes: List<ExplanationBlock>,
    onAdd: () -> Unit,
    onChange: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column {
        SectionLabel("本次批注", "只存在这一次测试记录里，不会改动节点本身")
        Spacer(Modifier.height(8.dp))
        ExplanationSection(
            blocks = notes,
            editable = true,
            addLabel = "添加批注",
            onChange = onChange,
            onDelete = onDelete,
            onAdd = onAdd,
        )
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnswerButton(text: String, broken: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !broken,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        Text(
            text = if (broken) "$text（分支未配置）" else text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultView(
    node: ResultNode,
    chain: NodeChain,
    trail: List<String>,
    answers: List<String>,
    notes: Map<String, List<ExplanationBlock>>,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = outcomeColors()
    val history by ChainStore.history.collectAsStateWithLifecycle()
    var runId by rememberSaveable { mutableStateOf<String?>(null) }

    val (accent, container, mark) = when (node.outcome) {
        Outcome.PASS -> Triple(colors.pass, colors.passContainer, "✓")
        Outcome.FAIL -> Triple(colors.fail, colors.failContainer, "✕")
        Outcome.NEUTRAL -> Triple(colors.neutral, colors.neutralContainer, "•")
    }

    // 到达结果就落一条历史。只在实际走过的路径上带批注，
    // 中途被换掉的那条分支上写的东西不进记录。
    LaunchedEffect(node.id, trail.size) {
        if (runId != null) return@LaunchedEffect
        val steps = answers.mapIndexed { index, answer ->
            val nodeId = trail.getOrNull(index).orEmpty()
            val q = chain.nodeById(nodeId) as? QuestionNode
            RunStep(
                question = q?.question?.ifBlank { "未命名问题" } ?: "未命名问题",
                answer = answer,
                nodeId = nodeId,
                notes = notes[nodeId].orEmpty(),
            )
        }
        val id = ChainStore.newId()
        ChainStore.recordRun(
            TestRun(
                id = id,
                chainId = chain.id,
                chainTitle = chain.title.ifBlank { "未命名节点链" },
                finishedAt = System.currentTimeMillis(),
                steps = steps,
                outcome = node.outcome,
                resultTitle = node.title.ifBlank { defaultResultTitle(node.outcome) },
                resultNotes = notes[node.id].orEmpty(),
            )
        )
        runId = id
    }

    val saved = history.firstOrNull { it.id == runId }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Surface(color = container, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = mark, style = MaterialTheme.typography.displaySmall, color = accent)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = node.title.ifBlank { defaultResultTitle(node.outcome) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (node.explanations.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                ExplanationSection(blocks = node.explanations, editable = false)
            }

            // 结果页的批注直接写进已经落好的那条记录，避免两份数据打架
            if (saved != null) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                RunNoteSection(
                    notes = saved.resultNotes,
                    onAdd = { ChainStore.addRunNote(saved.id, -1) },
                    onChange = { id, t, b -> ChainStore.updateRunNote(saved.id, -1, id, t, b) },
                    onDelete = { id -> ChainStore.deleteRunNote(saved.id, -1, id) },
                )
            }

            if (answers.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "作答回顾",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                answers.forEachIndexed { index, answer ->
                    val nodeId = trail.getOrNull(index).orEmpty()
                    val q = chain.nodeById(nodeId) as? QuestionNode
                    val stepNotes = notes[nodeId].orEmpty()
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = "${index + 1}. ${q?.question?.ifBlank { "未命名问题" } ?: "未命名问题"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = answer,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        // 直接把这一题的批注列出来，不再只报个条数。
                        // 这里是只读的：批注已经随本次测试落进记录了，
                        // 要改去"测试历史"改，免得两份数据对不上。
                        if (stepNotes.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            ExplanationSection(
                                blocks = stepNotes,
                                editable = false,
                                compact = true,
                            )
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("再测一次")
                }
                Button(onClick = onExit, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("完成")
                }
            }
        }
    }
}
