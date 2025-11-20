//
//  ContentView.swift
//  webrtc_call_ios
//
//  Created by Ravi on 20/11/25.
//

import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = CallViewModel()
    
    var body: some View {
        Group {
            if viewModel.callState.isConnected {
                CallScreen(viewModel: viewModel)
            } else {
                HomeView(viewModel: viewModel)
        }
        }
    }
}

#Preview {
    ContentView()
}
