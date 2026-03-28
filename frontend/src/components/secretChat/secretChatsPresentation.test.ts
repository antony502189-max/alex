import type { SecretChatSummary } from "../../types";
import {
  buildSecretChatPeerMeta,
  formatSecretChatListState,
  removeSecretChat,
  sortSecretChats,
  upsertSecretChat
} from "./secretChatsPresentation";

function createSecretChat(overrides: Partial<SecretChatSummary> = {}): SecretChatSummary {
  return {
    acceptedAt: null,
    autoDeleteSeconds: null,
    closedAt: null,
    createdAt: "2026-03-27T10:00:00.000Z",
    direction: "OUTGOING",
    initiatorPublicKey: "pub-1",
    initiatorSessionId: "session-1",
    lastMessageAt: null,
    peerDeviceName: "Pixel 9",
    peerDisplayName: "Nadia",
    peerPhoneNumber: "+375291111111",
    peerPhotoAccessExpiresAt: null,
    peerPhotoUrl: null,
    peerSessionId: "session-2",
    peerUserId: "user-2",
    recipientPublicKey: "pub-2",
    recipientSessionId: "session-2",
    secretChatId: "secret-1",
    sharedKeyFingerprint: "fp-1",
    status: "ACTIVE",
    ...overrides
  };
}

describe("secretChatsPresentation", () => {
  it("sorts, upserts, and removes secret chats", () => {
    const first = createSecretChat({ lastMessageAt: "2026-03-27T10:01:00.000Z", secretChatId: "a" });
    const second = createSecretChat({ lastMessageAt: "2026-03-27T10:02:00.000Z", secretChatId: "b" });

    expect(sortSecretChats([first, second]).map((item) => item.secretChatId)).toEqual(["b", "a"]);
    expect(
      upsertSecretChat([first], second).map((item) => item.secretChatId)
    ).toEqual(["b", "a"]);
    expect(removeSecretChat([first, second], "a").map((item) => item.secretChatId)).toEqual(["b"]);
  });

  it("formats list state and peer meta", () => {
    expect(formatSecretChatListState(createSecretChat({ autoDeleteSeconds: 10 }))).toBe("Active - TTL 10s");
    expect(buildSecretChatPeerMeta(createSecretChat())).toBe("Pixel 9 - outgoing");
  });
});
