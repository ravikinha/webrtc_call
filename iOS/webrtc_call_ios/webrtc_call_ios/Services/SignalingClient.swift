import Foundation
import Combine
import Starscream

class SignalingClient: WebSocketDelegate {
    private var socket: WebSocket?
    private let serverUrl: String
    private let userId: String
    private var isConnected: Bool = false
    private var isStompConnected: Bool = false
    private var subscriptionId: Int = 0
    var onMessageReceived: ((SignalMessage) -> Void)?
    var onConnected: (() -> Void)?
    var onDisconnected: (() -> Void)?
    
    init(serverUrl: String, userId: String) {
        self.serverUrl = serverUrl
        self.userId = userId
    }
    
    func connect() {
        guard let url = URL(string: serverUrl) else {
            print("Invalid WebSocket URL")
            return
        }
        
        var request = URLRequest(url: url)
        request.setValue(userId, forHTTPHeaderField: "userId")
        
        socket = WebSocket(request: request)
        socket?.delegate = self
        socket?.connect()
    }
    
    private func sendStompConnect() {
        let connectFrame = "CONNECT\n" +
            "accept-version:1.1,1.0\n" +
            "heart-beat:10000,10000\n" +
            "userId:\(userId)\n" +
            "\n" +
            "\u{0000}"
        socket?.write(string: connectFrame)
    }
    
    private func subscribeToMessages() {
        subscriptionId += 1
        let subscribeFrame = "SUBSCRIBE\n" +
            "id:sub-\(subscriptionId)\n" +
            "destination:/user/queue/signal\n" +
            "\n" +
            "\u{0000}"
        socket?.write(string: subscribeFrame)
    }
    
    func sendSignal(_ message: SignalMessage) {
        guard let socket = socket, isStompConnected else {
            print("❌ WebSocket not connected")
            return
        }
        
        do {
            let encoder = JSONEncoder()
            let data = try encoder.encode(message)
            let json = String(data: data, encoding: .utf8) ?? ""
            
            print("📤 Sending signal - Type: \(message.type), UserId: \(message.userId ?? "nil"), TargetUserId: \(message.targetUserId ?? "nil"), RoomId: \(message.roomId ?? "nil")")
            let sendFrame = "SEND\n" +
                "destination:/app/signal\n" +
                "content-type:application/json\n" +
                "\n" +
                "\(json)\u{0000}"
            socket.write(string: sendFrame)
            print("✅ Signal sent successfully")
        } catch {
            print("❌ Error encoding message: \(error)")
        }
    }
    
    func disconnect() {
        if isStompConnected {
            let disconnectFrame = "DISCONNECT\n" +
                "\n" +
                "\u{0000}"
            socket?.write(string: disconnectFrame)
        }
        socket?.disconnect()
        socket = nil
        isConnected = false
        isStompConnected = false
    }
    
    // MARK: - WebSocketDelegate
    func didReceive(event: WebSocketEvent, client: WebSocketClient) {
        switch event {
        case .connected(let headers):
            print("WebSocket connected: \(headers)")
            isConnected = true
            sendStompConnect()
        case .disconnected(let reason, let code):
            print("WebSocket disconnected: \(reason) with code: \(code)")
            isConnected = false
            isStompConnected = false
            DispatchQueue.main.async {
                self.onDisconnected?()
            }
        case .text(let string):
            handleStompMessage(string)
        case .error(let error):
            print("WebSocket error: \(error?.localizedDescription ?? "Unknown error")")
            isConnected = false
            isStompConnected = false
            DispatchQueue.main.async {
                self.onDisconnected?()
            }
        default:
            break
        }
    }
    
    private func handleStompMessage(_ message: String) {
        if message.hasPrefix("CONNECTED") {
            print("STOMP connected")
            isStompConnected = true
            subscribeToMessages()
            DispatchQueue.main.async {
                self.onConnected?()
            }
            return
        }
        
        if !message.hasPrefix("MESSAGE") {
            return
        }
        
        // Parse STOMP MESSAGE frame
        let lines = message.components(separatedBy: "\n")
        var bodyStart = -1
        
        for (index, line) in lines.enumerated() {
            if line.isEmpty && index < lines.count - 1 {
                bodyStart = index + 1
                break
            }
        }
        
        if bodyStart > 0 && bodyStart < lines.count {
            let body = lines[bodyStart...].joined(separator: "\n")
                .trimmingCharacters(in: CharacterSet(charactersIn: "\u{0000}"))
            
            guard let data = body.data(using: .utf8) else { return }
            
            do {
                let decoder = JSONDecoder()
                let signalMessage = try decoder.decode(SignalMessage.self, from: data)
                print("📥 Received signal - Type: \(signalMessage.type), UserId: \(signalMessage.userId ?? "nil"), TargetUserId: \(signalMessage.targetUserId ?? "nil")")
                onMessageReceived?(signalMessage)
            } catch {
                print("❌ Error decoding message: \(error)")
            }
        }
    }
}
