package com.bayram.xqtvapp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class StalkerClient(
    private var portalUrl: String,
    private var mac: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var token: String = ""
    var lastError: String = ""
        private set

    private fun base(): String {
        var u = portalUrl.trim()
        if (!u.endsWith("/")) u += "/"
        return u
    }

    private fun shortId(): String = mac.replace(":", "").uppercase().takeLast(12)

    private fun magHeaders(): Map<String, String> {
        val ids = shortId()
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp ver: 4 rev: 2721 Safari/533.3",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With" to "XMLHttpRequest",
            "X-User-Agent" to "Model: MAG254; Link: WiFi",
            "Referer" to base(),
            "Cookie" to "mac=$mac; stb_lang=en; timezone=Europe%2FIstanbul; " +
                    "serial_number=$ids; device_id=$ids; device_id2=$ids"
        )
    }

    private fun handshakeParams(stepToken: String): String {
        val ids = shortId()
        val sig = UUID.randomUUID().toString().replace("-", "").take(32)
        val p = linkedMapOf(
            "type" to "stb", "action" to "handshake", "JsHttpRequest" to "1-xml",
            "stb_type" to "MAG254", "stb_lang" to "en", "client_type" to "STB",
            "image_version" to "218", "video_out" to "hdmi", "hw_version" to "1.7-BD-00",
            "auth_second_step" to "0", "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r14-pub-254",
            "num_banks" to "1", "sn" to ids, "device_id" to ids,
            "device_id2" to ids, "signature" to sig, "prehash" to "0"
        )
        if (stepToken.isNotEmpty()) p["token"] = stepToken
        return p.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }

    private fun postFields(): FormBody {
        val ids = shortId()
        return FormBody.Builder()
            .add("server_id", "0").add("stb_lang", "en").add("stb_type", "MAG254")
            .add("screen_height", "720").add("hw_version", "1.7-BD-00")
            .add("client_type", "STB").add("image_version", "218").add("video_out", "hdmi")
            .add("device_id", ids).add("device_id2", ids).add("serial_number", ids)
            .add("signature", UUID.randomUUID().toString().replace("-", "").take(32))
            .add("auth_second_step", "0").add("num_banks", "1").add("sn", ids)
            .add("ver", "ImageDescription: 0.2.18-r14-pub-254").add("hd", "1")
            .build()
    }

    private fun doHandshakeOnce(stepToken: String): Pair<String, String> {
        return try {
            val url = base() + "portal.php?" + handshakeParams(stepToken)
            val rb = Request.Builder().url(url).post(postFields())
            magHeaders().forEach { (k, v) -> rb.header(k, v) }
            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return Pair("", "HTTP ${resp.code}")
                val js = try { JSONObject(text).optJSONObject("js") } catch (_: Exception) { null }
                if (js == null) return Pair("", "Sunucu JSON dönmedi (cevap: ${text.take(80)})")
                val t = js.optString("token", "")
                if (t.isEmpty()) {
                    val err = js.optString("error", js.optString("msg", ""))
                    return Pair("", if (err.isNotEmpty()) "Portal reddetti: $err" else "Token boş döndü (MAC/blok kontrol et)")
                }
                Pair(t, "")
            }
        } catch (e: java.net.UnknownHostException) {
            Pair("", "DNS/host çözülmedi (internet/DNS kontrol et)")
        } catch (e: java.net.SocketTimeoutException) {
            Pair("", "Zaman aşımı: portal 20 sn'de cevap vermedi")
        } catch (e: Exception) {
            Log.e("Stalker", "handshake fail", e)
            Pair("", "Ağ hatası: ${e.message}")
        }
    }

    suspend fun handshake(): Boolean = withContext(Dispatchers.IO) {
        lastError = ""
        val (t1, e1) = doHandshakeOnce("")
        if (t1.isEmpty()) { lastError = e1; return@withContext false }
        token = t1
        val (t2, _) = doHandshakeOnce(t1)
        if (t2.isNotEmpty()) token = t2
        val profile = api("get_profile")
        if (profile == null) { lastError = "Token alındı ama profil okunamadı"; return@withContext false }
        true
    }

    private suspend fun api(action: String, extra: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encAction = URLEncoder.encode(action, "UTF-8")
            val url = base() + "portal.php?type=stb&action=$encAction&JsHttpRequest=1-xml$extra"
            val rb = Request.Builder().url(url).post(postFields())
            magHeaders().forEach { (k, v) -> rb.header(k, v) }
            rb.header("Authorization", "Bearer $token")
            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext null
                return@withContext try { JSONObject(text).optJSONObject("js") } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e("Stalker", "api $action fail", e)
            null
        }
    }

    // ---- esnek JSON yardimcilari ----

    private fun dataArray(js: JSONObject): JSONArray? {
        js.optJSONArray("data")?.let { return it }
        // bazi portallar data'yi obje (map) doner: {"data":{"1":{...},"2":{...}}}
        js.optJSONObject("data")?.let { obj ->
            val arr = JSONArray()
            val keys = obj.keys()
            while (keys.hasNext()) {
                obj.optJSONObject(keys.next())?.let { arr.put(it) }
            }
            if (arr.length() > 0) return arr
        }
        // bazi aksiyonlar sonucu direkt array doner: {"js":[{...}]}
        return null
    }

    private fun str(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = o.optString(k, "")
            if (v.isNotEmpty() && v != "null") return v
        }
        return ""
    }

    suspend fun getGenres(): Map<String, String> {
        if (token.isEmpty() && !handshake()) return emptyMap()
        for (action in listOf("itv.get_genres", "itv.get_categories")) {
            val js = api(action) ?: continue
            val arr = dataArray(js) ?: continue
            val map = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = str(o, "id", "category_id", "tv_genre_id")
                val title = str(o, "title", "name", "alias")
                if (id.isNotEmpty() && title.isNotEmpty()) map[id] = title
            }
            if (map.isNotEmpty()) return map
        }
        return emptyMap()
    }

    private fun parseChannel(o: JSONObject, genreMap: Map<String, String>): StalkerChannel? {
        val name = str(o, "name", "o_name", "title")
        if (name.isEmpty()) return null
        val cmd = str(o, "cmd", "url", "ffmpeg_url")
        if (cmd.isEmpty()) return null
        var logo = str(o, "logo", "screenshot", "screenshot_uri", "icon", "cover", "poster")
        if (logo.startsWith("/")) logo = base().trimEnd('/') + logo
        val id = str(o, "id", "cmd_id", "ch_id").ifBlank { cmd.hashCode().toString() }
        var genre = str(o, "tv_genre_title", "tv_genre", "category", "group")
        if (genre.isEmpty()) {
            val gid = str(o, "tv_genre_id", "category_id", "genre_id")
            genre = genreMap[gid] ?: "General"
        }
        return StalkerChannel(id, name, logo, cmd, genre)
    }

    suspend fun getChannels(): List<StalkerChannel> {
        if (token.isEmpty() && !handshake()) return emptyList()
        val genreMap = getGenres()
        val out = mutableListOf<StalkerChannel>()
        val seen = mutableSetOf<String>()

        // 1) toplu liste
        val js = api("itv.get_all_channels")
        dataArray(js ?: JSONObject())?.let { arr ->
            for (i in 0 until arr.length()) {
                val ch = arr.optJSONObject(i)?.let { parseChannel(it, genreMap) } ?: continue
                if (seen.add(ch.id + ch.cmd)) out.add(ch)
            }
        }
        if (out.isNotEmpty()) return out

        // 2) sayfali ordered list (bazi portallar sadece bunu destekler)
        var page = 1
        while (page <= 20) {
            val p = URLEncoder.encode(
                "{\"p\":$page,\"fav\":0,\"sortby\":\"number\",\"hd\":0,\"items_per_page\":1000,\"genre\":0,\"search_str\":\"\"}",
                "UTF-8"
            )
            val pg = api("itv.get_ordered_list", "&p=$p") ?: break
            val arr = dataArray(pg) ?: break
            if (arr.length() == 0) break
            var added = 0
            for (i in 0 until arr.length()) {
                val ch = arr.optJSONObject(i)?.let { parseChannel(it, genreMap) } ?: continue
                if (seen.add(ch.id + ch.cmd)) { out.add(ch); added++ }
            }
            val total = pg.optInt("total_items", pg.optInt("total", 0))
            if (total > 0 && out.size >= total) break
            if (added == 0) break
            page++
        }
        if (out.isNotEmpty()) return out

        // 3) tur bazinda dene
        for ((gid, _) in genreMap) {
            val p = URLEncoder.encode(
                "{\"p\":1,\"fav\":0,\"sortby\":\"number\",\"hd\":0,\"items_per_page\":1000,\"genre\":\"$gid\",\"search_str\":\"\"}",
                "UTF-8"
            )
            val pg = api("itv.get_ordered_list", "&p=$p") ?: continue
            val arr = dataArray(pg) ?: continue
            for (i in 0 until arr.length()) {
                val ch = arr.optJSONObject(i)?.let { parseChannel(it, genreMap) } ?: continue
                if (seen.add(ch.id + ch.cmd)) out.add(ch)
            }
        }
        return out
    }

    suspend fun getVod(limitPages: Int = 5): List<StalkerChannel> {
        if (token.isEmpty() && !handshake()) return emptyList()
        val out = mutableListOf<StalkerChannel>()
        val seen = mutableSetOf<String>()
        var page = 1
        while (page <= limitPages) {
            val p = URLEncoder.encode(
                "{\"p\":$page,\"fav\":0,\"sortby\":\"added\",\"hd\":0,\"items_per_page\":200}",
                "UTF-8"
            )
            val pg = api("vod.get_ordered_list", "&p=$p") ?: break
            val arr = dataArray(pg) ?: break
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = str(o, "name", "o_name", "title")
                val cmd = str(o, "cmd", "url")
                if (name.isEmpty() || cmd.isEmpty()) continue
                var logo = str(o, "screenshot", "screenshot_uri", "logo", "cover", "poster")
                if (logo.startsWith("/")) logo = base().trimEnd('/') + logo
                val id = str(o, "id", "video_id").ifBlank { cmd.hashCode().toString() }
                if (seen.add(id)) out.add(StalkerChannel(id, name, logo, cmd, "VOD"))
            }
            page++
        }
        return out
    }

    suspend fun createLink(cmd: String): String {
        // direkt URL ise aynen dondur (xtream/m3u uyumu)
        if (cmd.startsWith("http")) return cmd
        if (token.isEmpty()) handshake()
        val enc = URLEncoder.encode(cmd, "UTF-8")
        val js = api("itv.create_link", "&cmd=$enc&forced_storage=0&disable_ad=0") ?: run {
            val js2 = api("vod.create_link", "&cmd=$enc&forced_storage=0&disable_ad=0") ?: return ""
            return cleanLink(js2.optString("cmd", js2.optString("url", "")))
        }
        var link = js.optString("cmd", "")
        if (link.isBlank()) link = js.optString("url", "")
        return cleanLink(link)
    }

    /** Ministra bazen "ffmpeg http://..." dondurur; player ham URL ister. */
    fun cleanLink(raw: String): String {
        var s = raw.trim()
        for (prefix in listOf("ffmpeg ", "auto ", "nimble ")) {
            if (s.startsWith(prefix, ignoreCase = true)) s = s.substring(prefix.length).trim()
        }
        // link goreceli ise portala tamamla
        if (s.startsWith("/")) s = base().trimEnd('/') + s
        return s
    }

    /** Stalker stream sunuculari MAG basliklari isteyebilir; player'a verilir. */
    fun streamHeaders(): Map<String, String> {
        val ids = shortId()
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp ver: 4 rev: 2721 Safari/533.3",
            "Referer" to base(),
            "Cookie" to "mac=$mac; stb_lang=en; timezone=Europe%2FIstanbul; " +
                    "serial_number=$ids; device_id=$ids; device_id2=$ids; stb_token=$token"
        )
    }
}
