import SwiftUI

enum VastuIllustrationType {
    case compass, house, om, water, flame, plant, mirror, yantra

    /// Picks a thematically-relevant illustration from keywords in a post's text, falling back to a mandala.
    static func forPost(_ postText: String) -> VastuIllustrationType {
        let text = postText.lowercased()
        func has(_ words: String...) -> Bool { words.contains { text.contains($0) } }
        if has("fire", "kitchen", "stove", "flame") { return .flame }
        if has("water", "fountain", "aquarium", "fish") { return .water }
        if has("tulsi", "plant", "garden", "basil") { return .plant }
        if has("mirror") { return .mirror }
        if has("pooja", "prayer") { return .om }
        if has("compass", "north", "south", "east", "west", "direction", "zone") { return .compass }
        if has("door", "entrance", "staircase", "house", "room", "office", "bedroom") { return .house }
        return .yantra
    }

    var background: Color {
        switch self {
        case .flame: return Color(hex: 0xFFF1E6)
        case .water: return Color(hex: 0xE8F1FF)
        case .plant: return Color(hex: 0xEAF7E9)
        case .mirror: return Color(hex: 0xF1F0F5)
        case .om: return Color(hex: 0xFFF5E0)
        case .compass: return Color(hex: 0xEFEBFF)
        case .house: return Color(hex: 0xEFF6FF)
        case .yantra: return Color(hex: 0xF6EFEA)
        }
    }
}

/// Android draws these in raw pixels; this converts to points at a typical ~2.75x density.
private let px: CGFloat = 1 / 2.75

/// 0→1→0 over `period` seconds (a reversing animation), or 0→1 looping when `reverses` is false.
private func phase(_ date: Date, period: Double, reverses: Bool = true) -> CGFloat {
    let t = date.timeIntervalSinceReferenceDate
    if !reverses { return CGFloat(t.truncatingRemainder(dividingBy: period) / period) }
    let cycle = t.truncatingRemainder(dividingBy: period * 2) / period
    return CGFloat(cycle <= 1 ? cycle : 2 - cycle)
}

struct VastuIllustration: View {
    let type: VastuIllustrationType

    var body: some View {
        ZStack {
            type.background
            switch type {
            case .compass: CompassIllustration()
            case .house: HouseIllustration()
            case .om: OmIllustration()
            case .water: WaterDropIllustration()
            case .flame: FlameIllustration()
            case .plant: PlantIllustration()
            case .mirror: MirrorIllustration()
            case .yantra: YantraIllustration()
            }
        }
    }
}

struct CompassIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            let rotation = Angle.degrees(Double(phase(timeline.date, period: 6, reverses: false)) * 360)
            Canvas { ctx, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let radius = min(size.width, size.height) / 3.2
                ctx.stroke(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                           with: .color(.vastuPrimary), style: StrokeStyle(lineWidth: 6 * px, lineCap: .round))
                for angle in stride(from: 0.0, to: 360.0, by: 90.0) {
                    let rad = CGFloat(angle * .pi / 180)
                    var tick = Path()
                    tick.move(to: CGPoint(x: center.x + (radius - 14 * px) * cos(rad), y: center.y + (radius - 14 * px) * sin(rad)))
                    tick.addLine(to: CGPoint(x: center.x + (radius + 8 * px) * cos(rad), y: center.y + (radius + 8 * px) * sin(rad)))
                    ctx.stroke(tick, with: .color(.vastuCopper), style: StrokeStyle(lineWidth: 5 * px, lineCap: .round))
                }
                var needle = Path()
                needle.move(to: CGPoint(x: 0, y: -radius + 6 * px))
                needle.addLine(to: CGPoint(x: -12 * px, y: 0))
                needle.addLine(to: CGPoint(x: 0, y: radius - 6 * px))
                needle.addLine(to: CGPoint(x: 12 * px, y: 0))
                needle.closeSubpath()
                let transform = CGAffineTransform(translationX: center.x, y: center.y).rotated(by: CGFloat(rotation.radians))
                ctx.fill(needle.applying(transform), with: .color(.vastuSaffron))
                ctx.fill(Path(ellipseIn: CGRect(x: center.x - 6 * px, y: center.y - 6 * px, width: 12 * px, height: 12 * px)),
                         with: .color(.vastuCharcoal))
            }
        }
    }
}

private struct HouseIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            let bounce = -10 * px * phase(timeline.date, period: 0.9)
            Canvas { ctx, size in
                let w = size.width, h = size.height
                let bodyTop = h * 0.5 + bounce
                ctx.fill(Path(CGRect(x: w * 0.32, y: bodyTop, width: w * 0.36, height: h * 0.72 - bodyTop)), with: .color(.vastuPrimary))
                var roof = Path()
                roof.move(to: CGPoint(x: w * 0.28, y: bodyTop))
                roof.addLine(to: CGPoint(x: w * 0.5, y: h * 0.3 + bounce))
                roof.addLine(to: CGPoint(x: w * 0.72, y: bodyTop))
                roof.closeSubpath()
                ctx.fill(roof, with: .color(.vastuCopper))
                let doorTop = h * 0.6 + bounce
                ctx.fill(Path(CGRect(x: w * 0.46, y: doorTop, width: w * 0.08, height: h * 0.72 - doorTop)), with: .color(.vastuSaffron))
            }
        }
    }
}

private struct OmIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            Text("ॐ")
                .font(.vt(56))
                .foregroundColor(.vastuCopper)
                .scaleEffect(0.9 + 0.18 * phase(timeline.date, period: 1.4))
        }
    }
}

private struct WaterDropIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            let bob = (-6 + 12 * phase(timeline.date, period: 1.2)) * px
            Canvas { ctx, size in
                let cx = size.width / 2, cy = size.height / 2 + bob
                let r = min(size.width, size.height) / 5
                var drop = Path()
                drop.move(to: CGPoint(x: cx, y: cy - r * 1.6))
                drop.addQuadCurve(to: CGPoint(x: cx, y: cy + r), control: CGPoint(x: cx + r * 1.3, y: cy - r * 0.2))
                drop.addQuadCurve(to: CGPoint(x: cx, y: cy - r * 1.6), control: CGPoint(x: cx - r * 1.3, y: cy - r * 0.2))
                drop.closeSubpath()
                ctx.fill(drop, with: .color(.boardBlue))
                let hr = r * 0.25
                ctx.fill(Path(ellipseIn: CGRect(x: cx - r * 0.3 - hr, y: cy - r * 0.1 - hr, width: hr * 2, height: hr * 2)),
                         with: .color(.white.opacity(0.5)))
            }
        }
    }
}

private struct FlameIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let cx = size.width / 2, cy = size.height / 2
                let r = min(size.width, size.height) / 4.5
                func flame(_ top: CGFloat, _ bottom: CGFloat, _ spread: CGFloat, _ mid: CGFloat) -> Path {
                    var p = Path()
                    p.move(to: CGPoint(x: cx, y: cy - r * top))
                    p.addQuadCurve(to: CGPoint(x: cx, y: cy + r * bottom), control: CGPoint(x: cx + r * spread, y: cy + r * mid))
                    p.addQuadCurve(to: CGPoint(x: cx, y: cy - r * top), control: CGPoint(x: cx - r * spread, y: cy + r * mid))
                    p.closeSubpath()
                    return p
                }
                ctx.fill(flame(1.8, 1.6, 1.4, 0), with: .color(Color(hex: 0xE8622C)))
                ctx.fill(flame(0.9, 1.1, 0.7, 0.2), with: .color(.vastuSaffron))
            }
            .scaleEffect(0.92 + 0.18 * phase(timeline.date, period: 0.4))
        }
    }
}

private struct PlantIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let w = size.width, h = size.height
                var pot = Path()
                pot.move(to: CGPoint(x: w * 0.4, y: h * 0.62))
                pot.addLine(to: CGPoint(x: w * 0.6, y: h * 0.62))
                pot.addLine(to: CGPoint(x: w * 0.56, y: h * 0.78))
                pot.addLine(to: CGPoint(x: w * 0.44, y: h * 0.78))
                pot.closeSubpath()
                ctx.fill(pot, with: .color(.vastuCopper))
                let leaf = Color(hex: 0x4C9A5B)
                ctx.fill(Path(ellipseIn: CGRect(x: w * 0.32, y: h * 0.3, width: w * 0.18, height: h * 0.32)), with: .color(leaf))
                ctx.fill(Path(ellipseIn: CGRect(x: w * 0.5, y: h * 0.24, width: w * 0.18, height: h * 0.36)), with: .color(leaf))
                ctx.fill(Path(ellipseIn: CGRect(x: w * 0.5, y: h * 0.42, width: w * 0.18, height: h * 0.3)), with: .color(leaf))
            }
            .rotationEffect(.degrees(Double(-4 + 8 * phase(timeline.date, period: 1.6))))
        }
    }
}

private struct MirrorIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            let shine = 0.15 + 0.55 * phase(timeline.date, period: 1.1)
            Canvas { ctx, size in
                let w = size.width, h = size.height
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.3, y: h * 0.2, width: w * 0.4, height: h * 0.6), cornerRadius: 24 * px),
                         with: .color(Color(hex: 0xB9C4D6)))
                var line = Path()
                line.move(to: CGPoint(x: w * 0.36, y: h * 0.28))
                line.addLine(to: CGPoint(x: w * 0.6, y: h * 0.72))
                ctx.stroke(line, with: .color(.white.opacity(Double(shine))), style: StrokeStyle(lineWidth: 10 * px, lineCap: .round))
            }
        }
    }
}

private struct YantraIllustration: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let cx = size.width / 2, cy = size.height / 2
                let cell = min(size.width, size.height) / 8
                let colors: [Color] = [.vastuPrimary, .vastuCopper, .vastuSaffron]
                var index = 0
                for row in -1...1 {
                    for col in -1...1 {
                        let rect = CGRect(x: cx + CGFloat(col) * cell * 1.4 - cell / 2, y: cy + CGFloat(row) * cell * 1.4 - cell / 2,
                                          width: cell, height: cell)
                        ctx.fill(Path(rect), with: .color(colors[index % colors.count].opacity(0.75)))
                        index += 1
                    }
                }
            }
            .rotationEffect(.degrees(Double(phase(timeline.date, period: 9, reverses: false)) * 360))
        }
    }
}
