package org.enchant.core.calls.webrtc

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.enchant.core.calls.model.CallQualityStats
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver

class PeerConnectionWrapper(private val pc: PeerConnection) {
    private val _iceCandidates = MutableSharedFlow<IceCandidate>(replay = 0, extraBufferCapacity = 64)
    val iceCandidates: SharedFlow<IceCandidate> = _iceCandidates.asSharedFlow()

    private val _connectionState = MutableSharedFlow<PeerConnection.IceConnectionState>(replay = 1, extraBufferCapacity = 4)
    val connectionState: SharedFlow<PeerConnection.IceConnectionState> = _connectionState.asSharedFlow()

    private val _signalingState = MutableSharedFlow<PeerConnection.SignalingState>(replay = 1, extraBufferCapacity = 4)
    val signalingState: SharedFlow<PeerConnection.SignalingState> = _signalingState.asSharedFlow()

    private val _dataChannels = MutableSharedFlow<DataChannel>(replay = 0, extraBufferCapacity = 4)
    val dataChannels: SharedFlow<DataChannel> = _dataChannels.asSharedFlow()

    private val _remoteStreams = MutableSharedFlow<MediaStream>(replay = 0, extraBufferCapacity = 4)
    val remoteStreams: SharedFlow<MediaStream> = _remoteStreams.asSharedFlow()

    private val _qualityStats = MutableSharedFlow<CallQualityStats>(replay = 0, extraBufferCapacity = 16)
    val qualityStats: SharedFlow<CallQualityStats> = _qualityStats.asSharedFlow()

    private var localAudioTrackId: String? = null
    private var localVideoTrackId: String? = null

    val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            _iceCandidates.tryEmit(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}

        override fun onSignalingChange(state: PeerConnection.SignalingState) {
            _signalingState.tryEmit(state)
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            _connectionState.tryEmit(state)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}

        override fun onAddStream(stream: MediaStream) {
            _remoteStreams.tryEmit(stream)
        }

        override fun onRemoveStream(stream: MediaStream) {}

        override fun onDataChannel(channel: DataChannel) {
            _dataChannels.tryEmit(channel)
        }

        override fun onRenegotiationNeeded() {}

        override fun onAddTrack(receiver: RtpReceiver, tracks: Array<MediaStream>) {
            tracks.forEach { stream ->
                if (stream.audioTracks.isNotEmpty() || stream.videoTracks.isNotEmpty()) {
                    _remoteStreams.tryEmit(stream)
                }
            }
        }
    }

    fun addLocalTracks(audioTrackId: String, videoTrackId: String? = null) {
        localAudioTrackId = audioTrackId
        localVideoTrackId = videoTrackId
    }

    fun getLocalAudioTrackId(): String? = localAudioTrackId
    fun getLocalVideoTrackId(): String? = localVideoTrackId

    fun close() {
        pc.close()
    }

    fun dispose() {
        close()
        pc.dispose()
    }
}