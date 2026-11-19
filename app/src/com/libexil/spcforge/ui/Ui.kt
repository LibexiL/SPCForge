package com.libexil.spcforge.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Colours (one dark theme, close to RadarForge's). */
object C {
    const val window = 0xff17181d.toInt()
    const val panel = 0xff111216.toInt()
    const val card = 0xff1f2027.toInt()
    const val cardAlt = 0xff262833.toInt()
    const val border = 0xff2f3240.toInt()
    const val text = 0xffe6e7ec.toInt()
    const val dim = 0xff9497a3.toInt()
    const val faint = 0xff6c6f7b.toInt()
    const val accent = 0xff4a7fd8.toInt()
    const val accentSoft = 0x334a7fd8
    const val link = 0xff8fb4ff.toInt()
    const val danger = 0xffe0524f.toInt()
    const val amber = 0xffe8a33a.toInt()
    const val ok = 0xff3fae6a.toInt()
    const val ripple = 0x33ffffff
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun rounded(color: Int, radiusPx: Float, stroke: Int = 0, strokePx: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radiusPx
    if (strokePx > 0) setStroke(strokePx, stroke)
}

fun ripple(base: Drawable?) = RippleDrawable(ColorStateList.valueOf(C.ripple), base, base ?: ColorDrawable(Color.WHITE))

/** Readable text colour on a coloured background. */
fun onColor(bg: Int): Int {
    val r = Color.red(bg); val g = Color.green(bg); val b = Color.blue(bg)
    return if (0.299 * r + 0.587 * g + 0.114 * b > 150) 0xff111111.toInt() else Color.WHITE
}

/** Background work and posting back to the UI thread. */
object Bg {
    private val pool = Executors.newFixedThreadPool(5) { r -> Thread(r, "spc-bg").apply { isDaemon = true } }
    val main = Handler(Looper.getMainLooper())
    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        pool.execute {
            val r = try { Result.success(work()) } catch (e: Throwable) { Result.failure(e) }
            main.post { done(r) }
        }
    }
    fun ui(f: () -> Unit) = main.post(f)
}

// ------------------------------------------------------------------------- icons
enum class Icon { HOME, MAP, WARNING, LIST, MORE, BACK, REFRESH, OPEN, LOCATE, CLOSE, CHEVRON, SETTINGS, FIRE, BOLT, CHART, INFO, DOWNLOAD }

class IconView(ctx: Context, var icon: Icon, private var tint: Int = C.text) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    fun setTint(c: Int) { tint = c; invalidate() }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) * 0.62f
        c.save()
        c.translate((width - s) / 2f, (height - s) / 2f)
        c.scale(s / 24f, s / 24f)
        p.color = tint; p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        path.reset()
        when (icon) {
            Icon.HOME -> {
                path.moveTo(3f, 11f); path.lineTo(12f, 3.5f); path.lineTo(21f, 11f); c.drawPath(path, p)
                path.reset(); path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20.5f); path.lineTo(18.5f, 20.5f); path.lineTo(18.5f, 9.5f); c.drawPath(path, p)
                c.drawRect(10f, 14f, 14f, 20.5f, p)
            }
            Icon.MAP -> {
                path.moveTo(2.5f, 6f); path.lineTo(8.5f, 3.5f); path.lineTo(15.5f, 6f); path.lineTo(21.5f, 3.5f); path.lineTo(21.5f, 18f)
                path.lineTo(15.5f, 20.5f); path.lineTo(8.5f, 18f); path.lineTo(2.5f, 20.5f); path.close(); c.drawPath(path, p)
                c.drawLine(8.5f, 3.5f, 8.5f, 18f, p); c.drawLine(15.5f, 6f, 15.5f, 20.5f, p)
            }
            Icon.WARNING -> {
                path.moveTo(12f, 2.5f); path.lineTo(22.5f, 21f); path.lineTo(1.5f, 21f); path.close(); c.drawPath(path, p)
                c.drawLine(12f, 9f, 12f, 14.5f, p); p.style = Paint.Style.FILL; c.drawCircle(12f, 17.7f, 1.3f, p)
            }
            Icon.LIST -> {
                for (y in floatArrayOf(6f, 12f, 18f)) { c.drawLine(8.5f, y, 21f, y, p) }
                p.style = Paint.Style.FILL; for (y in floatArrayOf(6f, 12f, 18f)) c.drawCircle(4f, y, 1.6f, p)
            }
            Icon.MORE -> { p.style = Paint.Style.FILL; for (x in floatArrayOf(5f, 12f, 19f)) c.drawCircle(x, 12f, 2.1f, p) }
            Icon.BACK -> { path.moveTo(15f, 4.5f); path.lineTo(7.5f, 12f); path.lineTo(15f, 19.5f); c.drawPath(path, p) }
            Icon.CHEVRON -> { path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f); c.drawPath(path, p) }
            Icon.REFRESH -> {
                c.drawArc(RectF(4f, 4f, 20f, 20f), -60f, 290f, false, p)
                p.style = Paint.Style.FILL; path.moveTo(20.5f, 3f); path.lineTo(21f, 10f); path.lineTo(14.5f, 8f); path.close(); c.drawPath(path, p)
            }
            Icon.OPEN -> {
                path.moveTo(13f, 4f); path.lineTo(20f, 4f); path.lineTo(20f, 11f); c.drawPath(path, p)
                c.drawLine(20f, 4f, 11f, 13f, p)
                path.reset(); path.moveTo(17f, 14f); path.lineTo(17f, 20f); path.lineTo(4f, 20f); path.lineTo(4f, 7f); path.lineTo(10f, 7f); c.drawPath(path, p)
            }
            Icon.LOCATE -> {
                c.drawCircle(12f, 12f, 7f, p); p.style = Paint.Style.FILL; c.drawCircle(12f, 12f, 2.6f, p); p.style = Paint.Style.STROKE
                c.drawLine(12f, 1f, 12f, 5f, p); c.drawLine(12f, 19f, 12f, 23f, p); c.drawLine(1f, 12f, 5f, 12f, p); c.drawLine(19f, 12f, 23f, 12f, p)
            }
            Icon.CLOSE -> { c.drawLine(6f, 6f, 18f, 18f, p); c.drawLine(18f, 6f, 6f, 18f, p) }
            Icon.SETTINGS -> {
                c.drawCircle(12f, 12f, 3.2f, p)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    c.drawLine(12f + 6.5f * Math.cos(a).toFloat(), 12f + 6.5f * Math.sin(a).toFloat(),
                        12f + 9.5f * Math.cos(a).toFloat(), 12f + 9.5f * Math.sin(a).toFloat(), p)
                }
                c.drawCircle(12f, 12f, 6.5f, p)
            }
            Icon.FIRE -> {
                path.moveTo(12f, 2.5f); path.cubicTo(13f, 7f, 19f, 9f, 19f, 15f); path.cubicTo(19f, 19f, 16f, 21.5f, 12f, 21.5f)
                path.cubicTo(8f, 21.5f, 5f, 19f, 5f, 15f); path.cubicTo(5f, 11f, 8f, 10f, 9f, 7f); path.cubicTo(10.5f, 9f, 10.5f, 11f, 10f, 12f)
                path.cubicTo(12f, 10f, 12.5f, 6f, 12f, 2.5f); c.drawPath(path, p)
            }
            Icon.BOLT -> {
                path.moveTo(13.5f, 2f); path.lineTo(5f, 13.5f); path.lineTo(11.5f, 13.5f); path.lineTo(10f, 22f); path.lineTo(19f, 10f)
                path.lineTo(12.5f, 10f); path.close(); c.drawPath(path, p)
            }
            Icon.CHART -> {
                c.drawLine(3f, 3f, 3f, 21f, p); c.drawLine(3f, 21f, 21f, 21f, p)
                path.moveTo(6f, 16f); path.lineTo(10.5f, 10.5f); path.lineTo(14f, 13.5f); path.lineTo(20f, 6f); c.drawPath(path, p)
            }
            Icon.INFO -> {
                c.drawCircle(12f, 12f, 9.5f, p); c.drawLine(12f, 10.5f, 12f, 17f, p)
                p.style = Paint.Style.FILL; c.drawCircle(12f, 7.3f, 1.3f, p)
            }
            Icon.DOWNLOAD -> {
                c.drawLine(12f, 3f, 12f, 15f, p); path.moveTo(6.5f, 9.5f); path.lineTo(12f, 15f); path.lineTo(17.5f, 9.5f); c.drawPath(path, p)
                c.drawLine(4f, 20.5f, 20f, 20.5f, p)
            }
        }
        c.restore()
    }
}

// ------------------------------------------------------------------------- widgets
object W {
    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = top; bottomMargin = bottom }

    fun col(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun row(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun text(ctx: Context, s: CharSequence, size: Float = 15f, color: Int = C.text, bold: Boolean = false) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun note(ctx: Context, s: CharSequence) = text(ctx, s, 13f, C.dim).apply { setLineSpacing(0f, 1.1f) }

    fun section(ctx: Context, s: String) = text(ctx, s.uppercase(), 12f, C.dim, true).apply {
        letterSpacing = 0.06f
        setPadding(ctx.dpi(4f), ctx.dpi(18f), 0, ctx.dpi(6f))
    }

    /** A rounded panel; [onClick] makes it tappable. */
    fun card(ctx: Context, pad: Float = 14f, color: Int = C.card, onClick: (() -> Unit)? = null) = col(ctx).apply {
        val bg = rounded(color, ctx.dp(14f))
        background = if (onClick != null) ripple(bg) else bg
        val p = ctx.dpi(pad); setPadding(p, p, p, p)
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() }; Anim.pressable(this, 0.98f) }
    }

    fun button(ctx: Context, label: String, primary: Boolean = false, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 15f; gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) Color.WHITE else C.text)
        background = ripple(rounded(if (primary) C.accent else C.cardAlt, ctx.dp(12f)))
        val v = ctx.dpi(11f); val h = ctx.dpi(16f); setPadding(h, v, h, v)
        isClickable = true; setOnClickListener { onClick() }
        Anim.pressable(this, 0.96f)
    }

    /** A pill-shaped label, e.g. "SLGT" in its risk colour. */
    fun pill(ctx: Context, s: String, bg: Int, size: Float = 12f) = text(ctx, s, size, onColor(bg), true).apply {
        background = rounded(bg, ctx.dp(20f))
        val v = ctx.dpi(3f); val h = ctx.dpi(9f); setPadding(h, v, h, v)
    }

    /** One tappable line: optional colour bar, title, subtitle, trailing text/chevron. */
    fun listRow(ctx: Context, title: CharSequence, sub: CharSequence?, trailing: String? = null, bar: Int? = null,
                icon: Icon? = null, onClick: (() -> Unit)?): LinearLayout {
        val r = row(ctx)
        val p = ctx.dpi(12f); r.setPadding(p, ctx.dpi(11f), ctx.dpi(8f), ctx.dpi(11f))
        r.background = ripple(rounded(C.card, ctx.dp(12f)))
        if (bar != null) r.addView(View(ctx).apply { background = rounded(bar, ctx.dp(3f)) },
            LinearLayout.LayoutParams(ctx.dpi(5f), ctx.dpi(38f)).apply { rightMargin = ctx.dpi(12f) })
        if (icon != null) r.addView(IconView(ctx, icon, C.dim), LinearLayout.LayoutParams(ctx.dpi(26f), ctx.dpi(26f)).apply { rightMargin = ctx.dpi(12f) })
        val t = col(ctx)
        t.addView(text(ctx, title, 15.5f, C.text, true))
        if (!sub.isNullOrEmpty()) t.addView(note(ctx, sub).apply { setPadding(0, ctx.dpi(2f), 0, 0) })
        r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) r.addView(text(ctx, trailing, 13f, C.dim).apply { setPadding(ctx.dpi(8f), 0, ctx.dpi(4f), 0) })
        if (onClick != null) {
            r.addView(IconView(ctx, Icon.CHEVRON, C.faint), LinearLayout.LayoutParams(ctx.dpi(20f), ctx.dpi(20f)))
            r.isClickable = true; r.setOnClickListener { onClick() }
            Anim.pressable(r, 0.98f)
        }
        return r
    }

    /**
     * A row of choice chips. Returns the container; [onPick] gets the index. [scroll] puts them in a
     * horizontal scroller (for long lists).
     */
    fun chips(ctx: Context, labels: List<String>, selected: Int, scroll: Boolean = false, onPick: (Int) -> Unit): View {
        val r = row(ctx)
        // the chip row is rebuilt on every pick: slide the highlight from the old chip to the new one
        val key = labels.joinToString("|")
        val prev = lastChip[key]
        lastChip[key] = selected
        labels.forEachIndexed { i, s ->
            val on = i == selected
            val bg = rounded(if (on) C.accent else C.cardAlt, ctx.dp(18f))
            val tv = text(ctx, s, 14f, if (on) Color.WHITE else C.text, on).apply {
                gravity = Gravity.CENTER
                background = ripple(bg)
                if (prev != null && prev != selected && (i == selected || i == prev)) {
                    Anim.color(bg, if (on) C.cardAlt else C.accent, if (on) C.accent else C.cardAlt)
                    Anim.textColor(this, if (on) C.text else Color.WHITE, if (on) Color.WHITE else C.text)
                    if (on) { scaleX = 0.94f; scaleY = 0.94f; animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Anim.ease).start() }
                }
                val v = ctx.dpi(8f); val h = ctx.dpi(14f); setPadding(h, v, h, v)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                isClickable = true; setOnClickListener { if (i != selected) onPick(i) }
            }
            val lp = if (scroll) LinearLayout.LayoutParams(-2, -2) else LinearLayout.LayoutParams(0, -2, 1f)
            if (i > 0) lp.leftMargin = ctx.dpi(6f)
            r.addView(tv, lp)
        }
        if (!scroll) return r.apply { tag = Anim.NO_FADE }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false; addView(r); tag = Anim.NO_FADE
            // keep the picked chip in view (and remember where the row was scrolled to)
            val start = lastScroll[key] ?: 0
            val hsv = this
            viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    hsv.viewTreeObserver.removeOnPreDrawListener(this)
                    hsv.scrollTo(start, 0)
                    val chip = r.getChildAt(selected)
                    if (chip != null) {
                        val left = chip.left - ctx.dpi(16f); val right = chip.right + ctx.dpi(16f)
                        if (left < hsv.scrollX) hsv.smoothScrollTo(left, 0) else if (right > hsv.scrollX + hsv.width) hsv.smoothScrollTo(right - hsv.width, 0)
                    }
                    return true
                }
            })
            setOnScrollChangeListener { _, x, _, _, _ -> lastScroll[key] = x }
        }
    }

    private val lastChip = HashMap<String, Int>()
    private val lastScroll = HashMap<String, Int>()

    fun spinnerText(ctx: Context, s: String = "Loading…") = text(ctx, s, 14f, C.dim).apply {
        gravity = Gravity.CENTER; setPadding(0, ctx.dpi(28f), 0, ctx.dpi(28f))
    }

    fun gap(ctx: Context, h: Float) = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, ctx.dpi(h)) }

    /** Fixed-width or reflowed product text, selectable. */
    fun product(ctx: Context, s: String, mono: Boolean, size: Float) = text(ctx, s, size, C.text).apply {
        setTextIsSelectable(true)
        if (mono) { typeface = Typeface.MONOSPACE; setHorizontallyScrolling(false) }
        setLineSpacing(0f, if (mono) 1.05f else 1.22f)
    }

    fun frame(ctx: Context) = FrameLayout(ctx)
}
