import Foundation
import WebRTC
import AVFoundation

class WebRTCManager: NSObject {
    private let peerConnectionFactory: RTCPeerConnectionFactory
    private var peerConnection: RTCPeerConnection?
    private var localVideoTrack: RTCVideoTrack?
    private var localAudioTrack: RTCAudioTrack?
    private var remoteVideoTrack: RTCVideoTrack?
    private var remoteAudioTrack: RTCAudioTrack?
    private var videoCapturer: RTCCameraVideoCapturer?
    private var currentCameraPosition: AVCaptureDevice.Position = .front
    
    var onLocalVideoTrack: ((RTCVideoTrack) -> Void)?
    var onRemoteVideoTrack: ((RTCVideoTrack) -> Void)?
    var onIceCandidate: ((RTCIceCandidate) -> Void)?
    var onConnectionStateChange: ((RTCIceConnectionState) -> Void)?
    var onLocalDescription: ((RTCSessionDescription) -> Void)?
    
    override init() {
        let encoderFactory = RTCDefaultVideoEncoderFactory()
        let decoderFactory = RTCDefaultVideoDecoderFactory()
        
        peerConnectionFactory = RTCPeerConnectionFactory(
            encoderFactory: encoderFactory,
            decoderFactory: decoderFactory
        )
        
        super.init()
    }
    
    func initializePeerConnection() {
        let configuration = RTCConfiguration()
        configuration.iceServers = [RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302"])]
        configuration.sdpSemantics = .unifiedPlan
        configuration.continualGatheringPolicy = .gatherContinually
        
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: nil
        )
        
        peerConnection = peerConnectionFactory.peerConnection(
            with: configuration,
            constraints: constraints,
            delegate: self
        )
    }
    
    func startLocalVideo(renderer: RTCVideoRenderer) {
        let videoSource = peerConnectionFactory.videoSource()
        
        #if TARGET_OS_SIMULATOR
        // Simulator doesn't support camera
        print("Camera not available on simulator")
        #else
        videoCapturer = RTCCameraVideoCapturer(delegate: videoSource)
        
        guard let capturer = videoCapturer,
              let frontCamera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) else {
            print("Camera not available")
            return
        }
        
        currentCameraPosition = .front
        let format = selectFormat(for: frontCamera)
        let fps = selectFps(for: format)
        
        capturer.startCapture(with: frontCamera, format: format, fps: Int(fps))
        #endif
        
        localVideoTrack = peerConnectionFactory.videoTrack(with: videoSource, trackId: "local_video")
        localVideoTrack?.add(renderer)
        
        let audioSource = peerConnectionFactory.audioSource(with: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil))
        localAudioTrack = peerConnectionFactory.audioTrack(with: audioSource, trackId: "local_audio")
        
        if let videoTrack = localVideoTrack {
            peerConnection?.add(videoTrack, streamIds: ["stream"])
            onLocalVideoTrack?(videoTrack)
        }
        
        if let audioTrack = localAudioTrack {
            peerConnection?.add(audioTrack, streamIds: ["stream"])
        }
    }
    
    private func selectFormat(for device: AVCaptureDevice) -> AVCaptureDevice.Format {
        let formats = RTCCameraVideoCapturer.supportedFormats(for: device)
        let targetWidth = 1280
        let targetHeight = 720
        
        var selectedFormat: AVCaptureDevice.Format?
        var currentDiff = Int.max
        
        for format in formats {
            let dimension = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
            let diff = abs(Int(dimension.width) - targetWidth) + abs(Int(dimension.height) - targetHeight)
            
            if diff < currentDiff {
                selectedFormat = format
                currentDiff = diff
            }
        }
        
        return selectedFormat ?? formats.first!
    }
    
    private func selectFps(for format: AVCaptureDevice.Format) -> Double {
        let fpsRanges = format.videoSupportedFrameRateRanges
        var maxFps: Double = 0
        
        for range in fpsRanges {
            maxFps = max(maxFps, range.maxFrameRate)
        }
        
        return min(maxFps, 30.0)
    }
    
    func createOffer() {
        print("🔄 createOffer() called")
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: [
                "OfferToReceiveAudio": "true",
                "OfferToReceiveVideo": "true"
            ],
            optionalConstraints: nil
        )
        
        peerConnection?.offer(for: constraints) { [weak self] sdp, error in
            guard let self = self, let sdp = sdp else {
                print("❌ Error creating offer: \(error?.localizedDescription ?? "Unknown")")
                return
            }
            
            print("✅ Offer created successfully")
            self.peerConnection?.setLocalDescription(sdp) { error in
                if let error = error {
                    print("❌ Error setting local description: \(error.localizedDescription)")
                } else {
                    print("✅ Offer set as local description successfully")
                    self.onLocalDescription?(sdp)
                }
            }
        }
    }
    
    func createAnswer() {
        print("🔄 createAnswer() called")
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: [
                "OfferToReceiveAudio": "true",
                "OfferToReceiveVideo": "true"
            ],
            optionalConstraints: nil
        )
        
        peerConnection?.answer(for: constraints) { [weak self] sdp, error in
            guard let self = self, let sdp = sdp else {
                print("❌ Error creating answer: \(error?.localizedDescription ?? "Unknown")")
                return
            }
            
            print("✅ Answer created successfully")
            self.peerConnection?.setLocalDescription(sdp) { error in
                if let error = error {
                    print("❌ Error setting local description: \(error.localizedDescription)")
                } else {
                    print("✅ Answer set as local description successfully")
                    self.onLocalDescription?(sdp)
                }
            }
        }
    }
    
    func setRemoteDescription(_ sdp: RTCSessionDescription) {
        peerConnection?.setRemoteDescription(sdp) { error in
            if let error = error {
                print("Error setting remote description: \(error.localizedDescription)")
            }
        }
    }
    
    func addIceCandidate(_ candidate: RTCIceCandidate) {
        peerConnection?.add(candidate)
    }
    
    func switchCamera() {
        guard let capturer = videoCapturer else { return }
        
        let newPosition: AVCaptureDevice.Position = currentCameraPosition == .front ? .back : .front
        
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: newPosition) else {
            print("Camera not available for position: \(newPosition == .front ? "front" : "back")")
            return
        }
        
        currentCameraPosition = newPosition
        let format = selectFormat(for: camera)
        let fps = selectFps(for: format)
        
        capturer.startCapture(with: camera, format: format, fps: Int(fps))
    }
    
    func toggleAudio(_ enabled: Bool) {
        localAudioTrack?.isEnabled = enabled
    }
    
    func toggleVideo(_ enabled: Bool) {
        localVideoTrack?.isEnabled = enabled
    }
    
    func disconnect() {
        videoCapturer?.stopCapture()
        localVideoTrack = nil
        localAudioTrack = nil
        remoteVideoTrack = nil
        remoteAudioTrack = nil
        peerConnection?.close()
        peerConnection = nil
    }
}

// MARK: - RTCPeerConnectionDelegate
extension WebRTCManager: RTCPeerConnectionDelegate {
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {
        print("Signaling state changed: \(stateChanged)")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        print("📺 Stream added (legacy callback) - stream id: \(stream.streamId)")
        
        let localVideoId = localVideoTrack?.trackId
        let localAudioId = localAudioTrack?.trackId
        
        // Check track IDs to avoid processing our own local tracks
        if let videoTrack = stream.videoTracks.first,
           videoTrack.trackId == localVideoId {
            print("⏭️ Skipping our own local stream (track ID match)")
            return
        }
        
        if let videoTrack = stream.videoTracks.first {
            print("✅ Video track found in stream (ID: \(videoTrack.trackId))")
            videoTrack.isEnabled = true
            remoteVideoTrack = videoTrack
            onRemoteVideoTrack?(videoTrack)
        }
        if let audioTrack = stream.audioTracks.first {
            print("✅ Audio track found in stream (ID: \(audioTrack.trackId))")
            audioTrack.isEnabled = true
            remoteAudioTrack = audioTrack
        }
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {
        print("Stream removed")
    }
    
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {
        print("Should negotiate")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        print("ICE connection state changed: \(newState)")
        onConnectionStateChange?(newState)
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {
        print("ICE gathering state changed: \(newState)")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        print("ICE candidate generated")
        onIceCandidate?(candidate)
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {
        print("ICE candidates removed")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {
        print("Data channel opened")
    }
    
    func peerConnection(_ peerConnection: RTCPeerConnection, didStartReceivingOn transceiver: RTCRtpTransceiver) {
        print("📡 didStartReceivingOn transceiver - media type: \(transceiver.mediaType), direction: \(transceiver.direction)")
        
        guard let track = transceiver.receiver.track else {
            print("❌ No track found in transceiver")
            return
        }
        
        // Check track ID to avoid processing our own local tracks
        let trackId = track.trackId
        let localVideoId = localVideoTrack?.trackId
        let localAudioId = localAudioTrack?.trackId
        
        print("Track ID: \(trackId), Local video ID: \(localVideoId ?? "nil"), Local audio ID: \(localAudioId ?? "nil")")
        
        if trackId == localVideoId || trackId == localAudioId {
            print("⏭️ Skipping track with ID \(trackId) (matches our local track)")
            return
        }
        
        if let videoTrack = track as? RTCVideoTrack {
            print("✅ Remote video track received via transceiver (ID: \(trackId))")
            videoTrack.isEnabled = true
            remoteVideoTrack = videoTrack
            DispatchQueue.main.async { [weak self] in
                self?.onRemoteVideoTrack?(videoTrack)
            }
        } else if let audioTrack = track as? RTCAudioTrack {
            print("✅ Remote audio track received via transceiver (ID: \(trackId))")
            audioTrack.isEnabled = true
            remoteAudioTrack = audioTrack
        }
    }
}

