import SwiftUI

struct ContentView: View {
    @StateObject private var vm = SseViewModel()

    var body: some View {
        VStack(spacing: 12) {
            Text("SSE demo")
                .font(.headline)

            HStack(spacing: 12) {
                Button("Start SSE") { vm.startSse() }
                    .disabled(vm.running)

                Button("Stop") { vm.stop() }
                    .disabled(!vm.running)

                Button("Fetch HTTP /text") {
                    Task { await vm.fetchHttpText() }
                }
            }

            if let err = vm.lastError {
                Text("Error: \(err)")
                    .font(.footnote)
            }

            if let t = vm.httpText {
                Text("HTTP /text:")
                    .font(.headline)
                ScrollView {
                    Text(t)
                        .font(.footnote)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(height: 120)
            }

            Text("SSE /sse:")
                .font(.headline)

            List(vm.lines, id: \.self) { line in
                Text(line).font(.footnote)
            }
        }
        .padding()
        .onDisappear { vm.stop() }
    }
}
