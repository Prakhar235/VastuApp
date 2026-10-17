import UIKit

/// One thing the agent draws on her whiteboard (or marks on a photo).
/// Coordinates are on a 0–100 grid (x left→right, y top→bottom; on the
/// board, top = North), so the model doesn't need to know any pixel sizes.
enum BoardShape: Equatable {
    case rect(x: Double, y: Double, w: Double, h: Double, label: String?, color: String)
    case circle(x: Double, y: Double, r: Double, label: String?, color: String)
    case line(x1: Double, y1: Double, x2: Double, y2: Double, arrow: Bool, color: String, label: String?)
    case label(x: Double, y: Double, text: String, color: String)

    var color: String {
        switch self {
        case .rect(_, _, _, _, _, let c), .circle(_, _, _, _, let c), .line(_, _, _, _, _, let c, _), .label(_, _, _, let c): return c
        }
    }
}

struct BoardDrawing: Equatable {
    let id = UUID()
    let title: String
    let shapes: [BoardShape]
}

/// What the agent says out loud (and posts to chat), plus an optional
/// whiteboard drawing and/or the caller's latest photo with her marks on it.
/// `details` is her written, point-by-point read of a photo — one line per
/// numbered mark — shown in the chat but not read aloud.
struct AgentTurn {
    let say: String
    var board: BoardDrawing?
    var markedPhoto: UIImage?
    var details: String?

    /// What goes in the chat bubble: the reply plus the written details.
    var chatText: String { details.map { "\(say)\n\n\($0)" } ?? say }
}

/// On a voice call she keeps it short; in the text chat she can write more.
enum AgentMode { case call, chat }

/// Ananya, the AI agent: an OpenAI chat model playing AnanyaAgent, with
/// memory of the conversation, able to see the caller's photos and videos
/// and to answer by marking up the photo itself (circles, arrows, labels —
/// see ImageMarkup). The shared whiteboard is off for now (boardEnabled).
///
/// Calls the OpenAI API directly with the key from Secrets.xcconfig
/// (OPENAI_API_KEY in Info.plist). Falls back to AgentFallbackReplies if
/// there's no key or the request fails, so the call never dead-ends.
actor VastuAgent {
    /// Swap for any newer vision-capable chat model on your account.
    static let model = "gpt-4o-mini"
    /// For turns where the caller shares a photo or video. Tested on real
    /// room photos (Android): gpt-4.1 puts marks on the right object;
    /// gpt-4o-mini and gpt-4o often circle the wrong thing.
    static let visionModel = "gpt-4.1"
    static let transcribeModel = "gpt-4o-mini-transcribe"
    /// Re-transcribes speech that came back in Urdu script; honours `language` strictly.
    static let hindiTranscribeModel = "whisper-1"
    /// The shared whiteboard is hidden for now; flip this to bring it back (also shows the Board page).
    static let boardEnabled = false

    private static let endpoint = URL(string: "https://api.openai.com/v1/chat/completions")!
    private static let transcribeEndpoint = URL(string: "https://api.openai.com/v1/audio/transcriptions")!
    private static let maxCallHistory = 24
    /// The text chat is saved and picked up again later, so she remembers more of it.
    private static let maxChatHistory = 80
    private static let keepImageTurns = 2
    // Keep this free of example sentences: on noisy or near-silent audio the
    // transcriber can return the prompt itself, which the agent then "hears".
    private static let transcribePrompt =
        "A caller asking a Vastu Shastra consultant about their home, in English, Hindi or Hinglish."

    private enum PhotoDetail {
        case low, high
        var apiValue: String { self == .low ? "low" : "high" }
        var maxSide: CGFloat { self == .low ? 512 : 1024 }
    }

    nonisolated let isConfigured: Bool
    nonisolated let mode: AgentMode
    private let apiKey: String
    private var history: [[String: Any]] = []
    /// The caller's latest photo, or their latest video's frames, without
    /// the grid — what the agent's marks get drawn on, in later turns too.
    private(set) var markable: [UIImage] = []
    /// What the agent last said, to spot the mic picking up her own voice.
    private var lastSay = ""

    init(mode: AgentMode = .call) {
        self.mode = mode
        let key = (Bundle.main.object(forInfoDictionaryKey: "OPENAI_API_KEY") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        apiKey = key
        isConfigured = !key.isEmpty && !key.hasPrefix("$(")
    }

    // MARK: Memory

    /// The conversation as the model sees it (her replies with their marks), to save between sessions.
    func exportHistory() -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: history) else { return "[]" }
        return String(data: data, encoding: .utf8) ?? "[]"
    }

    /// Picks a saved conversation back up, so she remembers what was asked,
    /// what she saw and what she marked; `images` is the last photo or video
    /// frames, so follow-ups can still be marked on it.
    func restore(historyJson: String, images: [UIImage]) {
        history = (try? JSONSerialization.jsonObject(with: Data(historyJson.utf8)) as? [[String: Any]]) ?? []
        trimHistory()
        markable = images
    }

    // MARK: Turns

    /// Greeting when the agent "picks up" the call, or the chat opens.
    func greet() async -> AgentTurn {
        let language = Self.languageName()
        return await respond(mode == .call
            ? "(The caller has just connected and nothing has been drawn or shared yet. Greet them warmly in \(language) in one or two sentences and ask what they'd like help with. Do not draw or mark anything.)"
            : "(The user has just opened the chat and nothing has been shared yet. Greet them warmly in \(language) in one or two sentences, and tell them they can ask a question, upload a photo of any room, or scan a whole room wall by wall, for Vastu suggestions marked on the photo. Do not mark anything.)",
            record: false)
    }

    func reply(_ userText: String) async -> AgentTurn { await respond(userText) }

    /// A turn on a video call: what the caller said plus what their camera
    /// shows at that moment (with `facing` when the compass knows it), so she
    /// can talk about what's in front of them and mark it. Low detail, to
    /// keep spoken replies quick.
    func replyWithView(_ userText: String, view: UIImage, facing: PhotoFacing?) async -> AgentTurn {
        var note = "(Video call: the caller's camera is on and this image is what it shows right now. "
        if let facing = facing { note += facing.describeForAgent() + " " }
        note += "Answer what they said. Use the view when it's relevant — name what you see and its direction — "
        note += "and mark it when pointing something out helps; don't describe the view if they asked about "
        note += "something else.) They said: \(userText)"
        markable = [view]
        return await respond(note, images: [ImageMarkup.withGrid(view)], model: Self.visionModel)
    }

    /// A full Vastu read of one room from its wall photos (the guided room
    /// scan). Not part of the conversation; see attachRoomScan to bring it
    /// into the chat. Nil if there's no API key or the request failed.
    func analyzeRoom(_ walls: [WallPhoto]) async -> RoomAnalysis? {
        guard isConfigured, !walls.isEmpty else { return nil }
        var note = "Here are \(walls.count) photos of the room, in order (photo 0 to \(walls.count - 1))."
        for (i, wall) in walls.enumerated() { note += " Photo \(i): " + wall.facing.describeForAgent() }
        let request: [String: Any] = [
            "model": Self.visionModel,
            "messages": [
                ["role": "system", "content": RoomAnalysis.prompt()],
                ["role": "user", "content": userContent(note, walls.map { ImageMarkup.withGrid($0.image) }, .high)]
            ],
            "response_format": ["type": "json_object"],
            "temperature": 0.4
        ]
        do {
            let started = Date()
            let raw = try await post(request)
            print("VastuAgent: room analysed in \(Int(Date().timeIntervalSince(started) * 1000)) ms")
            return try RoomAnalysis.parse(raw, photoCount: walls.count)
        } catch {
            print("VastuAgent: room analysis failed: \(error)")
            return nil
        }
    }

    /// Brings a room scan into the conversation, so follow-up questions about
    /// the room work: she remembers the photos and her read of them, and
    /// later marks go on those photos (frame = photo index).
    func attachRoomScan(walls: [WallPhoto], analysis: RoomAnalysis) {
        var note = "(I scanned my room wall by wall: \(walls.count) photos, in order (frame 0 to \(walls.count - 1))."
        for (i, wall) in walls.enumerated() { note += " Frame \(i): " + wall.facing.describeForAgent() }
        note += ")"
        history.append(["role": "user", "content": userContent(note, walls.map { ImageMarkup.withGrid($0.image) }, .low)])
        let say = (try? JSONSerialization.data(withJSONObject: ["say": analysis.asText()])).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        history.append(["role": "assistant", "content": say])
        markable = walls.map(\.image)
        trimHistory()
    }

    func reviewSketch(_ sketch: UIImage, note: String = "Here's my sketch") async -> AgentTurn {
        await respond(note, images: [sketch])
    }

    /// Vastu read of a photo of the caller's space, sent at high detail so
    /// doors, windows, mirrors and furniture are visible to the model. With
    /// `facing`, the model is told which direction each part of the frame is in.
    func reviewPhoto(_ photo: UIImage, question: String?, facing: PhotoFacing?) async -> AgentTurn {
        var note = "(The caller shared this photo of their home"
        if let question = question, !question.isEmpty { note += " and asks: \"\(question)\"" }
        note += ". "
        if let facing = facing {
            note += facing.describeForAgent() + " "
        } else {
            note += "The camera direction is unknown; judge what you can without directions and ask which way it faced. "
        }
        note += Self.photoReadSteps + ")"
        markable = [photo]
        return await respond(note, images: [ImageMarkup.withGrid(photo)], detail: .high, model: Self.visionModel)
    }

    /// Vastu read of a short video. The model can't watch video, so it gets
    /// frames spread evenly across the clip, in order, at low detail.
    /// `facing` is the direction the camera pointed when recording started.
    func reviewVideo(frames: [UIImage], durationSeconds: Int, question: String?, facing: PhotoFacing?) async -> AgentTurn {
        var note = "(The caller recorded a \(max(1, durationSeconds))-second video of their home"
        if let question = question, !question.isEmpty { note += " and asks: \"\(question)\"" }
        note += ". You can't watch it, so here are \(frames.count) frames taken evenly across it, in order "
        note += "(frame 0 to \(frames.count - 1)). "
        if let facing = facing {
            note += "For the first frame: " + facing.describeForAgent()
            note += " The caller probably turned or walked while recording, so work out where later frames point by "
            note += "following the pan — if things slide towards the left between frames, the camera is turning "
            note += "right (clockwise, e.g. from North towards East). Only name a direction for something when you're "
            note += "fairly sure. "
        } else {
            note += "The camera direction is unknown, so ask which way the video started facing. "
        }
        note += "Treat the frames as one walkthrough, not separate photos. " + Self.photoReadSteps
        note += " Put the marks on the one frame that shows the most of what you judge.)"
        markable = frames
        return await respond(note, images: frames.map(ImageMarkup.withGrid), historyImages: 3, model: Self.visionModel)
    }

    // MARK: Speech to text

    /// Speech-to-text for one caller utterance (a 16 kHz WAV from
    /// CallerListener). Nil if nothing intelligible was said, it was her own
    /// voice picked up by the mic, or the request failed.
    ///
    /// Spoken Hindi and Urdu sound almost the same, so auto-detection
    /// sometimes writes Hindi speech in Urdu (Arabic) script. The app only
    /// supports English and Hindi, so then it's re-run with whisper-1, which
    /// sticks to the language it's given.
    func transcribe(_ wav: Data) async -> String? {
        guard isConfigured else { return nil }
        do {
            let started = Date()
            let text = try await transcribe(wav, model: Self.transcribeModel, language: nil)
            var heard = text
            if let text = text, text.unicodeScalars.contains(where: Self.isArabicScript) {
                heard = (try? await transcribe(wav, model: Self.hindiTranscribeModel, language: "hi")) ?? text
            }
            print("VastuAgent: heard in \(Int(Date().timeIntervalSince(started) * 1000)) ms: \"\(text ?? "")\"" +
                  (heard != text ? " → as Hindi: \"\(heard ?? "")\"" : ""))
            guard let result = heard, !isEchoOfAgent(result) else { return nil }
            return result
        } catch {
            print("VastuAgent: transcription failed: \(error)")
            return nil
        }
    }

    /// True if `heard` is just a piece of the agent's last reply (her voice leaking into the mic).
    private func isEchoOfAgent(_ heard: String) -> Bool {
        let h = Self.normalized(heard)
        return h.count >= 6 && Self.normalized(lastSay).contains(h)
    }

    private func transcribe(_ wav: Data, model: String, language: String?) async throws -> String? {
        let boundary = "----vastu\(Int(Date().timeIntervalSince1970 * 1000))"
        var body = Data()
        func field(_ name: String, _ value: String) {
            body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n".utf8))
        }
        field("model", model)
        field("prompt", Self.transcribePrompt)
        if let language = language { field("language", language) }
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n".utf8))
        body.append(wav)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        let raw = try await send(Self.transcribeEndpoint, contentType: "multipart/form-data; boundary=\(boundary)", body: body)
        let text = ((try JSONSerialization.jsonObject(with: raw) as? [String: Any])?["text"] as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        // The prompt echoed back (sometimes as "Context: <prompt>") means nothing intelligible was said.
        let heard = Self.normalized(text)
        let prompt = Self.normalized(Self.transcribePrompt)
        let echoesPrompt = heard.count > 10 && (prompt.contains(heard) || heard.contains(String(prompt.prefix(30))))
        return text.isEmpty || echoesPrompt ? nil : text
    }

    // MARK: Requests

    private func respond(_ userText: String, images: [UIImage] = [], record: Bool = true,
                         detail: PhotoDetail = .low, historyImages: Int = 1, model: String = VastuAgent.model) async -> AgentTurn {
        guard isConfigured else { return fallback(userText, images) }

        let userMessage: [String: Any] = ["role": "user", "content": userContent(userText, images, detail)]
        var messages: [[String: Any]] = [["role": "system", "content": Self.systemPrompt(mode)]]
        messages += history
        messages.append(userMessage)
        let request: [String: Any] = [
            "model": model,
            "messages": messages,
            "response_format": ["type": "json_object"],
            "temperature": 0.7
        ]
        do {
            let started = Date()
            let raw = try await post(request)
            var turn = Self.parseTurn(raw)
            if let marks = Self.parseMarks(raw), !markable.isEmpty {
                let target = markable[min(max(0, marks.frame), markable.count - 1)]
                turn.markedPhoto = ImageMarkup.draw(target, shapes: marks.shapes)
            }
            print("VastuAgent: replied in \(Int(Date().timeIntervalSince(started) * 1000)) ms: \"\(turn.say)\"")
            lastSay = turn.say
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
            trimHistory()
            return turn
        } catch {
            print("VastuAgent: OpenAI request failed, using canned reply: \(error)")
            return fallback(userText, images)
        }
    }

    /// Keeps the history to its limit. Only the latest keepImageTurns photo
    /// turns keep their images; older ones become a text note, so a long chat
    /// stays fast and cheap but she still knows what was shared.
    private func trimHistory() {
        let limit = mode == .chat ? Self.maxChatHistory : Self.maxCallHistory
        if history.count > limit { history.removeFirst(history.count - limit) }
        var imageTurns = 0
        for i in history.indices.reversed() {
            guard let content = history[i]["content"] as? [[String: Any]] else { continue }
            imageTurns += 1
            if imageTurns <= Self.keepImageTurns { continue }
            let text = content.first?["text"] as? String ?? ""
            history[i] = ["role": "user", "content": "\(text) [photo shared earlier; no longer attached]"]
        }
    }

    private func fallback(_ userText: String, _ images: [UIImage]) -> AgentTurn {
        AgentTurn(say: images.isEmpty ? AgentFallbackReplies.reply(for: userText)
                  : tr("Thanks for sharing! I can't look at it properly right now — please send it again in a little while.",
                       "शेयर करने के लिए धन्यवाद! अभी मैं इसे ठीक से देख नहीं पा रही हूँ — थोड़ी देर बाद फिर से भेजिए।"))
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

    /// Sends a chat request and returns the reply's content (a JSON string).
    private func post(_ request: [String: Any]) async throws -> String {
        let data = try await send(Self.endpoint, contentType: "application/json",
                                  body: try JSONSerialization.data(withJSONObject: request))
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = json["choices"] as? [[String: Any]],
              let message = choices.first?["message"] as? [String: Any],
              let raw = message["content"] as? String else {
            throw AppError("Unexpected OpenAI response")
        }
        return raw
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
        (image.scaledDown(toMaxSide: maxSide).jpegData(compressionQuality: 0.8) ?? Data()).base64EncodedString()
    }

    private static func normalized(_ s: String) -> String {
        String(s.lowercased().unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.map(Character.init))
    }

    /// Arabic, Urdu and Persian letters (including the presentation-form blocks).
    private static func isArabicScript(_ c: Unicode.Scalar) -> Bool {
        (0x0600...0x06FF).contains(c.value) || (0x0750...0x077F).contains(c.value) || (0x08A0...0x08FF).contains(c.value) ||
            (0xFB50...0xFDFF).contains(c.value) || (0xFE70...0xFEFF).contains(c.value)
    }

    // MARK: Prompts

    private static func languageName() -> String { AgentLanguagePref.isHindi ? "Hindi" : "English" }

    /// What she does with every shared photo, asked or not: find what's in
    /// it, judge each thing, and mark and number it so the written list and
    /// the photo line up.
    private static let photoReadSteps =
        "Even if they asked nothing specific, do a full Vastu read of it: 1) work out what room or space this is; " +
        "2) find every Vastu-relevant thing you can actually see — doors, windows, bed, stove, sink, mirror, desk, " +
        "safe or locker, puja shelf or idols, toilet, staircase, heavy furniture, TV, plants, water, clocks, " +
        "pictures, colours, clutter, beams, light — and, where the directions are known, which direction each " +
        "is in; 3) judge each one by Vastu: what is already good, what is a problem and why. Mark every thing " +
        "you judge on the photo (up to 8 marks), numbered in the same order as your \"details\" list. In \"say\" " +
        "give the overall verdict and the two or three most important corrections, naming the directions."

    private static func systemPrompt(_ mode: AgentMode) -> String {
        let style = mode == .call ? """
            You are \(AnanyaAgent.name), a warm, practical \(AnanyaAgent.specialty) consultant on a live voice call
            inside the VastuTalks app. Everything you "say" is read aloud by text-to-speech, so speak naturally:
            1-3 short sentences (up to five when reading a photo or video), no lists, no emojis, and no markdown
            except the ** highlighting below.
            After answering, stop and let the caller talk — don't ramble or ask several questions at once.
            Don't open every reply with a greeting; greet only once, at the start of the call.
            """ : """
            You are \(AnanyaAgent.name), a warm, practical \(AnanyaAgent.specialty) consultant chatting by text
            inside the VastuTalks app. Write plain text (no emojis, and no markdown except the ** highlighting below): a short paragraph, or a few
            short lines starting with "• " when listing steps. Be specific and practical, and don't pad.
            Don't open every reply with a greeting; greet only once, at the start of the chat.
            """
        return style + "\n" + """
            Stay on Vastu Shastra, home layout and related topics; gently steer back if asked about something
            unrelated.
            """ + "\n\n" + languageRule() + "\n\n" + """
            Highlighting: wrap every suggestion — each thing you advise the caller to do, change, move, add or
            avoid — in double asterisks, e.g. "**Keep the bed in the South-West corner**", so it stands out in
            the chat. Highlight only the advice itself (a short phrase or one sentence), not explanations,
            greetings or praise. This applies to "say" and to each line of "details"; never use asterisks in
            mark labels.
            """ + "\n\n" + (boardEnabled ? boardPrompt : "") + marksPrompt
    }

    /// The reply language, from the switch on the chat and call screens
    /// (English by default). Read on every request, so switching takes
    /// effect from her next reply even mid-conversation.
    private static func languageRule() -> String {
        AgentLanguagePref.isHindi ? """
            Language: the user has chosen Hindi. Always reply in simple everyday Hindi written in Devanagari
            script, preferring Hindi words over Urdu/Persian ones (सुझाव not मशवरा, दिशा not तरफ़, ज़रूर is fine),
            even if the user writes in English or Hinglish, and even if earlier replies were in English. Common
            English words people use in Hindi are fine (बेडरूम, किचन, मिरर). Never reply in Urdu, Arabic or any
            other language, and never write in Urdu/Arabic script. Speech-to-text sometimes writes the user's Hindi
            in Urdu script by mistake — treat that as Hindi. Never tell the user they are speaking Urdu, and never
            comment on which language they use. You are a woman: in Hindi use feminine forms for yourself (मैं कर
            सकती हूँ, मैं बताती हूँ). Name directions in Hindi with the Vastu name where it helps (उत्तर-पूर्व /
            ईशान). Labels on drawings and photo marks are short and in Hindi.
            """ : """
            Language: the user has chosen English. Always reply in simple, clear English, even if the user writes
            in Hindi or Hinglish, and even if earlier replies were in Hindi (they can switch the app to Hindi
            themselves). Never reply in Urdu or any other language. Name directions in English, adding the Vastu
            name where it helps (North-East / Ishan). Labels on drawings and photo marks are short and in English.
            """
    }

    private static let boardPrompt = """
        You and the caller share a square whiteboard for the whole call. The caller sketches on it with
        their finger and can send you a picture of the whole board (their strokes plus anything you drew)
        — refer to what you actually see. When a direction, room placement or layout is easier to show
        than to say, draw on it. The board is a 100x100 grid: x goes left to right (West to East), y goes
        top to bottom (North to South), so the top edge is North. Your drawing appears on top of the
        caller's sketch using the same grid, so you can mark up their plan directly. Each new drawing of
        yours replaces your previous one. Keep drawings simple (3-12 shapes) and label rooms. To draw, add
        to your JSON reply:
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
        Colors: white, saffron, copper, blue, red, green. When you draw, have "say" talk the caller through it.


        """

    private static let marksPrompt = """
        When the caller shares a photo or video of their home, mark up that actual image so they can see
        exactly what you mean. The images you get have a faint yellow grid numbered along the top edge (x, 0
        to 100, left to right) and the left edge (y, 0 to 100, top to bottom); use it to put each mark right
        on the object, aiming at its centre; use a rect for big things like a bed or cupboard. The caller sees
        their photo without the grid. Only mark things you can really see — never guess an object that isn't
        there. Circle or box a problem in red, something good in green, and when you suggest moving
        something, add a green arrow from it to a visible spot where it should go. Start each circle or box
        label with its number, then 2-4 words ("1 Mirror facing bed", "2 Puja spot good"); arrows and text
        need no number. Never label a wall or corner with a direction unless you know which way the camera
        faced. Have "say" point to the marks by number and colour ("red circle 1", "green arrow"). Marks go on the
        most recent photo or video, also in follow-up answers about it; for a video, say which frame. Don't
        add marks when no photo or video has been shared or when you aren't talking about something in it.

        With marks, also fill "details": one line per numbered mark, in order, written for the caller to
        read — "1. <what it is and where> — <fine / problem: why> — **<what to do>**" — so the photo and the
        list match. Leave "details" null when there are no marks.

        Always reply with a single JSON object:
        {
          "say": "what you speak aloud",
          "details": null | "1. ...\\n2. ...",
          "marks": null | {
            "frame": 0,
            "shapes": [
              {"type": "circle", "x": 62, "y": 40, "r": 8, "label": "1 Mirror facing bed", "color": "red"},
              {"type": "rect", "x": 10, "y": 55, "w": 30, "h": 25, "label": "2 Bed", "color": "green"},
              {"type": "arrow", "x1": 30, "y1": 70, "x2": 15, "y2": 35, "label": "Move bed here", "color": "green"},
              {"type": "text", "x": 80, "y": 12, "text": "North wall", "color": "saffron"}
            ]
          }
        }
        Marks use percent of the image: x of its width, y of its height; a circle's r is percent of the width.
        """

    // MARK: Parsing

    private static func json(_ raw: String) -> [String: Any]? {
        (try? JSONSerialization.jsonObject(with: Data(raw.utf8))) as? [String: Any]
    }

    /// Photo marks in a reply, or nil when there are none.
    static func parseMarks(_ raw: String) -> PhotoMarks? {
        guard let m = json(raw)?["marks"] as? [String: Any], let shapes = m["shapes"] as? [Any] else { return nil }
        let parsed = shapes.compactMap { parseShape($0 as? [String: Any]) }
        return parsed.isEmpty ? nil : PhotoMarks(frame: Int(int64(m["frame"])), shapes: parsed)
    }

    static func parseTurn(_ raw: String) -> AgentTurn {
        let sorry = tr("Sorry, could you say that again?", "माफ़ कीजिए, क्या आप फिर से बता सकते हैं?")
        guard let json = json(raw) else { return AgentTurn(say: sorry) }
        let say = (json["say"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? sorry
        let details = (json["details"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        var board: BoardDrawing?
        if boardEnabled, let b = json["board"] as? [String: Any], let shapes = b["shapes"] as? [Any] {
            let parsed = shapes.compactMap { parseShape($0 as? [String: Any]) }
            if !parsed.isEmpty { board = BoardDrawing(title: b["title"] as? String ?? "", shapes: parsed) }
        }
        return AgentTurn(say: say, board: board, details: details.flatMap { $0.isEmpty || $0 == "null" ? nil : $0 })
    }

    private static func parseShape(_ o: [String: Any]?) -> BoardShape? {
        guard let o = o else { return nil }
        func f(_ key: String) -> Double { min(100, max(0, (o[key] as? NSNumber)?.doubleValue ?? 0)) }
        func clean(_ key: String) -> String? {
            guard let l = (o[key] as? String)?.replacingOccurrences(of: "**", with: ""), !l.isEmpty, l != "null" else { return nil }
            return l
        }
        let color = o["color"] as? String ?? "white"
        switch o["type"] as? String {
        case "rect": return .rect(x: f("x"), y: f("y"), w: f("w"), h: f("h"), label: clean("label"), color: color)
        case "circle": return .circle(x: f("x"), y: f("y"), r: f("r"), label: clean("label"), color: color)
        case "line": return .line(x1: f("x1"), y1: f("y1"), x2: f("x2"), y2: f("y2"), arrow: false, color: color, label: nil)
        case "arrow": return .line(x1: f("x1"), y1: f("y1"), x2: f("x2"), y2: f("y2"), arrow: true, color: color, label: clean("label"))
        case "text": return clean("text").map { .label(x: f("x"), y: f("y"), text: $0, color: color) }
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
