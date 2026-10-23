package com.pulseisland.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pulseisland.app.R

/**
 * Manages the floating Dynamic Island overlay window using Android WindowManager.
 *
 * Designed with strict battery efficiency:
 * - No background polling loops or timers
 * - Animations run ONLY during user-triggered state transitions and stop immediately
 * - Window size is WRAP_CONTENT to ensure touches pass through outside the pill
 * - Resources, views, and listeners are thoroughly released on cleanup
 */
class IslandOverlayManager(
    private val context: Context,
    private val onCloseRequested: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var currentState: IslandState = IslandState.COLLAPSED

    private val density = context.resources.displayMetrics.density

    // View references
    private var collapsedLayout: LinearLayout? = null
    private var expandedLayout: LinearLayout? = null
    private var pillBackground: GradientDrawable? = null

    val isShowing: Boolean
        get() = overlayView != null

    /**
     * Attaches the floating island to the WindowManager if not already showing.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return

        val params = createLayoutParams()
        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        // Pill shape background with sleek dark styling and subtle border
        val initialRadius = dpToPx(18f)
        pillBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = initialRadius
            setColor(Color.BLACK)
            setStroke(dpToPx(1f).toInt(), Color.parseColor("#33FFFFFF"))
        }
        root.background = pillBackground

        // Construct internal layouts
        val collapsed = createCollapsedView()
        val expanded = createExpandedView()

        collapsed.visibility = View.VISIBLE
        expanded.visibility = View.GONE

        root.addView(collapsed)
        root.addView(expanded)

        // Set tap handlers for expansion & collapse
        root.setOnClickListener {
            toggleState(params)
        }

        try {
            windowManager.addView(root, params)
            overlayView = root
            collapsedLayout = collapsed
            expandedLayout = expanded
            currentState = IslandState.COLLAPSED
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
        }
    }

    /**
     * Toggles between COLLAPSED and EXPANDED states with a smooth transition.
     */
    private fun toggleState(params: WindowManager.LayoutParams) {
        val root = overlayView ?: return
        val collapsed = collapsedLayout ?: return
        val expanded = expandedLayout ?: return

        val nextState = if (currentState == IslandState.COLLAPSED) {
            IslandState.EXPANDED
        } else {
            IslandState.COLLAPSED
        }

        // Hardware-accelerated transition for smooth morphing
        val transition = AutoTransition().apply {
            duration = 220
            interpolator = DecelerateInterpolator()
        }
        TransitionManager.beginDelayedTransition(root, transition)

        val targetRadius = if (nextState == IslandState.EXPANDED) dpToPx(24f) else dpToPx(18f)
        val currentRadius = if (currentState == IslandState.EXPANDED) dpToPx(24f) else dpToPx(18f)

        // Animate corner radius smoothly
        ValueAnimator.ofFloat(currentRadius, targetRadius).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                pillBackground?.cornerRadius = radius
            }
            start()
        }

        if (nextState == IslandState.EXPANDED) {
            collapsed.visibility = View.GONE
            expanded.visibility = View.VISIBLE
        } else {
            expanded.visibility = View.GONE
            collapsed.visibility = View.VISIBLE
        }

        currentState = nextState

        // Update layout parameters in WindowManager
        try {
            windowManager.updateViewLayout(root, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Builds the compact idle pill view.
     */
    private fun createCollapsedView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dpToPx(14f).toInt(),
                dpToPx(7f).toInt(),
                dpToPx(14f).toInt(),
                dpToPx(7f).toInt()
            )

            // Status pulse indicator (glowing emerald dot)
            val pulseDot = View(context).apply {
                val size = dpToPx(8f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(8f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#00E676"))
                }
            }

            // Minimalist island title
            val pillText = TextView(context).apply {
                text = "Pulse"
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

            // Cyan activity dot on trailing edge
            val trailingDot = View(context).apply {
                val size = dpToPx(6f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = dpToPx(8f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#00F0FF"))
                }
            }

            addView(pulseDot)
            addView(pillText)
            addView(trailingDot)
        }
    }

    /**
     * Builds the expanded panel showing PulseIsland info, active status, and quick stop button.
     */
    private fun createExpandedView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dpToPx(16f).toInt(),
                dpToPx(10f).toInt(),
                dpToPx(14f).toInt(),
                dpToPx(10f).toInt()
            )

            // Glowing indicator dot
            val indicator = View(context).apply {
                val size = dpToPx(10f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(10f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#00E676"))
                }
            }

            // Text block (Title + Status Subtitle)
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )

                val titleText = TextView(context).apply {
                    text = "PulseIsland"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                }

                val statusText = TextView(context).apply {
                    text = "Active • Tap pill to collapse"
                    textSize = 11f
                    setTextColor(Color.parseColor("#A0A0AB"))
                }

                addView(titleText)
                addView(statusText)
            }

            // Close button to stop the island directly
            val closeButton = ImageView(context).apply {
                val size = dpToPx(28f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = dpToPx(12f).toInt()
                }
                setImageResource(R.drawable.ic_close)
                setColorFilter(Color.parseColor("#D0D0DB"))
                setPadding(dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#26FFFFFF"))
                }
                contentDescription = "Stop Island"

                setOnClickListener {
                    onCloseRequested()
                }
            }

            addView(indicator)
            addView(textContainer)
            addView(closeButton)
        }
    }

    /**
     * Creates window layout params ensuring:
     * 1. WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY is used
     * 2. WRAP_CONTENT dimensions so touches outside pass through to underlying apps
     * 3. Positioned top-center, safely accounting for camera cutouts and status bar
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val statusBarHeight = getStatusBarHeight()
        val topOffset = (statusBarHeight * 0.35f).toInt().coerceAtLeast(dpToPx(12f).toInt())

        return WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

            // Non-focusable and does not block background touches
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = topOffset

            // Support display cutouts on Android 9+ (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Safely reads the system status bar height or defaults to 24dp.
     */
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            dpToPx(24f).toInt()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    /**
     * Removes the overlay and releases all references.
     */
    fun hide() {
        cleanup()
    }

    private fun cleanup() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // Already detached or window manager released
        } finally {
            overlayView = null
            collapsedLayout = null
            expandedLayout = null
            pillBackground = null
            currentState = IslandState.COLLAPSED
        }
    }
}
