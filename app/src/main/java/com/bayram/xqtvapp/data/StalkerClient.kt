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

class StalkerClient(
    private var portalUrl: String,
    private var mac: String
) {
    private val client = OkHttpClient()
    private var token: String = ""

    private fun base(): String {
        var u = portalUrl.trim()
        if (!u.endsWith("/")) u += "/"
        return u
    }

    private fun deviceIds(): Map<String, String> {
        val clean = mac.replace(":", "").uppercase()
        return mapOf(
            "serial_number" to clean.takeLast(12),
            "device_id" to clean.takeLast(12),
            "device_id2" to clean.takeLast(12),
            "signature" to UUID.randomUUID().toString().replace("-", "").take(32)
        )
    }

    suspend fun handshake(): Boolean = withContext(Dispatchers.IO) {
        try {
            val ids = deviceIds()
            val url = base() + "portal.php?type=stb&action=handshake&JsHttpRequest=1-xml"
            val body = FormBody.Builder()
                .add("referrer", "")
                .add("stb_lang", "en")
                .build()
            val req = Request.Builder()
                .url(url)
                .post(body)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                )
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Istanbul; " +
                        "serial_number=${ids["serial_number"]}; device_id=${ids["device_id"]}")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext false
                val js = JSONObject(text).optJSONObject("js") ?: return@withContext false
                token = js.optString("token", "")
                return@withContext token.isNotEmpty()
            }
        } catch (e: Exception) {
            Log.e("Stalker", "handshake fail", e)
            false
        }
    }

    private suspend fun api(action: String, extra: String = ""): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encAction = URLEncoder.encode(action, "UTF-8")
            val url = base() + "portal.php?type=stb&action=$encAction&JsHttpRequest=1-xml$extra"
            val body = FormBody.Builder().add("action", action).build()
            val req = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) MAG200")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Istanbul")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: return@withContext null
                return@withContext JSONObject(text).optJSONObject("js")
            }
        } catch (e: Exception) {
            Log.e("Stalker", "api $action fail", e)
            null
        }
    }

    suspend fun getChannels(): List<StalkerChannel> {
        if (token.isEmpty() && !handshake()) return emptyList()
        var js = api("itv.get_all_channels")
        if (js == null) {
            handshake()
            js = api("itv.get_all_channels")
        }
        val out = mutableListOf<StalkerChannel>()
        if (js == null) return out
        val data = js.optJSONArray("data") ?: return out
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            out.add(
                StalkerChannel(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    logo = o.optString("logo"),
                    cmd = o.optString("cmd"),
                    genre = o.optString("tv_genre_title", "General")
                )
            )
        }
        return out
    }

    suspend fun createLink(cmd: String): String {
        if (token.isEmpty()) handshake()
        val enc = URLEncoder.encode(cmd, "UTF-8")
        val js = api("itv.create_link", "&cmd=$enc") ?: return ""
        return js.optString("cmd", "")
    }
}
