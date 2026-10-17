import UIKit

/// The agent's marks on the caller's own photo (or one frame of their
/// video). Shapes use percent of the image: x of its width (left→right),
/// y of its height (top→bottom); a circle's r is percent of the width.
struct PhotoMarks {
    let frame: Int
    let shapes: [BoardShape]
}

/// Draws on real photos: a coordinate grid for the model, and the agent's marks for the caller.
enum ImageMarkup {
    /// Copy of `src` with a faint numbered 10% grid, sent to the model so it
    /// can say where things are in the picture. The caller never sees it.
    static func withGrid(_ src: UIImage) -> UIImage {
        render(src) { ctx, w, h, unit in
            ctx.setStrokeColor(UIColor.white.withAlphaComponent(0.4).cgColor)
            ctx.setLineWidth(max(1, unit * 0.25))
            let shadow = NSShadow()
            shadow.shadowColor = UIColor.black
            shadow.shadowBlurRadius = unit * 0.6
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: unit * 3.6),
                .foregroundColor: UIColor.yellow,
                .shadow: shadow
            ]
            for i in 1...9 {
                let x = w * CGFloat(i) / 10
                let y = h * CGFloat(i) / 10
                ctx.move(to: CGPoint(x: x, y: 0)); ctx.addLine(to: CGPoint(x: x, y: h))
                ctx.move(to: CGPoint(x: 0, y: y)); ctx.addLine(to: CGPoint(x: w, y: y))
                ctx.strokePath()
                let label = NSAttributedString(string: "\(i * 10)", attributes: attributes)
                label.draw(at: CGPoint(x: x + unit * 0.6, y: unit * 0.5))
                label.draw(at: CGPoint(x: unit * 0.6, y: y - unit * 0.6 - label.size().height))
            }
        }
    }

    /// Copy of `src` with the agent's `shapes` drawn on it, labels in readable pills.
    static func draw(_ src: UIImage, shapes: [BoardShape]) -> UIImage {
        render(src) { ctx, w, h, unit in
            func px(_ x: Double) -> CGFloat { CGFloat(x) / 100 * w }
            func py(_ y: Double) -> CGFloat { CGFloat(y) / 100 * h }

            for shape in shapes {
                let color = markColor(shape.color)
                ctx.saveGState()
                ctx.setShadow(offset: .zero, blur: unit * 0.8, color: UIColor.black.withAlphaComponent(0.67).cgColor)
                ctx.setStrokeColor(color.cgColor)
                ctx.setLineWidth(unit * 1.1)
                ctx.setLineCap(.round)
                ctx.setLineJoin(.round)
                switch shape {
                case let .circle(x, y, r, label, _):
                    let center = CGPoint(x: px(x), y: py(y))
                    let radius = max(CGFloat(r) / 100 * w, unit * 3)
                    ctx.strokeEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
                    ctx.restoreGState()
                    if let label = label { drawLabel(label, center.x, center.y + radius + unit * 4, color, unit, w, h) }
                case let .rect(x, y, rw, rh, label, _):
                    let rect = CGRect(x: px(x), y: py(y), width: px(rw), height: py(rh))
                    ctx.addPath(UIBezierPath(roundedRect: rect, cornerRadius: unit * 1.5).cgPath)
                    ctx.strokePath()
                    ctx.restoreGState()
                    if let label = label { drawLabel(label, rect.midX, rect.maxY + unit * 4, color, unit, w, h) }
                case let .line(x1, y1, x2, y2, arrow, _, label):
                    let start = CGPoint(x: px(x1), y: py(y1)), end = CGPoint(x: px(x2), y: py(y2))
                    ctx.move(to: start)
                    ctx.addLine(to: end)
                    if arrow {
                        let angle = atan2(end.y - start.y, end.x - start.x)
                        let head = unit * 4
                        ctx.move(to: CGPoint(x: end.x + head * cos(angle + 2.6), y: end.y + head * sin(angle + 2.6)))
                        ctx.addLine(to: end)
                        ctx.addLine(to: CGPoint(x: end.x + head * cos(angle - 2.6), y: end.y + head * sin(angle - 2.6)))
                    }
                    ctx.strokePath()
                    ctx.restoreGState()
                    if let label = label { drawLabel(label, (start.x + end.x) / 2, (start.y + end.y) / 2 - unit * 3, color, unit, w, h) }
                case let .label(x, y, text, _):
                    ctx.restoreGState()
                    drawLabel(text, px(x), py(y), color, unit, w, h)
                }
            }
        }
    }

    /// Draws `body` over a copy of `src` at the image's own pixel size (orientation baked in).
    private static func render(_ src: UIImage, _ body: (CGContext, CGFloat, CGFloat, CGFloat) -> Void) -> UIImage {
        let size = src.size
        let format = UIGraphicsImageRendererFormat()
        format.scale = src.scale
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            src.draw(in: CGRect(origin: .zero, size: size))
            body(context.cgContext, size.width, size.height, min(size.width, size.height) / 100)
        }
    }

    /// Text on a dark pill with a coloured edge, kept inside the image.
    private static func drawLabel(_ text: String, _ cx: CGFloat, _ cy: CGFloat, _ color: UIColor,
                                  _ unit: CGFloat, _ w: CGFloat, _ h: CGFloat) {
        let font = UIFont.boldSystemFont(ofSize: unit * 4.2)
        let string = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: UIColor.white])
        let textSize = string.size()
        let padX = unit * 1.8, padY = unit * 1.1
        let boxW = textSize.width + padX * 2
        let boxH = textSize.height + padY * 2
        let left = min(max(cx - boxW / 2, unit), max(unit, w - boxW - unit))
        let top = min(max(cy - boxH / 2, unit), max(unit, h - boxH - unit))
        let box = UIBezierPath(roundedRect: CGRect(x: left, y: top, width: boxW, height: boxH), cornerRadius: boxH / 2)
        UIColor(red: 0x15 / 255, green: 0x12 / 255, blue: 0x1F / 255, alpha: 0xDD / 255).setFill()
        box.fill()
        color.setStroke()
        box.lineWidth = unit * 0.5
        box.stroke()
        string.draw(at: CGPoint(x: left + padX, y: top + padY))
    }

    /// Lenient on purpose — the model sometimes invents shades like "lightgreen".
    private static func markColor(_ name: String) -> UIColor {
        let n = name.lowercased()
        if n.contains("red") { return UIColor(red: 1, green: 0x4D / 255, blue: 0x4D / 255, alpha: 1) }
        if n.contains("green") { return UIColor(red: 0x3D / 255, green: 0xDC / 255, blue: 0x84 / 255, alpha: 1) }
        if n.contains("blue") { return UIColor(red: 0x4D / 255, green: 0xA3 / 255, blue: 1, alpha: 1) }
        if ["saffron", "yellow", "orange", "gold"].contains(where: n.contains) {
            return UIColor(red: 1, green: 0xB0 / 255, blue: 0x20 / 255, alpha: 1)
        }
        return .white
    }
}
