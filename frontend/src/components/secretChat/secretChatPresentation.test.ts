import type {
  SecretChatAttachment,
  SecretChatMessage,
  SecretChatSummary
} from "../../types";
import {
  buildResolvedSecretMessageMeta,
  buildSecretAttachmentActionLabel,
  buildSecretAttachmentMeta,
  buildSecretAttachmentTitle,
  buildSecretChatDisabledComposerNotice,
  buildSecretChatStatusText,
  filterVisibleSecretMessages,
  formatSecretDuration,
  formatSecretFileSize,
  inferSecretAttachmentKind,
  mergeSecretChatMessages,
  type ResolvedSecretChatMessage
} from "./secretChatPresentation";

function createSummary(overrides: Partial<SecretChatSummary> = {}): SecretChatSummary {
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

function createMessage(overrides: Partial<SecretChatMessage> = {}): SecretChatMessage {
  return {
    ciphertext: "ciphertext",
    createdAt: "2026-03-27T10:01:00.000Z",
    expiresAt: null,
    messageType: "TEXT",
    nonce: "nonce",
    readAt: null,
    secretChatId: "secret-1",
    secretMessageId: "message-1",
    senderSessionId: "session-1",
    senderUserId: "user-1",
    ...overrides
  };
}

function createAttachment(overrides: Partial<SecretChatAttachment> = {}): SecretChatAttachment {
  return {
    attachmentId: "attachment-1",
    contentType: "image/jpeg",
    fileNonce: "nonce",
    fileSizeBytes: 1024,
    kind: "IMAGE",
    originalFileName: "photo.jpg",
    ...overrides
  };
}

describe("secretChatPresentation", () => {
  it("merges and filters visible messages", () => {
    const older = createMessage({ createdAt: "2026-03-27T10:00:00.000Z", secretMessageId: "m-1" });
    const newer = createMessage({ createdAt: "2026-03-27T10:02:00.000Z", secretMessageId: "m-2" });
    const expired = createMessage({
      createdAt: "2026-03-27T10:03:00.000Z",
      expiresAt: "2026-03-27T10:03:05.000Z",
      secretMessageId: "m-3"
    });

    expect(mergeSecretChatMessages([newer], [older, expired]).map((item) => item.secretMessageId)).toEqual([
      "m-1",
      "m-2",
      "m-3"
    ]);
    expect(
      filterVisibleSecretMessages([older, expired], new Date("2026-03-27T10:04:00.000Z").getTime())
    ).toEqual([older]);
  });

  it("formats file sizes, duration, and attachment kinds", () => {
    expect(formatSecretFileSize(500)).toBe("500 B");
    expect(formatSecretFileSize(1500)).toBe("1 KB");
    expect(formatSecretDuration(65000)).toBe("1:05");
    expect(inferSecretAttachmentKind("FILE", "clip.mp4", null)).toBe("VIDEO");
    expect(inferSecretAttachmentKind("IMAGE", "clip.mp4", "video/mp4")).toBe("IMAGE");
  });

  it("builds status, meta, and labels", () => {
    const resolved: ResolvedSecretChatMessage = {
      attachments: [],
      failed: true,
      raw: createMessage({
        expiresAt: "2026-03-27T10:05:00.000Z",
        readAt: "2026-03-27T10:02:00.000Z"
      }),
      text: "hello"
    };

    expect(buildSecretChatStatusText(createSummary({ autoDeleteSeconds: 10 }))).toBe("Active - auto-delete 10s");
    expect(buildResolvedSecretMessageMeta(resolved, "user-1")).toContain("undecryptable");
    expect(buildSecretAttachmentTitle(createAttachment({ kind: "VOICE" }))).toBe("Secret voice note");
    expect(buildSecretAttachmentMeta(createAttachment({ kind: "VIDEO", durationMs: 125000 }))).toContain("2:05");
    expect(
      buildSecretAttachmentActionLabel({
        attachment: createAttachment(),
        imageVisible: false,
        opening: false
      })
    ).toBe("Decrypt photo");
    expect(buildSecretChatDisabledComposerNotice(createSummary({ status: "PENDING" }))).toContain("Messages are disabled");
  });
});
