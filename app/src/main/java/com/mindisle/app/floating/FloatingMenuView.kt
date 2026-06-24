package com.mindisle.app.floating

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.mindisle.app.live2d.Live2DModel

class FloatingMenuView(context: Context) : FrameLayout(context) {
    var onOpenApp: (() -> Unit)? = null
    var onHide: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onScalePreview: ((Float) -> Unit)? = null
    var onScaleCommit: ((Float) -> Unit)? = null
    var onResetDisplay: (() -> Unit)? = null
    var onModelSelected: ((String) -> Unit)? = null
    var onTouchWidthChange: ((Int) -> Unit)? = null
    var onTouchHeightChange: ((Int) -> Unit)? = null

    /** Drag header — dragging this moves the entire floating group. */
    val dragHeader: TextView by lazy {
        TextView(context).apply {
            text = "心屿快捷菜单"
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(0xFF26324D.toInt())
            textSize = 13f
            setPadding(dp(8), 0, dp(8), 0)
        }
    }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    private val scrollView = ScrollView(context).apply {
        isFillViewport = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        isVerticalScrollBarEnabled = true
        addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        setOnTouchListener { view, _ ->
            view.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    init {
        setPadding(dp(8), dp(4), dp(8), dp(8))
        background = GradientDrawable().apply {
            setColor(0xF5FFFDF8.toInt())
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0x44C9B6FF)
        }
        elevation = dp(6).toFloat()

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        outer.addView(dragHeader, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))
        outer.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(outer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun render(models: List<Live2DModel>, currentScale: Float) {
        content.removeAllViews()
        addButton("返回 App") { onOpenApp?.invoke() }
        addButton("恢复默认大小") { onResetDisplay?.invoke() }
        models.forEach { model ->
            if (model.id == "xingyue_shuimu") {
                addButton("星月水母（full）") { onModelSelected?.invoke("xingyue_shuimu:full") }
                addButton("星月水母（small）") { onModelSelected?.invoke("xingyue_shuimu:small") }
            } else {
                addButton(model.name) { onModelSelected?.invoke(model.id) }
            }
        }
        addButton("隐藏快捷菜单") { onHide?.invoke() }
        addButton("关闭悬浮") { onClose?.invoke() }
    }

    private fun addScaleRow(currentScale: Float) {
        content.addView(label("人物大小"))
        content.addView(SeekBar(context).apply {
            max = 120
            progress = (((currentScale.coerceIn(0.8f, 1.2f) - 0.8f) / 0.4f) * max).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) onScalePreview?.invoke(progressToScale(progress))
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    onScaleCommit?.invoke(progressToScale(seekBar?.progress ?: progress))
                }
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun progressToScale(progress: Int): Float {
        return 0.8f + progress.coerceIn(0, 120) / 120f * 0.4f
    }

    private fun addTouchAreaRow(type: String, labelText: String) {
        val baseVal = if (type == "width") 220 else 300
        val lbl = label("$labelText：+0dp")
        lbl.tag = "${type}_label"
        content.addView(lbl)
        content.addView(SeekBar(context).apply {
            max = 200; progress = 0
            tag = "${type}_seekbar"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    lbl.text = "$labelText：+${progress}dp"
                    if (fromUser) {
                        if (type == "width") onTouchWidthChange?.invoke(progress)
                        else onTouchHeightChange?.invoke(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)))
    }

    private fun addButton(text: String, action: () -> Unit) {
        content.addView(TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(0xFF26324D.toInt())
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(0xFFF7F8FF.toInt())
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), 0x33C9B6FF)
            }
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).apply {
            bottomMargin = dp(6)
        })
    }

    private fun label(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.DKGRAY)
            textSize = 12f
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}
