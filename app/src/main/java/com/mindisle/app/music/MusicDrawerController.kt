package com.mindisle.app.music

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mindisle.app.R
import com.mindisle.app.ui.companion.CompanionSideMenuController
import java.io.File
import java.util.Locale

class MusicDrawerController(
    private val activity: AppCompatActivity,
    private val root: ViewGroup,
    private val playerManager: MusicPlayerManager,
    private val repository: MusicRepository,
    private val onAddMusic: () -> Unit,
    private val onBeforeOpen: () -> Unit
) : MusicPlayerManager.Listener {
    private val panel: View = activity.findViewById(R.id.music_drawer_panel)
    private val scrim: View = activity.findViewById(R.id.music_drawer_scrim)
    private val openButton: View = activity.findViewById(R.id.music_open_button)
    private val closeButton: View = activity.findViewById(R.id.music_drawer_close)
    private val vinyl: VinylRecordView = activity.findViewById(R.id.music_vinyl)
    private val tonearm: TonearmView = activity.findViewById(R.id.music_tonearm)
    private val title: TextView = activity.findViewById(R.id.music_current_title)
    private val artist: TextView = activity.findViewById(R.id.music_current_artist)
    private val progress: SeekBar = activity.findViewById(R.id.music_progress)
    private val currentTime: TextView = activity.findViewById(R.id.music_current_time)
    private val totalTime: TextView = activity.findViewById(R.id.music_total_time)
    private val playPause: ImageView = activity.findViewById(R.id.music_play_pause)
    private val modeButton: ImageView = activity.findViewById(R.id.music_mode_button)
    private val deleteCurrentButton: ImageView =
        activity.findViewById(R.id.music_delete_current_button)
    private val emptyState: View = activity.findViewById(R.id.music_empty_state)
    private val list: RecyclerView = activity.findViewById(R.id.music_track_list)
    private val deleteParticleOverlay: DeleteParticleView =
        activity.findViewById(R.id.music_delete_particle_overlay)
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = MusicAdapter(
        onPlay = playerManager::play,
        onDelete = ::animateTrackDeletion
    )
    private var vinylAnimator: ObjectAnimator? = null
    private var progressAnimator: ValueAnimator? = null
    private var opened = false
    private var userSeeking = false
    private var deleteMode = false
    private var deletionAnimating = false
    private var renderedTrackId: String? = null
    private var swipeDownX = 0f
    private var swipeDownY = 0f

    val isOpen: Boolean
        get() = opened

    private val progressTick = object : Runnable {
        override fun run() {
            if (!opened) return
            if (!userSeeking) updateProgress(animate = true)
            handler.postDelayed(this, 500L)
        }
    }

    fun attach() {
        playerManager.listener = this
        list.layoutManager = LinearLayoutManager(activity)
        list.adapter = adapter
        root.post {
            val params = panel.layoutParams
            params.width = drawerWidth()
            panel.layoutParams = params
            panel.translationX = -params.width.toFloat()
        }
        closeButton.setOnClickListener { close() }
        scrim.setOnClickListener { close() }
        activity.findViewById<View>(R.id.music_add_button).setOnClickListener { onAddMusic() }
        activity.findViewById<View>(R.id.music_empty_add_button).setOnClickListener { onAddMusic() }
        deleteCurrentButton.setOnClickListener {
            if (playerManager.getTracks().isEmpty()) {
                Toast.makeText(activity, "暂无可删除的音乐", Toast.LENGTH_SHORT).show()
            } else {
                setDeleteMode(!deleteMode, announce = true)
            }
        }
        deleteCurrentButton.setOnLongClickListener {
            triggerParticleVisibilityTest()
            true
        }
        modeButton.setOnClickListener {
            val mode = playerManager.cycleMode()
            renderMode(mode)
            Toast.makeText(activity, "已切换为${modeLabel(mode)}播放", Toast.LENGTH_SHORT).show()
        }
        activity.findViewById<View>(R.id.music_previous).setOnClickListener {
            playerManager.previous()
        }
        activity.findViewById<View>(R.id.music_next).setOnClickListener {
            playerManager.next()
        }
        playPause.setOnClickListener { playerManager.toggle() }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) currentTime.text = formatDuration(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
                progressAnimator?.cancel()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                userSeeking = false
                playerManager.seekTo(seekBar.progress)
                updateProgress()
            }
        })
        panel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeDownX = event.rawX
                    swipeDownY = event.rawY
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - swipeDownX
                    val dy = event.rawY - swipeDownY
                    if (dx < -dp(56) && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        close()
                    }
                }
            }
            false
        }
        bindPressFeedback(
            closeButton,
            playPause,
            activity.findViewById(R.id.music_previous),
            activity.findViewById(R.id.music_next),
            modeButton,
            activity.findViewById(R.id.music_add_button),
            activity.findViewById(R.id.music_empty_add_button),
            deleteCurrentButton
        )
        renderMode(playerManager.playMode)
        refreshTracks()
        onTrackChanged(playerManager.currentTrack)
        onPlaybackChanged(playerManager.isPlaying)
    }

    fun open() {
        if (opened) return
        opened = true
        android.util.Log.d(
            "MindIsleMusic",
            "open drawer panelWidth=${panel.width} rootWidth=${root.width}"
        )
        onBeforeOpen()
        val width = panel.width.takeIf { it > 0 } ?: drawerWidth()
        scrim.alpha = 0f
        scrim.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        scrim.bringToFront()
        panel.bringToFront()
        panel.translationX = -width.toFloat()
        panel.alpha = 0.92f
        panel.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220L)
            .start()
        scrim.animate().alpha(1f).setDuration(180L).start()
        openButton.animate().alpha(0f).setDuration(120L).start()
        handler.removeCallbacks(progressTick)
        updateProgress()
        handler.post(progressTick)
        tonearm.setPlaying(playerManager.isPlaying, animate = false)
        updateVinylAnimation()
    }

    fun close() {
        if (!opened) return
        opened = false
        setDeleteMode(false, announce = false)
        android.util.Log.d("MindIsleMusic", "close drawer")
        handler.removeCallbacks(progressTick)
        progressAnimator?.cancel()
        stopVinylAnimation()
        val width = panel.width.takeIf { it > 0 } ?: drawerWidth()
        panel.animate()
            .translationX(-width.toFloat())
            .alpha(0.94f)
            .setDuration(180L)
            .withEndAction {
                panel.visibility = View.GONE
                scrim.visibility = View.GONE
            }
            .start()
        scrim.animate().alpha(0f).setDuration(160L).start()
        openButton.animate().alpha(0.72f).setDuration(160L).start()
    }

    fun openList() {
        open()
        list.post {
            val currentId = playerManager.currentTrack?.id
            val index = playerManager.getTracks().indexOfFirst { it.id == currentId }
            if (index >= 0) {
                list.smoothScrollToPosition(index)
            }
            list.requestFocus()
        }
    }

    fun renderPlayMode(mode: MusicPlayMode) {
        renderMode(mode)
    }

    fun addTracks(newTracks: List<MusicTrack>) {
        if (newTracks.isEmpty()) {
            Toast.makeText(activity, "没有读取到可用的音频文件", Toast.LENGTH_SHORT).show()
            return
        }
        val merged = (playerManager.getTracks() + newTracks)
            .distinctBy { it.uri }
            .sortedBy { it.addedAt }
        repository.saveTracks(merged)
        playerManager.setTracks(merged)
        refreshTracks()
        Toast.makeText(activity, "已添加 ${newTracks.size} 首音乐", Toast.LENGTH_SHORT).show()
    }

    fun onHostPaused() {
        handler.removeCallbacks(progressTick)
        progressAnimator?.cancel()
        stopVinylAnimation()
        repository.lastPosition = playerManager.currentPosition
    }

    fun onHostResumed() {
        if (opened) {
            handler.post(progressTick)
            updateVinylAnimation()
        }
    }

    fun release() {
        setDeleteMode(false, announce = false)
        deleteParticleOverlay.cancelBurst()
        handler.removeCallbacksAndMessages(null)
        progressAnimator?.cancel()
        stopVinylAnimation()
        playerManager.release()
    }

    override fun onTrackChanged(track: MusicTrack?) {
        if (renderedTrackId != track?.id) {
            tonearm.setPlaying(false)
            stopVinylAnimation()
            renderedTrackId = track?.id
        }
        if (track == null) {
            title.text = "还没有添加音乐"
            artist.text = "把喜欢的旋律放进心屿"
            totalTime.text = "00:00"
            currentTime.text = "00:00"
            progress.max = 1
            progress.progress = 0
            vinyl.setCover(null)
        } else {
            title.text = track.title
            artist.text = track.artist
            totalTime.text = formatDuration(track.duration.toInt())
            progress.max = track.duration.toInt().coerceAtLeast(1)
            vinyl.animate().cancel()
            vinyl.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .alpha(0.7f)
                .setDuration(100L)
                .withEndAction {
                    vinyl.setCover(track.coverPath)
                    vinyl.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(180L)
                        .start()
                }
                .start()
        }
        adapter.updateCurrent(track?.id)
        updateProgress()
    }

    override fun onPlaybackChanged(isPlaying: Boolean) {
        playPause.setImageResource(
            if (isPlaying) R.drawable.ic_music_pause else R.drawable.ic_music_play
        )
        playPause.contentDescription = if (isPlaying) "暂停" else "播放"
        tonearm.setPlaying(isPlaying)
        updateVinylAnimation()
    }

    override fun onError(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private fun refreshTracks() {
        val tracks = playerManager.getTracks()
        if (tracks.isEmpty()) deleteMode = false
        adapter.submit(tracks, playerManager.currentTrack?.id)
        adapter.setDeleteMode(deleteMode)
        emptyState.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (tracks.isEmpty()) View.GONE else View.VISIBLE
        renderDeleteMode()
    }

    private fun setDeleteMode(enabled: Boolean, announce: Boolean) {
        deleteMode = enabled && playerManager.getTracks().isNotEmpty()
        adapter.setDeleteMode(deleteMode)
        renderDeleteMode()
        if (announce) {
            Toast.makeText(
                activity,
                if (deleteMode) "删除模式：按住歌曲 2.5 秒" else "已退出删除模式",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun renderDeleteMode() {
        val hasTracks = playerManager.getTracks().isNotEmpty()
        // Keep the debug trigger available even when the list is empty.
        deleteCurrentButton.isEnabled = true
        deleteCurrentButton.alpha = 1f
        deleteCurrentButton.setBackgroundResource(
            if (deleteMode) {
                R.drawable.bg_music_delete_mode_button
            } else {
                R.drawable.bg_music_action_button
            }
        )
        deleteCurrentButton.imageTintList = ColorStateList.valueOf(
            if (deleteMode) {
                Color.rgb(217, 65, 83)
            } else {
                ContextCompat.getColor(activity, R.color.xingyue_text_secondary)
            }
        )
        deleteCurrentButton.contentDescription = when {
            deleteMode -> "退出音乐删除模式，长按测试粒子"
            hasTracks -> "进入音乐删除模式，长按测试粒子"
            else -> "长按测试删除粒子"
        }
    }

    private fun animateTrackDeletion(track: MusicTrack, itemView: View) {
        if (deletionAnimating) return
        deletionAnimating = true
        list.isEnabled = false

        prepareParticleOverlay {
            val bounds = itemBoundsInOverlay(itemView)
            Log.d(
                PARTICLE_TAG,
                "delete item left=${bounds.left} top=${bounds.top} " +
                    "width=${bounds.width()} height=${bounds.height()} " +
                    "overlay=${deleteParticleOverlay.width}x${deleteParticleOverlay.height}"
            )
            deleteParticleOverlay.burst(bounds) {
                deleteTrack(track)
                deletionAnimating = false
                list.isEnabled = true
            }
        }
    }

    private fun triggerParticleVisibilityTest() {
        prepareParticleOverlay {
            val overlayWidth = deleteParticleOverlay.width.toFloat()
            val overlayHeight = deleteParticleOverlay.height.toFloat()
            val centerX = overlayWidth / 2f
            val centerY = overlayHeight / 2f
            val bounds = RectF(
                centerX - dp(56),
                centerY - dp(28),
                centerX + dp(56),
                centerY + dp(28)
            )
            Log.d(
                PARTICLE_TAG,
                "TEST burst overlay=${overlayWidth.toInt()}x${overlayHeight.toInt()} " +
                    "center=($centerX,$centerY)"
            )
            Toast.makeText(activity, "粒子可见性测试", Toast.LENGTH_SHORT).show()
            deleteParticleOverlay.burst(bounds) {
                Log.d(PARTICLE_TAG, "TEST burst complete; no music deleted")
            }
        }
    }

    private fun prepareParticleOverlay(action: () -> Unit) {
        deleteParticleOverlay.visibility = View.VISIBLE
        deleteParticleOverlay.alpha = 1f
        deleteParticleOverlay.bringToFront()
        deleteParticleOverlay.requestLayout()
        deleteParticleOverlay.invalidate()
        deleteParticleOverlay.post {
            Log.d(
                PARTICLE_TAG,
                "overlay ready width=${deleteParticleOverlay.width} " +
                    "height=${deleteParticleOverlay.height} alpha=${deleteParticleOverlay.alpha} " +
                    "visibility=${deleteParticleOverlay.visibility}"
            )
            if (deleteParticleOverlay.width > 0 && deleteParticleOverlay.height > 0) {
                action()
            } else {
                Log.e(PARTICLE_TAG, "overlay still has zero size after layout")
                deletionAnimating = false
                list.isEnabled = true
                deleteParticleOverlay.visibility = View.GONE
            }
        }
    }

    private fun itemBoundsInOverlay(itemView: View): RectF {
        val itemLocation = IntArray(2)
        val overlayLocation = IntArray(2)
        itemView.getLocationOnScreen(itemLocation)
        deleteParticleOverlay.getLocationOnScreen(overlayLocation)
        return RectF(
            (itemLocation[0] - overlayLocation[0]).toFloat(),
            (itemLocation[1] - overlayLocation[1]).toFloat(),
            (itemLocation[0] - overlayLocation[0] + itemView.width).toFloat(),
            (itemLocation[1] - overlayLocation[1] + itemView.height).toFloat()
        )
    }

    private fun deleteTrack(track: MusicTrack) {
        playerManager.removeTrack(track.id)
        val remaining = playerManager.getTracks()
        repository.saveTracks(remaining)
        track.coverPath?.let { runCatching { File(it).delete() } }
        refreshTracks()
    }

    private fun renderMode(mode: MusicPlayMode) {
        modeButton.setImageResource(
            when (mode) {
                MusicPlayMode.ORDER -> R.drawable.ic_music_order
                MusicPlayMode.SHUFFLE -> R.drawable.ic_music_shuffle
                MusicPlayMode.REPEAT_ONE -> R.drawable.ic_music_repeat_one
            }
        )
        modeButton.contentDescription = "${modeLabel(mode)}播放，点击切换模式"
    }

    private fun modeLabel(mode: MusicPlayMode): String = when (mode) {
        MusicPlayMode.ORDER -> "顺序"
        MusicPlayMode.SHUFFLE -> "随机"
        MusicPlayMode.REPEAT_ONE -> "单曲"
    }

    private fun updateProgress(animate: Boolean = false) {
        val duration = playerManager.duration.coerceAtLeast(1)
        val position = playerManager.currentPosition.coerceIn(0, duration)
        progress.max = duration
        progressAnimator?.cancel()
        if (animate && progress.progress != position) {
            progressAnimator = ValueAnimator.ofInt(progress.progress, position).apply {
                this.duration = 460L
                interpolator = LinearInterpolator()
                addUpdateListener { progress.progress = it.animatedValue as Int }
                start()
            }
        } else {
            progress.progress = position
        }
        currentTime.text = formatDuration(position)
        totalTime.text = formatDuration(duration)
    }

    private fun updateVinylAnimation() {
        if (!opened || !playerManager.isPlaying) {
            stopVinylAnimation()
            return
        }
        if (vinylAnimator?.isRunning == true) return
        val startRotation = vinyl.rotation
        vinylAnimator = ObjectAnimator.ofFloat(vinyl, View.ROTATION, startRotation, startRotation + 360f).apply {
            duration = 16_000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopVinylAnimation() {
        vinylAnimator?.cancel()
        vinylAnimator = null
    }

    private fun drawerWidth(): Int =
        (root.width * CompanionSideMenuController.DRAWER_WIDTH_RATIO).toInt()

    private fun bindPressFeedback(vararg views: View) {
        views.forEach { view ->
            view.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN ->
                        target.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        target.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
                }
                false
            }
        }
    }

    private fun formatDuration(milliseconds: Int): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun dp(value: Int): Float =
        value * activity.resources.displayMetrics.density

    private companion object {
        const val PARTICLE_TAG = "MusicDeleteParticles"
    }
}
