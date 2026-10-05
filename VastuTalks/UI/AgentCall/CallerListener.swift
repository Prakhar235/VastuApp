import AVFoundation

private let sampleRate = 16_000.0
private let frameSamples = 480 // 30 ms
private let startFrames = 3 // ~90 ms of loud audio = caller started talking
private let endSilenceFrames = 37 // ~1.1 s of quiet = caller finished
private let preRollFrames = 10 // keep ~300 ms before speech so the first word isn't clipped
private let minSpeechFrames = 12 // ignore blips shorter than ~360 ms
private let maxSpeechFrames = 1000 // cap one utterance at ~30 s

/// The AI agent's ears. After start() it keeps the mic open and waits —
/// for as long as it takes — for the caller to start talking, then
/// records until they pause and hands the utterance over as a 16 kHz
/// mono WAV (for VastuAgent.transcribe), and stops.
///
/// Same adaptive-noise-floor voice detector as the Android app.
final class CallerListener {
    /// Mic is open and waiting for speech.
    private(set) var isListening = false
    /// Caller is talking right now.
    private(set) var isHearingSpeech = false

    var onStateChanged: (() -> Void)?
    var onUtterance: ((Data) -> Void)?

    private let engine = AVAudioEngine()
    private let queue = DispatchQueue(label: "CallerListener")
    private var generation = 0 // main thread: bumps on every start/stop

    // Detector state — only touched on `queue`.
    private var queueGeneration = 0
    private var pendingSamples: [Int16] = []
    private var preRoll: [[Int16]] = []
    private var speech: [[Int16]] = []
    private var noiseFloor = 150.0
    private var loudRun = 0
    private var quietRun = 0
    private var inSpeech = false

    func start() {
        guard !isListening else { return }
        let input = engine.inputNode
        let inFormat = input.outputFormat(forBus: 0)
        guard inFormat.sampleRate > 0,
              let outFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: sampleRate, channels: 1, interleaved: true),
              let converter = AVAudioConverter(from: inFormat, to: outFormat) else { return }

        generation += 1
        let gen = generation
        queue.async { self.reset(gen) }

        input.installTap(onBus: 0, bufferSize: 4096, format: inFormat) { [weak self] buffer, _ in
            guard let self = self, let samples = Self.convert(buffer, with: converter, to: outFormat) else { return }
            self.queue.async { self.process(samples, gen) }
        }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            input.removeTap(onBus: 0)
            print("CallerListener: couldn't start the mic: \(error)")
            return
        }
        isListening = true
        onStateChanged?()
    }

    func stop() {
        guard isListening else { return }
        generation += 1
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        isListening = false
        isHearingSpeech = false
        onStateChanged?()
    }

    private static func convert(_ buffer: AVAudioPCMBuffer, with converter: AVAudioConverter, to format: AVAudioFormat) -> [Int16]? {
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * sampleRate / buffer.format.sampleRate + 64)
        guard let out = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: capacity) else { return nil }
        var fed = false
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            if fed {
                status.pointee = .noDataNow
                return nil
            }
            fed = true
            status.pointee = .haveData
            return buffer
        }
        guard error == nil, let channel = out.int16ChannelData else { return nil }
        return Array(UnsafeBufferPointer(start: channel[0], count: Int(out.frameLength)))
    }

    private func reset(_ gen: Int) {
        queueGeneration = gen
        pendingSamples.removeAll()
        preRoll.removeAll()
        speech.removeAll()
        noiseFloor = 150
        loudRun = 0
        quietRun = 0
        inSpeech = false
    }

    private func process(_ samples: [Int16], _ gen: Int) {
        guard gen == queueGeneration else { return }
        pendingSamples += samples
        while pendingSamples.count >= frameSamples {
            let chunk = Array(pendingSamples.prefix(frameSamples))
            pendingSamples.removeFirst(frameSamples)
            if handleFrame(chunk, gen) { return }
        }
    }

    /// Returns true once an utterance has been handed over.
    private func handleFrame(_ chunk: [Int16], _ gen: Int) -> Bool {
        let level = rms(chunk)
        if !inSpeech {
            preRoll.append(chunk)
            if preRoll.count > preRollFrames { preRoll.removeFirst() }
            loudRun = level > max(noiseFloor * 3, 400) ? loudRun + 1 : 0
            if loudRun >= startFrames {
                inSpeech = true
                quietRun = 0
                speech += preRoll
                preRoll.removeAll()
                setHearing(true, gen)
            } else {
                // Track background noise only while nobody is talking.
                noiseFloor = min(3000, max(50, noiseFloor * 0.97 + level * 0.03))
            }
        } else {
            speech.append(chunk)
            quietRun = level < max(noiseFloor * 2, 300) ? quietRun + 1 : 0
            if quietRun >= endSilenceFrames || speech.count >= maxSpeechFrames {
                if speech.count - quietRun >= minSpeechFrames {
                    let wav = Self.toWav(speech)
                    queueGeneration = -1 // ignore anything still in flight
                    DispatchQueue.main.async {
                        guard gen == self.generation else { return }
                        self.stop()
                        self.onUtterance?(wav)
                    }
                    return true
                }
                // Too short to be a real sentence (a cough, a tap) — keep waiting.
                speech.removeAll()
                inSpeech = false
                loudRun = 0
                setHearing(false, gen)
            }
        }
        return false
    }

    private func setHearing(_ hearing: Bool, _ gen: Int) {
        DispatchQueue.main.async {
            guard gen == self.generation, self.isHearingSpeech != hearing else { return }
            self.isHearingSpeech = hearing
            self.onStateChanged?()
        }
    }

    private func rms(_ samples: [Int16]) -> Double {
        var sum = 0.0
        for s in samples { sum += Double(s) * Double(s) }
        return (sum / Double(max(samples.count, 1))).squareRoot()
    }

    private static func toWav(_ frames: [[Int16]]) -> Data {
        let sampleCount = frames.reduce(0) { $0 + $1.count }
        let dataBytes = sampleCount * 2
        var data = Data(capacity: 44 + dataBytes)
        func append<T: FixedWidthInteger>(_ value: T) { withUnsafeBytes(of: value.littleEndian) { data.append(contentsOf: $0) } }
        data.append(contentsOf: Array("RIFF".utf8)); append(UInt32(36 + dataBytes)); data.append(contentsOf: Array("WAVE".utf8))
        data.append(contentsOf: Array("fmt ".utf8)); append(UInt32(16)); append(UInt16(1)); append(UInt16(1))
        append(UInt32(sampleRate)); append(UInt32(sampleRate * 2)); append(UInt16(2)); append(UInt16(16))
        data.append(contentsOf: Array("data".utf8)); append(UInt32(dataBytes))
        for frame in frames { for sample in frame { append(sample) } }
        return data
    }
}
