import SwiftUI

struct HomeView: View {
    @ObservedObject var viewModel: CallViewModel
    @State private var serverUrl = "ws://192.168.1.14:8080/ws"
    @State private var roomId = ""
    @State private var showingAlert = false
    @State private var alertMessage = ""
    
    var body: some View {
        NavigationView {
            ZStack {
                Color(UIColor.systemBackground)
                    .ignoresSafeArea()
                
                VStack(spacing: 30) {
                    Spacer()
                    
                    // App Icon/Logo
                    Image(systemName: "video.fill")
                        .font(.system(size: 80))
                        .foregroundColor(.blue)
                    
                    // App Title
                    VStack(spacing: 8) {
                        Text("WebRTC Video Call")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        
                        Text("Enter server URL and room ID to join a call")
                            .font(.body)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    
                    Spacer()
                    
                    // Input Fields
                    VStack(spacing: 16) {
                        // Server URL Input
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Server URL")
                                .font(.headline)
                            
                            TextField("http://your-server:8080/ws", text: $serverUrl)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .autocapitalization(.none)
                                .keyboardType(.URL)
                        }
                        
                        // Room ID Input
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Room ID")
                                .font(.headline)
                            
                            TextField("Enter room ID", text: $roomId)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .autocapitalization(.none)
                        }
                        
                        // Join Call Button
                        Button(action: {
                            joinCall()
                        }) {
                            HStack {
                                if viewModel.callState.isConnecting {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                } else {
                                    Image(systemName: "video.fill")
                                }
                                
                                Text(viewModel.callState.isConnecting ? "Connecting..." : "Join Call")
                                    .fontWeight(.semibold)
                            }
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(isButtonEnabled ? Color.blue : Color.gray)
                            .foregroundColor(.white)
                            .cornerRadius(12)
                        }
                        .disabled(!isButtonEnabled || viewModel.callState.isConnecting)
                    }
                    .padding(.horizontal, 24)
                    
                    // Info Text
                    Text("Make sure your camera and microphone permissions are enabled")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                    
                    Spacer()
                }
            }
            .navigationBarHidden(true)
            .alert(isPresented: $showingAlert) {
                Alert(
                    title: Text("Connection Error"),
                    message: Text(alertMessage),
                    dismissButton: .default(Text("OK"))
                )
            }
            .onChange(of: viewModel.callState.errorMessage) { errorMessage in
                if let error = errorMessage {
                    alertMessage = error
                    showingAlert = true
                    viewModel.clearError()
                }
            }
            .onChange(of: viewModel.callState.isConnecting) { isConnecting in
                if isConnecting {
                    // Optional: Show a connecting toast or HUD
                }
            }
        }
    }
    
    private var isButtonEnabled: Bool {
        !serverUrl.isEmpty && !roomId.isEmpty
    }
    
    private func joinCall() {
        guard !serverUrl.isEmpty && !roomId.isEmpty else { return }
        viewModel.connectToSignalingServer(serverUrl: serverUrl, roomId: roomId)
    }
}

struct HomeView_Previews: PreviewProvider {
    static var previews: some View {
        HomeView(viewModel: CallViewModel())
    }
}

