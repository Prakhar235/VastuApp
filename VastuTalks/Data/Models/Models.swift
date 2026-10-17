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

func int64(_ value: Any?) -> Int64 { (value as? NSNumber)?.int64Value ?? 0 }

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

/// Stored at users/{uid}.
struct UserProfile: Equatable {
    var uid = ""
    var name = ""
    var email = ""
    var phone = ""
    /// Profile photo as a small base64 JPEG (see ProfilePhotos); blank if none.
    var photo = ""
    var role = UserRole.normalUser.rawValue

    init(data: [String: Any]) {
        uid = data["uid"] as? String ?? ""
        name = data["name"] as? String ?? ""
        email = data["email"] as? String ?? ""
        phone = data["phone"] as? String ?? ""
        photo = data["photo"] as? String ?? ""
        role = data["role"] as? String ?? UserRole.normalUser.rawValue
    }
}

/// What an expert fills in at sign-up, on top of what every user gives.
struct ExpertDetails {
    let specialty: String
    let experienceYears: Int
    let city: String
    let bio: String
}

/// Stored at experts/{uid} — created only for accounts that signed up
/// as a Vastu Expert. Powers the live expert directory, so it's readable
/// by every signed-in user (the phone number stays private in users/{uid}).
///
/// Accounts made before sign-up asked for specialty, experience, city,
/// bio and photo have those blank; the UI shows neutral placeholders.
struct ExpertProfile: Identifiable, Equatable {
    var uid = ""
    var name = ""
    var email = ""
    /// Profile photo as a small base64 JPEG (see ProfilePhotos); blank if none.
    var photo = ""
    var specialty = ""
    var experienceYears = 0
    var city = ""
    var bio = ""
    /// The expert's online/offline switch — whether they *want* calls. Being
    /// reachable also needs their app to be running: see isPresent(at:).
    var isOnline = true
    /// Last check-in from the expert's app (every minute while it runs).
    /// Accounts that never checked in have 0, so they show as offline.
    var lastSeenMillis: Int64 = 0
    var createdAtMillis: Int64 = 0
    /// Total calls this expert has completed — bumped when a call they were on ends.
    var consultationsCompleted: Int64 = 0
    /// True while the expert is on a call (set from their device), so others see "busy".
    var inCall = false
    /// When inCall was last set, so a flag left by a crashed app doesn't keep them busy forever.
    var inCallSinceMillis: Int64 = 0

    var id: String { uid }

    static let maxCallMillis: Int64 = 2 * 60 * 60 * 1000
    /// How often an expert's app checks in.
    static let heartbeatNanos: UInt64 = 60 * 1_000_000_000
    /// Missed about three check-ins → shown as offline.
    static let presenceTimeoutMillis: Int64 = 3 * 60 * 1000

    init(uid: String, name: String, email: String, photo: String = "", details: ExpertDetails? = nil,
         isOnline: Bool = true, createdAtMillis: Int64) {
        self.uid = uid
        self.name = name
        self.email = email
        self.photo = photo
        specialty = details?.specialty ?? ""
        experienceYears = details?.experienceYears ?? 0
        city = details?.city ?? ""
        bio = details?.bio ?? ""
        self.isOnline = isOnline
        self.createdAtMillis = createdAtMillis
    }

    init(data: [String: Any]) {
        uid = data["uid"] as? String ?? ""
        name = data["name"] as? String ?? ""
        email = data["email"] as? String ?? ""
        photo = data["photo"] as? String ?? ""
        specialty = data["specialty"] as? String ?? ""
        experienceYears = Int(int64(data["experienceYears"]))
        city = data["city"] as? String ?? ""
        bio = data["bio"] as? String ?? ""
        // The Android app's Firestore mapper stores Kotlin's `isOnline` as "online".
        isOnline = data["online"] as? Bool ?? data["isOnline"] as? Bool ?? true
        lastSeenMillis = int64(data["lastSeenMillis"])
        createdAtMillis = int64(data["createdAtMillis"])
        consultationsCompleted = int64(data["consultationsCompleted"])
        inCall = data["inCall"] as? Bool ?? false
        inCallSinceMillis = int64(data["inCallSinceMillis"])
    }

    var dictionary: [String: Any] {
        [
            "uid": uid, "name": name, "email": email, "photo": photo,
            "specialty": specialty, "experienceYears": experienceYears, "city": city, "bio": bio,
            "online": isOnline, "lastSeenMillis": lastSeenMillis,
            "createdAtMillis": createdAtMillis, "consultationsCompleted": consultationsCompleted,
            "inCall": inCall, "inCallSinceMillis": inCallSinceMillis
        ]
    }

    /// Switched on and their app checked in within presenceTimeoutMillis of `now`.
    func isPresent(at now: Int64) -> Bool { isOnline && now - lastSeenMillis < Self.presenceTimeoutMillis }

    /// On a call at `now` (ignoring a stale flag more than maxCallMillis old).
    func isBusy(at now: Int64) -> Bool { inCall && now - inCallSinceMillis < Self.maxCallMillis }

    var displaySpecialty: String { specialty.isEmpty ? "Vastu Expert" : specialty }

    var displayBio: String { bio.isEmpty ? "\(name) hasn't added an introduction yet." : bio }

    /// "New" until they've done a consultation; there are no real ratings yet, so none are shown.
    var consultationsLabel: String {
        switch consultationsCompleted {
        case 0: return "New"
        case 1: return "1 consultation"
        default: return "\(consultationsCompleted) consultations"
        }
    }
}

extension Array where Element == ExpertProfile {
    /// Callable experts first, then busy, then offline; alphabetical within each.
    func sortedForDirectory(now: Int64) -> [ExpertProfile] {
        sorted { a, b in
            let ka = (a.isPresent(at: now) ? 0 : 1, a.isBusy(at: now) ? 1 : 0)
            let kb = (b.isPresent(at: now) ? 0 : 1, b.isBusy(at: now) ? 1 : 0)
            if ka != kb { return ka < kb }
            return a.name.lowercased() < b.name.lowercased()
        }
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

/// Calls with experts are free for now: prices, the wallet and per-call
/// cost are hidden everywhere. Flip this to show them again once real
/// billing exists.
enum CallPricing {
    static let paidCalls = false
}

/// Per-session prices for the (hidden) paid calls — there's no pricing
/// step for experts yet, so each expert gets one of these placeholder
/// rates. Same templates and expert → template mapping as the Android app.
struct ExpertDisplayExtras {
    let pricePerSession: Int

    private static let templates = [
        ExpertDisplayExtras(pricePerSession: 1200),
        ExpertDisplayExtras(pricePerSession: 1500),
        ExpertDisplayExtras(pricePerSession: 2000),
        ExpertDisplayExtras(pricePerSession: 999)
    ]

    /// Deterministic per-expert placeholder — uses Java's String.hashCode
    /// so an expert gets the same template on iOS and Android.
    static func forExpert(_ uid: String) -> ExpertDisplayExtras {
        var hash: Int32 = 0
        for unit in uid.utf16 { hash = 31 &* hash &+ Int32(unit) }
        let positive = hash < 0 ? 0 &- hash : hash
        return templates[Int(positive) % templates.count]
    }
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
    /// Shows the call button for her on Home and in the chat. Turn off to offer only the chat.
    static let callEnabled = true
}
