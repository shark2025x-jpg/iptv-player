package com.iptv.player

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

object XmltvParser {

    private const val DAY = 24 * 3600 * 1000L

    /**
     * Разбирает XMLTV. Хранит только нужные каналы и окно времени
     * (7 дней назад … 3 дня вперёд), чтобы не раздувать память.
     */
    fun parse(input: InputStream, wantedIds: Set<String>, wantedNames: Set<String>): EpgData {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        val stream: InputStream =
            if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered

        val parser = Xml.newPullParser()
        parser.setInput(stream, null)

        val now = System.currentTimeMillis()
        val minT = now - 7 * DAY
        val maxT = now + 3 * DAY

        val nameToId = HashMap<String, String>()
        val matchedIds = HashSet<String>()
        val byId = HashMap<String, MutableList<Program>>()

        var event = parser.eventType
        var chId = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> chId = parser.getAttributeValue(null, "id") ?: ""
                    "display-name" -> {
                        val n = parser.nextText().trim().lowercase()
                        if (chId.isNotEmpty() && n.isNotEmpty() && n in wantedNames) {
                            nameToId[n] = chId
                            matchedIds.add(chId)
                        }
                    }
                    "programme" -> {
                        val ch = parser.getAttributeValue(null, "channel") ?: ""
                        val start = parseTime(parser.getAttributeValue(null, "start"))
                        val stop = parseTime(parser.getAttributeValue(null, "stop"))
                        var title = ""
                        var desc = ""
                        while (true) {
                            val e = parser.next()
                            if (e == XmlPullParser.END_TAG && parser.name == "programme") break
                            if (e == XmlPullParser.END_DOCUMENT) break
                            if (e == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "title" -> if (title.isEmpty()) title = parser.nextText().trim()
                                    "desc" -> if (desc.isEmpty()) desc = parser.nextText().trim()
                                }
                            }
                        }
                        if (start > 0 && stop > start && stop >= minT && start <= maxT) {
                            val keep = ch in wantedIds || ch in matchedIds
                            if (keep) byId.getOrPut(ch) { ArrayList() }.add(Program(start, stop, title, desc))
                        }
                    }
                }
            }
            event = parser.next()
        }
        byId.values.forEach { l -> l.sortBy { it.start } }
        return EpgData(byId, nameToId)
    }

    private val fmtTz = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    }
    private val fmtNoTz = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() =
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return try {
            val t = s.trim()
            if (t.length > 14) fmtTz.get()!!.parse(t)!!.time
            else fmtNoTz.get()!!.parse(t.take(14))!!.time
        } catch (_: Exception) {
            0
        }
    }
}
