package com.example.nodechain

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nodechain.data.ChainStore
import com.example.nodechain.ui.about.EnvCheckScreen
import com.example.nodechain.ui.editor.ChainEditorScreen
import com.example.nodechain.ui.editor.NodeEditorScreen
import com.example.nodechain.ui.history.HistoryScreen
import com.example.nodechain.ui.home.HomeScreen
import com.example.nodechain.ui.transfer.ImportExportScreen
import com.example.nodechain.ui.run.RunScreen

class NodeChainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ChainStore.init(this)
    }
}

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ENV = "env"
    const val TRANSFER = "transfer"
    const val EDITOR = "editor/{chainId}"
    const val NODE = "node/{chainId}/{nodeId}/{depth}"
    const val RUN = "run/{chainId}"

    fun editor(chainId: String) = "editor/${Uri.encode(chainId)}"
    /** [depth] 是节点编辑页自身的嵌套层数：从链编辑页进来是 0，一层层往下建节点就累加。
     *  用来决定"上一节点"要不要显示——第 0 层的上一步是链编辑页，不是节点。 */
    fun node(chainId: String, nodeId: String, depth: Int) =
        "node/${Uri.encode(chainId)}/${Uri.encode(nodeId)}/$depth"
    fun run(chainId: String) = "run/${Uri.encode(chainId)}"
}

@Composable
fun NodeChainApp() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onRun = { nav.navigate(Routes.run(it)) },
                onEdit = { nav.navigate(Routes.editor(it)) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onEnvCheck = { nav.navigate(Routes.ENV) },
                onTransfer = { nav.navigate(Routes.TRANSFER) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("chainId") { type = NavType.StringType }),
        ) { entry ->
            val chainId = entry.arguments?.getString("chainId").orEmpty()
            ChainEditorScreen(
                chainId = chainId,
                onBack = { nav.popBackStack() },
                onEditNode = { nav.navigate(Routes.node(chainId, it, 0)) },
                onRun = { nav.navigate(Routes.run(chainId)) },
            )
        }

        composable(
            route = Routes.NODE,
            arguments = listOf(
                navArgument("chainId") { type = NavType.StringType },
                navArgument("nodeId") { type = NavType.StringType },
                navArgument("depth") { type = NavType.IntType },
            ),
        ) { entry ->
            val chainId = entry.arguments?.getString("chainId").orEmpty()
            val nodeId = entry.arguments?.getString("nodeId").orEmpty()
            val depth = entry.arguments?.getInt("depth") ?: 0
            NodeEditorScreen(
                chainId = chainId,
                nodeId = nodeId,
                depth = depth,
                onBack = { nav.popBackStack() },
                // 一路往下建了好几层节点时，不用一层层退回去
                onBackToChain = {
                    if (!nav.popBackStack(Routes.editor(chainId), inclusive = false)) {
                        nav.popBackStack()
                    }
                },
                onOpenNode = { nav.navigate(Routes.node(chainId, it, depth + 1)) },
            )
        }

        composable(
            route = Routes.RUN,
            arguments = listOf(navArgument("chainId") { type = NavType.StringType }),
        ) { entry ->
            RunScreen(
                chainId = entry.arguments?.getString("chainId").orEmpty(),
                onExit = { nav.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.ENV) {
            EnvCheckScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.TRANSFER) {
            ImportExportScreen(onBack = { nav.popBackStack() })
        }
    }
}
