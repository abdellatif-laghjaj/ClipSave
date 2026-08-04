package com.abdellatif.clipsave.access

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.toColorInt
import kotlin.math.abs

/**
 * Small, unobtrusive draggable download bubble.
 * Idle: docked to a screen edge at low opacity. Touch: full opacity. Release: snaps to the
 * nearest edge and fades back. Tap (no drag) triggers [onTap].
 */
class FloatingButtonController(
    private val context: Context,
    private val onTap: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var fadeRunnable: Runnable? = null

    private val sizePx get() = (48 * context.resources.displayMetrics.density).toInt()
    private val idleAlpha = 0.35f
    private val activeAlpha = 0.95f

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val v = TextView(context).apply {
            text = "⬇"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor("#3D5AFE".toColorInt())
            }
            alpha = idleAlpha
        }

        val lp = WindowManager.LayoutParams(
            sizePx, sizePx, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (context.resources.displayMetrics.heightPixels * 0.4f).toInt()
        }

        var startX = 0;
        var startY = 0
        var touchX = 0f;
        var touchY = 0f
        var moved = false

        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY; moved = false
                    cancelFade(); v.animate().alpha(activeAlpha).setDuration(120).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt();
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 16 || abs(dy) > 16) moved = true
                    lp.x = startX + dx; lp.y = startY + dy
                    runCatching { wm.updateViewLayout(v, lp) }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) onTap()
                    snapToEdge(v, lp)
                    scheduleFade(v)
                    true
                }

                else -> false
            }
        }

        runCatching {
            wm.addView(v, lp); view = v; params = lp
            scheduleFade(v)
        }
    }

    private fun snapToEdge(v: View, lp: WindowManager.LayoutParams) {
        val screenW = context.resources.displayMetrics.widthPixels
        val target = if (lp.x + sizePx / 2 < screenW / 2) 0 else screenW - sizePx
        ValueAnimator.ofInt(lp.x, target).apply {
            duration = 180
            addUpdateListener {
                lp.x = it.animatedValue as Int
                runCatching { wm.updateViewLayout(v, lp) }
            }
            start()
        }
    }

    private fun scheduleFade(v: View) {
        cancelFade()
        fadeRunnable = Runnable { v.animate().alpha(idleAlpha).setDuration(400).start() }
        main.postDelayed(fadeRunnable!!, 1500)
    }

    private fun cancelFade() {
        fadeRunnable?.let { main.removeCallbacks(it) }
    }

    fun hide() {
        cancelFade()
        view?.let { runCatching { wm.removeView(it) } }
        view = null; params = null
    }
}
