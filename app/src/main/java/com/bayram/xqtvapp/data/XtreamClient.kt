package com.bayram.xqtvapp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class XtreamClient(
    serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val server = serverUrl.trim().trimEnd('/')

    companion object {
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
    private val client: OkHttpClient get() = sharedClient

    var lastError: String = ""
        private set
    var expiry: String = ""

    private suspend fun get(path: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$server/$path")
                .header("User-Agent", "XqTvApp/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext null
                try { JSONObject(text) } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e("Xtream", "get fail", e)
            null
        }
    }

    private suspend fun getArray(path: String): JSONArray = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$server/$path")
                .header("User-Agent", "XqTvApp/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext JSONArray()
                try {
                    val t = text.trimStart()
                    if (t.startsWith("[")) return@withContext JSONArray(t)
                    // {"epg_listings":[...]} / {"data":[...]} seklinde nesneler
                    val o = JSONObject(t)
                    for (k in listOf("epg_listings", "data", "epg", "listings", "items")) {
                        o.optJSONArray(k)?.let { return@withContext it }
                    }
                    JSONArray()
                } catch (_: Exception) { JSONArray() }
            }
        } catch (e: Exception) {
            Log.e("Xtream", "array fail", e)
            JSONArray()
        }
    }

    /**
     * Buyuk listeler icin akan (streaming) JSON okuma: tum cevabi agacta
     * tutmak yerine obje obje okur. 20-30 bin kayitli panellerde OOM'u onler.
     */
    private suspend fun getArrayStream(path: String): List<Map<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$server/$path")
                    .header("User-Agent", "XqTvApp/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body ?: return@withContext emptyList<Map<String, String>>()
                    val reader = android.util.JsonReader(body.byteStream().bufferedReader())
                    try {
                        reader.beginArray()
                        val out = mutableListOf<Map<String, String>>()
                        while (reader.hasNext()) {
                            val map = mutableMapOf<String, String>()
                            try {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val name = try {
                                        reader.nextName()
                                    } catch (_: Exception) {
                                        try { reader.skipValue() } catch (_: Exception) { }
                                        continue
                                    }
                                    try {
                                        when (reader.peek()) {
                                            android.util.JsonToken.STRING ->
                                                map[name] = reader.nextString()
                                            android.util.JsonToken.NUMBER ->
                                                map[name] = reader.nextDouble().toLong().toString()
                                            android.util.JsonToken.BOOLEAN ->
                                                map[name] = reader.nextBoolean().toString()
                                            else -> reader.skipValue()
                                        }
                                    } catch (_: Exception) {
                                        try { reader.skipValue() } catch (_: Exception) { }
                                    }
                                }
                                reader.endObject()
                            } catch (_: Exception) { }
                            if (map.isNotEmpty()) out.add(map)
                        }
                        out
                    } finally {
                        try { reader.close() } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                Log.e("Xtream", "stream fail", e)
                emptyList()
            }
        }

    private fun u() = "username=${URLEncoder.encode(username, "UTF-8")}&password=${URLEncoder.encode(password, "UTF-8")}"

    suspend fun login(): Boolean {
        lastError = ""
        val js = get("player_api.php?${u()}") ?: run { lastError = "Sunucuya ulaşılamadı"; return false }
        val info = js.optJSONObject("user_info") ?: run { lastError = "Geçersiz cevap"; return false }
        if (info.optString("auth") != "1") {
            lastError = "Kullanıcı adı/şifre hatalı"
            return false
        }
        if (info.optString("status", "Active") != "Active") {
            lastError = "Hesap aktif değil: ${info.optString("status")}"
            return false
        }
        expiry = info.optString("exp_date", "")
        return true
    }

    private suspend fun categories(action: String): Map<String, String> {
        val arr = getArrayStream("player_api.php?${u()}&action=$action")
        val map = mutableMapOf<String, String>()
        for (o in arr) {
            val id = o["category_id"] ?: continue
            if (id.isEmpty()) continue
            map[id] = o["category_name"]?.takeIf { it.isNotEmpty() } ?: "General"
        }
        return map
    }

    suspend fun liveCategories() = categories("get_live_categories")
    suspend fun vodCategories() = categories("get_vod_categories")

    suspend fun liveStreams(): List<StalkerChannel> {
        val cats = liveCategories()
        val arr = getArrayStream("player_api.php?${u()}&action=get_live_streams")
        val out = mutableListOf<StalkerChannel>()
        for (o in arr) {
            val id = o["stream_id"] ?: continue
            if (id.isEmpty()) continue
            val name = o["name"]?.takeIf { it.isNotEmpty() } ?: "CH $id"
            val logo = o["stream_icon"] ?: ""
            val cat = cats[o["category_id"]] ?: "General"
            val url = "$server/live/$username/$password/$id.m3u8"
            out.add(StalkerChannel("live_$id", name, logo, url, cat))
        }
        return out
    }

    suspend fun vodStreams(): List<StalkerChannel> {
        val cats = vodCategories()
        val arr = getArrayStream("player_api.php?${u()}&action=get_vod_streams")
        val out = mutableListOf<StalkerChannel>()
        for (o in arr) {
            val id = o["stream_id"] ?: continue
            if (id.isEmpty()) continue
            val name = o["name"]?.takeIf { it.isNotEmpty() } ?: "VOD $id"
            val logo = o["stream_icon"] ?: ""
            val cat = cats[o["category_id"]] ?: "Movies"
            val ext = o["container_extension"]?.takeIf { it.isNotEmpty() } ?: "mp4"
            val url = "$server/movie/$username/$password/$id.$ext"
            out.add(StalkerChannel("vod_$id", name, logo, url, cat))
        }
        return out
    }

    suspend fun seriesCategories() = categories("get_series_categories")

    suspend fun seriesList(): List<SeriesEntry> {
        val cats = seriesCategories()
        val arr = getArrayStream("player_api.php?${u()}&action=get_series")
        val out = mutableListOf<SeriesEntry>()
        for (o in arr) {
            val id = o["series_id"] ?: continue
            if (id.isEmpty()) continue
            out.add(
                SeriesEntry(
                    id = id,
                    name = o["name"]?.takeIf { it.isNotEmpty() } ?: "Series $id",
                    cover = o["cover"] ?: "",
                    category = cats[o["category_id"]] ?: "Series"
                )
            )
        }
        return out
    }

    suspend fun seriesEpisodes(seriesId: String): SeriesEntry? {
        val js = get("player_api.php?${u()}&action=get_series_info&series_id=$seriesId")
            ?: return null
        val info = js.optJSONObject("info") ?: return null
        val eps = js.optJSONObject("episodes") ?: return null
        val cover = info.optString("cover", "")
        val name = info.optString("name", "")
        val list = mutableListOf<EpisodeEntry>()
        val seasons = eps.keys()
        while (seasons.hasNext()) {
            val sKey = seasons.next()
            val seasonNum = sKey.toIntOrNull() ?: continue
            val arr = eps.optJSONArray(sKey) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                val ext = o.optString("container_extension", "mp4")
                val epNum = o.optString("episode_num", "${i + 1}").toIntOrNull() ?: (i + 1)
                val title = o.optString("title", "Bölüm $epNum").ifBlank { "Bölüm $epNum" }
                val info = o.optJSONObject("info")
                val plot = info?.optString("plot", "").orEmpty()
                val durSecs = info?.optString("duration", "")
                    ?.let { parseDurationSecs(it) } ?: 0L
                list.add(
                    EpisodeEntry(
                        id = id, season = seasonNum, episode = epNum,
                        title = title,
                        url = "$server/series/$username/$password/$id.$ext",
                        cover = cover, plot = plot, durationSecs = durSecs
                    )
                )
            }
        }
        list.sortWith(compareBy({ it.season }, { it.episode }))
        val seriesPlot = info.optString("plot", "")
        val seriesCast = info.optString("cast", "")
        return SeriesEntry(seriesId, name, cover, "", list, plot = seriesPlot, cast = seriesCast)
    }

    /** Film detayi: puan, konu, oyuncu kadrosu (Xtream get_vod_info). */
    suspend fun vodInfo(streamId: String): VodDetail? {
        val js = get("player_api.php?${u()}&action=get_vod_info&vod_id=$streamId")
            ?: return null
        val info = js.optJSONObject("info") ?: return null
        val md = js.optJSONObject("movie_data") ?: JSONObject()
        return VodDetail(
            name = info.optString("name", md.optString("name", "")),
            plot = info.optString("plot", md.optString("plot", "")),
            rating = info.optString("rating", md.optString("rating", "")),
            year = info.optString("releasedate", md.optString("releasedate", "")).take(4),
            genre = info.optString("genre", md.optString("genre", "")),
            duration = info.optString("duration", md.optString("duration", "")),
            cast = info.optString("actors", md.optString("actors", md.optString("cast", ""))),
            director = info.optString("director", md.optString("director", "")),
            cover = info.optString("movie_image", md.optString("movie_image", ""))
        )
    }

    /** Kisa EPG: su anki yayin (baslik, baslangic, bitis epoch sn). Yoksa null. */
    suspend fun nowPlaying(streamId: String): EpgNow? {
        val list = dayEpg(streamId)
        val now = System.currentTimeMillis() / 1000
        return list.firstOrNull { it.startEpoch <= now && now < it.endEpoch }
    }

    /** Gunluk akis listesi (rehber gorunumu icin).
     *  Once get_simple_data_table denenir (varsa en hizlisi),
     *  bos donerse get_short_epg yedegine dusulur. Kaynakta yoksa
     *  bos doner; EpgXml internet yedegini devreye sokar. */
    suspend fun dayEpg(streamId: String): List<EpgNow> {
        val first = simpleTable(streamId)
        if (first.isNotEmpty()) return first
        return shortEpg(streamId)
    }

    private suspend fun simpleTable(streamId: String): List<EpgNow> {
        val arr = getArray(
            "player_api.php?${u()}&action=get_simple_data_table&stream_id=$streamId"
        )
        val out = mutableListOf<EpgNow>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = (o.optString("start", "").ifBlank { o.optString("start_timestamp", "0") })
                .toLongOrNull() ?: 0L
            val end = (o.optString("end", "").ifBlank { o.optString("stop_timestamp",
                o.optString("end_timestamp", o.optString("stop", "0"))) })
                .toLongOrNull() ?: 0L
            val title = o.optString("title", o.optString("name", ""))
            if (start > 0 && end > start && title.isNotEmpty()) {
                out.add(EpgNow(title, start, end, o.optString("description", o.optString("desc", ""))))
            }
        }
        return out.sortedBy { it.startEpoch }
    }

    /** Yedek EPG ucu: get_short_epg -> {"epg_listings":[{title,start,end,description}]}.
     *  Bazi panellerde start/end "2024-.. ..:..:.." ya da epoch string gelir. */
    private suspend fun shortEpg(streamId: String): List<EpgNow> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$server/player_api.php?${u()}&action=get_short_epg&stream_id=$streamId")
                .header("User-Agent", "XqTvApp/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext emptyList()
                val root = try { JSONObject(text.trimStart()) } catch (_: Exception) { return@withContext emptyList<EpgNow>() }
                val arr = root.optJSONArray("epg_listings") ?: return@withContext emptyList<EpgNow>()
                val out = mutableListOf<EpgNow>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val start = parseEpgTs(o.optString("start", o.optString("start_timestamp", "")))
                    val end = parseEpgTs(o.optString("end", o.optString("stop_timestamp",
                        o.optString("stop", o.optString("end_timestamp", "")))))
                    // short_epg bazen stop yerine start+duration verir
                    val stop2 = if (end <= start) {
                        val dur = o.optString("duration", "").toLongOrNull() ?: 0L
                        if (dur > 0) start + dur else 0L
                    } else end
                    val title = o.optString("title", o.optString("name", ""))
                        .ifBlank { "Program" }
                    if (start > 0 && stop2 > start) {
                        out.add(EpgNow(title, start, stop2,
                            o.optString("description", o.optString("desc", ""))))
                    }
                }
                out.sortedBy { it.startEpoch }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseEpgTs(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return 0L
        s.toLongOrNull()?.let { v ->
            // ms gelirse sn'ye indir
            return if (v > 41024448000L) v / 1000 else v
        }
        return try {
            val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
            for (f in fmts) {
                try {
                    val dt = java.text.SimpleDateFormat(f, java.util.Locale.US)
                    dt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val t = dt.parse(s)?.time?.div(1000) ?: 0L
                    if (t > 0) return t
                } catch (_: Exception) { }
            }
            0L
        } catch (_: Exception) { 0L }
    }
}

data class EpgNow(
    val title: String,
    val startEpoch: Long,
    val endEpoch: Long,
    val desc: String = ""
)

/** "2700" / "45:00" / "00:45:00" -> saniye. */
fun parseDurationSecs(raw: String): Long {
    val s = raw.trim()
    if (s.isEmpty()) return 0L
    s.toLongOrNull()?.let { return it }
    val parts = s.split(":").mapNotNull { it.toLongOrNull() }
    if (parts.isEmpty()) return 0L
    var total = 0L
    for (p in parts) total = total * 60 + p
    return total
}
