import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseFunctions
import FirebaseMessaging
import Foundation

/// Firebase needs VastuTalks/GoogleService-Info.plist (see README.md).
/// Without it the app shows a setup screen instead of crashing.
enum FirebaseSetup {
    private(set) static var isConfigured = false

    static func configure() {
        guard !isConfigured, Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else { return }
        FirebaseApp.configure()
        isConfigured = true
    }
}

struct AppError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

// MARK: - Auth

/// Thin wrapper around FirebaseAuth's email/password APIs.
final class AuthRepository {
    var currentUser: FirebaseAuth.User? { Auth.auth().currentUser }

    func signIn(email: String, password: String) async throws -> FirebaseAuth.User {
        do {
            return try await Auth.auth().signIn(withEmail: email.trimmingCharacters(in: .whitespaces), password: password).user
        } catch {
            throw Self.friendly(error)
        }
    }

    func signUp(email: String, password: String, displayName: String) async throws -> FirebaseAuth.User {
        let user: FirebaseAuth.User
        do {
            user = try await Auth.auth().createUser(withEmail: email.trimmingCharacters(in: .whitespaces), password: password).user
        } catch {
            throw Self.friendly(error)
        }
        // Best-effort — the account exists even if setting the name fails.
        let change = user.createProfileChangeRequest()
        change.displayName = displayName.trimmingCharacters(in: .whitespaces)
        try? await change.commitChanges()
        return user
    }

    func signOut() {
        try? Auth.auth().signOut()
    }

    /// Turns Firebase's error codes into short, user-facing messages.
    private static func friendly(_ error: Error) -> Error {
        let nsError = error as NSError
        guard nsError.domain == AuthErrorDomain, let code = AuthErrorCode.Code(rawValue: nsError.code) else { return error }
        switch code {
        case .userNotFound: return AppError("No account found with that email.")
        case .wrongPassword, .invalidCredential, .invalidEmail: return AppError("Incorrect email or password.")
        case .emailAlreadyInUse: return AppError("An account with that email already exists.")
        case .weakPassword: return AppError("Password is too weak. Use at least 6 characters.")
        default: return AppError(nsError.localizedDescription)
        }
    }
}

// MARK: - Users & experts

final class UserRepository {
    private var db: Firestore { Firestore.firestore() }

    /// Called right after account creation. Writes users/{uid} (with role)
    /// and, for experts, experts/{uid} so they show up in the directory.
    func createProfileForNewUser(uid: String, name: String, email: String, role: UserRole) async throws {
        let profile: [String: Any] = [
            "uid": uid, "name": name, "email": email, "role": role.rawValue,
            "createdAtMillis": millisNow(), "fcmToken": NSNull()
        ]
        try await db.collection("users").document(uid).setData(profile)
        if role == .vastuExpert {
            let expert = ExpertProfile(uid: uid, name: name, email: email, isOnline: true, createdAtMillis: millisNow())
            try await db.collection("experts").document(uid).setData(expert.dictionary)
        }
    }

    /// Saves this device's push token so calls can ring it.
    func saveFcmToken(_ token: String) async throws {
        guard let uid = Auth.auth().currentUser?.uid else { throw AppError("No signed-in user") }
        try await db.collection("users").document(uid).updateData(["fcmToken": token])
    }

    /// Called (only from the expert's own device) when a call they were on ends.
    func incrementConsultationCount(expertUid: String) async throws {
        try await db.collection("experts").document(expertUid)
            .updateData(["consultationsCompleted": FieldValue.increment(Int64(1))])
    }

    func currentUserRole() async throws -> UserRole {
        guard let uid = Auth.auth().currentUser?.uid else { throw AppError("No signed-in user") }
        let doc = try await db.collection("users").document(uid).getDocument()
        return (doc.data()?["role"] as? String).flatMap(UserRole.init(rawValue:)) ?? .normalUser
    }

    /// Live list of every signed-up expert — shows regardless of online/offline status.
    func listenExperts(_ onChange: @escaping (Result<[ExpertProfile], Error>) -> Void) -> ListenerRegistration {
        db.collection("experts").addSnapshotListener { snapshot, error in
            if let error = error {
                onChange(.failure(error))
            } else {
                onChange(.success(snapshot?.documents.map { ExpertProfile(data: $0.data()) } ?? []))
            }
        }
    }
}

// MARK: - Call signaling

final class CallSignalingRepository {
    private var collection: CollectionReference { Firestore.firestore().collection("callRequests") }

    /// Caller creates this. channelName is fixed per expert (not per
    /// call) so one Agora token can be cached and reused for a day of
    /// calls — see TokenRepository.fetchOrCreateExpertToken.
    func createCallRequest(callerUid: String, callerName: String, calleeUid: String,
                           calleeName: String, callType: CallType) async throws -> CallRequest {
        let request = CallRequest(
            callId: UUID().uuidString.lowercased(),
            channelName: "expert-\(calleeUid)",
            callerUid: callerUid, callerName: callerName,
            calleeUid: calleeUid, calleeName: calleeName,
            callType: callType.rawValue,
            status: CallRequestStatus.ringing.rawValue,
            createdAtMillis: millisNow()
        )
        try await collection.document(request.callId).setData(request.dictionary)
        return request
    }

    func updateStatus(callId: String, status: CallRequestStatus) async throws {
        try await collection.document(callId).updateData(["status": status.rawValue])
    }

    /// Expert side: live stream of calls ringing for this uid.
    func listenForIncomingCalls(myUid: String, _ onChange: @escaping (CallRequest?) -> Void) -> ListenerRegistration {
        collection
            .whereField("calleeUid", isEqualTo: myUid)
            .whereField("status", isEqualTo: CallRequestStatus.ringing.rawValue)
            .addSnapshotListener { snapshot, _ in
                onChange(snapshot?.documents.first.map { CallRequest(data: $0.data()) })
            }
    }

    /// Caller side: watch one call request for accept/decline.
    func listenForCallStatus(callId: String, _ onChange: @escaping (CallRequest?) -> Void) -> ListenerRegistration {
        collection.document(callId).addSnapshotListener { snapshot, _ in
            onChange(snapshot?.data().map(CallRequest.init(data:)))
        }
    }
}

// MARK: - Call history

/// Stores call history under users/{uid}/callHistory/{autoId}.
final class CallHistoryRepository {
    private func historyCollection() -> CollectionReference? {
        guard let uid = Auth.auth().currentUser?.uid else { return nil }
        return Firestore.firestore().collection("users").document(uid).collection("callHistory")
    }

    func logCall(_ entry: CallHistoryEntry) async throws {
        guard let collection = historyCollection() else { throw AppError("No signed-in user") }
        _ = try await collection.addDocument(data: entry.dictionary)
    }

    func fetchHistory() async throws -> [CallHistoryEntry] {
        guard let collection = historyCollection() else { throw AppError("No signed-in user") }
        let snapshot = try await collection.order(by: "startTimeMillis", descending: true).getDocuments()
        return snapshot.documents.map { CallHistoryEntry(id: $0.documentID, data: $0.data()) }
    }
}

// MARK: - Agora tokens

/// Calls the "generateAgoraToken" Cloud Function (see /functions) instead
/// of minting tokens on-device — the Agora App Certificate never ships
/// in the app.
final class TokenRepository {
    /// Buffer before the token's real expiry so we never hand out one that's about to die mid-call.
    private let expiryBufferMillis: Int64 = 5 * 60 * 1000

    func fetchToken(channelName: String, uid: Int) async throws -> String {
        do {
            let result = try await Functions.functions().httpsCallable("generateAgoraToken")
                .call(["channelName": channelName, "uid": uid])
            guard let token = (result.data as? [String: Any])?["token"] as? String, !token.isEmpty else {
                throw AppError("Token function returned no token.")
            }
            return token
        } catch let error as NSError where error.domain == FunctionsErrorDomain {
            throw AppError("Token function error (\(error.code)): \(error.localizedDescription)")
        }
    }

    /// Reuses a cached token for this expert's fixed channel if it's
    /// still valid for 5+ minutes; otherwise mints a fresh one (~23h)
    /// and caches it in expertTokens/{expertUid}. Uses uid 0 (Agora's
    /// wildcard), so one token works for both the caller and the callee.
    func fetchOrCreateExpertToken(expertUid: String, channelName: String) async throws -> String {
        if let hardcoded = AgoraConfig.hardcodedExpertTokens[expertUid] { return hardcoded }

        let docRef = Firestore.firestore().collection("expertTokens").document(expertUid)
        do {
            let existing = try await docRef.getDocument().data() ?? [:]
            let cachedToken = existing["token"] as? String
            let cachedChannel = existing["channelName"] as? String
            let expiresAt = (existing["expiresAtMillis"] as? NSNumber)?.int64Value ?? 0
            if let cachedToken = cachedToken, cachedChannel == channelName, expiresAt - millisNow() > expiryBufferMillis {
                return cachedToken
            }

            let fresh: String
            do {
                fresh = try await fetchToken(channelName: channelName, uid: 0)
            } catch {
                throw AppError(
                    "No valid token found for this expert. Add one manually at expertTokens/\(expertUid) " +
                    "in Firebase Console (see README), or deploy the generateAgoraToken Cloud Function."
                )
            }
            // Matches TOKEN_EXPIRATION_SECONDS in functions/index.js (~23h).
            try await docRef.setData([
                "token": fresh, "channelName": channelName,
                "expiresAtMillis": millisNow() + 23 * 60 * 60 * 1000
            ])
            return fresh
        } catch let error as AppError {
            throw error
        } catch {
            // Cache read/write failed — mint a token directly rather than failing the call.
            return try await fetchToken(channelName: channelName, uid: 0)
        }
    }
}

// MARK: - Push token

enum PushToken {
    /// Best-effort — a missed save just means this device won't ring for calls until the next sign-in.
    static func saveBestEffort() async {
        guard let token = try? await Messaging.messaging().token() else { return }
        try? await UserRepository().saveFcmToken(token)
    }
}
