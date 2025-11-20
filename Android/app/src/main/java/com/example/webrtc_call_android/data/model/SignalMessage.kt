package com.example.webrtc_call_android.data.model

data class SignalMessage(
    val type: String, // offer, answer, ice-candidate, join-room, leave-room
    val roomId: String? = null,
    val userId: String? = null,
    val targetUserId: String? = null,
    val data: Any? = null
)

