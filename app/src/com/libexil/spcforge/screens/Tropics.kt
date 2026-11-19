package com.libexil.spcforge.screens

import android.widget.LinearLayout
import com.libexil.spcforge.MainActivity
import com.libexil.spcforge.core.Nhc
import com.libexil.spcforge.core.Repo
import com.libexil.spcforge.core.Time
import com.libexil.spcforge.ui.C
import com.libexil.spcforge.ui.Icon
import com.libexil.spcforge.ui.W
import com.libexil.spcforge.ui.dpi

object TropicsData {
    fun storms(force: Boolean) = Repo.get("nhc-storms", 3 * 60_000L, force) { Nhc.loadStorms() }
    fun outlook(b: Nhc.Basin, force: Boolean) = Repo.get("nhc-two-${b.key}", 10 * 60_000L, force) { Nhc.loadOutlookText(b) }
    fun discussion(b: Nhc.Basin, force: Boolean) = Repo.get("nhc-twd-${b.key}", 10 * 60_000L, force) { Nhc.loadDiscussion(b) }
    fun stormText(s: Nhc.Storm, k: Nhc.TextKind, force: Boolean) =
        Repo.get("nhc-${s.id}-${s.advisoryNum}-${k.name}", 5 * 60_000L, force) { Nhc.loadStormText(s, k) }

    fun stormRow(a: MainActivity, s: Nhc.Storm): LinearLayout {
        val where = if (a.prefs.hasLocation) Nhc.fromYou(s, a.prefs.lat, a.prefs.lon) else null
        val sub = listOfNotNull(
            s.stats().ifEmpty { null },
            where ?: s.basin.ifEmpty { null },
            if (s.updatedMs > 0) "updated ${Time.friendly(s.updatedMs)}" else null,
        ).joinToString(" · ")
        return W.listRow(a, s.title, sub, trailing = s.badge, bar = s.color, icon = Icon.HURRICANE) { a.push(StormScreen(a, s)) }
    }
}

/** NHC: active storms plus each basin's tropical weather outlook. */
class TropicsScreen(a: MainActivity) : Screen(a) {
    override val title = "Tropics"
    private val basin: Nhc.Basin get() = Nhc.BASINS.firstOrNull { it.key == a.prefs.nhcBasin } ?: Nhc.BASINS[0]
    private var days = 7
    override val webUrl: String get() = basin.home

    override fun render(force: Boolean) {
        body.addView(W.section(a, "Active storms"))
        val storms = slot()
        loading(storms)
        async({ TropicsData.storms(force) }) { r ->
            storms.removeAllViews()
            val list = r.getOrNull() ?: run { failed(storms, "active storms", r.exceptionOrNull()); return@async }
            if (list.isEmpty()) storms.addView(W.card(a).apply {
                addView(W.text(a, "No active tropical cyclones", 15f, C.text, true))
                addView(W.note(a, "NHC isn't issuing advisories in the Atlantic or the eastern or central Pacific right now."))
            })
            for (s in list) storms.addView(TropicsData.stormRow(a, s), W.lp(bottom = a.dpi(6f)))
        }

        val b = basin
        body.addView(W.section(a, "Tropical weather outlook"))
        body.addView(W.chips(a, Nhc.BASINS.map { it.short }, Nhc.BASINS.indexOf(b)) { a.prefs.nhcBasin = Nhc.BASINS[it].key; reload(false) })
        body.addView(W.gap(a, 8f))
        body.addView(W.chips(a, listOf("7-day", "2-day"), if (days == 7) 0 else 1) { days = if (it == 0) 7 else 2; reload(false) })
        image(body, Nhc.outlookImage(b, days), top = 10f)
        body.addView(W.note(a, if (days == 7) "Areas NHC is watching for development over the next 7 days (X marks a current disturbance). Tap to zoom."
            else "Chance of formation in the next 48 hours. Tap to zoom.").apply { setPadding(a.dpi(4f), a.dpi(6f), 0, 0) })
        val text = slot(top = 4f)
        loading(text)
        async({ TropicsData.outlook(b, force) }) { r ->
            text.removeAllViews()
            val t = r.getOrNull()
            if (t == null) { text.addView(W.note(a, "The outlook text isn't available right now.").apply { setPadding(a.dpi(4f), a.dpi(10f), 0, 0) }); return@async }
            productText(text, t)
        }
        if (b.twdPil != null) body.addView(W.listRow(a, "Tropical Weather Discussion", "Forecasters' look at the whole ${b.name} basin", icon = Icon.INFO) {
            a.push(TextScreen(a, "Tropical Weather Discussion", "${Nhc.BASE}/text/MIA${b.twdPil}.shtml") { f -> TropicsData.discussion(b, f) })
        }, W.lp(top = a.dpi(10f)))
        body.addView(W.note(a, "From the National Hurricane Center and the Central Pacific Hurricane Center. Routine outlooks run " +
            "May 15 / June 1 to November 30; NHC issues special outlooks outside the season when needed.").apply { setPadding(a.dpi(4f), a.dpi(14f), a.dpi(4f), 0) })
    }
}

/** One storm: summary, NHC's graphics and the advisory text products. */
class StormScreen(a: MainActivity, private val s: Nhc.Storm) : Screen(a) {
    override val title: String get() = s.title
    override val webUrl: String get() = s.graphicsUrl ?: s.publicUrl ?: Nhc.BASE
    private var kind = Nhc.TextKind.PUBLIC

    override fun render(force: Boolean) {
        // headline card
        val c = W.card(a)
        val top = W.row(a)
        top.addView(W.pill(a, s.badge, s.color, 13f))
        top.addView(W.text(a, "  " + listOfNotNull(s.basin.ifEmpty { null }, s.advisoryNum.takeIf { it.isNotEmpty() }?.let { "Advisory $it" })
            .joinToString(" · "), 13f, C.dim))
        c.addView(top)
        c.addView(W.text(a, s.title, 20f, C.text, true).apply { setPadding(0, a.dpi(8f), 0, 0) })
        s.stats().takeIf { it.isNotEmpty() }?.let { c.addView(W.text(a, it, 15f, C.text).apply { setPadding(0, a.dpi(4f), 0, 0) }) }
        if (a.prefs.hasLocation) Nhc.fromYou(s, a.prefs.lat, a.prefs.lon)?.let {
            c.addView(W.text(a, it.replaceFirstChar { ch -> ch.uppercase() }, 14f, C.link).apply { setPadding(0, a.dpi(4f), 0, 0) })
        }
        if (s.updatedMs > 0) c.addView(W.note(a, "Updated ${Time.friendly(s.updatedMs)}").apply { setPadding(0, a.dpi(6f), 0, 0) })
        val sum = slot(c)
        body.addView(c)

        // graphics
        val imgs = Nhc.stormImages(s)
        image(body, imgs[0], top = 12f)
        body.addView(W.note(a, "The cone shows where the center will probably go; impacts often reach well outside it.").apply { setPadding(a.dpi(4f), a.dpi(6f), 0, 0) })
        body.addView(W.section(a, "More graphics"))
        for (img in imgs.drop(1)) image(body, img, top = 10f, hideIfMissing = true, caption = true)

        // text products
        body.addView(W.section(a, "Text products"))
        body.addView(W.chips(a, Nhc.TextKind.values().map { it.label }, kind.ordinal, scroll = true) { kind = Nhc.TextKind.values()[it]; reload(false) })
        val text = slot(top = 4f)
        loading(text)
        val k = kind
        async({ TropicsData.stormText(s, k, force) }) { r ->
            text.removeAllViews()
            val t = r.getOrNull()
            if (t == null) { text.addView(W.note(a, "${k.label} isn't available right now.").apply { setPadding(a.dpi(4f), a.dpi(10f), 0, 0) }); return@async }
            productText(text, t)
        }
        // the summary block from the public advisory, under the headline
        async({ TropicsData.stormText(s, Nhc.TextKind.PUBLIC, force) }) { r ->
            val lines = r.getOrNull()?.let { Nhc.summary(it) } ?: return@async
            if (lines.isEmpty()) return@async
            val box = W.col(a).apply { setPadding(0, a.dpi(10f), 0, 0) }
            for ((k2, v) in lines) {
                val row = W.row(a).apply { setPadding(0, a.dpi(2f), 0, a.dpi(2f)) }
                row.addView(W.text(a, k2, 13.5f, C.dim).apply { minWidth = a.dpi(84f) })
                row.addView(W.text(a, v, 13.5f, C.text), LinearLayout.LayoutParams(0, -2, 1f))
                box.addView(row)
            }
            sum.addView(box)
        }
    }
}

/** A single text product with its own loader. */
class TextScreen(a: MainActivity, override val title: String, private val url: String, private val load: (Boolean) -> String?) : Screen(a) {
    override val webUrl: String get() = url

    override fun render(force: Boolean) {
        val content = slot()
        loading(content)
        async({ load(force) }) { r ->
            content.removeAllViews()
            val t = r.getOrNull() ?: run { failed(content, title.lowercase(), r.exceptionOrNull()); return@async }
            productText(content, t)
        }
    }
}
