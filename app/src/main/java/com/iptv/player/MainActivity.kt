package com.iptv.player

import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
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
        chooseLauncherIcon()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) importFile(uri)
            }
            IptvTheme {
                App(vm, onPickFile = {
                    try {
                        picker.launch(arrayOf("*/*"))
                    } catch (e: Exception) {
                        // На многих ТВ-приставках нет системного выбора файлов
                        vm.message = "На этом устройстве нет выбора файлов. Добавьте плейлист по ссылке."
                    }
                })
            }
        }
    }

    /** Оставляет ровно одну иконку: для ТВ — с баннером, для телефона/обычной приставки — обычную. */
    private fun chooseLauncherIcon() {
        try {
            val ui = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val isTv = ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            val phone = ComponentName(this, "com.iptv.player.LauncherPhone")
            val tv = ComponentName(this, "com.iptv.player.LauncherTv")
            val wantTv = isTv
            fun setIcon(c: ComponentName, enable: Boolean) {
                val st = packageManager.getComponentEnabledSetting(c)
                val enabledNow = when (st) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                    else -> c == phone // значение по умолчанию из манифеста
                }
                if (enabledNow != enable) {
                    packageManager.setComponentEnabledSetting(
                        c,
                        if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            setIcon(tv, wantTv)
            setIcon(phone, !wantTv)
        } catch (_: Exception) {
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
