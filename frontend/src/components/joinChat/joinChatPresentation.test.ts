import type {
  JoinChatResult,
  PublicChatDiscovery
} from "../../types";
import {
  buildJoinChatLinkAction,
  buildDiscoveryMetaLines,
  buildJoinRequestStatusMessage,
  findExactPublicChatDiscovery,
  getDiscoveryActionLabel,
  getJoinFieldActionLabel,
  getPublicChatDiscoveryQuery,
  normalizeInviteToken,
  shouldDiscoverPublicChats
} from "./joinChatPresentation";

function createJoinResult(overrides: Partial<JoinChatResult> = {}): JoinChatResult {
  return {
    status: "REQUESTED",
    chat: null,
    chatId: "chat-1",
    title: "Team",
    publicUsername: "team",
    requestedAt: "2026-03-27T12:00:00.000Z",
    ...overrides
  };
}

function createDiscovery(overrides: Partial<PublicChatDiscovery> = {}): PublicChatDiscovery {
  return {
    chatId: "chat-1",
    chatType: "GROUP",
    title: "Team",
    photoUrl: null,
    photoAccessExpiresAt: null,
    publicUsername: "team",
    about: null,
    forumEnabled: false,
    memberCount: 12,
    joinRequiresApproval: true,
    joined: false,
    ...overrides
  };
}

describe("joinChatPresentation", () => {
  it("normalizes invite tokens and decides whether public discovery should run", () => {
    expect(normalizeInviteToken(" alex://join/team ")).toBe("team");
    expect(normalizeInviteToken("@team")).toBe("@team");
    expect(normalizeInviteToken("tg://resolve?domain=team")).toBe("@team");
    expect(normalizeInviteToken("telegram://join?invite=invite-token")).toBe("invite-token");
    expect(normalizeInviteToken("t.me/team")).toBe("@team");
    expect(normalizeInviteToken("https://t.me/team")).toBe("@team");
    expect(normalizeInviteToken("telegram.me/+invite-token")).toBe("invite-token");
    expect(normalizeInviteToken("https://t.me/+invite-token")).toBe("invite-token");
    expect(shouldDiscoverPublicChats("@te")).toBe(true);
    expect(shouldDiscoverPublicChats("alex://join/team")).toBe(true);
    expect(shouldDiscoverPublicChats("t.me/team")).toBe(true);
    expect(shouldDiscoverPublicChats("https://t.me/team")).toBe(true);
    expect(shouldDiscoverPublicChats("join/team")).toBe(false);
    expect(getPublicChatDiscoveryQuery("@team")).toBe("team");
  });

  it("builds status and discovery meta copy", () => {
    expect(buildJoinRequestStatusMessage(createJoinResult())).toBe(
      "Join request sent to Team (@team)."
    );
    expect(buildDiscoveryMetaLines(createDiscovery())).toEqual([
      "@team",
      "12 members | approval"
    ]);
    expect(
      buildDiscoveryMetaLines(
        createDiscovery({
          memberCount: 1,
          joined: true,
          joinRequiresApproval: false
        })
      )
    ).toEqual(["@team", "1 member | already joined"]);
    expect(
      buildDiscoveryMetaLines(
        createDiscovery({
          chatType: "CHANNEL",
          memberCount: 1200,
          publicUsername: "news",
          title: "News"
        })
      )
    ).toEqual(["@news", "1200 subscribers | approval"]);
    expect(getDiscoveryActionLabel(createDiscovery())).toBe("Request access");
    expect(
      getDiscoveryActionLabel(
        createDiscovery({
          joinRequiresApproval: false
        })
      )
    ).toBe("Join");
    expect(getDiscoveryActionLabel(createDiscovery({ joined: true }))).toBe("Open");
    expect(getJoinFieldActionLabel(createDiscovery())).toBe("Request access");
    expect(
      getJoinFieldActionLabel(
        createDiscovery({
          joinRequiresApproval: false
        })
      )
    ).toBe("Join chat");
    expect(getJoinFieldActionLabel(createDiscovery({ joined: true }))).toBe("Open chat");
    expect(
      findExactPublicChatDiscovery(
        [
          createDiscovery({
            publicUsername: "Team"
          })
        ],
        "@team"
      )
    ).toEqual(
      expect.objectContaining({
        publicUsername: "Team"
      })
    );
  });

  it("builds quick actions for parsed non-join links", () => {
    expect(
      buildJoinChatLinkAction({
        type: "CALL",
        token: "room-77"
      })
    ).toEqual({
      ctaLabel: "Join call",
      description: "Recognized a call link. Open the calls flow instead of trying to join it as a chat.",
      title: "Open call link"
    });

    expect(
      buildJoinChatLinkAction({
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
      buildJoinChatLinkAction({
        type: "JOIN",
        token: "@team"
      })
    ).toBeNull();
  });
});
