package com.example.webrtc_call_android.viewmodel

import android.app.Application
import android.util.Log
import com.google.gson.Gson
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.webrtc_call_android.data.model.SignalMessage
import com.example.webrtc_call_android.data.signaling.SignalingClient
import com.example.webrtc_call_android.webrtc.WebRTCManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*
import java.util.UUID

data class CallState(
    val isConnected: Boolean = false,
    val isCallActive: Boolean = false,
    val connectionState: String = "Disconnected",
    val isAudioEnabled: Boolean = true,
    val isVideoEnabled: Boolean = true,
    val roomId: String = "",
    val userId: String = "",
    val targetUserId: String? = null,
    val errorMessage: String? = null,
    val isConnecting: Boolean = false
)

class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "CallViewModel"
    
    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var signalingClient: SignalingClient? = null
    private var webRTCManager: WebRTCManager? = null
    private var eglBase: EglBase? = null
    private var localSdp: SessionDescription? = null
    private val gson = Gson()

    init {
        val userId = UUID.randomUUID().toString()
        _callState.value = _callState.value.copy(userId = userId)
    }

    fun initializeWebRTC(
        eglBaseContext: EglBase.Context,
        localSurfaceView: SurfaceViewRenderer,
        remoteSurfaceView: SurfaceViewRenderer
    ) {
        eglBase = EglBase.create(eglBaseContext)
        
        webRTCManager = WebRTCManager(
            context = getApplication(),
            onLocalStream = { videoTrack: VideoTrack, audioTrack: AudioTrack ->
                // Local stream ready
            },
            onRemoteStream = { videoTrack: VideoTrack?, audioTrack: AudioTrack? ->
                viewModelScope.launch {
                    videoTrack?.let { 
                        webRTCManager?.setRemoteVideoTrack(it, remoteSurfaceView)
                    }
                }
            },
            onIceCandidateCallback = { iceCandidate: IceCandidate ->
                sendIceCandidate(iceCandidate)
            },
            onConnectionStateChange = { state ->
                viewModelScope.launch {
                    _callState.value = _callState.value.copy(
                        connectionState = state.name,
                        isCallActive = state == PeerConnection.IceConnectionState.CONNECTED
                    )
                }
            }
        )
        
        webRTCManager?.setOnLocalDescriptionCreated { sdp ->
            when (sdp.type) {
                SessionDescription.Type.OFFER -> sendOffer(sdp)
                SessionDescription.Type.ANSWER -> sendAnswer(sdp)
                else -> {}
            }
        }

        webRTCManager?.initializePeerConnection()
        webRTCManager?.startLocalVideo(eglBaseContext, localSurfaceView)
    }

    fun connectToSignalingServer(serverUrl: String, roomId: String) {
        val userId = _callState.value.userId
        viewModelScope.launch {
            _callState.value = _callState.value.copy(
                roomId = roomId,
                isConnecting = true,
                errorMessage = null,
                connectionState = "Connecting..."
            )
        }

        // Convert http:// to ws:// or https:// to wss:// for WebSocket
        val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://")

        signalingClient = SignalingClient(
            serverUrl = wsUrl,
            userId = userId,
            onMessageReceived = { message ->
                handleSignalingMessage(message)
            },
            onConnected = {
                viewModelScope.launch {
                    _callState.value = _callState.value.copy(
                        isConnected = true,
                        isConnecting = false,
                        errorMessage = null,
                        connectionState = "Connected"
                    )
                    // Join room after STOMP connection is established
                    joinRoom(roomId)
                }
            },
            onDisconnected = {
                viewModelScope.launch {
                    _callState.value = _callState.value.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = "Disconnected from server",
                        connectionState = "Disconnected"
                    )
                }
            }
        )

        try {
            signalingClient?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to signaling server", e)
            viewModelScope.launch {
                _callState.value = _callState.value.copy(
                    isConnecting = false,
                    errorMessage = "Failed to connect: ${e.message}",
                    connectionState = "Connection Failed"
                )
            }
        }
    }
    
    fun clearError() {
        viewModelScope.launch {
            _callState.value = _callState.value.copy(errorMessage = null)
        }
    }

    private fun joinRoom(roomId: String) {
        val message = SignalMessage(
            type = "join-room",
            roomId = roomId,
            userId = _callState.value.userId
        )
        signalingClient?.sendSignal(message)
    }

    private fun handleSignalingMessage(message: SignalMessage) {
        Log.d(TAG, "handleSignalingMessage - type: ${message.type}, userId: ${message.userId}, targetUserId: ${message.targetUserId}")
        when (message.type) {
            "user-joined" -> {
                val targetUserId = message.userId
                Log.d(TAG, "User joined - targetUserId: $targetUserId, myUserId: ${_callState.value.userId}")
                if (targetUserId != null && targetUserId != _callState.value.userId) {
                    // Update state first synchronously, then create offer
                    _callState.value = _callState.value.copy(targetUserId = targetUserId)
                    Log.d(TAG, "Updated state with targetUserId: $targetUserId, creating offer...")
                    // Create offer when another user joins
                    webRTCManager?.createOffer()
                }
            }
            "offer" -> {
                try {
                    Log.d(TAG, "Received offer from userId: ${message.userId}")
                    val dataJson = gson.toJson(message.data)
                    val sdpData = gson.fromJson(dataJson, Map::class.java) as? Map<*, *>
                    sdpData?.let {
                        val sdpString = it["sdp"] as? String ?: ""
                        if (sdpString.isNotEmpty()) {
                            // Set targetUserId if not already set (synchronously)
                            if (_callState.value.targetUserId == null) {
                                _callState.value = _callState.value.copy(targetUserId = message.userId)
                                Log.d(TAG, "Set targetUserId from offer: ${message.userId}")
                            }
                            val description = SessionDescription(
                                SessionDescription.Type.OFFER,
                                sdpString
                            )
                            webRTCManager?.setRemoteDescription(description)
                            webRTCManager?.createAnswer()
                            Log.d(TAG, "Set remote description and creating answer")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling offer", e)
                }
            }
            "answer" -> {
                try {
                    val dataJson = gson.toJson(message.data)
                    val sdpData = gson.fromJson(dataJson, Map::class.java) as? Map<*, *>
                    sdpData?.let {
                        val sdpString = it["sdp"] as? String ?: ""
                        if (sdpString.isNotEmpty()) {
                            val description = SessionDescription(
                                SessionDescription.Type.ANSWER,
                                sdpString
                            )
                            webRTCManager?.setRemoteDescription(description)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling answer", e)
                }
            }
            "ice-candidate" -> {
                try {
                    val dataJson = gson.toJson(message.data)
                    val candidateData = gson.fromJson(dataJson, Map::class.java) as? Map<*, *>
                    candidateData?.let {
                        val candidate = it["candidate"] as? String ?: ""
                        val sdpMid = it["sdpMid"] as? String ?: ""
                        val sdpMLineIndex = when (val index = it["sdpMLineIndex"]) {
                            is Double -> index.toInt()
                            is Int -> index
                            else -> 0
                        }
                        if (candidate.isNotEmpty()) {
                            val iceCandidate = org.webrtc.IceCandidate(
                                sdpMid,
                                sdpMLineIndex,
                                candidate
                            )
                            webRTCManager?.addIceCandidate(iceCandidate)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling ice-candidate", e)
                }
            }
        }
    }

    private fun sendIceCandidate(iceCandidate: org.webrtc.IceCandidate) {
        val targetUserId = _callState.value.targetUserId
        if (targetUserId != null) {
            val candidateData = mapOf(
                "candidate" to iceCandidate.sdp,
                "sdpMid" to iceCandidate.sdpMid,
                "sdpMLineIndex" to iceCandidate.sdpMLineIndex
            )
            val message = SignalMessage(
                type = "ice-candidate",
                roomId = _callState.value.roomId,
                userId = _callState.value.userId,
                targetUserId = targetUserId,
                data = candidateData
            )
            signalingClient?.sendSignal(message)
        }
    }

    fun sendOffer(sdp: SessionDescription) {
        localSdp = sdp
        val targetUserId = _callState.value.targetUserId
        Log.d(TAG, "sendOffer called - targetUserId: $targetUserId")
        if (targetUserId != null) {
            val sdpData = mapOf(
                "type" to sdp.type.canonicalForm(),
                "sdp" to sdp.description
            )
            val message = SignalMessage(
                type = "offer",
                roomId = _callState.value.roomId,
                userId = _callState.value.userId,
                targetUserId = targetUserId,
                data = sdpData
            )
            Log.d(TAG, "Sending offer to targetUserId: $targetUserId in room: ${_callState.value.roomId}")
            signalingClient?.sendSignal(message)
        } else {
            Log.e(TAG, "Cannot send offer - targetUserId is null")
        }
    }

    fun sendAnswer(sdp: SessionDescription) {
        localSdp = sdp
        val targetUserId = _callState.value.targetUserId
        Log.d(TAG, "sendAnswer called - targetUserId: $targetUserId")
        if (targetUserId != null) {
            val sdpData = mapOf(
                "type" to sdp.type.canonicalForm(),
                "sdp" to sdp.description
            )
            val message = SignalMessage(
                type = "answer",
                roomId = _callState.value.roomId,
                userId = _callState.value.userId,
                targetUserId = targetUserId,
                data = sdpData
            )
            Log.d(TAG, "Sending answer to targetUserId: $targetUserId in room: ${_callState.value.roomId}")
            signalingClient?.sendSignal(message)
        } else {
            Log.e(TAG, "Cannot send answer - targetUserId is null")
        }
    }

    fun toggleAudio() {
        val newState = !_callState.value.isAudioEnabled
        webRTCManager?.toggleAudio(newState)
        _callState.value = _callState.value.copy(isAudioEnabled = newState)
    }

    fun toggleVideo() {
        val newState = !_callState.value.isVideoEnabled
        webRTCManager?.toggleVideo(newState)
        _callState.value = _callState.value.copy(isVideoEnabled = newState)
    }

    fun switchCamera() {
        webRTCManager?.switchCamera()
    }

    fun endCall() {
        signalingClient?.disconnect()
        webRTCManager?.disconnect()
        eglBase?.release()
        eglBase = null
        viewModelScope.launch {
            _callState.value = CallState(userId = _callState.value.userId)
        }
    }
}

