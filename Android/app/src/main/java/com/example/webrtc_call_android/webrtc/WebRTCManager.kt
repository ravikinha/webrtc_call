package com.example.webrtc_call_android.webrtc

import android.content.Context
import android.util.Log
import com.example.webrtc_call_android.data.model.SignalMessage
import org.webrtc.*
import org.webrtc.PeerConnection.*

class WebRTCManager(
    private val context: Context,
    private val onLocalStream: (VideoTrack, AudioTrack) -> Unit,
    private val onRemoteStream: (VideoTrack?, AudioTrack?) -> Unit,
    private val onIceCandidateCallback: (IceCandidate) -> Unit,
    private val onConnectionStateChange: (PeerConnection.IceConnectionState) -> Unit
) {
    private val TAG = "WebRTCManager"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var currentCameraName: String? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)

        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        peerConnectionFactory = factory
    }

    fun initializePeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_CONTINUALLY

        val constraints = MediaConstraints()

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    onIceCandidateCallback(iceCandidate)
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    onConnectionStateChange(state)
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d(TAG, "📺 onAddStream called (legacy callback) - stream id: ${mediaStream.id}")
                    val videoTrack = mediaStream.videoTracks.firstOrNull()
                    val audioTrack = mediaStream.audioTracks.firstOrNull()

                    // Check track IDs to avoid processing our own local tracks
                    val localVideoId = localVideoTrack?.id()
                    val localAudioId = localAudioTrack?.id()
                    
                    if (videoTrack?.id() == localVideoId || audioTrack?.id() == localAudioId) {
                        Log.d(TAG, "⏭️ Skipping our own local stream (track ID match)")
                        return
                    }

                    videoTrack?.let {
                        Log.d(TAG, "✅ Video track found in stream (ID: ${it.id()})")
                        it.setEnabled(true)
                        remoteVideoTrack = it
                    }
                    audioTrack?.let {
                        Log.d(TAG, "✅ Audio track found in stream (ID: ${it.id()})")
                        it.setEnabled(true)
                        remoteAudioTrack = it
                    }

                    if (videoTrack != null || audioTrack != null) {
                        onRemoteStream(
                            videoTrack ?: remoteVideoTrack,
                            audioTrack ?: remoteAudioTrack
                        )
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver) {
                    Log.d(TAG, "📡 onTrack called - media type: ${transceiver.mediaType}, direction: ${transceiver.direction}")

                    // Check if this is a receiving transceiver (has incoming track)
                    val track = transceiver.receiver.track()
                    if (track == null) {
                        Log.e(TAG, "❌ No track found in transceiver")
                        return
                    }

                    // Check track ID to avoid processing our own local tracks
                    val trackId = track.id()
                    val localVideoId = localVideoTrack?.id()
                    val localAudioId = localAudioTrack?.id()
                    
                    Log.d(TAG, "Track ID: $trackId, Local video ID: $localVideoId, Local audio ID: $localAudioId")
                    
                    if (trackId == localVideoId || trackId == localAudioId) {
                        Log.d(TAG, "⏭️ Skipping track with ID $trackId (matches our local track)")
                        return
                    }

                    when (track) {
                        is VideoTrack -> {
                            Log.d(TAG, "✅ Remote video track received via transceiver (ID: $trackId)")
                            track.setEnabled(true)
                            remoteVideoTrack = track
                            onRemoteStream(track, remoteAudioTrack)
                        }
                        is AudioTrack -> {
                            Log.d(TAG, "✅ Remote audio track received via transceiver (ID: $trackId)")
                            track.setEnabled(true)
                            remoteAudioTrack = track
                            onRemoteStream(remoteVideoTrack, track)
                        }
                    }
                }
            }
        )
    }

    fun startLocalVideo(eglBaseContext: EglBase.Context, surfaceView: SurfaceViewRenderer) {
        val videoSource = peerConnectionFactory?.createVideoSource(false)
        localVideoSource = videoSource

        videoCapturer = createCameraCapturer()
        videoCapturer?.initialize(
            SurfaceTextureHelper.create("CameraThread", eglBaseContext),
            context,
            videoSource?.capturerObserver
        )
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory?.createVideoTrack("local_video", videoSource)
        localVideoTrack?.addSink(surfaceView)

        val audioConstraints = MediaConstraints()
        localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio", localAudioSource)

        localVideoTrack?.let { videoTrack ->
            localAudioTrack?.let { audioTrack ->
                onLocalStream(videoTrack, audioTrack)
            }
        }

        // Add tracks with stream IDs for compatibility
        val streamIds = listOf("stream")
        localVideoTrack?.let { 
            peerConnection?.addTrack(it, streamIds)
            Log.d(TAG, "Local video track added to peer connection")
        }
        localAudioTrack?.let { 
            peerConnection?.addTrack(it, streamIds)
            Log.d(TAG, "Local audio track added to peer connection")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                currentCameraName = deviceName
                return enumerator.createCapturer(deviceName, null)
            }
        }

        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                currentCameraName = deviceName
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Error creating remote description: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Error setting remote description: $p0")
            }
        }, sdp)
    }

    fun setLocalDescription(sdp: SessionDescription) {
        peerConnection?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Local description set successfully")
                peerConnection?.localDescription?.let { sendLocalDescription(it) }
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Error creating local description: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Error setting local description: $p0")
            }
        }, sdp)
    }

    private var onLocalDescriptionCreated: ((SessionDescription) -> Unit)? = null
    
    fun setOnLocalDescriptionCreated(callback: (SessionDescription) -> Unit) {
        onLocalDescriptionCreated = callback
    }
    
    private fun sendLocalDescription(sdp: SessionDescription) {
        Log.d(TAG, "sendLocalDescription called - type: ${sdp.type}")
        onLocalDescriptionCreated?.invoke(sdp)
    }

    fun createOffer() {
        Log.d(TAG, "createOffer() called")
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(TAG, "Offer created successfully")
                sdp?.let { setLocalDescription(it) }
            }
            override fun onSetSuccess() {
                Log.d(TAG, "Offer set as local description successfully")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Error creating offer: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Error setting offer: $p0")
            }
        }, constraints)
    }

    fun createAnswer() {
        Log.d(TAG, "createAnswer() called")
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(TAG, "Answer created successfully")
                sdp?.let { setLocalDescription(it) }
            }
            override fun onSetSuccess() {
                Log.d(TAG, "Answer set as local description successfully")
            }
            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "Error creating answer: $p0")
            }
            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "Error setting answer: $p0")
            }
        }, constraints)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun setRemoteVideoTrack(videoTrack: VideoTrack?, surfaceView: SurfaceViewRenderer) {
        videoTrack?.addSink(surfaceView)
    }

    fun switchCamera() {
        videoCapturer?.let { capturer ->
            val cameraEnumerator = Camera2Enumerator(context)
            val deviceNames = cameraEnumerator.deviceNames
            
            if (deviceNames.size < 2) {
                Log.w(TAG, "Only one camera available, cannot switch")
                return
            }
            
            val currentDeviceName = currentCameraName
            val currentIndex = if (currentDeviceName != null) {
                deviceNames.indexOf(currentDeviceName)
            } else {
                -1
            }
            
            val newIndex = if (currentIndex >= 0 && currentIndex < deviceNames.size - 1) {
                currentIndex + 1
            } else {
                0 // Wrap around to first camera
            }
            
            val newDeviceName = deviceNames[newIndex]
            currentCameraName = newDeviceName
            
            capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
                override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                    Log.d(TAG, "Camera switched: isFrontCamera=$isFrontCamera")
                }
                override fun onCameraSwitchError(error: String?) {
                    Log.e(TAG, "Error switching camera: $error")
                    // Revert on error
                    currentCameraName = deviceNames.getOrNull(currentIndex)
                }
            }, newDeviceName)
        }
    }

    fun toggleAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun disconnect() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        localVideoSource?.dispose()
        localAudioSource?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
    }
}

