import AVFoundation
import CoreImage
import SwiftUI

/// The back camera, live: shown by CameraViewfinder, and able to take a
/// full photo or grab what the preview shows right now. Needs camera
/// permission to have been granted already. Stays not-ready on devices
/// without a camera (the simulator).
final class ViewfinderModel: NSObject, ObservableObject {
    /// True once the camera is up and a photo can be taken.
    @Published private(set) var isReady = false

    let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "ViewfinderModel.session")
    private let frameQueue = DispatchQueue(label: "ViewfinderModel.frames")
    private let photoOutput = AVCapturePhotoOutput()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let ciContext = CIContext()
    private var configured = false
    /// The newest preview frame (only touched on frameQueue).
    private var latestFrame: CVPixelBuffer?
    private var photoContinuation: CheckedContinuation<UIImage?, Never>?

    func start() {
        sessionQueue.async {
            if !self.configured { self.configure() }
            guard self.configured, !self.session.isRunning else { return }
            self.session.startRunning()
            DispatchQueue.main.async { self.isReady = self.session.isRunning }
        }
    }

    func stop() {
        sessionQueue.async {
            if self.session.isRunning { self.session.stopRunning() }
            DispatchQueue.main.async { self.isReady = false }
        }
    }

    private func configure() {
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: camera) else { return }
        session.beginConfiguration()
        session.sessionPreset = .photo
        guard session.canAddInput(input), session.canAddOutput(photoOutput), session.canAddOutput(videoOutput) else {
            session.commitConfiguration()
            return
        }
        session.addInput(input)
        session.addOutput(photoOutput)
        videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(self, queue: frameQueue)
        session.addOutput(videoOutput)
        for output in [photoOutput as AVCaptureOutput, videoOutput] {
            if let connection = output.connection(with: .video), connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
        }
        session.commitConfiguration()
        configured = true
    }

    /// What the preview shows right now — instant and silent, unlike
    /// takePhoto(), at video resolution. Nil until the first frame.
    func snapshot(maxSide: CGFloat = 1024) -> UIImage? {
        frameQueue.sync {
            guard let frame = latestFrame,
                  let cg = ciContext.createCGImage(CIImage(cvPixelBuffer: frame), from: CIImage(cvPixelBuffer: frame).extent) else { return nil }
            return UIImage(cgImage: cg).scaledDown(toMaxSide: maxSide)
        }
    }

    /// Takes a full photo, scaled to at most `maxSide` px. Nil if the camera isn't ready or the capture failed.
    func takePhoto(maxSide: CGFloat = 1600) async -> UIImage? {
        guard isReady else { return nil }
        let photo: UIImage? = await withCheckedContinuation { continuation in
            sessionQueue.async {
                guard self.photoContinuation == nil else { return continuation.resume(returning: nil) }
                self.photoContinuation = continuation
                self.photoOutput.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
            }
        }
        return photo?.scaledDown(toMaxSide: maxSide)
    }
}

extension ViewfinderModel: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        latestFrame = CMSampleBufferGetImageBuffer(sampleBuffer)
    }
}

extension ViewfinderModel: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let image = error == nil ? photo.fileDataRepresentation().flatMap(UIImage.init(data:)) : nil
        if let error = error { print("ViewfinderModel: capture failed: \(error)") }
        sessionQueue.async {
            self.photoContinuation?.resume(returning: image)
            self.photoContinuation = nil
        }
    }
}

/// Live preview from the back camera, filling its frame. Starts the camera while on screen.
struct CameraViewfinder: View {
    @ObservedObject var model: ViewfinderModel

    var body: some View {
        PreviewLayerView(session: model.session)
            .background(Color.black)
            .onAppear { model.start() }
            .onDisappear { model.stop() }
    }
}

private struct PreviewLayerView: UIViewRepresentable {
    let session: AVCaptureSession

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {}
}
