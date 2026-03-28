import {
  deriveCallPhoto,
  deriveCallTitle,
  isLiveCall,
  pickPreferredCall
} from "./rootCallUtils";
import type { CallSession, ChatSummary } from "../types";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    chatId: "chat-1",
    chatType: "DIRECT",
    title: "Chat title",
    photoUrl: null,
    photoAccessExpiresAt: null,
    peerUserId: "peer-1",
    peerPhoneNumber: null,
    peerDisplayName: "Peer 1",
    peerOnline: false,
    peerLastSeenAt: null,
    peerIsBot: false,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    publicUsername: null,
    about: null,
    autoDeleteSeconds: null,
    slowModeSeconds: null,
    forumEnabled: false,
    topicCount: 0,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    memberCount: 2,
    lastReadAt: null,
    unreadCount: 0,
    mentionCount: 0,
    replyCount: 0,
    archived: false,
    draftText: null,
    draftUpdatedAt: null,
    mutedUntil: null,
    pinned: false,
    pinOrder: null,
    pinnedMessageId: null,
    joinRequiresApproval: false,
    commentsEnabled: true,
    reactionsEnabled: true,
    crossPostingEnabled: false,
    markedUnread: false,
    ...overrides
  };
}

function createCall(overrides: Partial<CallSession> = {}): CallSession {
  return {
    callId: "call-1",
    chatId: "chat-1",
    createdByUserId: "user-1",
    kind: "VOICE",
    mode: "DIRECT",
    status: "ACTIVE",
    startedAt: "2026-03-27T10:00:00.000Z",
    answeredAt: "2026-03-27T10:00:05.000Z",
    endedAt: null,
    viewerCanModerate: false,
    viewerCanManageLinks: false,
    participants: [
      {
        userId: "user-1",
        displayName: "Alex",
        phoneNumber: null,
        photoUrl: null,
        photoAccessExpiresAt: null,
        state: "JOINED",
        invitedAt: "2026-03-27T10:00:00.000Z",
        joinedAt: "2026-03-27T10:00:05.000Z",
        leftAt: null,
        audioPublishingAllowed: true,
        videoPublishingAllowed: true,
        screenShareAllowed: true,
        screenSharing: false,
        moderatedByUserId: null,
        moderatedAt: null
      },
      {
        userId: "peer-1",
        displayName: "Peer 1",
        phoneNumber: null,
        photoUrl: "https://cdn.example.com/peer.png",
        photoAccessExpiresAt: null,
        state: "JOINED",
        invitedAt: "2026-03-27T10:00:00.000Z",
        joinedAt: "2026-03-27T10:00:06.000Z",
        leftAt: null,
        audioPublishingAllowed: true,
        videoPublishingAllowed: true,
        screenShareAllowed: true,
        screenSharing: false,
        moderatedByUserId: null,
        moderatedAt: null
      }
    ],
    ...overrides
  };
}

describe("rootCallUtils", () => {
  it("detects only active calls with a live local participant", () => {
    expect(isLiveCall(createCall(), "user-1")).toBe(true);
    expect(
      isLiveCall(
        createCall({
          participants: [
            {
              ...createCall().participants[0],
              state: "LEFT"
            }
          ]
        }),
        "user-1"
      )
    ).toBe(false);
    expect(
      isLiveCall(
        createCall({
          status: "ENDED"
        }),
        "user-1"
      )
    ).toBe(false);
  });

  it("picks the most recent live call", () => {
    const older = createCall({
      callId: "call-old",
      answeredAt: "2026-03-27T09:00:00.000Z"
    });
    const newer = createCall({
      callId: "call-new",
      answeredAt: "2026-03-27T11:00:00.000Z"
    });

    expect(pickPreferredCall([older, newer], "user-1")?.callId).toBe("call-new");
  });

  it("derives call title and photo from chat metadata first, then participant fallback", () => {
    const call = createCall();

    expect(deriveCallTitle(call, [createChat()], "user-1")).toBe("Chat title");
    expect(
      deriveCallTitle(
        createCall({
          chatId: "chat-missing",
          participants: [
            createCall().participants[1],
            {
              ...createCall().participants[1],
              userId: "peer-2",
              displayName: "Peer 2"
            }
          ]
        }),
        [],
        "user-1"
      )
    ).toBe("Peer 1, Peer 2");
    expect(deriveCallPhoto(call, [], "user-1")).toBe("https://cdn.example.com/peer.png");
    expect(
      deriveCallPhoto(
        call,
        [
          createChat({
            photoUrl: "https://cdn.example.com/chat.png"
          })
        ],
        "user-1"
      )
    ).toBe("https://cdn.example.com/chat.png");
  });
});
