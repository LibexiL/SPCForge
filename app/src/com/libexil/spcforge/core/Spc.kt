package com.libexil.spcforge.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** An image shown in the app: the first of [urls] that loads is used (SPC has moved between .gif and .png). */
data class Img(val label: String, val urls: List<String>, val whiteBg: Boolean = false)

/** One SPC web page boiled down: its graphics, its text products and any useful tables. */
data class Product(
    val title: String,
    val pageUrl: String,
    val images: List<Img>,
    val texts: List<String>,
    val tables: List<List<String>> = emptyList(),
)

object Spc {
    const val BASE = "https://www.spc.noaa.gov"
    const val IEM = "https://mesonet.agron.iastate.edu"
    const val NWS = "https://api.weather.gov"

    // ------------------------------------------------------------------ risk categories
    val CATEGORIES = listOf("TSTM", "MRGL", "SLGT", "ENH", "MDT", "HIGH")
    val CAT_COLOR = mapOf(
        "TSTM" to 0xffc1e9c1.toInt(), "MRGL" to 0xff66a366.toInt(), "SLGT" to 0xffffe066.toInt(),
        "ENH" to 0xffffa366.toInt(), "MDT" to 0xffe06666.toInt(), "HIGH" to 0xffee99ee.toInt(),
    )
    val CAT_NAME = mapOf(
        "TSTM" to "General thunderstorms", "MRGL" to "Marginal risk", "SLGT" to "Slight risk",
        "ENH" to "Enhanced risk", "MDT" to "Moderate risk", "HIGH" to "High risk",
    )
    val CAT_LEVEL = mapOf("TSTM" to "", "MRGL" to "1 of 5", "SLGT" to "2 of 5", "ENH" to "3 of 5", "MDT" to "4 of 5", "HIGH" to "5 of 5")
    const val NONE_COLOR = 0xff3a3d46.toInt()
    const val MD_COLOR = 0xff4f8fff.toInt()
    const val TOR_WATCH_COLOR = 0xffffd400.toInt()
    const val SVR_WATCH_COLOR = 0xffdb7093.toInt()

    fun catIndex(t: String) = CATEGORIES.indexOf(t)
    fun catLabel(t: String?): String = if (t == null) "No thunderstorms forecast" else
        CAT_NAME[t]!! + (CAT_LEVEL[t]?.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")

    fun pad4(n: Int) = String.format(Locale.US, "%04d", n)

    // ------------------------------------------------------------------ convective outlooks (pages)
    fun outlookPage(day: Int) = "$BASE/products/outlook/day${day}otlk.html"
    const val DAY48_PAGE = "$BASE/products/exper/day4-8/"
    fun afosUrl(pil: String) = "$IEM/cgi-bin/afos/retrieve.py?pil=$pil&fmt=text&limit=1"

    /** The issuance time ("1300") of the outlook a day-N page shows: the most-mentioned one. */
    fun issuanceTime(html: String, day: Int): String? {
        val counts = HashMap<String, Int>()
        for (m in Regex("""day${day}(?:prob)?otlk_(\d{4})|otlk_(\d{4})|day3prob_(\d{4})""").findAll(html)) {
            val t = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: continue
            counts[t] = (counts[t] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }

    fun outlookImages(day: Int, time: String?): List<Img> {
        val u = "$BASE/products/outlook"
        fun img(label: String, timed: String, plain: String): Img {
            val l = ArrayList<String>()
            if (time != null) { l.add("$u/${timed.replace("#", time)}.png"); l.add("$u/${timed.replace("#", time)}.gif") }
            l.add("$u/$plain.png"); l.add("$u/$plain.gif")
            return Img(label, l)
        }
        return if (day <= 2) listOf(
            img("Categorical", "day${day}otlk_#", "day${day}otlk"),
            img("Tornado", "day${day}probotlk_#_torn", "day${day}probotlk_torn"),
            img("Wind", "day${day}probotlk_#_wind", "day${day}probotlk_wind"),
            img("Hail", "day${day}probotlk_#_hail", "day${day}probotlk_hail"),
        ) else listOf(
            img("Categorical", "day3otlk_#", "day3otlk"),
            img("Probabilistic", "day3prob_#", "day3prob"),
        )
    }

    /** Rows of the "risk area / area / population / cities" tables under an outlook map. */
    fun riskTable(html: String): List<List<String>> {
        val lead = Regex("""^(HIGH|MODERATE|ENHANCED|SLIGHT|MARGINAL|TSTM|GENERAL|SIGNIFICANT|SIG|CIG\d|\d+\s*%)""", RegexOption.IGNORE_CASE)
        return Html.tableRows(html).filter { r -> r.size >= 3 && lead.containsMatchIn(r[0]) && r[1].any { it.isDigit() } }
    }

    fun mainText(texts: List<String>, prefer: String): String? =
        texts.firstOrNull { it.contains(prefer, ignoreCase = true) } ?: texts.maxByOrNull { it.length }

    fun loadOutlook(day: Int): Product {
        val page = outlookPage(day)
        var html = ""
        try { html = Net.getPage(page) } catch (_: Exception) {}
        val time = issuanceTime(html, day)
        val texts = Html.pres(html)
        var text = mainText(texts, "Convective Outlook")
        if (text == null) text = try { Net.getText(afosUrl("SWODY$day")) } catch (_: Exception) { null }
        val title = Html.title(html).ifBlank { "Day $day Convective Outlook" }
        val found = Html.images(html, page) { it.startsWith("day$day") }.sortedBy { if (it.contains("sig")) 1 else 0 }
        val imgs = outlookImages(day, time).map { img ->
            val pick: (String) -> Boolean = when (img.label) {
                "Tornado" -> { u -> u.contains("torn") }
                "Wind" -> { u -> u.contains("wind") }
                "Hail" -> { u -> u.contains("hail") }
                "Probabilistic" -> { u -> u.contains("prob") }
                else -> { u -> u.substringAfterLast('/').startsWith("day${day}otlk") }
            }
            img.copy(urls = (found.filter(pick) + img.urls).distinct())
        }
        return Product(title, page, imgs, listOfNotNull(text), riskTable(html))
    }

    fun loadDay48(): Product {
        var html = ""
        try { html = Net.getPage(DAY48_PAGE) } catch (_: Exception) {}
        val imgs = (4..8).map { d -> Img("Day $d", listOf("$BASE/products/exper/day4-8/day${d}prob.gif", "$BASE/products/exper/day4-8/day${d}prob.png")) }
        var text = mainText(Html.pres(html), "Day 4-8")
        if (text == null) text = try { Net.getText(afosUrl("SWOD48")) } catch (_: Exception) { null }
        return Product("Day 4–8 Convective Outlook", DAY48_PAGE, imgs, listOfNotNull(text))
    }

    // ------------------------------------------------------------------ outlook polygons (for "your location")
    class OutlookArea(val category: String, val threshold: String, val rings: List<FloatArray>, val issueMs: Long, val expireMs: Long) {
        private val box = Geo.ringsBox(rings)
        fun contains(lat: Double, lon: Double) =
            lat >= box[0] && lat <= box[1] && lon >= box[2] && lon <= box[3] && Geo.ringsContain(rings, lat, lon)
    }

    /** IEM's archive of SPC outlooks as GeoJSON: candidate URLs for day 1/2/3, newest first. */
    fun outlookGeoUrls(nowMs: Long, day: Int): List<String> {
        val utc = TimeZone.getTimeZone("UTC")
        fun date(c: Calendar) = String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        if (day <= 1) {
            val cands = ArrayList<Pair<Long, String>>()
            for (back in 0..1) {
                val d = Calendar.getInstance(utc).apply {
                    timeInMillis = nowMs
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_MONTH, -back)
                }
                for ((cycle, minutes) in listOf(6 to 360, 13 to 780, 16 to 990, 20 to 1200, 1 to 1500)) {
                    val t = d.timeInMillis + minutes * 60_000L
                    if (t - 45 * 60_000L <= nowMs) cands.add(t to "$IEM/api/1/nws/spc_outlook.geojson?day=1&valid=${date(d)}&cycle=$cycle")
                }
            }
            return cands.sortedByDescending { it.first }.take(4).map { it.second }
        }
        val cycles = if (day == 3) listOf(20, 19, 8, 7) else listOf(17, 6, 7, 1)
        val base = Calendar.getInstance(utc).apply { timeInMillis = nowMs; if (get(Calendar.HOUR_OF_DAY) < 6) add(Calendar.DAY_OF_MONTH, -1) }
        val out = ArrayList<String>()
        for (back in 0..1) {
            val d = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, day - 1 - back) }
            for (c in cycles) out.add("$IEM/api/1/nws/spc_outlook.geojson?day=$day&valid=${date(d)}&cycle=$c")
        }
        return out
    }

    fun parseOutlookGeo(json: String): List<OutlookArea> {
        val out = ArrayList<OutlookArea>()
        for (f in Json.parse(json).obj()["features"].arr()) {
            val fo = f.obj(); val p = fo["properties"].obj()
            val r = Geo.rings(fo["geometry"].obj())
            if (r.isEmpty()) continue
            out.add(OutlookArea(p["category"].str().uppercase(Locale.US), p["threshold"].str().uppercase(Locale.US), r,
                Time.parseIso(p["issue"].str()), Time.parseIso(p["expire"].str())))
        }
        return out
    }

    fun loadOutlookAreas(day: Int, now: Long = System.currentTimeMillis()): List<OutlookArea> {
        for (u in outlookGeoUrls(now, day)) {
            try {
                val a = parseOutlookGeo(Net.getText(u))
                if (a.isNotEmpty()) return a
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    class PointRisk(val day: Int, val category: String?, val probs: Map<String, String>, val intense: Map<String, String>, val issueMs: Long) {
        val color: Int get() = category?.let { CAT_COLOR[it] } ?: NONE_COLOR
        fun probLine(): String {
            if (probs.isEmpty()) return if (category == null) "" else "No severe-weather probabilities here"
            return probs.entries.joinToString(" · ") { (k, v) ->
                val name = when (k) { "TORNADO" -> "Tornado"; "WIND" -> "Wind"; "HAIL" -> "Hail"; else -> "Severe" }
                "$name $v" + (intense[k]?.let { " ($it)" } ?: "")
            }
        }
    }

    private fun probText(t: String): String? = t.toDoubleOrNull()?.let { "${Math.round(it * 100)}%" }

    fun riskAt(day: Int, areas: List<OutlookArea>, lat: Double, lon: Double): PointRisk {
        var best: String? = null
        val probs = LinkedHashMap<String, Pair<Double, String>>()
        val intense = LinkedHashMap<String, String>()
        var issue = 0L
        for (a in areas) {
            issue = maxOf(issue, a.issueMs)
            if (!a.contains(lat, lon)) continue
            if (a.category == "CATEGORICAL") {
                val i = catIndex(a.threshold)
                if (i >= 0 && (best == null || i > catIndex(best))) best = a.threshold
            } else {
                val v = a.threshold.toDoubleOrNull()
                if (v != null) {
                    if (v > (probs[a.category]?.first ?: -1.0)) probs[a.category] = v to probText(a.threshold)!!
                } else if (a.threshold == "SIGN") intense[a.category] = "significant"
                else if (a.threshold.startsWith("CIG")) {
                    val cur = intense[a.category]
                    if (cur == null || cur < a.threshold) intense[a.category] = a.threshold
                }
            }
        }
        val order = listOf("TORNADO", "WIND", "HAIL")
        val sorted = LinkedHashMap<String, String>()
        for (k in order) probs[k]?.let { sorted[k] = it.second }
        for ((k, v) in probs) if (k !in order) sorted[k] = v.second
        return PointRisk(day, best, sorted, intense, issue)
    }

    fun highestCategory(areas: List<OutlookArea>): String? =
        areas.filter { it.category == "CATEGORICAL" && catIndex(it.threshold) >= 0 }.maxByOrNull { catIndex(it.threshold) }?.threshold

    // ------------------------------------------------------------------ mesoscale discussions
    class Md(
        val number: Int, val productId: String, val issueMs: Long, val expireMs: Long, val concerning: String,
        val watchChance: Int?, val rings: List<FloatArray>,
    ) {
        private val box = if (rings.isEmpty()) null else Geo.ringsBox(rings)
        fun contains(lat: Double, lon: Double): Boolean {
            val b = box ?: return false
            return lat >= b[0] && lat <= b[1] && lon >= b[2] && lon <= b[3] && Geo.ringsContain(rings, lat, lon)
        }
    }

    const val MD_INDEX = "$BASE/products/md/"
    const val MD_GEO = "$IEM/api/1/nws/spc_mcd.geojson"
    fun mdPage(n: Int) = "$BASE/products/md/md${pad4(n)}.html"
    fun mdYearPage(n: Int, year: Int) = "$BASE/products/md/$year/md${pad4(n)}.html"
    fun nwsTextUrl(productId: String) = "$IEM/api/1/nwstext/$productId"

    fun parseMds(json: String): List<Md> {
        val out = ArrayList<Md>()
        for (f in Json.parse(json).obj()["features"].arr()) {
            val fo = f.obj(); val p = fo["properties"].obj()
            val n = p["num"].num().let { if (it.isNaN()) return@let -1 else it.toInt() }
            if (n < 0) continue
            val wc = p["watch_confidence"].num()
            out.add(Md(n, p["product_id"].str(), Time.parseIso(p["issue"].str()), Time.parseIso(p["expire"].str()),
                p["concerning"].str().trim(), if (wc.isNaN()) null else wc.toInt(), Geo.rings(fo["geometry"].obj())))
        }
        return out.sortedByDescending { it.number }
    }

    /** Active MDs: IEM's list, plus any extra numbers SPC's own page lists. */
    fun loadMds(): List<Md> {
        val list = ArrayList<Md>()
        var iemOk = false
        try { list.addAll(parseMds(Net.getText(MD_GEO))); iemOk = true } catch (_: Exception) {}
        try {
            val html = Net.getPage(MD_INDEX)
            val nums = Html.matches(html, Regex("""md(\d{4})\.html""")).mapNotNull { it.toIntOrNull() }
            if (!html.contains("No Mesoscale Discussions are currently in effect", true)) {
                for (n in nums) if (list.none { it.number == n }) list.add(Md(n, "", 0, 0, "", null, emptyList()))
            } else if (!iemOk) list.clear()
        } catch (_: Exception) {}
        val now = System.currentTimeMillis()
        return list.filter { it.expireMs == 0L || it.expireMs > now }.sortedByDescending { it.number }
    }

    fun loadMdDetail(md: Md): Product {
        val n4 = pad4(md.number)
        val year = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { if (md.issueMs > 0) timeInMillis = md.issueMs }.get(Calendar.YEAR)
        var html = ""
        var page = mdPage(md.number)
        for (u in listOf(mdPage(md.number), mdYearPage(md.number, year))) {
            try { html = Net.getPage(u); page = u; break } catch (_: Exception) {}
        }
        val found = Html.images(html, page) { it.contains(n4) }
        val urls = LinkedHashSet<String>(found)
        urls.add("$BASE/products/md/mcd$n4.png"); urls.add("$BASE/products/md/$year/mcd$n4.png"); urls.add("$BASE/products/md/mcd$n4.gif")
        var text = mainText(Html.pres(html), "Mesoscale Discussion")
        if (text == null && md.productId.isNotEmpty()) text = try { Net.getText(nwsTextUrl(md.productId)) } catch (_: Exception) { null }
        return Product("Mesoscale Discussion #${md.number}", page, listOf(Img("Mesoscale Discussion $n4", urls.toList())), listOfNotNull(text))
    }

    // ------------------------------------------------------------------ watches
    class Watch(
        val number: Int,
        var type: String,              // "TO" tornado, "SV" severe thunderstorm, "" unknown
        var expiresMs: Long,
        val states: MutableSet<String> = sortedSetOf(),
        var counties: Int = 0,
        var pds: Boolean = false,
        var coversYou: Boolean = false,
    ) {
        val name: String get() = when (type) { "TO" -> "Tornado Watch"; "SV" -> "Severe Thunderstorm Watch"; else -> "Watch" } + " $number"
        val color: Int get() = if (type == "TO") TOR_WATCH_COLOR else if (type == "SV") SVR_WATCH_COLOR else 0xffaaaaaa.toInt()
    }

    const val WATCH_INDEX = "$BASE/products/watch/"
    fun watchPage(n: Int) = "$BASE/products/watch/ww${pad4(n)}.html"
    const val NWS_WATCHES = "$NWS/alerts/active?status=actual&event=Tornado%20Watch,Severe%20Thunderstorm%20Watch"
    fun nwsPointAlerts(lat: Double, lon: Double) = String.format(Locale.US, "$NWS/alerts/active?status=actual&point=%.4f,%.4f", lat, lon)

    private val VTEC = Regex("""\.(TO|SV)\.A\.(\d{4})\.""")

    /** NWS watch alerts (one per office / county list) grouped by watch number. */
    fun parseNwsWatches(json: String, into: MutableMap<Int, Watch>, markCovers: Boolean = false) {
        for (f in Json.parse(json).obj()["features"].arr()) {
            val p = f.obj()["properties"].obj()
            val ev = p["event"].str()
            if (!ev.endsWith("Watch")) continue
            val vt = p["parameters"].obj()["VTEC"].arr().map { it.str() }.firstNotNullOfOrNull { VTEC.find(it) } ?: continue
            val num = vt.groupValues[2].toInt()
            val w = into.getOrPut(num) { Watch(num, vt.groupValues[1], 0) }
            w.type = vt.groupValues[1]
            val exp = Time.parseIso(p["ends"].str()).takeIf { it > 0 } ?: Time.parseIso(p["expires"].str())
            if (exp > w.expiresMs) w.expiresMs = exp
            if (markCovers) { w.coversYou = true; continue }
            val areas = p["areaDesc"].str().split(';').map { it.trim() }.filter { it.isNotEmpty() }
            w.counties += areas.size
            for (a in areas) Regex(""",\s*([A-Z]{2})$""").find(a)?.let { w.states.add(it.groupValues[1]) }
        }
    }

    fun loadWatches(lat: Double?, lon: Double?): List<Watch> {
        val map = LinkedHashMap<Int, Watch>()
        try { parseNwsWatches(Net.getText(NWS_WATCHES, accept = "application/geo+json"), map) } catch (_: Exception) {}
        try {
            val html = Net.getPage(WATCH_INDEX)
            val none = Regex("""(?i)no\s+(severe thunderstorm or tornado\s+|convective\s+)?watches\s+are\s+(currently\s+)?(valid|in effect)""").containsMatchIn(Html.strip(html))
            if (!none) for (n in Html.matches(html, Regex("""ww(\d{4})\.html""")).mapNotNull { it.toIntOrNull() }) {
                if (n > 0 && n !in map) map[n] = Watch(n, typeFromIndex(html, n), 0)
            }
        } catch (_: Exception) {}
        if (lat != null && lon != null) {
            try { parseNwsWatches(Net.getText(nwsPointAlerts(lat, lon), accept = "application/geo+json"), map, markCovers = true) } catch (_: Exception) {}
        }
        return map.values.sortedByDescending { it.number }
    }

    /** "Tornado Watch 123" / "Severe Thunderstorm Watch 123" near the link on SPC's index page. */
    private fun typeFromIndex(html: String, n: Int): String {
        val m = Regex("""(Tornado|Severe Thunderstorm) Watch\s*#?\s*0*$n\b""", RegexOption.IGNORE_CASE).find(html) ?: return ""
        return if (m.groupValues[1].startsWith("T", true)) "TO" else "SV"
    }

    fun loadWatchDetail(w: Watch): Product {
        val n4 = pad4(w.number)
        val page = watchPage(w.number)
        var html = ""
        try { html = Net.getPage(page) } catch (_: Exception) {}
        val urls = LinkedHashSet<String>(Html.images(html, page) { it.contains(n4) })
        urls.add("$BASE/products/watch/ww${n4}_radar.gif"); urls.add("$BASE/products/watch/ww${n4}_radar.png")
        val texts = Html.pres(html)
        val all = texts.joinToString("\n")
        if (all.contains("PARTICULARLY DANGEROUS SITUATION", true)) w.pds = true
        if (w.type.isEmpty()) w.type = when {
            all.contains("TORNADO WATCH", true) -> "TO"
            all.contains("SEVERE THUNDERSTORM WATCH", true) -> "SV"
            else -> ""
        }
        val probs = Html.tableRows(html).filter { r -> r.size >= 2 && r.any { it.contains('%') } &&
            Regex("""(?i)tornado|hail|wind|prob""").containsMatchIn(r[0]) }
        val images = urls.toList().let { l ->
            val radar = l.filter { it.contains("radar") }
            val rest = l.filter { !it.contains("radar") }
            listOf(Img("Watch $n4", radar.ifEmpty { l })) + rest.map { Img(it.substringAfterLast('/'), listOf(it)) }.take(2)
        }
        return Product(Html.title(html).ifBlank { w.name }, page, images, texts, probs)
    }

    // ------------------------------------------------------------------ storm reports
    data class Report(
        val kind: Char,        // 'T' tornado, 'W' wind, 'H' hail
        val hhmm: String, val magnitude: String, val location: String, val county: String, val state: String,
        val lat: Double, val lon: Double, val comments: String,
    ) {
        val title: String get() = when (kind) {
            'T' -> "Tornado" + (if (magnitude.isNotBlank() && !magnitude.equals("UNK", true)) " ($magnitude)" else "")
            'W' -> "Wind" + (magnitude.toIntOrNull()?.let { " $it mph" } ?: if (magnitude.equals("UNK", true)) " damage" else "")
            else -> "Hail" + (magnitude.toIntOrNull()?.let { String.format(Locale.US, " %.2f\"", it / 100.0) } ?: "")
        }
        /** EF2+ tornado, 75+ mph wind, 2"+ hail. */
        val significant: Boolean get() = when (kind) {
            'T' -> Regex("""[2-5]""").containsMatchIn(magnitude)
            'W' -> (magnitude.toIntOrNull() ?: 0) >= 75
            else -> (magnitude.toIntOrNull() ?: 0) >= 200
        }
    }

    fun reportsCsv(day: String) = "$BASE/climo/reports/$day.csv"
    fun reportsImage(day: String) = Img("Storm reports", listOf("$BASE/climo/reports/$day.gif", "$BASE/climo/reports/$day.png"))
    fun reportsPage(day: String) = "$BASE/climo/reports/$day.html"

    fun parseReports(csv: String): List<Report> {
        val out = ArrayList<Report>()
        var kind = ' '
        for (raw in csv.replace("\r", "").lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("Time,", true)) {
                val second = line.split(',').getOrNull(1)?.trim()?.lowercase(Locale.US) ?: ""
                kind = when { second.startsWith("f") || second.startsWith("ef") -> 'T'; second.startsWith("speed") -> 'W'; second.startsWith("size") -> 'H'; else -> ' ' }
                continue
            }
            if (kind == ' ') continue
            val c = line.split(',', limit = 8)
            if (c.size < 7) continue
            out.add(Report(kind, c[0].trim(), c[1].trim(), c[2].trim(), c[3].trim(), c[4].trim(),
                c[5].trim().toDoubleOrNull() ?: Double.NaN, c[6].trim().toDoubleOrNull() ?: Double.NaN, c.getOrNull(7)?.trim() ?: ""))
        }
        return out
    }

    // ------------------------------------------------------------------ other pages (fire weather, thunderstorm outlooks...)
    val FIRE_PAGES = listOf(
        "Day 1" to "$BASE/products/fire_wx/fwdy1.html",
        "Day 2" to "$BASE/products/fire_wx/fwdy2.html",
        "Day 3–8" to "$BASE/products/exper/fire_wx/",
    )
    val TSTM_PAGES = listOf("Thunderstorm outlooks" to "$BASE/products/exper/enhtstm/")

    /** Any SPC product page: its graphics (names guessed from the page) and its text. */
    fun loadPage(label: String, url: String, keep: (String) -> Boolean = { true }): Product {
        val html = Net.getPage(url)
        val imgs = Html.images(html, url, keep).map { Img(prettyName(it), listOf(it)) }
        return Product(Html.title(html).ifBlank { label }, url, imgs, Html.pres(html))
    }

    private fun prettyName(url: String): String = url.substringAfterLast('/').substringBeforeLast('.')
        .replace('_', ' ').replace(Regex("""(?i)^day(\d)"""), "Day $1 ")

    // ------------------------------------------------------------------ mesoanalysis
    val MESO_SECTORS = listOf(
        "19" to "National", "11" to "Northwest", "12" to "Southwest", "13" to "N. Plains", "14" to "C. Plains",
        "15" to "S. Plains", "16" to "Northeast", "17" to "Mid-Atlantic", "18" to "Southeast", "20" to "Midwest",
    )

    val MESO_PARAMS: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "Surface" to listOf("bigsfc" to "Surface observations", "pmsl" to "MSL pressure & wind", "ttd" to "Temp, dewpoint & wind",
            "thea" to "Theta-e advection", "mcon" to "Moisture convergence", "pwtr" to "Precipitable water"),
        "Upper air" to listOf("850mb" to "850 mb", "700mb" to "700 mb", "500mb" to "500 mb", "300mb" to "300 mb"),
        "Thermodynamics" to listOf("sbcp" to "Surface-based CAPE", "mlcp" to "Mixed-layer CAPE", "mucp" to "Most-unstable CAPE",
            "3cape" to "0–3 km CAPE", "dcape" to "Downdraft CAPE", "lclh" to "LCL height", "lllr" to "Low-level lapse rates",
            "laps" to "Mid-level lapse rates"),
        "Wind shear" to listOf("shr6" to "0–6 km shear", "eshr" to "Effective shear", "srh1" to "0–1 km SRH",
            "srh3" to "0–3 km SRH", "effh" to "Effective SRH"),
        "Composite indices" to listOf("stor" to "Sig. tornado (fixed)", "stpc" to "Sig. tornado (effective)", "scp" to "Supercell composite",
            "ship" to "Sig. hail", "dcp" to "Derecho composite", "sherbe" to "SHERBE", "moshe" to "MOSH"),
    )

    fun mesoImage(sector: String, param: String, label: String) =
        Img(label, listOf("$BASE/exper/mesoanalysis/s$sector/$param/$param.gif", "$BASE/exper/mesoanalysis/s$sector/$param/$param.png"), whiteBg = true)

    fun mesoPage(sector: String) = "$BASE/exper/mesoanalysis/new/viewsector.php?sector=$sector"
}
