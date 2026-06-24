package com.mindisle.app.ui.companion

import android.widget.TextView

class QuickQuestionAdapter(
    private val questionViews: List<TextView>,
    private val onQuestionClick: (String) -> Unit
) {
    fun bind(questions: List<String>) {
        questionViews.forEachIndexed { index, view ->
            val question = questions.getOrNull(index).orEmpty()
            view.text = question
            view.setOnClickListener { onQuestionClick(question) }
        }
    }
}
