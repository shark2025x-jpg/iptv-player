package com.iptv.player.ui

import android.view.KeyEvent as AKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.iptv.player.AppViewModel
import com.iptv.player.NowPlaying
import kotlinx.coroutines.delay

private data class QualityOption(val label: String, val group: androidx.media3.common.Tracks.Group, val index: Int)

private fun heightLabel(h: Int, w: Int): String {
    val tag = when {
        h >= 4320 || w >= 7680 -> " (8K)"
        h >= 2160 || w >= 3840 -> " (4K)"
        h >= 1440 -> " (2K)"
        h >= 1080 -> " (Full HD)"
        h >= 720 -> " (HD)"
        else -> ""
    }
    return "${h}p$tag"
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, np: NowPlaying, onGuide: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember {
        // Без ограничений по разрешению/битрейту: качество ограничивает только поток и устройство
        val selector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(ctx).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setViewportSizeToPhysicalDisplaySize(ctx, false)
                .build()
        }
        // Увеличенный буфер для высоких битрейтов (4K/8K)
        val memClass = (ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager).memoryClass
        val bufBytes = (memClass.toLong() * 1024 * 1024 / 4).coerceIn(16L * 1024 * 1024, 256L * 1024 * 1024).toInt()
        val maxMs = if (memClass >= 256) 90_000 else 30_000
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, maxMs, 2_500, 5_000)
            .setTargetBufferBytes(bufBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        ExoPlayer.Builder(ctx).setTrackSelector(selector).setLoadControl(load).build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var tracks by remember { mutableStateOf(androidx.media3.common.Tracks.EMPTY) }
    var selectedQuality by remember { mutableStateOf<String?>(null) } // null = авто
    var qualityMenu by remember { mutableStateOf(false) }
    val qualities = remember(tracks) {
        val out = ArrayList<QualityOption>()
        tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }.forEach { g ->
            for (i in 0 until g.length) {
                if (g.isTrackSupported(i)) {
                    val f = g.getTrackFormat(i)
                    if (f.height > 0) out += QualityOption(heightLabel(f.height, f.width), g, i)
                }
            }
        }
        // уникальные по высоте, максимально качественные сверху
        out.groupBy { it.label }.map { (_, v) -> v.maxBy { it.group.getTrackFormat(it.index).bitrate } }
            .sortedByDescending { it.group.getTrackFormat(it.index).height }
    }

    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentNp by androidx.compose.runtime.rememberUpdatedState(np)
    val triedHlsFlag = remember { booleanArrayOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var overlay by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    fun showOverlay() { overlay = true; overlayTick = System.currentTimeMillis() }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) error = null
            }
            override fun onIsPlayingChanged(p: Boolean) { isPlaying = p }
            override fun onTracksChanged(t: androidx.media3.common.Tracks) { tracks = t }
            override fun onPlayerError(e: PlaybackException) {
                // Ссылки без расширения часто являются HLS — пробуем один раз как HLS
                if (!triedHlsFlag[0]) {
                    triedHlsFlag[0] = true
                    player.setMediaItem(
                        MediaItem.Builder().setUri(currentNp.playUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                    )
                    player.prepare()
                } else {
                    error = "Не удалось воспроизвести поток"
                    buffering = false
                    showOverlay()
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(np.playUrl) {
        error = null
        triedHlsFlag[0] = false
        buffering = true
        selectedQuality = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO).build()
        player.setMediaItem(MediaItem.fromUri(np.playUrl))
        player.prepare()
        player.play()
        showOverlay()
    }

    // Автоскрытие панели
    LaunchedEffect(overlay, overlayTick, error) {
        if (overlay && error == null) { delay(vm.settings.overlaySec * 1000L); overlay = false }
    }
    // Позиция для архива
    LaunchedEffect(np.isArchive) {
        while (np.isArchive) {
            pos = player.currentPosition.coerceAtLeast(0)
            dur = player.duration.takeIf { it > 0 } ?: 0
            delay(500)
        }
    }

    LaunchedEffect(vm.settings.maxQuality) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(vm.settings.maxQuality).build()
    }

    var picDialog by remember { mutableStateOf(false) }

    BackHandler { vm.stop() }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_CHANNEL_UP, AKeyEvent.KEYCODE_PAGE_UP -> { vm.zap(-1); true }
                    AKeyEvent.KEYCODE_CHANNEL_DOWN, AKeyEvent.KEYCODE_PAGE_DOWN -> { vm.zap(1); true }
                    AKeyEvent.KEYCODE_MENU, AKeyEvent.KEYCODE_INFO, AKeyEvent.KEYCODE_GUIDE -> { onGuide(); true }
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_SPACE -> {
                        if (player.isPlaying) player.pause() else player.play(); showOverlay(); true
                    }
                    AKeyEvent.KEYCODE_DPAD_UP -> if (!overlay) { vm.zap(-1); true } else { showOverlay(); false }
                    AKeyEvent.KEYCODE_DPAD_DOWN -> if (!overlay) { vm.zap(1); true } else { showOverlay(); false }
                    AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_MEDIA_REWIND ->
                        if (np.isArchive) { player.seekTo((player.currentPosition - 15_000).coerceAtLeast(0)); showOverlay(); true } else false
                    AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                        if (np.isArchive) { player.seekTo(player.currentPosition + 15_000); showOverlay(); true } else false
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER ->
                        if (!overlay) { showOverlay(); true } else false
                    else -> false
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (overlay) overlay = false else showOverlay()
            },
    ) {
        val st = vm.settings
        val useTexture = st.needsTexture
        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

        if (st.ambient) AmbientGlow(playerViewRef, st.ambientStrength)

        if (picDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { picDialog = false }) {
                Column(
                    Modifier.fillMaxWidth().background(Surface1, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 8.dp),
                ) {
                    Text("Картинка и подсветка", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    SettingsContent(vm, onlyPicture = true)
                    androidx.compose.material3.TextButton(
                        onClick = { picDialog = false },
                        modifier = Modifier.align(Alignment.End).padding(end = 8.dp).tvFocus(20),
                    ) { Text("Закрыть") }
                }
            }
        }

        androidx.compose.runtime.key(useTexture) {
            AndroidView(
                modifier = Modifier.align(Alignment.Center).fillMaxSize(if (st.ambient) 0.9f else 1f),
                factory = { c ->
                    val layout = if (useTexture) com.iptv.player.R.layout.player_view_texture
                    else com.iptv.player.R.layout.player_view_surface
                    (android.view.LayoutInflater.from(c).inflate(layout, null) as PlayerView).apply {
                        this.player = player
                        playerViewRef = this
                    }
                },
                update = { pv ->
                    val tv = pv.videoSurfaceView as? android.view.TextureView
                    if (tv != null) {
                        if (st.hasPictureFx) {
                            tv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, android.graphics.Paint().apply {
                                colorFilter = android.graphics.ColorMatrixColorFilter(
                                    pictureMatrix(st.brightness, st.contrast, st.saturation))
                            })
                        } else tv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    }
                },
            )
        }

        if (buffering && error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
        }
        error?.let {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Выберите другой канал или повторите попытку", color = TextDim, fontSize = 13.sp)
                IconButton(onClick = {
                    error = null; buffering = true; triedHlsFlag[0] = false
                    player.setMediaItem(MediaItem.fromUri(np.playUrl)); player.prepare(); player.play()
                }, modifier = Modifier.tvFocus(24)) { Icon(Icons.Filled.PlayArrow, "Повторить", tint = Color.White) }
            }
        }

        if (overlay) {
            // верхняя панель
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color(0xDD000000), Color.Transparent)))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.stop() }, modifier = Modifier.tvFocus(24)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White)
                }
                Logo(np.channel.logo, np.channel.name, 40.dp)
                val now = System.currentTimeMillis()
                val title = np.program?.title
                    ?: vm.epg.forChannel(np.channel).firstOrNull { now in it.start until it.stop }?.title
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(np.channel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        np.isArchive -> "Архив · ${title.orEmpty()}"
                        title != null -> title
                        else -> ""
                    }
                    if (sub.isNotBlank()) Text(sub, color = Color(0xFFB8C2CF), fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { picDialog = true; showOverlay() }, modifier = Modifier.tvFocus(24)) {
                    Icon(Icons.Filled.Tune, "Картинка и подсветка", tint = Color.White)
                }
                Box {
                    androidx.compose.material3.TextButton(
                        onClick = { qualityMenu = true; showOverlay() },
                        modifier = Modifier.tvFocus(20),
                    ) {
                        Icon(Icons.Filled.HighQuality, "Качество", tint = Color.White)
                        Text("  " + (selectedQuality ?: "Авто"), color = Color.White, fontSize = 13.sp)
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = qualityMenu, onDismissRequest = { qualityMenu = false },
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(if (selectedQuality == null) "✓ Авто (адаптивное)" else "Авто (адаптивное)") },
                            onClick = {
                                selectedQuality = null; qualityMenu = false
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO).build()
                            },
                        )
                        qualities.forEach { q ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (selectedQuality == q.label) "✓ ${q.label}" else q.label) },
                                onClick = {
                                    selectedQuality = q.label; qualityMenu = false
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setOverrideForType(
                                            androidx.media3.common.TrackSelectionOverride(q.group.mediaTrackGroup, q.index)
                                        ).build()
                                },
                            )
                        }
                        if (qualities.isEmpty()) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Поток одного качества", color = TextDim) }, onClick = { qualityMenu = false },
                            )
                        }
                    }
                }
                IconButton(onClick = { vm.zap(-1) }, modifier = Modifier.tvFocus(24)) {
                    Icon(Icons.Filled.SkipPrevious, "Предыдущий", tint = Color.White)
                }
                IconButton(onClick = { vm.zap(1) }, modifier = Modifier.tvFocus(24)) {
                    Icon(Icons.Filled.SkipNext, "Следующий", tint = Color.White)
                }
                IconButton(onClick = onGuide, modifier = Modifier.tvFocus(24)) {
                    Icon(Icons.Filled.Schedule, "Программа", tint = Color.White)
                }
                val fav = vm.isFavorite(np.channel)
                IconButton(onClick = { vm.toggleFavorite(np.channel) }, modifier = Modifier.tvFocus(24)) {
                    Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Избранное",
                        tint = if (fav) Color(0xFFFFC857) else Color.White)
                }
            }
            // нижняя панель для архива
            if (np.isArchive) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play(); showOverlay()
                    }, modifier = Modifier.tvFocus(24)) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Пауза", tint = Color.White)
                    }
                    Text(fmtDur(pos), color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                        onValueChange = { if (dur > 0) { player.seekTo((it * dur).toLong()); showOverlay() } },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(fmtDur(dur), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun fmtDur(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** Матрица цветокоррекции: яркость / контраст / насыщенность. */
private fun pictureMatrix(brightness: Float, contrast: Float, saturation: Float): android.graphics.ColorMatrix {
    val m = android.graphics.ColorMatrix()
    m.setSaturation(saturation)
    val t = (0.5f * (1f - contrast) + brightness) * 255f
    val c = android.graphics.ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, t,
        0f, contrast, 0f, 0f, t,
        0f, 0f, contrast, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    ))
    m.postConcat(c)
    return m
}

/** Усреднённые цвета левого/правого/верхнего/нижнего краёв кадра. */
private fun edgeColors(bmp: android.graphics.Bitmap): List<Color> {
    val w = bmp.width
    val h = bmp.height
    fun avg(x0: Int, x1: Int, y0: Int, y1: Int): Color {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (x in x0 until x1) for (y in y0 until y1) {
            val p = bmp.getPixel(x, y)
            r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p); n++
        }
        if (n == 0) return Color.Black
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)   // чуть насыщеннее
        hsv[2] = (hsv[2] * 1.15f).coerceAtMost(1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
    val ex = (w / 10).coerceAtLeast(2)
    val ey = (h / 8).coerceAtLeast(2)
    return listOf(
        avg(0, ex, 0, h), avg(w - ex, w, 0, h),
        avg(0, w, 0, ey), avg(0, w, h - ey, h),
    )
}

/** Программная подсветка: цвета краёв видео мягко «растекаются» по экрану. */
@Composable
private fun AmbientGlow(pv: PlayerView?, strength: Float) {
    var colors by remember { mutableStateOf(List(4) { Color.Black }) }
    LaunchedEffect(pv) {
        while (true) {
            val tv = pv?.videoSurfaceView as? android.view.TextureView
            if (tv != null && tv.isAvailable) {
                val bmp = try { tv.getBitmap(64, 36) } catch (_: Exception) { null }
                if (bmp != null) {
                    colors = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        edgeColors(bmp).also { bmp.recycle() }
                    }
                }
            }
            delay(300)
        }
    }
    val spec = androidx.compose.animation.core.tween<Color>(500)
    val l by androidx.compose.animation.animateColorAsState(colors[0], spec, label = "l")
    val r by androidx.compose.animation.animateColorAsState(colors[1], spec, label = "r")
    val t by androidx.compose.animation.animateColorAsState(colors[2], spec, label = "t")
    val b by androidx.compose.animation.animateColorAsState(colors[3], spec, label = "b")
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val a = strength
        drawRect(Brush.horizontalGradient(listOf(l.copy(alpha = a), Color.Transparent), 0f, w * 0.5f),
            size = androidx.compose.ui.geometry.Size(w * 0.5f, h))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, r.copy(alpha = a)), w * 0.5f, w),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.5f, 0f),
            size = androidx.compose.ui.geometry.Size(w * 0.5f, h))
        drawRect(Brush.verticalGradient(listOf(t.copy(alpha = a), Color.Transparent), 0f, h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.5f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, b.copy(alpha = a)), h * 0.5f, h),
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.5f))
    }
}
