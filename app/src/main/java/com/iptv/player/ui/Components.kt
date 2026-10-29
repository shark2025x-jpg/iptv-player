package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.iptv.player.Channel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")
fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm", ru).format(Date(ms))
fun fmtDay(ms: Long): String = SimpleDateFormat("EEEE, d MMMM", ru).format(Date(ms))
fun fmtDateTime(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", ru).format(Date(ms))

@Composable
fun Logo(url: String, name: String, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Surface2),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Text(name.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        } else {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).padding(4.dp))
        }
    }
}

@Composable
fun ChannelRow(
    ch: Channel,
    isFav: Boolean,
    nowTitle: String?,
    progress: Float?,
    onPlay: () -> Unit,
    onFav: () -> Unit,
    onGuide: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .tvFocus().clip(RoundedCornerShape(12.dp)).background(Surface1)
            .clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(ch.logo, ch.name)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            if (nowTitle != null) {
                Text(nowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, fontSize = 13.sp)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp),
                        color = Accent, trackColor = Surface2,
                    )
                }
            } else if (ch.group.isNotBlank()) {
                Text(ch.group, maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextDim, fontSize = 13.sp)
            }
        }
        if (onGuide != null) {
            IconButton(onClick = onGuide, modifier = Modifier.tvFocus(24)) {
                Icon(Icons.Filled.Schedule, "Программа", tint = TextDim)
            }
        }
        IconButton(onClick = onFav, modifier = Modifier.tvFocus(24)) {
            Icon(
                if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder, "Избранное",
                tint = if (isFav) androidx.compose.ui.graphics.Color(0xFFFFC857) else TextDim,
            )
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text("Поиск каналов…") },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Очистить") }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun TextInputDialog(
    title: String, label: String, initial: String = "",
    onDismiss: () -> Unit, onOk: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextDim, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.tvFocus(20).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Accent else Surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, maxLines = 1, color = if (selected) androidx.compose.ui.graphics.Color.White else TextDim,
            fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Suppress("unused")
private val spacing = Arrangement.spacedBy(8.dp)
