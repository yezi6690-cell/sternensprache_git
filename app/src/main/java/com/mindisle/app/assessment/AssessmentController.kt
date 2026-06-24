package com.mindisle.app.assessment

import android.animation.ValueAnimator
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mindisle.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AssessmentController(
    private val activity: AppCompatActivity,
    private val onTalkRequested: () -> Unit
) {
    private enum class PageState {
        HOME,
        QUESTION,
        RESULT,
        HISTORY
    }

    private val storage = AssessmentStorage(activity)
    private val homePanel: ScrollView = activity.findViewById(R.id.assessment_home_panel)
    private val questionPanel: ScrollView = activity.findViewById(R.id.assessment_question_panel)
    private val resultPanel: ScrollView = activity.findViewById(R.id.assessment_result_panel)
    private val historyPanel: ScrollView = activity.findViewById(R.id.assessment_history_panel)
    private val optionLabels = listOf("几乎没有", "偶尔", "有时", "经常", "几乎一直")

    private var pageState = PageState.HOME
    private var currentDefinition: AssessmentDefinition = AssessmentCatalog.definitions.first()
    private var currentQuestionIndex = 0
    private var answers = IntArray(0) { -1 }
    private var currentRecord: AssessmentRecord? = null

    fun bind() {
        buildAssessmentCards()
        activity.findViewById<View>(R.id.assessment_start_button).setOnClickListener {
            startAssessment(
                AssessmentCatalog.find(COMPREHENSIVE_TYPE)
                    ?: AssessmentCatalog.definitions.first()
            )
        }
        activity.findViewById<View>(R.id.assessment_history_button).setOnClickListener {
            showHistory()
        }
        activity.findViewById<View>(R.id.assessment_today_report_button).setOnClickListener {
            storage.load().firstOrNull {
                it.type == COMPREHENSIVE_TYPE && isToday(it.createdAt)
            }?.let {
                showResult(it, animateScore = false)
            }
        }
        activity.findViewById<View>(R.id.assessment_question_back).setOnClickListener {
            requestAbandonAssessment()
        }
        activity.findViewById<View>(R.id.assessment_result_back).setOnClickListener {
            showHome()
        }
        activity.findViewById<View>(R.id.assessment_history_back).setOnClickListener {
            showHome()
        }
        activity.findViewById<View>(R.id.assessment_previous_button).setOnClickListener {
            if (currentQuestionIndex > 0) {
                currentQuestionIndex--
                renderQuestion(forward = false)
            }
        }
        activity.findViewById<View>(R.id.assessment_next_button).setOnClickListener {
            if (answers.getOrNull(currentQuestionIndex) == -1) {
                Toast.makeText(activity, "先选择一个最接近的感受吧", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (currentQuestionIndex < currentDefinition.questions.lastIndex) {
                currentQuestionIndex++
                renderQuestion(forward = true)
            } else {
                finishAssessment()
            }
        }
        activity.findViewById<View>(R.id.assessment_talk_button).setOnClickListener {
            onTalkRequested()
        }
        activity.findViewById<View>(R.id.assessment_retry_button).setOnClickListener {
            startAssessment(currentDefinition)
        }
        activity.findViewById<View>(R.id.assessment_save_button).setOnClickListener {
            Toast.makeText(activity, "报告已保存在当前设备", Toast.LENGTH_SHORT).show()
        }
        activity.findViewById<View>(R.id.assessment_home_button).setOnClickListener {
            showHome()
        }
        bindPressFeedback(
            R.id.assessment_start_button,
            R.id.assessment_history_button,
            R.id.assessment_question_back,
            R.id.assessment_result_back,
            R.id.assessment_history_back,
            R.id.assessment_previous_button,
            R.id.assessment_next_button,
            R.id.assessment_talk_button,
            R.id.assessment_retry_button,
            R.id.assessment_today_report_button,
            R.id.assessment_save_button,
            R.id.assessment_home_button
        )
        showHome(animate = false)
    }

    fun onVisible() {
        buildAssessmentCards()
        refreshTodayStatus()
        refreshRecentRecords()
        currentPanel().apply {
            animate().cancel()
            alpha = 0f
            translationY = dp(8).toFloat()
            animate().alpha(1f).translationY(0f).setDuration(180L).start()
        }
    }

    fun openHome() {
        showHome(animate = false)
    }

    fun startTodayAssessment(): Boolean =
        startAssessmentByType(COMPREHENSIVE_TYPE)

    fun openRecords() {
        showHistory()
    }

    fun startAssessmentByType(type: String): Boolean {
        val definition = AssessmentCatalog.find(type) ?: return false
        startAssessment(definition)
        return true
    }

    fun openLatestResult(): Boolean {
        val latest = storage.load().maxByOrNull { it.createdAt } ?: return false
        showResult(latest, animateScore = false)
        return true
    }

    fun handleBack(): Boolean {
        if (pageState == PageState.HOME) return false
        if (pageState == PageState.QUESTION) {
            requestAbandonAssessment()
            return true
        }
        showHome()
        return true
    }

    private fun buildAssessmentCards() {
        val grid = activity.findViewById<GridLayout>(R.id.assessment_type_grid)
        val recentRecords = storage.load()
        grid.removeAllViews()
        AssessmentCatalog.definitions
            .filterNot { it.id == COMPREHENSIVE_TYPE }
            .forEachIndexed { index, definition ->
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                minimumHeight = dp(178)
                setPadding(dp(16), dp(16), dp(16), dp(14))
                background = ContextCompat.getDrawable(activity, R.drawable.bg_assessment_card)
                isClickable = true
                isFocusable = true
                contentDescription = "${definition.title}，${definition.questions.size}题"
            }
            val params = GridLayout.LayoutParams().apply {
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
            card.layoutParams = params

            card.addView(ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                // Low-saturation accents distinguish assessment types without changing their logic.
                background = ContextCompat.getDrawable(activity, assessmentIconBadge(definition.id))
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setImageResource(definition.iconRes)
                contentDescription = null
            })
            card.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
                text = definition.title
                textSize = 16f
                setTextColor(activity.getColor(R.color.xingyue_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(5) }
                text = definition.description
                textSize = 12f
                maxLines = 2
                setTextColor(activity.getColor(R.color.xingyue_text_secondary))
            })
            card.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(9) }
                text = "${definition.questions.size} 题 · 约 ${definition.estimatedMinutes} 分钟"
                textSize = 12f
                setTextColor(activity.getColor(R.color.xingyue_primary_blue))
            })
            card.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                val recent = recentRecords.firstOrNull { it.type == definition.id }
                text = if (recent == null) {
                    "尚未评估"
                } else {
                    "最近：${recent.level} · ${recent.score} 分"
                }
                textSize = 11f
                maxLines = 1
                setTextColor(activity.getColor(R.color.xingyue_text_secondary))
            })
            card.setOnClickListener { startAssessment(definition) }
            attachPressFeedback(card)
            grid.addView(card)
        }
    }

    private fun startAssessment(definition: AssessmentDefinition) {
        currentDefinition = definition
        currentQuestionIndex = 0
        answers = IntArray(definition.questions.size) { -1 }
        pageState = PageState.QUESTION
        showOnly(questionPanel)
        questionPanel.scrollTo(0, 0)
        renderQuestion(forward = true)
    }

    private fun renderQuestion(forward: Boolean) {
        val question = currentDefinition.questions[currentQuestionIndex]
        activity.findViewById<TextView>(R.id.assessment_question_title).text = currentDefinition.title
        activity.findViewById<TextView>(R.id.assessment_question_dimension).text = question.dimension
        activity.findViewById<TextView>(R.id.assessment_question_count).text =
            "${currentQuestionIndex + 1} / ${currentDefinition.questions.size}"
        activity.findViewById<ProgressBar>(R.id.assessment_progress).progress =
            ((currentQuestionIndex + 1) * 100f / currentDefinition.questions.size).roundToInt()
        activity.findViewById<TextView>(R.id.assessment_question_text).apply {
            text = question.text
            animate().cancel()
            alpha = 0f
            translationX = if (forward) dp(14).toFloat() else -dp(14).toFloat()
            animate().alpha(1f).translationX(0f).setDuration(200L).start()
        }
        activity.findViewById<TextView>(R.id.assessment_previous_button).apply {
            isEnabled = currentQuestionIndex > 0
            alpha = if (isEnabled) 1f else 0.45f
        }
        activity.findViewById<TextView>(R.id.assessment_next_button).text =
            if (currentQuestionIndex == currentDefinition.questions.lastIndex) "查看结果" else "下一题"
        renderOptions()
        updateNextButtonState()
    }

    private fun renderOptions() {
        val container = activity.findViewById<LinearLayout>(R.id.assessment_option_container)
        container.removeAllViews()
        val selectedAnswer = answers[currentQuestionIndex]
        optionLabels.forEachIndexed { score, label ->
            val selected = score == selectedAnswer
            val option = TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(54)
                ).apply { topMargin = if (score == 0) 0 else dp(9) }
                background = ContextCompat.getDrawable(
                    activity,
                    if (selected) R.drawable.bg_assessment_option_selected
                    else R.drawable.bg_assessment_option
                )
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), 0, dp(18), 0)
                text = if (selected) "✓  $label" else label
                textSize = 15f
                setTextColor(
                    activity.getColor(
                        if (selected) R.color.xingyue_primary_blue
                        else R.color.xingyue_text_primary
                    )
                )
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                isClickable = true
                isFocusable = true
                contentDescription = "$label${if (selected) "，已选择" else ""}"
            }
            option.setOnClickListener {
                answers[currentQuestionIndex] = score
                renderOptions()
                updateNextButtonState()
            }
            attachPressFeedback(option)
            container.addView(option)
        }
    }

    private fun updateNextButtonState() {
        activity.findViewById<TextView>(R.id.assessment_next_button).apply {
            isEnabled = answers.getOrNull(currentQuestionIndex) != -1
            alpha = if (isEnabled) 1f else 0.45f
        }
    }

    private fun finishAssessment() {
        val normalizedAnswers = currentDefinition.questions.mapIndexed { index, question ->
            normalizeAnswer(answers[index], question)
        }
        val score = (normalizedAnswers.sum() * 100f / (normalizedAnswers.size * 4f)).roundToInt()
        val level = levelFor(score)
        val dimensionScores = calculateDimensions()
        val record = AssessmentRecord(
            id = System.currentTimeMillis(),
            type = currentDefinition.id,
            title = currentDefinition.title,
            score = score,
            level = level,
            summary = summaryFor(score),
            createdAt = System.currentTimeMillis(),
            answers = answers.toList(),
            dimensionScores = dimensionScores,
            suggestions = buildSuggestions(dimensionScores, score)
        )
        storage.save(record)
        currentRecord = record
        showResult(record, animateScore = true)
    }

    private fun calculateDimensions(): Map<String, Int> {
        val grouped = linkedMapOf<String, MutableList<Int>>()
        currentDefinition.questions.forEachIndexed { index, question ->
            grouped.getOrPut(question.dimension) { mutableListOf() }
                .add(normalizeAnswer(answers[index], question))
        }
        return grouped.mapValues { (_, scores) ->
            (scores.average() * 25f).roundToInt()
        }
    }

    private fun showResult(record: AssessmentRecord, animateScore: Boolean) {
        currentDefinition = AssessmentCatalog.find(record.type) ?: currentDefinition
        currentRecord = record
        pageState = PageState.RESULT
        showOnly(resultPanel)
        resultPanel.scrollTo(0, 0)
        activity.findViewById<TextView>(R.id.assessment_result_type).text = record.title
        activity.findViewById<TextView>(R.id.assessment_result_time).text =
            "生成于 ${formatTime(record.createdAt)} · 仅保存在当前设备"
        activity.findViewById<TextView>(R.id.assessment_result_level).apply {
            text = record.level
            val (backgroundRes, colorRes) = assessmentLevelStyle(record.score)
            background = ContextCompat.getDrawable(activity, backgroundRes)
            setTextColor(activity.getColor(colorRes))
        }
        activity.findViewById<TextView>(R.id.assessment_result_summary).text = record.summary
        renderDimensions(record)
        renderSuggestions(record)
        val scoreView = activity.findViewById<TextView>(R.id.assessment_result_score)
        if (animateScore) {
            ValueAnimator.ofInt(0, record.score).apply {
                duration = 520L
                addUpdateListener { scoreView.text = "${it.animatedValue}" }
                start()
            }
        } else {
            scoreView.text = "${record.score}"
        }
    }

    private fun renderDimensions(record: AssessmentRecord) {
        val container = activity.findViewById<LinearLayout>(R.id.assessment_dimension_container)
        container.removeAllViews()
        record.dimensionScores.entries.forEachIndexed { index, entry ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                if (index > 0) setPadding(0, dp(14), 0, 0)
            }
            row.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    text = entry.key
                    textSize = 14f
                    setTextColor(activity.getColor(R.color.xingyue_text_primary))
                })
                addView(TextView(activity).apply {
                    text = "${entry.value}"
                    textSize = 13f
                    setTextColor(activity.getColor(R.color.xingyue_primary_blue))
                    setTypeface(typeface, Typeface.BOLD)
                })
            })
            row.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(6)
                ).apply { topMargin = dp(7) }
                max = 100
                progress = entry.value
                progressDrawable = ContextCompat.getDrawable(activity, R.drawable.bg_assessment_progress)
            })
            row.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
                text = dimensionExplanation(entry.key, entry.value)
                textSize = 12f
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(activity.getColor(R.color.xingyue_text_secondary))
            })
            container.addView(row)
        }
    }

    private fun assessmentIconBadge(type: String): Int = when (type) {
        "emotion", "sleep" -> R.drawable.bg_assessment_icon_purple
        "anxiety" -> R.drawable.bg_assessment_icon_mint
        "low_mood" -> R.drawable.bg_assessment_icon_pink
        else -> R.drawable.bg_assessment_icon_blue
    }

    private fun assessmentLevelStyle(score: Int): Pair<Int, Int> = when {
        score <= 35 -> R.drawable.bg_assessment_level_mint to R.color.xingyue_mint_deep
        score <= 65 -> R.drawable.bg_assessment_level_purple to R.color.xingyue_moon_purple_deep
        else -> R.drawable.bg_assessment_level_pink to R.color.xingyue_mist_pink_deep
    }

    private fun renderSuggestions(record: AssessmentRecord) {
        val suggestions = record.suggestions.ifEmpty {
            buildSuggestions(record.dimensionScores, record.score)
        }
        val container = activity.findViewById<LinearLayout>(R.id.assessment_suggestion_container)
        container.removeAllViews()
        suggestions.forEachIndexed { index, suggestion ->
            container.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = if (index == 0) 0 else dp(12) }
                text = "${index + 1}.  $suggestion"
                textSize = 14f
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(activity.getColor(R.color.xingyue_text_primary))
            })
        }
    }

    private fun showHistory() {
        pageState = PageState.HISTORY
        showOnly(historyPanel)
        historyPanel.scrollTo(0, 0)
        val container = activity.findViewById<LinearLayout>(R.id.assessment_history_container)
        container.removeAllViews()
        val records = storage.load()
        if (records.isEmpty()) {
            container.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                background = ContextCompat.getDrawable(activity, R.drawable.bg_assessment_card)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(32), dp(20), dp(32))
                text = "还没有评估记录\n完成一次评估后，会在这里看到温柔的状态回顾。"
                textSize = 14f
                setLineSpacing(dp(5).toFloat(), 1f)
                setTextColor(activity.getColor(R.color.xingyue_text_secondary))
            })
            return
        }
        records.forEachIndexed { index, record ->
            val card = LinearLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = if (index == 0) 0 else dp(10) }
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(activity, R.drawable.bg_assessment_card)
                setPadding(dp(18), dp(17), dp(18), dp(17))
                isClickable = true
                isFocusable = true
            }
            card.addView(TextView(activity).apply {
                text = record.title
                textSize = 16f
                setTextColor(activity.getColor(R.color.xingyue_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            })
            card.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
                text = "${formatTime(record.createdAt)} · ${record.level} · ${record.score} 分"
                textSize = 13f
                setTextColor(activity.getColor(R.color.xingyue_text_secondary))
            })
            card.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
                text = record.summary
                textSize = 12f
                maxLines = 2
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(activity.getColor(R.color.xingyue_text_secondary))
            })
            card.addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(9) }
                text = "查看报告"
                textSize = 13f
                setTextColor(activity.getColor(R.color.xingyue_primary_blue))
                setTypeface(typeface, Typeface.BOLD)
            })
            card.setOnClickListener { showResult(record, animateScore = false) }
            attachPressFeedback(card)
            container.addView(card)
        }
    }

    private fun showHome(animate: Boolean = true) {
        pageState = PageState.HOME
        showOnly(homePanel, animate)
        homePanel.scrollTo(0, 0)
        buildAssessmentCards()
        refreshTodayStatus()
        refreshRecentRecords()
    }

    private fun refreshTodayStatus() {
        val todayRecord = storage.load().firstOrNull {
            it.type == COMPREHENSIVE_TYPE && isToday(it.createdAt)
        }
        val status = activity.findViewById<TextView>(R.id.assessment_today_status)
        val energy = activity.findViewById<TextView>(R.id.assessment_today_energy)
        val time = activity.findViewById<TextView>(R.id.assessment_today_time)
        val reportButton = activity.findViewById<View>(R.id.assessment_today_report_button)
        if (todayRecord == null) {
            status.text = "待评估"
            energy.text = "还没有今日评估，来和心屿一起看看今天的状态吧。"
            time.text = "记录只保存在当前设备"
            reportButton.visibility = View.GONE
        } else {
            status.text = todayRecord.level
            energy.text = "状态负荷：${todayRecord.score} / 100 · ${todayRecord.title}"
            time.text = "最近评估：${formatTime(todayRecord.createdAt)}"
            reportButton.visibility = View.VISIBLE
        }
    }

    private fun refreshRecentRecords() {
        val container = activity.findViewById<LinearLayout>(R.id.assessment_recent_container)
        container.removeAllViews()
        val records = storage.load().take(3)
        if (records.isEmpty()) {
            container.addView(createRecentRecordCard(null, 0))
            return
        }
        records.forEachIndexed { index, record ->
            container.addView(createRecentRecordCard(record, index))
        }
    }

    private fun createRecentRecordCard(record: AssessmentRecord?, index: Int): View =
        LinearLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = if (index == 0) dp(10) else dp(8) }
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(activity, R.drawable.bg_assessment_card)
            setPadding(dp(18), dp(16), dp(18), dp(16))

            if (record == null) {
                addView(TextView(activity).apply {
                    text = "还没有评估档案"
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(activity.getColor(R.color.xingyue_text_primary))
                })
                addView(TextView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(7) }
                    text = "完成一次评估后，报告会仅保存在当前设备。"
                    textSize = 13f
                    setTextColor(activity.getColor(R.color.xingyue_text_secondary))
                })
                return@apply
            }

            isClickable = true
            isFocusable = true
            contentDescription = "${record.title}，${record.level}，查看报告"
            addView(TextView(activity).apply {
                text = record.title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(activity.getColor(R.color.xingyue_text_primary))
            })
            addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
                text = "${record.level} · ${record.score} 分 · ${formatTime(record.createdAt)}"
                textSize = 12f
                setTextColor(activity.getColor(R.color.xingyue_primary_blue))
            })
            addView(TextView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
                text = record.summary
                textSize = 12f
                maxLines = 2
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(activity.getColor(R.color.xingyue_text_secondary))
            })
            setOnClickListener { showResult(record, animateScore = false) }
            attachPressFeedback(this)
        }

    private fun requestAbandonAssessment() {
        AlertDialog.Builder(activity)
            .setTitle("暂时离开本次评估？")
            .setMessage("当前作答还没有保存。你可以继续完成，或放弃并返回评估首页。")
            .setNegativeButton("继续作答", null)
            .setPositiveButton("放弃本次") { _, _ -> showHome() }
            .show()
    }

    private fun dimensionExplanation(name: String, score: Int): String {
        val signal = when {
            score <= 30 -> "当前信号较轻"
            score <= 60 -> "出现了一些值得留意的信号"
            score <= 80 -> "近期相关负荷较明显"
            else -> "近期相关负荷较强，建议优先获得支持"
        }
        return when {
            name.contains("压力") || name.contains("任务") || name.contains("负荷") ->
                "$signal，可以先减少同时处理的事情，为恢复留一点空间。"
            name.contains("睡眠") || name.contains("恢复") || name.contains("疲惫") ->
                "$signal，规律休息和降低睡前刺激可能更有帮助。"
            name.contains("焦虑") || name.contains("紧张") || name.contains("担忧") ||
                name.contains("反复") ->
                "$signal，尝试把注意力带回当下，并把担忧拆成可处理的小步骤。"
            name.contains("低落") || name.contains("兴趣") || name.contains("动力") ->
                "$signal，可以从低负担的小事开始，减少对自己的苛责。"
            name.contains("陪伴") || name.contains("社交") || name.contains("连接") ->
                "$signal，尊重独处需要，也可以选择一个可信赖的人表达感受。"
            else ->
                "$signal，继续观察情绪变化，并为自己保留温和的恢复时间。"
        }
    }

    private fun buildSuggestions(
        dimensionScores: Map<String, Int>,
        score: Int
    ): List<String> {
        val dimensionSuggestions = dimensionScores.entries
            .sortedByDescending { it.value }
            .take(2)
            .flatMap { suggestionForDimension(it.key) }

        val safetySuggestion = if (score > 80) {
            listOf("如果这种状态持续影响生活或让你感到不安全，请及时联系可信赖的人或专业支持机构。")
        } else {
            emptyList()
        }

        return (dimensionSuggestions + safetySuggestion)
            .distinct()
            .take(4)
            .ifEmpty {
                listOf(
                    "保持适合自己的生活节奏，并留意让你感到轻松的小事。",
                    "愿意时，可以记录今天的感受，观察状态如何变化。",
                    "如果持续感到困扰，可以和可信赖的人聊聊。"
                )
            }
    }

    private fun suggestionForDimension(name: String): List<String> = when {
        name.contains("压力") || name.contains("任务") || name.contains("负荷") -> listOf(
            "把今天最重要的事情拆成一个 10 分钟内可以开始的小任务。",
            "暂时放下不紧急的要求，先恢复一点精力。"
        )
        name.contains("睡眠") || name.contains("恢复") || name.contains("疲惫") -> listOf(
            "睡前 20 分钟减少短视频和刺激信息。",
            "做一次缓慢呼吸，让身体逐渐进入休息状态。"
        )
        name.contains("焦虑") || name.contains("紧张") || name.contains("担忧") ||
            name.contains("反复") -> listOf(
            "把正在担心的事情分成“现在能做”和“暂时无法控制”两部分。",
            "用三次缓慢呼吸把注意力带回身体和当下。"
        )
        name.contains("低落") || name.contains("兴趣") || name.contains("动力") -> listOf(
            "选择一件负担很小、完成后会稍微舒服一点的事情。",
            "允许自己降低今天的要求，不必用一次状态定义自己。"
        )
        name.contains("陪伴") || name.contains("社交") || name.contains("连接") -> listOf(
            "可以和心屿聊聊今天最想被理解的地方。",
            "给一个可信赖的人发一句简单消息，不需要一次解释太多。"
        )
        else -> listOf(
            "给自己安排一段不被打扰的短暂休息。",
            "记录一个让情绪稍微变轻的时刻。"
        )
    }

    private fun showOnly(target: View, animate: Boolean = true) {
        listOf(homePanel, questionPanel, resultPanel, historyPanel).forEach {
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
        PageState.QUESTION -> questionPanel
        PageState.RESULT -> resultPanel
        PageState.HISTORY -> historyPanel
    }

    private fun levelFor(score: Int): String = when {
        score <= 30 -> "状态较平稳"
        score <= 60 -> "需要关注"
        score <= 80 -> "压力偏高"
        else -> "建议寻求更多支持"
    }

    private fun summaryFor(score: Int): String = when {
        score <= 30 -> "你最近的整体状态相对平稳。继续照顾好自己的节奏，也别忘了给轻松和快乐留一点位置。"
        score <= 60 -> "你最近可能有一些消耗，身体和情绪正在提醒你慢一点。给自己一点恢复时间，会比勉强撑住更重要。"
        score <= 80 -> "你最近承受的压力可能偏高。先减少一些负担，并尝试向可信赖的人表达真实感受。"
        else -> "你可能已经辛苦了一段时间。请优先照顾安全与基本休息，并考虑联系可信赖的人或专业支持。"
    }

    private fun formatTime(time: Long): String =
        SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(time))

    private fun normalizeAnswer(answer: Int, question: AssessmentQuestion): Int =
        if (question.reverse) 4 - answer else answer

    private fun isToday(time: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = time }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    private fun bindPressFeedback(vararg ids: Int) {
        ids.forEach { attachPressFeedback(activity.findViewById(it)) }
    }

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

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val COMPREHENSIVE_TYPE = "comprehensive"
    }
}
