package com.mindisle.app.live2d

import android.util.Log
import android.webkit.WebView
import android.view.View
import android.widget.TextView
import org.json.JSONObject

class Live2DManager(
    private val webView: WebView,
    private val stateText: TextView,
    private val bubbleText: TextView
) {
    private val actionController = Live2DActionController()
    private val touchController = Live2DTouchController()
    private val controller = Live2DController(webView)

    val availableModels = Live2DModels.availableModels

    var currentModel: Live2DModel = availableModels.first()
        private set

    var state: Live2DState = Live2DState.IDLE
        private set

    init {
        controller.configure()
        loadDefaultModel()
    }

    fun setState(newState: Live2DState, bubble: String? = null) {
        state = newState
        stateText.text = newState.label
        setEmotionState(newState.label)
        bubble?.let { showBubble(it) }
    }

    fun touchCharacter(): String {
        val bubble = touchController.randomBubble()
        showBubble(bubble)
        if (currentModel.id == "xingyue_shuimu" && controller.isModelLoaded()) {
            controller.applyExpression(XINGYUE_SAFE_TAP_EXPRESSIONS.random())
        } else {
            playMotion("tap")
        }
        setState(Live2DState.HAPPY)
        return bubble
    }

    fun playMotion(motionName: String) {
        actionController.playMotion(motionName)
        controller.playMotion(motionName)
    }

    fun setExpression(expressionName: String) {
        actionController.setExpression(expressionName)
        controller.setExpression(expressionName)
    }

    fun applyExpression(expressionFile: String) {
        controller.applyExpression(expressionFile)
    }

    fun playMotionByFile(motionFile: String) {
        controller.playMotionByFile(motionFile)
    }

    fun lookAt(x: Float, y: Float, source: String = "direct") {
        controller.lookAt(x, y, source)
    }

    fun playTapReaction(x: Float, y: Float, source: String = "direct") {
        controller.playTapReaction(x, y, source)
    }

    fun resetLookAt() {
        controller.resetLookAt()
    }

    fun setTalkState(talking: Boolean) {
        controller.setTalkState(talking)
    }

    fun setEmotionState(state: String) {
        controller.setEmotionState(state)
    }

    fun runJs(script: String) {
        controller.runJs(script)
    }

    fun resetModel() {
        setState(Live2DState.IDLE)
        controller.resetModel()
    }

    fun changeModel(modelPath: String) {
        controller.changeModel(modelPath)
    }

    fun switchModel(modelId: String): Live2DModel? {
        val model = Live2DModels.saveSelectedModel(webView.context, modelId) ?: return null
        currentModel = model
        loadModel(model)
        return model
    }

    fun switchPlaceholderPet(petId: String) {
        actionController.switchModel(petId)
        switchModel(petId)
    }

    fun setViewScale(progress: Int) {
        val scale = 0.86f + progress / 100f * 0.24f
        controller.setModelScale(scale)
    }

    fun setDisplayPreset(preset: String) {
        controller.setDisplayPreset(preset)
    }

    fun isModelLoaded(): Boolean = controller.isModelLoaded()

    companion object {
        private val XINGYUE_SAFE_TAP_EXPRESSIONS = listOf(
            "脸红.exp3.json",
            "比心.exp3.json",
            "星星眼.exp3.json",
            "无语.exp3.json"
        )
    }

    private fun loadDefaultModel() {
        currentModel = Live2DModels.getSelectedModel(webView.context)
        loadModel(currentModel)
    }

    private fun loadModel(model: Live2DModel) {
        logAssetIntegrity(model)
        controller.loadModel(model)
        state = Live2DState.IDLE
        stateText.text = Live2DState.IDLE.label
    }

    private fun logAssetIntegrity(model: Live2DModel) {
        val ctx = webView.context
        val assetPath = model.assetModelPath
        val baseDir = assetPath.substringBeforeLast("/")

        Log.d("Live2D", "=== 模型资源诊断 ===")
        Log.d("Live2D", "  id: ${model.id}")
        Log.d("Live2D", "  assetPath: $assetPath")

        // 1. Entry file
        try {
            ctx.assets.open(assetPath).use { }
            Log.d("Live2D", "  入口: ✅ $assetPath")
        } catch (e: Exception) {
            Log.e("Live2D", "  入口: ❌ $assetPath  (${e.message})")
            return
        }

        // 2. Referenced files inside model3.json
        try {
            val json = ctx.assets.open(assetPath).bufferedReader().readText()
            val cfg = JSONObject(json)
            val refs = cfg.optJSONObject("FileReferences") ?: return

            // Moc
            val moc = refs.optString("Moc", null)
            if (moc != null) {
                try {
                    ctx.assets.open("$baseDir/$moc").use { }
                    Log.d("Live2D", "  Moc: ✅ $baseDir/$moc")
                } catch (e: Exception) {
                    Log.e("Live2D", "  Moc: ❌ $baseDir/$moc  (${e.message})")
                }
            }

            // Textures
            val textures = refs.optJSONArray("Textures")
            if (textures != null) {
                for (i in 0 until textures.length()) {
                    val tex = textures.getString(i)
                    try {
                        ctx.assets.open("$baseDir/$tex").use { }
                        Log.d("Live2D", "  Texture[$i]: ✅ $baseDir/$tex")
                    } catch (e: Exception) {
                        Log.e("Live2D", "  Texture[$i]: ❌ $baseDir/$tex  (${e.message})")
                    }
                }
            }

            // Physics
            val physics = refs.optString("Physics", null)
            if (physics != null) {
                try {
                    ctx.assets.open("$baseDir/$physics").use { }
                    Log.d("Live2D", "  Physics: ✅ $baseDir/$physics")
                } catch (e: Exception) {
                    Log.e("Live2D", "  Physics: ❌ $baseDir/$physics  (${e.message})")
                }
            }

            // DisplayInfo
            val cdi = refs.optString("DisplayInfo", null)
            if (cdi != null) {
                try {
                    ctx.assets.open("$baseDir/$cdi").use { }
                    Log.d("Live2D", "  CDI: ✅ $baseDir/$cdi")
                } catch (e: Exception) {
                    Log.e("Live2D", "  CDI: ❌ $baseDir/$cdi  (${e.message})")
                }
            }
        } catch (e: Exception) {
            Log.e("Live2D", "  model3.json 解析失败: ${e.message}")
        }
    }

    private fun showBubble(text: String) {
        bubbleText.clearAnimation()
        bubbleText.text = text
        bubbleText.visibility = View.VISIBLE
        bubbleText.alpha = 0f
        bubbleText.animate()
            .alpha(1f)
            .setDuration(140L)
            .withEndAction {
                bubbleText.postDelayed({
                    bubbleText.animate()
                        .alpha(0f)
                        .setDuration(180L)
                        .withEndAction { bubbleText.visibility = View.GONE }
                        .start()
                }, 1800L)
            }
            .start()
    }

}
