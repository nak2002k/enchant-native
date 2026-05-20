package org.enchant.core.calls.webrtc

import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume

class SdpHandler {

    suspend fun createOffer(pc: PeerConnection): String? = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    pc.setLocalDescription(NoopSdpObserver(), sdp)
                    cont.resume(sdp.description)
                } else {
                    cont.resume(null)
                }
            }
            override fun onCreateFailure(error: String?) {
                cont.resume(null)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun createAnswer(pc: PeerConnection): String? = suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                    pc.setLocalDescription(NoopSdpObserver(), sdp)
                    cont.resume(sdp.description)
                } else {
                    cont.resume(null)
                }
            }
            override fun onCreateFailure(error: String?) {
                cont.resume(null)
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun setRemoteDescription(
        pc: PeerConnection,
        sdp: String,
        type: SessionDescription.Type
    ): Boolean = suspendCancellableCoroutine { cont ->
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() { cont.resume(true) }
            override fun onSetFailure(error: String?) { cont.resume(false) }
        }, SessionDescription(type, sdp))
    }

    private class NoopSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetSuccess() {}
        override fun onSetFailure(p0: String?) {}
    }
}