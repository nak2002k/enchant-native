package org.enchant.core.calls.audio

import android.media.AudioManager
import javax.inject.Inject

class AudioRouter(
    private val audioManager: AudioManager
) {
    fun selectDevice(device: org.enchant.core.calls.model.AudioDevice) {
        when (device) {
            org.enchant.core.calls.model.AudioDevice.SPEAKER -> {
                audioManager.isSpeakerphoneOn = true
                audioManager.isBluetoothScoOn = false
            }
            org.enchant.core.calls.model.AudioDevice.EARPIECE -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = false
            }
            org.enchant.core.calls.model.AudioDevice.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            org.enchant.core.calls.model.AudioDevice.WIRED_HEADSET -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.isBluetoothScoOn = false
            }
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        audioManager.isSpeakerphoneOn = on
    }

    fun stopBluetoothSco() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
    }
}