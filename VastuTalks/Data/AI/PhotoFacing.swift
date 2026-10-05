import Foundation

/// The eight Vastu directions, clockwise from North.
enum CompassDirection: Int, CaseIterable {
    case n, ne, e, se, s, sw, w, nw

    var short: String { ["N", "NE", "E", "SE", "S", "SW", "W", "NW"][rawValue] }
    var label: String { ["North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West"][rawValue] }
    var vastuName: String { ["Uttar", "Ishan", "Purva", "Agneya", "Dakshin", "Nairutya", "Paschim", "Vayavya"][rawValue] }
    var degrees: Double { Double(rawValue) * 45 }

    /// The direction `steps` eighth-turns clockwise from this one (negative = anticlockwise).
    func turn(_ steps: Int) -> CompassDirection {
        let count = Self.allCases.count
        return Self(rawValue: ((rawValue + steps) % count + count) % count)!
    }

    static func of(degrees: Double) -> CompassDirection {
        let count = allCases.count
        let index = Int((degrees / 45).rounded())
        return Self(rawValue: (index % count + count) % count)!
    }
}

/// Which way the camera was pointing when a photo was taken, in degrees
/// clockwise from (magnetic) North, and how we know.
struct PhotoFacing: Equatable {
    enum Source {
        /// Written into the photo's metadata by the camera (GPS ImgDirection).
        case photo
        /// Read from the phone's compass while the caller reviewed the photo.
        case compass
        /// Picked by the caller.
        case caller
    }

    let degrees: Double
    let source: Source

    var direction: CompassDirection { .of(degrees: degrees) }

    /// Tells the model where everything in the frame sits, so it doesn't have to work it out.
    func describeForAgent() -> String {
        let d = direction
        let rounded = Int(degrees.rounded())
        let how: String
        switch source {
        case .photo: how = "according to the compass reading saved in the photo (about \(rounded)° from North)"
        case .compass: how = "according to the phone's compass (about \(rounded)° from North)"
        case .caller: how = "according to the caller"
        }
        return "The camera was pointing \(d.label) (\(d.vastuName)) \(how). So the wall or area straight ahead in the " +
            "photo is on the \(d.label) side of the room, the left edge of the frame is towards \(d.turn(-2).label), " +
            "the right edge is towards \(d.turn(2).label), the far-left corner is \(d.turn(-1).label), the far-right " +
            "corner is \(d.turn(1).label), and the photographer is standing on the \(d.turn(4).label) side."
    }
}

/// Keyword-matched canned replies Ananya falls back on when the OpenAI
/// model can't be reached (no API key, or the request failed).
enum AgentFallbackReplies {
    private static let topicReplies: [([String], String)] = [
        (["kitchen", "stove", "cooking"],
         "For kitchens, the South-East corner works best — that's the fire element's zone. Face East while cooking if you can."),
        (["bedroom", "sleep", "bed"],
         "For bedrooms, South-West is traditionally favored for the head of the household, and try sleeping with your head to the South."),
        (["entrance", "door", "main door"],
         "Entrances do best facing North, North-East, or East — that's the suggestion I sent you a moment ago, actually."),
        (["color", "colour", "paint"],
         "Light, calming colors work well in most rooms — soft yellows and greens in the North-East, earthy tones in the South-West."),
        (["plant", "garden", "tulsi"],
         "Plants are wonderful near the entrance — tulsi especially is considered auspicious right by the main door."),
        (["mirror"],
         "Just keep mirrors out of direct line with the bed or the main door — otherwise they're generally fine."),
        (["bathroom", "toilet"],
         "Toilets are best in the West or North-West, and ideally not sharing a wall with your kitchen or pooja room."),
        (["price", "cost", "session", "rate"],
         "The running cost of this call is shown at the top of the screen — it's billed by the minute while we talk."),
        (["hello", "hi", "hey", "namaste"],
         "Namaste! Happy to help — ask me about any room and I'll share what I can."),
        (["draw", "sketch", "whiteboard", "board"],
         "Feel free to open the whiteboard yourself using the pencil icon — sketch out your room and I can talk you through it.")
    ]

    private static let fallbacks = [
        "That's a good question — every space is a little different, so I'd want to see your actual floor plan to give a precise answer.",
        "Good thought. In general, keeping that area open and well-lit tends to help, but it really depends on your layout.",
        "I hear you. For something that specific, a full consultation would let me give you a proper, personalized answer."
    ]

    static func reply(for userText: String) -> String {
        let lower = userText.lowercased()
        let match = topicReplies.first { keywords, _ in keywords.contains { lower.contains($0) } }
        return match?.1 ?? fallbacks.randomElement()!
    }
}
