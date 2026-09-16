package com.example.nodechain.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 全部数据放两个 JSON 文件里。节点链本质是个小文档（通常几 KB），
 * 用文档式存储比关系表更贴合，也省掉了 Room + KSP 那一层构建复杂度。
 *
 * 编辑是即时生效的：内存里的 StateFlow 立刻更新，落盘做防抖，
 * 所以界面上不需要"保存"按钮，也不会因为中途退出丢东西。
 */
object ChainStore {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private lateinit var chainsFile: File
    private lateinit var historyFile: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _chains = MutableStateFlow<List<NodeChain>>(emptyList())
    val chains: StateFlow<List<NodeChain>> = _chains.asStateFlow()

    private val _history = MutableStateFlow<List<TestRun>>(emptyList())
    val history: StateFlow<List<TestRun>> = _history.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var saveChainsJob: Job? = null
    private var saveHistoryJob: Job? = null

    fun init(context: Context) {
        if (::chainsFile.isInitialized) return
        val dir = context.filesDir
        chainsFile = File(dir, "chains.json")
        historyFile = File(dir, "history.json")
        scope.launch {
            _chains.value = readList(chainsFile)
            _history.value = readList(historyFile)
            _loaded.value = true
        }
    }

    private inline fun <reified T> readList(file: File): List<T> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString<List<T>>(file.readText())
    }.getOrDefault(emptyList())

    private fun persistChains() {
        saveChainsJob?.cancel()
        val snapshot = _chains.value
        saveChainsJob = scope.launch {
            delay(300)
            runCatching { chainsFile.writeText(json.encodeToString(snapshot)) }
        }
    }

    private fun persistHistory() {
        saveHistoryJob?.cancel()
        val snapshot = _history.value
        saveHistoryJob = scope.launch {
            delay(300)
            runCatching { historyFile.writeText(json.encodeToString(snapshot)) }
        }
    }

    // ---------- 节点链 ----------

    fun chain(id: String): NodeChain? = _chains.value.firstOrNull { it.id == id }

    fun createChain(title: String, description: String = ""): String {
        val now = System.currentTimeMillis()
        val chain = NodeChain(
            id = newId(),
            title = title,
            description = description,
            createdAt = now,
            updatedAt = now,
        )
        _chains.value = _chains.value + chain
        persistChains()
        return chain.id
    }

    fun deleteChain(id: String) {
        _chains.value = _chains.value.filterNot { it.id == id }
        persistChains()
    }

    /**
     * 把一条链整体换上全新的 id（链、节点、选项、解释块都换），并保持内部引用正确。
     * 复制和导入都要用：id 撞车会让两条链互相串数据。
     */
    private fun reissueIds(source: NodeChain, title: String): NodeChain {
        val idMap = source.nodes.associate { it.id to newId() }
        val nodes = source.nodes.map { node ->
            when (node) {
                is QuestionNode -> node.copy(
                    id = idMap.getValue(node.id),
                    options = node.options.map { opt ->
                        opt.copy(id = newId(), nextNodeId = opt.nextNodeId?.let { idMap[it] })
                    },
                    explanations = node.explanations.map { it.copy(id = newId()) },
                )

                is ResultNode -> node.copy(
                    id = idMap.getValue(node.id),
                    explanations = node.explanations.map { it.copy(id = newId()) },
                )
            }
        }
        val now = System.currentTimeMillis()
        return source.copy(
            id = newId(),
            title = title,
            startNodeId = source.startNodeId?.let { idMap[it] },
            nodes = nodes,
            createdAt = now,
            updatedAt = now,
        )
    }

    /** 复制一条链，节点 id 全部重新生成，避免两条链互相干扰。 */
    fun duplicateChain(id: String): String? {
        val source = chain(id) ?: return null
        val copy = reissueIds(source, uniqueChainTitle(source.title + "（副本）"))
        _chains.value = _chains.value + copy
        persistChains()
        return copy.id
    }

    private fun mutate(chainId: String, block: (NodeChain) -> NodeChain) {
        _chains.value = _chains.value.map {
            if (it.id == chainId) block(it).copy(updatedAt = System.currentTimeMillis()) else it
        }
        persistChains()
    }

    fun updateChainMeta(chainId: String, title: String, description: String) =
        mutate(chainId) { it.copy(title = title, description = description) }

    fun setStartNode(chainId: String, nodeId: String?) =
        mutate(chainId) { it.copy(startNodeId = nodeId) }

    // ---------- 节点 ----------

    /** 新建节点；若这是链里第一个节点，顺手设成起点。 */
    fun addQuestionNode(chainId: String, question: String = ""): String {
        val id = newId()
        mutate(chainId) { chain ->
            val node = QuestionNode(
                id = id,
                question = question,
                options = listOf(AnswerOption(newId(), "是"), AnswerOption(newId(), "否")),
            )
            chain.copy(
                nodes = chain.nodes + node,
                startNodeId = chain.startNodeId ?: id,
            )
        }
        return id
    }

    fun addResultNode(chainId: String, outcome: Outcome = Outcome.PASS): String {
        val id = newId()
        mutate(chainId) { chain ->
            val node = ResultNode(
                id = id,
                title = uniqueTitle(chain, defaultResultTitle(outcome)),
                outcome = outcome,
            )
            chain.copy(nodes = chain.nodes + node, startNodeId = chain.startNodeId ?: id)
        }
        return id
    }

    /**
     * 默认标题撞车时自动编号："未通过" -> "未通过 2" -> "未通过 3"。
     *
     * 同名节点是个真实的坑：两个都叫"未通过"的结果节点，报错信息里根本分不出
     * 是哪一个走不到，用户会以为是误报。从源头上让默认名唯一。
     */
    internal fun uniqueTitle(chain: NodeChain, base: String): String =
        uniqueName(base, chain.nodes.mapNotNull { (it as? ResultNode)?.title }.toSet())

    /** 链名去重，导入撞名时用："新生入学评估" -> "新生入学评估 2"。 */
    private fun uniqueChainTitle(base: String, extraTaken: Set<String> = emptySet()): String =
        uniqueName(base, _chains.value.map { it.title }.toSet() + extraTaken)

    internal fun uniqueName(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    /** 删除节点，同时把所有指向它的答案断开，不留悬空引用。 */
    fun deleteNode(chainId: String, nodeId: String) = mutate(chainId) { chain ->
        val remaining = chain.nodes.filterNot { it.id == nodeId }.map { node ->
            if (node is QuestionNode) {
                node.copy(options = node.options.map { opt ->
                    if (opt.nextNodeId == nodeId) opt.copy(nextNodeId = null) else opt
                })
            } else {
                node
            }
        }
        chain.copy(
            nodes = remaining,
            startNodeId = if (chain.startNodeId == nodeId) remaining.firstOrNull()?.id else chain.startNodeId,
        )
    }

    private fun mutateNode(chainId: String, nodeId: String, block: (ChainNode) -> ChainNode) =
        mutate(chainId) { chain ->
            chain.copy(nodes = chain.nodes.map { if (it.id == nodeId) block(it) else it })
        }

    fun updateQuestion(chainId: String, nodeId: String, text: String) =
        mutateNode(chainId, nodeId) { if (it is QuestionNode) it.copy(question = text) else it }

    fun updateResult(chainId: String, nodeId: String, title: String, outcome: Outcome) =
        mutateNode(chainId, nodeId) {
            if (it is ResultNode) it.copy(title = title, outcome = outcome) else it
        }

    // ---------- 答案选项 ----------

    fun addOption(chainId: String, nodeId: String, text: String = "") =
        mutateNode(chainId, nodeId) {
            if (it is QuestionNode) it.copy(options = it.options + AnswerOption(newId(), text)) else it
        }

    fun updateOptionText(chainId: String, nodeId: String, optionId: String, text: String) =
        mutateNode(chainId, nodeId) { node ->
            if (node !is QuestionNode) node
            else node.copy(options = node.options.map {
                if (it.id == optionId) it.copy(text = text) else it
            })
        }

    fun setOptionTarget(chainId: String, nodeId: String, optionId: String, targetId: String?) =
        mutateNode(chainId, nodeId) { node ->
            if (node !is QuestionNode) node
            else node.copy(options = node.options.map {
                if (it.id == optionId) it.copy(nextNodeId = targetId) else it
            })
        }

    fun deleteOption(chainId: String, nodeId: String, optionId: String) =
        mutateNode(chainId, nodeId) { node ->
            if (node !is QuestionNode) node
            else node.copy(options = node.options.filterNot { it.id == optionId })
        }

    /** 新建一个节点并直接接到某个答案后面——搭链路时最顺手的操作。 */
    fun createAndLink(chainId: String, nodeId: String, optionId: String, result: Outcome?): String {
        val newNodeId = if (result == null) addQuestionNode(chainId) else addResultNode(chainId, result)
        setOptionTarget(chainId, nodeId, optionId, newNodeId)
        return newNodeId
    }

    // ---------- 解释性文字 ----------

    private fun mapExplanations(
        node: ChainNode,
        block: (List<ExplanationBlock>) -> List<ExplanationBlock>,
    ): ChainNode = when (node) {
        is QuestionNode -> node.copy(explanations = block(node.explanations))
        is ResultNode -> node.copy(explanations = block(node.explanations))
    }

    fun addExplanation(chainId: String, nodeId: String) =
        mutateNode(chainId, nodeId) { node ->
            mapExplanations(node) { it + ExplanationBlock(newId()) }
        }

    fun updateExplanation(
        chainId: String,
        nodeId: String,
        blockId: String,
        title: String,
        body: String,
    ) = mutateNode(chainId, nodeId) { node ->
        mapExplanations(node) { list ->
            list.map { if (it.id == blockId) it.copy(title = title, body = body) else it }
        }
    }

    fun deleteExplanation(chainId: String, nodeId: String, blockId: String) =
        mutateNode(chainId, nodeId) { node ->
            mapExplanations(node) { list -> list.filterNot { it.id == blockId } }
        }

    // ---------- 测试历史 ----------

    fun recordRun(run: TestRun) {
        _history.value = (listOf(run) + _history.value).take(MAX_HISTORY)
        persistHistory()
    }

    /**
     * 测试级批注的增删改。[stepIndex] 为 -1 表示结果页上的批注。
     *
     * 测试做完后在历史记录里仍然可以改批注——很多情况是当场来不及写、事后才补。
     * 但作答路径和结论是只读的，不提供修改入口。
     */
    private fun mutateRun(runId: String, block: (TestRun) -> TestRun) {
        _history.value = _history.value.map { if (it.id == runId) block(it) else it }
        persistHistory()
    }

    private fun mapRunNotes(
        run: TestRun,
        stepIndex: Int,
        block: (List<ExplanationBlock>) -> List<ExplanationBlock>,
    ): TestRun = if (stepIndex < 0) {
        run.copy(resultNotes = block(run.resultNotes))
    } else {
        run.copy(
            steps = run.steps.mapIndexed { i, step ->
                if (i == stepIndex) step.copy(notes = block(step.notes)) else step
            }
        )
    }

    fun addRunNote(runId: String, stepIndex: Int) = mutateRun(runId) { run ->
        mapRunNotes(run, stepIndex) { it + ExplanationBlock(newId()) }
    }

    fun updateRunNote(
        runId: String,
        stepIndex: Int,
        blockId: String,
        title: String,
        body: String,
    ) = mutateRun(runId) { run ->
        mapRunNotes(run, stepIndex) { list ->
            list.map { if (it.id == blockId) it.copy(title = title, body = body) else it }
        }
    }

    fun deleteRunNote(runId: String, stepIndex: Int, blockId: String) = mutateRun(runId) { run ->
        mapRunNotes(run, stepIndex) { list -> list.filterNot { it.id == blockId } }
    }

    fun clearHistory() {
        _history.value = emptyList()
        persistHistory()
    }

    fun deleteRun(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
        persistHistory()
    }

    private const val MAX_HISTORY = 200

    // ---------- 导入导出 ----------

    fun exportJson(chainIds: Set<String>, runIds: Set<String>): String {
        val bundle = ExportBundle(
            format = EXPORT_FORMAT,
            exportedAt = System.currentTimeMillis(),
            chains = _chains.value.filter { it.id in chainIds },
            history = _history.value.filter { it.id in runIds },
        )
        return json.encodeToString(bundle)
    }

    /**
     * 导入。全部 id 重新生成，所以同一个文件导两次会得到两份独立的数据，
     * 不会覆盖已有的东西。链名撞车就往后编号。
     *
     * 同一批里链和记录一起导的话，记录的 chainId 会重新指向新生成的那条链。
     */
    fun importJson(text: String): ImportResult {
        val bundle = parseBundle(text) ?: return ImportResult(
            error = "这个文件不是节点链导出的格式，或者内容已损坏。"
        )
        if (bundle.chains.isEmpty() && bundle.history.isEmpty()) {
            return ImportResult(error = "文件里没有可导入的内容。")
        }

        val renamed = mutableListOf<String>()
        val newChains = mutableListOf<NodeChain>()
        val chainIdMap = mutableMapOf<String, String>()
        // 同一批里可能自带重名，所以边导边把已占用的名字累加进去
        val takenInBatch = mutableSetOf<String>()

        bundle.chains.forEach { source ->
            val base = source.title.ifBlank { "未命名节点链" }
            val title = uniqueChainTitle(base, takenInBatch)
            if (title != base) renamed += title
            takenInBatch += title
            val copy = reissueIds(source, title)
            chainIdMap[source.id] = copy.id
            newChains += copy
        }

        val newRuns = bundle.history.map { run ->
            run.copy(
                id = newId(),
                chainId = chainIdMap[run.chainId] ?: run.chainId,
            )
        }

        if (newChains.isNotEmpty()) {
            _chains.value = _chains.value + newChains
            persistChains()
        }
        if (newRuns.isNotEmpty()) {
            _history.value = (newRuns + _history.value)
                .sortedByDescending { it.finishedAt }
                .take(MAX_HISTORY)
            persistHistory()
        }

        return ImportResult(chains = newChains.size, runs = newRuns.size, renamed = renamed)
    }

    /** 除了标准的导出文件，也认裸的链数组——直接把 chains.json 拿来导也能用。 */
    internal fun parseBundle(text: String): ExportBundle? {
        runCatching {
            val bundle = json.decodeFromString<ExportBundle>(text)
            if (bundle.format == EXPORT_FORMAT) return bundle
        }
        runCatching {
            return ExportBundle(
                format = EXPORT_FORMAT,
                chains = json.decodeFromString<List<NodeChain>>(text),
            )
        }
        return null
    }

    fun newId(): String = UUID.randomUUID().toString()
}
