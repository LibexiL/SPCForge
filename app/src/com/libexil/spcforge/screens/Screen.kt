package com.libexil.spcforge.screens

import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.libexil.spcforge.MainActivity
import com.libexil.spcforge.core.Img
import com.libexil.spcforge.core.Product
import com.libexil.spcforge.core.TextFmt
import com.libexil.spcforge.ui.AsyncImage
import com.libexil.spcforge.ui.Bg
import com.libexil.spcforge.ui.Anim
import com.libexil.spcforge.ui.C
import com.libexil.spcforge.ui.FadeColumn
import com.libexil.spcforge.ui.W
import com.libexil.spcforge.ui.dpi

/** One page of the app: a scrolling column that fills itself from the network. */
abstract class Screen(val a: MainActivity) {
    abstract val title: String
    open val webUrl: String? = null
    var loadedAt = 0L
        protected set

    protected val body: LinearLayout = FadeColumn(a)
    private var root: ScrollView? = null
    private var gen = 0

    fun view(): View {
        val r = root ?: ScrollView(a).apply {
            isFillViewport = true
            val p = a.dpi(12f)
            body.setPadding(p, a.dpi(8f), p, a.dpi(24f))
            body.layoutTransition = android.animation.LayoutTransition().apply {
                disableTransitionType(android.animation.LayoutTransition.APPEARING)
                disableTransitionType(android.animation.LayoutTransition.DISAPPEARING)
                disableTransitionType(android.animation.LayoutTransition.CHANGE_APPEARING)
                disableTransitionType(android.animation.LayoutTransition.CHANGE_DISAPPEARING)
                enableTransitionType(android.animation.LayoutTransition.CHANGING)
                setDuration(android.animation.LayoutTransition.CHANGING, Anim.FADE_MS)
                setInterpolator(android.animation.LayoutTransition.CHANGING, Anim.ease)
            }
            addView(body, ViewGroup.LayoutParams(-1, -2))
            root = this
            reload(false)
        }
        (r.parent as? ViewGroup)?.removeView(r)
        return r
    }

    fun scrollTop() { root?.smoothScrollTo(0, 0) }

    fun reload(force: Boolean) {
        gen++
        loadedAt = System.currentTimeMillis()
        // hold the page's height while it refills so the scroll position doesn't jump to the top
        val keep = body.height
        if (keep > 0) {
            body.minimumHeight = keep
            val g = gen
            body.postDelayed({ if (g == gen) body.minimumHeight = 0 }, 1500)
        }
        body.removeAllViews()
        render(force)
    }

    /** Fills [body]; [force]: the user asked to refresh. */
    protected abstract fun render(force: Boolean)

    /** Background work whose result is dropped if the screen was reloaded meanwhile. */
    protected fun <T> async(work: () -> T, done: (Result<T>) -> Unit) {
        val g = gen
        Bg.run(work) { if (g == gen) done(it) }
    }

    /** A container added to [body] now and filled later. */
    protected fun slot(into: LinearLayout = body, top: Float = 0f): LinearLayout =
        FadeColumn(a).also { it.tag = Anim.NO_FADE; into.addView(it, W.lp(top = a.dpi(top))) }

    protected fun loading(into: LinearLayout, s: String = "Loading…") { into.removeAllViews(); into.addView(W.spinnerText(a, s)) }

    protected fun failed(into: LinearLayout, what: String, e: Throwable?) {
        into.removeAllViews()
        val c = W.card(a)
        c.addView(W.text(a, "Couldn't load $what", 15f, C.text, true))
        c.addView(W.note(a, (e?.message ?: "No connection?") + "\nCheck your connection and tap refresh ↻ at the top."))
        into.addView(c)
    }

    protected fun gap(h: Float) = body.addView(W.gap(a, h))

    // ------------------------------------------------------------------ shared pieces
    fun image(into: LinearLayout, img: Img, top: Float = 0f, hideIfMissing: Boolean = false, caption: Boolean = false) =
        into.addView(AsyncImage(a, img, hideIfMissing, caption), W.lp(top = a.dpi(top)))

    /** An SPC text product: reflowed for the phone, or exactly as issued. */
    fun productText(into: LinearLayout, raw: String) {
        val clean = raw.lines().filterNot { it.trim().startsWith("CLICK TO GET", true) }.joinToString("\n")
        val card = W.card(a, 14f)
        if (a.prefs.reflow) {
            card.addView(W.product(a, TextFmt.reflow(clean), false, a.prefs.textSize))
        } else {
            val tv = W.product(a, clean.trimEnd(), true, a.prefs.textSize - 3f)
            card.addView(HorizontalScrollView(a).apply { addView(tv) })
        }
        val toggle = W.text(a, if (a.prefs.reflow) "Show as issued (fixed width)" else "Reformat for phone", 13f, C.link).apply {
            setPadding(0, a.dpi(12f), 0, 0)
            setOnClickListener { a.prefs.reflow = !a.prefs.reflow; reload(false) }
        }
        card.addView(toggle)
        into.addView(card, W.lp(top = a.dpi(6f)))
    }

    /** Images, tables and text of a [Product]. */
    fun showProduct(into: LinearLayout, p: Product, skipImages: Boolean = false) {
        if (!skipImages) p.images.forEachIndexed { i, img -> image(into, img, top = if (i == 0) 0f else 12f, hideIfMissing = i > 0, caption = p.images.size > 1) }
        if (p.tables.isNotEmpty()) {
            into.addView(W.section(a, "Details"))
            val c = W.card(a)
            for (r in p.tables) c.addView(W.text(a, r.joinToString("  ·  "), 13.5f, C.text).apply { setPadding(0, a.dpi(3f), 0, a.dpi(3f)) })
            into.addView(c)
        }
        if (p.texts.isEmpty() && p.images.isEmpty()) into.addView(W.note(a, "Nothing is posted here right now."))
        p.texts.forEachIndexed { i, t ->
            into.addView(W.section(a, if (i == 0) "Discussion" else "More text"))
            productText(into, t)
        }
    }
}
