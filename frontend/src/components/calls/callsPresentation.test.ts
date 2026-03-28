import type { CallHistoryEntry } from "../../types";
import {
  buildCallsHistoryEmptyState,
  buildCallsLinkAction,
  buildCallHistoryMeta,
  buildCallHistoryStatusLabel,
  buildCallHistorySubtitle,
  findExactCallsPublicChatMatch,
  formatMissedCallsSummary,
  formatCallHistoryDate,
  formatCallHistoryDuration,
  getMissedCallsCount,
  normalizeCallLinkToken
} from "./callsPresentation";

function createCall(overrides: Partial<CallHistoryEntry> = {}): CallHistoryEntry {
  return {
    answeredAt: "2026-03-27T10:01:00.000Z",
    callId: "call-1",
    chatId: "chat-1",
    chatType: "DIRECT",
    direction: "INCOMING",
    endedAt: "2026-03-27T10:03:05.000Z",
    kind: "VOICE",
    missed: false,
    mode: "DIRECT",
    participantCount: 2,
    photoAccessExpiresAt: null,
    photoUrl: null,
    startedAt: "2026-03-27T10:00:00.000Z",
    status: "ENDED",
    title: "Kate",
    ...overrides
  };
}

describe("callsPresentation", () => {
  it("formats history subtitles, dates and durations", () => {
    const call = createCall();
    expect(formatCallHistoryDuration(call)).toBe("2:05");
    expect(buildCallHistorySubtitle(call)).toBe("Incoming voice call | 2:05");
    expect(buildCallHistoryStatusLabel(call)).toBeNull();
    expect(buildCallHistoryMeta(call)).toContain("Direct | ");
    expect(formatCallHistoryDate(call.startedAt)).toContain("2026");
  });

  it("counts missed calls and normalizes join tokens", () => {
    expect(getMissedCallsCount([createCall({ missed: true }), createCall({ callId: "call-2" })])).toBe(1);
    expect(formatMissedCallsSummary(0)).toBe("No missed calls");
    expect(formatMissedCallsSummary(1)).toBe("1 missed call");
    expect(formatMissedCallsSummary(3)).toBe("3 missed calls");
    expect(buildCallsHistoryEmptyState(null)).toEqual({
      description: "Start a voice or video call from any dialog and it will show up here.",
      title: "No calls yet"
    });
    expect(buildCallsHistoryEmptyState("Offline mode")).toEqual({
      description:
        "Recent calls could not be refreshed yet. Reconnect to sync history, or place a call and it will appear here.",
      title: "Call history unavailable"
    });
    expect(normalizeCallLinkToken("  alex://call/abc  ")).toBe("abc");
    expect(normalizeCallLinkToken("alex://call?token=room-4")).toBe("room-4");
    expect(normalizeCallLinkToken("alex.example/call/room-1")).toBe("room-1");
    expect(normalizeCallLinkToken("t.me/call/room-1")).toBe("room-1");
    expect(normalizeCallLinkToken("https://t.me/call?token=room-3")).toBe("room-3");
    expect(normalizeCallLinkToken("tg://call/room-1")).toBe("room-1");
    expect(normalizeCallLinkToken("telegram://call?token=room-2")).toBe("room-2");
    expect(normalizeCallLinkToken("https://alex.example/call/room-1")).toBe("room-1");
    expect(buildCallHistorySubtitle(createCall({ missed: true, kind: "VIDEO" }))).toBe("Missed video call");
    expect(buildCallHistoryStatusLabel(createCall({ missed: true }))).toBe("Missed");
    expect(
      buildCallHistorySubtitle(
        createCall({
          answeredAt: null,
          direction: "INCOMING",
          status: "DECLINED"
        })
      )
    ).toBe("Declined voice call");
    expect(
      buildCallHistorySubtitle(
        createCall({
          answeredAt: null,
          direction: "OUTGOING",
          endedAt: "2026-03-27T10:00:20.000Z",
          status: "ENDED"
        })
      )
    ).toBe("Canceled voice call");
  });

  it("builds quick actions for non-call parsed links in the calls tab", () => {
    expect(
      buildCallsLinkAction({
        type: "JOIN",
        token: "@team"
      })
    ).toEqual({
      ctaLabel: "Open join flow",
      description: "Recognized a public chat username: @team",
      title: "Open chat link"
    });
    expect(
      buildCallsLinkAction(
        {
          type: "JOIN",
          token: "@team"
        },
        {
          about: null,
          archived: false,
          autoDeleteSeconds: null,
          chatId: "chat-team",
          chatType: "GROUP",
          commentsEnabled: false,
          crossPostingEnabled: false,
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
          photoAccessExpiresAt: null,
          photoUrl: null,
          pinOrder: null,
          pinned: false,
          pinnedMessageId: null,
          publicUsername: "team",
          reactionsEnabled: true,
          replyCount: 0,
          slowModeSeconds: null,
          title: "Team",
          topicCount: 0,
          unreadCount: 0
        }
      )
    ).toEqual({
      ctaLabel: "Open chat",
      description: "Recognized a public username for a chat already available locally: @team",
      title: "Open linked chat"
    });

    expect(
      buildCallsLinkAction({
        type: "CHAT",
        chatId: "chat-1",
        topicId: "topic-2"
      })
    ).toEqual({
      ctaLabel: "Open chat",
      description: "Recognized an app chat link with topic topic-2.",
      title: "Open linked chat"
    });

    expect(
      buildCallsLinkAction({
        type: "CALL",
        token: "room-1"
      })
    ).toBeNull();
    expect(
      findExactCallsPublicChatMatch(
        [
          {
            about: null,
            archived: false,
            autoDeleteSeconds: null,
            chatId: "chat-team",
            chatType: "GROUP",
            commentsEnabled: false,
            crossPostingEnabled: false,
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
            photoAccessExpiresAt: null,
            photoUrl: null,
            pinOrder: null,
            pinned: false,
            pinnedMessageId: null,
            publicUsername: "Team",
            reactionsEnabled: true,
            replyCount: 0,
            slowModeSeconds: null,
            title: "Team",
            topicCount: 0,
            unreadCount: 0
          }
        ],
        {
          type: "JOIN",
          token: "@team"
        }
      )
    ).toEqual(
      expect.objectContaining({
        chatId: "chat-team"
      })
    );
  });
});
