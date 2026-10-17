import FirebaseAuth
import FirebaseFirestore
import SwiftUI

/// One expert post: author (tap to open their profile, when onAuthorTap is
/// set), when it was posted, the text and the optional picture, then Like
/// and Comment buttons with their counts. With onDelete (the author's own
/// list), a delete button that asks first. onOpen opens the post with its
/// comments. With showComments, the latest comments are listed under the
/// post (off on the post's own screen, which lists them all).
struct PostCard: View {
    let post: Post
    var liked = false
    var onLike: () -> Void = {}
    var onOpen: (() -> Void)?
    var onAuthorTap: (() -> Void)?
    var onDelete: (() -> Void)?
    var showComments = true

    @State private var confirmDelete = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Button { onAuthorTap?() } label: {
                    HStack(spacing: 10) {
                        ProfileAvatar(photo: post.authorPhoto, name: post.authorName, size: 38)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(post.authorName).font(.vt(14)).foregroundColor(.vastuCharcoal)
                            Text("\(post.authorSpecialty) · \(timeAgo(post.createdAtMillis))").font(.vt(11, .regular)).foregroundColor(.textSecondary)
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(onAuthorTap == nil)
                if onDelete != nil {
                    Button { confirmDelete = true } label: {
                        Image(systemName: "trash").font(.system(size: 17)).foregroundColor(.textSecondary).frame(width: 36, height: 36)
                    }
                    .accessibilityLabel("Delete post")
                }
            }
            .padding(.leading, 16).padding(.trailing, 8).padding(.top, 14).padding(.bottom, 10)

            if !post.text.isEmpty {
                Text(post.text).font(.vt(14, .regular)).foregroundColor(.vastuCharcoal).lineSpacing(4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.bottom, 12)
                    .contentShape(Rectangle())
                    .onTapGesture { onOpen?() }
            }

            if let image = ProfilePhotos.decode(post.image) {
                Image(uiImage: image).resizable().scaledToFill()
                    .frame(maxWidth: .infinity, maxHeight: 320)
                    .frame(height: min(320, UIScreen.main.bounds.width * image.size.height / max(image.size.width, 1)))
                    .clipped()
                    .contentShape(Rectangle())
                    .onTapGesture { onOpen?() }
                    .accessibilityLabel("Picture in \(post.authorName)'s post")
            }

            Rectangle().fill(Color.inputFill).frame(height: 1)
            HStack(spacing: 0) {
                action(liked ? "heart.fill" : "heart", post.likeCount > 0 ? "\(post.likeCount)" : "Like",
                       tint: liked ? .danger : .textSecondary, action: onLike)
                action("bubble.left", post.commentCount == 0 ? "Comment" : post.commentCount == 1 ? "1 comment" : "\(post.commentCount) comments",
                       tint: .textSecondary) { onOpen?() }
            }
            .padding(.horizontal, 8).padding(.vertical, 4)

            if showComments && !post.recentComments.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    if post.commentCount > post.recentComments.count {
                        Text("View all \(post.commentCount) comments").font(.vt(13)).foregroundColor(.textSecondary)
                    }
                    ForEach(post.recentComments) { comment in
                        commentLine(comment).font(.vt(13, .regular)).lineLimit(3)
                    }
                    Text("Add a comment…").font(.vt(13, .regular)).foregroundColor(.textSecondary).padding(.top, 2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.bottom, 14)
                .contentShape(Rectangle())
                .onTapGesture { onOpen?() }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .alert("Delete this post?", isPresented: $confirmDelete) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) { onDelete?() }
        } message: {
            Text("It will be removed from every user's feed.")
        }
    }

    private func commentLine(_ comment: CommentPreview) -> Text {
        var line = Text(comment.authorName).bold().foregroundColor(.vastuCharcoal)
        if comment.authorIsExpert {
            line = line + Text(comment.authorName == post.authorName ? " · Author" : " · Expert").font(.vt(11)).foregroundColor(.vastuPrimary)
        }
        line = line + Text("  ")
        if !comment.replyToName.isEmpty { line = line + Text("@\(comment.replyToName) ").foregroundColor(.vastuPrimary) }
        return line + Text(comment.text).foregroundColor(.vastuCharcoal)
    }

    private func action(_ icon: String, _ label: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 17))
                Text(label).font(.vt(13))
            }
            .foregroundColor(tint)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Post detail

/// A post with its comments; anyone can like, comment and reply.
@MainActor
final class PostDetailViewModel: ObservableObject {
    enum State { case loading, gone, loaded(Post) }

    let postId: String
    let myUid = Auth.auth().currentUser?.uid
    @Published private(set) var state = State.loading
    @Published private(set) var comments: [PostComment] = []
    @Published private(set) var liked = false
    @Published var input = ""
    /// The comment being answered, if the user tapped Reply.
    @Published var replyingTo: PostComment?
    @Published private(set) var sending = false
    @Published private(set) var error: String?

    private let posts = PostRepository()
    private var me: UserProfile?
    private var registrations: [ListenerRegistration] = []

    init(postId: String) { self.postId = postId }

    func start() {
        guard registrations.isEmpty else { return }
        registrations.append(posts.listenPost(postId) { [weak self] post in
            Task { @MainActor in self?.state = post.map { .loaded($0) } ?? .gone }
        })
        registrations.append(posts.listenComments(postId) { [weak self] list in
            Task { @MainActor in self?.comments = list }
        })
        if let r = posts.listenMyLikes({ [weak self] ids in
            Task { @MainActor in
                guard let self = self else { return }
                self.liked = ids.contains(self.postId)
            }
        }) { registrations.append(r) }
        if let r = UserRepository().listenMyProfile({ [weak self] profile in
            Task { @MainActor in self?.me = profile }
        }) { registrations.append(r) }
    }

    func stop() {
        registrations.forEach { $0.remove() }
        registrations = []
    }

    /// Top-level comments, each followed by its replies.
    var threads: [PostComment] {
        let replies = Dictionary(grouping: comments.filter { !$0.replyToCommentId.isEmpty }, by: \.replyToCommentId)
        return comments.filter { $0.replyToCommentId.isEmpty }.flatMap { [$0] + (replies[$0.id] ?? []) }
    }

    func toggleLike() {
        let target = !liked
        Task { try? await posts.setLiked(postId, target) }
    }

    func send() {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !sending else { return }
        guard let profile = me else {
            error = "Still loading your profile — try again in a moment."
            return
        }
        sending = true
        error = nil
        Task {
            do {
                try await posts.addComment(postId: postId, me: profile, text: text, replyTo: replyingTo)
                input = ""
                replyingTo = nil
            } catch {
                self.error = "Couldn't send: \(error.localizedDescription)"
            }
            sending = false
        }
    }

    func delete(_ comment: PostComment) {
        Task { try? await posts.deleteComment(postId: postId, commentId: comment.id) }
    }
}

struct PostDetailView: View {
    let onBack: () -> Void
    let onAuthorTap: (_ uid: String, _ name: String) -> Void

    @StateObject private var vm: PostDetailViewModel
    @FocusState private var inputFocused: Bool

    init(postId: String, onBack: @escaping () -> Void, onAuthorTap: @escaping (String, String) -> Void) {
        _vm = StateObject(wrappedValue: PostDetailViewModel(postId: postId))
        self.onBack = onBack
        self.onAuthorTap = onAuthorTap
    }

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Post", titleSize: 18, onBack: onBack).padding(.horizontal, 8).padding(.vertical, 8)
            switch vm.state {
            case .loading:
                Spacer()
            case .gone:
                Spacer()
                Text("This post has been deleted.").font(.vt(14, .medium)).foregroundColor(.textSecondary)
                Spacer()
            case .loaded(let post):
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        PostCard(post: post, liked: vm.liked, onLike: { vm.toggleLike() }, onOpen: { inputFocused = true },
                                 onAuthorTap: { onAuthorTap(post.authorUid, post.authorName) }, showComments: false)
                        let threads = vm.threads
                        Text(threads.isEmpty ? "No comments yet — ask \(post.authorName) a question." : "Comments")
                            .font(threads.isEmpty ? .vt(13, .regular) : .vt(15))
                            .foregroundColor(threads.isEmpty ? .textSecondary : .vastuCharcoal)
                            .padding(.top, 16).padding(.bottom, 8).padding(.leading, 4)
                        ForEach(threads) { comment in
                            CommentRow(
                                comment: comment,
                                isPostAuthor: comment.authorUid == post.authorUid,
                                // Your own comments, and anything on your own post, can be deleted.
                                canDelete: comment.authorUid == vm.myUid || post.authorUid == vm.myUid,
                                onReply: {
                                    vm.replyingTo = comment
                                    inputFocused = true
                                },
                                onDelete: { vm.delete(comment) }
                            )
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 8)
                }
                .scrollDismissesKeyboard(.interactively)
                commentInput
            }
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .onAppear { vm.start() }
        .onDisappear { vm.stop() }
    }

    private var commentInput: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let reply = vm.replyingTo {
                HStack {
                    Text("Replying to \(reply.authorName)").font(.vt(12, .medium)).foregroundColor(.textSecondary)
                    Spacer()
                    Button { vm.replyingTo = nil } label: {
                        Image(systemName: "xmark").font(.system(size: 13, weight: .semibold)).foregroundColor(.textSecondary)
                    }
                    .accessibilityLabel("Cancel reply")
                }
                .padding(.horizontal, 4)
            }
            if let error = vm.error {
                Text(error).font(.vt(12, .medium)).foregroundColor(.danger).padding(.leading, 4)
            }
            HStack(spacing: 8) {
                TextField(vm.replyingTo != nil ? "Write a reply…" : "Ask a question or comment…", text: $vm.input, axis: .vertical)
                    .lineLimit(1...4)
                    .font(.vt(14, .regular))
                    .focused($inputFocused)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 22).fill(Color.inputFill))
                    .onChange(of: vm.input) { if $0.count > 1000 { vm.input = String($0.prefix(1000)) } }
                let canSend = !vm.input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !vm.sending
                Button { vm.send() } label: {
                    Circle().fill(canSend ? Color.vastuPrimary : Color.vastuPrimary.opacity(0.35)).frame(width: 44, height: 44)
                        .overlay(Image(systemName: "paperplane.fill").font(.system(size: 16)).foregroundColor(.white))
                }
                .disabled(!canSend)
                .accessibilityLabel("Send")
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Color.white)
    }
}

private struct CommentRow: View {
    let comment: PostComment
    let isPostAuthor: Bool
    let canDelete: Bool
    let onReply: () -> Void
    let onDelete: () -> Void

    var body: some View {
        let isReply = !comment.replyToCommentId.isEmpty
        HStack(alignment: .top, spacing: 10) {
            ProfileAvatar(photo: comment.authorPhoto, name: comment.authorName, size: isReply ? 28 : 34)
            VStack(alignment: .leading, spacing: 4) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(comment.authorName).font(.vt(13)).foregroundColor(.vastuCharcoal)
                        if comment.authorIsExpert {
                            Text(isPostAuthor ? "Author" : "Expert").font(.vt(10)).foregroundColor(.white)
                                .padding(.horizontal, 6).padding(.vertical, 1)
                                .background(RoundedRectangle(cornerRadius: 6).fill(Color.vastuPrimary))
                        }
                    }
                    Text((isReply && !comment.replyToName.isEmpty ? "@\(comment.replyToName) " : "") + comment.text)
                        .font(.vt(14, .regular)).foregroundColor(.vastuCharcoal).lineSpacing(3)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 12).padding(.vertical, 9)
                .background(RoundedRectangle(cornerRadius: 14).fill(Color.white))
                HStack(spacing: 14) {
                    Text(timeAgo(comment.createdAtMillis)).font(.vt(11, .regular)).foregroundColor(.textSecondary)
                    Button("Reply", action: onReply).font(.vt(12)).foregroundColor(.vastuPrimary)
                    if canDelete {
                        Button("Delete", action: onDelete).font(.vt(12)).foregroundColor(.danger)
                    }
                }
                .padding(.leading, 12)
            }
            Spacer(minLength: 0)
        }
        .padding(.leading, isReply ? 44 : 0)
        .padding(.bottom, 12)
    }
}

// MARK: - New post

/// Where a Vastu expert writes a post for every user's Posts tab: some
/// text and, optionally, a picture from the camera or gallery.
struct CreatePostView: View {
    let onDone: () -> Void

    private static let maxLength = 1500
    @State private var text = ""
    @State private var image: UIImage?
    @State private var posting = false
    @State private var error: String?
    @State private var source: MediaSource?
    @State private var toast: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                Button(action: onDone) {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .semibold)).foregroundColor(.vastuCharcoal).frame(width: 44, height: 44)
                }
                .disabled(posting)
                .accessibilityLabel("Cancel")
                Text("New post").font(.vt(18)).foregroundColor(.vastuCharcoal)
                Spacer()
            }
            .padding(.horizontal, 8).padding(.vertical, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 10) {
                        ProfileAvatar(photo: "", name: Auth.auth().currentUser?.displayName ?? "", size: 36)
                        Text("Share a Vastu tip or update with everyone").font(.vt(13, .medium)).foregroundColor(.textSecondary)
                    }
                    .padding(.bottom, 12)

                    ZStack(alignment: .topLeading) {
                        if text.isEmpty {
                            Text("e.g. Keep the North-East corner of your home light and clutter-free…")
                                .font(.vt(15, .regular)).foregroundColor(.textHint).padding(.horizontal, 16).padding(.vertical, 14)
                        }
                        TextField("", text: $text, axis: .vertical)
                            .lineLimit(6...)
                            .font(.vt(15, .regular)).foregroundColor(.vastuCharcoal)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .onChange(of: text) { if $0.count > Self.maxLength { text = String($0.prefix(Self.maxLength)) } }
                    }
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.inputFill, lineWidth: 1))

                    Text("\(text.count)/\(Self.maxLength)").font(.vt(11, .regular)).foregroundColor(.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 4)

                    if let picked = image {
                        Image(uiImage: picked).resizable().scaledToFill()
                            .frame(maxWidth: .infinity).frame(height: 240).clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(alignment: .topTrailing) {
                                Button { image = nil } label: {
                                    Circle().fill(Color.black.opacity(0.55)).frame(width: 32, height: 32)
                                        .overlay(Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundColor(.white))
                                }
                                .padding(8)
                                .accessibilityLabel("Remove picture")
                            }
                            .padding(.top, 12)
                    }

                    HStack(spacing: 10) {
                        attachButton("camera", "Take photo") {
                            MediaPermissions.openCamera(.camera, open: { source = $0 }, onError: { toast = $0 })
                        }
                        attachButton("photo.on.rectangle", "Gallery") { source = .gallery }
                    }
                    .padding(.top, 12)

                    if let error = error {
                        Text(error).font(.vt(13, .medium)).foregroundColor(.danger).padding(.top, 12)
                    }
                }
                .padding(.horizontal, 20)
            }
            .scrollDismissesKeyboard(.interactively)

            GradientButton(text: "Post", enabled: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !posting,
                           isLoading: posting, action: publish)
                .padding(.horizontal, 20).padding(.vertical, 12)
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .toast($toast)
        .mediaPicker($source, onPhoto: { image = $0.image }, onError: { toast = $0 })
    }

    private func attachButton(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 16))
                Text(label).font(.vt(13))
            }
            .foregroundColor(.vastuPrimary)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
        }
        .buttonStyle(.plain)
    }

    private func publish() {
        guard let uid = Auth.auth().currentUser?.uid else { return }
        hideKeyboard()
        posting = true
        error = nil
        Task {
            do {
                guard let author = try await UserRepository().getExpert(uid) else { throw AppError("Only Vastu experts can post.") }
                try await PostRepository().create(author: author, text: text, image: image)
                posting = false
                onDone()
            } catch {
                posting = false
                self.error = "Couldn't post: \(error.localizedDescription)"
            }
        }
    }
}

// MARK: - Notifications

/// Likes and comments on your posts, and experts' replies to your
/// comments, newest first. Opening the list marks them read; tapping one
/// opens the post.
struct NotificationsView: View {
    let onBack: () -> Void
    let onOpenPost: (String) -> Void

    @State private var notifications: [AppNotification]?
    /// What was new when the list opened stays highlighted for this visit.
    @State private var newThisVisit: Set<String> = []
    @State private var registration: ListenerRegistration?
    private let repository = NotificationRepository()

    var body: some View {
        VStack(spacing: 0) {
            BackHeader(title: "Notifications", titleSize: 18, onBack: onBack).padding(.horizontal, 8).padding(.vertical, 8)
            if let list = notifications {
                if list.isEmpty {
                    Spacer()
                    Text("Nothing yet. Likes and comments on your posts, and replies to your comments, show up here.")
                        .font(.vt(14, .medium)).foregroundColor(.textSecondary).multilineTextAlignment(.center).padding(32)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(list) { item in
                                Button { onOpenPost(item.postId) } label: { row(item, isNew: newThisVisit.contains(item.id)) }
                                    .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 8)
                    }
                }
            } else {
                Spacer()
            }
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .onAppear {
            guard registration == nil else { return }
            registration = repository.listen { list in
                notifications = list
                // Everything shown is marked read (which clears the bell's badge).
                let unread = list.filter { !$0.read }.map(\.id)
                newThisVisit.formUnion(unread)
                Task { await repository.markRead(unread) }
            }
        }
        .onDisappear {
            registration?.remove()
            registration = nil
        }
    }

    private func row(_ item: AppNotification, isNew: Bool) -> some View {
        let (icon, tint): (String, Color) = item.type == "like" ? ("heart.fill", .danger)
            : item.type == "reply" ? ("arrowshape.turn.up.left.fill", .vastuPrimary) : ("bubble.left.fill", .vastuPrimary)
        return HStack(spacing: 14) {
            ProfileAvatar(photo: item.actorPhoto, name: item.actorName, size: 44)
                .overlay(alignment: .bottomTrailing) {
                    Circle().fill(Color.white).frame(width: 20, height: 20)
                        .overlay(Image(systemName: icon).font(.system(size: 10)).foregroundColor(tint))
                        .offset(x: 4, y: 4)
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(item.title).font(.vt(14)).foregroundColor(.vastuCharcoal)
                if !item.body.isEmpty {
                    Text("“\(item.body)”").font(.vt(13, .regular)).foregroundColor(.textSecondary).lineLimit(2)
                }
                Text(timeAgo(item.createdAtMillis)).font(.vt(11, .regular)).foregroundColor(.textSecondary)
            }
            Spacer(minLength: 0)
            if isNew { Circle().fill(Color.vastuPrimary).frame(width: 9, height: 9) }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(isNew ? Color.vastuPrimary.opacity(0.07) : .white))
        .contentShape(Rectangle())
    }
}
