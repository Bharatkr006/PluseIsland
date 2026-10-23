package com.pulseisland.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.pulseisland.app.R
import com.pulseisland.app.data.IslandPreferences
import com.pulseisland.app.music.MusicState

/**
 * Manages the floating Dynamic Island overlay window using Android WindowManager.
 *
 * Music-aware: switches between default island appearance and a premium music player
 * interface based on active media session state. The transition is smooth and event-driven.
 *
 * Battery efficiency:
 * - No polling loops or timers
 * - Waveform animation only runs when playing
 * - Progress bar updates only when expanded AND playing
 * - All resources released on cleanup
 */
class IslandOverlayManager(
    private val context: Context,
    private val onCloseRequested: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var currentState: IslandState = IslandState.COLLAPSED

    private val density = context.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    // View references — default mode
    private var defaultCollapsedLayout: LinearLayout? = null
    private var defaultExpandedLayout: LinearLayout? = null

    // View references — music mode collapsed
    private var musicCollapsedLayout: LinearLayout? = null
    private var musicAlbumThumb: ImageView? = null
    private var waveformView: WaveformView? = null

    // View references — music mode expanded player
    private var musicExpandedLayout: LinearLayout? = null
    private var expandedAlbumArt: ImageView? = null
    private var expandedTitle: TextView? = null
    private var expandedArtist: TextView? = null
    private var expandedProgress: ProgressBar? = null
    private var expandedElapsed: TextView? = null
    private var expandedDuration: TextView? = null
    private var expandedPlayPause: ImageView? = null
    private var expandedSkipNext: ImageView? = null
    private var expandedSkipPrev: ImageView? = null

    private var pillBackground: GradientDrawable? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var closeButtonRef: View? = null

    // Music state
    private var currentMusicState: MusicState = MusicState.EMPTY
    private var musicControlCallbacks: MusicControlCallbacks? = null

    // Progress update runnable (only active when expanded + playing)
    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            val ms = currentMusicState
            if (currentState == IslandState.EXPANDED && ms.isPlaying && ms.durationMs > 0) {
                val progress = (ms.progressFraction() * 1000).toInt()
                expandedProgress?.progress = progress
                expandedElapsed?.text = formatDuration(ms.estimatedPositionMs())
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    val isShowing: Boolean
        get() = overlayView != null

    /** Callbacks to music control actions — wired from the service */
    interface MusicControlCallbacks {
        fun onPlayPause()
        fun onSkipNext()
        fun onSkipPrevious()
    }

    fun setMusicControlCallbacks(callbacks: MusicControlCallbacks?) {
        musicControlCallbacks = callbacks
    }

    // ─── Show / Hide ────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return

        val params = createLayoutParams()
        windowParams = params

        val root = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        val currentScale = IslandPreferences.getScale(context)
        root.scaleX = currentScale
        root.scaleY = currentScale

        val initialRadius = dpToPx(20f)
        pillBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = initialRadius
            setColor(Color.BLACK)
            setStroke(dpToPx(1f).toInt(), Color.parseColor("#33FFFFFF"))
        }
        root.background = pillBackground

        // Build all layouts
        val defCollapsed = buildDefaultCollapsedView()
        val defExpanded = buildDefaultExpandedView()
        val musCollapsed = buildMusicCollapsedView()
        val musExpanded = buildMusicExpandedView()

        defaultCollapsedLayout = defCollapsed
        defaultExpandedLayout = defExpanded
        musicCollapsedLayout = musCollapsed
        musicExpandedLayout = musExpanded

        // Initially show default collapsed
        defCollapsed.visibility = View.VISIBLE
        defExpanded.visibility = View.GONE
        musCollapsed.visibility = View.GONE
        musExpanded.visibility = View.GONE

        val childParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        root.addView(defCollapsed, childParams)
        root.addView(defExpanded, childParams)
        root.addView(musCollapsed, childParams)
        root.addView(musExpanded, childParams)

        // Touch handling: drag OR tap to expand/collapse
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var initX = 0
        var initY = 0
        var initTouchX = 0f
        var initTouchY = 0f
        var isDragging = false

        root.setOnTouchListener { _, event ->
            val winParams = windowParams ?: return@setOnTouchListener false
            val closeBtn = closeButtonRef
            if (currentState == IslandState.EXPANDED && closeBtn != null &&
                isPointInsideView(event.rawX, event.rawY, closeBtn)) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = winParams.x; initY = winParams.y
                    initTouchX = event.rawX; initTouchY = event.rawY
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initTouchX
                    val dy = event.rawY - initTouchY
                    if (!isDragging && (dx * dx + dy * dy) > (touchSlop * touchSlop)) isDragging = true
                    if (isDragging) {
                        winParams.x = (initX + dx).toInt()
                        winParams.y = (initY + dy).toInt().coerceAtLeast(0)
                        try { windowManager.updateViewLayout(root, winParams) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        IslandPreferences.setXOffsetDp(context, (winParams.x / density).toInt())
                        IslandPreferences.setYOffsetDp(context, (winParams.y / density).toInt())
                    } else {
                        toggleState()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { isDragging = false; true }
                else -> false
            }
        }

        try {
            windowManager.addView(root, params)
            overlayView = root
            currentState = IslandState.COLLAPSED
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
        }
    }

    fun hide() { cleanup() }

    // ─── Music State Update ──────────────────────────────────────────────────────

    /**
     * Called from the service whenever MusicState changes.
     * Updates collapsed/expanded views without rebuilding them.
     */
    fun updateMusicState(state: MusicState) {
        currentMusicState = state
        val root = overlayView ?: return

        mainHandler.post {
            when {
                state.hasActiveSession -> showMusicMode(state)
                else -> showDefaultMode()
            }
            // Refresh expanded player if currently expanded
            if (currentState == IslandState.EXPANDED && state.hasActiveSession) {
                refreshExpandedMusicPlayer(state)
            }
        }
    }

    /** Switch to music-aware collapsed pill */
    private fun showMusicMode(state: MusicState) {
        // Update album thumb
        val art = state.albumArt
        if (art != null) {
            val rounded = RoundedBitmapDrawableFactory.create(context.resources, art).apply {
                cornerRadius = dpToPx(4f)
            }
            musicAlbumThumb?.setImageDrawable(rounded)
            musicAlbumThumb?.visibility = View.VISIBLE
        } else {
            musicAlbumThumb?.setImageResource(R.drawable.ic_music_note)
            musicAlbumThumb?.visibility = View.VISIBLE
        }

        // Waveform animation
        if (state.isPlaying) {
            waveformView?.startAnimation()
        } else {
            waveformView?.stopAnimation()
        }

        // Swap to music collapsed if currently in default collapsed
        if (currentState == IslandState.COLLAPSED) {
            defaultCollapsedLayout?.visibility = View.GONE
            defaultExpandedLayout?.visibility = View.GONE
            musicCollapsedLayout?.visibility = View.VISIBLE
            musicExpandedLayout?.visibility = View.GONE
        }
    }

    /** Switch back to default island appearance */
    private fun showDefaultMode() {
        waveformView?.stopAnimation()
        if (currentState == IslandState.COLLAPSED) {
            musicCollapsedLayout?.visibility = View.GONE
            musicExpandedLayout?.visibility = View.GONE
            defaultCollapsedLayout?.visibility = View.VISIBLE
            defaultExpandedLayout?.visibility = View.GONE
        }
    }

    /** Refresh the expanded music player content */
    private fun refreshExpandedMusicPlayer(state: MusicState) {
        // Album art
        val art = state.albumArt
        if (art != null) {
            val rounded = RoundedBitmapDrawableFactory.create(context.resources, art).apply {
                cornerRadius = dpToPx(8f)
            }
            expandedAlbumArt?.setImageDrawable(rounded)
        } else {
            expandedAlbumArt?.setImageResource(R.drawable.ic_music_note)
        }

        expandedTitle?.text = state.title ?: "Unknown Track"
        expandedArtist?.text = state.artist ?: "Unknown Artist"

        // Play/pause icon
        expandedPlayPause?.setImageResource(
            if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        // Progress
        if (state.durationMs > 0) {
            expandedProgress?.max = 1000
            expandedProgress?.progress = (state.progressFraction() * 1000).toInt()
            expandedDuration?.text = formatDuration(state.durationMs)
            expandedElapsed?.text = formatDuration(state.estimatedPositionMs())
        } else {
            expandedProgress?.isIndeterminate = false
            expandedProgress?.progress = 0
            expandedDuration?.text = "--:--"
            expandedElapsed?.text = "--:--"
        }

        // Skip buttons enabled state
        expandedSkipNext?.alpha = if (state.canSkipNext) 1f else 0.35f
        expandedSkipPrev?.alpha = if (state.canSkipPrevious) 1f else 0.35f

        // Start/stop progress updater
        mainHandler.removeCallbacks(progressUpdateRunnable)
        if (state.isPlaying && state.durationMs > 0) {
            mainHandler.postDelayed(progressUpdateRunnable, 1000)
        }
    }

    // ─── Configuration ───────────────────────────────────────────────────────────

    fun updateConfiguration(config: IslandPreferences.IslandConfig) {
        val root = overlayView ?: return
        val params = windowParams ?: return
        params.x = dpToPx(config.xOffsetDp.toFloat()).toInt()
        params.y = dpToPx(config.yOffsetDp.toFloat()).toInt()
        root.scaleX = config.scale
        root.scaleY = config.scale
        try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    fun onConfigurationChanged() {
        val root = overlayView ?: return
        try {
            val updatedParams = createLayoutParams()
            windowParams = updatedParams
            windowManager.updateViewLayout(root, updatedParams)
        } catch (_: Exception) {}
    }

    // ─── Toggle Expand / Collapse ────────────────────────────────────────────────

    private fun toggleState() {
        val root = overlayView ?: return
        val params = windowParams ?: return
        val hasMusicSession = currentMusicState.hasActiveSession

        val nextState = if (currentState == IslandState.COLLAPSED) IslandState.EXPANDED else IslandState.COLLAPSED

        val transition = AutoTransition().apply {
            duration = 250
            interpolator = DecelerateInterpolator()
        }
        TransitionManager.beginDelayedTransition(root, transition)

        val targetRadius = if (nextState == IslandState.EXPANDED) dpToPx(24f) else dpToPx(20f)
        val fromRadius = if (currentState == IslandState.EXPANDED) dpToPx(24f) else dpToPx(20f)
        ValueAnimator.ofFloat(fromRadius, targetRadius).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { pillBackground?.cornerRadius = it.animatedValue as Float }
            start()
        }

        if (nextState == IslandState.EXPANDED) {
            if (hasMusicSession) {
                defaultCollapsedLayout?.visibility = View.GONE
                defaultExpandedLayout?.visibility = View.GONE
                musicCollapsedLayout?.visibility = View.GONE
                musicExpandedLayout?.visibility = View.VISIBLE
                refreshExpandedMusicPlayer(currentMusicState)
            } else {
                defaultCollapsedLayout?.visibility = View.GONE
                defaultExpandedLayout?.visibility = View.VISIBLE
                musicCollapsedLayout?.visibility = View.GONE
                musicExpandedLayout?.visibility = View.GONE
            }
        } else {
            mainHandler.removeCallbacks(progressUpdateRunnable)
            if (hasMusicSession) {
                defaultCollapsedLayout?.visibility = View.GONE
                defaultExpandedLayout?.visibility = View.GONE
                musicCollapsedLayout?.visibility = View.VISIBLE
                musicExpandedLayout?.visibility = View.GONE
            } else {
                defaultCollapsedLayout?.visibility = View.VISIBLE
                defaultExpandedLayout?.visibility = View.GONE
                musicCollapsedLayout?.visibility = View.GONE
                musicExpandedLayout?.visibility = View.GONE
            }
        }

        currentState = nextState
        try { windowManager.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    // ─── View Builders ───────────────────────────────────────────────────────────

    /** Default collapsed pill: green dot + "Pulse" text + cyan dot */
    private fun buildDefaultCollapsedView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14f).toInt(), dpToPx(7f).toInt(), dpToPx(14f).toInt(), dpToPx(7f).toInt())

            val pulseDot = View(context).apply {
                val s = dpToPx(8f).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dpToPx(8f).toInt() }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#00E676")) }
            }
            val pillText = TextView(context).apply {
                text = "Pulse"
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val trailingDot = View(context).apply {
                val s = dpToPx(6f).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginStart = dpToPx(8f).toInt() }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#00F0FF")) }
            }
            addView(pulseDot); addView(pillText); addView(trailingDot)
        }
    }

    /** Default expanded view: green dot + PulseIsland text + close button */
    private fun buildDefaultExpandedView(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16f).toInt(), dpToPx(10f).toInt(), dpToPx(14f).toInt(), dpToPx(10f).toInt())

            val indicator = View(context).apply {
                val s = dpToPx(10f).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dpToPx(10f).toInt() }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#00E676")) }
            }
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(TextView(context).apply {
                    text = "PulseIsland"; textSize = 14f; setTextColor(Color.WHITE)
                    typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = "Active • Tap pill to collapse"; textSize = 11f
                    setTextColor(Color.parseColor("#A0A0AB"))
                })
            }
            val closeButton = ImageView(context).apply {
                val s = dpToPx(28f).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s).apply { marginStart = dpToPx(12f).toInt() }
                setImageResource(R.drawable.ic_close)
                setColorFilter(Color.parseColor("#D0D0DB"))
                setPadding(dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt())
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#26FFFFFF")) }
                contentDescription = "Stop Island"
                setOnClickListener { onCloseRequested() }
            }
            closeButtonRef = closeButton
            addView(indicator); addView(textContainer); addView(closeButton)
        }
    }

    /** Music collapsed pill: rounded album art thumb + track info label + waveform */
    @SuppressLint("SetTextI18n")
    private fun buildMusicCollapsedView(): LinearLayout {
        val waveform = WaveformView(context)
        waveformView = waveform

        val thumbSize = dpToPx(28f).toInt()
        val thumb = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(thumbSize, thumbSize).apply {
                marginEnd = dpToPx(8f).toInt()
            }
            setImageResource(R.drawable.ic_music_note)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(5f)
                setColor(Color.parseColor("#222226"))
            }
            clipToOutline = true
        }
        musicAlbumThumb = thumb

        val waveParams = LinearLayout.LayoutParams(dpToPx(20f).toInt(), dpToPx(16f).toInt()).apply {
            marginStart = dpToPx(6f).toInt()
            gravity = Gravity.CENTER_VERTICAL
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8f).toInt(), dpToPx(6f).toInt(), dpToPx(10f).toInt(), dpToPx(6f).toInt())
            addView(thumb)
            addView(waveform, waveParams)
        }
    }

    /**
     * Music expanded player: album art + title/artist + progress + controls
     * All in a vertical LinearLayout with tight spacing
     */
    private fun buildMusicExpandedView(): LinearLayout {
        val artSize = dpToPx(68f).toInt()
        val albumArt = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(artSize, artSize).apply {
                marginEnd = dpToPx(12f).toInt()
            }
            setImageResource(R.drawable.ic_music_note)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(8f)
                setColor(Color.parseColor("#1A1A1E"))
            }
            clipToOutline = true
        }
        expandedAlbumArt = albumArt

        // Right side: title/artist/progress/controls
        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dpToPx(160f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(context).apply {
            text = "Unknown Track"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        expandedTitle = titleTv

        val artistTv = TextView(context).apply {
            text = "Unknown Artist"
            textSize = 11f
            setTextColor(Color.parseColor("#9A9AAA"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        expandedArtist = artistTv

        // Progress + duration row
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(3f).toInt()
            ).apply { topMargin = dpToPx(6f).toInt(); bottomMargin = dpToPx(2f).toInt() }
            max = 1000
            progress = 0
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00F0FF"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333340"))
        }
        expandedProgress = progressBar

        val timeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(6f).toInt() }
            weightSum = 1f
        }
        val elapsedTv = TextView(context).apply {
            text = "--:--"; textSize = 9f; setTextColor(Color.parseColor("#666680"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val durationTv = TextView(context).apply {
            text = "--:--"; textSize = 9f; setTextColor(Color.parseColor("#666680"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        expandedElapsed = elapsedTv
        expandedDuration = durationTv
        timeRow.addView(elapsedTv); timeRow.addView(durationTv)

        // Control buttons row
        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnSize = dpToPx(30f).toInt()
        val playPauseBtnSize = dpToPx(36f).toInt()

        val skipPrev = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setImageResource(R.drawable.ic_skip_previous)
            setColorFilter(Color.WHITE)
            alpha = 0.35f
            setOnClickListener { musicControlCallbacks?.onSkipPrevious() }
        }
        expandedSkipPrev = skipPrev

        val playPause = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(playPauseBtnSize, playPauseBtnSize).apply {
                marginStart = dpToPx(10f).toInt()
                marginEnd = dpToPx(10f).toInt()
            }
            setImageResource(R.drawable.ic_play)
            setColorFilter(Color.parseColor("#00F0FF"))
            setOnClickListener { musicControlCallbacks?.onPlayPause() }
        }
        expandedPlayPause = playPause

        val skipNext = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setImageResource(R.drawable.ic_skip_next)
            setColorFilter(Color.WHITE)
            alpha = 0.35f
            setOnClickListener { musicControlCallbacks?.onSkipNext() }
        }
        expandedSkipNext = skipNext

        controlsRow.addView(skipPrev); controlsRow.addView(playPause); controlsRow.addView(skipNext)

        rightCol.addView(titleTv); rightCol.addView(artistTv)
        rightCol.addView(progressBar); rightCol.addView(timeRow); rightCol.addView(controlsRow)

        // Close button (top right corner)
        val closeBtn = ImageView(context).apply {
            val s = dpToPx(22f).toInt()
            layoutParams = FrameLayout.LayoutParams(s, s, Gravity.TOP or Gravity.END).apply {
                topMargin = dpToPx(4f).toInt()
                marginEnd = dpToPx(4f).toInt()
            }
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.parseColor("#88FFFFFF"))
            setOnClickListener { onCloseRequested() }
        }
        closeButtonRef = closeBtn

        // Outer container
        val outerFrame = FrameLayout(context)
        val innerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12f).toInt(), dpToPx(12f).toInt(), dpToPx(36f).toInt(), dpToPx(12f).toInt())
            addView(albumArt); addView(rightCol)
        }
        outerFrame.addView(innerRow)
        outerFrame.addView(closeBtn)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(outerFrame)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun isPointInsideView(rawX: Float, rawY: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0].toFloat(); val top = location[1].toFloat()
        return rawX in left..(left + view.width) && rawY in top..(top + view.height)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val savedX = dpToPx(IslandPreferences.getXOffsetDp(context).toFloat()).toInt()
        val savedY = dpToPx(IslandPreferences.getYOffsetDp(context).toFloat()).toInt()
        return WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = savedX; y = savedY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)

    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "--:--"
        val totalSec = ms / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun cleanup() {
        mainHandler.removeCallbacks(progressUpdateRunnable)
        waveformView?.stopAnimation()
        val view = overlayView ?: return
        try { windowManager.removeView(view) } catch (_: Exception) {}
        finally {
            overlayView = null
            defaultCollapsedLayout = null; defaultExpandedLayout = null
            musicCollapsedLayout = null; musicExpandedLayout = null
            musicAlbumThumb = null; waveformView = null
            expandedAlbumArt = null; expandedTitle = null; expandedArtist = null
            expandedProgress = null; expandedElapsed = null; expandedDuration = null
            expandedPlayPause = null; expandedSkipNext = null; expandedSkipPrev = null
            pillBackground = null; windowParams = null; closeButtonRef = null
            currentState = IslandState.COLLAPSED
        }
    }
}
