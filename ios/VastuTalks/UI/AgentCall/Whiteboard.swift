import SwiftUI

private let secondsPerShape = 0.55
private let boardBackground = Color(hex: 0x1A1824)

let whiteboardPalette: [Color] = [.white, .vastuSaffron, .vastuCopper, .boardBlue, .boardRed]

/// A finger stroke, with points stored as 0–1 fractions of the board so it renders the same at any size.
struct UserStroke: Identifiable {
    let id = UUID()
    let points: [CGPoint]
    let color: Color
}

/// Everything on the agent call's shared whiteboard: the caller's
/// freehand strokes plus Ananya's latest drawing (from VastuAgent), both
/// on the same square grid with North at the top.
@MainActor
final class WhiteboardModel: ObservableObject {
    @Published private(set) var userStrokes: [UserStroke] = []
    @Published private(set) var agentDrawing: BoardDrawing?
    @Published private(set) var agentDrawingStart = Date()
    /// True when the board holds something that hasn't been saved as a picture yet.
    @Published private(set) var hasUnsavedChanges = false

    var isEmpty: Bool { userStrokes.isEmpty && agentDrawing == nil }

    func addStroke(_ stroke: UserStroke) {
        userStrokes.append(stroke)
        hasUnsavedChanges = true
    }

    func undo() {
        if !userStrokes.isEmpty { userStrokes.removeLast() }
    }

    func showAgentDrawing(_ drawing: BoardDrawing) {
        agentDrawing = drawing
        agentDrawingStart = Date()
        hasUnsavedChanges = true
    }

    func clear() {
        userStrokes.removeAll()
        agentDrawing = nil
        hasUnsavedChanges = false
    }

    func markSaved() { hasUnsavedChanges = false }

    /// The whole board, fully drawn (no animation), as a square image — for saving and for sending to the agent.
    func render(side: CGFloat = 1024) -> UIImage {
        let strokes = userStrokes
        let drawing = agentDrawing
        let renderer = ImageRenderer(content:
            Canvas { ctx, size in
                drawBoard(&ctx, size: size, strokes: strokes, current: nil, currentColor: .white,
                          agentDrawing: drawing, agentProgress: .greatestFiniteMagnitude)
            }
            .frame(width: side, height: side)
        )
        renderer.scale = 1
        return renderer.uiImage ?? UIImage()
    }
}

/// The board itself: square, as large as fits, drawable by finger, animating Ananya's drawings in stroke by stroke.
struct CallWhiteboard: View {
    @ObservedObject var board: WhiteboardModel
    let penColor: Color
    @State private var current: [CGPoint] = []
    /// Bumped when Ananya's drawing finishes animating, so the timeline below pauses again.
    @State private var animationDone = 0

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            let _ = animationDone
            let animating = board.agentDrawing.map { Date().timeIntervalSince(board.agentDrawingStart) < Double($0.shapes.count) * secondsPerShape } ?? false
            TimelineView(.animation(paused: !animating)) { timeline in
                let progress = timeline.date.timeIntervalSince(board.agentDrawingStart) / secondsPerShape
                Canvas { ctx, size in
                    drawBoard(&ctx, size: size, strokes: board.userStrokes, current: current, currentColor: penColor,
                              agentDrawing: board.agentDrawing, agentProgress: progress)
                }
            }
            .frame(width: side, height: side)
            .clipShape(RoundedRectangle(cornerRadius: 24))
            .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.08), lineWidth: 1))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let p = CGPoint(x: value.location.x / side, y: value.location.y / side)
                        if current.isEmpty {
                            current = [CGPoint(x: value.startLocation.x / side, y: value.startLocation.y / side)]
                        }
                        if p != current.last { current.append(p) }
                    }
                    .onEnded { _ in
                        // A tap leaves a dot; a drag leaves a line.
                        let points = current.count > 1 ? current : current + current
                        if !points.isEmpty { board.addStroke(UserStroke(points: points, color: penColor)) }
                        current = []
                    }
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .task(id: board.agentDrawing?.id) {
            guard let drawing = board.agentDrawing else { return }
            try? await Task.sleep(nanoseconds: UInt64((Double(drawing.shapes.count) * secondsPerShape + 0.1) * 1_000_000_000))
            animationDone += 1
        }
    }
}

private func drawBoard(_ ctx: inout GraphicsContext, size: CGSize, strokes: [UserStroke], current: [CGPoint]?,
                       currentColor: Color, agentDrawing: BoardDrawing?, agentProgress: Double) {
    let unit = min(size.width, size.height) / 100
    ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(boardBackground))

    // Grid
    var grid = Path()
    for i in 1..<10 {
        let x = size.width * CGFloat(i) / 10, y = size.height * CGFloat(i) / 10
        grid.move(to: CGPoint(x: x, y: 0)); grid.addLine(to: CGPoint(x: x, y: size.height))
        grid.move(to: CGPoint(x: 0, y: y)); grid.addLine(to: CGPoint(x: size.width, y: y))
    }
    ctx.stroke(grid, with: .color(.white.opacity(0.05)), lineWidth: 0.5)

    drawCompass(&ctx, size: size, unit: unit)

    func userStroke(_ points: [CGPoint], _ color: Color) {
        var path = Path()
        for (i, p) in points.enumerated() {
            let pt = CGPoint(x: p.x * size.width, y: p.y * size.height)
            if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
        }
        ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: unit * 0.8, lineCap: .round, lineJoin: .round))
    }
    strokes.forEach { userStroke($0.points, $0.color) }
    if let current = current, !current.isEmpty { userStroke(current, currentColor) }

    guard let drawing = agentDrawing else { return }
    if !drawing.title.isEmpty {
        drawLabel(&ctx, drawing.title, at: CGPoint(x: size.width / 2, y: size.height * 0.04), color: .white, unit: unit, alpha: 0.85)
    }
    for (i, shape) in drawing.shapes.enumerated() {
        let fraction = min(1, max(0, agentProgress - Double(i)))
        if fraction > 0 { drawShape(&ctx, shape, fraction: fraction, size: size, unit: unit) }
    }
}

/// Lenient on purpose — the model sometimes invents shades like "lightgreen".
private func boardColor(_ name: String) -> Color {
    let n = name.lowercased()
    if ["saffron", "yellow", "orange", "gold"].contains(where: n.contains) { return .vastuSaffron }
    if ["copper", "brown"].contains(where: n.contains) { return .vastuCopper }
    if n.contains("blue") { return .boardBlue }
    if n.contains("red") { return .boardRed }
    if n.contains("green") { return .boardGreen }
    return .white
}

private func drawShape(_ ctx: inout GraphicsContext, _ shape: BoardShape, fraction: Double, size: CGSize, unit: CGFloat) {
    func px(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x / 100 * size.width, y: y / 100 * size.height) }
    let color = boardColor(shape.color)
    let style = StrokeStyle(lineWidth: unit * 0.6, lineCap: .round, lineJoin: .round)
    // Draw only the first `fraction` of each path, so shapes appear to be drawn by hand.
    func partial(_ path: Path) {
        ctx.stroke(fraction >= 1 ? path : path.trimmedPath(from: 0, to: fraction), with: .color(color), style: style)
    }

    switch shape {
    case let .rect(x, y, w, h, label, _):
        let rect = CGRect(origin: px(x, y), size: .zero).union(CGRect(origin: px(x + w, y + h), size: .zero))
        partial(Path(rect))
        if fraction >= 1, let label = label {
            drawLabel(&ctx, label, at: CGPoint(x: rect.midX, y: rect.midY), color: color, unit: unit)
        }
    case let .circle(x, y, r, label, _):
        let center = px(x, y)
        let radius = CGFloat(r) * unit
        partial(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)))
        if fraction >= 1, let label = label {
            drawLabel(&ctx, label, at: CGPoint(x: center.x, y: center.y + radius + unit * 3), color: color, unit: unit)
        }
    case let .line(x1, y1, x2, y2, arrow, _):
        let start = px(x1, y1), end = px(x2, y2)
        var path = Path()
        path.move(to: start)
        path.addLine(to: end)
        partial(path)
        if arrow && fraction >= 1 {
            let angle = atan2(end.y - start.y, end.x - start.x)
            let head = unit * 2.5
            var heads = Path()
            for a in [angle + 2.6, angle - 2.6] {
                heads.move(to: end)
                heads.addLine(to: CGPoint(x: end.x + head * cos(a), y: end.y + head * sin(a)))
            }
            ctx.stroke(heads, with: .color(color), style: style)
        }
    case let .label(x, y, text, _):
        drawLabel(&ctx, text, at: px(x, y), color: color, unit: unit, alpha: fraction)
    }
}

private func drawLabel(_ ctx: inout GraphicsContext, _ text: String, at point: CGPoint, color: Color, unit: CGFloat, alpha: Double = 1) {
    ctx.draw(
        Text(text).font(.system(size: unit * 3.2, weight: .bold)).foregroundColor(color.opacity(alpha)),
        at: point, anchor: .center
    )
}

private func drawCompass(_ ctx: inout GraphicsContext, size: CGSize, unit: CGFloat) {
    let top = CGPoint(x: size.width - unit * 5, y: unit * 3)
    var arrow = Path()
    arrow.move(to: CGPoint(x: top.x, y: top.y + unit * 5)); arrow.addLine(to: top)
    arrow.move(to: top); arrow.addLine(to: CGPoint(x: top.x - unit, y: top.y + unit * 1.5))
    arrow.move(to: top); arrow.addLine(to: CGPoint(x: top.x + unit, y: top.y + unit * 1.5))
    ctx.stroke(arrow, with: .color(.white.opacity(0.45)), style: StrokeStyle(lineWidth: unit * 0.4, lineCap: .round))
    drawLabel(&ctx, "N", at: CGPoint(x: top.x, y: top.y + unit * 7.5), color: .white, unit: unit, alpha: 0.55)
}
