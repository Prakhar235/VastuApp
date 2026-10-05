import SwiftUI

enum ChatSender { case agent, user }

struct AgentChatMessage: Identifiable, Equatable {
    let id: Int
    let text: String
    var sender: ChatSender = .agent
    var image: UIImage?
}

/// The agent call's chat page: the full conversation with Ananya (spoken
/// lines are transcribed in here too) and a box to type a question.
struct AgentChatPage: View {
    let messages: [AgentChatMessage]
    let isAgentTyping: Bool
    @Binding var inputText: String
    let onSend: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 14) {
                        if messages.isEmpty && !isAgentTyping {
                            Text("Everything you and \(AnanyaAgent.name) say shows up here.")
                                .font(.vt(13)).foregroundColor(CallTones.textMuted)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                                .padding(.top, 40)
                        }
                        ForEach(messages) { message in
                            Group {
                                if message.sender == .agent { AgentBubble(message: message) } else { UserBubble(message: message) }
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
                ZStack(alignment: .leading) {
                    if inputText.isEmpty {
                        Text("Message \(AnanyaAgent.name)…").font(.vt(14, .regular)).foregroundColor(CallTones.textMuted)
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
            .padding(.leading, 18)
            .padding(.trailing, 6)
            .padding(.vertical, 6)
            .background(Capsule().fill(CallTones.surface))
            .overlay(Capsule().stroke(CallTones.hairline, lineWidth: 1))
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

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            AvatarImage(seed: AnanyaAgent.avatarSeed, placeholder: CallTones.surfaceRaised)
                .frame(width: 30, height: 30)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(AnanyaAgent.name).font(.vt(11, .semibold)).foregroundColor(CallTones.textMuted)
                Group {
                    if isTyping {
                        Text("typing…").font(.vt(14, .regular)).foregroundColor(CallTones.textMuted)
                    } else {
                        MessageBody(message: message)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(BubbleShape(tight: .topLeft).fill(CallTones.surface))
                .overlay(BubbleShape(tight: .topLeft).stroke(CallTones.hairline, lineWidth: 1))
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

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let image = message.image {
                // Photos keep their shape (portrait/landscape); sketches are square.
                let ratio = min(1.6, max(0.6, image.size.width / max(image.size.height, 1)))
                Image(uiImage: image).resizable().scaledToFill()
                    .frame(width: 210, height: 210 / ratio)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .accessibilityLabel("Sketch")
            }
            if !message.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(message.text).font(.vt(14, .regular)).foregroundColor(.white).lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Chat bubble: 18pt corners except one 4pt "tail" corner.
private struct BubbleShape: Shape {
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
