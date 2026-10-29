package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iptv.player.AppViewModel
import com.iptv.player.Catchup
import com.iptv.player.Channel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Телепрограмма канала. Прошедшие передачи можно смотреть из архива (если канал поддерживает). */
@Composable
fun GuideDialog(vm: AppViewModel, ch: Channel, onClose: () -> Unit) {
    val now = System.currentTimeMillis()
    val programs = remember(vm.epg, ch) { vm.epg.forChannel(ch) }
    val dayFmt = remember { SimpleDateFormat("yyyyMMdd", Locale.US) }
    val curIndex = programs.indexOfFirst { now in it.start until it.stop }.coerceAtLeast(0)

    // Позиция списка: учитываем заголовки дней
    val rows = remember(programs) {
        val out = ArrayList<Any>()
        var last = ""
        programs.forEach { p ->
            val d = dayFmt.format(Date(p.start))
            if (d != last) { last = d; out += fmtDay(p.start).replaceFirstChar { it.uppercase() } }
            out += p
        }
        out
    }
    val state = rememberLazyListState()
    LaunchedEffect(rows) {
        val target = programs.getOrNull(curIndex)
        val idx = rows.indexOfFirst { it === target }
        if (idx > 0) state.scrollToItem((idx - 1).coerceAtLeast(0))
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.tvFocus(24)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                }
                Logo(ch.logo, ch.name, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(
                        if (ch.hasArchive) "Архив: ${ch.catchupDays} дн." else "Архив недоступен",
                        color = TextDim, fontSize = 12.sp,
                    )
                }
                val fav = vm.isFavorite(ch)
                IconButton(onClick = { vm.toggleFavorite(ch) }, modifier = Modifier.tvFocus(24)) {
                    Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Избранное",
                        tint = if (fav) Color(0xFFFFC857) else TextDim)
                }
            }
            if (programs.isEmpty()) {
                EmptyState("Для этого канала нет программы передач.\nУкажите ссылку на EPG (XMLTV) в настройках плейлиста.")
            } else {
                LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                    items(rows.size) { i ->
                        when (val r = rows[i]) {
                            is String -> SectionHeader(r)
                            is com.iptv.player.Program -> {
                                val isNow = now in r.start until r.stop
                                val past = r.stop <= now
                                val canArchive = past && Catchup.canPlay(ch, r, now)
                                val clickable = isNow || canArchive
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                                        .tvFocus().clip(RoundedCornerShape(12.dp))
                                        .background(if (isNow) Color(0xFF1D3557) else Surface1)
                                        .then(
                                            if (clickable) Modifier.clickable {
                                                if (isNow) vm.play(ch) else vm.playArchive(ch, r)
                                                onClose()
                                            } else Modifier.clickable { }
                                        ).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${fmtTime(r.start)}–${fmtTime(r.stop)}",
                                        color = if (isNow) Accent else TextDim, fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(r.title, fontWeight = FontWeight.SemiBold,
                                            color = if (past && !canArchive) TextDim else Color.Unspecified)
                                        if (r.desc.isNotBlank()) {
                                            Text(r.desc, color = TextDim, fontSize = 12.sp, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (isNow) Text("СЕЙЧАС", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    else if (canArchive) Icon(Icons.Filled.PlayCircle, "Смотреть из архива", tint = Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val unusedBox: @Composable () -> Unit = { Box {} }
