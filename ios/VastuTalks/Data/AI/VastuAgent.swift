import UIKit

/// One thing the agent draws on her whiteboard. Coordinates are on a
/// 0–100 grid (x left→right, y top→bottom; top of the board = North),
/// so the model doesn't need to know the device's screen size.
enum BoardShape: Equatable {
    case rect(x: Double, y: Double, w: Double, h: Double, label: String?, color: String)
    case circle(x: Double, y: Double, r: Double, label: String?, color: String)
    case line(x1: Double, y1: Double, x2: Double, y2: Double, arrow: Bool, color: String)
    case label(x: Double, y: Double, text: String, color: String)

    var color: String {
        switch self {
        case .rect(_, _, _, _, _, let c), .circle(_, _, _, _, let c), .line(_, _, _, _, _, let c), .label(_, _, _, let c): return c
        }
    }
}

struct BoardDrawing: Equatable {
    let id = UUID()
    let title: String
    let shapes: [BoardShape]
}

/// What the agent says out loud (and posts to chat), plus an optional whiteboard drawing.
struct AgentTurn {
    let say: String
    var board: BoardDrawing?
}

/// Ananya, the AI agent: an OpenAI chat model playing AnanyaAgent, with
/// memory of the conversation, able to see the caller's sketches,
/// photos and videos, and to answer with a whiteboard drawing when a
/// layout is easier shown than said.
///
/// Calls the OpenAI API directly with the key from Secrets.xcconfig
/// (OPENAI_API_KEY in Info.plist). Falls back to AgentFallbackReplies if
/// there's no key or the request fails, so the call never dead-ends.
actor VastuAgent {
    /// Swap for any newer vision-capable chat model on your account.
    static let model = "gpt-4o-mini"
    static let transcribeModel = "gpt-4o-mini-transcribe"
    private static let endpoint = URL(string: "https://api.openai.com/v1/chat/completions")!
    private static let transcribeEndpoint = URL(string: "https://api.openai.com/v1/audio/transcriptions")!
    private static let maxHistory = 24

    private enum PhotoDetail {
        case low, high
        var apiValue: String { self == .low ? "low" : "high" }
        var maxSide: CGFloat { self == .low ? 512 : 1024 }
    }

    nonisolated let isConfigured: Bool
    private let apiKey: String
    private var history: [[String: Any]] = []

    init() {
        let key = (Bundle.main.object(forInfoDictionaryKey: "OPENAI_API_KEY") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        apiKey = key
        isConfigured = !key.isEmpty && !key.hasPrefix("$(")
    }

    /// Greeting when the agent "picks up" the call.
    func greet() async -> AgentTurn {
        await respond("(The caller has just connected and nothing has been drawn or shared yet. Greet them warmly in one or two sentences and ask what they'd like help with. Do not draw.)", record: false)
    }

    func reply(_ userText: String) async -> AgentTurn { await respond(userText) }

    func reviewSketch(_ sketch: UIImage, note: String = "Here's my sketch") async -> AgentTurn {
        await respond(note, images: [sketch])
    }

    /// Vastu read of a camera photo of the caller's space. Sent at higher
    /// resolution than sketches so doors, windows, mirrors and furniture
    /// are actually visible to the model. With `facing`, the model is told
    /// which direction each part of the frame is in.
    func reviewPhoto(_ photo: UIImage, question: String?, facing: PhotoFacing?) async -> AgentTurn {
        var note = "(The caller took this photo of their home with their camera"
        if let question = question, !question.isEmpty { note += " and asks: \"\(question)\"" }
        note += ". "
        if let facing = facing {
            note += facing.describeForAgent()
            note += " First work out what space this is and pick out the important things in it — doors, windows, "
            note += "bed, stove, sink, mirror, desk, safe, puja shelf, toilet, heavy furniture, plants, colours, clutter, "
            note += "light — and which direction each one is in using those directions. Then judge them by Vastu for "
            note += "those directions, mention what is already good, and give the two or three most useful corrections. "
            note += "Name the directions when you speak. You may use up to five sentences here. If moving something "
            note += "would help, draw a simple North-up plan of the room with what you saw placed in its real direction "
            note += "and an arrow showing where it should go.)"
        } else {
            note += "Say what space it is and what you notice — doors, windows, furniture, mirrors, colours, "
            note += "clutter, light — then give the two or three most useful Vastu suggestions for it. You may use "
            note += "up to five sentences here. The camera direction is unknown, so ask which way it was facing. If "
            note += "moving something would help, draw a simple plan of the space showing where it should go.)"
        }
        return await respond(note, images: [photo], detail: .high)
    }

    /// Vastu read of a short video. The model can't watch video, so it
    /// gets frames spread evenly across the clip, in order, at low detail.
    /// `facing` is the direction the camera pointed when recording started.
    func reviewVideo(frames: [UIImage], durationSeconds: Int, question: String?, facing: PhotoFacing?) async -> AgentTurn {
        var note = "(The caller recorded a \(max(1, durationSeconds))-second video of their home"
        if let question = question, !question.isEmpty { note += " and asks: \"\(question)\"" }
        note += ". You can't watch it, so here are \(frames.count) frames taken evenly across it, in order. "
        if let facing = facing {
            note += "For the first frame: "
            note += facing.describeForAgent()
            note += " The caller probably turned or walked while recording, so work out where later frames point by "
            note += "following the pan — if things slide towards the left between frames, the camera is turning "
            note += "right (clockwise, e.g. from North towards East). Only name a direction for something when you're "
            note += "fairly sure. "
        } else {
            note += "The camera direction is unknown, so ask which way the video started facing. "
        }
        note += "Treat the frames as one walkthrough, not separate photos: say what space or spaces it shows, pick out "
        note += "the important things — doors, windows, bed, stove, sink, mirror, desk, safe, puja shelf, toilet, heavy "
        note += "furniture, plants, colours, clutter, light — and where they are, mention what is already good, and give "
        note += "the two or three most useful Vastu corrections. You may use up to five sentences here. If moving "
        note += "something would help, draw a simple North-up plan of the space with an arrow showing where it should go.)"
        return await respond(note, images: frames, historyImages: 3)
    }

    /// Speech-to-text for one caller utterance (a 16 kHz WAV from
    /// CallerListener). Nil if nothing intelligible was said or the request failed.
    func transcribe(_ wav: Data) async -> String? {
        guard isConfigured else { return nil }
        let boundary = "----vastu\(Int(Date().timeIntervalSince1970 * 1000))"
        var body = Data()
        func field(_ name: String, _ value: String) {
            body.append("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n".data(using: .utf8)!)
        }
        field("model", Self.transcribeModel)
        field("prompt", "A caller asking a Vastu Shastra consultant about their home, possibly in Indian English or Hinglish.")
        body.append("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n".data(using: .utf8)!)
        body.append(wav)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        do {
            let raw = try await send(Self.transcribeEndpoint, contentType: "multipart/form-data; boundary=\(boundary)", body: body)
            let text = (try JSONSerialization.jsonObject(with: raw) as? [String: Any])?["text"] as? String
            let trimmed = text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return trimmed.isEmpty ? nil : trimmed
        } catch {
            print("VastuAgent: transcription failed: \(error)")
            return nil
        }
    }

    private func respond(_ userText: String, images: [UIImage] = [], record: Bool = true,
                         detail: PhotoDetail = .low, historyImages: Int = 1) async -> AgentTurn {
        guard isConfigured else { return fallback(userText, images) }

        let userMessage: [String: Any] = ["role": "user", "content": userContent(userText, images, detail)]
        do {
            var messages: [[String: Any]] = [["role": "system", "content": Self.systemPrompt]]
            messages += history
            messages.append(userMessage)
            let request: [String: Any] = [
                "model": Self.model,
                "messages": messages,
                "response_format": ["type": "json_object"],
                "temperature": 0.7
            ]
            let data = try await send(Self.endpoint, contentType: "application/json",
                                      body: try JSONSerialization.data(withJSONObject: request))
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let choices = json["choices"] as? [[String: Any]],
                  let message = choices.first?["message"] as? [String: Any],
                  let raw = message["content"] as? String else {
                throw AppError("Unexpected OpenAI response")
            }
            let turn = Self.parseTurn(raw)
            if record {
                // A high-detail photo costs ~25k tokens and a video's frames add
                // up; keep a few low-detail images in the history so follow-up
                // questions stay fast and cheap.
                if !images.isEmpty && (detail != .low || images.count > historyImages) {
                    let kept: [UIImage] = images.count <= historyImages ? images :
                        (0..<historyImages).map { i in images[i * (images.count - 1) / max(1, historyImages - 1)] }
                    history.append(["role": "user", "content": userContent(userText, kept, .low)])
                } else {
                    history.append(userMessage)
                }
            }
            history.append(["role": "assistant", "content": raw])
            while history.count > Self.maxHistory { history.removeFirst() }
            return turn
        } catch {
            print("VastuAgent: OpenAI request failed, using canned reply: \(error)")
            return fallback(userText, images)
        }
    }

    private func fallback(_ userText: String, _ images: [UIImage]) -> AgentTurn {
        AgentTurn(say: images.isEmpty ? AgentFallbackReplies.reply(for: userText)
                  : "Thanks for sharing that! Let me know which area of it you'd like suggestions for.")
    }

    private func userContent(_ text: String, _ images: [UIImage], _ detail: PhotoDetail) -> Any {
        if images.isEmpty { return text }
        var content: [[String: Any]] = [["type": "text", "text": text]]
        for image in images {
            content.append([
                "type": "image_url",
                "image_url": ["url": "data:image/jpeg;base64,\(Self.encode(image, maxSide: detail.maxSide))", "detail": detail.apiValue]
            ])
        }
        return content
    }

    private func send(_ url: URL, contentType: String, body: Data) async throws -> Data {
        var request = URLRequest(url: url, timeoutInterval: 45)
        request.httpMethod = "POST"
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = body
        let (data, response) = try await URLSession.shared.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else {
            throw AppError("OpenAI HTTP \(code): \(String(data: data, encoding: .utf8) ?? "")")
        }
        return data
    }

    private static func encode(_ image: UIImage, maxSide: CGFloat) -> String {
        let scaled = image.scaledDown(toMaxSide: maxSide)
        return (scaled.jpegData(compressionQuality: 0.8) ?? Data()).base64EncodedString()
    }

    private static let systemPrompt = """
        You are \(AnanyaAgent.name), a warm, practical \(AnanyaAgent.specialty) consultant on a live voice call
        inside the VastuTalks app. Everything you "say" is read aloud by text-to-speech, so speak naturally:
        1-3 short sentences, no lists, no markdown, no emojis. Stay on Vastu Shastra, home layout and related
        topics; gently steer back if asked about something unrelated. After answering, stop and let the
        caller talk — don't ramble or ask several questions at once.

        You and the caller share a square whiteboard for the whole call. The caller sketches on it with
        their finger and can send you a picture of the whole board (their strokes plus anything you drew)
        — refer to what you actually see. When a direction, room placement or layout is easier to show
        than to say, draw on it. The board is a 100x100 grid: x goes left to right (West to East), y goes
        top to bottom (North to South), so the top edge is North. Your drawing appears on top of the
        caller's sketch using the same grid, so you can mark up their plan directly. Each new drawing of
        yours replaces your previous one. Keep drawings simple (3-12 shapes) and label rooms.

        Always reply with a single JSON object:
        {
          "say": "what you speak aloud",
          "board": null | {
            "title": "short caption",
            "shapes": [
              {"type": "rect", "x": 10, "y": 10, "w": 30, "h": 25, "label": "Kitchen", "color": "saffron"},
              {"type": "circle", "x": 50, "y": 50, "r": 6, "label": "Brahmasthan", "color": "white"},
              {"type": "arrow", "x1": 50, "y1": 90, "x2": 50, "y2": 65, "color": "blue"},
              {"type": "line", "x1": 0, "y1": 50, "x2": 100, "y2": 50, "color": "copper"},
              {"type": "text", "x": 80, "y": 15, "text": "Ishan (NE)", "color": "white"}
            ]
          }
        }
        Colors: white, saffron, copper, blue, red, green. When you draw, have "say" talk the caller through it.
        """

    static func parseTurn(_ raw: String) -> AgentTurn {
        guard let data = raw.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return AgentTurn(say: "Sorry, could you say that again?")
        }
        let say = (json["say"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "Sorry, could you say that again?"
        var board: BoardDrawing?
        if let b = json["board"] as? [String: Any], let shapes = b["shapes"] as? [Any] {
            let parsed = shapes.compactMap { parseShape($0 as? [String: Any]) }
            if !parsed.isEmpty { board = BoardDrawing(title: b["title"] as? String ?? "", shapes: parsed) }
        }
        return AgentTurn(say: say, board: board)
    }

    private static func parseShape(_ o: [String: Any]?) -> BoardShape? {
        guard let o = o else { return nil }
        func f(_ key: String) -> Double { min(100, max(0, (o[key] as? NSNumber)?.doubleValue ?? 0)) }
        func label() -> String? {
            guard let l = o["label"] as? String, !l.isEmpty, l != "null" else { return nil }
            return l
        }
        let color = o["color"] as? String ?? "white"
        switch o["type"] as? String {
        case "rect": return .rect(x: f("x"), y: f("y"), w: f("w"), h: f("h"), label: label(), color: color)
        case "circle": return .circle(x: f("x"), y: f("y"), r: f("r"), label: label(), color: color)
        case "line": return .line(x1: f("x1"), y1: f("y1"), x2: f("x2"), y2: f("y2"), arrow: false, color: color)
        case "arrow": return .line(x1: f("x1"), y1: f("y1"), x2: f("x2"), y2: f("y2"), arrow: true, color: color)
        case "text":
            guard let text = o["text"] as? String, !text.isEmpty else { return nil }
            return .label(x: f("x"), y: f("y"), text: text, color: color)
        default: return nil
        }
    }
}

extension UIImage {
    /// Scaled so its longer side is at most `maxSide` points (scale 1), orientation baked in.
    func scaledDown(toMaxSide maxSide: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        let factor = min(1, maxSide / max(longest, 1))
        let target = CGSize(width: (size.width * factor).rounded(), height: (size.height * factor).rounded())
        if factor == 1 && imageOrientation == .up && scale == 1 { return self }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}
