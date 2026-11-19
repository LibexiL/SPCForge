package com.libexil.spcforge.core

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** National Hurricane Center (and Central Pacific Hurricane Center) products. */
object Nhc {
    const val BASE = "https://www.nhc.noaa.gov"
    const val CURRENT_STORMS = "$BASE/CurrentStorms.json"

    // ------------------------------------------------------------------ basins and outlooks
    class Basin(val key: String, val name: String, val short: String, val img: String, val twoPil: String, val twoPage: String,
                val twdPil: String?, val home: String)

    val BASINS = listOf(
        Basin("atl", "Atlantic", "Atlantic", "atl", "TWOAT", "$BASE/text/MIATWOAT.shtml", "TWDAT", "$BASE/?atlc"),
        Basin("epac", "Eastern Pacific", "E. Pacific", "pac", "TWOEP", "$BASE/text/MIATWOEP.shtml", "TWDEP", "$BASE/?epac"),
        Basin("cpac", "Central Pacific", "C. Pacific", "cpac", "TWOCP", "$BASE/text/HFOTWOCP.shtml", null, "$BASE/?cpac"),
    )

    fun outlookImage(b: Basin, days: Int) = Img(
        if (days == 7) "${b.name} 7-day outlook" else "${b.name} 2-day outlook",
        listOf("$BASE/xgtwo/two_${b.img}_${days}d0.png", "$BASE/xgtwo/two_${b.img}_${days}d0.gif"),
    )

    /** The Tropical Weather Outlook text for a basin (NHC's page, then the IEM archive). */
    fun loadOutlookText(b: Basin): String? {
        try { Spc.mainText(Html.pres(Net.getPage(b.twoPage)), "Tropical Weather Outlook")?.let { if (it.length > 80) return it } } catch (_: Exception) {}
        return try { Net.getText(Spc.afosUrl(b.twoPil)).takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    fun loadDiscussion(b: Basin): String? {
        val pil = b.twdPil ?: return null
        try { Spc.mainText(Html.pres(Net.getPage("$BASE/text/MIA$pil.shtml")), "Tropical Weather Discussion")?.let { if (it.length > 80) return it } } catch (_: Exception) {}
        return try { Net.getText(Spc.afosUrl(pil)).takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    // ------------------------------------------------------------------ active storms
    class Storm(
        val id: String,            // "al052026"
        val bin: String,           // "AT5"
        val name: String,
        val classification: String,// HU, TS, TD, STS, SD, PTC, PC
        val windKt: Int?,
        val pressureMb: Int?,
        val lat: Double, val lon: Double,
        val movementDir: Int?,
        val updatedMs: Long,
        val advisoryNum: String,
        val publicUrl: String?, val discussionUrl: String?, val forecastUrl: String?, val probsUrl: String?, val graphicsUrl: String?,
    ) {
        val basin: String get() = when (id.take(2).lowercase(Locale.US)) { "al" -> "Atlantic"; "ep" -> "Eastern Pacific"; "cp" -> "Central Pacific"; else -> "" }
        val windMph: Int? get() = windKt?.let { mph(it) }
        val category: Int? get() = windKt?.let { categoryOf(it) }

        val kind: String get() = when (classification.uppercase(Locale.US)) {
            "HU" -> if ((category ?: 0) >= 3) "Major Hurricane" else "Hurricane"
            "TS" -> "Tropical Storm"
            "TD" -> "Tropical Depression"
            "STS", "SS" -> "Subtropical Storm"
            "SD" -> "Subtropical Depression"
            "PTC" -> "Potential Tropical Cyclone"
            "PC" -> "Post-Tropical Cyclone"
            "TY" -> "Typhoon"
            else -> classification
        }
        /** "Hurricane Milton", "Tropical Depression Nine", "Potential Tropical Cyclone Three" */
        val title: String get() = when {
            name.isBlank() -> kind
            name.startsWith(kind, true) || name.contains(' ') -> name
            else -> "$kind $name"
        }
        val color: Int get() = when (classification.uppercase(Locale.US)) {
            "HU" -> when (category ?: 1) { 1 -> 0xffff6b4a.toInt(); 2 -> 0xffff3b3b.toInt(); 3 -> 0xffe0306e.toInt(); 4 -> 0xffc23bd6.toInt(); else -> 0xff9b59ff.toInt() }
            "TS" -> 0xffffc233.toInt()
            "TD" -> 0xff4fa3ff.toInt()
            "STS", "SS", "SD" -> 0xff3cc8b4.toInt()
            else -> 0xff9aa0ad.toInt()
        }
        val badge: String get() = when {
            classification.equals("HU", true) -> "CAT ${category ?: 1}"
            else -> classification.uppercase(Locale.US)
        }
        /** Folder of the storm's graphics, e.g. storm_graphics/AT05. */
        val graphicsDir: String get() {
            val m = Regex("""^([A-Za-z]{2})(\d+)$""").find(bin) ?: return "$BASE/storm_graphics/${bin.uppercase(Locale.US)}"
            return "$BASE/storm_graphics/${m.groupValues[1].uppercase(Locale.US)}${String.format(Locale.US, "%02d", m.groupValues[2].toInt())}"
        }

        fun stats(): String = listOfNotNull(
            windMph?.let { "$it mph" },
            pressureMb?.let { "$it mb" },
            movementDir?.let { "moving ${compass(it.toDouble())}" },
        ).joinToString(" · ")
    }

    fun mph(kt: Int): Int = (kt * 1.15078 / 5.0).roundToInt() * 5

    /** Saffir-Simpson category from sustained wind in knots (null below hurricane strength). */
    fun categoryOf(kt: Int): Int? = when {
        kt >= 137 -> 5; kt >= 113 -> 4; kt >= 96 -> 3; kt >= 83 -> 2; kt >= 64 -> 1; else -> null
    }

    private fun https(u: String): String? = u.trim().takeIf { it.startsWith("http") }?.replaceFirst(Regex("^http://"), "https://")

    private fun coord(num: Any?, text: Any?, negSuffix: Char): Double {
        val n = num.num()
        if (!n.isNaN()) return n
        val t = text.str().trim().uppercase(Locale.US)
        val v = t.trimEnd('N', 'S', 'E', 'W').toDoubleOrNull() ?: return Double.NaN
        return if (t.endsWith(negSuffix)) -v else v
    }

    private fun int(v: Any?): Int? {
        val d = v.num()
        if (!d.isNaN()) return d.roundToInt()
        return v.str().trim().toIntOrNull()
    }

    fun parseStorms(json: String): List<Storm> {
        val out = ArrayList<Storm>()
        for (s in Json.parse(json).obj()["activeStorms"].arr()) {
            val o = s.obj()
            val id = o["id"].str()
            if (id.isBlank()) continue
            fun url(k: String) = o[k].obj()["url"].str().let { https(it) }
            out.add(Storm(
                id = id, bin = o["binNumber"].str(), name = o["name"].str().trim(), classification = o["classification"].str().trim(),
                windKt = int(o["intensity"]), pressureMb = int(o["pressure"]),
                lat = coord(o["latitudeNumeric"], o["latitude"], 'S'), lon = coord(o["longitudeNumeric"], o["longitude"], 'W'),
                movementDir = int(o["movementDir"]), updatedMs = Time.parseIso(o["lastUpdate"].str()),
                advisoryNum = o["publicAdvisory"].obj()["advNum"].str().trim(),
                publicUrl = url("publicAdvisory"), discussionUrl = url("forecastDiscussion"), forecastUrl = url("forecastAdvisory"),
                probsUrl = url("windSpeedProbabilities"), graphicsUrl = url("forecastGraphics"),
            ))
        }
        // Atlantic first, then strongest first
        return out.sortedWith(compareBy<Storm>({ it.id.take(2).lowercase(Locale.US) != "al" }, { -(it.windKt ?: 0) }))
    }

    fun loadStorms(): List<Storm> = parseStorms(Net.getText(CURRENT_STORMS))

    // ------------------------------------------------------------------ one storm
    /** The storm's graphics suite; any that NHC doesn't make for this storm just don't show. */
    fun stormImages(s: Storm): List<Img> {
        val d = s.graphicsDir
        val id = s.id.uppercase(Locale.US)
        fun img(label: String, vararg names: String) = Img(label, names.map { "$d/${id}_$it" })
        return listOf(
            img("Forecast cone", "5day_cone_with_line_and_wind.png", "3day_cone_with_line_and_wind.png", "5day_cone_no_line_and_wind.png"),
            img("Key messages", "key_messages.png"),
            img("Current wind field", "current_wind.png"),
            img("Most likely arrival of tropical-storm winds", "most_likely_toa_34.png"),
            img("Earliest reasonable arrival of tropical-storm winds", "earliest_reasonable_toa_34.png"),
            img("Chance of tropical-storm-force winds (39+ mph)", "wind_probs_34_F120.png"),
            img("Chance of 58+ mph winds", "wind_probs_50_F120.png"),
            img("Chance of hurricane-force winds (74+ mph)", "wind_probs_64_F120.png"),
            img("Peak storm surge forecast", "peak_surge.png"),
            img("Rainfall potential", "rainfall.png"),
        )
    }

    enum class TextKind(val label: String, val pilPrefix: String) {
        PUBLIC("Public advisory", "TCP"), DISCUSSION("Discussion", "TCD"), FORECAST("Forecast advisory", "TCM"), PROBS("Wind probabilities", "PWS")
    }

    fun loadStormText(s: Storm, k: TextKind): String? {
        val url = when (k) { TextKind.PUBLIC -> s.publicUrl; TextKind.DISCUSSION -> s.discussionUrl; TextKind.FORECAST -> s.forecastUrl; TextKind.PROBS -> s.probsUrl }
        if (url != null) try {
            Html.pres(Net.getPage(url)).maxByOrNull { it.length }?.let { if (it.length > 80) return it }
        } catch (_: Exception) {}
        if (s.bin.isBlank()) return null
        return try { Net.getText(Spc.afosUrl(k.pilPrefix + s.bin.uppercase(Locale.US))).takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    /** "LOCATION...26.6N 77.6W" style lines at the top of a public advisory. */
    fun summary(advisory: String): List<Pair<String, String>> {
        val keys = listOf("LOCATION", "ABOUT", "MAXIMUM SUSTAINED WINDS", "PRESENT MOVEMENT", "MINIMUM CENTRAL PRESSURE")
        val out = ArrayList<Pair<String, String>>()
        for (raw in advisory.lines()) {
            val l = raw.trim()
            if (l.startsWith("ABOUT ")) { out.add("About" to l.removePrefix("ABOUT ").replace("...", " · ")); continue }
            val m = Regex("""^([A-Z ]+)\.\.\.(.+)$""").find(l) ?: continue
            val k = m.groupValues[1].trim()
            if (k in keys) out.add(prettyKey(k) to m.groupValues[2].trim().replace("...", " · "))
        }
        return out
    }

    private fun prettyKey(k: String) = when (k) {
        "LOCATION" -> "Location"; "ABOUT" -> "About"; "MAXIMUM SUSTAINED WINDS" -> "Max winds"
        "PRESENT MOVEMENT" -> "Movement"; "MINIMUM CENTRAL PRESSURE" -> "Pressure"; else -> k
    }

    // ------------------------------------------------------------------ distance from the user
    fun milesBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.8
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Compass direction from point 1 toward point 2. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun compass(deg: Double): String {
        val pts = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return pts[((deg % 360 + 360) % 360 / 22.5 + 0.5).toInt() % 16]
    }

    /** "640 mi SE of you" */
    fun fromYou(s: Storm, lat: Double, lon: Double): String? {
        if (s.lat.isNaN() || s.lon.isNaN()) return null
        val d = milesBetween(lat, lon, s.lat, s.lon)
        val dir = compass(bearing(lat, lon, s.lat, s.lon))
        val miles = if (d < 100) d.roundToInt() else (d / 10).roundToInt() * 10
        return String.format(Locale.US, "%,d mi %s of you", miles, dir)
    }
}
