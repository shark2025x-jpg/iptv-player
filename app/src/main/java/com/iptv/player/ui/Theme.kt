package com.iptv.player.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Bg = Color(0xFF0E1116)
val Surface1 = Color(0xFF161B22)
val Surface2 = Color(0xFF1F2630)
val Accent = Color(0xFF3D8BFF)
val TextDim = Color(0xFF8B96A5)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Bg,
    surface = Surface1,
    onSurface = Color(0xFFE6EDF3),
    onBackground = Color(0xFFE6EDF3),
    surfaceVariant = Surface2,
    secondaryContainer = Color(0xFF1D3557),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, content = content)

/** Яркая рамка при фокусе — важна для управления с пульта ТВ. */
fun Modifier.tvFocus(radius: Int = 12): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(radius.dp)) else Modifier)
}
