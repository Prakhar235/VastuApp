import Foundation

// Firestore documents are read/written as plain dictionaries with the
// same field names the Android app uses, so both apps share one backend.

enum UserRole: String {
    case normalUser = "NORMAL_USER"
    case vastuExpert = "VASTU_EXPERT"
}

enum CallType: String, Hashable {
    case video = "VIDEO"
    case audio = "AUDIO"
}

enum CallRequestStatus: String {
    case ringing = "RINGING"
    case accepted = "ACCEPTED"
    case declined = "DECLINED"
    case ended = "ENDED"
    case cancelled = "CANCELLED"
}

func millisNow() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

private func int64(_ value: Any?) -> Int64 { (value as? NSNumber)?.int64Value ?? 0 }

/// Stored at callRequests/{callId} — this is the whole real-time
/// signaling mechanism. The caller writes one of these; the callee's
/// app has a live Firestore listener watching for new ones addressed
/// to their uid.
struct CallRequest: Equatable {
    var callId = ""
    var channelName = ""
    var callerUid = ""
    var callerName = ""
    var calleeUid = ""
    var calleeName = ""
    var callType = ""
    var status = CallRequestStatus.ringing.rawValue
    var createdAtMillis: Int64 = 0

    init(callId: String, channelName: String, callerUid: String, callerName: String, calleeUid: String,
         calleeName: String, callType: String, status: String, createdAtMillis: Int64) {
        self.callId = callId
        self.channelName = channelName
        self.callerUid = callerUid
        self.callerName = callerName
        self.calleeUid = calleeUid
        self.calleeName = calleeName
        self.callType = callType
        self.status = status
        self.createdAtMillis = createdAtMillis
    }

    init(data: [String: Any]) {
        callId = data["callId"] as? String ?? ""
        channelName = data["channelName"] as? String ?? ""
        callerUid = data["callerUid"] as? String ?? ""
        callerName = data["callerName"] as? String ?? ""
        calleeUid = data["calleeUid"] as? String ?? ""
        calleeName = data["calleeName"] as? String ?? ""
        callType = data["callType"] as? String ?? ""
        status = data["status"] as? String ?? CallRequestStatus.ringing.rawValue
        createdAtMillis = int64(data["createdAtMillis"])
    }

    var dictionary: [String: Any] {
        [
            "callId": callId, "channelName": channelName,
            "callerUid": callerUid, "callerName": callerName,
            "calleeUid": calleeUid, "calleeName": calleeName,
            "callType": callType, "status": status, "createdAtMillis": createdAtMillis
        ]
    }

    var type: CallType { CallType(rawValue: callType) ?? .audio }
}

/// Stored at experts/{uid} — created only for accounts that signed up
/// as a Vastu Expert. Powers the live expert directory.
struct ExpertProfile: Identifiable, Equatable {
    var uid = ""
    var name = ""
    var email = ""
    var isOnline = true
    var createdAtMillis: Int64 = 0
    /// Total calls this expert has completed — bumped when a call they were on ends.
    var consultationsCompleted: Int64 = 0

    var id: String { uid }

    init(uid: String, name: String, email: String, isOnline: Bool = true, createdAtMillis: Int64) {
        self.uid = uid
        self.name = name
        self.email = email
        self.isOnline = isOnline
        self.createdAtMillis = createdAtMillis
    }

    init(data: [String: Any]) {
        uid = data["uid"] as? String ?? ""
        name = data["name"] as? String ?? ""
        email = data["email"] as? String ?? ""
        // The Android app's Firestore mapper stores Kotlin's `isOnline` as "online".
        isOnline = data["online"] as? Bool ?? data["isOnline"] as? Bool ?? true
        createdAtMillis = int64(data["createdAtMillis"])
        consultationsCompleted = int64(data["consultationsCompleted"])
    }

    var dictionary: [String: Any] {
        [
            "uid": uid, "name": name, "email": email, "online": isOnline,
            "createdAtMillis": createdAtMillis, "consultationsCompleted": consultationsCompleted
        ]
    }
}

/// One completed (or ended) call, logged under users/{uid}/callHistory.
struct CallHistoryEntry: Identifiable {
    var id = ""
    var expertId = ""
    var expertName = ""
    var callType = "" // "AUDIO" or "VIDEO"
    var startTimeMillis: Int64 = 0
    var durationSeconds = 0
    var cost = 0
    var channelName = ""

    init(expertId: String, expertName: String, callType: String, startTimeMillis: Int64,
         durationSeconds: Int, cost: Int, channelName: String) {
        self.expertId = expertId
        self.expertName = expertName
        self.callType = callType
        self.startTimeMillis = startTimeMillis
        self.durationSeconds = durationSeconds
        self.cost = cost
        self.channelName = channelName
    }

    init(id: String, data: [String: Any]) {
        self.id = id
        expertId = data["expertId"] as? String ?? ""
        expertName = data["expertName"] as? String ?? ""
        callType = data["callType"] as? String ?? ""
        startTimeMillis = int64(data["startTimeMillis"])
        durationSeconds = Int(int64(data["durationSeconds"]))
        cost = Int(int64(data["cost"]))
        channelName = data["channelName"] as? String ?? ""
    }

    var dictionary: [String: Any] {
        [
            "id": "", "expertId": expertId, "expertName": expertName, "callType": callType,
            "startTimeMillis": startTimeMillis, "durationSeconds": durationSeconds,
            "cost": cost, "channelName": channelName
        ]
    }
}

/// Real expert signups only capture name/email — there's no
/// specialty/bio/photo/pricing step yet. Until there is, every expert
/// cycles through one of these "profile templates" so the UI has
/// something to show. Same templates (and the same expert → template
/// mapping) as the Android app.
struct ExpertDisplayExtras {
    let specialty: String
    let experienceYears: Int
    let location: String
    let rating: Double
    let reviewCount: Int
    let pricePerSession: Int
    let avatarSeed: String
    let bio: String

    private static let templates = [
        ExpertDisplayExtras(
            specialty: "Residential Vastu", experienceYears: 15, location: "Mumbai", rating: 4.9, reviewCount: 328,
            pricePerSession: 1200, avatarSeed: "template-1",
            bio: "Certified Vastu expert specializing in residential spaces, helping families create balanced, harmonious homes."
        ),
        ExpertDisplayExtras(
            specialty: "Commercial Vastu", experienceYears: 12, location: "Delhi", rating: 4.8, reviewCount: 256,
            pricePerSession: 1500, avatarSeed: "template-2",
            bio: "Certified Vastu expert specializing in commercial vastu. With years of experience helping clients create harmonious spaces."
        ),
        ExpertDisplayExtras(
            specialty: "Spiritual Consultation", experienceYears: 20, location: "Bangalore", rating: 4.9, reviewCount: 412,
            pricePerSession: 2000, avatarSeed: "template-3",
            bio: "Certified Vastu expert blending traditional spiritual consultation with modern space-planning guidance."
        ),
        ExpertDisplayExtras(
            specialty: "Interior Vastu", experienceYears: 9, location: "Pune", rating: 4.7, reviewCount: 143,
            pricePerSession: 999, avatarSeed: "template-4",
            bio: "Certified Vastu expert focused on interior layouts, furniture placement, and room-by-room energy flow."
        )
    ]

    /// Deterministic per-expert placeholder profile — uses Java's
    /// String.hashCode so an expert gets the same template on iOS and Android.
    static func forExpert(_ uid: String) -> ExpertDisplayExtras {
        var hash: Int32 = 0
        for unit in uid.utf16 { hash = 31 &* hash &+ Int32(unit) }
        let positive = hash < 0 ? 0 &- hash : hash
        return templates[Int(positive) % templates.count]
    }

    func avatarSeed(for uid: String) -> String { "\(avatarSeed)-\(uid)" }
}

/// Profile of Ananya, VastuTalks' always-available AI Vastu agent
/// (VastuAgent gives her a mind, AgentVoice her voice). No second
/// account, Firestore signaling or Agora token is needed to call her;
/// she picks up after a short ring.
enum AnanyaAgent {
    static let id = "ai-agent-ananya"
    static let name = "Ananya Verma"
    static let firstName = "Ananya"
    static let specialty = "Residential Vastu"
    /// Seeds her pravatar.cc picture; changing it changes her face.
    static let avatarSeed = "demo-expert-ananya"
    static let rating = 4.9
    static let pricePerSession = 1200
}
