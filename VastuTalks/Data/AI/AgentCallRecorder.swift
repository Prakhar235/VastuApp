import Photos
import UIKit

/// Saves a call with the AI agent as it happens: every line said (by
/// the caller or Ananya) is appended to transcript.txt straight away,
/// and every board snapshot (PNG), camera photo (JPEG) and video (MP4)
/// is written — so nothing is lost if the app is killed mid-call.
///
/// Files go to the app's Documents/AgentCalls/<call time>/ folder,
/// which shows in the Files app (On My iPhone › Vastu Talks). Drawings,
/// photos and videos are also added to the photo library, like the
/// Android app adds them to the Gallery.
final class AgentCallRecorder {
    private let startedAt = Date()
    private let callName: String
    private let io = DispatchQueue(label: "AgentCallRecorder")
    private var drawingCount = 0
    private var photoCount = 0
    private var videoCount = 0

    let folder: URL
    private let transcript: URL

    init() {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        callName = formatter.string(from: startedAt)
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        folder = documents.appendingPathComponent("AgentCalls/\(callName)", isDirectory: true)
        transcript = folder.appendingPathComponent("transcript.txt")
        let header = "VastuTalks call with \(AnanyaAgent.name) (AI agent) — \(callName)\n\n"
        io.async { [folder, transcript] in
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try? header.write(to: transcript, atomically: true, encoding: .utf8)
        }
    }

    func logLine(_ speaker: String, _ text: String) {
        let line = "[\(elapsed())] \(speaker): \(text)\n"
        io.async { [transcript] in
            guard let handle = try? FileHandle(forWritingTo: transcript) else { return }
            handle.seekToEndOfFile()
            handle.write(line.data(using: .utf8)!)
            try? handle.close()
        }
    }

    /// Saves a board snapshot and notes it in the transcript. Returns the file name used.
    @discardableResult
    func saveDrawing(_ image: UIImage, who: String, caption: String) -> String {
        drawingCount += 1
        let name = String(format: "drawing_%02d_%@.png", drawingCount, who.lowercased().replacingOccurrences(of: " ", with: "_"))
        logLine(who, "[\(caption) — saved as \(name)]")
        let file = folder.appendingPathComponent(name)
        io.async {
            guard let data = image.pngData(), (try? data.write(to: file)) != nil else { return }
            Self.addToPhotoLibrary(imageFile: file)
        }
        return name
    }

    /// Saves a camera photo the caller shared, and notes it in the transcript.
    func savePhoto(_ image: UIImage, caption: String) {
        photoCount += 1
        let name = String(format: "photo_%02d.jpg", photoCount)
        logLine("You", "[\(caption) — saved as \(name)]")
        let file = folder.appendingPathComponent(name)
        io.async {
            guard let data = image.jpegData(compressionQuality: 0.9), (try? data.write(to: file)) != nil else { return }
            Self.addToPhotoLibrary(imageFile: file)
        }
    }

    /// Moves a video the caller shared into the call's folder, and notes it in the transcript.
    func saveVideo(_ source: URL, caption: String) {
        videoCount += 1
        let name = String(format: "video_%02d.mp4", videoCount)
        logLine("You", "[\(caption) — saved as \(name)]")
        let file = folder.appendingPathComponent(name)
        io.async {
            do {
                try FileManager.default.moveItem(at: source, to: file)
            } catch {
                guard (try? FileManager.default.copyItem(at: source, to: file)) != nil else { return }
                try? FileManager.default.removeItem(at: source)
            }
            Self.addToPhotoLibrary(videoFile: file)
        }
    }

    /// Call when the call ends: closes the transcript.
    func finish() {
        let line = "\nCall ended after \(elapsed()).\n"
        io.async { [transcript] in
            guard let handle = try? FileHandle(forWritingTo: transcript) else { return }
            handle.seekToEndOfFile()
            handle.write(line.data(using: .utf8)!)
            try? handle.close()
        }
    }

    private func elapsed() -> String {
        let s = Int(Date().timeIntervalSince(startedAt))
        return String(format: "%d:%02d", s / 60, s % 60)
    }

    private static func addToPhotoLibrary(imageFile: URL? = nil, videoFile: URL? = nil) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else { return }
            PHPhotoLibrary.shared().performChanges {
                if let imageFile = imageFile {
                    PHAssetCreationRequest.creationRequestForAssetFromImage(atFileURL: imageFile)
                }
                if let videoFile = videoFile {
                    PHAssetCreationRequest.creationRequestForAssetFromVideo(atFileURL: videoFile)
                }
            }
        }
    }
}
