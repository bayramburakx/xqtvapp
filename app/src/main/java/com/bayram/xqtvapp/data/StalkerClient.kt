package com.bayram.xqtvapp.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private fun magHeaders(extraCookie: String = ""): Map<String, String> {
        val ids = shortId()
        val cookie = "mac=$mac; stb_lang=en; timezone=Europe%2FIstanbul; " +
                "serial_number=$ids; device_id=$ids; device_id2=$ids" +
                (if (extraCookie.isNotEmpty()) "; $extraCookie" else "")
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG254 stbapp ver: 4 rev: 2721 Safari/533.3",
            "Accept" to "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With" to "XMLHttpRequest",
            "X-User-Agent" to "Model: MAG254; Link: WiFi",
            "Referer" to base(),
            "Cookie" to cookie
        )
    }

    private fun handshakeParams(stepToken: String): String {
        val ids = shortId()
        val sig = UUID.randomUUID().toString().replace("-", "").take(32)
        val p = linkedMapOf(
            "type" to "stb",
            "action" to "handshake",
            "JsHttpRequest" to "1-xml",
            "stb_type" to "MAG254",
            "stb_lang" to "en",
            "client_type" to "STB",
            "image_version" to "218",
            "video_out" to "hdmi",
            "hw_version" to "1.7-BD-00",
            "not_valid_token" to if (stepToken.isEmpty()) "0" else "0",
            "auth_second_step" to "0",
            "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r14-pub-254; ImageDate: Wed Mar 18 12:03:14 EET 2015; PORTAL version: 5.0.5; API Version: JS API version: 328; STB API version: 134; Player Engine version: 0x566",
            "num_banks" to "1",
            "sn" to ids,
            "device_id" to ids,
            "device_id2" to ids,
            "signature" to sig,
            "prehash" to "0"
        )
        if (stepToken.isNotEmpty()) p["token"] = stepToken
        return p.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }

    private fun postFields(): FormBody {
        val ids = shortId()
        return FormBody.Builder()
            .add("server_id", "0")
            .add("stb_lang", "en")
            .add("stb_type", "MAG254")
            .add("screen_height", "720")
            .add("hw_version", "1.7-BD-00")
            .add("client_type", "STB")
            .add("image_version", "218")
            .add("video_out", "hdmi")
            .add("device_id", ids)
            .add("device_id2", ids)
            .add("serial_number", ids)
            .add("signature", UUID.randomUUID().toString().replace("-", "").take(32))
            .add("auth_second_step", "0")
            .add("num_banks", "1")
            .add("sn", ids)
            .add("ver", "ImageDescription: 0.2.18-r14-pub-254")
            .add("hd", "1")
            .build()
    }

    private fun doHandshakeOnce(stepToken: String): Pair<String, String> {
        return try {
            val url = base() + "portal.php?" + handshakeParams(stepToken)
            val b = postFields()
            val rb = Request.Builder().url(url).post(b)
            magHeaders().forEach { (k, v) -> rb.header(k, v) }
            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return Pair("", "HTTP ${resp.code}")
                val js = try {
                    JSONObject(text).optJSONObject("js")
                } catch (_: Exception) { null }
                if (js == null) return Pair("", "Sunucu JSON dönmedi (cevap: ${text.take(80)})")
                val t = js.optString("token", "")
                if (t.isEmpty()) {
                    val err = js.optString("error", js.optString("msg", ""))
                    return Pair("", if (err.isNotEmpty()) "Portal reddetti: $err" else "Token boş döndü (MAC/blok kontrol et)")
                }
                return Pair(t, "")
            }
        } catch (e: java.net.UnknownHostException) {
            Pair("", "DNS/host çözülmedi (internet/DNS kontrol et)")
        } catch (e: java.net.SocketTimeoutException) {
            Pair("", "Zaman aşımı: portal 20 sn'de cevap vermedi (522/engelleme olabilir)")
        } catch (e: javax.net.ssl.SSLException) {
            Pair("", "SSL hatası: ${e.message}")
        } catch (e: Exception) {
            Log.e("Stalker", "handshake fail", e)
            Pair("", "Ağ hatası: ${e.message}")
        }
    }

    suspend fun handshake(): Boolean = withContext(Dispatchers.IO) {
        lastError = ""
        // 1. adim: tokensiz
        val (t1, e1) = doHandshakeOnce("")
        if (t1.isEmpty()) { lastError = e1; return@withContext false }
        token = t1
        // 2. adim: token ile (bazi portallar ister, basarisizsa ilk token ile devam et)
        val (t2, _) = doHandshakeOnce(t1)
        if (t2.isNotEmpty()) token = t2
        // profil dogrulama
        val profile = api("get_profile")
        if (profile == null) { lastError = "Token alındı ama profil okunamadı"; return@withContext false }
        true
    }

    private suspend fun api(action: String, extra: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encAction = URLEncoder.encode(action, "UTF-8")
            val url = base() + "portal.php?type=stb&action=$encAction&JsHttpRequest=1-xml&p=" +
                    URLEncoder.encode("{\"token\":\"$token\"}", "UTF-8") + extra
            val rb = Request.Builder().url(url).post(postFields())
            magHeaders().forEach { (k, v) -> rb.header(k, v) }
            rb.header("Authorization", "Bearer $token")
            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext null
                return@withContext try {
                    JSONObject(text).optJSONObject("js")
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e("Stalker", "api $action fail", e)
            null
        }
    }

    suspend fun getChannels(): List<StalkerChannel> {
        if (token.isEmpty() && !handshake()) return emptyList()
        var js = api("itv.get_all_channels")
        if (js == null || js.optJSONArray("data") == null) {
            handshake()
            js = api("itv.get_all_channels")
        }
        // yedek: ordered list
        if (js == null || js.optJSONArray("data") == null) {
            js = api("itv.get_ordered_list", "&fav=0&sortby=number&hd=0")
        }
        val out = mutableListOf<StalkerChannel>()
        val data = js?.optJSONArray("data") ?: return out
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            out.add(
                StalkerChannel(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    logo = o.optString("logo", o.optString("screenshot", "")),
                    cmd = o.optString("cmd"),
                    genre = o.optString("tv_genre_title", o.optString("tv_genre", "General"))
                )
            )
        }
        return out
    }

    suspend fun createLink(cmd: String): String {
        if (token.isEmpty()) handshake()
        val enc = URLEncoder.encode(cmd, "UTF-8")
        val js = api("itv.create_link", "&cmd=$enc&forced_storage=0&disable_ad=0") ?: return ""
        var link = js.optString("cmd", "")
        if (link.isBlank()) {
            // bazi portallar linki farkli alanda doner
            link = js.optString("url", "")
        }
        return link
    }
}
