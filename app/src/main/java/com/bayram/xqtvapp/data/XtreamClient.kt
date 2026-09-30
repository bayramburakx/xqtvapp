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
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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
                    if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray()
                } catch (_: Exception) { JSONArray() }
            }
        } catch (e: Exception) {
            Log.e("Xtream", "array fail", e)
            JSONArray()
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
        val arr = getArray("player_api.php?${u()}&action=$action")
        val map = mutableMapOf<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            map[o.optString("category_id")] = o.optString("category_name", "General")
        }
        return map
    }

    suspend fun liveCategories() = categories("get_live_categories")
    suspend fun vodCategories() = categories("get_vod_categories")

    suspend fun liveStreams(): List<StalkerChannel> {
        val cats = liveCategories()
        val arr = getArray("player_api.php?${u()}&action=get_live_streams")
        val out = mutableListOf<StalkerChannel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stream_id")
            if (id.isEmpty()) continue
            val name = o.optString("name", "CH $id")
            val logo = o.optString("stream_icon", "")
            val cat = cats[o.optString("category_id")] ?: "General"
            val url = "$server/live/$username/$password/$id.m3u8"
            out.add(StalkerChannel("live_$id", name, logo, url, cat))
        }
        return out
    }

    suspend fun vodStreams(): List<StalkerChannel> {
        val cats = vodCategories()
        val arr = getArray("player_api.php?${u()}&action=get_vod_streams")
        val out = mutableListOf<StalkerChannel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stream_id")
            if (id.isEmpty()) continue
            val name = o.optString("name", "VOD $id")
            val logo = o.optString("stream_icon", "")
            val cat = cats[o.optString("category_id")] ?: "Movies"
            val ext = o.optString("container_extension", "mp4")
            val url = "$server/movie/$username/$password/$id.$ext"
            out.add(StalkerChannel("vod_$id", name, logo, url, cat))
        }
        return out
    }
}
