import Foundation

/// The language Ananya replies in, and the agent screens are shown in.
enum AgentLanguage: String {
    case english = "ENGLISH"
    case hindi = "HINDI"
}

/// The chosen AgentLanguage, English unless the user switched to Hindi
/// (on the chat or call screen). Saved on the phone. Screens that show
/// translated text observe `shared`, so they redraw when it changes.
final class AgentLanguagePref: ObservableObject {
    static let shared = AgentLanguagePref()
    private static let key = "agent_language"

    @Published private(set) var current: AgentLanguage

    /// Read from any thread (VastuAgent builds prompts off the main thread).
    private static let lock = NSLock()
    private static var stored = AgentLanguage.english

    static var isHindi: Bool {
        lock.lock()
        defer { lock.unlock() }
        return stored == .hindi
    }

    private init() {
        let saved = AgentLanguage(rawValue: UserDefaults.standard.string(forKey: Self.key) ?? "") ?? .english
        current = saved
        Self.stored = saved
    }

    func set(_ language: AgentLanguage) {
        Self.lock.lock()
        Self.stored = language
        Self.lock.unlock()
        current = language
        UserDefaults.standard.set(language.rawValue, forKey: Self.key)
    }
}

/// The English or Hindi version of a piece of UI text, for the chosen language.
func tr(_ english: String, _ hindi: String) -> String { AgentLanguagePref.isHindi ? hindi : english }
