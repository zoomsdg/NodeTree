package com.example.nodechain.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 结果的性质，决定结果页的配色与图标。 */
@Serializable
enum class Outcome { PASS, FAIL, NEUTRAL }

/** 一块可折叠的解释性文字。 */
@Serializable
data class ExplanationBlock(
    val id: String,
    val title: String = "",
    val body: String = "",
)

/**
 * 一个答案选项。[nextNodeId] 为空表示这条分支还没接上，
 * 测试时走到这里会提示"分支未配置"而不是崩掉。
 */
@Serializable
data class AnswerOption(
    val id: String,
    val text: String = "",
    val nextNodeId: String? = null,
)

@Serializable
sealed interface ChainNode {
    val id: String
    val explanations: List<ExplanationBlock>
}

/** 问题节点：一个问题 + 若干答案，每个答案指向下一个节点。 */
@Serializable
@SerialName("question")
data class QuestionNode(
    override val id: String,
    val question: String = "",
    val options: List<AnswerOption> = emptyList(),
    override val explanations: List<ExplanationBlock> = emptyList(),
) : ChainNode

/** 结果节点：链路的终点。 */
@Serializable
@SerialName("result")
data class ResultNode(
    override val id: String,
    val title: String = "",
    val outcome: Outcome = Outcome.PASS,
    override val explanations: List<ExplanationBlock> = emptyList(),
) : ChainNode

/** 一条节点链，即一份完整的测试。 */
@Serializable
data class NodeChain(
    val id: String,
    val title: String = "",
    val description: String = "",
    val startNodeId: String? = null,
    val nodes: List<ChainNode> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

// ---------- 测试记录 ----------

/**
 * 测试中的一步。
 *
 * [notes] 是**测试级**解释性文字（批注）：只属于这一次测试，
 * 跟节点自带的 [ChainNode.explanations]（节点级）互不影响，改这里不会动到链本身。
 * 新增的字段都给了默认值，所以旧的历史记录照样能读出来。
 */
@Serializable
data class RunStep(
    val question: String,
    val answer: String,
    val nodeId: String = "",
    val notes: List<ExplanationBlock> = emptyList(),
)

@Serializable
data class TestRun(
    val id: String,
    val chainId: String,
    val chainTitle: String,
    val finishedAt: Long,
    val steps: List<RunStep> = emptyList(),
    val outcome: Outcome = Outcome.NEUTRAL,
    val resultTitle: String = "",
    /** 结果页上写的测试级批注。 */
    val resultNotes: List<ExplanationBlock> = emptyList(),
) {
    val noteCount: Int get() = steps.sumOf { it.notes.size } + resultNotes.size
}

// ---------- 导入导出 ----------

const val EXPORT_FORMAT = "nodechain-export"

/**
 * 导出文件的内容。节点链和测试记录可以混装，只勾一种就只有一种。
 *
 * [format] / [version] 是给以后留的余地：格式变了能认出来并给出人话提示，
 * 而不是丢一个解析异常给用户。
 */
@Serializable
data class ExportBundle(
    // 故意不给默认值：有默认值的话，任何一个 JSON 对象都能解析成"合法的导出文件"，
    // 格式校验就形同虚设了。必填才能真正把不相干的文件挡在外面。
    val format: String,
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val chains: List<NodeChain> = emptyList(),
    val history: List<TestRun> = emptyList(),
)

/** 导入结果，用来给用户一句明确的反馈。 */
data class ImportResult(
    val chains: Int = 0,
    val runs: Int = 0,
    val renamed: List<String> = emptyList(),
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

// ---------- 查询与校验 ----------

fun NodeChain.nodeById(id: String?): ChainNode? =
    if (id == null) null else nodes.firstOrNull { it.id == id }

val NodeChain.questionCount: Int get() = nodes.count { it is QuestionNode }
val NodeChain.resultCount: Int get() = nodes.count { it is ResultNode }

/** 从起点出发能走到的所有节点 id。 */
fun NodeChain.reachableIds(): Set<String> {
    val start = startNodeId ?: return emptySet()
    val seen = linkedSetOf<String>()
    val stack = ArrayDeque<String>()
    stack.addLast(start)
    while (stack.isNotEmpty()) {
        val id = stack.removeLast()
        if (!seen.add(id)) continue
        val node = nodeById(id)
        if (node is QuestionNode) {
            node.options.forEach { opt -> opt.nextNodeId?.let { stack.addLast(it) } }
        }
    }
    return seen
}

/**
 * 链路配置上的问题。
 *
 * [nodeId] 让界面能直接跳到出问题的那个节点——同一条链里完全可能有两个同名节点
 * （比如两个都叫"未通过"的结果），光靠文字描述根本分不清是哪一个。
 */
sealed interface ChainIssue {
    val message: String
    val nodeId: String?

    data object NoStart : ChainIssue {
        override val message = "还没有指定起始节点"
        override val nodeId: String? = null
    }

    data object NoNodes : ChainIssue {
        override val message = "还没有添加任何节点"
        override val nodeId: String? = null
    }

    data class EmptyQuestion(override val nodeId: String) : ChainIssue {
        override val message = "有问题节点的题干是空的"
    }

    data class NoOptions(override val nodeId: String, val question: String) : ChainIssue {
        override val message = "「$question」还没有添加答案"
    }

    data class DanglingOption(
        override val nodeId: String,
        val question: String,
        val answer: String,
    ) : ChainIssue {
        override val message = "「$question」的答案「$answer」还没接到下一个节点"
    }

    data class Unreachable(override val nodeId: String, val label: String) : ChainIssue {
        override val message = "「$label」从起点走不到：没有任何答案指向它"
    }
}

fun NodeChain.issues(): List<ChainIssue> {
    if (nodes.isEmpty()) return listOf(ChainIssue.NoNodes)
    val list = mutableListOf<ChainIssue>()
    if (startNodeId == null || nodeById(startNodeId) == null) list += ChainIssue.NoStart

    nodes.filterIsInstance<QuestionNode>().forEach { node ->
        val label = node.question.ifBlank { "未命名问题" }
        if (node.question.isBlank()) list += ChainIssue.EmptyQuestion(node.id)
        if (node.options.isEmpty()) {
            list += ChainIssue.NoOptions(node.id, label)
        } else {
            node.options.forEach { opt ->
                if (opt.nextNodeId == null || nodeById(opt.nextNodeId) == null) {
                    list += ChainIssue.DanglingOption(node.id, label, opt.text.ifBlank { "未命名答案" })
                }
            }
        }
    }

    val reachable = reachableIds()
    if (startNodeId != null) {
        nodes.filter { it.id !in reachable }.forEach {
            list += ChainIssue.Unreachable(it.id, it.displayLabel())
        }
    }
    return list
}

/** 节点在列表、预览图里显示的短标签。 */
fun ChainNode.displayLabel(): String = when (this) {
    is QuestionNode -> question.ifBlank { "未命名问题" }
    is ResultNode -> title.ifBlank { defaultResultTitle(outcome) }
}

/** 结果性质的显示名。徽章、类型选择、连接菜单都用它，避免三处文案各写各的。 */
fun Outcome.label(): String = when (this) {
    Outcome.PASS -> "通过"
    Outcome.FAIL -> "未通过"
    Outcome.NEUTRAL -> "中性"
}

fun defaultResultTitle(outcome: Outcome): String = when (outcome) {
    Outcome.PASS -> "通过"
    Outcome.FAIL -> "未通过"
    Outcome.NEUTRAL -> "结束"
}

/** 这条链是否已经可以跑测试了。 */
fun NodeChain.isRunnable(): Boolean =
    startNodeId != null && nodeById(startNodeId) != null && nodes.isNotEmpty()
