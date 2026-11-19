package com.libexil.spcforge.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object Time {
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    private fun fmt(p: String, tz: TimeZone = TimeZone.getDefault()) = java.text.SimpleDateFormat(p, Locale.US).apply { timeZone = tz }

    /** "9:41 PM" in the phone's time zone. */
    fun local(ms: Long): String = fmt("h:mm a").format(java.util.Date(ms))
    /** "Wed 9:41 PM" */
    fun localDay(ms: Long): String = fmt("EEE h:mm a").format(java.util.Date(ms))
    /** "Wed Nov 18" */
    fun localDate(ms: Long): String = fmt("EEE MMM d").format(java.util.Date(ms))
    fun zulu(ms: Long): String = fmt("HHmm'Z'", UTC).format(java.util.Date(ms))

    /** "today 9:41 PM", "Thu 3:00 AM" */
    fun friendly(ms: Long, now: Long = System.currentTimeMillis()): String {
        if (ms <= 0) return "?"
        val a = Calendar.getInstance().apply { timeInMillis = ms }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) local(ms) else localDay(ms)
    }

    fun age(ms: Long, now: Long = System.currentTimeMillis()): String {
        val s = (now - ms) / 1000
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86400 -> "${s / 3600} h ${(s % 3600) / 60} min ago"
            else -> "${s / 86400} d ago"
        }
    }

    /** ISO-8601 (2026-09-30T19:41:00-05:00, ...Z, with or without seconds / fraction); 0 on failure. */
    fun parseIso(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val t = s.trim().replace(Regex("""\.\d+"""), "").let { if (it.endsWith("Z")) it.dropLast(1) + "+00:00" else it }
        for (f in arrayOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mmXXX", "yyyy-MM-dd HH:mm:ssXXX")) {
            try { return java.text.SimpleDateFormat(f, Locale.US).parse(t)?.time ?: continue } catch (_: Exception) {}
        }
        return 0
    }

    /**
     * The start (12 UTC) of SPC's "convective day" containing [nowMs]: storm reports for "today" run
     * 12 UTC to 1159 UTC the next morning.
     */
    fun convectiveDayStart(nowMs: Long): Long {
        val c = Calendar.getInstance(UTC).apply { timeInMillis = nowMs }
        if (c.get(Calendar.HOUR_OF_DAY) < 12) c.add(Calendar.DAY_OF_MONTH, -1)
        c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** A report time "HHMM" (UTC) within the convective day starting at [dayStart] → epoch ms. */
    fun reportTime(hhmm: String, dayStart: Long): Long {
        val v = hhmm.trim().toIntOrNull() ?: return 0
        val h = v / 100; val m = v % 100
        if (h > 23 || m > 59) return 0
        var mins = h * 60 + m - 12 * 60
        if (mins < 0) mins += 24 * 60
        return dayStart + mins * 60_000L
    }
}

object Geo {
    fun ringsContain(rings: List<FloatArray>, lat: Double, lon: Double): Boolean {
        var inside = false
        for (r in rings) {
            val n = r.size / 2
            if (n < 3) continue
            var j = n - 1
            for (i in 0 until n) {
                val xi = r[2 * i]; val yi = r[2 * i + 1]
                val xj = r[2 * j]; val yj = r[2 * j + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** minLat, maxLat, minLon, maxLon */
    fun ringsBox(rings: List<FloatArray>): FloatArray {
        var a = 999f; var b = -999f; var c = 999f; var d = -999f
        for (r in rings) {
            var i = 0
            while (i + 1 < r.size) {
                c = minOf(c, r[i]); d = maxOf(d, r[i]); a = minOf(a, r[i + 1]); b = maxOf(b, r[i + 1]); i += 2
            }
        }
        return floatArrayOf(a, b, c, d)
    }

    /** GeoJSON Polygon / MultiPolygon → rings of lon,lat pairs. */
    fun rings(g: Map<String, Any?>): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        fun ring(r: Any?) {
            val pts = r.arr()
            val a = FloatArray(pts.size * 2)
            var n = 0
            for (pt in pts) {
                val c = pt.arr()
                val lon = c.getOrNull(0).num(); val lat = c.getOrNull(1).num()
                if (lon.isNaN() || lat.isNaN()) continue
                a[n++] = lon.toFloat(); a[n++] = lat.toFloat()
            }
            if (n >= 6) out.add(if (n == a.size) a else a.copyOf(n))
        }
        val coords = g["coordinates"].arr()
        when (g["type"].str()) {
            "Polygon" -> coords.forEach { ring(it) }
            "MultiPolygon" -> coords.forEach { poly -> poly.arr().forEach { ring(it) } }
        }
        return out
    }
}

/** Tiny time-limited cache so switching screens doesn't download everything again. */
object Repo {
    private class Entry(val at: Long, val value: Any?)
    private val map = HashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, ttlMs: Long, force: Boolean, load: () -> T): T {
        val now = System.currentTimeMillis()
        if (!force) synchronized(map) { map[key] }?.let { if (now - it.at < ttlMs) return it.value as T }
        val v = load()
        synchronized(map) { map[key] = Entry(now, v) }
        return v
    }

    fun peekAge(key: String): Long? = synchronized(map) { map[key]?.at }
    fun clear() = synchronized(map) { map.clear() }
}
