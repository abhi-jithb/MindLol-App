package com.abhijith.mindlol.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.abhijith.mindlol.domain.PillMode

/**
 * The small floating pill over the app in front: "4:59 left · Got it", or the grant chip "4:59".
 *
 * - Window type TYPE_ACCESSIBILITY_OVERLAY, added through the accessibility service's own
 *   WindowManager: no "display over other apps" permission.
 * - WRAP_CONTENT + FLAG_NOT_TOUCH_MODAL: the window is only as big as the pill, and every touch
 *   outside it goes to the app underneath. FLAG_NOT_FOCUSABLE: the keyboard and Back are untouched.
 * - A plain TextView, not Compose: Compose in a service window needs its own lifecycle wiring.
 * - Black and white only: black fill, thin white border, white text.
 *
 * Every WindowManager call is caught: [show] returns false if the pill couldn't be shown, so the
 * caller can fall back to a notification (W3).
 */
class WarningPill(private val service: AccessibilityService, private val onTap: () -> Unit) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var shownMode: PillMode? = null

    val isShowing: Boolean get() = view != null

    /** Shows or updates the pill. Returns false if the overlay couldn't be added. */
    fun show(text: String, mode: PillMode): Boolean {
        val wm = windowManager ?: return false
        val existing = view
        if (existing != null) {
            existing.text = text
            existing.contentDescription = describe(text, mode)
            if (mode != shownMode) style(existing, mode)
            shownMode = mode
            return true
        }
        return try {
            val v = TextView(service).apply {
                this.text = text
                contentDescription = describe(text, mode)
                setTextColor(Color.WHITE)
                setOnClickListener { onTap() }
                isClickable = true
            }
            style(v, mode)
            wm.addView(v, layoutParams())
            view = v
            shownMode = mode
            true
        } catch (e: Exception) {
            Log.w(TAG, "Warning pill: couldn't add the overlay", e)
            view = null
            shownMode = null
            false
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        shownMode = null
        try {
            windowManager?.removeView(v)
        } catch (e: Exception) {
            Log.w(TAG, "Warning pill: couldn't remove the overlay", e)
        }
    }

    private fun style(v: TextView, mode: PillMode) {
        val chip = mode == PillMode.CHIP
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (chip) 13f else 15f)
        val h = dp(if (chip) 10 else 16)
        val vert = dp(if (chip) 4 else 8)
        v.setPadding(h, vert, h, vert)
        v.background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = dp(100).toFloat()
            setStroke(dp(1.5f), Color.WHITE)
        }
    }

    private fun describe(text: String, mode: PillMode): String = when (mode) {
        PillMode.WARNING -> text.replace(" · Got it", "") + ". Tap to dismiss."
        PillMode.COUNTDOWN -> "$text. Tap to shrink."
        PillMode.CHIP -> "$text left. Tap to expand."
    }

    @Suppress("DEPRECATION") // TYPE_ACCESSIBILITY_OVERLAY is current; only the inset fallback is old
    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = statusBarHeight() + dp(8)
    }

    /** Below the status bar, so the pill never covers the clock or notifications. */
    private fun statusBarHeight(): Int {
        val wm = windowManager ?: return dp(32)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        } else {
            dp(32)
        }
    }

    private fun dp(value: Int): Int = dp(value.toFloat())

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()

    private companion object {
        const val TAG = "LiveLock"
    }
}
