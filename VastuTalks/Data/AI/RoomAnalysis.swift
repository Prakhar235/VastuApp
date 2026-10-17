import UIKit

/// One wall's photo from a room scan, and which way the camera faced when it was taken.
struct WallPhoto {
    let image: UIImage
    let facing: PhotoFacing
}

/// The nine Vastu zones of a room, as a 3×3 grid with North at the top.
enum RoomZone: String, CaseIterable {
    case nw = "NW", n = "N", ne = "NE", w = "W", center = "CENTER", e = "E", sw = "SW", s = "S", se = "SE"

    var row: Int { [0, 0, 0, 1, 1, 1, 2, 2, 2][Self.allCases.firstIndex(of: self)!] }
    var col: Int { [0, 1, 2, 0, 1, 2, 0, 1, 2][Self.allCases.firstIndex(of: self)!] }

    var label: String {
        switch self {
        case .nw: return tr("NW", "उ-प")
        case .n: return tr("North · wealth", "उत्तर · धन")
        case .ne: return tr("NE · Ishan", "उ-पू · ईशान")
        case .w: return tr("West", "पश्चिम")
        case .center: return tr("Brahmasthan", "ब्रह्मस्थान")
        case .e: return tr("East", "पूर्व")
        case .sw: return tr("SW", "द-प")
        case .s: return tr("South", "दक्षिण")
        case .se: return tr("SE · Agni", "द-पू · अग्नि")
        }
    }

    static func parse(_ code: String?) -> RoomZone? {
        let c = (code ?? "").trimmingCharacters(in: .whitespaces).uppercased()
        switch c {
        case "", "NULL": return nil
        case "C", "CENTRE", "CENTER", "BRAHMASTHAN": return .center
        default: return RoomZone(rawValue: c)
        }
    }
}

enum Severity: Int, Comparable {
    case major, minor, good

    static func < (a: Severity, b: Severity) -> Bool { a.rawValue < b.rawValue }

    var code: String { ["major", "minor", "good"][rawValue] }
}

/// One thing Ananya judged in the room: where it is in which photo, how
/// serious it is, why, how to fix it without moving it, and — when it
/// should move — the zone it belongs in plus a few questions to check the
/// move is possible.
struct RoomFinding: Identifiable, Equatable {
    var number: Int
    let title: String
    let objectName: String
    let severity: Severity
    let photo: Int
    /// A .rect or .circle in percent of the photo.
    let mark: BoardShape?
    let zone: RoomZone?
    var betterZone: RoomZone?
    let why: String
    let remedies: [String]
    let checks: [String]

    var id: Int { number }
}

struct RoomAnalysis {
    let room: String
    let score: Int
    let verdict: String
    let summary: String
    let findings: [RoomFinding]

    /// The photo with its findings drawn on it, numbered, for sharing into the chat.
    func markedPhoto(walls: [WallPhoto], photo: Int) -> UIImage? {
        guard walls.indices.contains(photo) else { return nil }
        let shapes: [BoardShape] = findings.filter { $0.photo == photo }.compactMap { f in
            let color = ["red", "saffron", "green"][f.severity.rawValue]
            let label = "\(f.number) \(f.objectName)"
            switch f.mark {
            case let .rect(x, y, w, h, _, _)?: return .rect(x: x, y: y, w: w, h: h, label: label, color: color)
            case let .circle(x, y, r, _, _)?: return .circle(x: x, y: y, r: r, label: label, color: color)
            default: return nil
            }
        }
        return ImageMarkup.draw(walls[photo].image, shapes: shapes)
    }

    /// The read in the same JSON shape the model returns, so parse() reads it back (for saving it with the chat).
    func toJson() -> String {
        let list: [[String: Any]] = findings.map { f in
            var mark: Any = NSNull()
            switch f.mark {
            case let .rect(x, y, w, h, _, _)?: mark = ["type": "rect", "x": x, "y": y, "w": w, "h": h]
            case let .circle(x, y, r, _, _)?: mark = ["type": "circle", "x": x, "y": y, "r": r]
            default: break
            }
            return [
                "title": f.title, "object": f.objectName, "severity": f.severity.code, "photo": f.photo,
                "mark": mark, "zone": f.zone?.rawValue ?? NSNull(), "better_zone": f.betterZone?.rawValue ?? NSNull(),
                "why": f.why, "remedies": f.remedies, "checks": f.checks
            ]
        }
        let json: [String: Any] = ["room": room, "score": score, "verdict": verdict, "summary": summary, "findings": list]
        let data = (try? JSONSerialization.data(withJSONObject: json)) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    /// The whole read as text, for the chat and for her memory of it.
    func asText() -> String {
        var text = "\(room) · \(tr("Vastu score", "वास्तु स्कोर")) \(score)/100 (\(verdict)). \(summary)"
        for f in findings {
            text += "\n\(f.number). \(f.title) — \(f.why)"
            if let better = f.betterZone { text += " \(tr("Better spot", "बेहतर जगह")): \(better.label)." }
            if f.severity != .good && !f.remedies.isEmpty { text += " **\(f.remedies.joined(separator: "; "))**" }
        }
        return text
    }

    /// What the model is asked to return for a scan (see VastuAgent.analyzeRoom).
    static func prompt() -> String {
        """
        You are \(AnanyaAgent.name), a practical Residential Vastu consultant in the
        VastuTalks app. The user photographed the walls of ONE room, guided by the phone's compass. Each photo
        has a faint yellow grid numbered along the top edge (x, 0 to 100, left to right) and the left edge
        (y, 0 to 100, top to bottom); the user sees it without the grid.

        Do a full Vastu read of the room: work out what room it is; find every Vastu-relevant thing you can
        really see (doors, windows, bed, stove, sink, mirror, desk, safe, puja shelf or idols, TV and
        electronics, heavy furniture, plants, water, clocks, pictures, colours, clutter, beams, light); work out
        which of the nine zones of the room each is in from the camera directions given; and judge each one.
        Never invent an object you can't see. At most 8 findings, problems first (major, then minor), then
        what is already good.

        \(languageRule())

        Reply with a single JSON object:
        {
          "room": "Living room",
          "score": 68,
          "verdict": "Fair",
          "summary": "1 major issue, 1 minor. 2 things are well placed.",
          "findings": [
            {
              "title": "TV on the North wall",
              "object": "TV",
              "severity": "major",
              "photo": 0,
              "mark": {"type": "rect", "x": 24, "y": 30, "w": 38, "h": 26},
              "zone": "N",
              "better_zone": "SE",
              "why": "North is the wealth zone; heavy electronics block it. The TV is fire element, which belongs in the South-East.",
              "remedies": ["Cover the TV with a cloth when it's off", "Keep a green plant on the TV unit"],
              "checks": ["Is there a power socket on the South-East wall?", "Is there room for the TV unit there (~1.8 m)?"]
            }
          ]
        }
        score: 0-100 for the whole room. verdict: 1-2 words. summary: one short sentence counting the findings.
        title: 3-6 words naming the thing and where it is. object: 1-2 words. severity: "major", "minor" or
        "good". photo: index of the photo it is in (0 = first). mark: where it is in that photo, in percent of
        the image — "rect" with x, y, w, h for big things, or "circle" with x, y and r (r is percent of the
        width) for small ones — aimed right on the object. zone and better_zone: one of NW, N, NE, W, C, E, SW, S,
        SE (C = centre / Brahmasthan); better_zone only when the thing should move, else null. why: 1-2
        sentences. remedies: for problems, 1-3 easy fixes that don't need moving it; [] for good things.
        checks: only with better_zone, 1-2 yes/no questions to see if the move is practical; else [].
        """
    }

    private static func languageRule() -> String {
        AgentLanguagePref.isHindi ? """
        Write every text field in simple everyday Hindi in Devanagari script, preferring Hindi words over
        Urdu/Persian ones; common English words people use in Hindi are fine (टीवी, बेडरूम). Name directions in
        Hindi (उत्तर, दक्षिण-पूर्व). The example values below are in English only to show the shape.
        """ : "Write every text field in simple, clear English."
    }

    static func parse(_ raw: String, photoCount: Int) throws -> RoomAnalysis {
        guard let data = raw.data(using: .utf8),
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw AppError("The room read wasn't valid JSON")
        }
        func clean(_ value: Any?) -> String {
            let s = (value as? String ?? "").replacingOccurrences(of: "**", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
            return s == "null" ? "" : s
        }
        func strings(_ value: Any?) -> [String] { (value as? [Any] ?? []).map(clean).filter { !$0.isEmpty } }

        let parsed: [RoomFinding] = (json["findings"] as? [Any] ?? []).compactMap { $0 as? [String: Any] }.prefix(8).map { o in
            let title = clean(o["title"])
            let object = clean(o["object"])
            let severity: Severity
            switch clean(o["severity"]).lowercased() {
            case "major": severity = .major
            case "good", "ok": severity = .good
            default: severity = .minor
            }
            return RoomFinding(
                number: 0,
                title: title,
                objectName: object.isEmpty ? title : object,
                severity: severity,
                photo: min(max(0, Int(int64(o["photo"]))), max(0, photoCount - 1)),
                mark: parseMark(o["mark"] as? [String: Any]),
                zone: RoomZone.parse(o["zone"] as? String),
                betterZone: RoomZone.parse(o["better_zone"] as? String),
                why: clean(o["why"]),
                remedies: strings(o["remedies"]),
                checks: strings(o["checks"])
            )
        }
        let findings = parsed.filter { !$0.title.isEmpty }
            .enumerated().sorted { ($0.element.severity, $0.offset) < ($1.element.severity, $1.offset) }.map(\.element)
            .enumerated().map { i, f -> RoomFinding in
                var finding = f
                finding.number = i + 1
                if finding.betterZone == finding.zone { finding.betterZone = nil }
                return finding
            }
        let room = clean(json["room"])
        return RoomAnalysis(
            room: room.isEmpty ? tr("Room", "कमरा") : room,
            score: min(100, max(0, Int(int64(json["score"])))),
            verdict: clean(json["verdict"]),
            summary: clean(json["summary"]),
            findings: findings
        )
    }

    private static func parseMark(_ o: [String: Any]?) -> BoardShape? {
        guard let o = o else { return nil }
        func f(_ key: String) -> Double { min(100, max(0, (o[key] as? NSNumber)?.doubleValue ?? 0)) }
        switch o["type"] as? String {
        case "rect": return .rect(x: f("x"), y: f("y"), w: min(f("w"), 100 - f("x")), h: min(f("h"), 100 - f("y")), label: nil, color: "white")
        case "circle": return .circle(x: f("x"), y: f("y"), r: f("r"), label: nil, color: "white")
        default: return nil
        }
    }
}

/// Carries a finished room scan from the scan screen into the chat with
/// Ananya, so she can answer follow-ups about it. Held in memory only;
/// the chat saves it once it's added.
final class RoomScanHandoff: ObservableObject {
    static let shared = RoomScanHandoff()

    struct Scan {
        let walls: [WallPhoto]
        let analysis: RoomAnalysis
    }

    @Published private(set) var pending: Scan?

    func send(_ scan: Scan) { pending = scan }

    /// The waiting scan, if any, handed over once.
    func take() -> Scan? {
        defer { pending = nil }
        return pending
    }
}
