package com.libexil.spcforge.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.util.LruCache
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.libexil.spcforge.core.Img
import com.libexil.spcforge.core.Net

/** Downloaded images, kept in memory until the user refreshes. */
object ImageCache {
    private val mem = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** Which of an image's candidate URLs worked last time. */
    private val winner = HashMap<String, String>()
    private val failed = HashSet<String>()

    fun clear() { mem.evictAll(); synchronized(failed) { failed.clear() } }

    fun cached(img: Img): Bitmap? = synchronized(winner) { winner[img.urls.first()] }?.let { mem.get(it) }

    /** First candidate URL that downloads and decodes; white background for transparent images when asked. */
    fun load(img: Img): Pair<Bitmap, String> {
        val key = img.urls.first()
        synchronized(winner) { winner[key] }?.let { u -> mem.get(u)?.let { return it to u } }
        var last: Exception? = null
        for (u in img.urls) {
            if (synchronized(failed) { u in failed }) continue
            try {
                val bytes = Net.getBytes(u, attempts = 1)
                var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw Exception("not an image")
                if (img.whiteBg || bmp.hasAlpha()) bmp = flatten(bmp, if (img.whiteBg) Color.WHITE else Color.WHITE)
                mem.put(u, bmp)
                synchronized(winner) { winner[key] = u }
                return bmp to u
            } catch (e: Net.HttpError) {
                synchronized(failed) { failed.add(u) }; last = e
            } catch (e: Exception) { last = e }
        }
        throw last ?: Exception("no image")
    }

    private fun flatten(b: Bitmap, bg: Int): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(bg); c.drawBitmap(b, 0f, 0f, null)
        return out
    }
}

/**
 * An SPC graphic in a card: loads in the background, keeps the image's shape, opens full screen on
 * tap. Hides itself when [hideIfMissing] and nothing loads (or when it's only a tiny icon).
 */
class AsyncImage(ctx: Context, private val img: Img, private val hideIfMissing: Boolean = false, private val caption: Boolean = false) : LinearLayout(ctx) {
    private val iv = object : ImageView(ctx) {
        override fun onMeasure(wSpec: Int, hSpec: Int) {
            val w = MeasureSpec.getSize(wSpec)
            val d = drawable
            val h = if (d != null && d.intrinsicWidth > 0) (w.toLong() * d.intrinsicHeight / d.intrinsicWidth).toInt() else (w * 0.66f).toInt()
            setMeasuredDimension(w, h)
        }
    }
    private val status = TextView(ctx).apply { textSize = 13f; setTextColor(C.dim); gravity = Gravity.CENTER }
    private var url: String? = null

    init {
        orientation = VERTICAL
        if (caption) addView(W.text(ctx, img.label, 13f, C.dim, true).apply { setPadding(ctx.dpi(2f), 0, 0, ctx.dpi(6f)) })
        val box = FrameLayout(ctx)
        box.background = rounded(0xff0c0d10.toInt(), ctx.dp(10f))
        box.clipToOutline = true
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.adjustViewBounds = true
        box.addView(iv, FrameLayout.LayoutParams(-1, -2))
        box.addView(status, FrameLayout.LayoutParams(-1, -1))
        addView(box, LayoutParams(-1, -2))
        box.setOnClickListener { val b = ImageCache.cached(img); if (b != null) Viewer.show(ctx, img.label, b, url) }
        val have = ImageCache.cached(img)
        if (have != null) show(have) else {
            status.text = "Loading image…"
            Bg.run({ ImageCache.load(img) }) { r ->
                r.onSuccess { (b, u) -> url = u; show(b) }
                r.onFailure {
                    if (hideIfMissing) visibility = GONE
                    else status.text = "Image not available right now"
                }
            }
        }
    }

    private fun show(b: Bitmap) {
        if (hideIfMissing && (b.width < 160 || b.height < 100)) { visibility = GONE; return }
        status.visibility = GONE
        iv.setImageBitmap(b)
        if (url == null) url = img.urls.first()
    }
}

/** Pinch-zoom and pan image view (double-tap zooms in / back out). */
class ZoomImageView(ctx: Context, private val bmp: Bitmap) : View(ctx) {
    private val m = Matrix()
    private var base = 1f
    private var scale = 1f
    private val tmp = RectF()

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val f = (scale * d.scaleFactor).coerceIn(1f, 8f) / scale
            scale *= f
            m.postScale(f, f, d.focusX, d.focusY)
            clamp(); invalidate(); return true
        }
    })
    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            m.postTranslate(-dx, -dy); clamp(); invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > 1.05f) fit() else { scale = 2.5f; m.postScale(2.5f, 2.5f, e.x, e.y); clamp() }
            invalidate(); return true
        }
    })

    private fun fit() {
        if (width == 0) return
        base = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        scale = 1f
        m.reset(); m.postScale(base, base)
        m.postTranslate((width - bmp.width * base) / 2f, (height - bmp.height * base) / 2f)
    }

    private fun clamp() {
        tmp.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()); m.mapRect(tmp)
        var dx = 0f; var dy = 0f
        if (tmp.width() <= width) dx = (width - tmp.width()) / 2f - tmp.left
        else { if (tmp.left > 0) dx = -tmp.left; if (tmp.right < width) dx = width - tmp.right }
        if (tmp.height() <= height) dy = (height - tmp.height()) / 2f - tmp.top
        else { if (tmp.top > 0) dy = -tmp.top; if (tmp.bottom < height) dy = height - tmp.bottom }
        m.postTranslate(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { fit() }
    override fun onDraw(c: Canvas) { c.drawBitmap(bmp, m, null) }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        return true
    }
}

/** Full-screen image with a title bar (close, open in browser). */
object Viewer {
    fun show(ctx: Context, title: String, bmp: Bitmap, url: String?) {
        val d = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = W.col(ctx).apply { setBackgroundColor(Color.BLACK) }
        val bar = W.row(ctx).apply { setPadding(ctx.dpi(4f), ctx.dpi(4f), ctx.dpi(4f), ctx.dpi(4f)) }
        bar.addView(IconView(ctx, Icon.CLOSE).apply { isClickable = true; background = ripple(null); setOnClickListener { d.dismiss() } },
            LinearLayout.LayoutParams(ctx.dpi(48f), ctx.dpi(48f)))
        bar.addView(W.text(ctx, title, 16f, C.text, true).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        if (url != null) bar.addView(IconView(ctx, Icon.OPEN).apply {
            isClickable = true; background = ripple(null)
            setOnClickListener { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {} }
        }, LinearLayout.LayoutParams(ctx.dpi(48f), ctx.dpi(48f)))
        root.addView(bar)
        root.addView(ZoomImageView(ctx, bmp), LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(W.note(ctx, "Pinch or double-tap to zoom").apply { gravity = Gravity.CENTER; setPadding(0, ctx.dpi(6f), 0, ctx.dpi(10f)) })
        d.setContentView(root, ViewGroup.LayoutParams(-1, -1))
        d.show()
    }
}
