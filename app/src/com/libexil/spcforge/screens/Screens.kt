package com.libexil.spcforge.screens

import android.widget.LinearLayout
import com.libexil.spcforge.MainActivity
import com.libexil.spcforge.Updater
import com.libexil.spcforge.App
import com.libexil.spcforge.core.Product
import com.libexil.spcforge.core.Releases
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

// =====================================================================================  outlooks
class OutlookScreen(a: MainActivity, private var day: Int) : Screen(a) {
    private var kind = 0
    override val title = "Convective Outlooks"
    override val webUrl: String get() = if (day <= 3) Spc.outlookPage(day) else Spc.DAY48_PAGE

    override fun render(force: Boolean) {
        body.addView(W.chips(a, listOf("Day 1", "Day 2", "Day 3", "Day 4–8"), day.coerceAtMost(4) - 1) { i -> day = i + 1; kind = 0; reload(false) })
        val head = slot(top = 10f)
        val content = slot(top = 6f)
        loading(content)
        async({ Data.outlook(day, force) }) { r ->
            content.removeAllViews()
            val p = r.getOrNull() ?: run { failed(content, "the outlook", r.exceptionOrNull()); return@async }
            if (day >= 4) {
                content.addView(W.note(a, "Areas with a 15% or 30% chance of severe storms within 25 miles of a point."))
                p.images.forEach { image(content, it, top = 12f, caption = true) }
                p.texts.firstOrNull()?.let { content.addView(W.section(a, "Discussion")); productText(content, it) }
                return@async
            }
            content.addView(W.chips(a, p.images.map { it.label }, kind) { i -> kind = i; reload(false) })
            image(content, p.images[kind.coerceIn(0, p.images.size - 1)], top = 10f)
            content.addView(W.note(a, p.title + " · tap the map to zoom").apply { setPadding(a.dpi(2f), a.dpi(6f), 0, 0) })
            if (p.tables.isNotEmpty()) {
                content.addView(W.section(a, "Risk areas"))
                val c = W.card(a)
                for (row in p.tables) {
                    val name = row[0]
                    val key = Spc.CAT_NAME.entries.firstOrNull { name.startsWith(it.value.substringBefore(' '), true) }?.key
                    val line = W.row(a).apply { setPadding(0, a.dpi(4f), 0, a.dpi(4f)) }
                    line.addView(W.pill(a, name, key?.let { Spc.CAT_COLOR[it] } ?: C.cardAlt))
                    line.addView(W.text(a, "  pop. ${row.getOrElse(2) { "" }}", 13.5f, C.text))
                    c.addView(line)
                    row.getOrNull(3)?.takeIf { it.isNotBlank() }?.let { c.addView(W.note(a, it).apply { setPadding(0, 0, 0, a.dpi(6f)) }) }
                }
                content.addView(c)
            }
            p.texts.firstOrNull()?.let { content.addView(W.section(a, "Forecast discussion")); productText(content, it) }
        }
        if (day <= 3 && a.prefs.hasLocation) async({ Spc.riskAt(day, Data.areas(day, force), a.prefs.lat, a.prefs.lon) }) { r ->
            val rk = r.getOrNull() ?: return@async
            val c = W.row(a).apply { background = rounded(rk.color, a.dp(10f)); setPadding(a.dpi(12f), a.dpi(8f), a.dpi(12f), a.dpi(8f)) }
            val col = W.col(a)
            col.addView(W.text(a, (a.prefs.placeName.ifBlank { "Your location" }) + ": " + Spc.catLabel(rk.category), 14.5f, onColor(rk.color), true))
            rk.probLine().takeIf { it.isNotEmpty() }?.let { col.addView(W.text(a, it, 13f, onColor(rk.color))) }
            c.addView(col)
            head.addView(c)
        }
    }
}

// =====================================================================================  watches & MDs
class ActiveScreen(a: MainActivity, private var which: Int) : Screen(a) {
    override val title = "Watches & Discussions"
    override val webUrl: String get() = if (which == 0) Spc.WATCH_INDEX else Spc.MD_INDEX

    override fun render(force: Boolean) {
        body.addView(W.chips(a, listOf("Watches", "Mesoscale discussions"), which) { which = it; reload(false); a.updateBar() })
        val list = slot(top = 10f)
        loading(list)
        if (which == 0) async({ Data.watches(a, force) }) { r ->
            list.removeAllViews()
            val ws = r.getOrNull() ?: run { failed(list, "watches", r.exceptionOrNull()); return@async }
            if (ws.isEmpty()) list.addView(W.card(a).apply { addView(W.note(a, "No severe thunderstorm or tornado watches are in effect.")) })
            for (w in ws) list.addView(HomeScreen.watchRow(a, w), W.lp(bottom = a.dpi(6f)))
            list.addView(W.note(a, "Yellow: tornado watch · pink: severe thunderstorm watch").apply { setPadding(a.dpi(4f), a.dpi(8f), 0, 0) })
        } else async({ Data.mds(force) }) { r ->
            list.removeAllViews()
            val ms = r.getOrNull() ?: run { failed(list, "mesoscale discussions", r.exceptionOrNull()); return@async }
            if (ms.isEmpty()) list.addView(W.card(a).apply { addView(W.note(a, "No mesoscale discussions are in effect.")) })
            for (m in ms) list.addView(HomeScreen.mdRow(a, m), W.lp(bottom = a.dpi(6f)))
        }
    }
}

class WatchScreen(a: MainActivity, private val w: Spc.Watch) : Screen(a) {
    override val title: String get() = w.name
    override val webUrl = Spc.watchPage(w.number)

    override fun render(force: Boolean) {
        val head = slot()
        val content = slot(top = 8f)
        loading(content)
        async({ Repo.get("watch${w.number}", 3 * 60_000L, force) { Spc.loadWatchDetail(w) } }) { r ->
            content.removeAllViews()
            a.updateBar()
            val p = r.getOrNull() ?: run { failed(content, "this watch", r.exceptionOrNull()); return@async }
            val tags = W.row(a)
            tags.addView(W.pill(a, w.name, w.color))
            head.addView(tags)
            if (w.pds) head.addView(W.row(a).apply { addView(W.pill(a, "PARTICULARLY DANGEROUS SITUATION", C.danger)) }, W.lp(top = a.dpi(6f)))
            if (w.expiresMs > 0 || w.states.isNotEmpty()) head.addView(W.note(a, listOfNotNull(
                if (w.expiresMs > 0) "Until ${Time.friendly(w.expiresMs)}" else null,
                w.states.takeIf { it.isNotEmpty() }?.joinToString(", "),
                if (w.coversYou) "includes your location" else null).joinToString(" · ")).apply { setPadding(a.dpi(2f), a.dpi(6f), 0, 0) })
            showProduct(content, p)
        }
    }
}

class MdScreen(a: MainActivity, private val md: Spc.Md) : Screen(a) {
    override val title = "Mesoscale Discussion #${md.number}"
    override val webUrl = Spc.mdPage(md.number)

    override fun render(force: Boolean) {
        if (md.concerning.isNotBlank() || md.watchChance != null) {
            val c = W.card(a)
            if (md.concerning.isNotBlank()) c.addView(W.text(a, "Concerning: ${md.concerning}", 15f, C.text, true))
            c.addView(W.note(a, listOfNotNull(
                md.watchChance?.let { "Chance of a watch: $it%" },
                if (md.issueMs > 0) "Issued ${Time.friendly(md.issueMs)}" else null,
                if (md.expireMs > 0) "until ${Time.friendly(md.expireMs)}" else null).joinToString(" · ")))
            body.addView(c, W.lp(bottom = a.dpi(10f)))
        }
        val content = slot()
        loading(content)
        async({ Repo.get("md${md.number}", 5 * 60_000L, force) { Spc.loadMdDetail(md) } }) { r ->
            content.removeAllViews()
            val p = r.getOrNull() ?: run { failed(content, "this discussion", r.exceptionOrNull()); return@async }
            showProduct(content, p)
        }
    }
}

// =====================================================================================  storm reports
class ReportsScreen(a: MainActivity) : Screen(a) {
    private var yesterday = false
    private var filter = 0
    override val title = "Storm Reports"
    override val webUrl: String get() = Spc.reportsPage(if (yesterday) "yesterday" else "today")

    override fun render(force: Boolean) {
        val day = if (yesterday) "yesterday" else "today"
        body.addView(W.chips(a, listOf("Today", "Yesterday"), if (yesterday) 1 else 0) { yesterday = it == 1; reload(false) })
        val dayStart = Time.convectiveDayStart(System.currentTimeMillis()) - if (yesterday) 86_400_000L else 0L
        body.addView(W.note(a, "${Time.localDate(dayStart)} ${Time.local(dayStart)} to ${Time.localDate(dayStart + 86_400_000L)} ${Time.local(dayStart + 86_399_000L)} (12Z–12Z). Reports are preliminary.")
            .apply { setPadding(a.dpi(4f), a.dpi(8f), 0, a.dpi(6f)) })
        val counts = slot()
        image(body, Spc.reportsImage(day), top = 8f)
        val list = slot(top = 8f)
        loading(list)
        async({ Data.reports(day, force) }) { r ->
            list.removeAllViews()
            val reps = r.getOrNull() ?: run { failed(list, "storm reports", r.exceptionOrNull()); return@async }
            counts.addView(W.card(a).apply { addView(HomeScreen.countsRow(a, reps)) })
            if (reps.isEmpty()) { list.addView(W.note(a, "No reports so far.")); return@async }
            list.addView(W.chips(a, listOf("All", "Tornado", "Wind", "Hail", "Significant ★"), filter, scroll = true) { filter = it; reload(false) }, W.lp(bottom = a.dpi(8f)))
            val shown = reps.filter { when (filter) { 1 -> it.kind == 'T'; 2 -> it.kind == 'W'; 3 -> it.kind == 'H'; 4 -> it.significant; else -> true } }
                .sortedByDescending { val t = Time.reportTime(it.hhmm, dayStart); t }
            if (shown.isEmpty()) list.addView(W.note(a, "None of these so far."))
            for (rep in shown.take(400)) {
                val t = Time.reportTime(rep.hhmm, dayStart)
                val color = when (rep.kind) { 'T' -> 0xffff4d4d.toInt(); 'W' -> 0xff5aa2ff.toInt(); else -> 0xff4ccd5c.toInt() }
                val where = "${rep.location}, ${rep.county} Co., ${rep.state}"
                val sub = if (rep.comments.isNotBlank()) "$where\n${rep.comments}" else where
                list.addView(W.listRow(a, rep.title + if (rep.significant) "  ★" else "", sub, if (t > 0) Time.local(t) else rep.hhmm, bar = color) {
                    if (!rep.lat.isNaN()) a.openMap(rep.lat, rep.lon, rep.title)
                }, W.lp(bottom = a.dpi(6f)))
            }
            if (shown.size > 400) list.addView(W.note(a, "Showing the newest 400 of ${shown.size}."))
            list.addView(W.note(a, "★ significant: EF2+ tornado, 75+ mph wind or 2\"+ hail · tap a report to see it on a map").apply { setPadding(a.dpi(4f), a.dpi(8f), 0, 0) })
        }
    }
}

// =====================================================================================  more
class MoreScreen(a: MainActivity) : Screen(a) {
    override val title = "More"
    override val webUrl = Spc.BASE

    override fun render(force: Boolean) {
        fun item(t: String, s: String, icon: Icon, f: () -> Unit) = body.addView(W.listRow(a, t, s, icon = icon, onClick = f), W.lp(bottom = a.dpi(6f)))
        body.addView(W.section(a, "Forecasts"))
        item("Fire weather outlooks", "Days 1, 2 and 3–8", Icon.FIRE) { a.push(PagesScreen(a, "Fire Weather", Spc.FIRE_PAGES)) }
        item("Thunderstorm outlooks", "General thunder for the next few hours", Icon.BOLT) { a.push(PagesScreen(a, "Thunderstorm Outlooks", Spc.TSTM_PAGES)) }
        item("Days 4–8 outlook", "Extended severe-weather outlook", Icon.MAP) { a.goTab(1, OutlookScreen(a, 4)) }
        item("Tropics (National Hurricane Center)", "Active storms, cones, advisories, outlooks", Icon.HURRICANE) { a.goTab(4, TropicsScreen(a)) }
        body.addView(W.section(a, "Analysis"))
        item("Mesoanalysis", "CAPE, shear, SRH, STP and more, hourly", Icon.CHART) { a.push(MesoScreen(a)) }
        item("Storm reports", "Today and yesterday", Icon.LIST) { a.goTab(3, ReportsScreen(a)) }
        body.addView(W.section(a, "App"))
        item("Settings", "Location, text, updates", Icon.SETTINGS) { a.push(SettingsScreen(a)) }
        item("Open the SPC website", "spc.noaa.gov", Icon.OPEN) { a.openUrl(Spc.BASE) }
        body.addView(W.note(a, "SPCForge ${(a.application as App).versionName()} · data from the NOAA Storm Prediction Center and National Hurricane Center, " +
            "the National Weather Service and the Iowa Environmental Mesonet. Not an official NOAA app.").apply { setPadding(a.dpi(4f), a.dpi(16f), a.dpi(4f), 0) })
    }
}

/** One or more SPC pages shown as their graphics and text. */
class PagesScreen(a: MainActivity, override val title: String, private val pages: List<Pair<String, String>>) : Screen(a) {
    private var sel = 0
    override val webUrl: String get() = pages[sel].second

    override fun render(force: Boolean) {
        if (pages.size > 1) body.addView(W.chips(a, pages.map { it.first }, sel) { sel = it; reload(false) }, W.lp(bottom = a.dpi(10f)))
        val content = slot()
        loading(content)
        val (label, url) = pages[sel]
        async({ Repo.get("page:$url", 5 * 60_000L, force) { Spc.loadPage(label, url) } }) { r ->
            content.removeAllViews()
            val p: Product = r.getOrNull() ?: run { failed(content, label.lowercase(), r.exceptionOrNull()); return@async }
            p.images.forEachIndexed { i, img -> image(content, img, top = if (i == 0) 0f else 12f, hideIfMissing = true, caption = p.images.size > 1) }
            showProduct(content, p.copy(images = emptyList()), skipImages = true)
        }
    }
}

class MesoScreen(a: MainActivity) : Screen(a) {
    override val title = "Mesoanalysis"
    override val webUrl: String get() = Spc.mesoPage(a.prefs.mesoSector)

    override fun render(force: Boolean) {
        val sectors = Spc.MESO_SECTORS
        val si = sectors.indexOfFirst { it.first == a.prefs.mesoSector }.coerceAtLeast(0)
        body.addView(W.chips(a, sectors.map { it.second }, si, scroll = true) { a.prefs.mesoSector = sectors[it].first; reload(false) })
        val all = Spc.MESO_PARAMS.flatMap { it.second }
        val param = all.firstOrNull { it.first == a.prefs.mesoParam } ?: all.first()
        body.addView(W.text(a, param.second, 16f, C.text, true).apply { setPadding(a.dpi(4f), a.dpi(12f), 0, a.dpi(6f)) })
        image(body, Spc.mesoImage(a.prefs.mesoSector, param.first, "${param.second} – ${sectors[si].second}"))
        body.addView(W.note(a, "Hourly SPC analysis (RAP first guess + observations). Tap the map to zoom.").apply { setPadding(a.dpi(4f), a.dpi(6f), 0, 0) })
        for ((group, params) in Spc.MESO_PARAMS) {
            body.addView(W.section(a, group))
            body.addView(W.chips(a, params.map { it.second }, params.indexOfFirst { it.first == param.first }, scroll = true) {
                a.prefs.mesoParam = params[it].first; reload(false); scrollTop()
            })
        }
    }
}

class SettingsScreen(a: MainActivity) : Screen(a) {
    override val title = "Settings"

    override fun render(force: Boolean) {
        val p = a.prefs
        body.addView(W.section(a, "Your location"))
        val lc = W.card(a)
        lc.addView(W.note(a, if (p.hasLocation) "Using ${p.placeName.ifBlank { String.format(java.util.Locale.US, "%.2f, %.2f", p.lat, p.lon) }}" +
            " (found ${Time.age(p.locatedAt)}). Used to show your outlook risk and the watches and discussions that cover you; it stays on your phone."
            else "Off. Turn it on to see the risk, watches and discussions for where you are."))
        val btns = W.row(a)
        btns.addView(W.button(a, if (p.hasLocation) "Update" else "Use my location", primary = true) { a.locate { reload(false) } }, LinearLayout.LayoutParams(0, -2, 1f))
        if (p.hasLocation) btns.addView(W.button(a, "Turn off") { p.useLocation = false; Repo.clear(); reload(false) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = a.dpi(8f) })
        lc.addView(btns, W.lp(top = a.dpi(10f)))
        body.addView(lc)

        body.addView(W.section(a, "Forecast text"))
        val tc = W.card(a)
        tc.addView(W.text(a, "Layout", 14f, C.text, true))
        tc.addView(W.chips(a, listOf("Reformat for phone", "As issued"), if (p.reflow) 0 else 1) { p.reflow = it == 0; reload(false) }, W.lp(top = a.dpi(6f)))
        tc.addView(W.text(a, "Text size", 14f, C.text, true).apply { setPadding(0, a.dpi(12f), 0, 0) })
        val sizes = listOf(13f, 15f, 17f, 19f)
        tc.addView(W.chips(a, listOf("Small", "Medium", "Large", "Huge"), sizes.indexOf(p.textSize).coerceAtLeast(1)) { p.textSize = sizes[it]; reload(false) },
            W.lp(top = a.dpi(6f)))
        body.addView(tc)

        body.addView(W.section(a, "Updates"))
        val uc = W.card(a)
        uc.addView(W.note(a, "SPCForge ${(a.application as App).versionName()} · new versions come from github.com/${Releases.REPO}"))
        uc.addView(W.text(a, "Check automatically (once a day)", 14f, C.text, true).apply { setPadding(0, a.dpi(10f), 0, 0) })
        uc.addView(W.chips(a, listOf("On", "Off"), if (p.autoUpdateCheck) 0 else 1) { p.autoUpdateCheck = it == 0; reload(false) }, W.lp(top = a.dpi(6f)))
        uc.addView(W.button(a, "Check for updates now") { Updater.check(a, manual = true) }, W.lp(top = a.dpi(10f)))
        body.addView(uc)
    }
}
