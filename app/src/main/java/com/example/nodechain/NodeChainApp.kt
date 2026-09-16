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
    const val NODE = "node/{chainId}/{nodeId}"
    const val RUN = "run/{chainId}"

    fun editor(chainId: String) = "editor/${Uri.encode(chainId)}"
    fun node(chainId: String, nodeId: String) = "node/${Uri.encode(chainId)}/${Uri.encode(nodeId)}"
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
                onEditNode = { nav.navigate(Routes.node(chainId, it)) },
                onRun = { nav.navigate(Routes.run(chainId)) },
            )
        }

        composable(
            route = Routes.NODE,
            arguments = listOf(
                navArgument("chainId") { type = NavType.StringType },
                navArgument("nodeId") { type = NavType.StringType },
            ),
        ) { entry ->
            val chainId = entry.arguments?.getString("chainId").orEmpty()
            val nodeId = entry.arguments?.getString("nodeId").orEmpty()
            NodeEditorScreen(
                chainId = chainId,
                nodeId = nodeId,
                onBack = { nav.popBackStack() },
                onOpenNode = { nav.navigate(Routes.node(chainId, it)) },
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
