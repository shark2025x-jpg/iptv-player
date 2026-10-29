package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.player.AppViewModel
import com.iptv.player.Channel
import com.iptv.player.HistoryItem
import com.iptv.player.Program
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    return now
}

@Composable
private fun ChannelList(
    vm: AppViewModel,
    list: List<Channel>,
    onGuide: (Channel) -> Unit,
    empty: String,
) {
    val now = rememberNow()
    if (list.isEmpty()) { EmptyState(empty); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list) { ch ->
            val cur = remember(vm.epg, ch, now / 60000) {
                vm.epg.forChannel(ch).firstOrNull { now in it.start until it.stop }
            }
            val progress = cur?.let { ((now - it.start).toFloat() / (it.stop - it.start)).coerceIn(0f, 1f) }
            val hasGuide = remember(vm.epg, ch) { vm.epg.forChannel(ch).isNotEmpty() || ch.hasArchive }
            ChannelRow(
                ch = ch,
                isFav = vm.isFavorite(ch),
                nowTitle = cur?.let { "${fmtTime(it.start)}  ${it.title}" },
                progress = progress,
                onPlay = { vm.play(ch, list) },
                onFav = { vm.toggleFavorite(ch) },
                onGuide = if (hasGuide) ({ onGuide(ch) }) else null,
            )
        }
    }
}

@Composable
fun ChannelsScreen(vm: AppViewModel, onGuide: (Channel) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    val groups = remember(vm.channels) { vm.channels.map { it.group }.distinct() }
    val now = rememberNow()

    val filtered = remember(vm.channels, vm.epg, query, group, now / 60000) {
        val q = query.trim().lowercase()
        vm.channels.filter { ch ->
            (group.isEmpty() || ch.group == group) &&
                (q.isEmpty() || ch.name.lowercase().contains(q) || ch.group.lowercase().contains(q) ||
                    vm.epg.forChannel(ch).firstOrNull { now in it.start until it.stop }
                        ?.title?.lowercase()?.contains(q) == true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(query, { query = it })
        if (groups.size > 1) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                item { Chip("Все (${vm.channels.size})", group.isEmpty()) { group = "" } }
                items(groups) { g -> Chip(g, group == g) { group = g } }
            }
        }
        if (vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val empty = if (vm.playlists.isEmpty()) "Добавьте плейлист во вкладке «Плейлисты»"
            else "Ничего не найдено"
            ChannelList(vm, filtered, onGuide, empty)
        }
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, onGuide: (Channel) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = remember(vm.favorites, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) vm.favorites else vm.favorites.filter { it.name.lowercase().contains(q) }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, { query = it })
        ChannelList(vm, list, onGuide, "Избранное пусто.\nНажмите ★ рядом с каналом, чтобы добавить.")
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val dayFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale("ru")) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("История просмотров", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            if (vm.history.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }, modifier = Modifier.tvFocus(20)) {
                    Icon(Icons.Filled.DeleteSweep, null); Text("  Очистить")
                }
            }
        }
        if (vm.history.isEmpty()) {
            EmptyState("Здесь появятся просмотренные каналы и передачи из архива")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            var lastDay = ""
            vm.history.forEach { h ->
                val day = dayFmt.format(Date(h.time))
                if (day != lastDay) {
                    lastDay = day
                    item(key = "d$day") { SectionHeader(day.replaceFirstChar { it.uppercase() }) }
                }
                item(key = "${h.channelKey}|${h.programStart}|${h.time}") { HistoryRow(vm, h) }
            }
        }
    }
}

@Composable
private fun HistoryRow(vm: AppViewModel, h: HistoryItem) {
    val isArchive = h.programTitle.isNotBlank()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .tvFocus().clip(RoundedCornerShape(12.dp)).background(Surface1)
            .clickable {
                val ch = vm.findChannel(h.channelKey)
                if (ch == null) vm.message = "Канал недоступен — откройте его плейлист"
                else if (isArchive && ch.hasArchive)
                    vm.playArchive(ch, Program(h.programStart, h.programStop, h.programTitle))
                else vm.play(ch)
            }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(h.logo, h.name)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(h.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                if (isArchive) "${fmtDateTime(h.programStart)} · ${h.programTitle}" else "Эфир · ${fmtTime(h.time)}",
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, fontSize = 13.sp,
            )
        }
        Icon(Icons.Filled.PlayCircle, null, tint = Accent)
        IconButton(onClick = { vm.removeHistory(h) }, modifier = Modifier.tvFocus(24)) {
            Icon(Icons.Filled.Close, "Удалить", tint = TextDim)
        }
    }
}
