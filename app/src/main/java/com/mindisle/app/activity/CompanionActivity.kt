package com.mindisle.app.activity

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mindisle.app.R
import com.mindisle.app.assessment.AssessmentController
import com.mindisle.app.background.BackgroundImportResult
import com.mindisle.app.background.CompanionBackgroundManager
import com.mindisle.app.ai.AiAction
import com.mindisle.app.ai.AiActionExecutor
import com.mindisle.app.ai.AiActionResult
import com.mindisle.app.ai.AiChatResponse
import com.mindisle.app.ai.AiChatClient
import com.mindisle.app.ai.AiRole
import com.mindisle.app.ai.AiRoleManager
import com.mindisle.app.ai.ApiService
import com.mindisle.app.ai.ChatHistoryItem
import com.mindisle.app.ai.ChatStorage
import com.mindisle.app.chat.ChatRepository
import com.mindisle.app.chat.LocalReplyEngine
import com.mindisle.app.floating.FloatingInteractionAccessibilityService
import com.mindisle.app.floating.FloatingInteractionStatusStore
import com.mindisle.app.floating.FloatingLive2DBridge
import com.mindisle.app.floating.FloatingModelVariantRepository
import com.mindisle.app.floating.FloatingSettingsManager
import com.mindisle.app.floating.Live2DFloatingService
import com.mindisle.app.live2d.Live2DManager
import com.mindisle.app.live2d.Live2DModels
import com.mindisle.app.live2d.Live2DState
import com.mindisle.app.manager.CrisisKeywordManager
import com.mindisle.app.music.MusicDrawerController
import com.mindisle.app.music.MusicMetadataUtils
import com.mindisle.app.music.MusicPlayMode
import com.mindisle.app.music.MusicPlayerManager
import com.mindisle.app.music.MusicRepository
import com.mindisle.app.pet.PetSettingManager
import com.mindisle.app.profile.ProfileQrView
import com.mindisle.app.profile.ProfileStatusCatalog
import com.mindisle.app.profile.ProfileStatusStore
import com.mindisle.app.profile.StatusItem
import com.mindisle.app.profile.UserProfile
import com.mindisle.app.profile.UserProfileStore
import com.mindisle.app.relax.RelaxController
import com.mindisle.app.ui.companion.CompanionSideMenuController
import com.mindisle.app.ui.companion.CompanionViewModel
import com.mindisle.app.ui.companion.FloatingLightSpotView
import com.mindisle.app.ui.companion.MessageList

import com.mindisle.app.utils.StatusBarUtils
import com.mindisle.app.utils.ToastUtils
import com.mindisle.app.voice.VoiceRecognitionCallback
import com.mindisle.app.voice.VoiceRecognizerManager
import com.mindisle.app.voice.XunfeiChineseAsrManager

private data class XingyueLive2DTestItem(
    val label: String,
    val file: String
)

private enum class CompanionOverlayMode {
    BACKGROUND_PICKER,
    ROLE_PICKER,
    DIALOG,
    BOTTOM_SHEET,
    PLUS_PANEL
}

class CompanionActivity : AppCompatActivity(), AiActionExecutor.Host {
    private val viewModel = CompanionViewModel()
    private val repository = ChatRepository()
    private val apiService: ApiService = AiChatClient()
    private val conversationHistory = mutableListOf<ChatHistoryItem>()
    private lateinit var aiRoleManager: AiRoleManager
    private lateinit var chatStorage: ChatStorage
    private lateinit var currentAiRole: AiRole
    private var currentRoleId: String = ""
    private var currentModelId: String = ""
    private lateinit var live2DManager: Live2DManager
    private lateinit var sideMenuController: CompanionSideMenuController
    private lateinit var petSettingManager: PetSettingManager
    private lateinit var floatingSettingsManager: FloatingSettingsManager
    private lateinit var companionBackgroundManager: CompanionBackgroundManager
    private lateinit var profileStatusStore: ProfileStatusStore
    private lateinit var assessmentController: AssessmentController
    private lateinit var relaxController: RelaxController
    private lateinit var musicRepository: MusicRepository
    private lateinit var musicPlayerManager: MusicPlayerManager
    private lateinit var musicDrawerController: MusicDrawerController
    private lateinit var aiActionExecutor: AiActionExecutor
    private lateinit var companionBackgroundImage: ImageView
    private lateinit var floatingLightSpotView: FloatingLightSpotView
    private lateinit var messageList: MessageList
    private lateinit var live2DWebView: WebView
    private lateinit var input: EditText
    private val voiceRecognizer: VoiceRecognizerManager = XunfeiChineseAsrManager()
    private var pendingStartFloating = false
    private var pendingFloatingVariant: String? = null
    private var defaultInputHint: CharSequence? = null
    private var xingyueDisplayPreset: String = "FULL_BODY"
    private var lastLive2DLookAtMs: Long = 0L
    private var live2DDownX: Float = 0f
    private var live2DDownY: Float = 0f
    private var live2DDownTime: Long = 0L
    private var aiRequestInProgress = false
    private var currentBottomTabId: Int = R.id.nav_companion_tab
    private var companionImeVisible = false
    private var musicEdgeTracking = false
    private var musicEdgeStartX = 0f
    private var musicEdgeStartY = 0f
    private val companionOverlayCounts = mutableMapOf<CompanionOverlayMode, Int>()
    private val xingyueExpressionItems = listOf(
        XingyueLive2DTestItem("比心", "比心.exp3.json"),
        XingyueLive2DTestItem("生气", "生气.exp3.json"),
        XingyueLive2DTestItem("脸红", "脸红.exp3.json"),
        XingyueLive2DTestItem("脸黑", "脸黑.exp3.json"),
        XingyueLive2DTestItem("哭哭", "哭哭.exp3.json"),
        XingyueLive2DTestItem("白眼", "白眼.exp3.json"),
        XingyueLive2DTestItem("爱心眼", "爱心眼.exp3.json"),
        XingyueLive2DTestItem("无语", "无语.exp3.json"),
        XingyueLive2DTestItem("祈祷", "祈祷.exp3.json"),
        XingyueLive2DTestItem("星星眼", "星星眼.exp3.json"),
        XingyueLive2DTestItem("吐舌", "吐舌.exp3.json"),
        XingyueLive2DTestItem("眩晕", "眩晕.exp3.json"),
        XingyueLive2DTestItem("黑眼", "黑眼.exp3.json"),
        XingyueLive2DTestItem("珍珠", "珍珠.exp3.json"),
        XingyueLive2DTestItem("月亮", "月亮.exp3.json"),
        XingyueLive2DTestItem("麦克风", "麦克风.exp3.json"),
        XingyueLive2DTestItem("游戏机", "游戏机.exp3.json")
    )
    private val xingyueMotionItems = listOf(
        XingyueLive2DTestItem("默认动作", "Scene1.motion3.json")
    )

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                musicEdgeTracking =
                    event.rawX <= 18f * density &&
                        (!::musicDrawerController.isInitialized || !musicDrawerController.isOpen)
                musicEdgeStartX = event.rawX
                musicEdgeStartY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                if (musicEdgeTracking) {
                    val dx = event.rawX - musicEdgeStartX
                    val dy = event.rawY - musicEdgeStartY
                    if (dx > 56f * density && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        musicEdgeTracking = false
                        if (::musicDrawerController.isInitialized) {
                            openMusicDrawer()
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> musicEdgeTracking = false
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            val focusedView = currentFocus
            if (focusedView is EditText && isTouchOutsideView(focusedView, event)) {
                hideKeyboardAndClearFocus()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtils.applyLightStatusBar(this)
        setContentView(R.layout.activity_companion)
        companionBackgroundImage = findViewById(R.id.companion_background_image)
        companionBackgroundManager = CompanionBackgroundManager(this)
        profileStatusStore = ProfileStatusStore(this)
        companionBackgroundManager.applySavedBackground(companionBackgroundImage)
        setupCompanionStageEffects()
        petSettingManager = PetSettingManager(this)
        floatingSettingsManager = FloatingSettingsManager(this)
        aiRoleManager = AiRoleManager(this)
        chatStorage = ChatStorage(this)
        currentAiRole = aiRoleManager.getSelectedRole(
            Live2DModels.getSelectedModel(this).id
        )
        updateCurrentRoleState(currentAiRole)
        bindLive2DStage()
        if (live2DManager.currentModel.id != currentAiRole.modelId) {
            val switchedModel = live2DManager.switchModel(currentAiRole.modelId)
            if (switchedModel == null) {
                aiRoleManager.findByModelId(live2DManager.currentModel.id)?.let {
                    currentAiRole = aiRoleManager.saveSelectedRole(it.roleId) ?: it
                    updateCurrentRoleState(currentAiRole)
                }
            }
        }
        bindChatPanel()
        bindRoleSelector()
        bindSideMenu()
        bindMusicDrawer()
        bindProfilePage()
        assessmentController = AssessmentController(this) {
            selectBottomTab(R.id.nav_companion_tab)
        }
        assessmentController.bind()
        relaxController = RelaxController(this) {
            if (::musicPlayerManager.isInitialized && musicPlayerManager.isPlaying) {
                musicPlayerManager.pause()
            }
        }
        relaxController.bind()
        bindBottomNavigation()
        aiActionExecutor = AiActionExecutor(this)
        setupFloatingInputBar()
        voiceRecognizer.init(this)
    }

    override fun onResume() {
        super.onResume()
        if (::floatingLightSpotView.isInitialized) {
            floatingLightSpotView.startAnimation()
        }
        if (pendingStartFloating) {
            val requestedVariant = pendingFloatingVariant
            pendingStartFloating = false
            pendingFloatingVariant = null
            if (hasOverlayPermission()) {
                startFloatingLive2D(requestedVariant)
            } else {
                findViewById<SwitchCompat>(R.id.floating_pet_switch).isChecked = false
                ToastUtils.show(this, "需要允许显示在其他应用上层后，才能开启桌面悬浮")
            }
        }
        updateFloatingInteractionStatus()
        if (::musicDrawerController.isInitialized) {
            musicDrawerController.onHostResumed()
        }
    }

    override fun onPause() {
        if (::floatingLightSpotView.isInitialized) {
            floatingLightSpotView.stopAnimation()
        }
        if (::relaxController.isInitialized) {
            relaxController.onHidden()
        }
        if (::musicDrawerController.isInitialized) {
            musicDrawerController.onHostPaused()
        }
        super.onPause()
    }

    override fun onDestroy() {
        saveCurrentRoleHistory()
        if (::live2DWebView.isInitialized) {
            live2DWebView.destroy()
        }
        apiService.cancelAll()
        voiceRecognizer.release()
        if (::musicDrawerController.isInitialized) {
            musicDrawerController.release()
        }
        if (::relaxController.isInitialized) {
            relaxController.release()
        }
        super.onDestroy()
    }

    private fun bindLive2DStage() {
        live2DWebView = findViewById(R.id.live2d_web_view)
        val stateText = findViewById<TextView>(R.id.live2d_state_text).apply { visibility = View.GONE }
        live2DManager = Live2DManager(
            live2DWebView,
            stateText,
            findViewById(R.id.character_bubble_text)
        )
        live2DWebView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    live2DDownX = event.x
                    live2DDownY = event.y
                    live2DDownTime = System.currentTimeMillis()
                    sendLive2DLookAt(view, event, force = true)
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    sendLive2DLookAt(view, event, force = false)
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - live2DDownX
                    val dy = event.y - live2DDownY
                    val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val duration = System.currentTimeMillis() - live2DDownTime
                    val isTap = distance < dp(12).toFloat() && duration < 250L

                    if (
                        live2DManager.currentModel.runtimeVersion == "cubism5" &&
                        !live2DManager.isModelLoaded()
                    ) {
                        ToastUtils.show(this, "星月水母模型加载失败，请检查 Cubism 5 运行库或模型资源。")
                    } else if (isTap) {
                        if (
                            live2DManager.currentModel.runtimeVersion == "cubism5" &&
                            view.width > 0 &&
                            view.height > 0
                        ) {
                            val x = event.x / view.width * 2f - 1f
                            val y = 1f - event.y / view.height * 2f
                            live2DManager.playTapReaction(x, y, "app")
                        }
                        live2DManager.touchCharacter()
                    }
                    live2DManager.resetLookAt()
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> {
                    live2DManager.resetLookAt()
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        live2DWebView.setOnLongClickListener {
            sideMenuController.open()
            true
        }
        findViewById<android.view.View>(R.id.live2d_stage).setOnLongClickListener {
            sideMenuController.open()
            true
        }
    }

    private fun sendLive2DLookAt(view: View, event: MotionEvent, force: Boolean) {
        if (
            view.width <= 0 ||
            view.height <= 0 ||
            live2DManager.currentModel.runtimeVersion != "cubism5"
        ) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastLive2DLookAtMs < 32L) {
            return
        }
        lastLive2DLookAtMs = now
        val x = event.x / view.width * 2f - 1f
        val y = 1f - event.y / view.height * 2f
        live2DManager.lookAt(x, y, "app")
    }

    private fun bindChatPanel() {
        messageList = findViewById(R.id.companion_message_list)
        messageList.setAssistantName(currentAiRole.displayName)
        loadCurrentRoleHistory()
        input = findViewById(R.id.companion_input)
        defaultInputHint = input.hint
        input.isEnabled = true
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.isCursorVisible = true

        // Tap the capsule or the EditText → show keyboard
        val chatInputBar = findViewById<View>(R.id.chat_input_bar)
        chatInputBar.setOnClickListener { focusInputAndShowKeyboard() }
        input.setOnClickListener { focusInputAndShowKeyboard() }
        input.setOnFocusChangeListener { _, hasFocus ->
            chatInputBar.setBackgroundResource(
                if (hasFocus) R.drawable.bg_floating_input_focused
                else R.drawable.bg_floating_input
            )
            chatInputBar.animate().cancel()
            chatInputBar.alpha = if (hasFocus) 0.92f else 1f
            chatInputBar.animate().alpha(1f).setDuration(180L).start()
            if (hasFocus) focusInputAndShowKeyboard()
        }

        // Icon switching: mic (empty) ↔ send (has text)
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateRightActionIcon() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Right icon: mic → voice, send → unified send, keyboard → text
        findViewById<ImageView>(R.id.btn_right_action).setOnClickListener {
            if (isVoiceMode) {
                voiceRecognizer.stopListening()
            } else if (input.text.isNullOrBlank() && pendingImageUri == null) {
                launchVoiceRecognition()
            } else {
                Log.d("MindIsleAi", "点击发送按钮")
                handleSend()
            }
        }

        // Preview close button
        findViewById<ImageView>(R.id.preview_close).setOnClickListener {
            removePendingImage()
        }

        // Plus button — show lightweight function panel
        findViewById<ImageView>(R.id.btn_plus).setOnClickListener {
            showPlusPanel()
        }

        // Quick action chips
        findViewById<View>(R.id.chip_focus).setOnClickListener { handleUserText("开始专注") }
        findViewById<View>(R.id.chip_chat).setOnClickListener { handleUserText("陪我聊聊") }
        findViewById<View>(R.id.chip_reminder).setOnClickListener { handleUserText("提醒我休息") }
        findViewById<View>(R.id.chip_mood).setOnClickListener { handleUserText("记录心情") }
        findViewById<View>(R.id.chip_more).setOnClickListener {
            ToastUtils.show(this, "更多功能即将上线")
        }
        bindCompanionPressFeedback(
            R.id.chip_focus,
            R.id.chip_chat,
            R.id.chip_reminder,
            R.id.chip_mood,
            R.id.chip_more,
            R.id.btn_plus,
            R.id.btn_right_action
        )

        // IME send action
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                handleUserText(input.text.toString().trim())
                true
            } else false
        }
    }

    private var isVoiceMode = false
    private var pendingImageUri: String? = null

    // --- Photo picker launcher ---
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) showImagePreview(uri)
    }

    private fun setupCompanionStageEffects() {
        floatingLightSpotView = findViewById(R.id.floating_light_spot_view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurRadius = 3f * resources.displayMetrics.density
            companionBackgroundImage.setRenderEffect(
                RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
            )
        }
    }

    private val pickBackgroundLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleBackgroundImport(uri)
        }
    }

    private val pickProfileAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val profile = UserProfileStore.load(this)
            profile.avatarUri = uri.toString()
            UserProfileStore.save(this, profile)
            refreshProfileViews(profile)
        }
    }

    private val pickMusicLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importMusicUris(uris)
        }
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startXunfeiVoiceRecognition()
        } else {
            ToastUtils.show(this, "需要麦克风权限才能使用语音输入")
        }
    }
    private fun updateRightActionIcon() {
        val btn = findViewById<ImageView>(R.id.btn_right_action)
        if (isVoiceMode) return
        if (input.text.isNullOrBlank() && pendingImageUri == null) {
            btn.setImageResource(R.drawable.ic_mic)
            btn.setBackgroundResource(R.drawable.bg_icon_circle_mic)
        } else {
            btn.setImageResource(R.drawable.ic_send)
            btn.setBackgroundResource(R.drawable.bg_icon_circle_send)
        }
    }

    private fun enterVoiceMode() {
        isVoiceMode = true
        input.visibility = View.VISIBLE
        input.hint = "正在听你说……"
        findViewById<View>(R.id.voice_mode_overlay).visibility = View.GONE
        val btn = findViewById<ImageView>(R.id.btn_right_action)
        btn.setImageResource(R.drawable.ic_close)
        btn.setBackgroundResource(R.drawable.bg_icon_circle_plus)
        hideKeyboardAndClearFocus()
    }

    private fun exitVoiceMode() {
        isVoiceMode = false
        input.visibility = View.VISIBLE
        input.hint = defaultInputHint
        findViewById<View>(R.id.voice_mode_overlay).visibility = View.GONE
        updateRightActionIcon()
    }

    private fun showPlusPanel() {
        val items = arrayOf("图片", "语音输入", "拍照", "文件", "心情记录")
        showTrackedDialog(
            MaterialAlertDialogBuilder(this)
            .setTitle("添加内容")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    1 -> launchVoiceRecognition()
                    2 -> ToastUtils.show(this, "拍照功能即将上线")
                    3 -> ToastUtils.show(this, "文件功能即将上线")
                    4 -> handleUserText("记录心情")
                }
            },
            CompanionOverlayMode.PLUS_PANEL
        )
    }
    private fun launchVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startXunfeiVoiceRecognition()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startXunfeiVoiceRecognition() {
        enterVoiceMode()
        input.setText("")
        voiceRecognizer.startListening(object : VoiceRecognitionCallback {
            override fun onStart() {
                input.hint = "正在听你说……"
            }

            override fun onPartialResult(text: String) {
                fillVoiceText(text)
            }

            override fun onFinalResult(text: String) {
                fillVoiceText(text)
            }

            override fun onError(message: String) {
                ToastUtils.show(this@CompanionActivity, message)
            }

            override fun onEnd() {
                exitVoiceMode()
            }
        })
    }

    private fun fillVoiceText(text: String) {
        if (text.isBlank()) return
        input.setText(text)
        input.setSelection(input.text?.length ?: 0)
        updateRightActionIcon()
    }
    private fun showImagePreview(uri: Uri) {
        val uriString = uri.toString()
        try {
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { }

        pendingImageUri = uriString
        val previewBar = findViewById<View>(R.id.image_preview_inner)
        val previewImage = findViewById<ImageView>(R.id.preview_image)
        previewImage.setImageURI(uri)
        previewBar.visibility = View.VISIBLE
        updateCompanionBottomLayerVisibility()
        updateRightActionIcon()
    }

    private fun removePendingImage() {
        pendingImageUri = null
        findViewById<View>(R.id.image_preview_inner).visibility = View.GONE
        updateCompanionBottomLayerVisibility()
        updateRightActionIcon()
    }

    private fun handleSend() {
        val text = input.text.toString().trim()
        val imageUri = pendingImageUri

        if (text.isBlank() && imageUri == null) return

        // Text only → delegate to existing logic
        if (imageUri == null) {
            handleUserText(text)
            return
        }

        // Image (with or without text)
        val displayText = text.ifBlank { "" }
        if (imageUri != null && text.isNotBlank()) {
            repository.add("我", text)
            messageList.addUserImageWithText(imageUri, text)
        } else {
            repository.add("我", "[图片]")
            messageList.addUserImage(imageUri)
        }

        // Clear state
        input.setText("")
        pendingImageUri = null
        findViewById<View>(R.id.image_preview_inner).visibility = View.GONE
        updateCompanionBottomLayerVisibility()
        updateRightActionIcon()

        live2DManager.setEmotionState("talking")
        scrollMessagesToBottom()

        val reply = LocalReplyEngine.reply(if (text.isNotBlank()) text else "图片")
        live2DManager.setTalkState(true)
        appendAssistantMessage(reply)
        live2DWebView.postDelayed({ live2DManager.setTalkState(false) }, 1400L)

        val state = when {
            text.contains("专注") || text.contains("学习") -> Live2DState.STUDY
            text.contains("休息") -> Live2DState.REST
            text.contains("累") || text.contains("困") -> Live2DState.SLEEPY
            text.contains("聊") -> Live2DState.LISTENING
            else -> Live2DState.TALKING
        }
        live2DManager.setState(state)

        if (text.isNotBlank() && CrisisKeywordManager.containsCrisisKeyword(text)) {
            startActivity(Intent(this, CrisisHelpActivity::class.java))
        }
    }

    private fun focusInputAndShowKeyboard() {
        input.requestFocus()
        input.setSelection(input.text?.length ?: 0)
        input.post {
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            ViewCompat.requestApplyInsets(findViewById(R.id.companion_root))
        }
    }

    private fun hideKeyboardAndClearFocus() {
        val focusedView = currentFocus ?: return
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
        focusedView.clearFocus()
        ViewCompat.requestApplyInsets(findViewById(R.id.companion_root))
    }

    private fun isTouchOutsideView(view: View, event: MotionEvent): Boolean {
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)
        return !bounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun setCompanionOverlayOpen(mode: CompanionOverlayMode, open: Boolean) {
        val currentCount = companionOverlayCounts[mode] ?: 0
        val nextCount = if (open) currentCount + 1 else (currentCount - 1).coerceAtLeast(0)
        if (nextCount == 0) {
            companionOverlayCounts.remove(mode)
        } else {
            companionOverlayCounts[mode] = nextCount
        }
        updateCompanionBottomLayerVisibility()
    }

    private fun updateCompanionBottomLayerVisibility() {
        val isCompanionPage = currentBottomTabId == R.id.nav_companion_tab
        val hasOverlay = companionOverlayCounts.values.any { it > 0 }
        val shouldShow = isCompanionPage && !hasOverlay
        val composeArea = findViewById<View>(R.id.chat_compose_area)
        val quickActions = findViewById<View>(R.id.quick_actions)

        composeArea.visibility = if (shouldShow) View.VISIBLE else View.GONE
        quickActions.visibility = if (
            shouldShow && !companionImeVisible && pendingImageUri == null
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // Character feedback is page-scoped too; never leave it over an overlay or another tab.
        if (!shouldShow) {
            findViewById<View>(R.id.character_bubble_text).visibility = View.GONE
        }
        if (shouldShow && (!::sideMenuController.isInitialized || !sideMenuController.isOpen)) {
            composeArea.bringToFront()
        }
    }

    private fun showTrackedDialog(
        builder: MaterialAlertDialogBuilder,
        mode: CompanionOverlayMode = CompanionOverlayMode.DIALOG
    ) {
        val dialog = builder.create()
        setCompanionOverlayOpen(mode, true)
        dialog.setOnDismissListener {
            setCompanionOverlayOpen(mode, false)
        }
        dialog.show()
    }

    private fun setupFloatingInputBar() {
        val root = findViewById<FrameLayout>(R.id.companion_root)
        val composeArea = findViewById<View>(R.id.chat_compose_area)
        val bottomNav = findViewById<View>(R.id.fixed_bottom_nav)

        // Detach the entire compose area (quick-chips + input bar + voice /
        // send buttons) from the weight-based chat panel and re-attach as a
        // direct child of companion_root so the whole block floats above the
        // keyboard as a single unit.
        (composeArea.parent as? ViewGroup)?.removeView(composeArea)
        root.addView(composeArea, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        })
        composeArea.bringToFront()

        // Add bottom padding to the message list so the last message
        // remains visible above the floating compose area.
        messageList.setPadding(
            messageList.paddingLeft,
            messageList.paddingTop,
            messageList.paddingRight,
            messageList.paddingBottom + dp(100)
        )

        // Use WindowInsetsCompat to detect IME height at runtime — never
        // hardcode keyboard size. Only the compose area moves; nothing else
        // is touched.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            companionImeVisible = imeVisible
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val isCompanionPage = currentBottomTabId == R.id.nav_companion_tab

            val barParams = composeArea.layoutParams as FrameLayout.LayoutParams
            if (imeVisible && isCompanionPage) {
                bottomNav.visibility = View.GONE
                barParams.bottomMargin = imeBottom + dp(8)
            } else {
                bottomNav.visibility = View.VISIBLE
                barParams.bottomMargin = bottomNav.height.coerceAtLeast(dp(76)) + dp(16) + navBottom
            }
            composeArea.layoutParams = barParams
            updateCompanionBottomLayerVisibility()

            if (imeVisible && isCompanionPage) {
                scrollMessagesToBottom(delayMs = 200L)
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun handleUserText(text: String) {
        Log.d("MindIsleAi", "CompanionActivity handleUserText called")
        if (text.isBlank()) {
            ToastUtils.show(this, "可以先对小屿说一句话")
            return
        }
        if (aiRequestInProgress) {
            ToastUtils.show(this, "心屿正在回复，请稍等一下")
            return
        }

        val requestRole = ensureActiveAiRole()
        val requestRoleId = requestRole.roleId
        val requestModelId = requestRole.modelId
        val requestHistory = ArrayList(conversationHistory)
        repository.add("我", text)
        messageList.addUserMessage(text)
        conversationHistory.add(ChatHistoryItem("user", text))
        chatStorage.save(requestModelId, conversationHistory)
        input.setText("")
        scrollMessagesToBottom()

        val thinkingPosition = messageList.addAssistantMessage("心屿正在思考...")
        setAiRequestInProgress(true)
        scrollMessagesToBottom()

        Log.d("MindIsleAi", "currentRoleId=$requestRoleId")
        Log.d("MindIsleAi", "currentModelId=$requestModelId")
        Log.d("MindIsleAi", "historyKey=${chatStorage.historyKey(requestModelId)}")
        Log.d("MindIsleAi", "history count=${requestHistory.size}")
        Log.d("MindIsleAi", "request modelId=$requestModelId")
        Log.d("MindIsleAi", "CompanionActivity calling apiService.sendMessage")
        apiService.sendMessage(
            text,
            requestRoleId,
            requestModelId,
            requestHistory,
            object : ApiService.Callback {
            override fun onSuccess(response: AiChatResponse) {
                Log.d("MindIsleAi", "CompanionActivity request success")
                if (isFinishing || isDestroyed) return
                if (requestModelId != currentModelId || requestRoleId != currentRoleId) {
                    Log.d(
                        "MindIsleAi",
                        "ignored stale response roleId=$requestRoleId modelId=$requestModelId"
                    )
                    return
                }
                Log.d(
                    "MindIsleAi",
                    "response role=${response.role} emotion=${response.emotion} " +
                        "expression=${response.expression} motion=${response.motion} " +
                        "actions=${response.actions.size} needConfirm=${response.isNeedConfirm}"
                )
                conversationHistory.add(ChatHistoryItem("assistant", response.reply))
                chatStorage.save(requestModelId, conversationHistory)
                completeAiReply(thinkingPosition, response.reply)
                applyStructuredLive2DResponse(response)
                aiActionExecutor.execute(response.actions)
            }

            override fun onFailure() {
                Log.e("MindIsleAi", "CompanionActivity request failure")
                if (isFinishing || isDestroyed) return
                if (requestModelId != currentModelId || requestRoleId != currentRoleId) return
                completeAiReply(thinkingPosition, "网络异常，请稍后重试")
            }
        })
    }

    private fun ensureActiveAiRole(): AiRole {
        if (
            ::currentAiRole.isInitialized &&
            currentRoleId.isNotBlank() &&
            currentModelId.isNotBlank()
        ) {
            return currentAiRole
        }

        val preferredModelId = if (::live2DManager.isInitialized) {
            live2DManager.currentModel.id
        } else {
            AiRoleManager.DEFAULT_ROLE_ID
        }
        currentAiRole = aiRoleManager.getSelectedRole(preferredModelId)
        updateCurrentRoleState(currentAiRole)
        Log.w(
            "MindIsleAi",
            "restored request roleId=$currentRoleId modelId=$currentModelId"
        )
        return currentAiRole
    }

    private fun completeAiReply(thinkingPosition: Int, reply: String) {
        repository.add("心屿", reply)
        viewModel.latestReply = reply
        findViewById<TextView>(R.id.exchange_latest_text).text = "心屿：$reply"
        messageList.replaceAssistantMessage(thinkingPosition, reply)
        setAiRequestInProgress(false)
        scrollMessagesToBottom()
    }

    private fun applyStructuredLive2DResponse(response: AiChatResponse) {
        Log.d("MindIsleAi", "response emotion=${response.emotion}")
        Log.d("MindIsleAi", "response expression=${response.expression}")
        Log.d("MindIsleAi", "response motion=${response.motion}")
        if (!::live2DManager.isInitialized || !live2DManager.isModelLoaded()) {
            Log.w("MindIsleAi", "live2d response ignored because model is not loaded")
            return
        }
        runCatching {
            live2DManager.setExpression(response.expression)
            live2DManager.playMotion(response.motion)
        }.onFailure { error ->
            Log.w("MindIsleAi", "live2d response failed: ${error.message}")
        }
    }

    private fun setAiRequestInProgress(inProgress: Boolean) {
        aiRequestInProgress = inProgress
        findViewById<ImageView>(R.id.btn_right_action).apply {
            isEnabled = !inProgress
            alpha = if (inProgress) 0.55f else 1f
        }
        scrollMessagesToBottom()
    }

    private fun bindRoleSelector() {
        findViewById<TextView>(R.id.companion_title_text).apply {
            isClickable = true
            isFocusable = true
            bringToFront()
            setOnClickListener { showAiRoleDialog() }
        }
        updateRoleHeader()
    }

    private fun showAiRoleDialog() {
        val roles = aiRoleManager.getEnabledRoles()
            .filter { role -> live2DManager.availableModels.any { it.id == role.modelId } }
        val selectedIndex = roles.indexOfFirst { it.roleId == currentAiRole.roleId }
        showTrackedDialog(
            MaterialAlertDialogBuilder(this)
            .setTitle("选择陪伴角色")
            .setSingleChoiceItems(
                roles.map { it.displayName }.toTypedArray(),
                selectedIndex
            ) { dialog, which ->
                selectAiRole(roles[which])
                dialog.dismiss()
            }
            .setNeutralButton("清空聊天记录") { _, _ -> confirmClearChatHistory() }
            .setNegativeButton("取消", null),
            CompanionOverlayMode.ROLE_PICKER
        )
    }

    private fun selectAiRole(role: AiRole) {
        if (role.roleId == currentRoleId && role.modelId == currentModelId) return
        saveCurrentRoleHistory()
        cancelPendingAiRequestForRoleSwitch()
        val model = live2DManager.switchModel(role.modelId)
        if (model == null) {
            ToastUtils.show(this, "角色模型不可用，请检查模型配置")
            return
        }
        currentAiRole = aiRoleManager.saveSelectedRole(role.roleId) ?: role
        updateCurrentRoleState(currentAiRole)
        messageList.setAssistantName(currentAiRole.displayName)
        loadCurrentRoleHistory()
        updateRoleHeader()
        updateCurrentModelName()
        refreshProfileHome()
        notifyFloatingService()
        Log.d("MindIsleAi", "switched roleId=$currentRoleId modelId=$currentModelId")
        ToastUtils.show(this, "已切换为${currentAiRole.displayName}")
    }

    private fun updateCurrentRoleState(role: AiRole) {
        currentRoleId = role.roleId
        currentModelId = role.modelId
    }

    private fun saveCurrentRoleHistory() {
        if (currentModelId.isNotBlank()) {
            chatStorage.save(currentModelId, conversationHistory)
        }
    }

    private fun loadCurrentRoleHistory() {
        conversationHistory.clear()
        messageList.clearMessages()
        val loadedHistory = chatStorage.load(currentModelId)
        conversationHistory.addAll(loadedHistory)
        loadedHistory.forEach { message ->
            messageList.addStoredMessage(message.role, message.content)
        }
        Log.d(
            "MindIsleAi",
            "loaded history key=${chatStorage.historyKey(currentModelId)} count=${loadedHistory.size}"
        )
        scrollMessagesToBottom()
    }

    private fun cancelPendingAiRequestForRoleSwitch() {
        if (!aiRequestInProgress) return
        apiService.cancelAll()
        setAiRequestInProgress(false)
        Log.d("MindIsleAi", "cancelled pending request before role switch")
    }

    private fun updateRoleHeader() {
        findViewById<TextView>(R.id.companion_title_text).text =
            currentAiRole.displayName
    }

    private fun confirmClearChatHistory() {
        showTrackedDialog(
            MaterialAlertDialogBuilder(this)
            .setTitle("清空聊天记录")
            .setMessage("将删除当前设备保存的最近聊天记录。")
            .setPositiveButton("清空") { _, _ ->
                conversationHistory.clear()
                chatStorage.clear(currentModelId)
                messageList.clearMessages()
                ToastUtils.show(this, "聊天记录已清空")
            }
            .setNegativeButton("取消", null)
        )
    }

    private fun appendAssistantMessage(text: String) {
        repository.add("心屿", text)
        viewModel.latestReply = text
        findViewById<TextView>(R.id.exchange_latest_text).text = "小屿：$text"
        if (::messageList.isInitialized) {
            messageList.addAssistantMessage(text)
            scrollMessagesToBottom()
        }
    }

    private fun scrollMessagesToBottom(delayMs: Long = 0L) {
        if (::messageList.isInitialized) {
            messageList.postDelayed({ messageList.scrollToBottom() }, delayMs)
        }
    }

    private fun bindSideMenu() {
        sideMenuController = CompanionSideMenuController(
            findViewById(R.id.companion_root),
            findViewById(R.id.companion_side_menu),
            findViewById(R.id.side_scrim),
            findViewById(R.id.side_menu_handle),
            onOpened = {
                hideKeyboardAndClearFocus()
                if (::musicDrawerController.isInitialized && musicDrawerController.isOpen) {
                    closeMusicDrawer()
                }
            },
            onClosed = {
                updateCompanionBottomLayerVisibility()
            }
        )
        sideMenuController.attach()
        updateCurrentModelName()

        findViewById<TextView>(R.id.menu_switch_pet).setOnClickListener { showPetSwitchDialog() }
        findViewById<TextView>(R.id.menu_change_background).setOnClickListener {
            showBackgroundPickerDialog()
        }
        bindActionButton(R.id.action_hello, Live2DState.HAPPY, "hello", "default", "我在呢。")
        bindActionButton(R.id.action_happy, Live2DState.HAPPY, "happy", "happy", "看到你来，我很开心。")
        bindActionButton(R.id.action_rest, Live2DState.REST, "rest", "default", "我们休息一下也很好。")
        findViewById<TextView>(R.id.action_default).setOnClickListener {
            live2DManager.resetModel()
            ToastUtils.show(this, "已切回默认状态")
        }
        bindActionButton(R.id.action_talking, Live2DState.TALKING, "talking", "default", "正在说话。")
        bindTextOptions(R.id.costume_group, "costume")
        bindSwitch(R.id.touch_feedback_switch, "touch_feedback", true)
        bindSwitch(R.id.talking_animation_switch, "talking_animation", true)
        bindSwitch(R.id.blink_switch, "auto_blink", true)
        bindSwitch(R.id.voice_switch, "voice_enabled", true)
        bindFloatingControls()
        bindCharacterSize()
        bindXingyueDisplayModes()
        bindXingyueTestChips()
        bindXingyueLookPresets()

        findViewById<TextView>(R.id.menu_pomodoro).setOnClickListener {
            sideMenuController.close()
            handleUserText("开始专注")
        }
        findViewById<TextView>(R.id.menu_more_settings).setOnClickListener {
            ToastUtils.show(this, "更多角色设置将在第二阶段继续完善")
        }
        bindCompanionPressFeedback(
            R.id.menu_switch_pet,
            R.id.menu_change_background,
            R.id.action_hello,
            R.id.action_happy,
            R.id.action_rest,
            R.id.action_default,
            R.id.action_talking,
            R.id.display_mode_full_body,
            R.id.display_mode_half_body,
            R.id.display_mode_chibi
        )
    }

    private fun bindMusicDrawer() {
        musicRepository = MusicRepository(this)
        musicPlayerManager = MusicPlayerManager(this, musicRepository)
        musicDrawerController = MusicDrawerController(
            activity = this,
            root = findViewById(R.id.companion_root),
            playerManager = musicPlayerManager,
            repository = musicRepository,
            onAddMusic = {
                pickMusicLauncher.launch(arrayOf("audio/*"))
            },
            onBeforeOpen = {
                hideKeyboardAndClearFocus()
                if (::sideMenuController.isInitialized && sideMenuController.isOpen) {
                    sideMenuController.close()
                }
            }
        )
        musicDrawerController.attach()
        findViewById<View>(R.id.music_open_button).setOnClickListener {
            Log.d("MindIsleMusic", "music shortcut clicked")
            openMusicDrawer()
        }
    }

    private fun openMusicDrawer() {
        if (!::musicDrawerController.isInitialized) {
            Log.e("MindIsleMusic", "openMusicDrawer called before controller initialization")
            return
        }
        musicDrawerController.open()
    }

    private fun closeMusicDrawer() {
        if (::musicDrawerController.isInitialized) {
            musicDrawerController.close()
        }
    }

    private fun toggleMusicDrawer() {
        if (!::musicDrawerController.isInitialized) return
        if (musicDrawerController.isOpen) {
            closeMusicDrawer()
        } else {
            openMusicDrawer()
        }
    }

    private fun importMusicUris(uris: List<Uri>) {
        ToastUtils.show(this, "正在读取音乐信息…")
        Thread {
            val tracks = uris.mapNotNull { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                runCatching { MusicMetadataUtils.read(this, uri) }.getOrNull()
            }
            runOnUiThread {
                if (::musicDrawerController.isInitialized) {
                    musicDrawerController.addTracks(tracks)
                }
            }
        }.start()
    }

    private fun showBackgroundPickerDialog() {
        val builtins = companionBackgroundManager.builtinBackgrounds
        val options = builtins.map { it.displayName } + listOf("从相册选择", "恢复默认")
        showTrackedDialog(
            MaterialAlertDialogBuilder(this)
            .setTitle("更换背景")
            .setItems(options.toTypedArray()) { dialog, which ->
                when {
                    which < builtins.size -> {
                        val selected = builtins[which]
                        if (companionBackgroundManager.selectBuiltin(
                                companionBackgroundImage,
                                selected.fileName
                            )
                        ) {
                            ToastUtils.show(this, "已切换为${selected.displayName}")
                            sideMenuController.close()
                        } else {
                            ToastUtils.show(this, "背景加载失败，请稍后重试")
                        }
                    }

                    which == builtins.size -> {
                        pickBackgroundLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }

                    else -> {
                        if (companionBackgroundManager.resetToDefault(
                                companionBackgroundImage
                            )
                        ) {
                            ToastUtils.show(this, "已恢复默认背景")
                            sideMenuController.close()
                        } else {
                            ToastUtils.show(this, "默认背景加载失败")
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null),
            CompanionOverlayMode.BACKGROUND_PICKER
        )
    }

    private fun handleBackgroundImport(uri: Uri) {
        when (val result = companionBackgroundManager.importFromGallery(uri)) {
            is BackgroundImportResult.Success -> {
                if (companionBackgroundManager.selectCustom(
                        companionBackgroundImage,
                        result.file
                    )
                ) {
                    ToastUtils.show(this, "背景已更换")
                    if (::sideMenuController.isInitialized) {
                        sideMenuController.close()
                    }
                } else {
                    ToastUtils.show(this, "背景加载失败，请重新选择")
                }
            }

            BackgroundImportResult.TooLarge ->
                ToastUtils.show(this, "图片过大，请选择 10MB 以下的图片")

            BackgroundImportResult.UnsupportedFormat ->
                ToastUtils.show(this, "仅支持 JPG、PNG 或 WebP 图片")

            BackgroundImportResult.Failed ->
                ToastUtils.show(this, "背景导入失败，请重新选择")
        }
    }

    private fun showPetSwitchDialog() {
        val models = live2DManager.availableModels
        val names = models.map { it.name }.toTypedArray()
        showTrackedDialog(
            MaterialAlertDialogBuilder(this)
            .setTitle("切换角色")
            .setItems(names) { _, which ->
                val selectedModel = models[which]
                val matchingRole = aiRoleManager.findByModelId(selectedModel.id)
                if (matchingRole == null) {
                    ToastUtils.show(this, "模型加载失败，请检查资源路径")
                    return@setItems
                }
                selectAiRole(matchingRole)
            }
            .setNegativeButton("取消", null),
            CompanionOverlayMode.ROLE_PICKER
        )
    }

    private fun updateCurrentModelName() {
        val model = live2DManager.currentModel
        findViewById<TextView>(R.id.menu_pet_name_text).text = "心屿 · ${model.name}"
        updateXingyueDisplayModeVisibility()
    }

    private fun bindXingyueDisplayModes() {
        findViewById<TextView>(R.id.display_mode_full_body).setOnClickListener {
            applyXingyueDisplayPreset("FULL_BODY", "全身")
        }
        findViewById<TextView>(R.id.display_mode_half_body).setOnClickListener {
            applyXingyueDisplayPreset("HALF_BODY", "半身")
        }
        findViewById<TextView>(R.id.display_mode_chibi).setOnClickListener {
            applyXingyueDisplayPreset("CHIBI", "缩小")
        }
        updateXingyueDisplayModeVisibility()
        updateDisplayPresetButtons()
    }

    private fun applyXingyueDisplayPreset(preset: String, label: String) {
        if (live2DManager.currentModel.id != "xingyue_shuimu") {
            ToastUtils.show(this, "显示模式仅用于星月水母")
            return
        }
        xingyueDisplayPreset = preset
        live2DManager.setDisplayPreset(preset)
        updateDisplayPresetButtons()
        ToastUtils.show(this, "已切换为${label}模式")
    }

    private fun updateXingyueDisplayModeVisibility() {
        val visible = live2DManager.currentModel.id == "xingyue_shuimu"
        val visibility = if (visible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.xingyue_display_mode_title).visibility = visibility
        findViewById<View>(R.id.xingyue_display_mode_group).visibility = visibility
        findViewById<View>(R.id.xingyue_expression_title).visibility = visibility
        findViewById<View>(R.id.xingyue_expression_scroll).visibility = visibility
        findViewById<View>(R.id.xingyue_motion_title).visibility = visibility
        findViewById<View>(R.id.xingyue_motion_scroll).visibility = visibility
    }

    private fun updateDisplayPresetButtons() {
        val presets = listOf(
            R.id.display_mode_full_body to "FULL_BODY",
            R.id.display_mode_half_body to "HALF_BODY",
            R.id.display_mode_chibi to "CHIBI"
        )
        presets.forEach { (viewId, preset) ->
            val view = findViewById<TextView>(viewId)
            val active = xingyueDisplayPreset == preset
            view.setBackgroundResource(if (active) R.drawable.bg_tag_active else R.drawable.bg_tag_inactive)
            view.setTextColor(ContextCompat.getColor(this, if (active) android.R.color.white else R.color.mi_text_main))
        }
    }

    private fun bindXingyueTestChips() {
        bindXingyueChipGroup(R.id.xingyue_expression_chip_group, xingyueExpressionItems) { item ->
            if (live2DManager.currentModel.id != "xingyue_shuimu") {
                ToastUtils.show(this, "表情测试仅用于星月水母")
                return@bindXingyueChipGroup
            }
            live2DManager.applyExpression(item.file)
            ToastUtils.show(this, "已应用${item.label}")
        }
        bindXingyueChipGroup(R.id.xingyue_motion_chip_group, xingyueMotionItems) { item ->
            if (live2DManager.currentModel.id != "xingyue_shuimu") {
                ToastUtils.show(this, "动作测试仅用于星月水母")
                return@bindXingyueChipGroup
            }
            live2DManager.playMotionByFile(item.file)
            ToastUtils.show(this, "已播放${item.label}")
        }
    }

    private fun bindXingyueLookPresets() {
        val lookPresetItems = listOf(
            XingyueLive2DTestItem("soft", "soft"),
            XingyueLive2DTestItem("body", "body"),
            XingyueLive2DTestItem("hair", "hair"),
            XingyueLive2DTestItem("full", "fullPhysics"),
            XingyueLive2DTestItem("extreme", "extreme")
        )
        bindXingyueChipGroup(R.id.xingyue_look_preset_group, lookPresetItems) { item ->
            if (live2DManager.currentModel.id != "xingyue_shuimu") {
                ToastUtils.show(this, "拖拽预设仅用于星月水母")
                return@bindXingyueChipGroup
            }
            live2DManager.runJs("setLookPreset('${item.file}')")
            ToastUtils.show(this, "拖拽预设: ${item.label}")
        }
    }

    private fun bindXingyueChipGroup(
        groupId: Int,
        items: List<XingyueLive2DTestItem>,
        onClick: (XingyueLive2DTestItem) -> Unit
    ) {
        val group = findViewById<LinearLayout>(groupId)
        group.removeAllViews()
        items.forEach { item ->
            val chip = TextView(this).apply {
                text = item.label
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@CompanionActivity, R.color.xingyue_text_primary))
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(14), 0, dp(14), 0)
                setBackgroundResource(R.drawable.bg_quick_chip)
                setOnClickListener { onClick(item) }
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            view.animate().cancel()
                            view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).start()
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.animate().cancel()
                            view.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                        }
                    }
                    false
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginEnd = dp(8)
            }
            group.addView(chip, params)
        }
    }

    private fun bindActionButton(
        viewId: Int,
        state: Live2DState,
        motionName: String,
        expressionName: String,
        bubble: String
    ) {
        findViewById<TextView>(viewId).setOnClickListener {
            live2DManager.playMotion(motionName)
            live2DManager.setExpression(expressionName)
            live2DManager.setState(state)
            ToastUtils.show(this, bubble)
        }
    }

    private fun bindTextOptions(groupId: Int, key: String) {
        val group = findViewById<LinearLayout>(groupId)
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is TextView) {
                child.setOnClickListener {
                    petSettingManager.saveString(key, child.text.toString())
                    ToastUtils.show(this, "已选择${child.text}，真实换装资源将在第二阶段接入")
                }
            }
        }
    }

    private fun bindSwitch(viewId: Int, key: String, defaultValue: Boolean) {
        val switch = findViewById<SwitchCompat>(viewId)
        switch.isChecked = petSettingManager.readBoolean(key, defaultValue)
        switch.setOnCheckedChangeListener { _, isChecked ->
            petSettingManager.saveBoolean(key, isChecked)
        }
    }

    private fun bindFloatingControls() {
        floatingSettingsManager.enhancedInteractionEnabled = false
        hideEnhancedInteractionControls()

        val floatingSwitch = findViewById<SwitchCompat>(R.id.floating_pet_switch)
        floatingSwitch.isChecked = floatingSettingsManager.enabled
        floatingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startFloatingLive2D()
            } else {
                stopFloatingLive2D()
            }
        }

        findViewById<TextView>(R.id.floating_open_button).setOnClickListener {
            if (floatingSwitch.isChecked) {
                startFloatingLive2D()
            } else {
                floatingSwitch.isChecked = true
            }
        }
        findViewById<TextView>(R.id.floating_close_button).setOnClickListener {
            floatingSwitch.isChecked = false
            stopFloatingLive2D()
        }
        findViewById<TextView>(R.id.floating_switch_model).setOnClickListener {
            showPetSwitchDialog()
        }
        findViewById<TextView>(R.id.floating_reset_position).setOnClickListener {
            floatingSettingsManager.resetPosition()
            notifyFloatingService(Live2DFloatingService.ACTION_RESET_POSITION)
            ToastUtils.show(this, "已重置悬浮位置")
        }

        findViewById<TextView>(R.id.floating_accessibility_settings).setOnClickListener {
            ToastUtils.show(this, "请在辅助功能中开启心屿增强互动模式")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val enhancedSwitch = findViewById<SwitchCompat>(R.id.enhanced_interaction_switch)
        enhancedSwitch.isChecked = floatingSettingsManager.enhancedInteractionEnabled
        enhancedSwitch.setOnCheckedChangeListener { _, isChecked ->
            floatingSettingsManager.enhancedInteractionEnabled = isChecked
            updateFloatingInteractionStatus()
            if (isChecked && !isFloatingAccessibilityEnabled()) {
                ToastUtils.show(this, "请先开启系统辅助功能权限")
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                ToastUtils.show(this, if (isChecked) "增强互动模式已开启" else "增强互动模式已关闭")
            }
        }
        findViewById<TextView>(R.id.enhanced_interaction_close).setOnClickListener {
            enhancedSwitch.isChecked = false
            floatingSettingsManager.enhancedInteractionEnabled = false
            updateFloatingInteractionStatus()
            ToastUtils.show(this, "增强互动模式已关闭")
        }
        findViewById<TextView>(R.id.enhanced_interaction_test).setOnClickListener {
            val controller = FloatingLive2DBridge.current()
            if (controller == null) {
                ToastUtils.show(this, "悬浮人物未启动")
            } else {
                controller.lookAt(0.6f, 0.3f)
                controller.playTapReaction(0.6f, 0.3f)
                controller.showBubble("我在这里陪你")
                ToastUtils.show(this, "已发送测试反应")
            }
            updateFloatingInteractionStatus()
        }
        updateFloatingInteractionStatus()

        val modelScaleSeekBar = findViewById<SeekBar>(R.id.floating_model_scale_seekbar)
        modelScaleSeekBar.progress = ((floatingSettingsManager.modelScale - 0.8f) / 0.4f * 120).toInt()
        modelScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    floatingSettingsManager.modelScale = 0.8f + progress / 120f * 0.4f
                    notifyFloatingService()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val windowSizeSeekBar = findViewById<SeekBar>(R.id.floating_window_size_seekbar)
        windowSizeSeekBar.visibility = View.GONE
        (windowSizeSeekBar.parent as? LinearLayout)?.let { parent ->
            val index = parent.indexOfChild(windowSizeSeekBar)
            if (index > 0) parent.getChildAt(index - 1).visibility = View.GONE
        }

        val transparentSwitch = findViewById<SwitchCompat>(R.id.floating_transparent_switch)
        transparentSwitch.isChecked = floatingSettingsManager.transparentBackground
        transparentSwitch.setOnCheckedChangeListener { _, isChecked ->
            floatingSettingsManager.transparentBackground = isChecked
            notifyFloatingService()
        }
    }

    private fun hideEnhancedInteractionControls() {
        intArrayOf(
            R.id.enhanced_interaction_switch,
            R.id.floating_accessibility_settings,
            R.id.enhanced_interaction_close,
            R.id.enhanced_interaction_test,
            R.id.floating_interaction_status
        ).forEach { viewId ->
            findViewById<View>(viewId).visibility = View.GONE
        }
    }

    private fun startFloatingLive2D(variantId: String? = null) {
        val model = live2DManager.currentModel
        if (
            model.id == FloatingModelVariantRepository.XINGYUE_SHUIMU_MODEL_ID &&
            variantId == null
        ) {
            showXingyueFloatingVariantDialog()
            return
        }
        if (!hasOverlayPermission()) {
            pendingStartFloating = true
            pendingFloatingVariant = variantId
            requestOverlayPermission()
            return
        }
        floatingSettingsManager.modelScale = 1f
        floatingSettingsManager.saveFloatingSelection(model.id, variantId)
        floatingSettingsManager.enabled = true
        startService(Intent(this, Live2DFloatingService::class.java).apply {
            action = Live2DFloatingService.ACTION_START
            putExtra(Live2DFloatingService.EXTRA_MODEL_ID, model.id)
            putExtra(Live2DFloatingService.EXTRA_VARIANT, variantId)
        })
        ToastUtils.show(this, "心屿已在桌面陪伴你")
    }

    private fun showXingyueFloatingVariantDialog() {
        val model = live2DManager.currentModel
        val variants = FloatingModelVariantRepository.supportedVariants(this, model)
        if (variants.isEmpty()) {
            ToastUtils.show(this, "星月水母悬浮配置读取失败")
            findViewById<SwitchCompat>(R.id.floating_pet_switch).isChecked = false
            return
        }
        showTrackedDialog(
            MaterialAlertDialogBuilder(this)
            .setTitle("选择星月水母悬浮形态")
            .setItems(variants.map { it.displayName }.toTypedArray()) { _, which ->
                startFloatingLive2D(variants[which].id)
            }
            .setNegativeButton("取消") { _, _ ->
                findViewById<SwitchCompat>(R.id.floating_pet_switch).isChecked = false
            }
            .setOnCancelListener {
                findViewById<SwitchCompat>(R.id.floating_pet_switch).isChecked = false
            },
            CompanionOverlayMode.DIALOG
        )
    }

    private fun updateFloatingInteractionStatus() {
        val accessibilityState = if (isFloatingAccessibilityEnabled()) "已开启辅助功能" else "未开启辅助功能"
        val floatingState = if (FloatingLive2DBridge.current() != null) "悬浮人物已连接" else "悬浮人物未启动"
        val enhancedState = if (floatingSettingsManager.enhancedInteractionEnabled) "增强互动已开启" else "增强互动已关闭"
        findViewById<TextView>(R.id.floating_interaction_status).text =
            "$accessibilityState\n$floatingState\n$enhancedState\n${FloatingInteractionStatusStore(this).readSummary()}"
    }

    private fun isFloatingAccessibilityEnabled(): Boolean {
        val expectedName = ComponentName(
            this,
            FloatingInteractionAccessibilityService::class.java
        ).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (serviceName in splitter) {
            if (serviceName.equals(expectedName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun stopFloatingLive2D() {
        floatingSettingsManager.enabled = false
        startService(Intent(this, Live2DFloatingService::class.java).apply {
            action = Live2DFloatingService.ACTION_STOP
        })
    }

    private fun notifyFloatingService(actionName: String = Live2DFloatingService.ACTION_UPDATE) {
        if (!floatingSettingsManager.enabled && actionName != Live2DFloatingService.ACTION_RESET_POSITION) {
            return
        }
        startService(Intent(this, Live2DFloatingService::class.java).apply {
            action = actionName
            putExtra(Live2DFloatingService.EXTRA_MODEL_ID, live2DManager.currentModel.id)
            if (live2DManager.currentModel.id == FloatingModelVariantRepository.XINGYUE_SHUIMU_MODEL_ID) {
                putExtra(
                    Live2DFloatingService.EXTRA_VARIANT,
                    floatingSettingsManager.floatingVariant
                )
            }
        })
    }

    override fun currentRoleId(): String = currentRoleId

    override fun currentModelId(): String = currentModelId

    override fun currentPage(): String = when (currentBottomTabId) {
        R.id.nav_companion_tab -> "companion"
        R.id.nav_exchange_tab -> "relax"
        R.id.nav_diagnosis_tab -> "assessment"
        R.id.nav_profile_tab -> "profile"
        else -> "unknown"
    }

    override fun confirm(action: AiAction, onConfirm: () -> Unit) {
        val message = action.displayText.ifBlank {
            "确定要清空当前角色的聊天记录吗？"
        }
        showTrackedDialog(
            MaterialAlertDialogBuilder(this)
                .setTitle("确认操作")
                .setMessage(message)
                .setNegativeButton("取消") { _, _ ->
                    Log.d("MindIsleAi", "action cancelled type=${action.type}")
                }
                .setPositiveButton("确认") { _, _ -> onConfirm() }
        )
    }

    override fun openPage(page: String): AiActionResult {
        val tabId = when (page) {
            "companion" -> R.id.nav_companion_tab
            "relax" -> R.id.nav_exchange_tab
            "assessment" -> R.id.nav_diagnosis_tab
            "profile" -> R.id.nav_profile_tab
            else -> return AiActionResult.failure("navigation.openPage", "invalid page")
        }
        Log.d("MindIsleAi", "open page=$page")
        selectBottomTab(tabId)
        return AiActionResult.success("navigation.openPage", "opened page=$page")
    }

    override fun switchRole(modelId: String): AiActionResult {
        val role = aiRoleManager.findByModelId(modelId)
            ?: return AiActionResult.failure("role.switch", "role model is unavailable")
        if (role.modelId == currentModelId) {
            return AiActionResult.success("role.switch", "role already selected")
        }
        selectAiRole(role)
        return if (currentModelId == modelId) {
            AiActionResult.success("role.switch", "switched modelId=$modelId")
        } else {
            AiActionResult.failure("role.switch", "model switch failed")
        }
    }

    override fun setExpression(expression: String): AiActionResult {
        if (!live2DManager.isModelLoaded()) {
            return AiActionResult.failure("live2d.setExpression", "model is not loaded")
        }
        val supported = currentAiRole.live2d.expressions
        if (supported.isNotEmpty() && expression !in supported) {
            return AiActionResult.failure(
                "live2d.setExpression",
                "expression is not available for current model"
            )
        }
        live2DManager.setExpression(expression)
        return AiActionResult.success("live2d.setExpression", "expression=$expression")
    }

    override fun playMotion(motion: String): AiActionResult {
        if (!live2DManager.isModelLoaded()) {
            return AiActionResult.failure("live2d.playMotion", "model is not loaded")
        }
        val supported = currentAiRole.live2d.motions
        if (supported.isNotEmpty() && motion !in supported) {
            return AiActionResult.failure(
                "live2d.playMotion",
                "motion is not available for current model"
            )
        }
        live2DManager.playMotion(motion)
        return AiActionResult.success("live2d.playMotion", "motion=$motion")
    }

    override fun clearCurrentRoleHistory(): AiActionResult {
        conversationHistory.clear()
        chatStorage.clear(currentModelId)
        messageList.clearMessages()
        scrollMessagesToBottom()
        ToastUtils.show(this, "当前角色聊天记录已清空")
        return AiActionResult.success(
            "chat.clearCurrentRoleHistory",
            "cleared history for modelId=$currentModelId"
        )
    }

    override fun openMusicPanel(): AiActionResult {
        prepareMusicAction(
            actionType = "music.openPanel",
            requireTracks = false,
            requirePanel = true
        )?.let { return it }
        openMusicDrawer()
        return AiActionResult.success("music.openPanel", "music panel opened")
    }

    override fun playMusic(): AiActionResult {
        prepareMusicAction("music.play", requireTracks = true)?.let { return it }
        musicPlayerManager.play()
        return AiActionResult.success("music.play", "music playback requested")
    }

    override fun pauseMusic(): AiActionResult {
        prepareMusicAction("music.pause", requireTracks = false)?.let { return it }
        if (musicPlayerManager.isPlaying) musicPlayerManager.pause()
        return AiActionResult.success("music.pause", "music paused")
    }

    override fun nextMusic(): AiActionResult {
        prepareMusicAction("music.next", requireTracks = true)?.let { return it }
        musicPlayerManager.next()
        return AiActionResult.success("music.next", "next track requested")
    }

    override fun previousMusic(): AiActionResult {
        prepareMusicAction("music.previous", requireTracks = true)?.let { return it }
        musicPlayerManager.previous()
        return AiActionResult.success("music.previous", "previous track requested")
    }

    override fun togglePlayMusic(): AiActionResult {
        prepareMusicAction("music.togglePlay", requireTracks = true)?.let { return it }
        musicPlayerManager.toggle()
        return AiActionResult.success("music.togglePlay", "music playback toggled")
    }

    override fun openMusicList(): AiActionResult {
        prepareMusicAction(
            actionType = "music.openList",
            requireTracks = false,
            requirePanel = true
        )?.let { return it }
        musicDrawerController.openList()
        return AiActionResult.success("music.openList", "music list opened")
    }

    override fun setMusicPlayMode(mode: String): AiActionResult {
        prepareMusicAction("music.setPlayMode", requireTracks = false)?.let { return it }
        val playMode = when (mode) {
            "sequence" -> MusicPlayMode.ORDER
            "shuffle" -> MusicPlayMode.SHUFFLE
            "single" -> MusicPlayMode.REPEAT_ONE
            else -> return AiActionResult.failure("music.setPlayMode", "invalid play mode")
        }
        musicPlayerManager.setPlayMode(playMode)
        if (::musicDrawerController.isInitialized) {
            musicDrawerController.renderPlayMode(playMode)
        }
        ToastUtils.show(this, "已切换为${musicPlayModeLabel(playMode)}播放")
        return AiActionResult.success("music.setPlayMode", "play mode=$mode")
    }

    override fun toggleMusicPlayMode(): AiActionResult {
        prepareMusicAction("music.togglePlayMode", requireTracks = false)?.let { return it }
        val mode = musicPlayerManager.cycleMode()
        if (::musicDrawerController.isInitialized) {
            musicDrawerController.renderPlayMode(mode)
        }
        ToastUtils.show(this, "已切换为${musicPlayModeLabel(mode)}播放")
        return AiActionResult.success("music.togglePlayMode", "play mode=${mode.name}")
    }

    private fun prepareMusicAction(
        actionType: String,
        requireTracks: Boolean,
        requirePanel: Boolean = false
    ): AiActionResult? {
        val pageBeforeAction = currentPage()
        Log.d("MindIsleAi", "music action type=$actionType")
        Log.d("MindIsleAi", "music action navigateToRelax=false")
        Log.d("MindIsleAi", "current page=$pageBeforeAction")
        Log.d("MindIsleAi", "use global MusicPlayerManager")
        Log.d("MindIsleAi", "use RelaxFragment=false")
        if (isFinishing || isDestroyed) {
            Log.w("MindIsleAi", "music player ready=false activity unavailable")
            return AiActionResult.failure(actionType, "音乐播放器正在准备中")
        }
        val playerReady = ::musicPlayerManager.isInitialized
        val panelReady = ::musicDrawerController.isInitialized
        Log.d("MindIsleAi", "music player ready=$playerReady panel ready=$panelReady")
        if (!playerReady || (requirePanel && !panelReady)) {
            return AiActionResult.failure(actionType, "音乐播放器正在准备中")
        }
        val listSize = musicPlayerManager.getTracks().size
        Log.d("MindIsleAi", "music list size=$listSize")
        if (requireTracks && listSize == 0) {
            return AiActionResult.failure(actionType, "请先添加音乐")
        }
        if (currentPage() != pageBeforeAction) {
            Log.e("MindIsleAi", "ERROR music action should not navigate to relax")
            return AiActionResult.failure(actionType, "音乐操作不应切换当前页面")
        }
        return null
    }

    private fun musicPlayModeLabel(mode: MusicPlayMode): String = when (mode) {
        MusicPlayMode.ORDER -> "顺序"
        MusicPlayMode.SHUFFLE -> "随机"
        MusicPlayMode.REPEAT_ONE -> "单曲循环"
    }

    override fun openFeature(actionType: String): AiActionResult {
        return when (actionType) {
            "assessment.open" -> {
                selectBottomTab(R.id.nav_diagnosis_tab)
                assessmentController.openHome()
                actionSuccess(actionType)
            }
            "assessment.startToday" ->
                openAssessmentAction(actionType) {
                    assessmentController.startTodayAssessment()
                }
            "assessment.openRecords" -> {
                selectBottomTab(R.id.nav_diagnosis_tab)
                assessmentController.openRecords()
                actionSuccess(actionType)
            }
            "assessment.openEmotion" ->
                openAssessmentType(actionType, "emotion")
            "assessment.openPressure" ->
                openAssessmentType(actionType, "stress")
            "assessment.openSleep" ->
                openAssessmentType(actionType, "sleep")
            "assessment.openAnxiety" ->
                openAssessmentType(actionType, "anxiety")
            "assessment.openLowMood" ->
                openAssessmentType(actionType, "low_mood")
            "assessment.openSocialEnergy" ->
                openAssessmentType(actionType, "social_energy")
            "assessment.openLatestResult" ->
                openAssessmentAction(actionType, "还没有最近评估") {
                    assessmentController.openLatestResult()
                }
            "relax.open" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                relaxController.openHome()
                actionSuccess(actionType)
            }
            "relax.startThreeMinute", "relax.openBreathing" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                if (actionType == "relax.startThreeMinute") {
                    relaxController.startThreeMinuteRelax()
                } else {
                    relaxController.openBreathing()
                }
                actionSuccess(actionType)
            }
            "relax.openJellyfishBubble" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                relaxController.openJellyfishBubble()
                actionSuccess(actionType)
            }
            "relax.openStarCollection" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                relaxController.openStarCollection()
                actionSuccess(actionType)
            }
            "relax.openMoodBottle" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                relaxController.openMoodBottle()
                actionSuccess(actionType)
            }
            "relax.openBodyScan" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                relaxController.openBodyScan()
                actionSuccess(actionType)
            }
            "relax.openWhiteNoise" ->
                unavailableFeature(actionType, R.id.nav_exchange_tab)
            "relax.openRecentRelax" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                relaxController.openRecentRelax()
                actionSuccess(actionType)
            }
            "game.openSnake" -> openClassicGameAction(actionType, "snake")
            "game.open2048" -> openClassicGameAction(actionType, "game_2048")
            "game.openTetris" -> openClassicGameAction(actionType, "tetris")
            "game.openPinball" -> openClassicGameAction(actionType, "pinball")
            "game.openRecentGame" -> {
                selectBottomTab(R.id.nav_exchange_tab)
                relaxController.openRecentGame()
                actionSuccess(actionType)
            }
            "profile.open" -> {
                selectBottomTab(R.id.nav_profile_tab)
                actionSuccess(actionType)
            }
            "profile.openPersonalInfo" ->
                openProfileEntry(actionType, R.id.profile_edit_entry)
            "profile.openStatus" ->
                openProfileEntry(actionType, R.id.profile_status_entry)
            "profile.openQrCode" ->
                openProfileEntry(actionType, R.id.profile_qr_entry)
            "profile.openRoleManagement" ->
                openProfileEntry(actionType, R.id.profile_role_entry)
            "profile.openChatRecords" ->
                openProfileEntry(actionType, R.id.profile_chat_history_entry)
            "profile.openSettings" ->
                unavailableFeature(actionType, R.id.nav_profile_tab)
            "profile.openHelpFeedback" ->
                unavailableFeature(actionType, R.id.nav_profile_tab)
            "profile.openAbout" ->
                openProfileEntry(actionType, R.id.profile_about_entry)
            "profile.openPet" ->
                openProfileEntry(actionType, R.id.profile_pet_entry)
            "profile.openBackgroundTheme" ->
                openProfileEntry(actionType, R.id.profile_theme_entry)
            else -> AiActionResult.failure(actionType, "unknown feature action")
        }
    }

    override fun showActionFailure(message: String) {
        ToastUtils.show(this, message.ifBlank { "操作暂时无法完成" })
    }

    private fun openAssessmentType(actionType: String, assessmentType: String): AiActionResult =
        openAssessmentAction(actionType) {
            assessmentController.startAssessmentByType(assessmentType)
        }

    private fun openAssessmentAction(
        actionType: String,
        failureMessage: String = "该功能正在完善中",
        action: () -> Boolean
    ): AiActionResult {
        selectBottomTab(R.id.nav_diagnosis_tab)
        return if (action()) {
            actionSuccess(actionType)
        } else {
            AiActionResult.failure(actionType, failureMessage)
        }
    }

    private fun openClassicGameAction(actionType: String, gameType: String): AiActionResult {
        selectBottomTab(R.id.nav_exchange_tab)
        return if (relaxController.openClassicGame(gameType)) {
            actionSuccess(actionType)
        } else {
            AiActionResult.failure(actionType, "该功能正在完善中")
        }
    }

    private fun openProfileEntry(actionType: String, viewId: Int): AiActionResult {
        selectBottomTab(R.id.nav_profile_tab)
        val entry = findViewById<View>(viewId)
        if (!entry.performClick()) {
            return AiActionResult.failure(actionType, "该功能正在完善中")
        }
        return actionSuccess(actionType)
    }

    private fun actionSuccess(actionType: String): AiActionResult =
        AiActionResult.success(actionType, "action completed")

    private fun unavailableFeature(actionType: String, tabId: Int): AiActionResult {
        selectBottomTab(tabId)
        return AiActionResult.failure(actionType, "该功能正在完善中")
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        ToastUtils.show(this, "请允许心屿显示在其他应用上层")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun bindCharacterSize() {
        val seekBar = findViewById<SeekBar>(R.id.character_size_seekbar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                live2DManager.setViewScale(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun bindBottomNavigation() {
        findViewById<View>(R.id.nav_companion_tab).setOnClickListener {
            selectBottomTab(R.id.nav_companion_tab)
        }
        findViewById<View>(R.id.nav_exchange_tab).setOnClickListener {
            selectBottomTab(R.id.nav_exchange_tab)
        }
        findViewById<View>(R.id.nav_diagnosis_tab).setOnClickListener {
            selectBottomTab(R.id.nav_diagnosis_tab)
        }
        findViewById<View>(R.id.nav_profile_tab).setOnClickListener {
            selectBottomTab(R.id.nav_profile_tab)
        }
        val initialTab = when (intent.getStringExtra(EXTRA_INITIAL_TAB)) {
            TAB_EXCHANGE, TAB_RELAX -> R.id.nav_exchange_tab
            else -> R.id.nav_companion_tab
        }
        selectBottomTab(initialTab)
    }

    private fun selectBottomTab(tabId: Int) {
        hideKeyboardAndClearFocus()
        if (::sideMenuController.isInitialized && sideMenuController.isOpen) {
            sideMenuController.close()
        }
        currentBottomTabId = tabId
        val isCompanion = tabId == R.id.nav_companion_tab
        val isExchange = tabId == R.id.nav_exchange_tab
        val isDiagnosis = tabId == R.id.nav_diagnosis_tab
        val isProfile = tabId == R.id.nav_profile_tab

        findViewById<View>(R.id.section_companion_content).visibility = if (isCompanion) View.VISIBLE else View.GONE
        findViewById<View>(R.id.section_exchange_content).visibility = if (isExchange) View.VISIBLE else View.GONE
        findViewById<View>(R.id.section_diagnosis_content).visibility = if (isDiagnosis) View.VISIBLE else View.GONE
        findViewById<View>(R.id.section_profile_content).visibility = if (isProfile) View.VISIBLE else View.GONE
        updateCompanionBottomLayerVisibility()
        if (isDiagnosis && ::assessmentController.isInitialized) {
            assessmentController.onVisible()
        }
        if (::relaxController.isInitialized) {
            if (isExchange) relaxController.onVisible() else relaxController.onHidden()
        }
        if (isProfile) {
            showProfileHome()
        } else {
            findViewById<View>(R.id.profile_edit_content).visibility = View.GONE
        }

        applyBottomTabStyle(R.id.nav_companion_label, R.id.nav_companion_dot, isCompanion)
        applyBottomTabStyle(R.id.nav_exchange_label, R.id.nav_exchange_dot, isExchange)
        applyBottomTabStyle(R.id.nav_diagnosis_label, R.id.nav_diagnosis_dot, isDiagnosis)
        applyBottomTabStyle(R.id.nav_profile_label, R.id.nav_profile_dot, isProfile)

        findViewById<View>(tabId).animate().cancel()
        findViewById<View>(tabId).scaleX = 0.96f
        findViewById<View>(tabId).scaleY = 0.96f
        findViewById<View>(tabId).animate().scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun bindProfilePage() {
        findViewById<View>(R.id.profile_edit_entry).setOnClickListener { showProfileEditor() }
        findViewById<View>(R.id.profile_status_entry).setOnClickListener { showProfileStatusPicker() }
        findViewById<View>(R.id.profile_status_chip).setOnClickListener { showProfileStatusPicker() }
        findViewById<View>(R.id.profile_qr_entry).setOnClickListener { showProfileQrDialog() }
        findViewById<View>(R.id.profile_qr_button).setOnClickListener { showProfileQrDialog() }
        findViewById<View>(R.id.profile_avatar_button).setOnClickListener { openProfileAvatarPicker() }
        findViewById<View>(R.id.profile_edit_avatar_button).setOnClickListener { openProfileAvatarPicker() }
        findViewById<View>(R.id.profile_edit_back).setOnClickListener { showProfileHome() }
        findViewById<View>(R.id.profile_edit_save).setOnClickListener { saveProfileEditor() }

        findViewById<View>(R.id.profile_theme_entry).setOnClickListener { showBackgroundPickerDialog() }
        findViewById<View>(R.id.profile_role_entry).setOnClickListener { showAiRoleDialog() }
        findViewById<View>(R.id.profile_pet_entry).setOnClickListener {
            ToastUtils.show(this, "当前陪伴角色：${currentAiRole.displayName}")
        }
        findViewById<View>(R.id.profile_chat_history_entry).setOnClickListener {
            ToastUtils.show(this, "当前角色已保存 ${conversationHistory.size} 条聊天记录")
        }
        findViewById<View>(R.id.profile_settings_entry).setOnClickListener {
            ToastUtils.show(this, "设置功能正在整理中")
        }
        findViewById<View>(R.id.profile_help_entry).setOnClickListener {
            ToastUtils.show(this, "帮助与反馈功能正在完善中")
        }
        findViewById<View>(R.id.profile_about_entry).setOnClickListener {
            showTrackedDialog(
                MaterialAlertDialogBuilder(this)
                .setTitle("关于心屿")
                .setMessage("心屿是一处可以安心聊天、记录心情，也能与 Live2D 伙伴相伴的小空间。")
                .setPositiveButton("知道了", null)
            )
        }

        bindProfilePressFeedback(
            R.id.profile_pet_entry,
            R.id.profile_theme_entry,
            R.id.profile_edit_entry,
            R.id.profile_status_entry,
            R.id.profile_status_chip,
            R.id.profile_qr_entry,
            R.id.profile_role_entry,
            R.id.profile_chat_history_entry,
            R.id.profile_settings_entry,
            R.id.profile_help_entry,
            R.id.profile_about_entry
        )
        refreshProfileHome()
    }

    private fun openProfileAvatarPicker() {
        pickProfileAvatarLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun showProfileEditor() {
        hideKeyboardAndClearFocus()
        val profile = UserProfileStore.load(this)
        fillProfileEditor(profile)
        findViewById<View>(R.id.profile_home_content).visibility = View.GONE
        findViewById<View>(R.id.profile_edit_content).apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(180L).start()
        }
        // The companion input is page-scoped and must stay hidden while editing profile fields.
        findViewById<View>(R.id.chat_compose_area).visibility = View.GONE
    }

    private fun showProfileHome() {
        hideKeyboardAndClearFocus()
        findViewById<View>(R.id.profile_edit_content).visibility = View.GONE
        findViewById<View>(R.id.profile_home_content).apply {
            alpha = 1f
            visibility = View.VISIBLE
        }
        refreshProfileHome()
    }

    private fun refreshProfileHome() {
        if (!::currentAiRole.isInitialized) return
        refreshProfileViews(UserProfileStore.load(this))
        findViewById<TextView>(R.id.profile_companion_role).text = currentAiRole.displayName
    }

    private fun refreshProfileViews(profile: UserProfile) {
        val activeStatus = profileStatusStore.loadActive()
        val statusItem = ProfileStatusCatalog.find(activeStatus?.statusId)
        findViewById<TextView>(R.id.profile_name).text = profile.nickname
        findViewById<TextView>(R.id.profile_status).text = activeStatus?.statusText ?: profile.status
        findViewById<TextView>(R.id.profile_user_id).text = "ID：${profile.userId}"
        findViewById<ImageView>(R.id.profile_status_chip_icon).setImageResource(
            statusItem?.iconRes ?: R.drawable.ic_status_plus
        )
        findViewById<TextView>(R.id.profile_status_chip_text).text =
            statusItem?.name ?: "状态"
        applyProfileAvatar(findViewById(R.id.profile_avatar), profile.avatarUri)
        applyProfileAvatar(findViewById(R.id.profile_edit_avatar), profile.avatarUri)
    }

    private fun showProfileStatusPicker() {
        hideKeyboardAndClearFocus()
        findViewById<View>(R.id.chat_compose_area).visibility = View.GONE

        val activeStatus = profileStatusStore.loadActive()
        var selectedItem = ProfileStatusCatalog.find(activeStatus?.statusId)
        val content = layoutInflater.inflate(R.layout.dialog_profile_status, null)
        val input = content.findViewById<EditText>(R.id.status_text_input)
        val selectedIcon = content.findViewById<ImageView>(R.id.status_selected_icon)
        val clearButton = content.findViewById<TextView>(R.id.status_clear_button)
        val saveButton = content.findViewById<TextView>(R.id.status_save_button)
        val categoriesContainer = content.findViewById<LinearLayout>(R.id.status_categories_container)
        val optionViews = linkedMapOf<View, Pair<View, StatusItem>>()

        input.setText(activeStatus?.statusText.orEmpty())
        clearButton.text = if (activeStatus == null) "取消" else "结束状态"

        fun renderSelection() {
            selectedIcon.setImageResource(selectedItem?.iconRes ?: R.drawable.ic_status_jellyfish)
            input.hint = selectedItem?.defaultText ?: "写一句今天的状态"
            optionViews.forEach { (root, value) ->
                val (badge, item) = value
                val selected = item.id == selectedItem?.id
                root.isSelected = selected
                badge.isSelected = selected
                root.findViewById<TextView>(R.id.status_option_name).setTextColor(
                    if (selected) getColor(R.color.profile_accent_deep) else getColor(R.color.mi_text_sub)
                )
            }
        }

        ProfileStatusCatalog.categories.forEach { (category, items) ->
            categoriesContainer.addView(TextView(this).apply {
                text = category
                setTextColor(getColor(R.color.profile_caption))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(17), 0, dp(7))
            })
            val grid = GridLayout(this).apply {
                columnCount = 4
                alignmentMode = GridLayout.ALIGN_BOUNDS
                useDefaultMargins = false
            }
            items.forEach { item ->
                val option = layoutInflater.inflate(R.layout.item_profile_status, grid, false)
                val badge = option.findViewById<View>(R.id.status_option_badge)
                option.findViewById<ImageView>(R.id.status_option_icon).setImageResource(item.iconRes)
                option.findViewById<TextView>(R.id.status_option_name).text = item.name
                optionViews[option] = badge to item
                option.setOnClickListener {
                    selectedItem = item
                    if (input.text.isNullOrBlank()) {
                        input.hint = item.defaultText
                    }
                    renderSelection()
                    option.animate().cancel()
                    option.scaleX = 0.94f
                    option.scaleY = 0.94f
                    option.animate().scaleX(1f).scaleY(1f).setDuration(170L).start()
                }
                grid.addView(option, GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(96)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            categoriesContainer.addView(
                grid,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        renderSelection()

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)
        setCompanionOverlayOpen(CompanionOverlayMode.BOTTOM_SHEET, true)
        content.findViewById<View>(R.id.status_sheet_close).setOnClickListener {
            dismissStatusSheet(dialog, content)
        }
        clearButton.setOnClickListener {
            if (activeStatus != null) {
                profileStatusStore.clear()
                refreshProfileHome()
                animateStatusChip()
            }
            dismissStatusSheet(dialog, content)
        }
        saveButton.setOnClickListener {
            val item = selectedItem
            if (item == null) {
                ToastUtils.show(this, "先选择一个今天的状态")
                return@setOnClickListener
            }
            profileStatusStore.save(item, input.text.toString())
            refreshProfileHome()
            animateStatusChip()
            dismissStatusSheet(dialog, content)
        }
        dialog.setOnDismissListener {
            hideKeyboardAndClearFocus()
            setCompanionOverlayOpen(CompanionOverlayMode.BOTTOM_SHEET, false)
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                it.setBackgroundColor(Color.TRANSPARENT)
                it.elevation = 0f
                it.layoutParams.height = (resources.displayMetrics.heightPixels * 0.90f).toInt()
                BottomSheetBehavior.from(it).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
            content.alpha = 0f
            content.translationY = dp(28).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(210L).start()
        }
        dialog.show()
    }

    private fun dismissStatusSheet(dialog: BottomSheetDialog, content: View) {
        content.animate()
            .alpha(0f)
            .translationY(dp(24).toFloat())
            .setDuration(150L)
            .withEndAction { dialog.dismiss() }
            .start()
    }

    private fun animateStatusChip() {
        findViewById<View>(R.id.profile_status_chip).apply {
            animate().cancel()
            scaleX = 0.86f
            scaleY = 0.86f
            alpha = 0.65f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(190L).start()
        }
    }

    private fun fillProfileEditor(profile: UserProfile) {
        findViewById<EditText>(R.id.profile_edit_nickname).setText(profile.nickname)
        findViewById<EditText>(R.id.profile_edit_status).setText(profile.status)
        findViewById<EditText>(R.id.profile_edit_gender).setText(profile.gender)
        findViewById<EditText>(R.id.profile_edit_birthday).setText(profile.birthday)
        findViewById<EditText>(R.id.profile_edit_hometown).setText(profile.hometown)
        findViewById<EditText>(R.id.profile_edit_signature).setText(profile.signature)
        findViewById<EditText>(R.id.profile_edit_identity).setText(profile.identity)
        findViewById<EditText>(R.id.profile_edit_school_company).setText(profile.schoolOrCompany)
        findViewById<EditText>(R.id.profile_edit_major_direction).setText(profile.majorOrDirection)
        findViewById<EditText>(R.id.profile_edit_group_organization).setText(profile.groupOrOrganization)
        findViewById<EditText>(R.id.profile_edit_interests).setText(profile.interests)
        findViewById<TextView>(R.id.profile_edit_user_id).text = "用户ID：${profile.userId}"
        findViewById<TextView>(R.id.profile_edit_register_time).text = "注册时间：${profile.registerTime}"
        findViewById<TextView>(R.id.profile_edit_account_status).text = "账号状态：${profile.accountStatus}"
        applyProfileAvatar(findViewById(R.id.profile_edit_avatar), profile.avatarUri)
    }

    private fun saveProfileEditor() {
        val profile = UserProfileStore.load(this)
        profile.nickname = profileText(R.id.profile_edit_nickname, "叶子")
        profile.status = profileText(R.id.profile_edit_status, "今天也要好好生活")
        profile.gender = profileText(R.id.profile_edit_gender, "未设置")
        profile.birthday = profileText(R.id.profile_edit_birthday, "未设置")
        profile.hometown = profileText(R.id.profile_edit_hometown, "未设置")
        profile.signature = profileText(R.id.profile_edit_signature, "保持开心，慢慢来")
        profile.identity = profileText(R.id.profile_edit_identity, "未设置")
        profile.schoolOrCompany = profileText(R.id.profile_edit_school_company, "未设置")
        profile.majorOrDirection = profileText(R.id.profile_edit_major_direction, "未设置")
        profile.groupOrOrganization = profileText(R.id.profile_edit_group_organization, "未设置")
        profile.interests = profileText(R.id.profile_edit_interests, "陪伴、音乐、生活")
        UserProfileStore.save(this, profile)
        ToastUtils.show(this, "资料已保存")
        showProfileHome()
    }

    private fun profileText(viewId: Int, fallback: String): String {
        return findViewById<EditText>(viewId).text.toString().trim().ifBlank { fallback }
    }

    private fun applyProfileAvatar(imageView: ImageView, avatarUri: String?) {
        if (avatarUri.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.deco_xingyue_avatar)
            return
        }
        runCatching {
            imageView.setImageURI(Uri.parse(avatarUri))
            if (imageView.drawable == null) {
                imageView.setImageResource(R.drawable.deco_xingyue_avatar)
            }
        }.onFailure {
            imageView.setImageResource(R.drawable.deco_xingyue_avatar)
        }
    }

    private fun showProfileQrDialog() {
        val profile = UserProfileStore.load(this)
        val content = layoutInflater.inflate(R.layout.dialog_profile_qr, null)
        content.findViewById<ProfileQrView>(R.id.profile_qr_view).setSeed(profile.userId)
        content.findViewById<TextView>(R.id.profile_qr_name).text = profile.nickname
        content.findViewById<TextView>(R.id.profile_qr_id).text = "ID：${profile.userId}"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(content)
            .create()
        setCompanionOverlayOpen(CompanionOverlayMode.DIALOG, true)
        content.findViewById<View>(R.id.profile_qr_close).setOnClickListener {
            content.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(140L)
                .withEndAction { dialog.dismiss() }
                .start()
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener {
            setCompanionOverlayOpen(CompanionOverlayMode.DIALOG, false)
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            content.alpha = 0f
            content.scaleX = 0.94f
            content.scaleY = 0.94f
            content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190L).start()
        }
        dialog.show()
    }

    private fun bindProfilePressFeedback(vararg viewIds: Int) {
        viewIds.forEach { viewId ->
            findViewById<View>(viewId).setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(90L).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
                    }
                }
                false
            }
        }
    }

    private fun bindCompanionPressFeedback(vararg viewIds: Int) {
        viewIds.forEach { viewId ->
            findViewById<View>(viewId).setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().cancel()
                        view.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(80L)
                            .start()
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().cancel()
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(160L)
                            .start()
                    }
                }
                false
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::musicDrawerController.isInitialized && musicDrawerController.isOpen) {
            closeMusicDrawer()
            return
        }
        if (::sideMenuController.isInitialized && sideMenuController.isOpen) {
            sideMenuController.close()
            return
        }
        if (
            currentBottomTabId == R.id.nav_diagnosis_tab &&
            ::assessmentController.isInitialized &&
            assessmentController.handleBack()
        ) {
            return
        }
        if (
            currentBottomTabId == R.id.nav_exchange_tab &&
            ::relaxController.isInitialized &&
            relaxController.handleBack()
        ) {
            return
        }
        val profileEditor = findViewById<View>(R.id.profile_edit_content)
        if (profileEditor.visibility == View.VISIBLE) {
            showProfileHome()
            return
        }
        super.onBackPressed()
    }

    private fun applyBottomTabStyle(labelId: Int, dotId: Int, active: Boolean) {
        val label = findViewById<TextView>(labelId)
        val dot = findViewById<View>(dotId)
        label.setTextColor(
            if (active) getColor(R.color.xingyue_primary_blue)
            else getColor(R.color.mi_text_sub)
        )
        label.textSize = if (active) 13f else 12f
        label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        dot.visibility = if (active) View.VISIBLE else View.INVISIBLE
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "mindisle_initial_tab"
        const val TAB_EXCHANGE = "exchange"
        const val TAB_RELAX = "relax"
    }
}
