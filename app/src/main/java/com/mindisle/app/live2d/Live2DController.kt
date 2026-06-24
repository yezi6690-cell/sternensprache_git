package com.mindisle.app.live2d

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class Live2DController(private val webView: WebView) {
    private val bridge = Live2DBridge()
    private var pendingDisplayPreset: String? = null
    private var pendingInitialExpression: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.background = null
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = false
            useWideViewPort = false
            textZoom = 100
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = false
        }
        webView.addJavascriptInterface(bridge, "AndroidLive2D")
        webView.addJavascriptInterface(bridge, "MindIsleLive2DBridge")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.e(
                    "Live2DConsole",
                    "${consoleMessage.messageLevel()} ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}"
                )
                return true
            }
        }
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(webView.context.applicationContext)
            )
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }
    }

    fun loadModel(
        model: Live2DModel,
        floating: Boolean = false,
        variant: String? = null,
        displayPreset: String? = null,
        initialExpression: String? = null
    ) {
        Log.d("XingyueLive2D", "load model in Live2DController")
        Log.d("XingyueLive2D", "modelId=${model.id}")
        Log.d("XingyueLive2D", "modelName=${model.name}")
        Log.d("XingyueLive2D", "assetModelPath=${model.assetModelPath}")
        bridge.currentModelId = model.id
        bridge.currentModelName = model.name
        bridge.currentAssetModelPath = model.assetModelPath
        bridge.currentFailureMessage = failureMessageFor(model)
        bridge.isModelLoaded = false
        pendingDisplayPreset = displayPreset
        pendingInitialExpression = initialExpression
        Live2DAssetValidator.validate(webView.context, model)
        val url = Live2DModels.viewerUrl(model, floating, variant)
        Log.d("Live2DAssets", "WebView loadUrl: $url")
        if (floating) {
            Log.d("FloatingPet", "floating url=$url")
        }
        webView.loadUrl(url)
    }

    fun playMotion(motionName: String) = callJs("playMotion", motionName)

    fun setExpression(expressionName: String) = callJs("setExpression", expressionName)

    fun setEmotionState(state: String) = callJs("setEmotionState", state)

    fun resetModel() = callJs("resetModel")

    fun changeModel(modelPath: String) = callJs("changeModel", modelPath)

    fun setModelScale(scale: Float) = callJsRaw("setModelScale(${scale.coerceIn(0.8f, 1.2f)})")

    fun setUserScaleMultiplier(multiplier: Float) =
        callJsRaw("setUserScaleMultiplier(${multiplier.coerceIn(0.8f, 1.2f)});")

    fun setModelPosition(x: Float, y: Float) = callJsRaw("setModelPosition($x,$y)")

    fun resetModelTransform() = callJs("resetModelTransform")

    fun resizeCanvas(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        callJsRaw("resizeCanvas($width,$height);")
    }

    fun fitModelToCanvas() = callJs("fitModelToCanvas")

    fun lookAt(x: Float, y: Float, source: String = "direct") =
        callJsRaw("lookAtSmooth(${x.coerceIn(-1f, 1f)},${y.coerceIn(-1f, 1f)},${JSONObject.quote(source)});")

    fun playTapReaction(x: Float, y: Float, source: String = "direct") =
        callJsRaw("playTapReactionSmooth(${x.coerceIn(-1f, 1f)},${y.coerceIn(-1f, 1f)},${JSONObject.quote(source)});")

    fun resetLookAt() = callJs("resetLookAtSmooth")

    fun setTalkState(talking: Boolean) = callJsRaw("setTalkState($talking);")

    fun playIdleAction(actionName: String) = callJs("playIdleAction", actionName)

    fun showExpression(expressionName: String) = callJs("showExpression", expressionName)

    fun setDisplayPreset(preset: String) = callJs("setDisplayPreset", preset)

    fun applyExpression(expressionFile: String) = callJs("applyExpressionByFile", expressionFile)

    fun runJs(script: String) = callJsRaw(script)

    fun playMotionByFile(motionFile: String) = callJs("playMotionByFile", motionFile)

    fun isModelLoaded(): Boolean = bridge.isModelLoaded

    fun evaluate(script: String, callback: ((String?) -> Unit)? = null) {
        webView.post { webView.evaluateJavascript(script, callback) }
    }

    private fun callJs(methodName: String, vararg args: String) {
        val joinedArgs = args.joinToString(",") { JSONObject.quote(it) }
        callJsRaw("$methodName($joinedArgs);")
    }

    private fun callJsRaw(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun failureMessageFor(model: Live2DModel): String {
        return "${model.name}模型加载失败，请检查模型文件是否完整。"
    }

    inner class Live2DBridge {
        var currentModelId: String = ""
        var currentModelName: String = ""
        var currentAssetModelPath: String = ""
        var currentFailureMessage: String = "模型加载失败，请检查模型文件是否完整。"
        var isModelLoaded: Boolean = false

        @JavascriptInterface
        fun onModelLoaded(modelId: String?) {
            val firstSuccess = !isModelLoaded
            currentModelId = modelId ?: currentModelId
            isModelLoaded = true
            currentFailureMessage = ""
            Log.d("XingyueLive2D", "clear failure message")
            Log.d("XingyueLive2D", "model loaded id=$currentModelId")
            if (firstSuccess) {
                webView.post {
                    pendingDisplayPreset?.let { setDisplayPreset(it) }
                    pendingInitialExpression?.let { applyExpression(it) }
                }
            }
        }

        @JavascriptInterface
        fun onModelLoadFailed(message: String?) {
            if (isModelLoaded) {
                Log.d("XingyueLive2D", "ignore load failure after success: ${message.orEmpty()}")
                return
            }
            val toastMessage = message?.takeIf { it.isNotBlank() } ?: currentFailureMessage
            Log.e("XingyueLive2D", "load failed in Live2DController")
            Log.e("XingyueLive2D", "modelId=$currentModelId")
            Log.e("XingyueLive2D", "modelName=$currentModelName")
            Log.e("XingyueLive2D", "assetModelPath=$currentAssetModelPath")
            Log.e("Live2DAssets", "$currentModelName WebView 加载失败：$toastMessage")
            webView.post {
                Toast.makeText(webView.context, toastMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
