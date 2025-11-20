import Foundation

struct SignalMessage: Codable {
    let type: String // offer, answer, ice-candidate, join-room, leave-room
    let roomId: String?
    let userId: String?
    let targetUserId: String?
    let data: SignalData?
}

struct SignalData: Codable {
    let type: String?
    let sdp: String?
    let candidate: String?
    let sdpMid: String?
    let sdpMLineIndex: Int?
}

struct CallState {
    var isConnected: Bool = false
    var isCallActive: Bool = false
    var connectionState: String = "Disconnected"
    var isAudioEnabled: Bool = true
    var isVideoEnabled: Bool = true
    var roomId: String = ""
    var userId: String = ""
    var targetUserId: String? = nil
    var errorMessage: String? = nil
    var isConnecting: Bool = false
}

