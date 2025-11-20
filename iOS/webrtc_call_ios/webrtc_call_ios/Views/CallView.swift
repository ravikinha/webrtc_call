import SwiftUI
import WebRTC

struct CallView: UIViewRepresentable {
    let track: RTCVideoTrack?
    
    func makeUIView(context: Context) -> RTCMTLVideoView {
        let view = RTCMTLVideoView(frame: .zero)
        view.videoContentMode = .scaleAspectFill
        track?.add(view)
        return view
    }
    
    func updateUIView(_ uiView: RTCMTLVideoView, context: Context) {
        // View updates handled by WebRTC
    }
}

struct VideoViewWrapper: UIViewRepresentable {
    let videoView: RTCMTLVideoView
    
    func makeUIView(context: Context) -> RTCMTLVideoView {
        return videoView
    }
    
    func updateUIView(_ uiView: RTCMTLVideoView, context: Context) {
        // View updates handled by WebRTC
    }
}

struct CallScreen: View {
    @ObservedObject var viewModel: CallViewModel
    @State private var localVideoView: RTCMTLVideoView?
    @State private var remoteVideoView: RTCMTLVideoView?
    
    var body: some View {
        ZStack {
            // Remote video (full screen)
            if let remoteView = remoteVideoView {
                VideoViewWrapper(videoView: remoteView)
                    .ignoresSafeArea()
            } else {
            Color.black.ignoresSafeArea()
            }
            
            // Local video (picture-in-picture)
            if let localView = localVideoView {
                VStack {
                    HStack {
                        Spacer()
                        VideoViewWrapper(videoView: localView)
                    .frame(width: 120, height: 160)
                    .cornerRadius(12)
                            .clipped()
                    .padding()
                    }
                    Spacer()
                }
            }
            
            VStack {
                // Connection status
                if !viewModel.callState.isCallActive {
                    Text("Status: \(viewModel.callState.connectionState)")
                        .foregroundColor(.white)
                        .padding()
                        .background(Color.black.opacity(0.6))
                        .cornerRadius(8)
                        .padding(.top)
                }
                
                Spacer()
                
                // Control buttons
                if viewModel.callState.isConnected {
                    HStack(spacing: 20) {
                        // Audio toggle
                        Button(action: { viewModel.toggleAudio() }) {
                            Image(systemName: viewModel.callState.isAudioEnabled ? "mic.fill" : "mic.slash.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .frame(width: 56, height: 56)
                                .background(viewModel.callState.isAudioEnabled ? Color.blue : Color.red)
                                .clipShape(Circle())
                        }
                        
                        // Video toggle
                        Button(action: { viewModel.toggleVideo() }) {
                            Image(systemName: viewModel.callState.isVideoEnabled ? "video.fill" : "video.slash.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .frame(width: 56, height: 56)
                                .background(viewModel.callState.isVideoEnabled ? Color.blue : Color.red)
                                .clipShape(Circle())
                        }
                        
                        // Switch camera
                        Button(action: { viewModel.switchCamera() }) {
                            Image(systemName: "camera.rotate.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .frame(width: 56, height: 56)
                                .background(Color.gray)
                                .clipShape(Circle())
                        }
                        
                        // End call
                        Button(action: { viewModel.endCall() }) {
                            Image(systemName: "phone.down.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .frame(width: 56, height: 56)
                                .background(Color.red)
                                .clipShape(Circle())
                        }
                    }
                    .padding(.bottom, 40)
                }
            }
        }
        .onAppear {
            setupVideoViews()
        }
        .onDisappear {
            viewModel.endCall()
        }
    }
    
    private func setupVideoViews() {
        let localView = RTCMTLVideoView(frame: .zero)
        localView.videoContentMode = .scaleAspectFill
        localVideoView = localView
        
        let remoteView = RTCMTLVideoView(frame: .zero)
        remoteView.videoContentMode = .scaleAspectFill
        remoteVideoView = remoteView
        
        viewModel.initializeWebRTC(
            localRenderer: localView,
            remoteRenderer: remoteView
        )
    }
}

