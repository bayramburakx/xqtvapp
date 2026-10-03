package com.bayram.xqtvapp.data

/**
 * Ulke bazli kategori gruplama: "US | Movies", "USA - Sports", "[UK] News",
 * "TRT Spor" gibi her turlu yazimi ulkeye baglar.
 *
 * Kaynak EPG / liste siralamasini degistirmez; sadece Kategoriler
 * ekraninda ulke + alt kategori anahtarlari uretir. Gizleme durumu
 * yine hidden-keys ("live:<hamAd>") ile tutulur.
 */
data class CountryDef(
    val code: String,
    val flag: String,
    val trName: String,
    val aliases: Set<String>
)

val COUNTRIES = listOf(
    CountryDef("US", "🇺🇸", "ABD", setOf("us", "usa", "unitedstates", "america", "american")),
    CountryDef("TR", "🇹🇷", "Türkiye", setOf("tr", "tur", "turkiye", "turkey", "turkish", "turk", "turkce", "trt", "anatolia", "anadolu")),
    CountryDef("UK", "🇬🇧", "Birleşik Krallık", setOf("uk", "gbr", "britain", "british", "england", "english", "london")),
    CountryDef("CA", "🇨🇦", "Kanada", setOf("ca", "can", "canada", "canadian")),
    CountryDef("DE", "🇩🇪", "Almanya", setOf("de", "deu", "deutschland", "german", "germany", "deutsch")),
    CountryDef("FR", "🇫🇷", "Fransa", setOf("fr", "fra", "france", "french", "francais")),
    CountryDef("ES", "🇪🇸", "İspanya", setOf("es", "esp", "espana", "spain", "spanish", "espanol")),
    CountryDef("IT", "🇮🇹", "İtalya", setOf("it", "ita", "italy", "italian", "italia")),
    CountryDef("PT", "🇵🇹", "Portekiz", setOf("pt", "por", "portugal", "portuguese")),
    CountryDef("NL", "🇳🇱", "Hollanda", setOf("nl", "ned", "netherlands", "dutch", "holland")),
    CountryDef("AR", "🇸🇦", "Arapça", setOf("ar", "ara", "arab", "arabic", "arabia", "saudi", "ksa", "uae", "emirates", "dubai", "qatar", "kuwait", "bahrain", "oman", "jordan", "lebanon", "egypt", "eg", "iraq", "morocco", "tunisia", "algeria", "mbc", "osn", "jazeera", "rotana")),
    CountryDef("IN", "🇮🇳", "Hindistan", setOf("in", "ind", "india", "indian", "hindi", "zee")),
    CountryDef("PK", "🇵🇰", "Pakistan", setOf("pk", "pak", "pakistan", "pakistani", "ary", "hum")),
    CountryDef("GR", "🇬🇷", "Yunanistan", setOf("gr", "gre", "greece", "greek")),
    CountryDef("PL", "🇵🇱", "Polonya", setOf("pl", "pol", "poland", "polish", "polsat", "tvp")),
    CountryDef("RU", "🇷🇺", "Rusya", setOf("ru", "rus", "russia", "russian")),
    CountryDef("UA", "🇺🇦", "Ukrayna", setOf("ua", "ukr", "ukraine", "ukrainian")),
    CountryDef("RO", "🇷🇴", "Romanya", setOf("ro", "rom", "romania", "romanian")),
    CountryDef("BR", "🇧🇷", "Brezilya", setOf("br", "bra", "brazil", "brazilian")),
    CountryDef("LA", "🇲🇽", "Latin", setOf("mx", "mexico", "mexican", "latino", "latina", "latin", "argentina", "colombia", "chile", "peru", "venezuela", "telemundo", "univision")),
    CountryDef("AU", "🇦🇺", "Avustralya", setOf("au", "aus", "australia", "australian")),
    CountryDef("IR", "🇮🇷", "İran", setOf("ir", "iran", "iranian", "persian", "farsi")),
    CountryDef("IL", "🇮🇱", "İsrail", setOf("il", "israel", "israeli", "hebrew")),
    CountryDef("AL", "🇦🇱", "Arnavutluk", setOf("al", "albania", "albanian")),
    CountryDef("BK", "🇷🇸", "Balkan", setOf("rs", "serbia", "serbian", "hr", "croatia", "croatian", "ba", "bosnia", "bosnian", "mk", "macedonia", "macedonian", "balkan", "balkans", "si", "slovenia", "montenegro")),
    CountryDef("SC", "🇸🇪", "İskandinav", setOf("se", "sweden", "swedish", "norway", "norwegian", "dk", "denmark", "danish", "fi", "finland", "finnish", "scandinavia")),
    CountryDef("AT", "🇦🇹", "Avusturya", setOf("at", "aut", "austria", "austrian")),
    CountryDef("CH", "🇨🇭", "İsviçre", setOf("ch", "swiss", "switzerland")),
    CountryDef("XX", "🌐", "Diğer", emptySet())
)

private val COUNTRY_ORDER = listOf(
    "US", "TR", "UK", "CA", "DE", "FR", "ES", "IT", "PT", "NL",
    "AR", "IN", "PK", "GR", "PL", "RU", "UA", "RO", "BR", "LA",
    "AU", "IR", "IL", "AL", "BK", "SC", "AT", "CH"
)

fun countryDef(code: String): CountryDef =
    COUNTRIES.firstOrNull { it.code == code } ?: COUNTRIES.last()

private fun foldTr(s: String): String = s.lowercase()
    .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
    .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')

private fun toks(s: String): List<String> =
    foldTr(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

/** Ham kategori adindan ulke kodu cikarir; eslesme yoksa "XX" (Diger). */
fun detectCountry(raw: String): String {
    val t = toks(raw).toSet()
    if (t.isEmpty()) return "XX"
    for (c in COUNTRIES) {
        if (c.code == "XX") continue
        if (c.aliases.any { it in t }) return c.code
    }
    return "XX"
}

/**
 * Alt kategori etiketi: ulke belirten kisimlar atilir, kalan gosterilir.
 * "US | Movies" -> "Movies", "USA - Sports HD" -> "Sports HD",
 * "TRT Spor" -> "Spor", sade "Haber" -> "Haber", bossa -> "Genel".
 */
fun subLabel(raw: String, aliases: Set<String>): String {
    val parts = raw.split('|', '｜', '-', '–', '—', ':', '/', '[', ']', '(', ')', '{', '}')
        .map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size > 1) {
        val kept = parts.filter { p -> toks(p).any { it !in aliases } }
        if (kept.isNotEmpty()) return kept.joinToString(" • ")
        return "Genel"
    }
    val words = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
    val kept = words.filter { w ->
        val t = foldTr(w).trim('[', ']', '(', ')', '|', '-', '+', '_')
        t.isNotEmpty() && t !in aliases
    }
    return if (kept.isEmpty()) "Genel" else kept.joinToString(" ")
}

data class SubGroup(
    val label: String,
    val raws: List<String>,
    val count: Int,
    val keys: List<String>
)

data class CountryGroup(
    val code: String,
    val subs: List<SubGroup>,
    val totalCount: Int
)

/**
 * Ham (ad, icerikSayisi) listesini ulke -> alt kategori agacina cevirir.
 * overrides: "tur||hamAd" -> ulkeKodu (kullanicinin "Ulke ata" secimi).
 */
fun groupByCountry(
    kind: String,
    cats: List<Pair<String, Int>>,
    overrides: Map<String, String> = emptyMap()
): List<CountryGroup> {
    val byCountry = mutableMapOf<String, MutableMap<String, SubGroup>>()
    for ((raw, count) in cats) {
        val code = overrides["$kind||$raw"] ?: detectCountry(raw)
        val aliases = COUNTRIES.firstOrNull { it.code == code }?.aliases ?: emptySet()
        val label = subLabel(raw, aliases)
        val subs = byCountry.getOrPut(code) { mutableMapOf() }
        val old = subs[label]
        subs[label] = SubGroup(
            label = label,
            raws = (old?.raws ?: emptyList()) + raw,
            count = (old?.count ?: 0) + count,
            keys = (old?.keys ?: emptyList()) + "$kind:$raw"
        )
    }
    return byCountry.map { (code, subs) ->
        CountryGroup(
            code = code,
            subs = subs.values.sortedWith(compareByDescending<SubGroup> { it.count }.thenBy { it.label }),
            totalCount = subs.values.sumOf { it.count }
        )
    }.sortedWith(
        compareBy<CountryGroup> {
            val i = COUNTRY_ORDER.indexOf(it.code)
            if (i < 0) Int.MAX_VALUE else i
        }.thenBy { if (it.code == "XX") "~~~" else countryDef(it.code).trName }
    )
}
