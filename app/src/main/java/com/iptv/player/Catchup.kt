package com.iptv.player

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Построение ссылки на архивную передачу (catchup / timeshift). */
object Catchup {

    fun canPlay(ch: Channel, p: Program, now: Long = System.currentTimeMillis()): Boolean {
        if (!ch.hasArchive) return false
        if (p.start > now) return false
        return p.start >= now - ch.catchupDays * 24L * 3600 * 1000
    }

    fun build(ch: Channel, p: Program, now: Long = System.currentTimeMillis()): String {
        val startS = p.start / 1000
        val endS = p.stop / 1000
        val nowS = now / 1000
        val dur = endS - startS
        val base = ch.url

        fun fill(tpl: String): String {
            var s = tpl
            // ${(b)yyyyMMddHHmmss} / ${(e)...} / ${(utc)...}
            s = Regex("""\$\{\((b|e|utc|start|end)\)([^}]*)\}""").replace(s) { m ->
                val t = if (m.groupValues[1] == "e" || m.groupValues[1] == "end") p.stop else p.start
                val f = SimpleDateFormat(m.groupValues[2], Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                f.format(Date(t))
            }
            val map = mapOf(
                "start" to startS, "utc" to startS, "timestamp" to startS,
                "end" to endS, "utcend" to endS,
                "now" to nowS, "lutc" to nowS,
                "offset" to (nowS - startS), "duration" to dur,
            )
            for ((k, v) in map) s = s.replace("\${$k}", v.toString()).replace("{$k}", v.toString())
            return s
        }

        return when (ch.catchup) {
            "append" -> {
                val src = ch.catchupSource.ifBlank { "?utc={utc}&lutc={lutc}" }
                base + fill(src)
            }
            "default" -> if (ch.catchupSource.isNotBlank()) fill(ch.catchupSource) else shift(base, startS, nowS)
            "flussonic", "fs", "flussonic-ts" -> {
                when {
                    base.contains("/index.m3u8") -> base.replace("/index.m3u8", "/video-$startS-$dur.m3u8")
                    base.contains("/mpegts") -> base.replace("/mpegts", "/archive-$startS-$dur.ts")
                    else -> shift(base, startS, nowS)
                }
            }
            else -> shift(base, startS, nowS) // "shift" и всё прочее
        }
    }

    private fun shift(base: String, startS: Long, nowS: Long): String {
        val sep = if (base.contains('?')) "&" else "?"
        return "$base${sep}utc=$startS&lutc=$nowS"
    }
}
