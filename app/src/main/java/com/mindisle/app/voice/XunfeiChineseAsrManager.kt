package com.mindisle.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Base64
import com.mindisle.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class XunfeiChineseAsrManager : VoiceRecognizerManager {
    private companion object {
        const val TAG = "XinyuVoice"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private lateinit var appContext: Context
    private var recorder: PcmAudioRecorder? = null
    private var webSocket: WebSocket? = null
    private var callback: VoiceRecognitionCallback? = null
    private val listening = AtomicBoolean(false)
    private val firstFrame = AtomicBoolean(true)
    private val stopRequested = AtomicBoolean(false)
    private val audioSeq = AtomicInteger(1)
    private var resultPieces = linkedMapOf<Int, String>()
    private var fallbackSn = 0

    override fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun startListening(callback: VoiceRecognitionCallback) {
        if (listening.get()) return
        this.callback = callback
        if (!::appContext.isInitialized) {
            callback.onError("语音服务未初始化")
            callback.onEnd()
            return
        }
        if (BuildConfig.XFYUN_APP_ID.isBlank()
            || BuildConfig.XFYUN_API_KEY.isBlank()
            || BuildConfig.XFYUN_API_SECRET.isBlank()
            || BuildConfig.XFYUN_ASR_URL.isBlank()
        ) {
            logSafeConfig()
            callback.onError("语音服务配置缺失，请检查 local.properties")
            callback.onEnd()
            return
        }

        logSafeConfig()

        resultPieces.clear()
        fallbackSn = 0
        firstFrame.set(true)
        audioSeq.set(1)
        stopRequested.set(false)
        listening.set(true)

        val authUrl = try {
            createAuthUrl(BuildConfig.XFYUN_ASR_URL)
            } catch (_: Exception) {
            listening.set(false)
            callback.onError("语音服务鉴权失败，请检查 APPID、APIKey 或 APISecret")
            callback.onEnd()
            return
        }

        webSocket = client.newWebSocket(Request.Builder().url(authUrl).build(), createListener(callback))
    }

    override fun stopListening() {
        if (!listening.get()) return
        stopRequested.set(true)
        cleanupRecorder()
        sendEndFrame()
    }

    override fun cancel() {
        stopRequested.set(true)
        cleanupRecorder()
        webSocket?.close(1000, "cancel")
        webSocket = null
        listening.set(false)
        post { callback?.onEnd() }
    }

    override fun release() {
        cancel()
        client.dispatcher.executorService.shutdown()
    }

    private fun createListener(callback: VoiceRecognitionCallback): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                post { callback.onStart() }
                startRecorder(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received")
                handleServerMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing code=$code reason=$reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                cleanupRecorder()
                listening.set(false)
                Log.e(TAG, "WebSocket failure: ${t.javaClass.simpleName}, message=${t.message}", t)
                if (response != null) {
                    Log.e(TAG, "WebSocket failure response code=${response.code}, message=${response.message}")
                }
                val message = mapConnectionFailure(t, response)
                post {
                    callback.onError(message)
                    callback.onEnd()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed code=$code reason=$reason")
                cleanupRecorder()
                listening.set(false)
                post {
                    if (stopRequested.get() && currentResultText().isBlank()) {
                        callback.onError("没有听清，可以再试一次")
                    }
                    callback.onEnd()
                }
            }
        }
    }

    private fun startRecorder(socket: WebSocket) {
        recorder = PcmAudioRecorder(appContext).also { audioRecorder ->
            audioRecorder.start(
                onFrame = { frame ->
                    if (!listening.get() || stopRequested.get()) return@start
                    socket.send(createAudioFrame(frame, firstFrame.getAndSet(false), end = false))
                    Thread.sleep(40)
                },
                onError = {
                    post { callback?.onError("麦克风启动失败，请检查权限") }
                    stopListening()
                }
            )
        }
    }

    private fun cleanupRecorder() {
        recorder?.stop()
        recorder = null
    }

    private fun sendEndFrame() {
        val socket = webSocket ?: return
        socket.send(createAudioFrame(ByteArray(0), first = false, end = true))
    }

    private fun createAudioFrame(frame: ByteArray, first: Boolean, end: Boolean): String {
        val status = when {
            end -> 2
            first -> 0
            else -> 1
        }
        val json = JSONObject()
            .put("header", JSONObject()
                .put("app_id", BuildConfig.XFYUN_APP_ID)
                .put("status", status)
            )
            .put("payload", JSONObject()
                .put("audio", JSONObject()
                    .put("encoding", "raw")
                    .put("sample_rate", 16000)
                    .put("channels", 1)
                    .put("bit_depth", 16)
                    .put("seq", audioSeq.getAndIncrement())
                    .put("status", status)
                    .put("audio", Base64.encodeToString(frame, Base64.NO_WRAP))
                )
            )

        if (first) {
            json.put("parameter", JSONObject()
                .put("iat", JSONObject()
                    .put("domain", "slm")
                    .put("language", "zh_cn")
                    .put("accent", "mandarin")
                    .put("eos", 6000)
                    .put("dwa", "wpgs")
                    .put("result", JSONObject()
                        .put("encoding", "utf8")
                        .put("compress", "raw")
                        .put("format", "json")
                    )
                )
            )
        }
        return json.toString()
    }

    private fun handleServerMessage(text: String) {
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        val header = json.optJSONObject("header")
        val code = header?.optInt("code", 0) ?: json.optInt("code", 0)
        if (code != 0) {
            cleanupRecorder()
            listening.set(false)
            webSocket?.close(1000, "xfyun error")
            val xfyunMessage = header?.optString("message")
                ?: json.optString("message")
                ?: json.optString("desc")
            val sid = header?.optString("sid") ?: json.optString("sid")
            Log.e(TAG, "xunfei code=$code message=$xfyunMessage sid=$sid")
            val message = if (isAuthError(code)) {
                "语音服务鉴权失败，请检查 APPID、APIKey 或 APISecret"
            } else if (xfyunMessage.contains("no category route found", ignoreCase = true)) {
                "语音服务参数错误，请检查 domain / URL 配置"
            } else if (xfyunMessage.isNotBlank()) {
                "语音识别失败：$xfyunMessage"
            } else {
                "语音识别失败：错误码 $code"
            }
            post {
                callback?.onError(message)
                callback?.onEnd()
            }
            return
        }

        val resultObject = decodeResultObject(json)
        if (resultObject != null) {
            mergeResult(resultObject)
            val partial = currentResultText()
            if (partial.isNotBlank()) {
                post { callback?.onPartialResult(partial) }
            }
        }

        val headerStatus = header?.optInt("status", -1) ?: -1
        val resultStatus = resultObject?.optInt("status", -1) ?: -1
        if (headerStatus == 2 || resultStatus == 2) {
            val finalText = currentResultText()
            post {
                if (finalText.isBlank()) {
                    callback?.onError("没有听清，可以再试一次")
                } else {
                    callback?.onFinalResult(finalText)
                }
            }
            webSocket?.close(1000, "complete")
        }
    }

    private fun decodeResultObject(response: JSONObject): JSONObject? {
        val textBase64 = response
            .optJSONObject("payload")
            ?.optJSONObject("result")
            ?.optString("text")
            .orEmpty()
        if (textBase64.isBlank()) return null

        return try {
            val decoded = Base64.decode(textBase64, Base64.NO_WRAP).toString(Charsets.UTF_8)
            JSONObject(decoded)
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeResult(result: JSONObject) {
        val piece = parseWords(result)
        if (piece.isBlank()) return

        val sn = result.optInt("sn", ++fallbackSn)
        if (result.optString("pgs") == "rpl") {
            val range = result.optJSONArray("rg")
            val start = range?.optInt(0, sn) ?: sn
            val end = range?.optInt(1, sn) ?: sn
            for (index in start..end) {
                resultPieces.remove(index)
            }
        }
        resultPieces[sn] = piece
    }

    private fun parseWords(result: JSONObject): String {
        val words = result.optJSONArray("ws") ?: return ""
        val builder = StringBuilder()
        for (i in 0 until words.length()) {
            val cw = words.optJSONObject(i)?.optJSONArray("cw") ?: continue
            builder.append(cw.optJSONObject(0)?.optString("w").orEmpty())
        }
        return builder.toString()
    }

    private fun currentResultText(): String {
        return resultPieces.toSortedMap().values.joinToString("").trim()
    }

    private fun createAuthUrl(rawUrl: String): String {
        val cleanedUrl = rawUrl.trim().trimEnd('?')
        val uri = URI(cleanedUrl)
        val host = uri.host
        val path = if (uri.rawPath.isNullOrBlank()) "/" else uri.rawPath
        val date = gmtDate()
        val signatureOrigin = "host: $host\ndate: $date\nGET $path HTTP/1.1"
        Log.d(TAG, "Xunfei auth host=$host requestLine=GET $path HTTP/1.1")
        val signature = hmacSha256(signatureOrigin, BuildConfig.XFYUN_API_SECRET)
        val authorizationOrigin =
            "api_key=\"${BuildConfig.XFYUN_API_KEY}\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        return "$cleanedUrl?authorization=${urlEncode(authorization)}&date=${urlEncode(date)}&host=${urlEncode(host)}"
    }

    private fun logSafeConfig() {
        Log.d(TAG, "XFYUN_ASR_URL=${BuildConfig.XFYUN_ASR_URL}")
        Log.d(TAG, "APP_ID empty=${BuildConfig.XFYUN_APP_ID.isBlank()}")
        Log.d(TAG, "API_KEY empty=${BuildConfig.XFYUN_API_KEY.isBlank()}")
        Log.d(TAG, "API_SECRET empty=${BuildConfig.XFYUN_API_SECRET.isBlank()}")
    }

    private fun mapConnectionFailure(t: Throwable, response: Response?): String {
        val code = response?.code
        return when {
            code == 401 || code == 403 -> "语音服务鉴权失败，请检查 APPID、APIKey 或 APISecret"
            t is UnknownHostException || t is SocketTimeoutException -> "网络连接失败，请检查网络或代理"
            else -> "语音服务连接失败"
        }
    }

    private fun isAuthError(code: Int): Boolean {
        return code == 401 ||
            code == 403 ||
            code == 10005 ||
            code == 10006 ||
            code == 10105 ||
            code == 10106
    }

    private fun hmacSha256(text: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun gmtDate(): String {
        return SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date())
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }
}
