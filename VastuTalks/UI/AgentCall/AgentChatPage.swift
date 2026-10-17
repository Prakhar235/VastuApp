import SwiftUI

enum ChatSender { case agent, user }

struct AgentChatMessage: Identifiable, Equatable {
    let id: Int
    let text: String
    var sender: ChatSender = .agent
    var image: UIImage?
    /// Set on a room scan's report; the full report is saved with the chat (AgentChatStore.loadReport).
    var hasReport = false
}

/// Ways to show Ananya a space: camera photo, a photo from the gallery, a
/// short video, or (when offered) a guided scan of a whole room.
struct ShareMediaActions {
    let takePhoto: () -> Void
    let pickPhoto: () -> Void
    let recordVideo: () -> Void
    var scanRoom: (() -> Void)?
}

/// A menu with the ShareMediaActions, opened by tapping `label`.
struct ShareMediaMenu<Label: View>: View {
    let actions: ShareMediaActions
    @ViewBuilder let label: () -> Label

    var body: some View {
        Menu {
            if let scanRoom = actions.scanRoom {
                Button(action: scanRoom) { SwiftUI.Label(tr("Scan a whole room (wall by wall)", "पूरा कमरा स्कैन करें (हर दीवार)"), systemImage: "safari") }
            }
            Button(action: actions.takePhoto) { SwiftUI.Label(tr("Take a photo", "फ़ोटो खींचें"), systemImage: "camera") }
            Button(action: actions.pickPhoto) { SwiftUI.Label(tr("Upload a photo from gallery", "गैलरी से फ़ोटो अपलोड करें"), systemImage: "photo.on.rectangle") }
            Button(action: actions.recordVideo) { SwiftUI.Label(tr("Record a video (up to 30 s)", "वीडियो रिकॉर्ड करें (30 सेकंड तक)"), systemImage: "video") }
        } label: {
            label()
        }
    }
}

/// The conversation with Ananya — the chat page of a call (spoken lines
/// are transcribed in here too) or the whole text-chat screen — and a box
/// to type a question. With `media`, the box also gets a button to share
/// a photo or video.
struct AgentChatPage: View {
    let messages: [AgentChatMessage]
    let isAgentTyping: Bool
    @Binding var inputText: String
    let onSend: () -> Void
    var media: ShareMediaActions?
    /// Opens the full room report saved with a message that hasReport.
    var onOpenReport: (AgentChatMessage) -> Void = { _ in }
    var emptyHint: String?

    @ObservedObject private var language = AgentLanguagePref.shared

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 14) {
                        if messages.isEmpty && !isAgentTyping {
                            Text(emptyHint ?? tr("Everything you and \(AnanyaAgent.name) say will show up here.",
                                                 "आप और \(AnanyaAgent.name) जो भी कहेंगे, वह यहाँ दिखेगा।"))
                                .font(.vt(13)).foregroundColor(CallTones.textMuted)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                                .padding(.top, 40)
                        }
                        ForEach(messages) { message in
                            Group {
                                if message.sender == .agent {
                                    AgentBubble(message: message, onOpenReport: { onOpenReport(message) })
                                } else {
                                    UserBubble(message: message)
                                }
                            }
                            .id(message.id)
                        }
                        if isAgentTyping {
                            AgentBubble(message: AgentChatMessage(id: -1, text: "…"), isTyping: true).id(-1)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                }
                .scrollDismissesKeyboard(.interactively)
                .onAppear { scrollToEnd(proxy, animated: false) }
                .onChange(of: messages.count) { _ in scrollToEnd(proxy) }
                .onChange(of: isAgentTyping) { _ in scrollToEnd(proxy) }
            }

            HStack(spacing: 8) {
                if let media = media {
                    ShareMediaMenu(actions: media) {
                        Circle().fill(CallTones.surfaceRaised).frame(width: 40, height: 40)
                            .overlay(Image(systemName: "photo.badge.plus").font(.system(size: 17)).foregroundColor(CallTones.textPrimary))
                    }
                    .accessibilityLabel("Share a photo or video")
                }
                ZStack(alignment: .leading) {
                    if inputText.isEmpty {
                        Text(tr("Ask \(AnanyaAgent.name)…", "\(AnanyaAgent.name) से पूछें…")).font(.vt(14, .regular)).foregroundColor(CallTones.textMuted)
                    }
                    TextField("", text: $inputText, axis: .vertical)
                        .lineLimit(1...4)
                        .font(.vt(14, .regular))
                        .foregroundColor(CallTones.textPrimary)
                        .tint(.vastuPrimary)
                        .submitLabel(.send)
                        .onSubmit(onSend)
                }
                .padding(.vertical, 8)

                let canSend = !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                Button(action: onSend) {
                    Circle().fill(canSend ? Color.vastuPrimary : CallTones.surfaceRaised).frame(width: 40, height: 40)
                        .overlay(Image(systemName: "paperplane.fill").font(.system(size: 15))
                            .foregroundColor(canSend ? .white : CallTones.textMuted))
                }
                .disabled(!canSend)
                .accessibilityLabel("Send")
            }
            .padding(.leading, media == nil ? 18 : 6)
            .padding(.trailing, 6)
            .padding(.vertical, 6)
            .background(RoundedRectangle(cornerRadius: 26).fill(CallTones.surface))
            .overlay(RoundedRectangle(cornerRadius: 26).stroke(CallTones.hairline, lineWidth: 1))
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }

    private func scrollToEnd(_ proxy: ScrollViewProxy, animated: Bool = true) {
        let target = isAgentTyping ? -1 : messages.last?.id
        guard let id = target else { return }
        if animated {
            withAnimation(.easeOut(duration: 0.25)) { proxy.scrollTo(id, anchor: .bottom) }
        } else {
            proxy.scrollTo(id, anchor: .bottom)
        }
    }
}

private struct AgentBubble: View {
    let message: AgentChatMessage
    var isTyping = false
    var onOpenReport: () -> Void = {}

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            AvatarImage(seed: AnanyaAgent.avatarSeed, placeholder: CallTones.surfaceRaised)
                .frame(width: 30, height: 30)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(AnanyaAgent.name).font(.vt(11, .semibold)).foregroundColor(CallTones.textMuted)
                Group {
                    if isTyping {
                        Text(tr("Typing…", "लिख रही हैं…")).font(.vt(14, .regular)).foregroundColor(CallTones.textMuted)
                    } else {
                        MessageBody(message: message)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(BubbleShape(tight: .topLeft).fill(CallTones.surface))
                .overlay(BubbleShape(tight: .topLeft).stroke(CallTones.hairline, lineWidth: 1))
                if message.hasReport {
                    Button(action: onOpenReport) {
                        Text(tr("View full report", "पूरी रिपोर्ट देखें")).font(.vt(13, .semibold)).foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 8)
                            .background(Capsule().fill(Color.vastuPrimary))
                    }
                    .buttonStyle(.plain)
                    .padding(.top, 4)
                }
            }
            .frame(maxWidth: 290, alignment: .leading)
            Spacer(minLength: 0)
        }
    }
}

private struct UserBubble: View {
    let message: AgentChatMessage

    var body: some View {
        HStack {
            Spacer(minLength: 0)
            MessageBody(message: message)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(BubbleShape(tight: .topRight).fill(Color.vastuPrimary))
                .frame(maxWidth: 290, alignment: .trailing)
        }
    }
}

private struct MessageBody: View {
    let message: AgentChatMessage
    @State private var fullScreen = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let image = message.image {
                // Photos keep their shape (portrait/landscape); her marked-up photos are bigger so the labels are readable.
                let ratio = min(1.6, max(0.6, image.size.width / max(image.size.height, 1)))
                let width: CGFloat = message.sender == .agent ? 262 : 210
                Image(uiImage: image).resizable().scaledToFill()
                    .frame(width: width, height: width / ratio)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .contentShape(Rectangle())
                    .onTapGesture { fullScreen = true }
                    .accessibilityLabel(message.sender == .agent ? "Photo with suggestions marked" : "Shared image")
                    .fullScreenCover(isPresented: $fullScreen) { FullScreenImage(image: image) }
            }
            if !message.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(message.sender == .agent ? highlightSuggestions(message.text) : AttributedString(message.text))
                    .font(.vt(14, .regular)).foregroundColor(.white).lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Ananya wraps each suggestion in **double asterisks** (see VastuAgent's
/// prompt); those parts are shown bold in saffron on a soft saffron wash so
/// the advice stands out from the explanation. An unclosed ** is shown as is.
func highlightSuggestions(_ text: String) -> AttributedString {
    var result = AttributedString()
    var rest = Substring(text)
    while let start = rest.range(of: "**"), let end = rest[start.upperBound...].range(of: "**") {
        result += AttributedString(String(rest[..<start.lowerBound]))
        var highlight = AttributedString(String(rest[start.upperBound..<end.lowerBound]))
        highlight.swiftUI.foregroundColor = Color.vastuSaffron
        highlight.swiftUI.font = Font.vt(14, .bold)
        highlight.swiftUI.backgroundColor = Color.vastuSaffron.opacity(0.14)
        result += highlight
        rest = rest[end.upperBound...]
    }
    result += AttributedString(String(rest))
    return result
}

/// Tap-to-close full-screen view of a chat image, so marks and labels can be read.
struct FullScreenImage: View {
    let image: UIImage
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            Image(uiImage: image).resizable().scaledToFit()
        }
        .contentShape(Rectangle())
        .onTapGesture { dismiss() }
        .accessibilityLabel("Full-screen image")
        .accessibilityAddTraits(.isButton)
    }
}

/// Chat bubble: 18pt corners except one 4pt "tail" corner.
struct BubbleShape: Shape {
    let tight: UIRectCorner

    func path(in rect: CGRect) -> Path {
        let big: CGFloat = 18, small: CGFloat = 4
        func r(_ corner: UIRectCorner) -> CGFloat { corner == tight ? small : big }
        var p = Path()
        p.move(to: CGPoint(x: rect.minX + r(.topLeft), y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX - r(.topRight), y: rect.minY))
        p.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.minY), tangent2End: CGPoint(x: rect.maxX, y: rect.maxY), radius: r(.topRight))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r(.bottomRight)))
        p.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.maxY), radius: r(.bottomRight))
        p.addLine(to: CGPoint(x: rect.minX + r(.bottomLeft), y: rect.maxY))
        p.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.minY), radius: r(.bottomLeft))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY + r(.topLeft)))
        p.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.minY), tangent2End: CGPoint(x: rect.maxX, y: rect.minY), radius: r(.topLeft))
        p.closeSubpath()
        return p
    }
}
