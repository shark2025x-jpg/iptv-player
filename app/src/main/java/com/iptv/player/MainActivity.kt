package com.iptv.player

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.iptv.player.ui.App
import com.iptv.player.ui.IptvTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) importFile(uri)
            }
            IptvTheme {
                App(vm, onPickFile = { picker.launch(arrayOf("*/*")) })
            }
        }
    }

    private fun importFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Не удалось открыть файл")
            val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            }?.substringBeforeLast('.') ?: ""
            if (!text.contains("#EXTM3U") && !text.contains("#EXTINF")) {
                vm.message = "Это не похоже на M3U плейлист"
                return
            }
            vm.addFile(name, text)
        } catch (e: Exception) {
            vm.message = "Ошибка чтения файла: ${e.message}"
        }
    }
}
