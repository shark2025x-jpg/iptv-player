package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.iptv.player.AppViewModel
import com.iptv.player.Channel

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Каналы", Icons.Filled.Tv),
    Tab("Избранное", Icons.Filled.Star),
    Tab("История", Icons.Filled.History),
    Tab("Плейлисты", Icons.AutoMirrored.Filled.PlaylistPlay),
    Tab("Настройки", Icons.Filled.Settings),
)

@Composable
fun App(vm: AppViewModel, onPickFile: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(if (vm.playlists.isEmpty()) 3 else 0) }
    var guideChannel by remember { mutableStateOf<Channel?>(null) }
    val snack = remember { SnackbarHostState() }
    val wide = LocalConfiguration.current.screenWidthDp >= 720
    val np = vm.nowPlaying

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }

    // Полноэкранный режим во время просмотра
    val view = LocalView.current
    LaunchedEffect(np != null) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val c = WindowCompat.getInsetsController(window, view)
        if (np != null) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (wide) {
            Row(Modifier.fillMaxSize().statusBarsPadding()) {
                NavigationRail(containerColor = Surface1) {
                    Box(Modifier.weight(1f))
                    tabs.forEachIndexed { i, t ->
                        NavigationRailItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(t.icon, t.title) }, label = { Text(t.title, fontSize = 11.sp) },
                            modifier = Modifier.tvFocus(16),
                        )
                    }
                    Box(Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).fillMaxSize()) {
                    TopBar(vm)
                    Content(vm, tab, onPickFile) { guideChannel = it }
                }
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        } else {
            Scaffold(
                containerColor = Bg,
                snackbarHost = { SnackbarHost(snack) },
                bottomBar = {
                    NavigationBar(containerColor = Surface1) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(t.icon, t.title) },
                                label = { Text(t.title, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.tvFocus(16),
                            )
                        }
                    }
                },
            ) { pad ->
                Column(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
                    TopBar(vm)
                    Content(vm, tab, onPickFile) { guideChannel = it }
                }
            }
        }

        if (np != null) {
            PlayerScreen(vm, np, onGuide = { guideChannel = np.channel })
        }
        guideChannel?.let { GuideDialog(vm, it) { guideChannel = null } }
    }
}

@Composable
private fun Content(vm: AppViewModel, tab: Int, onPickFile: () -> Unit, onGuide: (Channel) -> Unit) {
    when (tab) {
        0 -> ChannelsScreen(vm, onGuide)
        1 -> FavoritesScreen(vm, onGuide)
        2 -> HistoryScreen(vm)
        3 -> PlaylistsScreen(vm, onPickFile)
        else -> SettingsScreen(vm)
    }
}

/** Верхняя панель: быстрое переключение между плейлистами. */
@Composable
private fun TopBar(vm: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("IPTV", color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Box(Modifier.weight(1f)) {}
        if (vm.playlists.isNotEmpty()) {
            Box {
                TextButton(onClick = { open = true }, modifier = Modifier.tvFocus(20)) {
                    Text(vm.current?.name ?: "Плейлист", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 4.dp))
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    vm.playlists.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Text(p.name, fontWeight = if (p.id == vm.currentId) FontWeight.Bold else FontWeight.Normal,
                                    color = if (p.id == vm.currentId) Accent else androidx.compose.ui.graphics.Color.Unspecified)
                            },
                            onClick = { open = false; vm.select(p.id) },
                        )
                    }
                }
            }
        }
    }
}
