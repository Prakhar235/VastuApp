import AVFoundation
import SwiftUI
import UniformTypeIdentifiers

/// A photo from the camera, plus the compass heading it was taken at if the camera recorded one.
struct CapturedPhoto: Identifiable {
    let id = UUID()
    let image: UIImage
    let exifHeading: Double?
}

/// A short clip: the video file itself (kept with the call) and a handful
/// of frames spread across it — the AI model can't watch video, so it's
/// shown these instead, in order.
struct CapturedVideo: Identifiable {
    let id = UUID()
    let file: URL
    let frames: [UIImage]
    let durationSeconds: Double
}

enum CameraMode: String, Identifiable {
    case photo, video
    var id: String { rawValue }
}

enum MediaLoader {
    /// Full-size photo, orientation baked in, scaled to at most `maxSide` px.
    static func photo(_ image: UIImage, metadata: [String: Any]?, maxSide: CGFloat = 1600) -> CapturedPhoto {
        let gps = metadata?[kCGImagePropertyGPSDictionary as String] as? [String: Any]
        let heading = (gps?[kCGImagePropertyGPSImgDirection as String] as? NSNumber)?.doubleValue
        return CapturedPhoto(
            image: image.scaledDown(toMaxSide: maxSide),
            exifHeading: heading.flatMap { (0...360).contains($0) ? $0 : nil }
        )
    }

    /// Pulls evenly spaced frames: about one every 3 seconds, at least 3 and at most `maxFrames`.
    static func video(_ file: URL, maxFrames: Int = 8, frameMaxSide: CGFloat = 1024) -> CapturedVideo? {
        let asset = AVURLAsset(url: file)
        // Synchronous on purpose: this already runs off the main thread, and iOS 16's async load() would need an async caller.
        let duration = CMTimeGetSeconds(asset.duration)
        guard duration.isFinite, duration > 0 else { return nil }
        let count = min(maxFrames, max(3, Int(duration / 3) + 1))
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: frameMaxSide, height: frameMaxSide)
        generator.requestedTimeToleranceBefore = .zero
        generator.requestedTimeToleranceAfter = .zero
        let frames: [UIImage] = (0..<count).compactMap { i in
            // Sample the middle of each slice, so we skip the shaky first and last moments.
            let seconds = duration * Double(2 * i + 1) / Double(2 * count)
            guard let cg = try? generator.copyCGImage(at: CMTime(seconds: seconds, preferredTimescale: 600), actualTime: nil) else { return nil }
            return UIImage(cgImage: cg)
        }
        return frames.isEmpty ? nil : CapturedVideo(file: file, frames: frames, durationSeconds: duration)
    }
}

/// The system camera, in photo or video mode (videos capped at 30 seconds).
struct CameraPicker: UIViewControllerRepresentable {
    let mode: CameraMode
    let onPhoto: (UIImage, [String: Any]?) -> Void
    let onVideo: (URL) -> Void
    let onCancel: () -> Void

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        if mode == .video {
            picker.mediaTypes = [UTType.movie.identifier]
            picker.cameraCaptureMode = .video
            picker.videoMaximumDuration = 30
            picker.videoQuality = .typeHigh
        } else {
            picker.mediaTypes = [UTType.image.identifier]
            picker.cameraCaptureMode = .photo
        }
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        private let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let url = info[.mediaURL] as? URL {
                // The picker deletes its file soon; keep our own copy.
                let copy = FileManager.default.temporaryDirectory.appendingPathComponent("video_\(Int(Date().timeIntervalSince1970 * 1000)).mp4")
                if (try? FileManager.default.copyItem(at: url, to: copy)) != nil {
                    parent.onVideo(copy)
                } else {
                    parent.onCancel()
                }
            } else if let image = info[.originalImage] as? UIImage {
                parent.onPhoto(image, info[.mediaMetadata] as? [String: Any])
            } else {
                parent.onCancel()
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCancel()
        }
    }
}

/// Full-screen review of a just-taken photo or video: the caller can add
/// an optional note ("bedroom"), retake it, or send it.
///
/// Also settles which way the camera was facing, so the agent can tell
/// which direction each thing is in: the heading saved in the photo if
/// there is one, otherwise the live compass (point the phone the way you
/// took it), and the caller can always tap a direction by hand. A video
/// shows one frame large with a strip of the others underneath.
struct CaptureReviewSheet: View {
    let frames: [UIImage]
    let exifHeading: Double?
    let isVideo: Bool
    let agentName: String
    let onRetake: () -> Void
    let onDismiss: () -> Void
    let onSend: (_ question: String, _ facing: PhotoFacing?) -> Void

    @StateObject private var heading = CameraHeading()
    @State private var question = ""
    @State private var shown = 0
    @State private var picked: CompassDirection?

    private var facing: PhotoFacing? {
        if let picked = picked { return PhotoFacing(degrees: picked.degrees, source: .caller) }
        if let exif = exifHeading { return PhotoFacing(degrees: exif, source: .photo) }
        if let live = heading.degrees { return PhotoFacing(degrees: live, source: .compass) }
        return nil
    }

    var body: some View {
        ZStack {
            CallTones.ink.opacity(0.97).ignoresSafeArea()
                .onTapGesture { hideKeyboard() } // swallow touches so nothing behind reacts

            VStack(spacing: 0) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Ask about this space").font(.vt(18, .semibold)).foregroundColor(CallTones.textPrimary)
                        Text("\(agentName) will suggest Vastu improvements").font(.vt(13, .regular)).foregroundColor(CallTones.textMuted)
                    }
                    Spacer()
                    Button(action: onDismiss) {
                        Circle().fill(CallTones.surface).frame(width: 38, height: 38)
                            .overlay(Image(systemName: "xmark").font(.system(size: 15, weight: .semibold)).foregroundColor(CallTones.textPrimary))
                    }
                    .accessibilityLabel(isVideo ? "Discard video" : "Discard photo")
                }
                .padding(.vertical, 14)

                Image(uiImage: frames[min(shown, frames.count - 1)])
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(CallTones.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .accessibilityLabel(isVideo ? "Video frame \(shown + 1)" : "Photo to send")

                if frames.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(frames.indices, id: \.self) { i in
                                Image(uiImage: frames[i]).resizable().scaledToFill()
                                    .frame(width: 52, height: 52)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(i == shown ? Color.vastuPrimary : .clear, lineWidth: 2))
                                    .onTapGesture { shown = i }
                                    .accessibilityLabel("Show frame \(i + 1)")
                            }
                        }
                    }
                    .padding(.top, 10)
                }

                facingPicker.padding(.top, 14)

                ZStack(alignment: .leading) {
                    if question.isEmpty {
                        Text("Add a note (optional) — e.g. “bedroom, door faces east”")
                            .font(.vt(14, .regular)).foregroundColor(CallTones.textMuted)
                    }
                    TextField("", text: $question, axis: .vertical)
                        .lineLimit(1...3)
                        .font(.vt(14, .regular))
                        .foregroundColor(CallTones.textPrimary)
                        .tint(.vastuPrimary)
                        .submitLabel(.send)
                        .onSubmit { onSend(question.trimmingCharacters(in: .whitespacesAndNewlines), facing) }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(RoundedRectangle(cornerRadius: 18).fill(CallTones.surface))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(CallTones.hairline, lineWidth: 1))
                .padding(.top, 14)

                GeometryReader { geo in
                    let unit = (geo.size.width - 12) / 2.6
                    HStack(spacing: 12) {
                        Button(action: onRetake) {
                            Text(isVideo ? "Record again" : "Retake").font(.vt(14, .semibold)).foregroundColor(CallTones.textPrimary)
                                .lineLimit(1)
                                .frame(width: unit, height: 48)
                                .background(Capsule().fill(CallTones.surface))
                                .overlay(Capsule().stroke(CallTones.hairline, lineWidth: 1))
                        }
                        Button { onSend(question.trimmingCharacters(in: .whitespacesAndNewlines), facing) } label: {
                            Text("Ask \(agentName)").font(.vt(14, .semibold)).foregroundColor(.white)
                                .lineLimit(1)
                                .frame(width: unit * 1.6, height: 48)
                                .background(Capsule().fill(Color.vastuPrimary))
                        }
                    }
                    .buttonStyle(.plain)
                }
                .frame(height: 48)
                .padding(.top, 14)
                .padding(.bottom, 16)
            }
            .padding(.horizontal, 20)
        }
        .onAppear { heading.start() }
        .onDisappear { heading.stop() }
    }

    /// "Camera facing East" (or "Started facing East" for a video) plus a row of the eight directions to correct it.
    private var facingPicker: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(facing.map { "\(isVideo ? "Started facing" : "Camera facing") \($0.direction.label) (\($0.direction.vastuName))" }
                 ?? (isVideo ? "Which way did the video start facing?" : "Which way was the camera facing?"))
                .font(.vt(14, .semibold)).foregroundColor(CallTones.textPrimary)
            Text(facingHint).font(.vt(12, .regular)).foregroundColor(CallTones.textMuted)
                .padding(.top, 2).padding(.bottom, 8)
            HStack(spacing: 6) {
                ForEach(CompassDirection.allCases, id: \.self) { direction in
                    let selected = facing?.direction == direction
                    let manual = selected && facing?.source == .caller
                    Button {
                        picked = picked == direction ? nil : direction // tap again to go back to automatic
                    } label: {
                        Text(direction.short).font(.vt(12, .semibold))
                            .foregroundColor(selected ? .white : CallTones.textPrimary)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(RoundedRectangle(cornerRadius: 12).fill(
                                manual ? Color.vastuPrimary : selected ? Color.vastuPrimary.opacity(0.35) : CallTones.surface))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(CallTones.hairline, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var facingHint: String {
        switch facing?.source {
        case .photo: return "From the compass reading saved in the photo · tap to change"
        case .compass:
            return isVideo ? "Live compass — point your phone where you started recording, or tap a direction"
                : "Live compass — point your phone the way you took the photo, or tap a direction"
        case .caller: return "Set by you · tap it again to use the compass"
        case nil: return "No compass on this phone — tap a direction"
        }
    }
}
