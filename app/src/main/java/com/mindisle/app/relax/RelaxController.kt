package com.mindisle.app.relax

import android.graphics.Typeface
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mindisle.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class RelaxController(
    private val activity: AppCompatActivity,
    private val onWhiteNoiseStarted: () -> Unit = {}
) {
    private enum class PageState {
        HOME,
        BREATHING,
        BUBBLE_GAME,
        STAR_GAME,
        BOTTLE,
        BODY_SCAN,
        WHITE_NOISE,
        CLASSIC_GAME,
        RESULT
    }

    private val storage = RelaxStorage(activity)
    private val root: FrameLayout = activity.findViewById(R.id.relax_root)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var homePanel: ScrollView
    private lateinit var featurePanel: FrameLayout
    private lateinit var resultPanel: ScrollView
    private lateinit var todayDurationText: TextView
    private lateinit var todayCountText: TextView
    private lateinit var todayEnergyText: TextView
    private lateinit var todayRecommendText: TextView
    private lateinit var recentContainer: LinearLayout
    private lateinit var recentGameContainer: LinearLayout
    private lateinit var classicGamesAnchor: View
    private lateinit var recentRelaxAnchor: View
    private lateinit var recentGameAnchor: View

    private var pageState = PageState.HOME
    private var breathingRunning = false
    private var breathingRemainingMs = BREATHING_TOTAL_MS
    private var breathingPhaseCursorMs = 0L
    private var breathingLastTickMs = 0L
    private var breathingLastPhase = ""
    private var breathingBall: View? = null
    private var breathingPhaseText: TextView? = null
    private var breathingHintText: TextView? = null
    private var breathingTimerText: TextView? = null
    private var breathingToggleButton: TextView? = null
    private var gameTimer: CountDownTimer? = null
    private var gameStartTime = 0L
    private var gameCount = 0
    private var playView: RelaxPlayView? = null
    private var classicGameView: ClassicGameView? = null
    private var currentClassicGame: ClassicGame? = null
    private var classicScoreText: TextView? = null
    private var classicHighScoreText: TextView? = null
    private var classicPauseButton: TextView? = null
    private var whiteNoisePlayer: WhiteNoisePlayerController? = null
    private var whiteNoiseRipple: WhiteNoiseRippleView? = null
    private var whiteNoisePlayButton: ImageView? = null
    private var whiteNoiseStatusText: TextView? = null
    private var whiteNoiseProgress: SeekBar? = null
    private var whiteNoiseCurrentTime: TextView? = null
    private var whiteNoiseTotalTime: TextView? = null
    private var whiteNoiseTimerStatus: TextView? = null
    private var whiteNoiseTimer: CountDownTimer? = null
    private var whiteNoiseTimerMinutes = 0
    private var whiteNoiseUserSeeking = false
    private var whiteNoiseRecordSaved = false

    private val breathingTick = object : Runnable {
        override fun run() {
            if (!breathingRunning) return
            val now = SystemClock.elapsedRealtime()
            val delta = (now - breathingLastTickMs).coerceAtMost(500L)
            breathingLastTickMs = now
            breathingRemainingMs = (breathingRemainingMs - delta).coerceAtLeast(0L)
            breathingPhaseCursorMs = (breathingPhaseCursorMs + delta) % BREATHING_CYCLE_MS
            updateBreathingPhase()
            breathingTimerText?.text = formatDuration((breathingRemainingMs / 1000L).toInt())
            if (breathingRemainingMs == 0L) {
                finishBreathing(completed = true)
            } else {
                handler.postDelayed(this, 100L)
            }
        }
    }

    private val whiteNoiseProgressTick = object : Runnable {
        override fun run() {
            if (pageState != PageState.WHITE_NOISE) return
            val player = whiteNoisePlayer ?: return
            if (!whiteNoiseUserSeeking) {
                whiteNoiseProgress?.max = player.duration.coerceAtLeast(1)
                whiteNoiseProgress?.progress = player.currentPosition
                whiteNoiseCurrentTime?.text = formatWhiteNoiseTime(player.currentPosition)
            }
            if (player.isPlaying) {
                handler.postDelayed(this, 250L)
            }
        }
    }

    fun bind() {
        root.removeAllViews()
        homePanel = createHomePanel()
        featurePanel = FrameLayout(activity).apply {
            visibility = View.GONE
            setBackgroundColor(activity.getColor(android.R.color.transparent))
        }
        resultPanel = createResultPanel()
        root.addView(homePanel, matchParentParams())
        root.addView(featurePanel, matchParentParams())
        root.addView(resultPanel, matchParentParams())
        showHome(animate = false)
    }

    fun onVisible() {
        refreshHome()
        currentPanel().apply {
            animate().cancel()
            alpha = 0f
            translationY = dp(8).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(180L).start()
        }
    }

    fun onHidden() {
        pauseBreathing()
        pauseClassicGame()
        pauseWhiteNoise(cancelTimer = true)
        if (pageState == PageState.BUBBLE_GAME || pageState == PageState.STAR_GAME) {
            stopGame()
            pageState = PageState.HOME
            refreshHome()
            showOnly(homePanel, animate = false)
        }
    }

    fun openGameHub() {
        showHome(animate = false)
        homePanel.post {
            homePanel.smoothScrollTo(
                0,
                (classicGamesAnchor.top - dp(16)).coerceAtLeast(0)
            )
        }
    }

    fun openHome() {
        showHome(animate = false)
    }

    fun startThreeMinuteRelax() {
        showBreathing()
    }

    fun openBreathing() {
        showBreathing()
    }

    fun openJellyfishBubble() {
        showGame(RelaxPlayView.Mode.BUBBLES)
    }

    fun openStarCollection() {
        showGame(RelaxPlayView.Mode.STARS)
    }

    fun openMoodBottle() {
        showBottle()
    }

    fun openBodyScan() {
        showBodyScan()
    }

    fun openClassicGame(type: String): Boolean {
        val game = classicGames().firstOrNull { it.type == type } ?: return false
        showClassicGame(game)
        return true
    }

    fun openRecentRelax() {
        showHomeAndScrollTo(recentRelaxAnchor)
    }

    fun openRecentGame() {
        showHomeAndScrollTo(recentGameAnchor)
    }

    private fun showHomeAndScrollTo(anchor: View) {
        showHome(animate = false)
        homePanel.post {
            homePanel.smoothScrollTo(0, (anchor.top - dp(16)).coerceAtLeast(0))
        }
    }

    fun handleBack(): Boolean {
        if (pageState == PageState.HOME) return false
        pauseBreathing()
        stopGame()
        releaseClassicGame()
        pauseWhiteNoise(cancelTimer = true)
        showHome()
        return true
    }

    private fun createHomePanel(): ScrollView {
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            setBackgroundColor(activity.getColor(android.R.color.transparent))
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(30))
        }
        scroll.addView(content, matchWidthWrapParams())

        content.addView(text("放松", 26f, R.color.xingyue_text_primary, bold = true))
        content.addView(
            text("给身心留一点安静、轻盈的恢复时间", 15f, R.color.xingyue_text_secondary)
                .withTopMargin(6)
        )
        content.addView(createHeroCard().withTopMargin(18))
        content.addView(createTodayCard().withTopMargin(14))
        content.addView(sectionTitle("选择此刻适合你的方式").withTopMargin(22))
        content.addView(
            text("所有练习仅在当前设备运行，不联网、不上传", 13f, R.color.xingyue_text_secondary)
                .withTopMargin(4)
        )

        val grid = GridLayout(activity).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = false
        }
        val features = listOf(
            Feature("呼吸放松", "跟随 4·2·6 节奏慢下来", R.drawable.ic_relax_breathe, R.drawable.bg_relax_card, PageState.BREATHING),
            Feature("水母泡泡", "轻点漂浮泡泡，释放一点紧绷", R.drawable.ic_relax_bubble, R.drawable.bg_relax_card_mint, PageState.BUBBLE_GAME),
            Feature("星星收集", "收集十颗温柔星光", R.drawable.ic_relax_star, R.drawable.bg_relax_card_purple, PageState.STAR_GAME),
            Feature("心情漂流瓶", "写下一句想暂时放下的心情", R.drawable.ic_relax_bottle, R.drawable.bg_relax_card_pink, PageState.BOTTLE),
            Feature("白噪音小憩", "十小时助眠声音，陪思绪慢慢安静", R.drawable.ic_relax_noise, R.drawable.bg_relax_card_mint, PageState.WHITE_NOISE),
            Feature("身体扫描", "用五个小步骤放松身体", R.drawable.ic_relax_scan, R.drawable.bg_relax_card, PageState.BODY_SCAN)
        )
        features.forEachIndexed { index, feature ->
            grid.addView(createFeatureCard(feature, index))
        }
        content.addView(grid.withTopMargin(10))

        classicGamesAnchor = sectionTitle("经典小游戏").withTopMargin(24)
        content.addView(classicGamesAnchor)
        content.addView(
            text("没有倒计时，想玩多久都可以", 13f, R.color.xingyue_text_secondary)
                .withTopMargin(4)
        )
        val gameGrid = GridLayout(activity).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = false
        }
        classicGames().forEachIndexed { index, game ->
            gameGrid.addView(createClassicGameCard(game, index))
        }
        content.addView(gameGrid.withTopMargin(10))

        content.addView(sectionTitle("最近游戏").withTopMargin(22))
        recentGameAnchor = content.getChildAt(content.childCount - 1)
        recentGameContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(recentGameContainer)

        content.addView(sectionTitle("最近放松").withTopMargin(22))
        recentRelaxAnchor = content.getChildAt(content.childCount - 1)
        recentContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(recentContainer)

        val notice = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            background = drawable(R.drawable.bg_relax_card_mint)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_assess_privacy)
                imageTintList = activity.getColorStateList(R.color.xingyue_mint_deep)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(text(
                "放松练习只用于日常情绪舒缓和自我照顾。如果你持续感到痛苦或有安全风险，请及时联系可信赖的人或专业支持。",
                12f,
                R.color.xingyue_text_secondary
            ).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            })
        }
        content.addView(notice.withTopMargin(14))
        return scroll
    }

    private fun createHeroCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(R.drawable.bg_relax_hero)
            setPadding(dp(20), dp(20), dp(20), dp(18))
            addView(chip("心屿放松中心"))
            addView(text("放松一会", 22f, R.color.xingyue_text_primary, bold = true).withTopMargin(14))
            addView(text(
                "今天也辛苦了，来和心屿一起慢慢缓一缓吧。",
                14f,
                R.color.xingyue_text_secondary
            ).apply { setLineSpacing(dp(3).toFloat(), 1f) }.withTopMargin(7))
            addView(primaryButton("开始 3 分钟放松") { showBreathing() }.withTopMargin(17))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                listOf("轻呼吸", "不记录压力", "随时退出").forEach { label ->
                    addView(text(label, 12f, R.color.xingyue_text_secondary).apply {
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }.withTopMargin(13))
        }

    private fun createTodayCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(R.drawable.bg_relax_card)
            setPadding(dp(18), dp(17), dp(18), dp(17))
            addView(sectionTitle("今日放松状态"))
            val firstRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                todayDurationText = statValue("0 分钟", "放松时长")
                todayCountText = statValue("0 次", "完成练习")
                addView(todayDurationText.parent as View, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(todayCountText.parent as View, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            addView(firstRow.withTopMargin(13))
            todayEnergyText = text("柔和恢复中", 13f, R.color.xingyue_mint_deep, bold = true)
            todayRecommendText = text("今日推荐：呼吸放松", 12f, R.color.xingyue_text_secondary)
            addView(todayEnergyText.withTopMargin(13))
            addView(todayRecommendText.withTopMargin(5))
        }

    private fun statValue(value: String, label: String): TextView {
        val valueView = text(value, 19f, R.color.xingyue_primary_blue, bold = true)
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(valueView)
            addView(text(label, 12f, R.color.xingyue_text_secondary).withTopMargin(3))
        }
        valueView.tag = wrapper
        return valueView.apply { setTag(R.id.relax_root, wrapper) }
    }

    private fun gameStat(value: String, label: String): TextView {
        val valueView = text(value, 18f, R.color.xingyue_primary_blue_deep, bold = true)
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(valueView)
            addView(text(label, 11f, R.color.xingyue_text_secondary).withTopMargin(2))
        }
        valueView.setTag(R.id.relax_root, wrapper)
        return valueView
    }

    private val TextView.parent: View?
        get() = getTag(R.id.relax_root) as? View

    private fun createFeatureCard(feature: Feature, index: Int): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(154)
            background = drawable(feature.backgroundRes)
            setPadding(dp(16), dp(15), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = "${feature.title}，${feature.subtitle}"
            addView(ImageView(activity).apply {
                background = drawable(R.drawable.bg_relax_badge)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setImageResource(feature.iconRes)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(text(feature.title, 16f, R.color.xingyue_text_primary, bold = true).withTopMargin(11))
            addView(text(feature.subtitle, 12f, R.color.xingyue_text_secondary).apply {
                maxLines = 2
                setLineSpacing(dp(2).toFloat(), 1f)
            }.withTopMargin(5))
            setOnClickListener {
                when (feature.state) {
                    PageState.BREATHING -> showBreathing()
                    PageState.BUBBLE_GAME -> showGame(RelaxPlayView.Mode.BUBBLES)
                    PageState.STAR_GAME -> showGame(RelaxPlayView.Mode.STARS)
                    PageState.BOTTLE -> showBottle()
                    PageState.BODY_SCAN -> showBodyScan()
                    PageState.WHITE_NOISE -> showWhiteNoise()
                    else -> Unit
                }
            }
            attachPressFeedback(this)
        }
        card.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(
                if (index % 2 == 0) 0 else dp(6),
                dp(6),
                if (index % 2 == 0) dp(6) else 0,
                dp(6)
            )
        }
        return card
    }

    private fun createClassicGameCard(game: ClassicGame, index: Int): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(148)
            background = drawable(game.backgroundRes)
            setPadding(dp(16), dp(15), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = "${game.name}，${game.subtitle}"
            addView(ImageView(activity).apply {
                background = drawable(R.drawable.bg_relax_badge)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setImageResource(game.iconRes)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(text(game.name, 16f, R.color.xingyue_text_primary, bold = true).withTopMargin(11))
            addView(text(game.subtitle, 12f, R.color.xingyue_text_secondary).apply {
                maxLines = 2
                setLineSpacing(dp(2).toFloat(), 1f)
            }.withTopMargin(5))
            setOnClickListener { showClassicGame(game) }
            attachPressFeedback(this)
        }
        card.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(
                if (index % 2 == 0) 0 else dp(6),
                dp(6),
                if (index % 2 == 0) dp(6) else 0,
                dp(6)
            )
        }
        return card
    }

    private fun showWhiteNoise() {
        pageState = PageState.WHITE_NOISE
        whiteNoiseRecordSaved = false
        whiteNoiseTimerMinutes = 0
        cancelWhiteNoiseTimer()

        val player = whiteNoisePlayer ?: WhiteNoisePlayerController(activity).also {
            whiteNoisePlayer = it
        }
        val content = featureContent(
            "白噪音小憩",
            "让声音慢慢把思绪放轻"
        )

        val visual = FrameLayout(activity).apply {
            background = drawable(R.drawable.bg_white_noise_visual)
            contentDescription = "助眠白噪音呼吸波纹"
        }
        whiteNoiseRipple = WhiteNoiseRippleView(activity)
        visual.addView(whiteNoiseRipple, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        visual.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(text("助眠白噪音", 21f, R.color.xingyue_text_primary, bold = true).apply {
                    gravity = Gravity.CENTER
                })
                addView(text("Dolby Sleep Sample", 12f, R.color.xingyue_text_secondary).apply {
                    gravity = Gravity.CENTER
                }.withTopMargin(5))
                addView(text("十小时助眠音频", 12f, R.color.xingyue_primary_blue_deep, bold = true).apply {
                    gravity = Gravity.CENTER
                }.withTopMargin(12))
            },
            FrameLayout.LayoutParams(dp(176), dp(118), Gravity.CENTER)
        )
        content.addView(visual, LinearLayout.LayoutParams(dp(286), dp(286)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(22)
        })

        val controlCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(R.drawable.bg_relax_card)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        whiteNoisePlayButton = ImageView(activity).apply {
            background = drawable(R.drawable.bg_white_noise_play_button)
            setPadding(dp(21), dp(21), dp(21), dp(21))
            setImageResource(R.drawable.ic_music_play)
            contentDescription = "播放白噪音"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animate().cancel()
                scaleX = 0.96f
                scaleY = 0.96f
                animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                if (player.isPlaying) {
                    player.pause()
                    cancelWhiteNoiseTimer()
                } else {
                    onWhiteNoiseStarted()
                    player.play()
                }
            }
        }
        controlCard.addView(
            whiteNoisePlayButton,
            LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )
        whiteNoiseStatusText = text(
            "开始小憩",
            14f,
            R.color.xingyue_primary_blue_deep,
            bold = true
        ).apply { gravity = Gravity.CENTER }
        controlCard.addView(whiteNoiseStatusText!!.withTopMargin(10))

        whiteNoiseProgress = SeekBar(activity).apply {
            max = 1
            progress = 0
            splitTrack = false
            progressDrawable = drawable(R.drawable.bg_music_progress)
            thumb = drawable(R.drawable.bg_white_noise_progress_thumb)
            thumbOffset = 0
            contentDescription = "白噪音播放进度"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    value: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        whiteNoiseCurrentTime?.text = formatWhiteNoiseTime(value)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    whiteNoiseUserSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    whiteNoiseUserSeeking = false
                    player.seekTo(seekBar.progress)
                    updateWhiteNoiseProgress()
                }
            })
        }
        controlCard.addView(
            whiteNoiseProgress,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply { topMargin = dp(12) }
        )
        whiteNoiseCurrentTime = text("00:00", 12f, R.color.xingyue_text_secondary)
        whiteNoiseTotalTime = text("10:00:00", 12f, R.color.xingyue_text_secondary).apply {
            gravity = Gravity.END
        }
        controlCard.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(
                    whiteNoiseCurrentTime,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    whiteNoiseTotalTime,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
            }
        )

        val volumeValue = text("72%", 12f, R.color.xingyue_text_secondary).apply {
            gravity = Gravity.END
        }
        controlCard.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    text("音量", 14f, R.color.xingyue_text_primary, bold = true),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(volumeValue, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
            }.withTopMargin(18)
        )
        controlCard.addView(
            SeekBar(activity).apply {
                max = 100
                progress = 72
                splitTrack = false
                progressDrawable = drawable(R.drawable.bg_music_progress)
                thumb = drawable(R.drawable.bg_white_noise_progress_thumb)
                contentDescription = "白噪音音量"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        value: Int,
                        fromUser: Boolean
                    ) {
                        volumeValue.text = "$value%"
                        player.setVolume(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
                })
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        )
        controlCard.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text("循环播放", 14f, R.color.xingyue_text_primary, bold = true))
                        addView(text(
                            "持续陪伴，不打断安静",
                            12f,
                            R.color.xingyue_text_secondary
                        ).withTopMargin(3))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(
                    SwitchCompat(activity).apply {
                        isChecked = true
                        contentDescription = "循环播放白噪音"
                        thumbTintList =
                            activity.getColorStateList(R.color.xingyue_primary_blue)
                        trackTintList =
                            activity.getColorStateList(R.color.xingyue_primary_blue_light)
                        setOnCheckedChangeListener { _, checked ->
                            player.setLooping(checked)
                        }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(48)
                    )
                )
            }.withTopMargin(8)
        )
        content.addView(controlCard.withTopMargin(18))

        content.addView(sectionTitle("定时停止").withTopMargin(22))
        content.addView(text(
            "到时间后会轻轻暂停，不影响其他放松功能",
            12f,
            R.color.xingyue_text_secondary
        ).withTopMargin(4))
        val timerButtons = mutableListOf<TextView>()
        val timerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(15 to "15 分钟", 30 to "30 分钟", 60 to "60 分钟", 0 to "不定时")
            .forEachIndexed { index, option ->
                val timerButton = text(
                    option.second,
                    12f,
                    R.color.xingyue_primary_blue_deep,
                    bold = true
                ).apply {
                    gravity = Gravity.CENTER
                    background = drawable(R.drawable.bg_white_noise_timer_chip)
                    isSelected = option.first == 0
                    isClickable = true
                    isFocusable = true
                    contentDescription = "定时${option.second}"
                    setOnClickListener {
                        whiteNoiseTimerMinutes = option.first
                        timerButtons.forEach { button -> button.isSelected = button === this }
                        cancelWhiteNoiseTimer(updateText = false)
                        whiteNoiseTimerStatus?.text = if (option.first == 0) {
                            "当前不定时"
                        } else if (player.isPlaying) {
                            scheduleWhiteNoiseTimer()
                            "将在 ${option.first} 分钟后停止"
                        } else {
                            "播放后将在 ${option.first} 分钟后停止"
                        }
                    }
                    attachPressFeedback(this)
                }
                timerButtons += timerButton
                timerRow.addView(
                    timerButton,
                    LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        marginStart = if (index == 0) 0 else dp(4)
                        marginEnd = if (index == 3) 0 else dp(4)
                    }
                )
            }
        content.addView(timerRow.withTopMargin(12))
        whiteNoiseTimerStatus = text(
            "当前不定时",
            12f,
            R.color.xingyue_text_secondary
        ).apply { gravity = Gravity.CENTER }
        content.addView(whiteNoiseTimerStatus!!.withTopMargin(9))

        content.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = drawable(R.drawable.bg_relax_card_mint)
                setPadding(dp(18), dp(17), dp(18), dp(17))
                addView(text(
                    "闭上眼睛，跟着声音，把注意力轻轻放回呼吸里。",
                    14f,
                    R.color.xingyue_text_primary,
                    bold = true
                ).apply {
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(3).toFloat(), 1f)
                })
                addView(text(
                    "这段声音会陪你慢慢安静下来。",
                    12f,
                    R.color.xingyue_text_secondary
                ).apply { gravity = Gravity.CENTER }.withTopMargin(6))
            }.withTopMargin(18)
        )

        player.listener = object : WhiteNoisePlayerController.Listener {
            override fun onPrepared(durationMs: Int) {
                whiteNoiseProgress?.max = durationMs.coerceAtLeast(1)
                whiteNoiseTotalTime?.text = formatWhiteNoiseTime(durationMs)
                updateWhiteNoiseProgress()
            }

            override fun onPlaybackChanged(isPlaying: Boolean) {
                whiteNoisePlayButton?.setImageResource(
                    if (isPlaying) R.drawable.ic_music_pause else R.drawable.ic_music_play
                )
                whiteNoisePlayButton?.contentDescription =
                    if (isPlaying) "暂停白噪音" else "播放白噪音"
                whiteNoiseStatusText?.text = if (isPlaying) "暂停一下" else {
                    if (player.currentPosition > 0) "继续播放" else "开始小憩"
                }
                whiteNoiseRipple?.setPlaying(isPlaying)
                handler.removeCallbacks(whiteNoiseProgressTick)
                if (isPlaying) {
                    handler.post(whiteNoiseProgressTick)
                    scheduleWhiteNoiseTimer()
                    if (!whiteNoiseRecordSaved) {
                        whiteNoiseRecordSaved = true
                        saveRecord(
                            "white_noise",
                            "白噪音小憩",
                            0,
                            "完成了一次白噪音小憩"
                        )
                    }
                }
            }

            override fun onError(message: String) {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                whiteNoiseRipple?.setPlaying(false)
                whiteNoiseStatusText?.text = "暂时无法播放"
            }
        }
        showFeatureContent(content)
        player.prepare()
    }

    private fun updateWhiteNoiseProgress() {
        val player = whiteNoisePlayer ?: return
        if (!whiteNoiseUserSeeking) {
            whiteNoiseProgress?.max = player.duration.coerceAtLeast(1)
            whiteNoiseProgress?.progress = player.currentPosition
            whiteNoiseCurrentTime?.text = formatWhiteNoiseTime(player.currentPosition)
        }
    }

    private fun scheduleWhiteNoiseTimer() {
        cancelWhiteNoiseTimer(updateText = false)
        val minutes = whiteNoiseTimerMinutes
        if (minutes <= 0 || whiteNoisePlayer?.isPlaying != true) {
            if (minutes <= 0) whiteNoiseTimerStatus?.text = "当前不定时"
            return
        }
        whiteNoiseTimerStatus?.text = "将在 $minutes 分钟后停止"
        whiteNoiseTimer = object : CountDownTimer(minutes * 60_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remainingMinutes = ((millisUntilFinished + 59_999L) / 60_000L)
                    .coerceAtLeast(1L)
                whiteNoiseTimerStatus?.text = "将在 $remainingMinutes 分钟后停止"
            }

            override fun onFinish() {
                whiteNoiseTimer = null
                whiteNoisePlayer?.pause()
                whiteNoiseTimerStatus?.text = "定时已结束，声音已暂停"
                Toast.makeText(activity, "白噪音已按时暂停", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun cancelWhiteNoiseTimer(updateText: Boolean = true) {
        whiteNoiseTimer?.cancel()
        whiteNoiseTimer = null
        if (updateText) {
            whiteNoiseTimerStatus?.text = if (whiteNoiseTimerMinutes > 0) {
                "定时已暂停"
            } else {
                "当前不定时"
            }
        }
    }

    private fun pauseWhiteNoise(cancelTimer: Boolean) {
        handler.removeCallbacks(whiteNoiseProgressTick)
        whiteNoiseRipple?.setPlaying(false)
        whiteNoisePlayer?.pause()
        if (cancelTimer) cancelWhiteNoiseTimer()
    }

    fun release() {
        pauseWhiteNoise(cancelTimer = true)
        whiteNoisePlayer?.release()
        whiteNoisePlayer = null
        whiteNoiseRipple = null
        whiteNoisePlayButton = null
        whiteNoiseProgress = null
    }

    private fun showBreathing() {
        pageState = PageState.BREATHING
        breathingRunning = false
        breathingRemainingMs = BREATHING_TOTAL_MS
        breathingPhaseCursorMs = 0L
        breathingLastPhase = ""
        val content = featureContent("呼吸放松", "跟随圆球，让呼吸慢慢变得绵长")

        breathingBall = TextView(activity).apply {
            background = drawable(R.drawable.bg_relax_ball)
            gravity = Gravity.CENTER
            text = "准备"
            textSize = 18f
            setTextColor(activity.getColor(android.R.color.white))
            setTypeface(typeface, Typeface.BOLD)
        }
        content.addView(breathingBall, LinearLayout.LayoutParams(dp(168), dp(168)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(30)
        })
        breathingPhaseText = text("准备开始", 22f, R.color.xingyue_text_primary, bold = true).apply {
            gravity = Gravity.CENTER
        }
        breathingHintText = text("找到舒服的姿势，不需要刻意用力", 14f, R.color.xingyue_text_secondary).apply {
            gravity = Gravity.CENTER
        }
        breathingTimerText = text("03:00", 30f, R.color.xingyue_primary_blue_deep, bold = true).apply {
            gravity = Gravity.CENTER
        }
        content.addView(breathingPhaseText!!.withTopMargin(24))
        content.addView(breathingHintText!!.withTopMargin(7))
        content.addView(breathingTimerText!!.withTopMargin(20))

        breathingToggleButton = primaryButton("开始") { toggleBreathing() }
        content.addView(breathingToggleButton!!.withTopMargin(22))
        content.addView(secondaryButton("结束练习") { finishBreathing(completed = false) }.withTopMargin(10))
        showFeatureContent(content)
    }

    private fun toggleBreathing() {
        if (breathingRunning) {
            pauseBreathing()
        } else {
            breathingRunning = true
            breathingLastTickMs = SystemClock.elapsedRealtime()
            breathingToggleButton?.text = "暂停"
            updateBreathingPhase(force = true)
            handler.post(breathingTick)
        }
    }

    private fun pauseBreathing() {
        if (!breathingRunning) return
        breathingRunning = false
        handler.removeCallbacks(breathingTick)
        breathingBall?.animate()?.cancel()
        breathingToggleButton?.text = "继续"
    }

    private fun updateBreathingPhase(force: Boolean = false) {
        val (phase, hint, targetScale, duration) = when {
            breathingPhaseCursorMs < 4_000L -> Phase("吸气", "慢慢吸气，给自己一点空间", 1.32f, 4_000L)
            breathingPhaseCursorMs < 6_000L -> Phase("停顿", "停一下，感受此刻", 1.32f, 180L)
            else -> Phase("呼气", "慢慢呼出，把紧绷放下", 0.82f, 6_000L)
        }
        breathingPhaseText?.text = phase
        breathingHintText?.text = hint
        (breathingBall as? TextView)?.text = phase
        if (force || phase != breathingLastPhase) {
            breathingLastPhase = phase
            breathingBall?.animate()?.cancel()
            breathingBall?.animate()
                ?.scaleX(targetScale)
                ?.scaleY(targetScale)
                ?.setDuration(duration)
                ?.start()
        }
    }

    private fun finishBreathing(completed: Boolean) {
        pauseBreathing()
        val elapsedSeconds = ((BREATHING_TOTAL_MS - breathingRemainingMs) / 1000L).toInt()
        if (elapsedSeconds >= 5) {
            saveRecord(
                "breathing",
                "呼吸放松",
                elapsedSeconds,
                if (completed) "完成了 3 分钟放松" else "完成了一段呼吸练习"
            )
        }
        showResult(
            "呼吸慢下来了",
            if (completed) "你完成了一次 3 分钟放松。" else "这段短暂停留也很有价值。",
            "呼吸放松 · ${formatDuration(elapsedSeconds)}"
        )
    }

    private fun showGame(mode: RelaxPlayView.Mode) {
        pageState = if (mode == RelaxPlayView.Mode.BUBBLES) PageState.BUBBLE_GAME else PageState.STAR_GAME
        gameCount = 0
        gameStartTime = SystemClock.elapsedRealtime()
        val isBubble = mode == RelaxPlayView.Mode.BUBBLES
        val content = featureContent(
            if (isBubble) "水母泡泡" else "星星收集",
            if (isBubble) "轻点漂浮泡泡，让紧绷慢慢散开" else "收集十颗温柔星光"
        )
        val infoRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            val counter = text(if (isBubble) "消除 0" else "星光 0 / 10", 15f, R.color.xingyue_primary_blue_deep, bold = true)
            val timer = text("00:45", 15f, R.color.xingyue_text_secondary, bold = true).apply {
                gravity = Gravity.END
            }
            addView(counter, LinearLayout.LayoutParams(0, dp(44), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            addView(timer, LinearLayout.LayoutParams(0, dp(44), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            tag = Pair(counter, timer)
        }
        content.addView(infoRow.withTopMargin(18))
        val gameView = RelaxPlayView(activity).apply {
            background = drawable(R.drawable.bg_relax_hero)
            onItemCollected = {
                gameCount++
                val pair = infoRow.tag as Pair<*, *>
                (pair.first as TextView).text =
                    if (isBubble) "消除 $gameCount" else "星光 $gameCount / 10"
                if (!isBubble && gameCount >= 10) finishGame(mode)
            }
        }
        playView = gameView
        content.addView(gameView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(360)
        ).apply { topMargin = dp(8) })
        content.addView(secondaryButton("结束本次放松") { finishGame(mode) }.withTopMargin(12))
        showFeatureContent(content)
        gameView.start(mode)
        gameTimer?.cancel()
        gameTimer = object : CountDownTimer(GAME_DURATION_MS, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                val pair = infoRow.tag as Pair<*, *>
                (pair.second as TextView).text = formatDuration((millisUntilFinished / 1000L).toInt())
            }

            override fun onFinish() {
                finishGame(mode)
            }
        }.start()
    }

    private fun finishGame(mode: RelaxPlayView.Mode) {
        val wasRunning = gameTimer != null || playView != null
        val elapsed = ((SystemClock.elapsedRealtime() - gameStartTime) / 1000L).toInt().coerceAtLeast(1)
        stopGame()
        if (!wasRunning) return
        val isBubble = mode == RelaxPlayView.Mode.BUBBLES
        saveRecord(
            if (isBubble) "bubble" else "star",
            if (isBubble) "水母泡泡" else "星星收集",
            elapsed,
            if (isBubble) "消除了 $gameCount 个泡泡" else "收集了 $gameCount 颗星光"
        )
        showResult(
            if (isBubble) "泡泡轻轻散开了" else "温柔星光已收好",
            if (isBubble) "你消除了 $gameCount 个泡泡，给心情腾出了一点空间。"
            else "你收集了 $gameCount 颗星光，今日心情能量 +1。",
            "${if (isBubble) "水母泡泡" else "星星收集"} · ${formatDuration(elapsed)}"
        )
    }

    private fun stopGame() {
        gameTimer?.cancel()
        gameTimer = null
        playView?.stop()
        playView = null
    }

    private fun showClassicGame(game: ClassicGame) {
        releaseClassicGame()
        pageState = PageState.CLASSIC_GAME
        currentClassicGame = game

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            // The shell navigation floats above page content, so reserve its
            // touch-safe area without shrinking the game controls themselves.
            setPadding(dp(16), dp(18), dp(16), dp(92))
            addView(header(game.name) { showHome() })
            addView(
                text(game.instructions, 12f, R.color.xingyue_text_secondary).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                }.withTopMargin(5)
            )
        }

        val scoreCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = drawable(R.drawable.bg_relax_card)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            classicScoreText = gameStat("0", "当前分数")
            classicHighScoreText = gameStat(
                storage.getGameHighScore(game.type).toString(),
                "最高分"
            )
            addView(
                classicScoreText!!.parent as View,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                classicHighScoreText!!.parent as View,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        content.addView(scoreCard.withTopMargin(12))

        val gameView = when (game.type) {
            GAME_SNAKE -> SnakeGameView(activity)
            GAME_2048 -> Game2048View(activity)
            GAME_TETRIS -> TetrisGameView(activity)
            else -> PinballGameView(activity)
        }
        classicGameView = gameView
        gameView.background = drawable(R.drawable.bg_relax_hero)
        gameView.onScoreChanged = { value ->
            classicScoreText?.text = value.toString()
            classicHighScoreText?.text =
                maxOf(value, storage.getGameHighScore(game.type)).toString()
        }
        gameView.onGameOver = { score -> completeClassicGame(game, score) }
        content.addView(
            gameView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(8)
            }
        )

        if (game.type == GAME_TETRIS) {
            content.addView(createTetrisControls(gameView as TetrisGameView))
        }

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        classicPauseButton = compactButton("暂停") { toggleClassicPause() }
        actions.addView(
            classicPauseButton,
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) }
        )
        actions.addView(
            compactButton("重新开始") { restartClassicGame() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(5)
                marginEnd = dp(5)
            }
        )
        actions.addView(
            compactButton("退出") { showHome() },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) }
        )
        content.addView(actions.withTopMargin(4))

        showClassicGameContent(content)
        gameView.restartGame()
    }

    private fun createTetrisControls(game: TetrisGameView): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(gameIconButton(R.drawable.ic_game_arrow, "左移", rotation = 180f) {
                game.moveLeft()
            })
            addView(gameIconButton(R.drawable.ic_game_rotate, "旋转") {
                game.rotatePiece()
            }.withHorizontalMargin(7))
            addView(gameIconButton(R.drawable.ic_game_arrow, "右移") {
                game.moveRight()
            })
            addView(gameIconButton(R.drawable.ic_game_arrow, "加速下落", rotation = 90f) {
                game.softDrop()
            }.withStartMargin(7))
        }

    private fun gameIconButton(
        iconRes: Int,
        description: String,
        rotation: Float = 0f,
        onClick: () -> Unit
    ): ImageView = ImageView(activity).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        background = drawable(R.drawable.bg_music_control_button)
        setPadding(dp(13), dp(13), dp(13), dp(13))
        setImageResource(iconRes)
        this.rotation = rotation
        contentDescription = description
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        attachPressFeedback(this)
    }

    private fun toggleClassicPause() {
        val game = classicGameView ?: return
        if (game.isGameOver) return
        if (game.isPaused) {
            game.resumeGame()
            classicPauseButton?.text = "暂停"
        } else {
            game.pauseGame()
            classicPauseButton?.text = "继续"
        }
    }

    private fun pauseClassicGame() {
        val game = classicGameView ?: return
        if (!game.isPaused && !game.isGameOver) {
            game.pauseGame()
            classicPauseButton?.text = "继续"
        }
    }

    private fun restartClassicGame() {
        classicGameView?.restartGame()
        classicPauseButton?.text = "暂停"
        classicScoreText?.text = "0"
    }

    private fun completeClassicGame(game: ClassicGame, score: Int) {
        val highScore = storage.updateGameHighScore(game.type, score)
        storage.saveGameRecord(
            RelaxGameRecord(
                id = System.currentTimeMillis(),
                gameType = game.type,
                gameName = game.name,
                score = score,
                createdAt = System.currentTimeMillis()
            )
        )
        classicHighScoreText?.text = highScore.toString()
        classicPauseButton?.text = "继续"
        refreshRecentGames()

        MaterialAlertDialogBuilder(activity)
            .setTitle(game.resultTitle)
            .setMessage("${game.resultText(score)}\n\n本次 $score 分 · 最高 $highScore 分")
            .setCancelable(false)
            .setNegativeButton("返回放松页") { _, _ -> showHome() }
            .setPositiveButton("再来一次") { _, _ -> restartClassicGame() }
            .show()
    }

    private fun releaseClassicGame() {
        classicGameView?.releaseGame()
        classicGameView = null
        currentClassicGame = null
        classicScoreText = null
        classicHighScoreText = null
        classicPauseButton = null
    }

    private fun showClassicGameContent(content: LinearLayout) {
        featurePanel.removeAllViews()
        featurePanel.addView(content, matchParentParams())
        showOnly(featurePanel)
    }

    private fun showBottle() {
        pageState = PageState.BOTTLE
        val content = featureContent("心情漂流瓶", "写下一句想暂时放下的心情")
        val bottle = ImageView(activity).apply {
            background = drawable(R.drawable.bg_relax_badge)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setImageResource(R.drawable.ic_relax_bottle)
            contentDescription = "心情漂流瓶"
        }
        content.addView(bottle, LinearLayout.LayoutParams(dp(76), dp(76)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(28)
        })
        val input = EditText(activity).apply {
            minHeight = dp(128)
            gravity = Gravity.TOP or Gravity.START
            background = drawable(R.drawable.bg_relax_input)
            hint = "写下一句想暂时放下的心情"
            setHintTextColor(activity.getColor(R.color.xingyue_text_hint))
            setTextColor(activity.getColor(R.color.xingyue_text_primary))
            textSize = 15f
            maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        content.addView(input.withTopMargin(24))
        content.addView(text(
            "文字不会上传，也不会发送给 AI；本次练习只记录完成状态。",
            12f,
            R.color.xingyue_text_secondary
        ).withTopMargin(9))
        content.addView(primaryButton("放进漂流瓶") {
            if (input.text.toString().trim().isEmpty()) {
                Toast.makeText(activity, "先写下一点此刻的感受吧", Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            val keyboard = activity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(input.windowToken, 0)
            input.clearFocus()
            bottle.animate().cancel()
            bottle.animate()
                .translationX(dp(90).toFloat())
                .translationY(-dp(54).toFloat())
                .alpha(0f)
                .setDuration(900L)
                .withEndAction {
                    saveRecord("bottle", "心情漂流瓶", 0, "写下了一句话")
                    showResult(
                        "这句话已被温柔收好",
                        "你不需要立刻解决所有感受，先放下一小会也可以。",
                        "心情漂流瓶 · 仅本地完成记录"
                    )
                }
                .start()
        }.withTopMargin(20))
        showFeatureContent(content)
    }

    private fun showBodyScan() {
        pageState = PageState.BODY_SCAN
        val steps = listOf(
            "放松肩颈" to "让肩膀慢慢下沉，松开下颌。",
            "感受呼吸" to "不改变节奏，只感受空气轻轻进出。",
            "放松手臂" to "感受手臂的重量，把手指也松开。",
            "感受身体重量" to "让座椅或床面稳稳承托住你。",
            "回到当下" to "听一听周围的声音，再慢慢睁开眼睛。"
        )
        var index = 0
        val content = featureContent("身体扫描", "五个小步骤，带身体回到当下")
        val stepLabel = chip("第 1 / 5 步")
        val title = text(steps.first().first, 24f, R.color.xingyue_text_primary, bold = true)
        val body = text(steps.first().second, 16f, R.color.xingyue_text_secondary).apply {
            setLineSpacing(dp(5).toFloat(), 1f)
        }
        content.addView(stepLabel.withTopMargin(28))
        content.addView(title.withTopMargin(20))
        content.addView(body.withTopMargin(10))
        lateinit var next: TextView
        next = primaryButton("下一步") {
            if (index == steps.lastIndex) {
                saveRecord("body_scan", "身体扫描", 100, "完成了五步身体扫描")
                showResult(
                    "身体慢慢松开了",
                    "你完成了一次轻量身体扫描，试着把这份松弛留久一点。",
                    "身体扫描 · 约 2 分钟"
                )
            } else {
                index++
                stepLabel.text = "第 ${index + 1} / ${steps.size} 步"
                title.animate().cancel()
                title.alpha = 0f
                title.translationY = dp(8).toFloat()
                title.text = steps[index].first
                body.text = steps[index].second
                title.animate().alpha(1f).translationY(0f).setDuration(180L).start()
                if (index == steps.lastIndex) next.text = "完成扫描"
            }
        }
        content.addView(next.withTopMargin(34))
        showFeatureContent(content)
    }

    private fun createResultPanel(): ScrollView {
        val scroll = ScrollView(activity).apply {
            visibility = View.GONE
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        scroll.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(26), dp(20), dp(30))
            addView(header("完成一次放松") { showHome() })
            addView(ImageView(activity).apply {
                background = drawable(R.drawable.bg_relax_badge)
                setPadding(dp(20), dp(20), dp(20), dp(20))
                setImageResource(R.drawable.ic_relax_star)
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(88), dp(88)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(44)
            })
            addView(text("", 24f, R.color.xingyue_text_primary, bold = true).apply {
                id = View.generateViewId()
                tag = RESULT_TITLE_TAG
                gravity = Gravity.CENTER
            }.withTopMargin(24))
            addView(text("", 15f, R.color.xingyue_text_secondary).apply {
                tag = RESULT_BODY_TAG
                gravity = Gravity.CENTER
                setLineSpacing(dp(4).toFloat(), 1f)
            }.withTopMargin(10))
            addView(chip("").apply { tag = RESULT_META_TAG }.withTopMargin(18))
            addView(primaryButton("再来一次呼吸放松") { showBreathing() }.withTopMargin(34))
            addView(secondaryButton("返回放松首页") { showHome() }.withTopMargin(10))
        }, matchWidthWrapParams())
        return scroll
    }

    private fun showResult(title: String, body: String, meta: String) {
        pageState = PageState.RESULT
        findTaggedText(resultPanel, RESULT_TITLE_TAG)?.text = title
        findTaggedText(resultPanel, RESULT_BODY_TAG)?.text = body
        findTaggedText(resultPanel, RESULT_META_TAG)?.text = meta
        showOnly(resultPanel)
    }

    private fun featureContent(title: String, subtitle: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
            addView(header(title) { showHome() })
            addView(text(subtitle, 14f, R.color.xingyue_text_secondary).apply {
                gravity = Gravity.CENTER
            }.withTopMargin(8))
        }

    private fun showFeatureContent(content: LinearLayout) {
        featurePanel.removeAllViews()
        featurePanel.addView(ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content, matchWidthWrapParams())
        }, matchParentParams())
        showOnly(featurePanel)
    }

    private fun header(title: String, onBack: () -> Unit): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(activity).apply {
                background = drawable(R.drawable.bg_assessment_icon_button)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setImageResource(R.drawable.ic_arrow_back)
                imageTintList = activity.getColorStateList(R.color.xingyue_text_primary)
                contentDescription = "返回放松首页"
                isClickable = true
                isFocusable = true
                setOnClickListener { onBack() }
                attachPressFeedback(this)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(text(title, 20f, R.color.xingyue_text_primary, bold = true).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(48)
            })
        }

    private fun refreshHome() {
        if (!::todayDurationText.isInitialized) return
        val todayRecords = storage.load().filter { isToday(it.createdAt) }
        val totalSeconds = todayRecords.sumOf { it.durationSeconds }
        todayDurationText.text = if (totalSeconds < 60) "${totalSeconds} 秒" else "${totalSeconds / 60} 分钟"
        todayCountText.text = "${todayRecords.size} 次"
        todayEnergyText.text = when {
            todayRecords.size >= 3 -> "今天已经好好照顾自己了"
            todayRecords.isNotEmpty() -> "柔和恢复中"
            else -> "等待一小段恢复时间"
        }
        todayRecommendText.text = "今日推荐：${todayRecords.firstOrNull()?.title ?: "呼吸放松"}"
        recentContainer.removeAllViews()
        val records = storage.load().take(3)
        if (records.isEmpty()) {
            recentContainer.addView(
                recentCard("还没有放松记录", "完成一次练习后，会在这里留下仅属于你的本地记录。", 0)
            )
        } else {
            records.forEachIndexed { index, record ->
                val duration = if (record.durationSeconds > 0) " · ${formatDuration(record.durationSeconds)}" else ""
                recentContainer.addView(
                    recentCard(record.title, "${record.resultText}$duration · ${formatTime(record.createdAt)}", index)
                )
            }
        }
        refreshRecentGames()
    }

    private fun refreshRecentGames() {
        if (!::recentGameContainer.isInitialized) return
        recentGameContainer.removeAllViews()
        val records = storage.loadGameRecords().take(3)
        if (records.isEmpty()) {
            recentGameContainer.addView(
                recentCard(
                    "还没有游戏记录",
                    "玩一局经典小游戏后，最高分会只保存在当前设备。",
                    0
                )
            )
        } else {
            records.forEachIndexed { index, record ->
                recentGameContainer.addView(
                    recentCard(
                        record.gameName,
                        "${record.score} 分 · ${formatTime(record.createdAt)}",
                        index
                    )
                )
            }
        }
    }

    private fun recentCard(title: String, subtitle: String, index: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = drawable(R.drawable.bg_relax_card)
            setPadding(dp(17), dp(15), dp(17), dp(15))
            addView(text(title, 15f, R.color.xingyue_text_primary, bold = true))
            addView(text(subtitle, 12f, R.color.xingyue_text_secondary).withTopMargin(6))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = if (index == 0) dp(10) else dp(8) }
        }

    private fun saveRecord(
        type: String,
        title: String,
        durationSeconds: Int,
        resultText: String
    ) {
        storage.save(
            RelaxRecord(
                id = System.currentTimeMillis(),
                type = type,
                title = title,
                durationSeconds = durationSeconds,
                resultText = resultText,
                createdAt = System.currentTimeMillis()
            )
        )
        refreshHome()
    }

    private fun showHome(animate: Boolean = true) {
        pauseBreathing()
        stopGame()
        releaseClassicGame()
        pauseWhiteNoise(cancelTimer = true)
        pageState = PageState.HOME
        refreshHome()
        homePanel.scrollTo(0, 0)
        showOnly(homePanel, animate)
    }

    private fun showOnly(target: View, animate: Boolean = true) {
        listOf(homePanel, featurePanel, resultPanel).forEach {
            it.visibility = if (it === target) View.VISIBLE else View.GONE
        }
        if (animate) {
            target.animate().cancel()
            target.alpha = 0f
            target.translationY = dp(8).toFloat()
            target.animate().alpha(1f).translationY(0f).setDuration(200L).start()
        } else {
            target.alpha = 1f
            target.translationY = 0f
        }
    }

    private fun currentPanel(): View = when (pageState) {
        PageState.HOME -> homePanel
        PageState.RESULT -> resultPanel
        else -> featurePanel
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView =
        button(label, R.drawable.bg_relax_primary_button, android.R.color.white, onClick)

    private fun secondaryButton(label: String, onClick: () -> Unit): TextView =
        button(label, R.drawable.bg_relax_secondary_button, R.color.xingyue_primary_blue_deep, onClick)

    private fun compactButton(label: String, onClick: () -> Unit): TextView =
        text(label, 13f, R.color.xingyue_primary_blue_deep, bold = true).apply {
            background = drawable(R.drawable.bg_relax_secondary_button)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            attachPressFeedback(this)
        }

    private fun button(
        label: String,
        backgroundRes: Int,
        textColorRes: Int,
        onClick: () -> Unit
    ): TextView = text(label, 15f, textColorRes, bold = true).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        background = drawable(backgroundRes)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        attachPressFeedback(this)
    }

    private fun chip(label: String): TextView =
        text(label, 12f, R.color.xingyue_primary_blue_deep, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34)
            )
            background = drawable(R.drawable.bg_assessment_label)
            gravity = Gravity.CENTER
            setPadding(dp(13), 0, dp(13), 0)
        }

    private fun sectionTitle(label: String): TextView =
        text(label, 18f, R.color.xingyue_text_primary, bold = true)

    private fun text(label: String, size: Float, colorRes: Int, bold: Boolean = false): TextView =
        TextView(activity).apply {
            text = label
            textSize = size
            setTextColor(activity.getColor(colorRes))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun drawable(resId: Int) = ContextCompat.getDrawable(activity, resId)

    private fun attachPressFeedback(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    target.animate().cancel()
                    target.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    target.animate().cancel()
                    target.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                }
            }
            false
        }
    }

    private fun findTaggedText(rootView: View, targetTag: String): TextView? {
        if (rootView is TextView && rootView.tag == targetTag) return rootView
        if (rootView is ViewGroup) {
            for (index in 0 until rootView.childCount) {
                findTaggedText(rootView.getChildAt(index), targetTag)?.let { return it }
            }
        }
        return null
    }

    private fun View.withTopMargin(value: Int): View = apply {
        val current = layoutParams as? LinearLayout.LayoutParams
        layoutParams = (current ?: LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).apply { topMargin = dp(value) }
    }

    private fun View.withHorizontalMargin(value: Int): View = apply {
        val current = layoutParams as? LinearLayout.LayoutParams
        layoutParams = (current ?: LinearLayout.LayoutParams(dp(48), dp(48))).apply {
            marginStart = dp(value)
            marginEnd = dp(value)
        }
    }

    private fun View.withStartMargin(value: Int): View = apply {
        val current = layoutParams as? LinearLayout.LayoutParams
        layoutParams = (current ?: LinearLayout.LayoutParams(dp(48), dp(48))).apply {
            marginStart = dp(value)
        }
    }

    private fun matchParentParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    private fun matchWidthWrapParams() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()

    private fun formatDuration(seconds: Int): String =
        String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60)

    private fun formatWhiteNoiseTime(milliseconds: Int): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0) / 1_000)
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.CHINA, "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatTime(time: Long): String =
        SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(time))

    private fun isToday(time: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = time }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    private data class Feature(
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val backgroundRes: Int,
        val state: PageState?
    )

    private fun classicGames(): List<ClassicGame> = listOf(
        ClassicGame(
            type = GAME_SNAKE,
            name = "贪吃蛇",
            subtitle = "轻轻移动，找回一点专注",
            instructions = "上下左右滑动控制方向，收集星光并避开边界",
            iconRes = R.drawable.ic_relax_snake,
            backgroundRes = R.drawable.bg_relax_card,
            resultTitle = "星光收集结束",
            resultText = { score -> "这次收集了 ${score / 10} 颗小星光，要不要再来一次？" }
        ),
        ClassicGame(
            type = GAME_2048,
            name = "2048",
            subtitle = "慢慢合成，让思绪安静下来",
            instructions = "上下左右滑动，相同数字会在移动方向上合并",
            iconRes = R.drawable.ic_relax_2048,
            backgroundRes = R.drawable.bg_relax_card_purple,
            resultTitle = "这一局整理完成",
            resultText = { score -> "你整理出了 $score 分的清爽感。" }
        ),
        ClassicGame(
            type = GAME_TETRIS,
            name = "俄罗斯方块",
            subtitle = "整理方块，也整理一下心情",
            instructions = "点击旋转、滑动移动，也可以使用下方图标控制",
            iconRes = R.drawable.ic_relax_tetris,
            backgroundRes = R.drawable.bg_relax_card_mint,
            resultTitle = "方块轻轻落定",
            resultText = { _ -> "你消除了一点点压力，也为自己腾出了一点空间。" }
        ),
        ClassicGame(
            type = GAME_PINBALL,
            name = "二维弹球",
            subtitle = "看小球跳动，释放一点压力",
            instructions = "左右拖动底部挡板，不要让蓝白小球掉下去",
            iconRes = R.drawable.ic_relax_pinball,
            backgroundRes = R.drawable.bg_relax_card_pink,
            resultTitle = "小球暂时休息啦",
            resultText = { score -> "小球带回了 $score 分星光，也帮你放松了一点点。" }
        )
    )

    private data class ClassicGame(
        val type: String,
        val name: String,
        val subtitle: String,
        val instructions: String,
        val iconRes: Int,
        val backgroundRes: Int,
        val resultTitle: String,
        val resultText: (Int) -> String
    )

    private data class Phase(
        val label: String,
        val hint: String,
        val scale: Float,
        val duration: Long
    )

    companion object {
        private const val BREATHING_TOTAL_MS = 180_000L
        private const val BREATHING_CYCLE_MS = 12_000L
        private const val GAME_DURATION_MS = 45_000L
        private const val GAME_SNAKE = "snake"
        private const val GAME_2048 = "game_2048"
        private const val GAME_TETRIS = "tetris"
        private const val GAME_PINBALL = "pinball"
        private const val RESULT_TITLE_TAG = "relax_result_title"
        private const val RESULT_BODY_TAG = "relax_result_body"
        private const val RESULT_META_TAG = "relax_result_meta"
    }
}
