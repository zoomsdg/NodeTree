package com.example.nodechain

import com.example.nodechain.data.AnswerOption
import com.example.nodechain.data.ChainStore
import com.example.nodechain.data.EXPORT_FORMAT
import com.example.nodechain.data.ExplanationBlock
import com.example.nodechain.data.ExportBundle
import com.example.nodechain.data.RunStep
import com.example.nodechain.data.TestRun
import com.example.nodechain.data.ChainIssue
import com.example.nodechain.data.NodeChain
import com.example.nodechain.data.Outcome
import com.example.nodechain.data.QuestionNode
import com.example.nodechain.data.ResultNode
import com.example.nodechain.data.isRunnable
import com.example.nodechain.data.issues
import com.example.nodechain.data.reachableIds
import com.example.nodechain.ui.about.sdkLabel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 链路的校验与遍历逻辑是这个 app 的核心，
 * 而且全是纯函数，用 JVM 单元测试覆盖最划算。
 */
class ChainLogicTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun sampleChain(): NodeChain {
        val pass = ResultNode(id = "pass", title = "通过", outcome = Outcome.PASS)
        val fail = ResultNode(id = "fail", title = "未通过", outcome = Outcome.FAIL)
        val q2 = QuestionNode(
            id = "q2",
            question = "会写自己的名字吗？",
            options = listOf(
                AnswerOption("q2a", "会", "pass"),
                AnswerOption("q2b", "不会", "fail"),
            ),
        )
        val q1 = QuestionNode(
            id = "q1",
            question = "满 12 周岁了吗？",
            options = listOf(
                AnswerOption("q1a", "是", "q2"),
                AnswerOption("q1b", "否", "fail"),
            ),
        )
        return NodeChain(
            id = "c1",
            title = "入学评估",
            startNodeId = "q1",
            nodes = listOf(q1, q2, pass, fail),
        )
    }

    @Test
    fun `配置完整的链没有校验问题`() {
        assertTrue(sampleChain().issues().isEmpty())
        assertTrue(sampleChain().isRunnable())
    }

    @Test
    fun `可达性能覆盖全部节点`() {
        assertEquals(setOf("q1", "q2", "pass", "fail"), sampleChain().reachableIds())
    }

    @Test
    fun `答案没接下一个节点时报断头路`() {
        val chain = sampleChain().let { c ->
            val q1 = c.nodes.first { it.id == "q1" } as QuestionNode
            val broken = q1.copy(options = q1.options.map { it.copy(nextNodeId = null) })
            c.copy(nodes = c.nodes.map { if (it.id == "q1") broken else it })
        }
        val dangling = chain.issues().filterIsInstance<ChainIssue.DanglingOption>()
        assertEquals(2, dangling.size)
    }

    @Test
    fun `走不到的节点会被指出来`() {
        val orphan = ResultNode(id = "orphan", title = "孤儿", outcome = Outcome.NEUTRAL)
        val chain = sampleChain().let { it.copy(nodes = it.nodes + orphan) }
        val unreachable = chain.issues().filterIsInstance<ChainIssue.Unreachable>()
        assertEquals(1, unreachable.size)
        assertEquals("orphan", unreachable.first().nodeId)
    }

    @Test
    fun `没有起点的链跑不起来`() {
        val chain = sampleChain().copy(startNodeId = null)
        assertFalse(chain.isRunnable())
        assertTrue(chain.issues().contains(ChainIssue.NoStart))
    }

    @Test
    fun `成环的链路遍历不会死循环`() {
        val q1 = QuestionNode(
            id = "q1",
            question = "再来一次？",
            options = listOf(AnswerOption("a", "是", "q1")),
        )
        val chain = NodeChain(id = "c", title = "环", startNodeId = "q1", nodes = listOf(q1))
        assertEquals(setOf("q1"), chain.reachableIds())
    }

    @Test
    fun `节点链能完整地序列化再还原`() {
        val original = sampleChain()
        val restored = json.decodeFromString<NodeChain>(json.encodeToString(original))
        assertEquals(original, restored)
    }

    @Test
    fun `走不到的问题会带上节点 id 供界面定位`() {
        val orphan = ResultNode(id = "orphan", title = "未通过", outcome = Outcome.FAIL)
        val chain = sampleChain().let { it.copy(nodes = it.nodes + orphan) }
        val issue = chain.issues().filterIsInstance<ChainIssue.Unreachable>().single()
        assertEquals("orphan", issue.nodeId)
    }

    @Test
    fun `同名的结果节点会自动编号`() {
        val chain = NodeChain(
            id = "c",
            nodes = listOf(
                ResultNode(id = "r1", title = "未通过", outcome = Outcome.FAIL),
                ResultNode(id = "r2", title = "未通过 2", outcome = Outcome.FAIL),
            ),
        )
        // 撞车时往后找第一个没被占用的编号
        assertEquals("未通过 3", ChainStore.uniqueTitle(chain, "未通过"))
        // 不撞车就保持原样
        assertEquals("通过", ChainStore.uniqueTitle(chain, "通过"))
    }

    @Test
    fun `旧版历史记录没有批注字段也能读出来`() {
        // 加测试级批注之前存下来的记录，缺 nodeId / notes / resultNotes
        val legacy = """
            {"id":"r1","chainId":"c1","chainTitle":"入学评估","finishedAt":1757900000000,
             "steps":[{"question":"满 12 周岁了吗？","answer":"是"}],
             "outcome":"PASS","resultTitle":"通过"}
        """.trimIndent()
        val run = json.decodeFromString<TestRun>(legacy)
        assertEquals(1, run.steps.size)
        assertEquals("", run.steps.first().nodeId)
        assertTrue(run.steps.first().notes.isEmpty())
        assertTrue(run.resultNotes.isEmpty())
        assertEquals(0, run.noteCount)
    }

    @Test
    fun `批注计数把题目和结果的都算上`() {
        val run = TestRun(
            id = "r", chainId = "c", chainTitle = "t", finishedAt = 0L,
            steps = listOf(
                RunStep("q1", "a1", "n1", listOf(ExplanationBlock("b1"), ExplanationBlock("b2"))),
                RunStep("q2", "a2", "n2", listOf(ExplanationBlock("b3"))),
            ),
            resultNotes = listOf(ExplanationBlock("b4")),
        )
        assertEquals(4, run.noteCount)
    }

    @Test
    fun `测试级批注不写进节点本身`() {
        // 批注只活在 TestRun 里，链和节点的数据结构完全没被碰过
        val chain = sampleChain()
        val run = TestRun(
            id = "r", chainId = chain.id, chainTitle = chain.title, finishedAt = 0L,
            steps = listOf(RunStep("满 12 周岁了吗？", "是", "q1", listOf(ExplanationBlock("b1", "口头补充")))),
        )
        assertEquals(1, run.steps.first().notes.size)
        assertTrue(chain.nodes.all { it.explanations.isEmpty() })
    }

    @Test
    fun `链名去重按顺序往后编号`() {
        val taken = setOf("入学评估", "入学评估 2")
        assertEquals("入学评估 3", ChainStore.uniqueName("入学评估", taken))
        assertEquals("别的链", ChainStore.uniqueName("别的链", taken))
    }

    @Test
    fun `导出文件能完整往返`() {
        val bundle = ExportBundle(
            format = EXPORT_FORMAT,
            exportedAt = 1758000000000L,
            chains = listOf(sampleChain()),
            history = listOf(
                TestRun(
                    id = "r1", chainId = "c1", chainTitle = "入学评估", finishedAt = 1L,
                    steps = listOf(RunStep("q", "a", "n", listOf(ExplanationBlock("b", "批注")))),
                    resultNotes = listOf(ExplanationBlock("b2", "总评")),
                )
            ),
        )
        val restored = json.decodeFromString<ExportBundle>(json.encodeToString(bundle))
        assertEquals(bundle, restored)
        assertEquals(EXPORT_FORMAT, restored.format)
    }

    @Test
    fun `裸的链数组也能被识别成可导入内容`() {
        // 直接把 chains.json 拿来导，应该也认
        val raw = json.encodeToString(listOf(sampleChain()))
        val parsed = ChainStore.parseBundle(raw)
        assertEquals(1, parsed?.chains?.size)
        assertTrue(parsed?.history.isNullOrEmpty())
    }

    @Test
    fun `不是导出格式的文件会被拒绝`() {
        assertNull(ChainStore.parseBundle("这不是 json"))
        assertNull(ChainStore.parseBundle("""{"hello":"world"}"""))
    }

    @Test
    fun `版本号翻译覆盖已知与未知区间`() {
        assertEquals("Android 12", sdkLabel(31))
        assertEquals("Android 16", sdkLabel(36))
        assertEquals("Android 17", sdkLabel(99))
        assertEquals("Android (API 21)", sdkLabel(21))
    }
}
