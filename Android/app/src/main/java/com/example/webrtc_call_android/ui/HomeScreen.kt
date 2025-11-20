package com.example.webrtc_call_android.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.webrtc_call_android.viewmodel.CallViewModel

@Composable
fun HomeScreen(
    onJoinCall: (serverUrl: String, roomId: String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: CallViewModel = viewModel()
    val callState by viewModel.callState.collectAsState()
    
    var serverUrl by remember { mutableStateOf("http://192.168.1.14:8080/ws") }
    var roomId by remember { mutableStateOf("") }
    
    // Show error toast when errorMessage is set
    LaunchedEffect(callState.errorMessage) {
        callState.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    
    // Show connecting toast
    LaunchedEffect(callState.isConnecting) {
        if (callState.isConnecting) {
            Toast.makeText(context, "Connecting to server...", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Icon/Logo
            Icon(
                imageVector = Icons.Default.VideoCall,
                contentDescription = "Video Call",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // App Title
            Text(
                text = "WebRTC Video Call",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Enter server URL and room ID to join a call",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Server URL Input
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("http://your-server:8080/ws") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Room ID Input
            OutlinedTextField(
                value = roomId,
                onValueChange = { roomId = it },
                label = { Text("Room ID") },
                placeholder = { Text("Enter room ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Join Call Button
            Button(
                onClick = {
                    if (serverUrl.isNotBlank() && roomId.isNotBlank()) {
                        onJoinCall(serverUrl, roomId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = serverUrl.isNotBlank() && roomId.isNotBlank() && !callState.isConnecting
            ) {
                if (callState.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (callState.isConnecting) "Connecting..." else "Join Call",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Info Text
            Text(
                text = "Make sure your camera and microphone permissions are enabled",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

