package com.example.webrtc_call_android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.webrtc_call_android.ui.CallScreen
import com.example.webrtc_call_android.ui.HomeScreen
import com.example.webrtc_call_android.ui.theme.WebRTCTheme
import com.example.webrtc_call_android.viewmodel.CallViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebRTCTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CallViewModel = viewModel()
                    val callState by viewModel.callState.collectAsState()
                    
                    // Navigate to CallScreen only when connected
                    if (callState.isConnected) {
                        CallScreen(viewModel = viewModel)
                    } else {
                        HomeScreen(
                            onJoinCall = { serverUrl, roomId ->
                                viewModel.connectToSignalingServer(serverUrl, roomId)
                            }
                        )
                    }
                }
            }
        }
    }
}

