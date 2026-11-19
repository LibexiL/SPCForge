package com.libexil.spcforge.screens

import android.view.Gravity
import android.widget.LinearLayout
import com.libexil.spcforge.MainActivity
import com.libexil.spcforge.core.Repo
import com.libexil.spcforge.core.Spc
import com.libexil.spcforge.core.Time
import com.libexil.spcforge.ui.C
import com.libexil.spcforge.ui.Icon
import com.libexil.spcforge.ui.W
import com.libexil.spcforge.ui.dp
import com.libexil.spcforge.ui.dpi
import com.libexil.spcforge.ui.onColor
import com.libexil.spcforge.ui.rounded

private const val TTL = 4 * 60_000L

/** Cached loaders shared by the screens. */
object Data {
    fun outlook(day: Int, force: Boolean) = Repo.get("outlook$day", TTL, force) { if (day <= 3) Spc.loadOutlook(day) else Spc.loadDay48() }
    fun areas(day: Int, force: Boolean) = Repo.get("areas$day", TTL, force) { Spc.loadOutlookAreas(day) }
    fun mds(force: Boolean) = Repo.get("mds", 2 * 60_000L, force) { Spc.loadMds() }
    fun watches(a: MainActivity, force: Boolean): List<Spc.Watch> {
        val lat = if (a.prefs.hasLocation) a.prefs.lat else null
        val lon = if (a.prefs.hasLocation) a.prefs.lon else null
        return Repo.get("watches$lat,$lon", 2 * 60_000L, force) { Spc.loadWatches(lat, lon) }
    }
    fun reports(day: String, force: Boolean) = Repo.get("reports-$day", 3 * 60_000L, force) {
        Spc.parseReports(com.libexil.spcforge.core.Net.getText(Spc.reportsCsv(day)))
    }
}

class HomeScreen(a: MainActivity) : Screen(a) {
    override val title = "SPCForge"
    override val webUrl = Spc.BASE

    override fun render(force: Boolean) {
        val alerts = slot()
        val local = slot()
        body.addView(W.section(a, "Day 1 convective outlook"))
        val d1 = slot()
        body.addView(W.section(a, "Active now"))
        val active = slot()
        body.addView(W.section(a, "Today's storm reports"))
        val reports = slot()
        body.addView(W.section(a, "Coming days"))
        val ahead = slot()

        localRisk(local, force)
        day1(d1, force)
        activeNow(active, alerts, force)
        reportCounts(reports, force)
        comingDays(ahead, force)
    }

    // ---- your location
    private fun localRisk(into: LinearLayout, force: Boolean) {
        if (!a.prefs.hasLocation) {
            val c = W.card(a)
            c.addView(W.text(a, "Your risk today", 16f, C.text, true))
            c.addView(W.note(a, "See the outlook risk, watches and mesoscale discussions for where you are."))
            c.addView(W.button(a, "Use my location", primary = true) { a.locate { if (it) reload(false) } }, W.lp(top = a.dpi(10f)))
            into.addView(c)
            return
        }
        loading(into, "Checking your location's risk…")
        val lat = a.prefs.lat; val lon = a.prefs.lon
        async({ (1..3).map { d -> Spc.riskAt(d, Data.areas(d, force), lat, lon) } }) { r ->
            into.removeAllViews()
            val risks = r.getOrNull()
            if (risks == null) { failed(into, "your local risk", r.exceptionOrNull()); return@async }
            val today = risks[0]
            val c = W.card(a, onClick = { a.goTab(1, OutlookScreen(a, 1)) })
            val place = a.prefs.placeName.ifBlank { "your location" }
            c.addView(W.text(a, "Today at $place", 13f, C.dim, true))
            val banner = W.text(a, Spc.catLabel(today.category), 21f, onColor(today.color), true).apply {
                background = rounded(today.color, a.dp(10f))
                setPadding(a.dpi(14f), a.dpi(10f), a.dpi(14f), a.dpi(10f))
            }
            c.addView(banner, W.lp(top = a.dpi(8f)))
            val probs = today.probLine()
            if (probs.isNotEmpty()) c.addView(W.text(a, probs, 14.5f, C.text).apply { setPadding(0, a.dpi(8f), 0, 0) })
            for (rk in risks.drop(1)) {
                val row = W.row(a).apply { setPadding(0, a.dpi(8f), 0, 0) }
                row.addView(W.text(a, "Day ${rk.day}", 14f, C.dim).apply { minWidth = a.dpi(52f) })
                row.addView(W.pill(a, rk.category ?: "NONE", rk.color))
                row.addView(W.text(a, "  " + Spc.catLabel(rk.category).substringBefore(" ("), 14f, C.text))
                c.addView(row)
            }
            if (today.issueMs > 0) c.addView(W.note(a, "Outlook issued ${Time.friendly(today.issueMs)}").apply { setPadding(0, a.dpi(8f), 0, 0) })
            into.addView(c)
        }
    }

    // ---- day 1
    private fun day1(into: LinearLayout, force: Boolean) {
        val c = W.card(a, 10f, onClick = { a.goTab(1, OutlookScreen(a, 1)) })
        into.addView(c)
        val head = W.col(a).apply { setPadding(a.dpi(4f), 0, 0, a.dpi(8f)) }
        c.addView(head)
        val imgSlot = W.col(a)
        c.addView(imgSlot)
        loading(imgSlot)
        async({ Data.outlook(1, force) }) { r ->
            imgSlot.removeAllViews()
            val p = r.getOrNull()
            if (p == null) { failed(imgSlot, "the Day 1 outlook", r.exceptionOrNull()); return@async }
            image(imgSlot, p.images[0])
            imgSlot.addView(W.note(a, "Tap for tornado, wind and hail probabilities and the forecast discussion").apply { setPadding(a.dpi(4f), a.dpi(8f), 0, 0) })
        }
        async({ Data.areas(1, force) }) { r ->
            val areas = r.getOrNull() ?: return@async
            val hi = Spc.highestCategory(areas)
            val row = W.row(a)
            row.addView(W.text(a, "Highest risk:  ", 15f, C.text, true))
            row.addView(W.pill(a, if (hi == null) "None" else Spc.CAT_NAME[hi]!!, hi?.let { Spc.CAT_COLOR[it] } ?: Spc.NONE_COLOR, 13f))
            head.addView(row)
        }
    }

    // ---- watches and MDs
    private fun activeNow(into: LinearLayout, alerts: LinearLayout, force: Boolean) {
        loading(into)
        val loc = a.prefs.hasLocation
        async({ Data.watches(a, force) to Data.mds(force) }) { r ->
            into.removeAllViews()
            val (watches, mds) = r.getOrNull() ?: run { failed(into, "watches and discussions", r.exceptionOrNull()); return@async }
            // alerts for the user's location
            if (loc) {
                for (w in watches.filter { it.coversYou }) alerts.addView(W.listRow(a, "You're in ${w.name}",
                    if (w.expiresMs > 0) "Until ${Time.friendly(w.expiresMs)}" else null, bar = w.color, icon = Icon.WARNING) {
                    a.push(WatchScreen(a, w)) }, W.lp(bottom = a.dpi(8f)))
                for (m in mds.filter { it.contains(a.prefs.lat, a.prefs.lon) }) alerts.addView(W.listRow(a, "Mesoscale Discussion #${m.number} covers you",
                    m.concerning.ifBlank { null }, bar = Spc.MD_COLOR, icon = Icon.INFO) { a.push(MdScreen(a, m)) }, W.lp(bottom = a.dpi(8f)))
            }
            if (watches.isEmpty() && mds.isEmpty()) {
                into.addView(W.card(a).apply { addView(W.note(a, "No watches or mesoscale discussions are in effect.")) })
                return@async
            }
            for (w in watches.take(6)) into.addView(watchRow(a, w), W.lp(bottom = a.dpi(6f)))
            for (m in mds.take(6)) into.addView(mdRow(a, m), W.lp(bottom = a.dpi(6f)))
            if (watches.size > 6 || mds.size > 6) into.addView(W.button(a, "See all") { a.goTab(2, ActiveScreen(a, 0)) })
        }
    }

    // ---- reports
    private fun reportCounts(into: LinearLayout, force: Boolean) {
        val c = W.card(a, onClick = { a.goTab(3, ReportsScreen(a)) })
        into.addView(c)
        loading(c)
        async({ Data.reports("today", force) }) { r ->
            c.removeAllViews()
            val reps = r.getOrNull()
            if (reps == null) { c.addView(W.note(a, "Couldn't load today's reports")); return@async }
            c.addView(countsRow(a, reps))
            c.addView(W.note(a, "Since 7 AM CT (12Z) · tap for the map and list").apply { setPadding(0, a.dpi(8f), 0, 0); gravity = Gravity.CENTER })
        }
    }

    // ---- days 2 and 3
    private fun comingDays(into: LinearLayout, force: Boolean) {
        val row = W.row(a).apply { gravity = Gravity.TOP }
        into.addView(row)
        for (d in 2..3) {
            val c = W.card(a, 8f, onClick = { a.goTab(1, OutlookScreen(a, d)) })
            c.addView(W.text(a, "Day $d", 14f, C.text, true).apply { setPadding(a.dpi(2f), 0, 0, a.dpi(6f)) })
            val slot = W.col(a); c.addView(slot)
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { if (d == 2) rightMargin = a.dpi(8f) })
            async({ Data.outlook(d, force) }) { r -> r.getOrNull()?.let { image(slot, it.images[0]) } ?: slot.addView(W.note(a, "Not available")) }
        }
        into.addView(W.listRow(a, "Days 4–8", "Extended severe-weather outlook", icon = Icon.MAP) { a.goTab(1, OutlookScreen(a, 4)) },
            W.lp(top = a.dpi(8f)))
    }

    companion object {
        fun watchRow(a: MainActivity, w: Spc.Watch) = W.listRow(a, w.name + if (w.pds) " (PDS)" else "",
            listOfNotNull(
                if (w.expiresMs > 0) "Until ${Time.friendly(w.expiresMs)}" else null,
                w.states.takeIf { it.isNotEmpty() }?.joinToString(", "),
                if (w.counties > 0) "${w.counties} counties" else null,
            ).joinToString(" · ").ifEmpty { null }, bar = w.color) { a.push(WatchScreen(a, w)) }

        fun mdRow(a: MainActivity, m: Spc.Md) = W.listRow(a, "Mesoscale Discussion #${m.number}",
            listOfNotNull(
                m.concerning.ifBlank { null },
                m.watchChance?.let { "Watch chance $it%" },
                if (m.expireMs > 0) "Until ${Time.friendly(m.expireMs)}" else null,
            ).joinToString(" · ").ifEmpty { null }, bar = Spc.MD_COLOR) { a.push(MdScreen(a, m)) }

        fun countsRow(a: MainActivity, reps: List<Spc.Report>): LinearLayout {
            val row = W.row(a)
            for ((k, label, color) in listOf(Triple('T', "Tornado", 0xffff4d4d.toInt()), Triple('W', "Wind", 0xff5aa2ff.toInt()), Triple('H', "Hail", 0xff4ccd5c.toInt()))) {
                val col = W.col(a).apply { gravity = Gravity.CENTER_HORIZONTAL }
                col.addView(W.text(a, reps.count { it.kind == k }.toString(), 26f, color, true))
                col.addView(W.text(a, label, 13f, C.dim))
                row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            }
            return row
        }
    }
}
