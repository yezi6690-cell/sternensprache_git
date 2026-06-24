package com.mindisle.app.ui.companion

import android.content.Context
import android.graphics.Outline
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mindisle.app.R

class MessageList @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private data class Entry(
        val text: String? = null,
        val imageUri: String? = null,
        val isUser: Boolean
    )

    private val entries = mutableListOf<Entry>()
    private val messageAdapter = MessageAdapter()
    private var assistantName: String = "心屿"

    init {
        layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        adapter = messageAdapter
        itemAnimator = null
        clipToPadding = false
    }

    fun addAssistantMessage(text: String): Int {
        return addEntry(Entry(text = text, isUser = false))
    }

    fun addUserMessage(text: String): Int {
        return addEntry(Entry(text = text, isUser = true))
    }

    fun addUserImage(uri: String): Int {
        return addEntry(Entry(imageUri = uri, isUser = true))
    }

    fun addUserImageWithText(uri: String, text: String): Int {
        return addEntry(Entry(text = text, imageUri = uri, isUser = true))
    }

    fun replaceAssistantMessage(position: Int, text: String) {
        if (position !in entries.indices) return
        entries[position] = Entry(text = text, isUser = false)
        messageAdapter.notifyItemChanged(position)
    }

    fun addStoredMessage(role: String, text: String) {
        if (role == "user") {
            addUserMessage(text)
        } else {
            addAssistantMessage(text)
        }
    }

    fun setAssistantName(name: String) {
        assistantName = name.ifBlank { "心屿" }
        messageAdapter.notifyDataSetChanged()
    }

    fun clearMessages() {
        val count = entries.size
        entries.clear()
        if (count > 0) {
            messageAdapter.notifyItemRangeRemoved(0, count)
        }
    }

    fun scrollToBottom() {
        if (entries.isNotEmpty()) {
            post { smoothScrollToPosition(entries.lastIndex) }
        }
    }

    private fun addEntry(entry: Entry): Int {
        entries += entry
        val position = entries.lastIndex
        messageAdapter.notifyItemInserted(position)
        scrollToBottom()
        return position
    }

    private inner class MessageAdapter : Adapter<MessageViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(4), 0, dp(4))
            }
            return MessageViewHolder(row)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            holder.bind(entries[position])
        }

        override fun getItemCount(): Int = entries.size
    }

    private inner class MessageViewHolder(
        private val row: LinearLayout
    ) : ViewHolder(row) {

        fun bind(entry: Entry) {
            row.removeAllViews()
            row.gravity = if (entry.isUser) Gravity.END else Gravity.START

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = if (entry.isUser) Gravity.END else Gravity.START
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            entry.imageUri?.let { uri ->
                content.addView(createImageView(uri))
            }
            entry.text?.takeIf { it.isNotBlank() }?.let { text ->
                content.addView(createTextBubble(text, entry.isUser, entry.imageUri != null))
            }
            row.addView(content)
        }
    }

    private fun createTextBubble(text: String, isUser: Boolean, hasImage: Boolean): TextView {
        return TextView(context).apply {
            this.text = if (isUser) "我：$text" else "$assistantName：$text"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.mi_text_main))
            setLineSpacing(dp(2).toFloat(), 1f)
            setBackgroundResource(
                if (isUser) R.drawable.bg_companion_user_bubble
                else R.drawable.bg_companion_ai_bubble
            )
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (hasImage) topMargin = dp(6)
            }
        }
    }

    private fun createImageView(uri: String): ImageView {
        return ImageView(context).apply {
            setImageURI(Uri.parse(uri))
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            maxWidth = (resources.displayMetrics.widthPixels * 0.65f).toInt()
            maxHeight = dp(220)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(14).toFloat())
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }
}
