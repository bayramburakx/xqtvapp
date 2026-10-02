package com.bayram.xqtvapp.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Internet XMLTV yedegi: kaynagin kendi EPG'si yoksa (Xtream'de
 * get_simple_data_table/get_short_epg bos donerse, M3U/Stalker'da
 * hic yoksa) devreye girer. Once kaynak denenir, yoksa internetten
 * cekilir. Amerikan + global kanallar icin cok ulkeli listeler ve
 * bulanik (fuzzy) eslesme kullanilir.
 *
 * Siralama: kaynak EPG -> kullanici XMLTV URL'i -> M3U url-tvg -> yerlesik global listeler.
 * 24 saat dosyada durur; ag + parse tamamen Dispatchers.IO'dadir.
 */
object EpgXml {
    private const val TTL_MS = 24L * 60 * 60 * 1000
    private const val MAX_BYTES = 25 * 1024 * 1024
    private val gson = Gson()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private data class Dump(
        val savedAt: Long = 0L,
        val byName: Map<String, List<EpgEntry>> = emptyMap()
    )

    private val mem = LinkedHashMap<String, Dump>()
    private val type = object : TypeToken<Dump>() {}.type

    /** Yerlesik global listeler (iptv-epg.org, ulke bazli; 404 olanlar sessizce atlanir). */
    private val BUILTIN = listOf(
        "https://iptv-epg.org/files/epg-tr.xml",
        "https://iptv-epg.org/files/epg-uk.xml",
        "https://iptv-epg.org/files/epg-us.xml",
        "https://iptv-epg.org/files/epg-fr.xml",
        "https://iptv-epg.org/files/epg-de.xml",
        "https://iptv-epg.org/files/epg-es.xml",
        "https://iptv-epg.org/files/epg-it.xml",
        "https://iptv-epg.org/files/epg-ar.xml",
        "https://iptv-epg.org/files/epg-nl.xml"
    )

    private val QUALITY = setOf(
        "1080p", "720p", "480p", "576p", "4k", "uhd", "fhd", "hd", "sd",
        "h265", "hevc", "50fps", "60fps"
    )
    private val COUNTRY = setOf(
        "usa", "us", "uk", "tr", "turkey", "de", "germany", "fr", "france",
        "it", "italy", "es", "spain", "pt", "nl", "netherlands", "be", "ar",
        "arab", "eu", "europe", "global", "world", "international", "local",
        "east", "west", "national"
    )

    fun normalize(name: String): String {
        var s = name.lowercase()
            .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
            .replace("ş", "s").replace("ö", "o").replace("ç", "c")
        s = s.replace(Regex("\\.(m3u8|mp4|mkv|ts)$"), " ")
        s = s.replace(Regex("[^a-z0-9]+"), " ").trim()
        val toks = s.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in QUALITY }
        return toks.joinToString("")
    }

    private fun stripped(name: String): String {
        var s = name.lowercase()
            .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
            .replace("ş", "s").replace("ö", "o").replace("ç", "c")
        s = s.replace(Regex("[^a-z0-9]+"), " ").trim()
        val toks = s.split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in QUALITY && it !in COUNTRY }
        return toks.joinToString("")
    }

    /** Denenecek anahtarlar: tam normalize, ulkesiz, ilk anlamli kelime. */
    private fun candidates(name: String): List<String> {
        val n = normalize(name)
        val st = stripped(name)
        val out = mutableListOf<String>()
        if (n.isNotBlank()) out.add(n)
        if (st.isNotBlank() && st != n) out.add(st)
        // "cnnusa" -> "cnn" gibi kok: ulke eklerini sondan kirp
        var cur = st
        for (c in listOf("usa", "us", "uk", "tr", "eu")) {
            if (cur.endsWith(c) && cur.length > c.length + 2) {
                cur = cur.dropLast(c.length)
                if (cur !in out) out.add(cur)
            }
        }
        // kelime bazli: en uzun anlamli kelime (>=4 harf)
        val words = name.lowercase().replace(Regex("[^a-z0-9çğıöşü ]"), " ")
            .split(Regex("\\s+")).filter { it.length >= 4 }
        words.sortedByDescending { it.length }.take(2).forEach {
            val k = normalize(it)
            if (k.isNotBlank() && k !in out && k.length >= 4) out.add(k)
        }
        return out.distinct().take(5)
    }

    private fun findLists(byName: Map<String, List<EpgEntry>>, chName: String): List<EpgEntry> {
        if (byName.isEmpty() || chName.isBlank()) return emptyList()
        val cands = candidates(chName)
        // 1) birebir
        for (k in cands) {
            byName[k]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        // 2) iceren/icerilen (min 4 harf, yanlis eslesmeyi onlemek icin)
        for (k in cands) {
            if (k.length < 4) continue
            for ((key, list) in byName) {
                if (list.isEmpty()) continue
                if (key.length < 4) continue
                if (key.contains(k) || k.contains(key)) return list
            }
        }
        return emptyList()
    }

    private fun cacheFile(ctx: Context, m3uUrl: String): File {
        val key = (m3uUrl.hashCode().toUInt().toString(16))
        return File(ctx.filesDir, "portio_epg_$key.json")
    }

    /** M3U basligindaki url-tvg adresini bul (ilk 64KB yeterli). */
    private fun findUrlTvg(m3uUrl: String): String? {
        if (m3uUrl.isBlank()) return null
        return try {
            val req = Request.Builder().url(m3uUrl)
                .header("User-Agent", "Portio/2.0")
                .header("Range", "bytes=0-65535")
                .header("Icy-MetaData", "0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) return null
                val stream = resp.body?.byteStream() ?: return null
                val head = stream.use {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (total < 65536) {
                        val n = it.read(buf, 0, minOf(buf.size, 65536 - total))
                        if (n < 0) break
                        total += n
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                val text = head.toString(Charsets.UTF_8)
                Regex("""url-tvg="([^"]+)"""").find(text)?.groupValues?.get(1)
                    ?: Regex("""x-tvg-url="([^"]+)"""").find(text)?.groupValues?.get(1)
            }
        } catch (_: Exception) { null }
    }

    private fun downloadCapped(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Portio/2.0")
                .header("Accept-Encoding", "gzip")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                val isGzip = url.endsWith(".gz") ||
                        (resp.header("Content-Encoding") ?: "").contains("gzip", true)
                val stream = if (isGzip) GZIPInputStream(body.byteStream()) else body.byteStream()
                stream.use {
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(32 * 1024)
                    var total = 0
                    while (true) {
                        val n = it.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return null
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            }
        } catch (_: Exception) { null }
    }

    private fun parseXml(data: ByteArray): Map<String, List<EpgEntry>> {
        val out = mutableMapOf<String, MutableList<EpgEntry>>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val p = factory.newPullParser()
            p.setInput(ByteArrayInputStream(data), "UTF-8")
            val nowS = System.currentTimeMillis() / 1000
            val minS = nowS - 6 * 3600
            val maxS = nowS + 48 * 3600
            var ev = p.eventType
            var inProg = false
            var ch = ""
            var start = 0L
            var stop = 0L
            var title = ""
            var desc = ""
            var curTag = ""
            var count = 0
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && count < 80000) {
                when (ev) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (p.name) {
                            "programme" -> {
                                inProg = true
                                ch = p.getAttributeValue(null, "channel") ?: ""
                                start = xmltvTime(p.getAttributeValue(null, "start"))
                                stop = xmltvTime(p.getAttributeValue(null, "stop"))
                                title = ""; desc = ""
                            }
                            "title", "desc" -> curTag = p.name
                            else -> curTag = ""
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inProg) {
                            val tx = p.text ?: ""
                            if (curTag == "title") title += tx
                            else if (curTag == "desc" && desc.length < 500) desc += tx
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (p.name == "programme") {
                            inProg = false
                            if (ch.isNotEmpty() && title.isNotBlank() &&
                                stop > minS && start < maxS && start < stop
                            ) {
                                out.getOrPut(ch) { mutableListOf() }
                                    .add(EpgEntry(title.trim(), start, stop))
                                count++
                            }
                        }
                        curTag = ""
                    }
                }
                ev = p.next()
            }
        } catch (_: Exception) { }
        return out
    }

    private fun xmltvTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            val s = raw.trim().replace(" ", "")
            val core = s.take(14)
            val dt = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
            dt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            var t = dt.parse(core)?.time?.div(1000) ?: 0L
            val tz = Regex("""([+-])(\d{2})(\d{2})$""").find(s)
            if (tz != null) {
                val off = tz.groupValues[2].toLong() * 3600 + tz.groupValues[3].toLong() * 60
                t -= if (tz.groupValues[1] == "+") off else -off
            }
            t
        } catch (_: Exception) { 0L }
    }

    private fun dumpFromById(byId: Map<String, List<EpgEntry>>): Dump {
        val byName = mutableMapOf<String, MutableList<EpgEntry>>()
        byId.forEach { (id, list) ->
            byName.getOrPut(normalize(id)) { mutableListOf() }.addAll(list)
            val st = stripped(id)
            if (st.isNotBlank() && st != normalize(id)) {
                byName.getOrPut(st) { mutableListOf() }.addAll(list)
            }
            byName.getOrPut(id) { mutableListOf() }.addAll(list)
        }
        byName.values.forEach { it.sortBy { e -> e.startEpoch } }
        return Dump(System.currentTimeMillis(), byName)
    }

    private suspend fun ensureLoaded(ctx: Context, m3uUrl: String): Dump = withContext(Dispatchers.IO) {
        if (m3uUrl.isBlank()) return@withContext Dump(System.currentTimeMillis(), emptyMap())
        synchronized(mem) {
            mem[m3uUrl]?.let { if (System.currentTimeMillis() - it.savedAt < TTL_MS) return@withContext it }
        }
        val f = cacheFile(ctx, m3uUrl)
        try {
            if (f.exists()) {
                val d = gson.fromJson<Dump>(f.readText(), type)
                if (System.currentTimeMillis() - d.savedAt < TTL_MS) {
                    synchronized(mem) { mem[m3uUrl] = d }
                    return@withContext d
                }
            }
        } catch (_: Exception) { }
        var dump = Dump(System.currentTimeMillis(), emptyMap())
        val tvg = findUrlTvg(m3uUrl)
        if (tvg != null) {
            val data = downloadCapped(tvg)
            if (data != null) {
                dump = dumpFromById(parseXml(data))
                try { f.writeText(gson.toJson(dump)) } catch (_: Exception) { }
            }
        }
        synchronized(mem) { mem[m3uUrl] = dump }
        dump
    }

    private suspend fun loadDirect(ctx: Context, key: String, xmlUrl: String): Dump {
        synchronized(mem) {
            mem[key]?.let { if (System.currentTimeMillis() - it.savedAt < TTL_MS) return it }
        }
        val f = File(ctx.filesDir, "portio_epg_" + (xmlUrl.hashCode().toUInt().toString(16)) + ".json")
        try {
            if (f.exists()) {
                val d = gson.fromJson<Dump>(f.readText(), type)
                if (System.currentTimeMillis() - d.savedAt < TTL_MS) {
                    synchronized(mem) { mem[key] = d }
                    return d
                }
            }
        } catch (_: Exception) { }
        var dump = Dump(System.currentTimeMillis(), emptyMap())
        val data = withContext(Dispatchers.IO) { downloadCapped(xmlUrl) }
        if (data != null) {
            dump = dumpFromById(parseXml(data))
            try { withContext(Dispatchers.IO) { f.writeText(gson.toJson(dump)) } } catch (_: Exception) { }
        }
        synchronized(mem) { mem[key] = dump }
        return dump
    }

    private suspend fun matchCustom(ctx: Context, xmlUrl: String, name: String): List<EpgEntry> {
        return try {
            val dump = loadDirect(ctx, "custom:" + xmlUrl, xmlUrl)
            findLists(dump.byName, name)
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun matchBuiltin(ctx: Context, name: String): List<EpgEntry> {
        for (u in BUILTIN) {
            try {
                val dump = loadDirect(ctx, "builtin:" + u, u)
                val l = findLists(dump.byName, name)
                if (l.isNotEmpty()) return l
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    private suspend fun userEpgUrl(ctx: Context): String {
        return try { FavoritesStore.defaultEpgFlow(ctx).first() } catch (_: Exception) { "" }
    }

    /** Tek kanal icin su anki yayin: kaynak -> ozel URL -> M3U url-tvg -> global. */
    suspend fun lookupNow(
        ctx: Context, session: com.bayram.xqtvapp.Session, ch: StalkerChannel
    ): EpgEntry? {
        // 1) kaynak EPG'si (Xtream)
        if (session.xServer.isNotBlank()) {
            try {
                val sid = ch.id.removePrefix("live_")
                if (sid != ch.id) {
                    val x = XtreamClient(session.xServer, session.xUser, session.xPass)
                    EpgCache.get(x, sid)?.let { return it }
                }
            } catch (_: Exception) { }
        }
        val now = System.currentTimeMillis() / 1000
        // 2) kullanicinin ozel XMLTV adresi
        try {
            val defUrl = userEpgUrl(ctx)
            if (defUrl.isNotBlank()) {
                matchCustom(ctx, defUrl, ch.name).firstOrNull {
                    it.startEpoch <= now && now < it.endEpoch
                }?.let { return it }
            }
        } catch (_: Exception) { }
        // 3) M3U url-tvg
        try {
            if (session.m3uUrl.isNotBlank()) {
                val dump = ensureLoaded(ctx, session.m3uUrl)
                findLists(dump.byName, ch.name).firstOrNull {
                    it.startEpoch <= now && now < it.endEpoch
                }?.let { return it }
            }
        } catch (_: Exception) { }
        // 4) yerlesik global listeler (TR + US/UK/EU/AR)
        try {
            matchBuiltin(ctx, ch.name).firstOrNull {
                it.startEpoch <= now && now < it.endEpoch
            }?.let { return it }
        } catch (_: Exception) { }
        return null
    }

    /** Rehber icin gunluk akis (once kaynak, sonra internet). */
    suspend fun lookupDay(
        ctx: Context, session: com.bayram.xqtvapp.Session, ch: StalkerChannel
    ): List<EpgEntry> {
        if (session.xServer.isNotBlank()) {
            try {
                val sid = ch.id.removePrefix("live_")
                if (sid != ch.id) {
                    val x = XtreamClient(session.xServer, session.xUser, session.xPass)
                    val l = EpgCache.getDay(x, sid)
                    if (l.isNotEmpty()) return l
                }
            } catch (_: Exception) { }
        }
        val now = System.currentTimeMillis() / 1000
        try {
            val defUrl = userEpgUrl(ctx)
            if (defUrl.isNotBlank()) {
                val l = matchCustom(ctx, defUrl, ch.name).filter { it.endEpoch > now - 3600 }
                if (l.isNotEmpty()) return l
            }
        } catch (_: Exception) { }
        try {
            if (session.m3uUrl.isNotBlank()) {
                val dump = ensureLoaded(ctx, session.m3uUrl)
                val l = findLists(dump.byName, ch.name).filter { it.endEpoch > now - 3600 }
                if (l.isNotEmpty()) return l
            }
        } catch (_: Exception) { }
        try {
            val l = matchBuiltin(ctx, ch.name).filter { it.endEpoch > now - 3600 }
            if (l.isNotEmpty()) return l
        } catch (_: Exception) { }
        return emptyList()
    }
}
