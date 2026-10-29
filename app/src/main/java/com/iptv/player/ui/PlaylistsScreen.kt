package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.player.AppViewModel
import com.iptv.player.Playlist

@Composable
fun PlaylistsScreen(vm: AppViewModel, onPickFile: () -> Unit) {
    var showAddUrl by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var editEpg by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }
    var editFilter by remember { mutableStateOf<Playlist?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { showAddUrl = true }, modifier = Modifier.weight(1f).tvFocus(20)) {
                Icon(Icons.Filled.Link, null); Text("  По ссылке")
            }
            OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f).tvFocus(20)) {
                Icon(Icons.Filled.Folder, null); Text("  Из файла")
            }
        }

        if (vm.playlists.isEmpty()) {
            EmptyState("Плейлистов пока нет.\nДобавьте M3U по ссылке или выберите файл на устройстве.")
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.playlists) { p ->
                val active = p.id == vm.currentId
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                        .tvFocus().clip(RoundedCornerShape(12.dp))
                        .background(if (active) androidx.compose.ui.graphics.Color(0xFF1D3557) else Surface1)
                        .clickable { vm.select(p.id) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (active) Icons.Filled.CheckCircle else Icons.Filled.Tv, null,
                        tint = if (active) Accent else TextDim,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (active) "${vm.channels.size} каналов · " + (if (p.isUrl) "ссылка" else "файл")
                            else if (p.isUrl) p.source else "Локальный файл",
                            color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (active && (vm.loading || vm.epgLoading)) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(24.dp), strokeWidth = 2.dp)
                    }
                    if (active && p.isUrl) {
                        IconButton(onClick = { vm.refresh() }, modifier = Modifier.tvFocus(24)) {
                            Icon(Icons.Filled.Refresh, "Обновить", tint = TextDim)
                        }
                    }
                    IconButton(onClick = { editEpg = p }, modifier = Modifier.tvFocus(24)) {
                        Icon(Icons.Filled.Link, "EPG", tint = TextDim)
                    }
                    IconButton(onClick = { editFilter = p }, modifier = Modifier.tvFocus(24)) {
                        Icon(Icons.Filled.FilterAlt, "Фильтр", tint = if (p.filter.isNotBlank()) Accent else TextDim)
                    }
                    IconButton(onClick = { renaming = p }, modifier = Modifier.tvFocus(24)) {
                        Icon(Icons.Filled.Edit, "Переименовать", tint = TextDim)
                    }
                    IconButton(onClick = { deleting = p }, modifier = Modifier.tvFocus(24)) {
                        Icon(Icons.Filled.Delete, "Удалить", tint = TextDim)
                    }
                }
            }
        }
    }

    if (showAddUrl) AddUrlDialog(onDismiss = { showAddUrl = false }) { n, u, e ->
        showAddUrl = false; vm.addUrl(n, u, e)
    }
    renaming?.let { p ->
        TextInputDialog("Название плейлиста", "Название", p.name, { renaming = null }) {
            vm.rename(p.id, it); renaming = null
        }
    }
    editEpg?.let { p ->
        TextInputDialog("Ссылка на программу передач (XMLTV)", "URL EPG (.xml / .xml.gz)", p.epgUrl,
            { editEpg = null }) { vm.setEpgUrl(p.id, it); editEpg = null }
    }
    editFilter?.let { p ->
        TextInputDialog("Скрыть каналы со словами", "Через запятую: реклама, promo, ads",
            p.filter, { editFilter = null }) { vm.setFilter(p.id, it); editFilter = null }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${p.name}»?") },
            text = { Text("Избранное и история сохранятся.") },
            confirmButton = { TextButton(onClick = { vm.delete(p.id); deleting = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AddUrlDialog(onDismiss: () -> Unit, onOk: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый плейлист") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("Ссылка на M3U") }, singleLine = true)
                OutlinedTextField(epg, { epg = it }, label = { Text("Ссылка на EPG (необязательно)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onOk(name, url, epg) }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
