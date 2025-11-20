import Foundation
import Combine
import WebRTC

class CallViewModel: ObservableObject {
    @Published var callState = CallState()
    
    private var webRTCManager: WebRTCManager?
    private var signalingClient: SignalingClient?
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        callState.userId = UUID().uuidString
    }
    
    func initializeWebRTC(localRenderer: RTCVideoRenderer, remoteRenderer: RTCVideoRenderer) {
        webRTCManager = WebRTCManager()
        webRTCManager?.initializePeerConnection()
        webRTCManager?.startLocalVideo(renderer: localRenderer)
        
        webRTCManager?.onLocalDescription = { [weak self] sdp in
            guard let self = self else { return }
            self.sendLocalDescription(sdp)
        }
        
        webRTCManager?.onRemoteVideoTrack = { [weak self] track in
            print("🎥 onRemoteVideoTrack callback triggered")
            DispatchQueue.main.async {
            track.add(remoteRenderer)
                print("✅ Remote video track added to renderer")
                // Force a state update to trigger UI refresh
                self?.callState.connectionState = self?.callState.connectionState ?? "Unknown"
            }
        }
        
        webRTCManager?.onIceCandidate = { [weak self] candidate in
            self?.sendIceCandidate(candidate)
        }
        
        webRTCManager?.onConnectionStateChange = { [weak self] state in
            DispatchQueue.main.async {
                self?.callState.connectionState = self?.stringFromIceConnectionState(state) ?? "Unknown"
                self?.callState.isCallActive = state == .connected
            }
        }
    }
    
    func connectToSignalingServer(serverUrl: String, roomId: String) {
        callState.roomId = roomId
        callState.isConnecting = true
        callState.errorMessage = nil
        callState.connectionState = "Connecting..."
        
        signalingClient = SignalingClient(
            serverUrl: serverUrl,
            userId: callState.userId
        )
        
        signalingClient?.onMessageReceived = { [weak self] message in
            self?.handleSignalingMessage(message)
        }
        
        signalingClient?.onConnected = { [weak self] in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.callState.isConnected = true
                self.callState.isConnecting = false
                self.callState.errorMessage = nil
                self.callState.connectionState = "Connected"
                // Join room after STOMP connection is established
                self.joinRoom(roomId)
            }
        }
        
        signalingClient?.onDisconnected = { [weak self] in
            guard let self = self else { return }
            DispatchQueue.main.async {
                self.callState.isConnected = false
                self.callState.isConnecting = false
                self.callState.errorMessage = "Disconnected from server"
                self.callState.connectionState = "Disconnected"
            }
        }
        
        do {
            signalingClient?.connect()
        } catch {
            DispatchQueue.main.async {
                self.callState.isConnecting = false
                self.callState.errorMessage = "Failed to connect: \(error.localizedDescription)"
                self.callState.connectionState = "Connection Failed"
            }
        }
    }
    
    func clearError() {
        callState.errorMessage = nil
    }
    
    private func joinRoom(_ roomId: String) {
        let message = SignalMessage(
            type: "join-room",
            roomId: roomId,
            userId: callState.userId,
            targetUserId: nil,
            data: nil
        )
        signalingClient?.sendSignal(message)
    }
    
    private func handleSignalingMessage(_ message: SignalMessage) {
        print("📨 handleSignalingMessage - type: \(message.type), userId: \(message.userId ?? "nil"), targetUserId: \(message.targetUserId ?? "nil")")
        switch message.type {
        case "user-joined":
            if let targetUserId = message.userId, targetUserId != callState.userId {
                print("👤 User joined - targetUserId: \(targetUserId), myUserId: \(callState.userId)")
                callState.targetUserId = targetUserId
                print("📞 Updated state with targetUserId: \(targetUserId), creating offer...")
                webRTCManager?.createOffer()
            }
            
        case "offer":
            if let data = message.data, let sdpString = data.sdp {
                print("📥 Received offer from userId: \(message.userId ?? "nil")")
                // Set targetUserId if not already set
                if callState.targetUserId == nil {
                    callState.targetUserId = message.userId
                    print("🎯 Set targetUserId from offer: \(message.userId ?? "nil")")
                }
                let sdp = RTCSessionDescription(type: .offer, sdp: sdpString)
                webRTCManager?.setRemoteDescription(sdp)
                webRTCManager?.createAnswer()
                print("✅ Set remote description and creating answer")
            }
            
        case "answer":
            if let data = message.data, let sdpString = data.sdp {
                print("📥 Received answer from userId: \(message.userId ?? "nil")")
                let sdp = RTCSessionDescription(type: .answer, sdp: sdpString)
                webRTCManager?.setRemoteDescription(sdp)
                print("✅ Set remote answer description")
            }
            
        case "ice-candidate":
            if let data = message.data,
               let candidate = data.candidate,
               let sdpMid = data.sdpMid,
               let sdpMLineIndex = data.sdpMLineIndex {
                let iceCandidate = RTCIceCandidate(
                    sdp: candidate,
                    sdpMLineIndex: Int32(sdpMLineIndex),
                    sdpMid: sdpMid
                )
                webRTCManager?.addIceCandidate(iceCandidate)
            }
            
        default:
            break
        }
    }
    
    private func sendLocalDescription(_ sdp: RTCSessionDescription) {
        print("📤 sendLocalDescription called - type: \(sdp.type), targetUserId: \(callState.targetUserId ?? "nil")")
        guard let targetUserId = callState.targetUserId else {
            print("❌ Cannot send \(sdp.type) - targetUserId is nil")
            return
        }
        
        let sdpTypeString: String
        switch sdp.type {
        case .offer:
            sdpTypeString = "offer"
        case .answer:
            sdpTypeString = "answer"
        case .prAnswer:
            sdpTypeString = "pranswer"
        @unknown default:
            sdpTypeString = "unknown"
        }
        
        let signalData = SignalData(
            type: sdpTypeString,
            sdp: sdp.sdp,
            candidate: nil,
            sdpMid: nil,
            sdpMLineIndex: nil
        )
        
        let messageType = sdp.type == .offer ? "offer" : "answer"
        let message = SignalMessage(
            type: messageType,
            roomId: callState.roomId,
            userId: callState.userId,
            targetUserId: targetUserId,
            data: signalData
        )
        
        print("📡 Sending \(messageType) to targetUserId: \(targetUserId) in room: \(callState.roomId)")
        signalingClient?.sendSignal(message)
    }
    
    private func sendIceCandidate(_ candidate: RTCIceCandidate) {
        guard let targetUserId = callState.targetUserId else { return }
        
        let signalData = SignalData(
            type: nil,
            sdp: nil,
            candidate: candidate.sdp,
            sdpMid: candidate.sdpMid,
            sdpMLineIndex: Int(candidate.sdpMLineIndex)
        )
        
        let message = SignalMessage(
            type: "ice-candidate",
            roomId: callState.roomId,
            userId: callState.userId,
            targetUserId: targetUserId,
            data: signalData
        )
        
        signalingClient?.sendSignal(message)
    }
    
    func toggleAudio() {
        callState.isAudioEnabled.toggle()
        webRTCManager?.toggleAudio(callState.isAudioEnabled)
    }
    
    func toggleVideo() {
        callState.isVideoEnabled.toggle()
        webRTCManager?.toggleVideo(callState.isVideoEnabled)
    }
    
    func switchCamera() {
        webRTCManager?.switchCamera()
    }
    
    func endCall() {
        signalingClient?.disconnect()
        webRTCManager?.disconnect()
        callState = CallState(userId: callState.userId)
    }
    
    private func stringFromIceConnectionState(_ state: RTCIceConnectionState) -> String {
        switch state {
        case .new: return "New"
        case .checking: return "Checking"
        case .connected: return "Connected"
        case .completed: return "Completed"
        case .failed: return "Failed"
        case .disconnected: return "Disconnected"
        case .closed: return "Closed"
        case .count: return "Count"
        @unknown default: return "Unknown"
        }
    }
}

