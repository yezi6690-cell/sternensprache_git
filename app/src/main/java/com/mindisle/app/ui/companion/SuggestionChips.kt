package com.mindisle.app.ui.companion

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView

class SuggestionChips @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {
    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }
}
