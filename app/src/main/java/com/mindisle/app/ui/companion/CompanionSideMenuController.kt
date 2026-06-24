package com.mindisle.app.ui.companion

import android.view.View

class CompanionSideMenuController(
    private val root: View,
    private val menu: View,
    private val scrim: View,
    private val handle: View,
    private val onOpened: () -> Unit = {},
    private val onClosed: () -> Unit = {}
) {
    companion object {
        const val DRAWER_WIDTH_RATIO = 0.7f
    }

    private var opened = false

    val isOpen: Boolean
        get() = opened

    fun attach() {
        root.post {
            val params = menu.layoutParams
            params.width = (root.width * DRAWER_WIDTH_RATIO).toInt()
            menu.layoutParams = params
            menu.translationX = params.width.toFloat()
        }
        handle.setOnClickListener { open() }
        scrim.setOnClickListener { close() }
    }

    fun open() {
        if (opened) return
        opened = true
        onOpened()
        val width = menu.width.takeIf { it > 0 }
            ?: (root.width * DRAWER_WIDTH_RATIO).toInt()
        menu.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.visibility = View.VISIBLE
        // The compose area is attached to the root at runtime. Reassert the
        // drawer stack whenever it opens so it always receives touch first.
        scrim.bringToFront()
        menu.bringToFront()
        menu.translationX = width.toFloat()
        menu.animate().translationX(0f).setDuration(220L).start()
        scrim.animate().alpha(1f).setDuration(180L).start()
        handle.animate().alpha(0f).setDuration(120L).start()
    }

    fun close() {
        if (!opened) return
        opened = false
        val width = menu.width.takeIf { it > 0 }
            ?: (root.width * DRAWER_WIDTH_RATIO).toInt()
        menu.animate()
            .translationX(width.toFloat())
            .setDuration(200L)
            .withEndAction {
                menu.visibility = View.GONE
                scrim.visibility = View.GONE
                onClosed()
            }
            .start()
        scrim.animate().alpha(0f).setDuration(180L).start()
        handle.animate().alpha(1f).setDuration(160L).start()
    }

}
