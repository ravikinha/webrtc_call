package com.example.webrtc_call_android.ui

import android.Manifest
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.webrtc_call_android.viewmodel.CallViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.webrtc.*
import org.webrtc.RendererCommon.ScalingType

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(viewModel: CallViewModel) {
    val context = LocalContext.current
    val callState by viewModel.callState.collectAsState()
    
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(Unit) {
        permissionsState.launchMultiplePermissionRequest()
    }

    if (!permissionsState.allPermissionsGranted) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Please grant camera and microphone permissions")
        }
        return
    }

    var eglBaseContext by remember { mutableStateOf<EglBase.Context?>(null) }
    var localSurfaceView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteSurfaceView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    LaunchedEffect(Unit) {
        val eglBase = EglBase.create()
        eglBaseContext = eglBase.eglBaseContext
        
        val localView = SurfaceViewRenderer(context)
        localView.init(eglBase.eglBaseContext, null)
        localView.setMirror(true)
        localView.setEnableHardwareScaler(true)
        localSurfaceView = localView

        val remoteView = SurfaceViewRenderer(context)
        remoteView.init(eglBase.eglBaseContext, null)
        remoteView.setMirror(false)
        remoteView.setEnableHardwareScaler(true)
        remoteSurfaceView = remoteView

        eglBaseContext?.let { ctx ->
            localView?.let { local ->
                remoteView?.let { remote ->
                    viewModel.initializeWebRTC(ctx, local, remote)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            localSurfaceView?.release()
            remoteSurfaceView?.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Remote video (full screen)
        remoteSurfaceView?.let { view ->
            AndroidView(
                factory = { view },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Local video (picture-in-picture)
        localSurfaceView?.let { view ->
            AndroidView(
                factory = { view },
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }

        // Connection status
        if (!callState.isCallActive) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = "Status: ${callState.connectionState}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Room ID input
            if (!callState.isConnected) {
                var roomIdInput by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = roomIdInput,
                        onValueChange = { roomIdInput = it },
                        label = { Text("Room ID") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (roomIdInput.isNotBlank()) {
                                viewModel.connectToSignalingServer(
                                    "http://192.168.1.14:8080/ws",
                                    roomIdInput
                                )
                            }
                        }
                    ) {
                        Text("Join")
                    }
                }
            }

            // Control buttons
            if (callState.isConnected) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Audio toggle
                    FloatingActionButton(
                        onClick = { viewModel.toggleAudio() },
                        modifier = Modifier.size(56.dp),
                        containerColor = if (callState.isAudioEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    ) {
                        Icon(
                            imageVector = if (callState.isAudioEnabled) 
                                Icons.Default.Mic 
                            else 
                                Icons.Default.MicOff,
                            contentDescription = "Toggle Audio"
                        )
                    }

                    // Video toggle
                    FloatingActionButton(
                        onClick = { viewModel.toggleVideo() },
                        modifier = Modifier.size(56.dp),
                        containerColor = if (callState.isVideoEnabled) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    ) {
                        Icon(
                            imageVector = if (callState.isVideoEnabled) 
                                Icons.Default.Videocam 
                            else 
                                Icons.Default.VideocamOff,
                            contentDescription = "Toggle Video"
                        )
                    }

                    // Switch camera
                    FloatingActionButton(
                        onClick = { viewModel.switchCamera() },
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera"
                        )
                    }

                    // End call
                    FloatingActionButton(
                        onClick = { viewModel.endCall() },
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call"
                        )
                    }
                }
            }
        }
    }
}

