package com.mindisle.app.floating

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import com.mindisle.app.live2d.Live2DController
import com.mindisle.app.live2d.Live2DModels
import org.json.JSONObject

class FloatingLive2DView(
    context: Context,
    private val settings: FloatingSettingsManager,
    modelId: String,
    variantId: String?
) : FrameLayout(context) {
    data class ModelVisualBounds(
        val width: Float,
        val height: Float
    )

    private val webView = WebView(context)
    private val controller = Live2DController(webView)
    private val bubbleView = TextView(context)
    private var currentModel = Live2DModels.availableModels.firstOrNull { it.id == modelId }
        ?: Live2DModels.getSelectedModel(context)
    var currentVariant: FloatingModelVariant? =
        FloatingModelVariantRepository.resolve(context, currentModel, variantId)
        private set
    val touchTargets: List<View>
        get() = listOf(this, webView)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false

        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setupBubble()

        controller.configure()
        loadCurrentModel()
        applySavedTransform()
    }

    fun reloadCurrentModel() {
        loadCurrentModel()
        applySavedTransform()
    }

    fun showRandomBubble() {
        alpha = 1f
        val messages = arrayOf(
            "我在这里陪你",
            "要不要休息一下？",
            "点我可以回到心屿"
        )
        showBubble(messages.random())
        controller.playTapReaction(0f, 0f)
    }

    fun lookAt(x: Float, y: Float) {
        controller.lookAt(x, y, source = "external")
    }

    fun lookAtDirect(x: Float, y: Float) {
        controller.lookAt(x, y, source = "direct")
    }

    fun playTapReaction(x: Float, y: Float) {
        controller.playTapReaction(x, y, source = "external")
    }

    fun playIdleAction(actionName: String) {
        controller.playIdleAction(actionName)
    }

    fun resetLookAt() {
        controller.resetLookAt()
    }

    fun switchModel(modelId: String, variantId: String? = null): ModelSwitchResult? {
        val model = Live2DModels.saveSelectedModel(context, modelId) ?: return null
        settings.modelScale = 1f
        settings.modelOffsetX = 0f
        settings.modelOffsetY = 0f
        currentModel = model
        currentVariant = FloatingModelVariantRepository.resolve(context, model, variantId)
        settings.saveFloatingSelection(model.id, currentVariant?.id)
        loadCurrentModel()
        applySavedTransform()
        showBubble(model.name)
        return ModelSwitchResult(model.id, currentVariant)
    }

    fun setModelScale(scale: Float) {
        settings.modelScale = scale
        val renderScale = currentVariant?.renderScale ?: 1.0f
        controller.setUserScaleMultiplier(scale * renderScale)
    }

    fun previewModelScale(scale: Float) {
        val renderScale = currentVariant?.renderScale ?: 1.0f
        controller.setUserScaleMultiplier(scale * renderScale)
    }

    fun refitToWindow(scale: Float = settings.modelScale) {
        val renderScale = currentVariant?.renderScale ?: 1.0f
        syncCanvasAndModel(scale * renderScale)
    }

    fun requestModelVisualBounds(scale: Float = settings.modelScale, callback: (ModelVisualBounds?) -> Unit) {
        syncCanvasAndModel(scale)
        postDelayed({
            controller.evaluate("getModelVisualBounds && getModelVisualBounds();") { result ->
                callback(parseBounds(result))
            }
        }, 180L)
    }

    fun setModelPosition(x: Float, y: Float) {
        settings.modelOffsetX = x
        settings.modelOffsetY = y
        controller.setModelPosition(x, y)
    }

    fun resetModelTransform() {
        settings.modelScale = 1f
        settings.modelOffsetX = 0f
        settings.modelOffsetY = 0f
        controller.resetModelTransform()
        syncCanvasAndModel(settings.modelScale)
    }

    fun destroy() {
        webView.destroy()
    }

    private fun setupBubble() {
        bubbleView.visibility = View.GONE
        bubbleView.isClickable = false
        bubbleView.isFocusable = false
        bubbleView.textSize = 13f
        bubbleView.setTextColor(0xFF26324D.toInt())
        bubbleView.gravity = Gravity.CENTER
        bubbleView.setPadding(dp(12), dp(7), dp(12), dp(7))
        bubbleView.background = GradientDrawable().apply {
            setColor(0xEEFFFDF8.toInt())
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x33D07158)
        }
        addView(
            bubbleView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                topMargin = dp(10)
            }
        )
    }

    fun showBubble(text: String) {
        bubbleView.clearAnimation()
        bubbleView.text = text
        bubbleView.visibility = View.VISIBLE
        bubbleView.alpha = 0f
        bubbleView.animate()
            .alpha(1f)
            .setDuration(120L)
            .withEndAction {
                bubbleView.postDelayed({
                    bubbleView.animate()
                        .alpha(0f)
                        .setDuration(160L)
                        .withEndAction { bubbleView.visibility = View.GONE }
                        .start()
                }, 1600L)
            }
            .start()
    }

    private fun applySavedTransform() {
        postDelayed({
            syncCanvasAndModel(settings.modelScale)
        }, 420L)
        postDelayed({
            syncCanvasAndModel(settings.modelScale)
        }, 1200L)
    }

    private fun loadCurrentModel() {
        val variant = currentVariant
        val resolvedModel = if (variant == null) {
            currentModel
        } else {
            currentModel.copy(assetModelPath = variant.assetModelPath)
        }
        controller.loadModel(
            model = resolvedModel,
            floating = true,
            variant = variant?.id,
            displayPreset = variant?.displayPreset,
            initialExpression = variant?.initialExpression
        )
    }

    private fun syncCanvasAndModel(scale: Float) {
        post {
            if (width > 0 && height > 0) {
                controller.resizeCanvas(width, height)
                controller.fitModelToCanvas()
                controller.setUserScaleMultiplier(scale)
            }
        }
    }

    private fun parseBounds(result: String?): ModelVisualBounds? {
        val payload = result?.trim()
        if (payload.isNullOrEmpty() || payload == "null") return null
        return runCatching {
            val obj = JSONObject(payload)
            val width = obj.optDouble("width", 0.0).toFloat()
            val height = obj.optDouble("height", 0.0).toFloat()
            if (width <= 1f || height <= 1f) null else ModelVisualBounds(width, height)
        }.getOrNull()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    data class ModelSwitchResult(
        val modelId: String,
        val variant: FloatingModelVariant?
    )
}
