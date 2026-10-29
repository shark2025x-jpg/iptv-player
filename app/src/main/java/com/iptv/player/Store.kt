package com.iptv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Простое хранилище на SharedPreferences + файлы (без Room/kapt). */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val dir = File(ctx.filesDir, "playlists").apply { mkdirs() }

    // ---------- Плейлисты ----------
    fun loadPlaylists(): List<Playlist> {
        val arr = JSONArray(prefs.getString("playlists", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                source = o.optString("source"),
                isUrl = o.optBoolean("isUrl", true),
                epgUrl = o.optString("epgUrl"),
                filter = o.optString("filter"),
            )
        }
    }

    fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("source", it.source)
                .put("isUrl", it.isUrl).put("epgUrl", it.epgUrl).put("filter", it.filter))
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    var currentPlaylistId: String
        get() = prefs.getString("current", "") ?: ""
        set(v) = prefs.edit().putString("current", v).apply()

    fun saveText(id: String, text: String) = File(dir, "$id.m3u").writeText(text)
    fun loadText(id: String): String? = File(dir, "$id.m3u").takeIf { it.exists() }?.readText()
    fun deleteText(id: String) { File(dir, "$id.m3u").delete() }

    // ---------- Настройки ----------
    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            ambient = prefs.getBoolean("s_ambient", d.ambient),
            ambientStrength = prefs.getFloat("s_ambientStrength", d.ambientStrength),
            preset = prefs.getString("s_preset", d.preset) ?: d.preset,
            brightness = prefs.getFloat("s_brightness", d.brightness),
            contrast = prefs.getFloat("s_contrast", d.contrast),
            saturation = prefs.getFloat("s_saturation", d.saturation),
            maxQuality = prefs.getBoolean("s_maxQuality", d.maxQuality),
            overlaySec = prefs.getInt("s_overlaySec", d.overlaySec),
        )
    }

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putBoolean("s_ambient", s.ambient).putFloat("s_ambientStrength", s.ambientStrength)
            .putString("s_preset", s.preset).putFloat("s_brightness", s.brightness)
            .putFloat("s_contrast", s.contrast).putFloat("s_saturation", s.saturation)
            .putBoolean("s_maxQuality", s.maxQuality).putInt("s_overlaySec", s.overlaySec)
            .apply()
    }

    // ---------- Избранное ----------
    fun loadFavorites(): List<Channel> = readChannels(prefs.getString("favs", "[]") ?: "[]")

    fun saveFavorites(list: List<Channel>) {
        prefs.edit().putString("favs", writeChannels(list)).apply()
    }

    // ---------- История ----------
    fun loadHistory(): List<HistoryItem> {
        val arr = JSONArray(prefs.getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(
                channelKey = o.getString("key"),
                name = o.getString("name"),
                logo = o.optString("logo"),
                time = o.getLong("time"),
                programTitle = o.optString("pt"),
                programStart = o.optLong("ps"),
                programStop = o.optLong("pe"),
            )
        }
    }

    fun saveHistory(list: List<HistoryItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("key", it.channelKey).put("name", it.name).put("logo", it.logo)
                .put("time", it.time).put("pt", it.programTitle).put("ps", it.programStart)
                .put("pe", it.programStop))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun writeChannels(list: List<Channel>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("url", it.url).put("group", it.group)
                .put("logo", it.logo).put("tvgId", it.tvgId).put("tvgName", it.tvgName)
                .put("catchup", it.catchup).put("days", it.catchupDays).put("csrc", it.catchupSource))
        }
        return arr.toString()
    }

    private fun readChannels(json: String): List<Channel> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Channel(
                name = o.getString("name"), url = o.getString("url"), group = o.optString("group"),
                logo = o.optString("logo"), tvgId = o.optString("tvgId"), tvgName = o.optString("tvgName"),
                catchup = o.optString("catchup"), catchupDays = o.optInt("days"),
                catchupSource = o.optString("csrc"),
            )
        }
    }
}
