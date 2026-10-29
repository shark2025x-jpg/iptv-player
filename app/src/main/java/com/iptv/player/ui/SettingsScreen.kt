package com.iptv.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.player.AppViewModel
import com.iptv.player.Presets
import com.iptv.player.Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Настройки", fontWeight = FontWeight.Bold, fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        SettingsContent(vm, onlyPicture = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(vm: AppViewModel, onlyPicture: Boolean) {
    val s = vm.settings
    fun upd(n: Settings) = vm.updateSettings(n)
    fun custom(n: Settings) = upd(n.copy(preset = "custom"))

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("ПОДСВЕТКА (AMBILIGHT)")
        SwitchRow("Свечение вокруг видео", "Цвета краёв картинки подсвечивают экран. Картинка немного уменьшается.",
            s.ambient) { upd(s.copy(ambient = it)) }
        if (s.ambient) {
            LabeledSlider("Яркость свечения", "${(s.ambientStrength * 100).toInt()}%",
                s.ambientStrength, 0.2f..1f) { upd(s.copy(ambientStrength = it)) }
        }

        SectionHeader("КАРТИНКА")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)) {
            Presets.all.forEach { (id, title) -> Chip(title, s.preset == id) { upd(Presets.apply(s, id)) } }
        }
        LabeledSlider("Яркость", "%+d".format((s.brightness * 100).toInt()),
            s.brightness, -0.3f..0.3f) { custom(s.copy(brightness = it)) }
        LabeledSlider("Контраст", "${(s.contrast * 100).toInt()}%",
            s.contrast, 0.7f..1.4f) { custom(s.copy(contrast = it)) }
        LabeledSlider("Насыщенность", "${(s.saturation * 100).toInt()}%",
            s.saturation, 0f..2f) { custom(s.copy(saturation = it)) }
        TextButton(onClick = { upd(Presets.apply(s, "standard")) }, modifier = Modifier.tvFocus(20)) {
            Text("Сбросить картинку")
        }
        if (s.needsTexture) {
            Text("Эффекты используют режим отрисовки, который чуть тяжелее для 4K/8K. " +
                "Для максимальной производительности выключите подсветку и верните «Стандарт».",
                color = TextDim, fontSize = 12.sp)
        }

        if (!onlyPicture) {
            SectionHeader("ВОСПРОИЗВЕДЕНИЕ")
            SwitchRow("Всегда максимальное качество",
                "Не снижать разрешение автоматически (нужен быстрый интернет)", s.maxQuality) {
                upd(s.copy(maxQuality = it))
            }
            Text("Время показа панели плеера", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                listOf(3, 5, 10).forEach { sec -> Chip("$sec с", s.overlaySec == sec) { upd(s.copy(overlaySec = sec)) } }
            }
            SectionHeader("О ПРИЛОЖЕНИИ")
            Text("IPTV Плеер 1.0 · приложение не содержит рекламы",
                color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, color = TextDim, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.tvFocus(16))
    }
}

@Composable
private fun LabeledSlider(
    label: String, valueText: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = TextDim, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.tvFocus(16))
    }
}
