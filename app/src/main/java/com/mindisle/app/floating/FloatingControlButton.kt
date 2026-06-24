package com.mindisle.app.floating

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView

class FloatingControlButton(context: Context) : AppCompatTextView(context) {
    init {
        text = "+"
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xB326324D.toInt())
            setStroke(dp(1), 0x66FFFFFF)
        }
        elevation = dp(4).toFloat()
        contentDescription = "悬浮人物控制按钮"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}
