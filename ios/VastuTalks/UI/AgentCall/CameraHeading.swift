import CoreMotion
import Foundation

/// Live compass heading of the phone's back camera, in degrees clockwise
/// from magnetic North (nil until the first reading, or if the phone has
/// no compass).
///
/// Uses the direction the back camera looks, so it works whether the
/// phone is held upright or tilted; when it's lying nearly flat (camera
/// pointing at the floor) it falls back to where the top of the phone
/// points, which is the way the person holding it is facing. Same maths
/// as the Android app (gravity + magnetic field → East/North axes).
final class CameraHeading: ObservableObject {
    @Published private(set) var degrees: Double?

    private let motion = CMMotionManager()
    private var east = 0.0
    private var north = 0.0
    private static let smoothing = 0.15

    func start() {
        guard motion.isDeviceMotionAvailable,
              CMMotionManager.availableAttitudeReferenceFrames().contains(.xMagneticNorthZVertical) else { return }
        motion.deviceMotionUpdateInterval = 1.0 / 30
        motion.startDeviceMotionUpdates(using: .xMagneticNorthZVertical, to: .main) { [weak self] data, _ in
            guard let data = data else { return }
            self?.update(data)
        }
    }

    func stop() {
        motion.stopDeviceMotionUpdates()
    }

    private func update(_ data: CMDeviceMotion) {
        // Device frame: x right, y towards the top, z out of the screen.
        let up = (-data.gravity.x, -data.gravity.y, -data.gravity.z)
        let field = data.magneticField.field
        let h = (field.x, field.y, field.z)
        guard var e = cross(h, up), let n = cross(up, e) else { return }
        e = normalized(e)
        let nn = normalized(n)

        // Back camera looks along device -Z.
        var x = -e.2
        var y = -nn.2
        if hypot(x, y) < 0.35 { // nearly flat: use device +Y instead
            x = e.1
            y = nn.1
        }
        let angle = atan2(x, y)
        if degrees == nil {
            east = sin(angle)
            north = cos(angle)
        } else {
            east += (sin(angle) - east) * Self.smoothing
            north += (cos(angle) - north) * Self.smoothing
        }
        let result = atan2(east, north) * 180 / .pi
        degrees = (result + 360).truncatingRemainder(dividingBy: 360)
    }

    private typealias Vec = (Double, Double, Double)

    private func cross(_ a: Vec, _ b: Vec) -> Vec? {
        let c = (a.1 * b.2 - a.2 * b.1, a.2 * b.0 - a.0 * b.2, a.0 * b.1 - a.1 * b.0)
        return (c.0 * c.0 + c.1 * c.1 + c.2 * c.2) < 1e-6 ? nil : c
    }

    private func normalized(_ v: Vec) -> Vec {
        let length = (v.0 * v.0 + v.1 * v.1 + v.2 * v.2).squareRoot()
        return (v.0 / length, v.1 / length, v.2 / length)
    }
}
