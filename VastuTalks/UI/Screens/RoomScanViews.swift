import AVFoundation
import SwiftUI

/// Colours of the scan screens, beyond CallTones.
enum ScanTones {
    static let major = Color(hex: 0xFF8A4C)
    static let minor = Color(hex: 0xF5C451)
    static let good = Color(hex: 0x57C7A0)
    static let goodText = Color(hex: 0x7FE0BC)
    static let track = Color(hex: 0x2E2A42)
    static let body = Color(hex: 0xC9C3DE)
    static let scrim = Color(hex: 0x12101C, alpha: 0.82)
}

extension Severity {
    var color: Color {
        switch self {
        case .major: return ScanTones.major
        case .minor: return ScanTones.minor
        case .good: return ScanTones.good
        }
    }
}

/// The guided room scan: one photo of each wall (North, East, South, West,
/// in that order; any can be skipped), then Ananya's read of the whole
/// room — a score, each thing she found marked on its photo, and for
/// things that should move, where to (see RoomResultView).
@MainActor
final class RoomScanViewModel: ObservableObject {
    enum Stage { case capture, analyzing, result, map, failed }

    static let wallOrder: [CompassDirection] = [.n, .e, .s, .w]

    @Published private(set) var stage = Stage.capture
    /// Index into wallOrder of the wall being photographed.
    @Published private(set) var step = 0
    @Published private(set) var photos: [CompassDirection: WallPhoto] = [:]
    @Published private(set) var skipped: Set<CompassDirection> = []
    @Published private(set) var analysis: RoomAnalysis?
    /// The finding shown on the map.
    @Published private(set) var mapFinding: RoomFinding?

    private let agent = VastuAgent()
    private var analyzeTask: Task<Void, Never>?
    /// The current analysis is already in the chat.
    private var sentToChat = false
    /// Photos of a saved report being viewed again (they aren't in `photos`).
    private var savedWalls: [WallPhoto]?

    var currentWall: CompassDirection { Self.wallOrder[step] }

    /// The photos sent for analysis, in wall order; findings refer to them by index.
    var walls: [WallPhoto] { savedWalls ?? Self.wallOrder.compactMap { photos[$0] } }

    /// Shows a report saved with the chat, as it was.
    func showSaved(_ scan: RoomScanHandoff.Scan) {
        savedWalls = scan.walls
        analysis = scan.analysis
        sentToChat = true
        stage = .result
    }

    func goToWall(_ direction: CompassDirection) {
        step = Self.wallOrder.firstIndex(of: direction) ?? 0
    }

    func onPhoto(_ photo: WallPhoto) {
        photos[currentWall] = photo
        skipped.remove(currentWall)
        advance()
    }

    func skip() {
        if photos[currentWall] == nil { skipped.insert(currentWall) }
        advance()
    }

    /// Next wall still to do, or the analysis once every wall is photographed or skipped.
    private func advance() {
        let count = Self.wallOrder.count
        let next = (1...count).map { (step + $0) % count }
            .first { photos[Self.wallOrder[$0]] == nil && !skipped.contains(Self.wallOrder[$0]) }
        if let next = next {
            step = next
        } else if !photos.isEmpty {
            analyze()
        }
    }

    func analyze() {
        guard !photos.isEmpty else { return }
        stage = .analyzing
        analyzeTask?.cancel()
        let walls = self.walls
        analyzeTask = Task {
            let result = await agent.analyzeRoom(walls)
            guard !Task.isCancelled else { return }
            analysis = result
            sentToChat = false
            // Straight into the chat (and saved there), whether or not they ask about it.
            _ = handOffToChat()
            stage = result != nil ? .result : .failed
        }
    }

    /// Back to taking photos, keeping the ones already taken.
    func backToCapture() {
        analyzeTask?.cancel()
        stage = .capture
    }

    func showOnMap(_ finding: RoomFinding) {
        mapFinding = finding
        stage = .map
    }

    func closeMap() { stage = .result }

    /// Starts over with no photos, e.g. after moving things around.
    func rescan() {
        analyzeTask?.cancel()
        photos = [:]
        skipped = []
        savedWalls = nil
        analysis = nil
        mapFinding = nil
        step = 0
        stage = .capture
    }

    /// Sends the scan to the chat with Ananya (once); false if there's nothing to send.
    func handOffToChat() -> Bool {
        guard let result = analysis else { return false }
        if !sentToChat { RoomScanHandoff.shared.send(RoomScanHandoff.Scan(walls: walls, analysis: result)) }
        sentToChat = true
        return true
    }
}

/// The guided room scan: photograph each wall with the live compass showing
/// which way the camera faces, then see Ananya's read of the room and, for
/// anything that should move, where to.
struct RoomScanView: View {
    let onClose: () -> Void
    let onAskAgent: () -> Void
    @ObservedObject var vm: RoomScanViewModel
    @ObservedObject private var language = AgentLanguagePref.shared

    var body: some View {
        ZStack {
            CallTones.ink.ignoresSafeArea()
            switch vm.stage {
            case .capture:
                CaptureStage(vm: vm, onClose: onClose)
            case .analyzing, .failed:
                AnalyzingStage(vm: vm)
            case .result:
                RoomResultView(vm: vm, onBack: onClose, onAskAgent: { if vm.handOffToChat() { onAskAgent() } })
            case .map:
                RoomMapView(vm: vm)
            }
        }
        .darkScreenStatusBar()
    }
}

/// A fresh scan, owning its view model.
struct NewRoomScanView: View {
    let onClose: () -> Void
    let onAskAgent: () -> Void
    @StateObject private var vm = RoomScanViewModel()

    var body: some View {
        RoomScanView(onClose: onClose, onAskAgent: onAskAgent, vm: vm)
    }
}

/// A room report saved with the chat with Ananya, opened again from its
/// message. Same screens as a fresh scan; "Scan again" starts a new one.
struct RoomReportView: View {
    let messageId: Int
    let onClose: () -> Void
    @StateObject private var vm = RoomScanViewModel()
    @State private var loaded = false
    @State private var failed = false

    var body: some View {
        Group {
            if loaded {
                RoomScanView(onClose: onClose, onAskAgent: onClose, vm: vm)
            } else {
                CallTones.ink.ignoresSafeArea()
            }
        }
        .task {
            guard !loaded else { return }
            let id = messageId
            if let scan = await Task.detached(operation: { AgentChatStore().loadReport(messageId: id) }).value {
                vm.showSaved(scan)
                loaded = true
            } else {
                failed = true
            }
        }
        .alert(tr("Couldn't open this report.", "यह रिपोर्ट नहीं खुल सकी।"), isPresented: $failed) {
            Button("OK", action: onClose)
        }
    }
}

// MARK: - Capture

private struct CaptureStage: View {
    @ObservedObject var vm: RoomScanViewModel
    let onClose: () -> Void

    @StateObject private var viewfinder = ViewfinderModel()
    @StateObject private var heading = CameraHeading()
    @State private var hasCamera = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
    @State private var capturing = false
    @State private var picking = false
    @State private var toast: String?

    /// Within this many degrees of the wall's direction counts as facing it.
    private let onTarget = 20.0

    var body: some View {
        let wall = vm.currentWall
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                RoundIconButton(icon: "chevron.left", label: tr("Back", "वापस"), action: onClose)
                VStack(alignment: .leading, spacing: 2) {
                    Text(tr("Room scan", "कमरा स्कैन")).font(.vt(18)).foregroundColor(CallTones.textPrimary)
                    Text(tr("Wall \(vm.step + 1) / 4 · one photo of each wall", "दीवार \(vm.step + 1) / 4 · हर दीवार की एक फ़ोटो"))
                        .font(.vt(13, .medium)).foregroundColor(CallTones.textMuted)
                }
                Spacer()
                LanguageSwitch()
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 8)

            WallProgress(vm: vm)

            ZStack {
                RoundedRectangle(cornerRadius: 24).fill(CallTones.surface)
                if hasCamera {
                    CameraViewfinder(model: viewfinder)
                } else {
                    VStack(spacing: 16) {
                        Text(tr("Allow the camera to photograph each wall with its compass direction.",
                                "हर दीवार की फ़ोटो दिशा के साथ लेने के लिए कैमरा चालू करें।"))
                            .font(.vt(15, .medium)).foregroundColor(CallTones.textPrimary).multilineTextAlignment(.center)
                        PillButton(label: tr("Allow camera", "कैमरा चालू करें")) {
                            AVCaptureDevice.requestAccess(for: .video) { granted in
                                DispatchQueue.main.async {
                                    hasCamera = granted
                                    if !granted { toast = tr("Turn on camera access for Vastu Talks in Settings.", "सेटिंग्स में Vastu Talks के लिए कैमरा चालू करें।") }
                                }
                            }
                        }
                    }
                    .padding(24)
                }

                CompassDial(heading: heading.degrees)

                VStack {
                    HStack {
                        aimPill(wall)
                        Spacer()
                        if heading.degrees != nil {
                            OverlayPill(text: heading.isUpright ? tr("Phone upright", "फ़ोन सीधा") : tr("Hold phone upright", "फ़ोन सीधा रखें"),
                                        color: heading.isUpright ? CallTones.textPrimary : ScanTones.minor, showCheck: heading.isUpright)
                        }
                    }
                    Spacer()
                    Text(tr("Stand facing the \(wall.localName) wall and fit the whole wall in the frame",
                            "\(wall.localName) वाली दीवार के सामने खड़े होकर पूरी दीवार फ़्रेम में लें"))
                        .font(.vt(14, .medium)).foregroundColor(CallTones.textPrimary).lineSpacing(3)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 14).fill(ScanTones.scrim))
                }
                .padding(14)

                if capturing {
                    ProgressView().tint(.white).scaleEffect(1.8)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .padding(.horizontal, 16)

            if !vm.photos.isEmpty {
                Button { vm.analyze() } label: {
                    Text(tr("Analyse now with \(vm.photos.count) of 4 walls", "\(vm.photos.count) / 4 दीवारों के साथ अभी विश्लेषण करें"))
                        .font(.vt(14, .semibold)).foregroundColor(Color(hex: 0xB9A6FF))
                        .padding(.horizontal, 12).padding(.vertical, 8)
                }
                .padding(.top, 10)
            }

            HStack {
                SquareButton(action: { picking = true }) {
                    Image(systemName: "photo.on.rectangle").font(.system(size: 20)).foregroundColor(CallTones.textPrimary)
                }
                .accessibilityLabel(tr("Pick from gallery", "गैलरी से चुनें"))
                Spacer()
                let canShoot = hasCamera && viewfinder.isReady && !capturing
                Button(action: shoot) {
                    Circle().fill(hasCamera && viewfinder.isReady ? Color.vastuPrimary : ScanTones.track)
                        .frame(width: 64, height: 64)
                        .padding(6)
                        .overlay(Circle().stroke(CallTones.textPrimary, lineWidth: 4))
                }
                .disabled(!canShoot)
                .accessibilityLabel(tr("Take photo", "फ़ोटो लें"))
                Spacer()
                SquareButton(action: { vm.skip() }) {
                    Text(tr("Skip", "छोड़ें")).font(.vt(13, .medium)).foregroundColor(CallTones.textMuted)
                }
            }
            .padding(.horizontal, 36)
            .padding(.vertical, 16)
        }
        .toast($toast)
        .onAppear { heading.start() }
        .onDisappear { heading.stop() }
        .sheet(isPresented: $picking) {
            PhotoLibraryPicker { photo in
                picking = false
                guard let photo = photo else { return }
                // A gallery photo is taken to show the wall being asked for, unless it saved its own heading.
                let facing = photo.exifHeading.map { PhotoFacing(degrees: $0, source: .photo) }
                    ?? PhotoFacing(degrees: vm.currentWall.degrees, source: .caller)
                vm.onPhoto(WallPhoto(image: photo.image, facing: facing))
            }
            .ignoresSafeArea()
        }
    }

    private func aimPill(_ wall: CompassDirection) -> some View {
        guard let heading = heading.degrees else {
            return OverlayPill(text: tr("No compass — face the \(wall.localName) wall", "कम्पास नहीं — \(wall.localName) की दीवार की ओर मुड़ें"),
                               color: ScanTones.minor, showCheck: false)
        }
        // How far the camera is clockwise of the wall, from -180 to 180 (positive = turned too far right).
        let off = (heading - wall.degrees + 540).truncatingRemainder(dividingBy: 360) - 180
        if abs(off) <= onTarget {
            return OverlayPill(text: tr("Compass on target", "कम्पास सटीक"), color: ScanTones.goodText, showCheck: true)
        }
        let degrees = Int(abs(off).rounded())
        return OverlayPill(text: off > 0 ? tr("Turn left \(degrees)°", "बाएँ मुड़ें \(degrees)°") : tr("Turn right \(degrees)°", "दाएँ मुड़ें \(degrees)°"),
                           color: ScanTones.minor, showCheck: false)
    }

    private func shoot() {
        guard !capturing, viewfinder.isReady else { return }
        capturing = true
        let at = heading.degrees
        Task {
            let image = await viewfinder.takePhoto()
            capturing = false
            guard let image = image else {
                toast = tr("Couldn't take the photo — try again.", "फ़ोटो नहीं खिंची — फिर से कोशिश करें।")
                return
            }
            let facing = at.map { PhotoFacing(degrees: $0, source: .compass) }
                ?? PhotoFacing(degrees: vm.currentWall.degrees, source: .caller)
            vm.onPhoto(WallPhoto(image: image, facing: facing))
        }
    }
}

/// The four walls, done / current / to do; tap one to (re)take it.
private struct WallProgress: View {
    @ObservedObject var vm: RoomScanViewModel

    var body: some View {
        HStack(spacing: 8) {
            ForEach(RoomScanViewModel.wallOrder, id: \.self) { direction in
                let done = vm.photos[direction] != nil
                let current = direction == vm.currentWall
                let skipped = vm.skipped.contains(direction)
                Button { vm.goToWall(direction) } label: {
                    VStack(spacing: 6) {
                        Capsule().fill(current ? Color.vastuPrimary : done ? ScanTones.good : ScanTones.track).frame(height: 4)
                        Text(direction.localName + (done && !current ? " ✓" : skipped && !current ? " –" : ""))
                            .font(.vt(12, current || done ? .bold : .regular))
                            .foregroundColor(current ? CallTones.textPrimary : done ? ScanTones.good : CallTones.textMuted)
                            .lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .padding(.vertical, 4)
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

/// Big round readout of where the camera points: degrees and direction name.
private struct CompassDial: View {
    let heading: Double?

    var body: some View {
        VStack(spacing: 2) {
            Image(systemName: "safari").font(.system(size: 24)).foregroundColor(.white)
            Text(heading.map { "\(Int($0.rounded()) % 360)°" } ?? "–").font(.system(size: 34, weight: .heavy)).foregroundColor(.white)
            Text(heading.map { CompassDirection.of(degrees: $0).localName } ?? tr("No compass", "कम्पास नहीं"))
                .font(.vt(15)).foregroundColor(.white)
        }
        .frame(width: 150, height: 150)
        .background(Circle().fill(Color(hex: 0x12101C, alpha: 0.55)))
        .overlay(Circle().stroke(Color.white.opacity(0.85), lineWidth: 2))
    }
}

private struct AnalyzingStage: View {
    @ObservedObject var vm: RoomScanViewModel

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                ForEach(Array(vm.walls.enumerated()), id: \.offset) { _, wall in
                    Image(uiImage: wall.image).resizable().scaledToFill()
                        .frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            if vm.stage == .failed {
                Text(tr("Couldn't analyse the room right now.", "अभी कमरे का विश्लेषण नहीं हो पाया।"))
                    .font(.vt(17)).foregroundColor(CallTones.textPrimary).multilineTextAlignment(.center).padding(.top, 24)
                Text(tr("Check your internet connection and try again.", "इंटरनेट कनेक्शन देखें और फिर से कोशिश करें।"))
                    .font(.vt(14, .medium)).foregroundColor(CallTones.textMuted).multilineTextAlignment(.center).padding(.top, 6)
                HStack(spacing: 12) {
                    PillButton(label: tr("Back to photos", "फ़ोटो पर वापस"), outlined: true) { vm.backToCapture() }
                    PillButton(label: tr("Try again", "फिर कोशिश करें")) { vm.analyze() }
                }
                .padding(.top, 20)
            } else {
                ProgressView().tint(.vastuPrimary).scaleEffect(1.4).padding(.top, 28)
                Text(tr("\(AnanyaAgent.firstName) is reading your room…", "\(AnanyaAgent.firstName) आपके कमरे को देख रही हैं…"))
                    .font(.vt(16, .semibold)).foregroundColor(CallTones.textPrimary).padding(.top, 16)
                Text(tr("Finding each object, its direction, and what to fix", "हर चीज़, उसकी दिशा और क्या ठीक करना है — देखा जा रहा है"))
                    .font(.vt(13, .medium)).foregroundColor(CallTones.textMuted).multilineTextAlignment(.center).padding(.top, 4)
            }
        }
        .padding(24)
    }
}

// MARK: - Result

/// Ananya's read of the scanned room: the score, each wall's photo with her
/// findings numbered on it, and the list of what to fix, most important
/// first. Tapping a finding spotlights it on its photo.
private struct RoomResultView: View {
    @ObservedObject var vm: RoomScanViewModel
    let onBack: () -> Void
    let onAskAgent: () -> Void

    @State private var shownPhoto = 0
    @State private var selected: Int?
    @State private var didSetInitialPhoto = false

    var body: some View {
        if let analysis = vm.analysis {
            let walls = vm.walls
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(spacing: 12) {
                            RoundIconButton(icon: "chevron.left", label: tr("Back", "वापस"), action: onBack)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(analysis.room) · \(tr("analysis", "विश्लेषण"))").font(.vt(18)).foregroundColor(CallTones.textPrimary)
                                Text(tr("\(walls.count) walls · directions from the compass", "\(walls.count) दीवारें · कम्पास से मापी दिशा"))
                                    .font(.vt(13, .medium)).foregroundColor(CallTones.textMuted)
                            }
                        }

                        ScoreCard(score: analysis.score, verdict: analysis.verdict, summary: analysis.summary)

                        if walls.count > 1 {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(Array(walls.enumerated()), id: \.offset) { i, wall in
                                        ChoiceChip(label: wall.facing.direction.localName, chosen: i == shownPhoto) {
                                            shownPhoto = i
                                            selected = nil
                                        }
                                    }
                                }
                            }
                        }

                        if walls.indices.contains(shownPhoto) {
                            MarkedPhoto(wall: walls[shownPhoto], findings: analysis.findings.filter { $0.photo == shownPhoto },
                                        selected: selected, onSelect: { selected = selected == $0 ? nil : $0 })
                                .id("photo")
                        }

                        if !analysis.findings.isEmpty {
                            HStack(alignment: .lastTextBaseline) {
                                Text(tr("What to fix", "क्या ठीक करें")).font(.vt(17)).foregroundColor(CallTones.textPrimary)
                                Spacer()
                                Text(tr("Most important first", "ज़रूरी पहले")).font(.vt(13, .medium)).foregroundColor(CallTones.textMuted)
                            }
                        }
                        ForEach(analysis.findings) { finding in
                            FindingCard(finding: finding, isSelected: selected == finding.number,
                                        onTap: {
                                            selected = selected == finding.number ? nil : finding.number
                                            shownPhoto = finding.photo
                                            withAnimation { proxy.scrollTo("photo", anchor: .top) }
                                        },
                                        onShowMap: { vm.showOnMap(finding) })
                        }

                        Text(tr("Directions come from the phone's compass (about ±10°). If one looks wrong, scan again holding the phone away from metal and electronics.",
                                "दिशा फ़ोन के कम्पास से ली गई है (सटीकता लगभग ±10°)। गलत लगे तो फ़ोन को धातु और इलेक्ट्रॉनिक्स से दूर रखकर फिर से स्कैन करें।"))
                            .font(.vt(12, .medium)).foregroundColor(Color(hex: 0x8D87A6)).lineSpacing(4)

                        PillButton(label: tr("Ask \(AnanyaAgent.firstName) about this room", "\(AnanyaAgent.firstName) से इस कमरे के बारे में पूछें"),
                                   outlined: true, fullWidth: true, action: onAskAgent)
                        PillButton(label: tr("Scan again", "फिर से स्कैन करें"), fullWidth: true) { vm.rescan() }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 28)
                }
            }
            .onAppear {
                guard !didSetInitialPhoto else { return }
                didSetInitialPhoto = true
                shownPhoto = analysis.findings.first?.photo ?? 0
            }
        }
    }
}

private struct ScoreCard: View {
    let score: Int
    let verdict: String
    let summary: String

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle().stroke(ScanTones.track, lineWidth: 8)
                Circle().trim(from: 0, to: CGFloat(score) / 100)
                    .stroke(Color.vastuPrimary, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                VStack(spacing: 0) {
                    Text("\(score)").font(.system(size: 22, weight: .heavy)).foregroundColor(CallTones.textPrimary)
                    Text("/ 100").font(.vt(11, .medium)).foregroundColor(CallTones.textMuted)
                }
            }
            .frame(width: 72, height: 72)
            .padding(2)
            VStack(alignment: .leading, spacing: 4) {
                Text(tr("Vastu score", "वास्तु स्कोर") + (verdict.isEmpty ? "" : " · \(verdict)"))
                    .font(.vt(16)).foregroundColor(CallTones.textPrimary)
                if !summary.isEmpty {
                    Text(summary).font(.vt(14, .medium)).foregroundColor(ScanTones.body).lineSpacing(3)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(CallTones.surface))
    }
}

/// The wall photo at its own shape, with each finding outlined in its
/// severity colour and numbered. With a selected finding, the others fade.
private struct MarkedPhoto: View {
    let wall: WallPhoto
    let findings: [RoomFinding]
    let selected: Int?
    let onSelect: (Int) -> Void

    var body: some View {
        let ratio = wall.image.size.width / max(wall.image.size.height, 1)
        ZStack {
            RoundedRectangle(cornerRadius: 20).fill(CallTones.surface)
            GeometryReader { geo in
                let size = geo.size
                ZStack(alignment: .topLeading) {
                    Image(uiImage: wall.image).resizable().frame(width: size.width, height: size.height)
                    Canvas { ctx, canvasSize in
                        for f in findings {
                            let alpha = selected == nil || selected == f.number ? 1.0 : 0.3
                            let width: CGFloat = selected == f.number ? 4 : 2.5
                            switch f.mark {
                            case let .rect(x, y, w, h, _, _)?:
                                let rect = CGRect(x: x / 100 * canvasSize.width, y: y / 100 * canvasSize.height,
                                                  width: w / 100 * canvasSize.width, height: h / 100 * canvasSize.height)
                                ctx.stroke(Path(roundedRect: rect, cornerRadius: 6), with: .color(f.severity.color.opacity(alpha)), lineWidth: width)
                            case let .circle(x, y, r, _, _)?:
                                let radius = max(r / 100 * canvasSize.width, 14)
                                let center = CGPoint(x: x / 100 * canvasSize.width, y: y / 100 * canvasSize.height)
                                ctx.stroke(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                                           with: .color(f.severity.color.opacity(alpha)), lineWidth: width)
                            default:
                                break
                            }
                        }
                    }
                    ForEach(findings) { f in
                        if let center = badgeCenter(f, ratio: ratio) {
                            let alpha = selected == nil || selected == f.number ? 1.0 : 0.4
                            Button { onSelect(f.number) } label: {
                                Text("\(f.number)").font(.system(size: 15, weight: .heavy)).foregroundColor(Color(hex: 0x1A0E06))
                                    .frame(width: 30, height: 30)
                                    .background(Circle().fill(f.severity.color.opacity(alpha)))
                                    .overlay(Circle().stroke(Color.white.opacity(alpha), lineWidth: 2))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(f.title)
                            .position(x: center.x / 100 * size.width, y: center.y / 100 * size.height)
                        }
                    }
                    HStack(spacing: 6) {
                        Image(systemName: "safari").font(.system(size: 13)).foregroundColor(.white)
                        Text(tr("This wall: ", "यह दीवार: ") + "\(wall.facing.direction.localName) · \(Int(wall.facing.degrees.rounded()) % 360)°")
                            .font(.vt(13)).foregroundColor(.white)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 14).fill(ScanTones.scrim))
                    .padding(12)
                }
            }
            .aspectRatio(ratio, contentMode: .fit)
        }
        .frame(maxWidth: .infinity, maxHeight: 460)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    /// Where a finding's number sits, in percent of the photo.
    private func badgeCenter(_ f: RoomFinding, ratio: CGFloat) -> CGPoint? {
        switch f.mark {
        case let .rect(x, y, w, h, _, _)?: return CGPoint(x: x + w / 2, y: y + h / 2)
        case let .circle(x, y, r, _, _)?: return CGPoint(x: x + r * 0.7, y: y + r * 0.7 * Double(ratio))
        default: return nil
        }
    }
}

private struct FindingCard: View {
    let finding: RoomFinding
    let isSelected: Bool
    let onTap: () -> Void
    let onShowMap: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                NumberBadge(finding: finding)
                VStack(alignment: .leading, spacing: 2) {
                    Text(finding.title).font(.vt(16)).foregroundColor(CallTones.textPrimary)
                    if finding.severity == .good {
                        Text(tr("Well placed · no change needed", "सही जगह · कोई बदलाव नहीं")).font(.vt(13, .medium)).foregroundColor(CallTones.textMuted)
                    }
                }
                Spacer(minLength: 4)
                if finding.severity == .good {
                    Image(systemName: "checkmark").font(.system(size: 17, weight: .bold)).foregroundColor(ScanTones.good)
                } else {
                    Text(finding.severity == .major ? tr("Major", "बड़ी") : tr("Minor", "छोटी"))
                        .font(.vt(12)).foregroundColor(finding.severity.color)
                        .padding(.horizontal, 10).padding(.vertical, 2)
                        .background(RoundedRectangle(cornerRadius: 10).fill(finding.severity.color.opacity(0.16)))
                }
            }
            if !finding.why.isEmpty && (finding.severity != .good || isSelected) {
                Text(finding.why).font(.vt(14, .medium)).foregroundColor(ScanTones.body).lineSpacing(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if finding.severity == .major && !finding.remedies.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(tr("Fixes without moving it", "बिना शिफ्ट किए उपाय")).font(.vt(13)).foregroundColor(CallTones.textMuted)
                    ForEach(finding.remedies, id: \.self) {
                        Text("• \($0)").font(.vt(14, .medium)).foregroundColor(CallTones.textPrimary).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 12).fill(CallTones.ink))
            }
            if finding.betterZone != nil {
                Button(action: onShowMap) {
                    Text(tr("See the new spot on the map", "नक्शे पर नई जगह देखें")).font(.vt(14)).foregroundColor(.white)
                        .frame(maxWidth: .infinity).frame(height: 44)
                        .background(RoundedRectangle(cornerRadius: 12).fill(finding.severity == .major ? Color.vastuPrimary : .clear))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.vastuPrimary, lineWidth: finding.severity == .major ? 0 : 1))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 18).fill(CallTones.surface))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(
            isSelected ? finding.severity.color : finding.severity == .major ? Color(hex: 0x3A2B24) : .clear,
            lineWidth: isSelected ? 2 : 1))
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}

private struct NumberBadge: View {
    let finding: RoomFinding
    var size: CGFloat = 28

    var body: some View {
        Text("\(finding.number)").font(.system(size: size / 2, weight: .heavy)).foregroundColor(Color(hex: 0x1A0E06))
            .frame(width: size, height: size)
            .background(Circle().fill(finding.severity.color))
    }
}

// MARK: - Map

/// Top-down map of the room as the nine Vastu zones, North up: every
/// finding in its zone, and for the chosen finding a dashed arrow to the
/// zone it should move to, plus questions to check the move is practical
/// and what to do if it isn't.
private struct RoomMapView: View {
    @ObservedObject var vm: RoomScanViewModel
    @State private var answers: [Int: Bool] = [:]

    var body: some View {
        if let analysis = vm.analysis, let focus = vm.mapFinding, let target = focus.betterZone {
            let cantMove = answers.values.contains(false)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 12) {
                        RoundIconButton(icon: "chevron.left", label: tr("Back", "वापस")) { vm.closeMap() }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tr("New spot for the \(focus.objectName)", "\(focus.objectName) की नई जगह")).font(.vt(18)).foregroundColor(CallTones.textPrimary)
                            Text(tr("Top-down map of the room", "कमरे का ऊपर से नक्शा")).font(.vt(13, .medium)).foregroundColor(CallTones.textMuted)
                        }
                    }

                    VStack(spacing: 6) {
                        sideLabel("\(CompassDirection.n.localName) ↑")
                        HStack(spacing: 0) {
                            sideLabel(String(CompassDirection.w.localName.prefix(1))).frame(width: 26)
                            ZoneGrid(findings: analysis.findings, focus: focus, target: target)
                            sideLabel(String(CompassDirection.e.localName.prefix(1))).frame(width: 26)
                        }
                        sideLabel(CompassDirection.s.localName)
                    }
                    .padding(.top, 14).padding(.bottom, 18)
                    .background(RoundedRectangle(cornerRadius: 20).fill(CallTones.surface))

                    FlowLayoutLegend(findings: analysis.findings.filter { $0.zone != nil })

                    if !focus.checks.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(tr("Is this possible?", "क्या यह संभव है?")).font(.vt(16)).foregroundColor(CallTones.textPrimary)
                            ForEach(Array(focus.checks.enumerated()), id: \.offset) { i, check in
                                HStack(spacing: 8) {
                                    Text(check).font(.vt(14, .medium)).foregroundColor(ScanTones.body).frame(maxWidth: .infinity, alignment: .leading)
                                    ChoiceChip(label: tr("Yes", "हाँ"), chosen: answers[i] == true) { answers[i] = true }
                                    ChoiceChip(label: tr("No", "नहीं"), chosen: answers[i] == false) { answers[i] = false }
                                }
                            }
                        }
                        .padding(16)
                        .background(RoundedRectangle(cornerRadius: 18).fill(CallTones.surface))
                    }

                    if !focus.remedies.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(tr("Can't move it?", "शिफ्ट नहीं कर सकते?")).font(.vt(15)).foregroundColor(CallTones.textPrimary)
                            Text(tr("Leave the \(focus.objectName) where it is and:", "\(focus.objectName) जहाँ है वहीं रहने दें और:"))
                                .font(.vt(14, .medium)).foregroundColor(ScanTones.body)
                            ForEach(focus.remedies, id: \.self) {
                                Text("• \($0)").font(.vt(14, .medium)).foregroundColor(CallTones.textPrimary).fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(RoundedRectangle(cornerRadius: 18).fill(cantMove ? CallTones.surface : CallTones.ink))
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(cantMove ? Color.vastuPrimary : ScanTones.track, lineWidth: cantMove ? 2 : 1))
                    }

                    PillButton(label: tr("Scan again after the changes", "बदलाव के बाद फिर से स्कैन करें"), fullWidth: true) { vm.rescan() }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 28)
            }
        }
    }

    private func sideLabel(_ text: String) -> some View {
        Text(text).font(.vt(13)).foregroundColor(CallTones.textMuted).multilineTextAlignment(.center)
    }
}

/// The 3×3 zone grid with each finding in its zone and the suggested move drawn as a dashed arrow.
private struct ZoneGrid: View {
    let findings: [RoomFinding]
    let focus: RoomFinding
    let target: RoomZone

    var body: some View {
        GeometryReader { geo in
            let cell = geo.size.width / 3
            ZStack(alignment: .topLeading) {
                CallTones.ink
                ForEach(RoomZone.allCases, id: \.self) { zone in
                    ZoneCell(zone: zone, findings: findings.filter { $0.zone == zone }, focus: focus, isTarget: zone == target)
                        .frame(width: cell, height: cell, alignment: .topLeading)
                        .offset(x: cell * CGFloat(zone.col), y: cell * CGFloat(zone.row))
                }
                if let from = focus.zone {
                    MoveArrow(start: center(from, cell), end: center(target, cell))
                }
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color(hex: 0x8C86A6), lineWidth: 4))
    }

    private func center(_ zone: RoomZone, _ cell: CGFloat) -> CGPoint {
        CGPoint(x: cell * (CGFloat(zone.col) + 0.5), y: cell * (CGFloat(zone.row) + 0.62))
    }
}

/// One zone of the grid: its name and the numbered findings in it.
private struct ZoneCell: View {
    let zone: RoomZone
    let findings: [RoomFinding]
    let focus: RoomFinding
    let isTarget: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(zone.label).font(.vt(10, isTarget ? .bold : .regular))
                .foregroundColor(isTarget ? Color(hex: 0xC9B8FF) : Color(hex: 0x6E6889))
                .lineLimit(2)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 22), spacing: 3)], alignment: .leading, spacing: 3) {
                ForEach(findings) { f in
                    Text("\(f.number)").font(.system(size: 11, weight: .heavy)).foregroundColor(Color(hex: 0x1A0E06))
                        .frame(width: 22, height: 22)
                        .background(RoundedRectangle(cornerRadius: 6).fill(f.severity.color.opacity(f == focus ? 1 : 0.55)))
                }
            }
            if isTarget {
                Text(tr("New spot", "नई जगह")).font(.vt(10)).foregroundColor(.white)
                    .padding(.horizontal, 6).padding(.vertical, 1)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.vastuPrimary))
            }
            Spacer(minLength: 0)
        }
        .padding(6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(isTarget ? Color.vastuPrimary.opacity(0.14) : .clear)
        .overlay(Rectangle().stroke(ScanTones.track, lineWidth: 0.5))
    }
}

/// The suggested move: a dashed line from the thing's zone to its new one.
private struct MoveArrow: View {
    let start: CGPoint
    let end: CGPoint

    var body: some View {
        ZStack {
            Path { p in
                p.move(to: start)
                p.addLine(to: end)
            }
            .stroke(Color.vastuPrimary, style: StrokeStyle(lineWidth: 2.5, dash: [8, 6]))
            Circle().fill(Color.vastuPrimary).frame(width: 10, height: 10).position(end)
        }
    }
}

/// "1 TV  2 Bed …" with each finding's severity colour, wrapping onto new lines.
private struct FlowLayoutLegend: View {
    let findings: [RoomFinding]

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: 12, alignment: .leading)], alignment: .leading, spacing: 6) {
            ForEach(findings) { f in
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 3).fill(f.severity.color).frame(width: 12, height: 12)
                    Text("\(f.number) \(f.objectName)").font(.vt(12, .medium)).foregroundColor(ScanTones.body).lineLimit(1)
                }
            }
        }
    }
}

// MARK: - Shared pieces

private struct OverlayPill: View {
    let text: String
    let color: Color
    let showCheck: Bool

    var body: some View {
        HStack(spacing: 6) {
            if showCheck { Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundColor(color) }
            Text(text).font(.vt(13)).foregroundColor(color)
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(RoundedRectangle(cornerRadius: 16).fill(ScanTones.scrim))
    }
}

private struct ChoiceChip: View {
    let label: String
    let chosen: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label).font(.vt(14, chosen ? .bold : .regular)).foregroundColor(chosen ? .white : ScanTones.body)
                .padding(.horizontal, 14).frame(height: 36)
                .background(Capsule().fill(chosen ? Color.vastuPrimary : .clear))
                .overlay(Capsule().stroke(chosen ? .clear : ScanTones.track, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(chosen ? .isSelected : [])
    }
}

struct RoundIconButton: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Circle().fill(CallTones.surface).frame(width: 44, height: 44)
                .overlay(Image(systemName: icon).font(.system(size: 17, weight: .semibold)).foregroundColor(CallTones.textPrimary))
        }
        .accessibilityLabel(label)
    }
}

private struct SquareButton<Content: View>: View {
    let action: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 16).fill(CallTones.surface).frame(width: 52, height: 52)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(ScanTones.track, lineWidth: 1))
                .overlay(content())
        }
        .buttonStyle(.plain)
    }
}

/// Filled (or outlined) rounded button with a text label.
struct PillButton: View {
    let label: String
    var outlined = false
    var fullWidth = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label).font(.vt(15)).foregroundColor(.white).lineLimit(1).minimumScaleFactor(0.8)
                .padding(.horizontal, 20)
                .frame(maxWidth: fullWidth ? .infinity : nil)
                .frame(height: 48)
                .background(Capsule().fill(outlined ? .clear : Color.vastuPrimary))
                .overlay(Capsule().stroke(outlined ? Color.vastuPrimary : .clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
