package org.enchant.core.calls.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.webrtc.*
import java.util.UUID
import javax.inject.Inject

class MediaStreamManager(
    private val context: Context,
    private val engine: WebRtcEngine
) {
    private var currentCapturer: CameraVideoCapturer? = null
    private var currentVideoSource: VideoSource? = null
    private var localStream: MediaStream? = null
    private var audioTrack: AudioTrack? = null
    private var videoTrack: VideoTrack? = null

    fun createLocalStream(includeVideo: Boolean): MediaStream? {
        val factory = engine.peerConnectionFactory ?: run {
            Log.e("MediaStreamManager", "Factory not ready")
            return null
        }

        val audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("audio_${UUID.randomUUID()}", audioSource)

        val stream = factory.createLocalMediaStream("stream_${UUID.randomUUID()}")
        stream.addTrack(audioTrack!!)
        localStream = stream

        if (includeVideo) {
            addVideoTrack(factory)
        }

        return stream
    }

    fun setAudioEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    fun setVideoEnabled(enabled: Boolean) {
        videoTrack?.setEnabled(enabled)
    }

    fun addVideo(): Boolean {
        if (videoTrack != null) {
            setVideoEnabled(true)
            return true
        }
        val factory = engine.peerConnectionFactory ?: return false
        return addVideoTrack(factory) != null
    }

    fun removeVideo() {
        videoTrack?.setEnabled(false)
        currentCapturer?.stopCapture()
        currentCapturer?.dispose()
        currentCapturer = null
        currentVideoSource = null
        videoTrack = null
    }

    fun switchCamera() {
        currentCapturer?.switchCamera(null)
    }

    fun getVideoTrack(): VideoTrack? = videoTrack

    fun getLocalStream(): MediaStream? = localStream

    fun release() {
        currentCapturer?.stopCapture()
        currentCapturer?.dispose()
        currentCapturer = null
        currentVideoSource = null
        videoTrack = null
        audioTrack = null
        localStream = null
    }

    private fun addVideoTrack(factory: PeerConnectionFactory): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("MediaStreamManager", "Camera permission not granted")
            return false
        }

        return try {
            val enumerator = Camera2Enumerator(context)
            val cameraName = enumerator.deviceNames.firstOrNull {
                enumerator.isFrontFacing(it)
            } ?: enumerator.deviceNames.firstOrNull {
                enumerator.isBackFacing(it)
            } ?: return false

            val capturer = enumerator.createCapturer(cameraName, null)
            currentCapturer = capturer

            val videoSource = factory.createVideoSource(capturer.isScreencast == true)
            currentVideoSource = videoSource
            capturer.startCapture(1280, 720, 30)

            videoTrack = factory.createVideoTrack("video_${UUID.randomUUID()}", videoSource)
            localStream?.addTrack(videoTrack!!)
            true
        } catch (e: Exception) {
            Log.e("MediaStreamManager", "Failed to add video: ${e.message}")
            false
        }
    }
}