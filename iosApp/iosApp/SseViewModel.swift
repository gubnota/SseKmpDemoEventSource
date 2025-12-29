import Foundation
import Shared

@MainActor
final class SseViewModel: ObservableObject {
    @Published var running: Bool = false
    @Published var lines: [String] = []
    @Published var lastError: String?
    @Published var httpText: String?

    private let client = SseClient()
    private let base = "http://192.168.50.223:8787"
    private lazy var listener = Listener(owner: self)

    func startSse() {
        lastError = nil
        httpText = nil
        lines.removeAll()
        running = true
        client.start(url: "\(base)/sse", listener: listener)
    }

    func stop() {
        client.stop()
        running = false
    }

    func fetchHttpText() async {
        lastError = nil
        httpText = nil
        do {
            let (data, _) = try await URLSession.shared.data(from: URL(string: "\(base)/text")!)
            httpText = String(data: data, encoding: .utf8)
        } catch {
            lastError = String(describing: error)
        }
    }
}

private final class Listener: NSObject, SseListener {
    private weak var owner: SseViewModel?

    init(owner: SseViewModel) { self.owner = owner }

    func onLine(line: String) {
        DispatchQueue.main.async {
            guard let owner = self.owner else { return }
            if owner.lines.count > 300 { owner.lines.removeFirst() }
            owner.lines.append(line)
        }
    }

    func onError(error: String) {
        DispatchQueue.main.async {
            self.owner?.lastError = error
            self.owner?.running = false
        }
    }
}
