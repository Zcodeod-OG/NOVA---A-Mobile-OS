package com.nova.runtime.app.action

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings

class SystemHardwareController(
    private val context: Context
) {
    private var isTorchOn = false

    fun toggleFlashlight(): ActionExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraManager != null && cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
                ActionExecutionResult(
                    isSuccess = true,
                    appName = "Flashlight",
                    message = "Toggled Flashlight ${if (isTorchOn) "ON" else "OFF"}"
                )
            } else {
                ActionExecutionResult(false, "Flashlight", "Flashlight hardware unavailable")
            }
        } catch (e: Exception) {
            ActionExecutionResult(false, "Flashlight", "Failed to toggle flashlight: ${e.localizedMessage}")
        }
    }

    fun setVolume(levelPercent: Int): ActionExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (maxVolume * (levelPercent / 100.0)).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
            ActionExecutionResult(true, "Volume Control", "Set media volume to $levelPercent%")
        } catch (e: Exception) {
            ActionExecutionResult(false, "Volume Control", "Failed to adjust volume")
        }
    }

    fun openWifiSettings(): ActionExecutionResult {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return ActionExecutionResult(true, "Wi-Fi Settings", "Opened Wi-Fi settings")
    }

    fun openBluetoothSettings(): ActionExecutionResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return ActionExecutionResult(true, "Bluetooth Settings", "Opened Bluetooth settings")
    }

    fun openDisplaySettings(): ActionExecutionResult {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
        return ActionExecutionResult(true, "Display Settings", "Opened Display settings")
    }
}
