package com.mindisle.app.music

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mindisle.app.R
import java.util.Locale
import kotlin.math.abs

class MusicAdapter(
    private val onPlay: (Int) -> Unit,
    private val onDelete: (MusicTrack, View) -> Unit
) : RecyclerView.Adapter<MusicAdapter.TrackHolder>() {
    private var tracks: List<MusicTrack> = emptyList()
    private var currentTrackId: String? = null
    private var deleteMode = false

    fun submit(newTracks: List<MusicTrack>, currentId: String?) {
        val oldTracks = tracks
        val oldCurrentId = currentTrackId
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldTracks.size

            override fun getNewListSize(): Int = newTracks.size

            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                oldTracks[oldPosition].id == newTracks[newPosition].id

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val oldTrack = oldTracks[oldPosition]
                val newTrack = newTracks[newPosition]
                return oldTrack == newTrack &&
                    (oldTrack.id == oldCurrentId) == (newTrack.id == currentId)
            }
        })
        tracks = newTracks
        currentTrackId = currentId
        diff.dispatchUpdatesTo(this)
    }

    fun updateCurrent(currentId: String?) {
        val previousId = currentTrackId
        if (previousId == currentId) return
        currentTrackId = currentId
        tracks.indexOfFirst { it.id == previousId }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
        tracks.indexOfFirst { it.id == currentId }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
    }

    fun setDeleteMode(enabled: Boolean) {
        if (deleteMode == enabled) return
        deleteMode = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackHolder =
        TrackHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_music_track,
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: TrackHolder, position: Int) {
        holder.bind(tracks[position], tracks[position].id == currentTrackId)
    }

    override fun onViewRecycled(holder: TrackHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = tracks.size

    inner class TrackHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.music_track_root)
        private val content: View = itemView.findViewById(R.id.music_track_content)
        private val title: TextView = itemView.findViewById(R.id.music_track_title)
        private val artist: TextView = itemView.findViewById(R.id.music_track_artist)
        private val duration: TextView = itemView.findViewById(R.id.music_track_duration)
        private val playingIndicator: View =
            itemView.findViewById(R.id.music_track_playing_indicator)
        private val touchSlop = ViewConfiguration.get(itemView.context).scaledTouchSlop
        private val holdBackground = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), HOLD_BLUE_STROKE)
        }
        private var holdAnimator: ValueAnimator? = null
        private var recoveryAnimator: ValueAnimator? = null
        private var downX = 0f
        private var downY = 0f
        private var holdCancelled = false
        private var deleting = false
        private var selected = false
        private var currentHoldColor = HOLD_BLUE

        fun bind(track: MusicTrack, isSelected: Boolean) {
            recycle()
            selected = isSelected
            title.text = track.title
            artist.text = track.artist
            duration.text = formatDuration(track.duration)
            playingIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            applyBaseBackground()
            root.contentDescription = if (deleteMode) {
                "${track.title}，长按两点五秒删除"
            } else {
                "${track.title}，${track.artist}"
            }
            root.setOnClickListener {
                if (!deleteMode) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) onPlay(position)
                }
            }
            root.setOnLongClickListener(null)
            root.setOnTouchListener { _, event -> handleTouch(track, event) }
        }

        fun recycle() {
            holdCancelled = true
            holdAnimator?.cancel()
            holdAnimator = null
            recoveryAnimator?.cancel()
            recoveryAnimator = null
            content.animate().cancel()
            root.animate().cancel()
            root.alpha = 1f
            root.scaleX = 1f
            root.scaleY = 1f
            content.alpha = 1f
            content.scaleX = 1f
            content.scaleY = 1f
            root.isEnabled = true
            deleting = false
        }

        private fun handleTouch(track: MusicTrack, event: MotionEvent): Boolean {
            if (!deleteMode) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        content.animate()
                            .scaleX(0.98f)
                            .scaleY(0.98f)
                            .setDuration(80L)
                            .start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        content.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150L)
                            .start()
                }
                return false
            }
            if (deleting) return true

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    startDeleteHold(track)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - downX) > touchSlop ||
                        abs(event.y - downY) > touchSlop
                    ) {
                        cancelDeleteHold(animateBack = true)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    cancelDeleteHold(animateBack = true)
            }
            return true
        }

        private fun startDeleteHold(track: MusicTrack) {
            cancelDeleteHold(animateBack = false)
            holdCancelled = false
            currentHoldColor = HOLD_BLUE
            holdBackground.setColor(currentHoldColor)
            holdBackground.setStroke(dp(1), HOLD_BLUE_STROKE)
            root.background = holdBackground
            content.animate()
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(120L)
                .start()

            holdAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DELETE_HOLD_DURATION
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val progress = it.animatedValue as Float
                    currentHoldColor = when {
                        progress < 0.55f -> ColorUtils.blendARGB(
                            HOLD_BLUE,
                            HOLD_ORANGE,
                            progress / 0.55f
                        )
                        else -> ColorUtils.blendARGB(
                            HOLD_ORANGE,
                            HOLD_RED,
                            (progress - 0.55f) / 0.45f
                        )
                    }
                    holdBackground.setColor(currentHoldColor)
                    holdBackground.setStroke(
                        dp(1),
                        ColorUtils.blendARGB(HOLD_BLUE_STROKE, HOLD_RED_STROKE, progress)
                    )
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        holdCancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        holdAnimator = null
                        if (!holdCancelled &&
                            (animation as ValueAnimator).animatedFraction >= 0.999f
                        ) {
                            startDeleteBurst(track)
                        }
                    }
                })
                start()
            }
        }

        private fun cancelDeleteHold(animateBack: Boolean) {
            if (deleting) return
            val hadActiveHold = holdAnimator?.isRunning == true
            holdCancelled = true
            holdAnimator?.cancel()
            holdAnimator = null
            content.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150L)
                .start()
            if (!hadActiveHold || !animateBack) {
                applyBaseBackground()
                return
            }

            val startColor = currentHoldColor
            recoveryAnimator?.cancel()
            recoveryAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180L
                addUpdateListener {
                    val progress = it.animatedValue as Float
                    holdBackground.setColor(
                        ColorUtils.blendARGB(startColor, baseColor(), progress)
                    )
                    holdBackground.setStroke(
                        dp(1),
                        ColorUtils.blendARGB(HOLD_RED_STROKE, baseStrokeColor(), progress)
                    )
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        recoveryAnimator = null
                        applyBaseBackground()
                    }
                })
                start()
            }
        }

        private fun startDeleteBurst(track: MusicTrack) {
            deleting = true
            root.isEnabled = false
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            holdBackground.setColor(HOLD_RED)
            holdBackground.setStroke(dp(1), HOLD_RED_STROKE)
            content.animate()
                .alpha(0.2f)
                .setDuration(180L)
                .start()
            root.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(220L)
                .start()
            onDelete(track, root)
        }

        private fun applyBaseBackground() {
            root.background = ContextCompat.getDrawable(
                root.context,
                if (selected) R.drawable.bg_music_track_selected else R.drawable.bg_music_track
            )
        }

        private fun baseColor(): Int =
            if (selected) Color.parseColor("#EADCEEFF") else Color.parseColor("#E8FFFFFF")

        private fun baseStrokeColor(): Int =
            if (selected) Color.parseColor("#7FAFE8") else Color.parseColor("#5CB8D7FF")

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density).toInt()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
        return String.format(Locale.CHINA, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private companion object {
        const val DELETE_HOLD_DURATION = 2_500L
        val HOLD_BLUE = Color.parseColor("#DCEEFF")
        val HOLD_ORANGE = Color.parseColor("#FFB164")
        val HOLD_RED = Color.parseColor("#FF5967")
        val HOLD_BLUE_STROKE = Color.parseColor("#7FAFE8")
        val HOLD_RED_STROKE = Color.parseColor("#D94153")
    }
}
