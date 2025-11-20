# WebRTC Call Architecture

## System Overview

```
┌─────────────────┐                           ┌─────────────────┐
│   iOS Client    │                           │ Android Client  │
│                 │                           │                 │
│  ┌───────────┐  │                           │  ┌───────────┐  │
│  │ CallView  │  │                           │  │CallScreen │  │
│  │  (SwiftUI)│  │                           │  │ (Compose) │  │
│  └─────┬─────┘  │                           │  └─────┬─────┘  │
│        │        │                           │        │        │
│  ┌─────▼─────┐  │                           │  ┌─────▼─────┐  │
│  │CallView   │  │                           │  │CallView   │  │
│  │  Model    │◄─┼───────────┐   ┌──────────┼─►│  Model    │  │
│  └─────┬─────┘  │           │   │          │  └─────┬─────┘  │
│        │        │           │   │          │        │        │
│  ┌─────▼─────┐  │       ┌───▼───▼───┐     │  ┌─────▼─────┐  │
│  │  WebRTC   │  │       │  Signaling │     │  │  WebRTC   │  │
│  │  Manager  │  │       │   Server   │     │  │  Manager  │  │
│  └─────┬─────┘  │       │  (Spring)  │     │  └─────┬─────┘  │
│        │        │       └─────────────┘    │        │        │
│  ┌─────▼─────┐  │                           │  ┌─────▼─────┐  │
│  │RTCPeer    │  │                           │  │PeerConn   │  │
│  │Connection │◄─┼───────────────────────────┼─►│ection     │  │
│  └───────────┘  │    WebRTC P2P Connection │  └───────────┘  │
└─────────────────┘                           └─────────────────┘
```

## Video Track Flow

### Scenario 1: Android Joins First

```
Step 1: Android Joins Room
┌─────────┐                  ┌────────┐
│ Android │─────join-room───►│ Server │
└─────────┘                  └────────┘

Step 2: iOS Joins and Gets Notification
┌─────────┐                  ┌────────┐                  ┌─────┐
│   iOS   │◄────user-joined──│ Server │◄────join-room────│ iOS │
└─────────┘                  └────────┘                  └─────┘

Step 3: iOS Creates Offer
┌─────┐                      ┌────────┐                  ┌─────────┐
│ iOS │───createOffer()────► │ Server │─────offer──────► │ Android │
└─────┘                      └────────┘                  └─────────┘
        Local tracks added                              setRemoteDescription()

Step 4: Android Creates Answer
┌─────────┐                  ┌────────┐                  ┌─────┐
│ Android │─────answer──────►│ Server │─────answer─────► │ iOS │
└─────────┘                  └────────┘                  └─────┘
                                                       setRemoteDescription()

Step 5: ICE Candidates Exchange
┌─────┐                      ┌────────┐                  ┌─────────┐
│ iOS │◄────ice-candidate────│ Server │◄───ice-candidate──│ Android │
└─────┘                      └────────┘                  └─────────┘

Step 6: Track Reception (THE FIX)
iOS Side:
┌──────────────────────────────────────────────────────┐
│ didStartReceivingOn transceiver (NEW)               │
│   ↓                                                   │
│ Get video track from transceiver.receiver           │
│   ↓                                                   │
│ Enable track: track.isEnabled = true                │
│   ↓                                                   │
│ Call onRemoteVideoTrack callback                    │
│   ↓                                                   │
│ Main thread: Add track to renderer                  │
│   ↓                                                   │
│ VideoViewWrapper displays video                     │
└──────────────────────────────────────────────────────┘

Android Side:
┌──────────────────────────────────────────────────────┐
│ onTrack(transceiver) called                         │
│   ↓                                                   │
│ Get video track from transceiver.receiver           │
│   ↓                                                   │
│ Enable track: track.setEnabled(true)                │
│   ↓                                                   │
│ Call onRemoteStream callback                        │
│   ↓                                                   │
│ Add track to SurfaceViewRenderer                    │
└──────────────────────────────────────────────────────┘
```

## Key Components

### iOS WebRTCManager

```
┌─────────────────────────────────────────────────┐
│         iOS WebRTCManager                       │
├─────────────────────────────────────────────────┤
│ Properties:                                     │
│  - peerConnection: RTCPeerConnection           │
│  - localVideoTrack: RTCVideoTrack              │
│  - remoteVideoTrack: RTCVideoTrack             │
│  - videoCapturer: RTCCameraVideoCapturer       │
│                                                  │
│ Callbacks:                                      │
│  - onLocalVideoTrack                           │
│  - onRemoteVideoTrack ✨ (triggers renderer)   │
│  - onIceCandidate                              │
│  - onConnectionStateChange                     │
│                                                  │
│ Delegate Methods:                               │
│  - didAdd stream (legacy, Plan B)              │
│  - didStartReceivingOn ✨ (NEW, Unified Plan)  │
│  - didGenerate candidate                       │
│  - didChange iceConnectionState                │
└─────────────────────────────────────────────────┘
```

### Android WebRTCManager

```
┌─────────────────────────────────────────────────┐
│        Android WebRTCManager                    │
├─────────────────────────────────────────────────┤
│ Properties:                                     │
│  - peerConnection: PeerConnection              │
│  - localVideoTrack: VideoTrack                 │
│  - remoteVideoTrack: VideoTrack                │
│  - videoCapturer: CameraVideoCapturer          │
│                                                  │
│ Callbacks:                                      │
│  - onLocalStream                               │
│  - onRemoteStream ✨ (triggers renderer)       │
│  - onIceCandidateCallback                      │
│  - onConnectionStateChange                     │
│                                                  │
│ Observer Methods:                               │
│  - onAddStream (legacy, Plan B)                │
│  - onTrack ✨ (modern, Unified Plan)           │
│  - onIceCandidate                              │
│  - onIceConnectionChange                       │
└─────────────────────────────────────────────────┘
```

## Track Reception - Before vs After

### BEFORE (Broken)

```
iOS Side:
┌──────────────────────────┐
│ Android sends track      │
│         ↓                │
│ iOS peerConnection       │
│  receives track via      │
│  transceiver             │
│         ↓                │
│ ❌ No handler!           │
│  (didStartReceivingOn    │
│   not implemented)       │
│         ↓                │
│ Track lost/ignored       │
│         ↓                │
│ 🔴 NO VIDEO              │
└──────────────────────────┘

UI:
┌──────────────────────────┐
│ Video views created      │
│   but not in view        │
│   hierarchy              │
│         ↓                │
│ @State holds references  │
│   but never rendered     │
│         ↓                │
│ 🔴 BLACK SCREEN          │
└──────────────────────────┘
```

### AFTER (Fixed)

```
iOS Side:
┌──────────────────────────┐
│ Android sends track      │
│         ↓                │
│ iOS peerConnection       │
│  receives track via      │
│  transceiver             │
│         ↓                │
│ ✅ didStartReceivingOn   │
│  (newly implemented)     │
│         ↓                │
│ Extract video track      │
│         ↓                │
│ Enable track             │
│         ↓                │
│ Main thread dispatch     │
│         ↓                │
│ Add to renderer          │
│         ↓                │
│ 🟢 VIDEO RENDERS         │
└──────────────────────────┘

UI:
┌──────────────────────────┐
│ VideoViewWrapper created │
│         ↓                │
│ Embedded in SwiftUI      │
│   view hierarchy         │
│         ↓                │
│ RTCMTLVideoView          │
│   properly displayed     │
│         ↓                │
│ Track added to view      │
│         ↓                │
│ 🟢 VIDEO VISIBLE         │
└──────────────────────────┘
```

## SDP Semantics: Plan B vs Unified Plan

### Plan B (Deprecated)
```
┌────────────────────────────────┐
│     Single Media Section       │
│  Multiple tracks in one m=     │
│                                │
│  m=video                       │
│    - track1                    │
│    - track2                    │
│    - track3                    │
│                                │
│  Callback: didAdd stream       │
└────────────────────────────────┘
```

### Unified Plan (Modern) ✅
```
┌────────────────────────────────┐
│  Multiple Media Sections       │
│  One track per m= section      │
│                                │
│  m=video (transceiver 1)       │
│    - track1                    │
│                                │
│  m=video (transceiver 2)       │
│    - track2                    │
│                                │
│  m=audio (transceiver 3)       │
│    - track3                    │
│                                │
│  Callback: didStartReceivingOn │
└────────────────────────────────┘
```

## Configuration Comparison

### Both Platforms (After Fix)

```
┌────────────────────────────────────────────────────┐
│              WebRTC Configuration                  │
├────────────────────────────────────────────────────┤
│ SDP Semantics:      UNIFIED_PLAN                  │
│ ICE Servers:        stun:stun.l.google.com:19302  │
│ Video Resolution:   1280x720                       │
│ Frame Rate:         30 fps                         │
│ Video Codec:        VP8/H.264 (negotiated)        │
│ Audio Codec:        Opus (negotiated)              │
│ Stream ID:          "stream"                       │
│ Gathering Policy:   GATHER_CONTINUALLY            │
└────────────────────────────────────────────────────┘
```

## Signaling Message Flow

```
Join Room:
{
  "type": "join-room",
  "roomId": "room123",
  "userId": "user-uuid",
  "targetUserId": null
}

User Joined Notification:
{
  "type": "user-joined",
  "roomId": "room123",
  "userId": "other-user-uuid",
  "targetUserId": null
}

Offer:
{
  "type": "offer",
  "roomId": "room123",
  "userId": "user-uuid",
  "targetUserId": "other-user-uuid",
  "data": {
    "type": "offer",
    "sdp": "v=0\r\no=- ... (full SDP)"
  }
}

Answer:
{
  "type": "answer",
  "roomId": "room123",
  "userId": "user-uuid",
  "targetUserId": "other-user-uuid",
  "data": {
    "type": "answer",
    "sdp": "v=0\r\no=- ... (full SDP)"
  }
}

ICE Candidate:
{
  "type": "ice-candidate",
  "roomId": "room123",
  "userId": "user-uuid",
  "targetUserId": "other-user-uuid",
  "data": {
    "candidate": "candidate:... udp ...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

## Video View Hierarchy

### iOS (SwiftUI)

```
ZStack
├── VideoViewWrapper (remote video - full screen)
│   └── RTCMTLVideoView
│       └── Remote video track
│
├── VStack
│   ├── HStack
│   │   ├── Spacer
│   │   └── VideoViewWrapper (local video - PiP)
│   │       └── RTCMTLVideoView
│   │           └── Local video track
│   └── Spacer
│
└── VStack (controls)
    ├── Status text
    ├── Spacer
    └── HStack (buttons)
        ├── Audio toggle
        ├── Video toggle
        ├── Switch camera
        └── End call
```

### Android (Compose)

```
Box (modifier = Modifier.fillMaxSize())
├── SurfaceViewRenderer (remote video - full screen)
│   └── Remote video track
│
├── Box (local video - PiP, top-right)
│   └── SurfaceViewRenderer
│       └── Local video track
│
└── Column (controls)
    ├── Status text
    ├── Spacer
    └── Row (buttons)
        ├── Audio toggle
        ├── Video toggle
        ├── Switch camera
        └── End call
```

## Critical Code Paths

### When Remote Track Arrives (iOS)

```swift
// 1. Delegate method called
func peerConnection(_ peerConnection: RTCPeerConnection, 
                   didStartReceivingOn transceiver: RTCRtpTransceiver) {
    
    // 2. Get track from receiver
    guard let track = transceiver.receiver.track else { return }
    
    // 3. Check if video track
    if let videoTrack = track as? RTCVideoTrack {
        
        // 4. Enable the track
        videoTrack.isEnabled = true
        
        // 5. Store reference
        remoteVideoTrack = videoTrack
        
        // 6. Call callback on main thread
        DispatchQueue.main.async {
            self.onRemoteVideoTrack?(videoTrack)
        }
    }
}

// 7. Callback in CallViewModel
webRTCManager?.onRemoteVideoTrack = { track in
    // 8. Add to renderer
    track.add(remoteRenderer)  // ← remoteRenderer is RTCMTLVideoView
}

// 9. SwiftUI renders
VideoViewWrapper(videoView: remoteView)
```

### When Remote Track Arrives (Android)

```kotlin
// 1. Observer method called
override fun onTrack(transceiver: RtpTransceiver) {
    
    // 2. Get track from receiver
    transceiver.receiver.track()?.let { track ->
        
        // 3. Check if video track
        when (track) {
            is VideoTrack -> {
                
                // 4. Enable the track
                track.setEnabled(true)
                
                // 5. Store reference
                remoteVideoTrack = track
                
                // 6. Call callback
                onRemoteStream(track, remoteAudioTrack)
            }
        }
    }
}

// 7. Callback in CallViewModel
onRemoteStream = { videoTrack: VideoTrack?, audioTrack: AudioTrack? ->
    videoTrack?.let { 
        // 8. Add to renderer
        webRTCManager?.setRemoteVideoTrack(it, remoteSurfaceView)
    }
}

// 9. Compose renders
AndroidView(
    factory = { remoteSurfaceView },  // ← SurfaceViewRenderer
    modifier = Modifier.fillMaxSize()
)
```

## Performance Considerations

### Video Capture Pipeline

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Camera     │───►│   Capturer   │───►│ Video Source │
│   Hardware   │    │  (platform-  │    │              │
│              │    │   specific)  │    │              │
└──────────────┘    └──────────────┘    └──────────────┘
                                              │
                                              ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Network    │◄───│   Encoder    │◄───│ Video Track  │
│              │    │  (VP8/H.264) │    │              │
└──────────────┘    └──────────────┘    └──────────────┘
```

### Video Rendering Pipeline

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Network    │───►│   Decoder    │───►│ Video Track  │
│              │    │  (VP8/H.264) │    │   (remote)   │
└──────────────┘    └──────────────┘    └──────────────┘
                                              │
                                              ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Display    │◄───│   Renderer   │◄───│  Add Track   │
│   Hardware   │    │  (Metal/GL)  │    │  to Renderer │
└──────────────┘    └──────────────┘    └──────────────┘
```

## Thread Safety

### iOS
- WebRTC callbacks: Background threads
- UI updates: Main thread required
- Solution: DispatchQueue.main.async { }

### Android
- WebRTC callbacks: Background threads
- UI updates: Main thread required
- Solution: viewModelScope.launch { } (uses Main dispatcher)

## Summary of Changes

✅ **iOS WebRTCManager**: Added `didStartReceivingOn transceiver` delegate method
✅ **iOS CallView**: Created `VideoViewWrapper` and embedded views in SwiftUI
✅ **iOS CallViewModel**: Added main thread dispatch for video callbacks
✅ **Android WebRTCManager**: Added stream IDs and improved logging
✅ **Both**: Track enabling, proper callbacks, enhanced debugging

