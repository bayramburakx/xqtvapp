package com.bayram.xqtvapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object M3uParser {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(6, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "XqTvApp/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            resp.body?.string() ?: throw Exception("Boş cevap")
        }
    }

    fun parse(text: String): List<StalkerChannel> {
        val out = mutableListOf<StalkerChannel>()
        var name = ""
        var logo = ""
        var group = "General"
        var id = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                name = line.substringAfterLast(",").trim().ifBlank { "Unknown" }
                logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
                group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                    ?.ifBlank { "General" } ?: "General"
                id = Regex("tvg-id=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                if (name.isEmpty()) name = line
                val key = (id.ifBlank { line }).hashCode().toString()
                val isVod = line.contains(".mp4", true) || line.contains(".mkv", true) ||
                        line.contains("/movie/", true) || line.contains("/vod/", true)
                out.add(
                    StalkerChannel(
                        key, name, logo, line,
                        if (isVod) "VOD" else group.ifBlank { "General" }
                    )
                )
                name = ""; logo = ""; group = "General"; id = ""
            }
        }
        return out
    }
}
