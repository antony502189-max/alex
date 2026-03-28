import { renderHook } from "@testing-library/react-native";
import type { ChatMember, ChatSummary } from "../../types";
import { useChatConversationCapabilities } from "./useChatConversationCapabilities";

const baseChat: ChatSummary = {
  about: "General chat",
  archived: false,
  autoDeleteSeconds: 3600,
  chatId: "chat-1",
  chatType: "GROUP",
  commentsEnabled: true,
  crossPostingEnabled: true,
  draftText: null,
  draftUpdatedAt: null,
  forumEnabled: false,
  joinRequiresApproval: false,
  lastMessageAt: "2026-03-27T10:00:00.000Z",
  lastReadAt: null,
  linkedDiscussionChatId: null,
  linkedDiscussionChatTitle: null,
  markedUnread: false,
  memberCount: 12,
  mentionCount: 0,
  mutedUntil: null,
  peerBotSupportsInline: false,
  peerBotWebAppUrl: null,
  peerDisplayName: null,
  peerIsBot: false,
  peerLastSeenAt: null,
  peerOnline: false,
  peerPhoneNumber: null,
  peerUserId: null,
  photoAccessExpiresAt: "2026-03-27T12:00:00.000Z",
  photoUrl: "https://example.test/chat.jpg",
  pinOrder: null,
  pinned: false,
  pinnedMessageId: null,
  publicUsername: "team",
  reactionsEnabled: true,
  replyCount: 0,
  slowModeSeconds: 30,
  title: "Team",
  topicCount: 0,
  unreadCount: 0
};

const baseMember: ChatMember = {
  anonymousAdmin: true,
  canApproveJoinRequests: false,
  canManageInviteLinks: false,
  canManageMembers: false,
  canManageMessages: true,
  canPinMessages: true,
  canPostMessages: true,
  canSendMessages: true,
  displayName: "Alice",
  joinedAt: "2026-03-01T00:00:00.000Z",
  lastReadAt: null,
  lastSentMessageAt: "2026-03-27T10:00:10.000Z",
  phoneNumber: null,
  photoAccessExpiresAt: null,
  photoUrl: null,
  restrictedUntil: null,
  restrictionReason: null,
  role: "member",
  userId: "user-1"
};

describe("useChatConversationCapabilities", () => {
  it("derives posting, pinning, reactions, and optimistic author state", () => {
    const { result } = renderHook(() =>
      useChatConversationCapabilities({
        chat: baseChat,
        currentTimeMs: new Date("2026-03-27T10:00:20.000Z").getTime(),
        currentUserId: "user-1",
        members: [baseMember],
        topicClosed: false
      })
    );

    expect(result.current.myMembership?.userId).toBe("user-1");
    expect(result.current.memberRestricted).toBe(false);
    expect(result.current.channelPostingDisabled).toBe(false);
    expect(result.current.canPinMessages).toBe(true);
    expect(result.current.reactionsEnabled).toBe(true);
    expect(result.current.myAnonymousAdmin).toBe(true);
    expect(result.current.optimisticAuthor).toMatchObject({
      anonymousSender: true,
      displaySenderName: "Team",
      displaySenderPhotoUrl: "https://example.test/chat.jpg"
    });
    expect(result.current.slowModeEndsAt).toBe(
      new Date("2026-03-27T10:00:40.000Z").getTime()
    );
    expect(result.current.slowModeLabel).toContain("20s");
    expect(result.current.canPost).toBe(false);
  });

  it("reports restrictions and posting availability when the member cannot send", () => {
    const { result } = renderHook(() =>
      useChatConversationCapabilities({
        chat: { ...baseChat, chatType: "CHANNEL", reactionsEnabled: false, slowModeSeconds: null },
        currentTimeMs: new Date("2026-03-27T10:00:20.000Z").getTime(),
        currentUserId: "user-1",
        members: [
          {
            ...baseMember,
            anonymousAdmin: false,
            canPinMessages: false,
            canPostMessages: false,
            canSendMessages: false,
            restrictedUntil: "2026-03-28T10:00:00.000Z"
          }
        ],
        topicClosed: false
      })
    );

    expect(result.current.memberRestricted).toBe(true);
    expect(result.current.channelPostingDisabled).toBe(true);
    expect(result.current.canPost).toBe(false);
    expect(result.current.reactionsEnabled).toBe(false);
    expect(result.current.canPinMessages).toBe(false);
    expect(result.current.myAnonymousAdmin).toBe(false);
    expect(result.current.optimisticAuthor.anonymousSender).toBe(false);
    expect(result.current.restrictionLabel).toContain("Posting restricted until");
    expect(result.current.slowModeEndsAt).toBeNull();
  });
});
