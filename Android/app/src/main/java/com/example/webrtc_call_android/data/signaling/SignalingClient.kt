package com.example.webrtc_call_android.data.signaling

import android.util.Log
import com.example.webrtc_call_android.data.model.SignalMessage
import com.google.gson.Gson
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SignalingClient(
    private val serverUrl: String,
    private val userId: String,
    private val onMessageReceived: (SignalMessage) -> Unit,
    private val onConnected: (() -> Unit)? = null,
    private val onDisconnected: (() -> Unit)? = null
) {
    private val TAG = "SignalingClient"
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val disposables = CompositeDisposable()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val messageIdCounter = AtomicInteger(0)
    private var isConnected = false

    fun connect() {
        try {
            val request = Request.Builder()
                .url(serverUrl)
                .addHeader("userId", userId)
                .build()

            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connection opened")
                    isConnected = true
                    // Send STOMP CONNECT frame
                    sendStompConnect()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received message: $text")
                    handleStompMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error", t)
                    isConnected = false
                    onDisconnected?.invoke()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code - $reason")
                    isConnected = false
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code - $reason")
                    isConnected = false
                    onDisconnected?.invoke()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WebSocket", e)
        }
    }

    private fun sendStompConnect() {
        val connectFrame = "CONNECT\n" +
                "accept-version:1.1,1.0\n" +
                "heart-beat:10000,10000\n" +
                "userId:$userId\n" +
                "\n" +
                "\u0000"
        webSocket?.send(connectFrame)
    }

    private fun handleStompMessage(message: String) {
        if (!message.startsWith("MESSAGE")) {
            // Handle other STOMP frames (CONNECTED, ERROR, etc.)
            if (message.startsWith("CONNECTED")) {
                Log.d(TAG, "STOMP connected")
                subscribeToMessages()
                onConnected?.invoke()
            }
            return
        }

        // Parse STOMP MESSAGE frame
        val lines = message.split("\n")
        var bodyStart = -1
        for (i in lines.indices) {
            if (lines[i].isEmpty() && i < lines.size - 1) {
                bodyStart = i + 1
                break
            }
        }

        if (bodyStart > 0 && bodyStart < lines.size) {
            val body = lines.subList(bodyStart, lines.size)
                .joinToString("\n")
                .trimEnd('\u0000')
            
            try {
                val signalMessage = gson.fromJson(body, SignalMessage::class.java)
                Log.d(TAG, "Received signal - Type: ${signalMessage.type}, UserId: ${signalMessage.userId}, TargetUserId: ${signalMessage.targetUserId}")
                onMessageReceived(signalMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: $body", e)
            }
        }
    }

    private fun subscribeToMessages() {
        val subscribeFrame = "SUBSCRIBE\n" +
                "id:sub-${messageIdCounter.incrementAndGet()}\n" +
                "destination:/user/queue/signal\n" +
                "\n" +
                "\u0000"
        webSocket?.send(subscribeFrame)
    }

    fun sendSignal(message: SignalMessage) {
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected")
            return
        }

        try {
            val json = gson.toJson(message)
            Log.d(TAG, "Sending signal - Type: ${message.type}, UserId: ${message.userId}, TargetUserId: ${message.targetUserId}, RoomId: ${message.roomId}")
            val sendFrame = "SEND\n" +
                    "destination:/app/signal\n" +
                    "content-type:application/json\n" +
                    "\n" +
                    "$json\u0000"
            webSocket?.send(sendFrame)
            Log.d(TAG, "Signal sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending signal", e)
        }
    }

    fun disconnect() {
        disposables.clear()
        if (isConnected) {
            val disconnectFrame = "DISCONNECT\n" +
                    "\n" +
                    "\u0000"
            webSocket?.send(disconnectFrame)
        }
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
    }
}
