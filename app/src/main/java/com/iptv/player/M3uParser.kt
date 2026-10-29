package com.iptv.player

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    data class Result(val channels: List<Channel>, val epgUrl: String)

    fun parse(text: String): Result {
        val channels = ArrayList<Channel>()
        var epgUrl = ""
        var pending: Map<String, String>? = null
        var pendingName = ""
        var groupOverride = ""

        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val a = attrs(line)
                    epgUrl = a["url-tvg"] ?: a["x-tvg-url"] ?: a["tvg-url"] ?: ""
                    // Несколько адресов через запятую — берём первый
                    epgUrl = epgUrl.substringBefore(',').trim()
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = attrs(line)
                    pendingName = extractName(line)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    groupOverride = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                else -> {
                    val a = pending
                    if (a != null) {
                        val name = pendingName.ifBlank { a["tvg-name"] ?: line }
                        val group = (a["group-title"] ?: "").ifBlank { groupOverride }
                        channels += Channel(
                            name = name,
                            url = line,
                            group = group.ifBlank { "Без группы" },
                            logo = a["tvg-logo"] ?: "",
                            tvgId = a["tvg-id"] ?: "",
                            tvgName = a["tvg-name"] ?: "",
                            catchup = (a["catchup"] ?: a["catchup-type"] ?: "").lowercase(),
                            catchupDays = (a["catchup-days"] ?: a["tvg-rec"] ?: "").toIntOrNull() ?: 0,
                            catchupSource = a["catchup-source"] ?: "",
                        )
                    }
                    pending = null
                    pendingName = ""
                    groupOverride = ""
                }
            }
        }
        return Result(channels, epgUrl)
    }

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun extractName(line: String): String {
        val lastQuote = line.lastIndexOf('"')
        val from = if (lastQuote >= 0) lastQuote else line.indexOf(':').coerceAtLeast(0)
        val comma = line.indexOf(',', from)
        return if (comma >= 0) line.substring(comma + 1).trim() else ""
    }
}
