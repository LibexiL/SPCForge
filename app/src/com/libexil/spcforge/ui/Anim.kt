package com.libexil.spcforge.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Motion used across the app. Everything goes through Android's animators, so the system
 * "Remove animations" / animation-scale settings are respected automatically.
 */
object Anim {
    /** Material "emphasized decelerate"-style easing: quick start, soft landing. */
    val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    val easeInOut = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    const val SCREEN_MS = 260L
    const val FADE_MS = 220L
    const val PRESS_MS = 90L

    /** Marks a view that should appear instantly instead of fading in (e.g. a chip row being rebuilt). */
    const val NO_FADE = "no-fade"

    /** Cards, rows and buttons shrink slightly while pressed. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scale: Float = 0.97f) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(scale).scaleY(scale).setDuration(PRESS_MS).setInterpolator(ease).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(PRESS_MS * 2).setInterpolator(ease).start()
            }
            false   // the click / ripple still happen as usual
        }
    }

    /** Fades [v] in while it slides up a little. */
    fun reveal(v: View, delayMs: Long = 0, dyPx: Float = v.context.dp(10f)) {
        v.alpha = 0f
        v.translationY = dyPx
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(FADE_MS).setInterpolator(ease).start()
    }

    /** Animates a solid rounded background from one colour to another. */
    fun color(d: GradientDrawable, from: Int, to: Int, ms: Long = 200, onUpdate: ((Int) -> Unit)? = null) {
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = ms; interpolator = easeInOut
            addUpdateListener { val c = it.animatedValue as Int; d.setColor(c); onUpdate?.invoke(c) }
        }.start()
    }

    fun textColor(tv: TextView, from: Int, to: Int, ms: Long = 200) {
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = ms; interpolator = easeInOut
            addUpdateListener { tv.setTextColor(it.animatedValue as Int) }
        }.start()
    }

    /** "0 → 37" counter. */
    fun countUp(tv: TextView, to: Int, ms: Long = 700) {
        if (to <= 0) { tv.text = to.toString(); return }
        tv.text = "0"
        ValueAnimator.ofInt(0, to).apply {
            duration = ms; interpolator = ease
            addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
        }.start()
    }
}

/**
 * A column whose children fade and slide in when they're added after it is on screen (content that
 * arrives from the network), with a short stagger between children added together. The first few dozen
 * are animated; anything beyond that is off screen anyway and just appears.
 */
open class FadeColumn(ctx: Context) : LinearLayout(ctx) {
    private var batchStart = 0L
    private var inBatch = 0

    init { orientation = VERTICAL }

    override fun addView(child: View, index: Int, params: android.view.ViewGroup.LayoutParams) {
        super.addView(child, index, params)
        if (!isAttachedToWindow || !isLaidOut || child.tag == Anim.NO_FADE) return
        val now = SystemClock.uptimeMillis()
        if (now - batchStart > 150) { batchStart = now; inBatch = 0 }
        val i = inBatch++
        if (i < 24) Anim.reveal(child, delayMs = minOf(i, 8) * 28L)
    }
}
