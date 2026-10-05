import AVFoundation

/// Ananya's spoken voice: the system's built-in text-to-speech (no extra
/// dependency, works offline). `isSpeaking` lets the call screen keep the
/// mic closed so she doesn't hear herself.
///
/// Each utterance also gets a generous timeout, so a synthesizer that
/// never reports "done" can't leave her stuck "speaking" (which would
/// keep the mic closed forever).
final class AgentVoice: NSObject, AVSpeechSynthesizerDelegate {
    private(set) var isSpeaking = false {
        didSet { if isSpeaking != oldValue { onSpeakingChanged?(isSpeaking) } }
    }
    var onSpeakingChanged: ((Bool) -> Void)?

    private let synthesizer = AVSpeechSynthesizer()
    private var pending: [ObjectIdentifier: DispatchWorkItem] = [:]
    private let voice = AVSpeechSynthesisVoice(language: "en-IN") ?? AVSpeechSynthesisVoice(language: "en-US")

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func speak(_ text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = voice
        let id = ObjectIdentifier(utterance)
        let timeout = DispatchWorkItem { [weak self] in self?.finished(id) }
        pending[id] = timeout
        isSpeaking = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 4 + Double(text.count) * 0.12, execute: timeout)
        synthesizer.speak(utterance)
    }

    func stop() {
        synthesizer.stopSpeaking(at: .immediate)
        pending.values.forEach { $0.cancel() }
        pending.removeAll()
        isSpeaking = false
    }

    private func finished(_ id: ObjectIdentifier) {
        pending.removeValue(forKey: id)?.cancel()
        isSpeaking = !pending.isEmpty
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        let id = ObjectIdentifier(utterance)
        DispatchQueue.main.async { self.finished(id) }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        let id = ObjectIdentifier(utterance)
        DispatchQueue.main.async { self.finished(id) }
    }
}
