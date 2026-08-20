package com.echobooks.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.echobooks.app.EchoBooksApp
import com.echobooks.app.ui.components.BottomNavTab
import com.echobooks.app.ui.components.GlassBottomBar
import com.echobooks.app.ui.create.CreateScreen
import com.echobooks.app.ui.create.GeneratingScreen
import com.echobooks.app.ui.home.HomeScreen
import com.echobooks.app.ui.import.ImportArgs
import com.echobooks.app.ui.import.ImportScreen
import com.echobooks.app.ui.library.LibraryScreen
import com.echobooks.app.ui.player.PlayerScreen
import com.echobooks.app.ui.settings.SettingsScreen
import com.echobooks.app.ui.viewmodel.GenerationViewModel
import com.echobooks.app.ui.viewmodel.ImportViewModel
import com.echobooks.app.ui.viewmodel.LibraryViewModel
import com.echobooks.app.ui.viewmodel.MiniPlayerViewModel
import com.echobooks.app.ui.viewmodel.PlayerViewModel
import com.echobooks.app.ui.viewmodel.SettingsViewModel
import java.io.File

object Routes {
    const val Home = "home"
    const val Library = "library"
    const val Create = "create"
    const val Generating = "generating"
    const val Import = "import"
    const val Player = "player"
    const val Settings = "settings"
}

private val TopLevelRoutes = setOf(Routes.Home, Routes.Create, Routes.Library, Routes.Settings)

@Composable
fun AppNavHost() {
    val nav: NavHostController = rememberNavController()
    val generationVm: GenerationViewModel = viewModel()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in TopLevelRoutes

    Scaffold(
        containerColor = com.echobooks.app.ui.theme.InkDeep,
        bottomBar = {
            if (showBar) {
                GlassBottomBar(
                    currentRoute = currentRoute ?: Routes.Home,
                    onSelect = { route ->
                        nav.navigate(route) {
                            popUpTo(Routes.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            NavHost(nav, startDestination = Routes.Home) {
                composable(Routes.Home) {
                    val vm: LibraryViewModel = viewModel()
                    val miniVm: MiniPlayerViewModel = viewModel()
                    HomeScreen(
                        vm = vm,
                        miniVm = miniVm,
                        onOpenBook = { id -> nav.navigate("${Routes.Player}/$id") },
                        onCreateBook = { nav.navigate(Routes.Create) },
                        onSettings = { nav.navigate(Routes.Settings) }
                    )
                }
                composable(Routes.Library) {
                    val vm: LibraryViewModel = viewModel()
                    val miniVm: MiniPlayerViewModel = viewModel()
                    LibraryScreen(
                        vm = vm,
                        miniVm = miniVm,
                        onOpenBook = { id -> nav.navigate("${Routes.Player}/$id") },
                        onCreateBook = { nav.navigate(Routes.Create) },
                        onImportBook = { _, _ -> nav.navigate(Routes.Import) },
                        onSettings = { nav.navigate(Routes.Settings) }
                    )
                }
                composable(Routes.Create) {
                    CreateScreen(
                        onBack = { nav.navigate(Routes.Home) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true } },
                        onStart = { spec ->
                            generationVm.start(spec)
                            nav.navigate(Routes.Generating)
                        }
                    )
                }
                composable(Routes.Generating) {
                    GeneratingScreen(
                        vm = generationVm,
                        onLeave = { nav.navigate(Routes.Home) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true } },
                        onCancel = {
                            generationVm.cancel()
                            nav.navigate(Routes.Home) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true }
                        },
                        onDone = { id ->
                            nav.navigate("${Routes.Player}/$id") {
                                popUpTo(Routes.Home)
                            }
                        },
                        onListen = { id ->
                            nav.navigate("${Routes.Player}/$id")
                        }
                    )
                }
                composable(Routes.Import) {
                    val vm: ImportViewModel = viewModel()
                    ImportScreen(
                        vm = vm,
                        onCancel = {
                            vm.cancel()
                            nav.popBackStack()
                        },
                        onDone = { id ->
                            nav.navigate("${Routes.Player}/$id") {
                                popUpTo(Routes.Home)
                            }
                        }
                    )
                }
                composable("${Routes.Player}/{bookId}") { entry ->
                    val bookId = entry.arguments?.getString("bookId")?.toLongOrNull() ?: 0L
                    val vm: PlayerViewModel = viewModel()
                    PlayerScreen(
                        vm = vm,
                        bookId = bookId,
                        onBack = {
                            vm.saveNow()
                            nav.popBackStack()
                        }
                    )
                }
                composable(Routes.Settings) {
                    val vm: SettingsViewModel = viewModel()
                    SettingsScreen(
                        vm = vm,
                        onBack = { nav.navigate(Routes.Home) { popUpTo(Routes.Home) { saveState = true }; launchSingleTop = true } }
                    )
                }
            }
        }
    }
}