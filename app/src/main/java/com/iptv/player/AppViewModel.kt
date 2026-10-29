package com.iptv.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.GZIPInputStream

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var playlists by mutableStateOf(store.loadPlaylists())
        private set
    var currentId by mutableStateOf(store.currentPlaylistId)
        private set
    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set
    var epg by mutableStateOf(EpgData())
        private set
    var favorites by mutableStateOf(store.loadFavorites())
        private set
    var history by mutableStateOf(store.loadHistory())
        private set
    var loading by mutableStateOf(false)
        private set
    var epgLoading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    var settings by mutableStateOf(store.loadSettings())
        private set

    fun updateSettings(s: Settings) {
        settings = s
        store.saveSettings(s)
    }

    val current: Playlist? get() = playlists.firstOrNull { it.id == currentId }
    private var loadJob: Job? = null

    init {
        if (current == null && playlists.isNotEmpty()) currentId = playlists.first().id
        current?.let { open(it, forceRefresh = false) }
    }

    // ---------- Плейлисты ----------
    fun addUrl(name: String, url: String, epgUrl: String) {
        val p = Playlist(UUID.randomUUID().toString(), name.ifBlank { "Плейлист ${playlists.size + 1}" },
            url.trim(), true, epgUrl.trim())
        playlists = playlists + p
        store.savePlaylists(playlists)
        select(p.id)
    }

    fun addFile(name: String, text: String) {
        val p = Playlist(UUID.randomUUID().toString(), name.ifBlank { "Плейлист ${playlists.size + 1}" }, "", false)
        store.saveText(p.id, text)
        playlists = playlists + p
        store.savePlaylists(playlists)
        select(p.id)
    }

    fun rename(id: String, newName: String) {
        if (newName.isBlank()) return
        playlists = playlists.map { if (it.id == id) it.copy(name = newName.trim()) else it }
        store.savePlaylists(playlists)
    }

    fun setEpgUrl(id: String, epgUrl: String) {
        playlists = playlists.map { if (it.id == id) it.copy(epgUrl = epgUrl.trim()) else it }
        store.savePlaylists(playlists)
        if (id == currentId) current?.let { open(it, forceRefresh = false) }
    }

    private var allChannels: List<Channel> = emptyList()

    private fun filterChannels(list: List<Channel>, filter: String): List<Channel> {
        val words = filter.split(',', ';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filterNot { ch ->
            val n = ch.name.lowercase()
            val g = ch.group.lowercase()
            words.any { n.contains(it) || g.contains(it) }
        }
    }

    /** Скрыть каналы, в названии/группе которых есть указанные слова (через запятую). */
    fun setFilter(id: String, words: String) {
        playlists = playlists.map { if (it.id == id) it.copy(filter = words.trim()) else it }
        store.savePlaylists(playlists)
        if (id == currentId) {
            channels = filterChannels(allChannels, words)
            val hidden = allChannels.size - channels.size
            message = if (words.isBlank()) "Фильтр выключен" else "Скрыто каналов: $hidden"
        }
    }

    fun delete(id: String) {
        playlists = playlists.filter { it.id != id }
        store.savePlaylists(playlists)
        store.deleteText(id)
        if (currentId == id) {
            val next = playlists.firstOrNull()
            if (next != null) select(next.id) else {
                currentId = ""; store.currentPlaylistId = ""; channels = emptyList(); epg = EpgData()
            }
        }
    }

    fun select(id: String) {
        currentId = id
        store.currentPlaylistId = id
        current?.let { open(it, forceRefresh = false) }
    }

    fun refresh() { current?.let { open(it, forceRefresh = true) } }

    private fun open(p: Playlist, forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true
            epg = EpgData()
            try {
                val cached = store.loadText(p.id)
                val text = if (p.isUrl) {
                    if (cached != null && !forceRefresh) cached
                    else withContext(Dispatchers.IO) { download(p.source).also { store.saveText(p.id, it) } }
                } else cached ?: error("Файл плейлиста не найден")
                val parsed = withContext(Dispatchers.Default) { M3uParser.parse(text) }
                allChannels = parsed.channels
                channels = filterChannels(allChannels, current?.filter ?: p.filter)
                if (allChannels.isEmpty()) message = "В плейлисте нет каналов"
                loading = false
                loadEpg(p, parsed.epgUrl)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                loading = false
                message = "Ошибка загрузки: ${e.message}"
            }
        }
    }

    private suspend fun loadEpg(p: Playlist, fromM3u: String) {
        val url = p.epgUrl.ifBlank { fromM3u }
        if (url.isBlank()) return
        epgLoading = true
        try {
            val ids = channels.mapNotNull { it.tvgId.takeIf(String::isNotBlank) }.toSet()
            val names = channels.flatMap { listOf(it.name.lowercase(), it.tvgName.lowercase()) }
                .filter { it.isNotBlank() }.toSet()
            epg = withContext(Dispatchers.IO) {
                openStream(url).use { XmltvParser.parse(it, ids, names) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            message = "Не удалось загрузить программу передач"
        } finally {
            epgLoading = false
        }
    }

    // ---------- Избранное ----------
    fun isFavorite(ch: Channel) = favorites.any { it.key == ch.key }

    fun toggleFavorite(ch: Channel) {
        favorites = if (isFavorite(ch)) favorites.filter { it.key != ch.key } else favorites + ch
        store.saveFavorites(favorites)
    }

    // ---------- Воспроизведение / история ----------
    /** Список, по которому переключаются каналы кнопками вверх/вниз в плеере. */
    var zapList: List<Channel> = emptyList()

    fun play(ch: Channel, list: List<Channel>? = null) {
        if (list != null) zapList = list
        nowPlaying = NowPlaying(ch, ch.url)
        addHistory(ch, null)
    }

    fun playArchive(ch: Channel, p: Program) {
        nowPlaying = NowPlaying(ch, Catchup.build(ch, p), p, isArchive = true)
        addHistory(ch, p)
    }

    fun stop() { nowPlaying = null }

    /** Переключение на соседний канал в переданном списке. */
    fun zap(delta: Int) {
        val list = zapList.ifEmpty { channels }
        val cur = nowPlaying?.channel ?: return
        val i = list.indexOfFirst { it.key == cur.key }
        if (i < 0 || list.isEmpty()) return
        play(list[(i + delta + list.size) % list.size])
    }

    private fun addHistory(ch: Channel, p: Program?) {
        val item = HistoryItem(ch.key, ch.name, ch.logo, System.currentTimeMillis(),
            p?.title.orEmpty(), p?.start ?: 0, p?.stop ?: 0)
        history = (listOf(item) + history.filterNot {
            it.channelKey == item.channelKey && it.programStart == item.programStart
        }).take(200)
        store.saveHistory(history)
    }

    fun clearHistory() { history = emptyList(); store.saveHistory(history) }

    fun removeHistory(item: HistoryItem) {
        history = history.filter { it !== item }
        store.saveHistory(history)
    }

    /** Найти канал по ключу: сначала в текущем плейлисте, затем в избранном. */
    fun findChannel(key: String): Channel? =
        channels.firstOrNull { it.key == key } ?: favorites.firstOrNull { it.key == key }

    // ---------- Сеть ----------
    private fun openStream(url: String): java.io.InputStream {
        var u = url.trim()
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) IPTVPlayer/1.0")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: error("Пустой редирект")
                u = URL(URL(u), loc).toString()
                c.disconnect()
            } else if (code in 200..299) return c.inputStream
            else error("HTTP $code")
        }
        error("Слишком много перенаправлений")
    }

    private fun download(url: String): String {
        val bytes = openStream(url).use { it.readBytes() }
        val data = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() } else bytes
        return String(data, Charsets.UTF_8)
    }
}
