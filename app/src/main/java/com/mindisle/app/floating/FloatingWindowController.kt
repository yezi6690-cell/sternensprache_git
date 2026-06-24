package com.mindisle.app.floating

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import com.mindisle.app.activity.CompanionActivity
import com.mindisle.app.live2d.Live2DModels

class FloatingWindowController(
    private val context: Context,
    private val settings: FloatingSettingsManager
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sizeManager = FloatingSizeManager(context, settings)
    private var floatingView: FloatingLive2DView? = null
    private var live2dParams: WindowManager.LayoutParams? = null
    private var quickMenuView: FloatingMenuView? = null
    private var quickMenuParams: WindowManager.LayoutParams? = null
    private var live2DController: FloatingLive2DController? = null
    private var idleActionController: FloatingIdleActionController? = null
    private var lastSizePreviewAt = 0L
    private var currentModelId: String? = null
    private var currentVariantId: String? = null
    private var onCloseRequested: (() -> Unit)? = null

    // ── Shared anchor ──
    private var floatingX = 0
    private var floatingY = 0

    private var dragDownRawX = 0f
    private var dragDownRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0

    // ── Touch area adjustment ──
    private var extraTouchWidthDp = 0
    private var extraTouchHeightDp = 0

    val isShowing: Boolean
        get() = floatingView != null

    fun show(modelId: String, variantId: String?, onCloseRequested: () -> Unit) {
        if (floatingView != null) {
            if (currentModelId == modelId && currentVariantId == variantId) {
                updateFromSettings()
                return
            }
            remove()
        }
        this.onCloseRequested = onCloseRequested
        val model = Live2DModels.availableModels.firstOrNull { it.id == modelId }
            ?: Live2DModels.getSelectedModel(context)
        val variant = FloatingModelVariantRepository.resolve(context, model, variantId)
        currentModelId = model.id
        currentVariantId = variant?.id
        Log.d("FloatingLayout", "modelId=${model.id}, variant=${variant?.id}")

        val view = FloatingLive2DView(context, settings, model.id, variant?.id)
        val params = buildLive2dParams(variant)
        val idleController = FloatingIdleActionController(view)

        FloatingGestureController(
            rootView = view,
            touchTargets = view.touchTargets,
            params = params,
            windowManager = windowManager,
            settings = settings,
            callbacks = object : FloatingGestureController.Callbacks {
                override fun onInteractionStart() {
                    idleController.pause()
                }
                override fun onTouchLookAt(x: Float, y: Float) {
                    view.lookAtDirect(x, y)
                }
                override fun onClick() {
                    view.showRandomBubble()
                }
                override fun onDoubleClick() {
                    toggleQuickMenu()
                }
                override fun onTripleClick() {
                    openApp()
                }
                override fun onPositionChanged(x: Int, y: Int) {
                    floatingX = x
                    floatingY = y
                    updateFloatingLayout()
                }
                override fun onInteractionEnd(wasDragging: Boolean) {
                    view.postDelayed({ view.resetLookAt() }, 1500L)
                    idleController.resumeLater()
                }
            }
        ).attach()

        // Reset to minimum scale for every model
        settings.modelScale = FloatingSizeManager.MIN_SCALE_MULTIPLIER
        floatingX = settings.x
        floatingY = settings.y
        extraTouchWidthDp = 0;   extraTouchHeightDp = 0
        baseWidthPx = 0;         baseHeightPx = 0
        windowManager.addView(view, params)
        floatingView = view
        live2dParams = params
        idleActionController = idleController.also { it.start() }
        live2DController = FloatingLive2DController(view, params).also {
            FloatingLive2DBridge.register(it)
        }

        // Menu hidden by default — double-tap to open
        updateFloatingLayout()
        Log.d("FloatingLayout", "floatingX=$floatingX, floatingY=$floatingY")

        if (variant == null) {
            view.postDelayed({
                autoFitWindowToModel(settings.modelScale, persist = true, retry = 3)
            }, 1500L)
        } else {
            settings.windowWidthDp = variant.widthDp
            settings.windowHeightDp = variant.heightDp
            settings.modelScale = FloatingSizeManager.MIN_SCALE_MULTIPLIER
            view.postDelayed({ view.refitToWindow(FloatingSizeManager.MIN_SCALE_MULTIPLIER) }, 500L)
        }
    }

    fun updateFromSettings() {
        settings.modelScale = FloatingSizeManager.MIN_SCALE_MULTIPLIER
        if (currentVariantId != null) {
            floatingView?.refitToWindow(settings.modelScale)
        } else {
            autoFitWindowToModel(settings.modelScale, persist = true, retry = 2)
        }
    }

    fun resetPosition() {
        settings.resetPosition()
        floatingX = settings.x
        floatingY = settings.y
        updateFloatingLayout()
    }

    fun remove() {
        val view = floatingView ?: return
        idleActionController?.stop()
        hideQuickMenu()
        runCatching { windowManager.removeView(view) }
        FloatingLive2DBridge.unregister(live2DController)
        view.destroy()
        floatingView = null
        live2dParams = null
        quickMenuView = null
        quickMenuParams = null
        live2DController = null
        idleActionController = null
        currentModelId = null
        currentVariantId = null
        onCloseRequested = null
    }

    // ── Anchor-aware layout ──

    private fun updateFloatingLayout() {
        val live2dLp = live2dParams ?: return
        live2dLp.x = floatingX
        live2dLp.y = floatingY
        floatingView?.let { windowManager.updateViewLayout(it, live2dLp) }

        val menuLp = quickMenuParams
        val menuView = quickMenuView
        if (menuLp != null && menuView != null) {
            val live2dWidth = live2dLp.width.takeIf { it > 0 } ?: dp(settings.windowWidthDp)
            menuLp.x = floatingX + live2dWidth + dp(QUICK_MENU_GAP_DP)
            menuLp.y = floatingY
            menuLp.height = getQuickMenuHeightPx()
            windowManager.updateViewLayout(menuView, menuLp)
            Log.d("FloatingLayout", "update top-aligned layout")
        }
    }

    private fun ensureGroupInsideScreen() {
        val lp = live2dParams ?: return
        val screen = currentScreenBounds()
        val menuWidth = quickMenuParams?.width ?: dp(QUICK_MENU_WIDTH_DP)
        val groupRight = floatingX + lp.width + dp(QUICK_MENU_GAP_DP) + menuWidth
        if (groupRight > screen.right) floatingX -= groupRight - screen.right
        if (floatingX < 0) floatingX = 0
        val groupBottom = floatingY + maxOf(lp.height, quickMenuParams?.height ?: 0)
        if (groupBottom > screen.bottom) floatingY -= groupBottom - screen.bottom
        if (floatingY < dp(MIN_MENU_TOP_DP)) floatingY = dp(MIN_MENU_TOP_DP)
    }

    // ── Quick menu ──

    private fun toggleQuickMenu() {
        if (quickMenuView != null) {
            hideQuickMenuOnly()
        } else {
            showQuickMenu()
        }
    }

    private fun showQuickMenu() {
        if (quickMenuView != null) return
        val menu = FloatingMenuView(context)
        bindMenu(menu)
        menu.render(Live2DModels.availableModels, settings.modelScale)
        setupMenuDrag(menu)

        val width = getQuickMenuWidthPx()
        val params = buildQuickMenuParams(width)
        windowManager.addView(menu, params)
        quickMenuView = menu
        quickMenuParams = params
        ensureGroupInsideScreen()
        updateFloatingLayout()

        Log.d("FloatingLayout", "live2dWidth=${live2dParams?.width}, live2dHeight=${live2dParams?.height}")
        Log.d("FloatingLayout", "menuX=${params.x}, menuY=${params.y}")
    }

    // ── Menu hide: pet stays ──
    private fun hideQuickMenuOnly() {
        quickMenuView?.let { runCatching { windowManager.removeView(it) } }
        quickMenuView = null
        quickMenuParams = null
    }

    // ── Full close: menu + pet + service ──
    private fun closeFloatingAll() {
        hideQuickMenuOnly()
        onCloseRequested?.invoke()
    }

    // Backward-compat alias used internally (remove(), cleanup)
    private fun hideQuickMenu() = hideQuickMenuOnly()

    private fun setupMenuDrag(menu: FloatingMenuView) {
        menu.dragHeader?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownRawX = event.rawX
                    dragDownRawY = event.rawY
                    dragStartX = floatingX
                    dragStartY = floatingY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingX = dragStartX + (event.rawX - dragDownRawX).toInt()
                    floatingY = dragStartY + (event.rawY - dragDownRawY).toInt()
                    ensureGroupInsideScreen()
                    updateFloatingLayout()
                    true
                }
                else -> true
            }
        }
    }

    private fun bindMenu(menu: FloatingMenuView) {
        menu.onOpenApp = { openApp() }
        menu.onHide = { hideQuickMenuOnly() }
        menu.onClose = { closeFloatingAll() }
        menu.onScalePreview = { scale -> updateScale(scale, save = false) }
        menu.onScaleCommit = { scale -> updateScale(scale, save = true) }
        menu.onResetDisplay = { resetDisplay() }
        menu.onTouchWidthChange = { delta -> applyTouchAreaChange("width", delta) }
        menu.onTouchHeightChange = { delta -> applyTouchAreaChange("height", delta) }
        menu.onModelSelected = { selection ->
            // Support "modelId:variantId" format (e.g., "xingyue_shuimu:small")
            val parts = selection.split(":", limit = 2)
            val modelId = parts[0]
            val explicitVariant = parts.getOrNull(1)
            extraTouchWidthDp = 0;   extraTouchHeightDp = 0
            baseWidthPx = 0;         baseHeightPx = 0
            settings.modelScale = FloatingSizeManager.MIN_SCALE_MULTIPLIER
            val result = floatingView?.switchModel(modelId, explicitVariant)
            if (result != null) {
                currentModelId = result.modelId
                currentVariantId = result.variant?.id
                if (result.variant != null) {
                    updateWindowSize(result.variant.widthDp, result.variant.heightDp, FloatingSizeManager.MIN_SCALE_MULTIPLIER, persist = true)
                } else {
                    autoFitWindowToModel(FloatingSizeManager.MIN_SCALE_MULTIPLIER, persist = true, retry = 4)
                }
                Log.d("FloatingLayout", "switch variant=${result.variant?.id}")
                ensureGroupInsideScreen()
                updateFloatingLayout()
                menu.render(Live2DModels.availableModels, settings.modelScale)
            }
        }
    }

    // ── Touch area ──
    private var baseWidthPx = 0
    private var baseHeightPx = 0

    private fun applyTouchAreaChange(type: String, deltaDp: Int) {
        if (type == "width")  extraTouchWidthDp  = deltaDp.coerceIn(0, 200)
        if (type == "height") extraTouchHeightDp = deltaDp.coerceIn(0, 200)
        val lp = live2dParams ?: return
        // Capture base on first call
        if (baseWidthPx <= 0) baseWidthPx = (lp.width.takeIf { it > 0 } ?: dp(settings.windowWidthDp))
        if (baseHeightPx <= 0) baseHeightPx = (lp.height.takeIf { it > 0 } ?: dp(settings.windowHeightDp))
        val cx = floatingX + (if (lp.width > 0) lp.width else baseWidthPx) / 2
        val cy = floatingY + (if (lp.height > 0) lp.height else baseHeightPx) / 2
        lp.width  = baseWidthPx  + dp(extraTouchWidthDp)
        lp.height = baseHeightPx + dp(extraTouchHeightDp)
        floatingX = cx - lp.width / 2
        floatingY = cy - lp.height / 2
        floatingView?.let { windowManager.updateViewLayout(it, lp) }
        ensureGroupInsideScreen()
        updateFloatingLayout()
        floatingView?.post { floatingView?.refitToWindow(settings.modelScale) }
    }

    // ── Scale / Size ──

    private fun updateScale(scale: Float, save: Boolean) {
        val view = floatingView ?: return
        val now = System.currentTimeMillis()
        if (!save && now - lastSizePreviewAt < SIZE_PREVIEW_THROTTLE_MS) return
        lastSizePreviewAt = now
        val nextScale = scale.coerceIn(
            FloatingSizeManager.MIN_SCALE_MULTIPLIER,
            FloatingSizeManager.MAX_SCALE_MULTIPLIER
        )
        if (save) settings.modelScale = nextScale
        view.post { view.previewModelScale(nextScale) }
        if (currentVariantId != null) {
            view.refitToWindow(nextScale)
        } else {
            autoFitWindowToModel(nextScale, persist = save, retry = 1)
        }
    }

    private fun updateWindowSize(widthDp: Int, heightDp: Int, scale: Float, persist: Boolean) {
        val view = floatingView ?: return
        val lp = live2dParams ?: return
        val oldW = lp.width.takeIf { it > 0 } ?: dp(settings.windowWidthDp)
        val cx = lp.x + oldW / 2
        val newW = dp(widthDp)
        val newH = dp(heightDp)
        lp.width = newW
        lp.height = newH
        floatingX = cx - newW / 2
        // Keep floatingY unchanged — top alignment must be preserved
        updateFloatingLayout()
        if (persist) {
            settings.windowWidthDp = widthDp
            settings.windowHeightDp = heightDp
            settings.modelScale = scale
            settings.saveWindowPosition(floatingX, floatingY)
        }
        view.post { view.refitToWindow(scale) }
    }

    private fun autoFitWindowToModel(scale: Float, persist: Boolean, retry: Int) {
        val view = floatingView ?: return
        view.requestModelVisualBounds(scale) { bounds ->
            if (bounds == null) {
                if (retry > 0) {
                    view.postDelayed({ autoFitWindowToModel(scale, persist, retry - 1) }, 360L)
                } else view.refitToWindow(scale)
                return@requestModelVisualBounds
            }
            val autoSize = sizeManager.calculateAutoSize(bounds.width, bounds.height, scale)
            updateWindowSize(autoSize.widthDp, autoSize.heightDp, scale, persist)
        }
    }

    private fun resetDisplay() {
        sizeManager.resetToDefault()
        floatingView?.resetModelTransform()
        val variant = floatingView?.currentVariant
        floatingView?.postDelayed({
            if (variant != null) {
                updateWindowSize(variant.widthDp, variant.heightDp,
                    FloatingSizeManager.DEFAULT_SCALE_MULTIPLIER, persist = true)
            } else {
                autoFitWindowToModel(
                    FloatingSizeManager.DEFAULT_SCALE_MULTIPLIER, persist = true, retry = 4)
            }
        }, 260L)
    }

    private fun openApp() {
        val intent = Intent(context, CompanionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }

    // ── Helpers ──

    private fun getQuickMenuHeightPx(): Int {
        val screen = currentScreenBounds()
        val available = screen.bottom - floatingY.coerceAtLeast(dp(MIN_MENU_TOP_DP)) -
            dp(QUICK_MENU_BOTTOM_MARGIN_DP)
        return minOf(dp(QUICK_MENU_MAX_HEIGHT_DP),
            available.coerceAtLeast(dp(QUICK_MENU_MIN_HEIGHT_DP)))
    }

    private fun getQuickMenuWidthPx(): Int {
        val lp = live2dParams ?: return dp(QUICK_MENU_WIDTH_DP)
        val screen = currentScreenBounds()
        val available = screen.width() - lp.width - dp(QUICK_MENU_GAP_DP)
        return minOf(dp(QUICK_MENU_WIDTH_DP), available.coerceAtLeast(dp(QUICK_MENU_MIN_WIDTH_DP)))
    }

    private fun currentScreenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            val metrics = android.util.DisplayMetrics().also {
                windowManager.defaultDisplay.getRealMetrics(it)
            }
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun buildLive2dParams(variant: FloatingModelVariant?): WindowManager.LayoutParams {
        val w = variant?.widthDp ?: FloatingSizeManager.DEFAULT_WINDOW_WIDTH_DP
        val h = variant?.heightDp ?: FloatingSizeManager.DEFAULT_WINDOW_HEIGHT_DP
        return WindowManager.LayoutParams(dp(w), dp(h),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatingX
            y = floatingY
        }
    }

    private fun buildQuickMenuParams(widthPx: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(widthPx, getQuickMenuHeightPx(),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    companion object {
        private const val SIZE_PREVIEW_THROTTLE_MS = 50L
        private const val QUICK_MENU_WIDTH_DP = 180
        private const val QUICK_MENU_MIN_WIDTH_DP = 96
        private const val QUICK_MENU_GAP_DP = 8
        private const val QUICK_MENU_MAX_HEIGHT_DP = 360
        private const val QUICK_MENU_MIN_HEIGHT_DP = 180
        private const val QUICK_MENU_BOTTOM_MARGIN_DP = 40
        private const val MIN_MENU_TOP_DP = 24
    }
}
