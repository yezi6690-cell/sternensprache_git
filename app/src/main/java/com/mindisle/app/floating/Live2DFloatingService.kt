package com.mindisle.app.floating

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.mindisle.app.live2d.Live2DModels

class Live2DFloatingService : Service() {
    private lateinit var settings: FloatingSettingsManager
    private lateinit var windowController: FloatingWindowController

    override fun onCreate() {
        super.onCreate()
        settings = FloatingSettingsManager(this)
        windowController = FloatingWindowController(this, settings)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.enabled = false
                windowController.remove()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESET_POSITION -> {
                settings.resetPosition()
                windowController.resetPosition()
                return START_STICKY
            }
        }

        if (!canDrawOverlays()) {
            settings.enabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        settings.enabled = true
        val selectedModel = Live2DModels.getSelectedModel(this)
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
            ?: settings.floatingModelId
            ?: selectedModel.id
        val variant = intent?.getStringExtra(EXTRA_VARIANT)
            ?: settings.floatingVariant
        settings.saveFloatingSelection(modelId, variant)
        Log.d("FloatingPet", "service modelId=$modelId, variant=$variant")
        windowController.show(modelId, variant) { stopSelf() }
        if (intent?.action == ACTION_UPDATE) {
            windowController.updateFromSettings()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        windowController.remove()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    companion object {
        const val ACTION_START = "com.mindisle.app.floating.START"
        const val ACTION_STOP = "com.mindisle.app.floating.STOP"
        const val ACTION_UPDATE = "com.mindisle.app.floating.UPDATE"
        const val ACTION_RESET_POSITION = "com.mindisle.app.floating.RESET_POSITION"
        const val EXTRA_MODEL_ID = "modelId"
        const val EXTRA_VARIANT = "variant"
    }
}
