# WebRTC Call Application

A complete end-to-end WebRTC video calling application with Android (Jetpack Compose), iOS (SwiftUI), and Java Spring Boot backend.

## Architecture

- **Backend**: Java Spring Boot with WebSocket (STOMP) for signaling
- **Android**: Jetpack Compose with MVVM architecture
- **iOS**: SwiftUI with MVVM architecture

## Prerequisites

### Backend
- Java 17 or higher
- Maven 3.6 or higher

### Android
- Android Studio
- Android SDK 27+
- Kotlin

### iOS
- Xcode 15+
- iOS 15+
- Swift 5.9+

## Setup Instructions

### 1. Backend Setup

```bash
cd java_backend
mvn clean install
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`

**WebSocket Endpoint**: `ws://localhost:8080/ws`

### 2. Android Setup

1. Open the `Android` folder in Android Studio
2. Sync Gradle files
3. Update the server URL in `CallScreen.kt` if needed (default: `http://10.0.2.2:8080/ws` for emulator)
4. Run on device or emulator

**Note**: For physical devices, use your computer's IP address instead of `10.0.2.2`

### 3. iOS Setup

1. Open `iOS/webrtc_call_ios/webrtc_call_ios.xcodeproj` in Xcode
2. Add WebRTC framework via Swift Package Manager:
   - File → Add Package Dependencies
   - Add: `https://github.com/webrtc-sdk/Specs.git`
   - Select version 114.0.0 or later
3. Add Starscream for WebSocket:
   - Add: `https://github.com/daltoniam/Starscream.git`
   - Select version 4.0.0 or later
4. Update the server URL in `CallView.swift` if needed (default: `ws://localhost:8080/ws`)
5. Run on device or simulator

**Note**: For physical devices, use your computer's IP address instead of `localhost`

## Usage

1. Start the backend server
2. Launch the Android or iOS app
3. Enter a room ID (e.g., "room1")
4. Click "Join"
5. Launch another instance (on a different device or emulator)
6. Enter the same room ID and join
7. The two clients should connect and start video calling

## Features

- ✅ Real-time video and audio calling
- ✅ Camera switching (front/back)
- ✅ Mute/unmute audio
- ✅ Enable/disable video
- ✅ Room-based calling
- ✅ WebRTC peer-to-peer connection
- ✅ STUN server support

## Project Structure

```
webrtc_call/
├── java_backend/
│   ├── src/main/java/com/example/webrtccall/
│   │   ├── config/          # WebSocket configuration
│   │   ├── controller/       # WebSocket controller
│   │   ├── dto/              # Data transfer objects
│   │   └── service/          # Room management service
│   └── pom.xml
├── Android/
│   └── app/src/main/java/com/example/webrtc_call_android/
│       ├── data/             # Data models and signaling
│       ├── webrtc/           # WebRTC manager
│       ├── viewmodel/        # ViewModels
│       └── ui/               # Compose UI
└── iOS/
    └── webrtc_call_ios/webrtc_call_ios/
        ├── Models/           # Data models
        ├── Services/         # WebRTC and signaling
        ├── ViewModels/       # ViewModels
        └── Views/            # SwiftUI views
```

## Signaling Flow

1. Client connects to WebSocket server
2. Client sends "join-room" message with room ID
3. Server notifies other users in the room
4. First user creates offer
5. Second user receives offer and creates answer
6. Both users exchange ICE candidates
7. Peer-to-peer connection established

## Troubleshooting

### Android
- Ensure camera and microphone permissions are granted
- Check that the server URL is correct for your network
- For emulator: use `10.0.2.2:8080`
- For physical device: use your computer's IP address

### iOS
- Ensure camera and microphone permissions are granted in Info.plist
- Check that WebRTC framework is properly linked
- For simulator: camera won't work, use a physical device
- For physical device: use your computer's IP address

### Backend
- Ensure port 8080 is not in use
- Check firewall settings
- Verify CORS configuration if needed

## License

This project is part of a WebRTC implementation example.
