import UIKit

/// Keeps the text chat with Ananya on the phone, so it's still there next
/// time and she picks up where she left off. Saves three things in the
/// app's private storage (Application Support/agent_chat/):
///  - the chat as shown: every message, its images as JPEGs;
///  - the conversation as the model sees it (VastuAgent.exportHistory),
///    which includes her marks, so follow-up questions have the context;
///  - the last photo or video frames she can still mark up.
///
/// Writes happen in order on one background queue.
final class AgentChatStore {
    struct Saved {
        let messages: [AgentChatMessage]
        let history: String
        let markable: [UIImage]
    }

    private let dir: URL
    private let chatFile: URL
    private let io = DispatchQueue(label: "AgentChatStore")

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        dir = support.appendingPathComponent("agent_chat", isDirectory: true)
        chatFile = dir.appendingPathComponent("chat.json")
    }

    /// The saved chat, or nil if there isn't one (or it can't be read). Call off the main thread.
    func load() -> Saved? {
        io.sync {
            guard let data = try? Data(contentsOf: chatFile),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let list = json["messages"] as? [[String: Any]] else { return nil }
            let messages = list.map { m -> AgentChatMessage in
                let imageName = m["image"] as? String ?? ""
                return AgentChatMessage(
                    id: Int(int64(m["id"])),
                    text: m["text"] as? String ?? "",
                    sender: m["sender"] as? String == "USER" ? .user : .agent,
                    image: imageName.isEmpty ? nil : UIImage(contentsOfFile: dir.appendingPathComponent(imageName).path),
                    hasReport: m["report"] as? Bool ?? false
                )
            }
            let markable = (0..<Int(int64(json["markableCount"]))).compactMap {
                UIImage(contentsOfFile: dir.appendingPathComponent(markableName($0)).path)
            }
            return Saved(messages: messages, history: json["history"] as? String ?? "[]", markable: markable)
        }
    }

    /// Saves the chat and the model's history. Images already on disk aren't written again.
    func save(messages: [AgentChatMessage], history: String, markableCount: Int) {
        io.async { [dir, chatFile] in
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let list: [[String: Any]] = messages.map { m in
                var imageName = ""
                if let image = m.image {
                    imageName = "msg_\(m.id).jpg"
                    let file = dir.appendingPathComponent(imageName)
                    if !FileManager.default.fileExists(atPath: file.path) {
                        try? image.jpegData(compressionQuality: 0.85)?.write(to: file)
                    }
                }
                return ["id": m.id, "text": m.text, "sender": m.sender == .user ? "USER" : "AGENT",
                        "image": imageName, "report": m.hasReport]
            }
            let json: [String: Any] = ["messages": list, "history": history, "markableCount": markableCount]
            // Written atomically, so a crash mid-write can't corrupt the saved chat.
            if let data = try? JSONSerialization.data(withJSONObject: json) {
                try? data.write(to: chatFile, options: .atomic)
            }
        }
    }

    /// Saves the photo or video frames her marks go on (replacing the previous ones).
    func saveMarkable(_ images: [UIImage]) {
        io.async { [dir] in
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            let old = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
            old.filter { $0.hasPrefix("markable_") }.forEach { try? FileManager.default.removeItem(at: dir.appendingPathComponent($0)) }
            for (i, image) in images.enumerated() {
                try? image.jpegData(compressionQuality: 0.9)?.write(to: dir.appendingPathComponent(self.markableName(i)))
            }
        }
    }

    /// Saves a room scan's full report (photos, directions and the read) for the message `messageId`.
    func saveReport(messageId: Int, walls: [WallPhoto], analysis: RoomAnalysis) {
        io.async { [dir] in
            let reportDir = dir.appendingPathComponent(self.reportName(messageId), isDirectory: true)
            try? FileManager.default.createDirectory(at: reportDir, withIntermediateDirectories: true)
            var wallList: [[String: Any]] = []
            for (i, wall) in walls.enumerated() {
                try? wall.image.jpegData(compressionQuality: 0.9)?.write(to: reportDir.appendingPathComponent("wall_\(i).jpg"))
                wallList.append(["degrees": wall.facing.degrees, "source": wall.facing.source.rawValue])
            }
            let json: [String: Any] = ["walls": wallList, "analysis": analysis.toJson()]
            if let data = try? JSONSerialization.data(withJSONObject: json) {
                try? data.write(to: reportDir.appendingPathComponent("report.json"), options: .atomic)
            }
        }
    }

    /// The room report saved for `messageId`, or nil. Call off the main thread.
    func loadReport(messageId: Int) -> RoomScanHandoff.Scan? {
        io.sync {
            let reportDir = dir.appendingPathComponent(reportName(messageId), isDirectory: true)
            guard let data = try? Data(contentsOf: reportDir.appendingPathComponent("report.json")),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let wallList = json["walls"] as? [[String: Any]],
                  let analysisJson = json["analysis"] as? String else { return nil }
            var walls: [WallPhoto] = []
            for (i, w) in wallList.enumerated() {
                guard let image = UIImage(contentsOfFile: reportDir.appendingPathComponent("wall_\(i).jpg").path) else { return nil }
                let source = PhotoFacing.Source(rawValue: w["source"] as? String ?? "") ?? .caller
                walls.append(WallPhoto(image: image, facing: PhotoFacing(degrees: (w["degrees"] as? NSNumber)?.doubleValue ?? 0, source: source)))
            }
            guard let analysis = try? RoomAnalysis.parse(analysisJson, photoCount: walls.count) else { return nil }
            return RoomScanHandoff.Scan(walls: walls, analysis: analysis)
        }
    }

    /// Deletes the saved chat, for a fresh start.
    func clear() {
        io.async { [dir] in try? FileManager.default.removeItem(at: dir) }
    }

    private func markableName(_ i: Int) -> String { "markable_\(i).jpg" }
    private func reportName(_ messageId: Int) -> String { "report_\(messageId)" }
}
