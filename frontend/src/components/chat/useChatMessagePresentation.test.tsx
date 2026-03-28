import { renderHook } from "@testing-library/react-native";
import type { AttachmentTransferState } from "../../store/useAttachmentTransferStore";
import type {
  ChatMember,
  ChatMessage,
  MessageAttachment
} from "../../types";
import { useChatMessagePresentation } from "./useChatMessagePresentation";

const baseAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "att-1",
  contentType: "audio/ogg",
  downloadUrl: "",
  durationMs: 3200,
  fileSizeBytes: 2048,
  height: null,
  kind: "VOICE",
  originalFileName: "voice.ogg",
  previewUrl: null,
  requiresAuthorization: false,
  streamingSupported: false,
  thumbnailUrl: null,
  uploadState: "UPLOADED",
  waveform: [20, 65, 40],
  width: null
};

const baseMessage: ChatMessage = {
  anonymousSender: false,
  attachments: [],
  caption: null,
  chatId: "chat-1",
  clientMessageId: null,
  commentCount: 0,
  contactCard: null,
  createdAt: "2026-03-27T10:00:00.000Z",
  deletedAt: null,
  deliveredAt: null,
  deliveryStatus: "DELIVERED",
  discussionChatId: null,
  discussionRootMessageId: null,
  displaySenderName: null,
  displaySenderPhotoAccessExpiresAt: null,
  displaySenderPhotoUrl: null,
  editedAt: null,
  entities: [],
  expiresAt: null,
  forwardedFromChatId: null,
  forwardedFromMessageId: null,
  location: null,
  messageId: "msg-1",
  messageType: "TEXT",
  poll: null,
  readAt: null,
  reactions: [],
  recipientId: null,
  replyToMessageId: null,
  senderId: "user-1",
  serviceMessage: null,
  silent: false,
  sticker: null,
  text: "Hello from me",
  threadRootMessageId: null,
  topicId: null,
  viaBotUserId: null
};

const members: ChatMember[] = [
  {
    anonymousAdmin: false,
    canApproveJoinRequests: false,
    canManageInviteLinks: false,
    canManageMembers: false,
    canManageMessages: true,
    canPinMessages: true,
    canPostMessages: true,
    canSendMessages: true,
    displayName: "Alice",
    joinedAt: "2026-03-01T00:00:00.000Z",
    lastReadAt: "2026-03-27T10:02:00.000Z",
    lastSentMessageAt: null,
    phoneNumber: null,
    photoAccessExpiresAt: null,
    photoUrl: null,
    restrictedUntil: null,
    restrictionReason: null,
    role: "ADMIN",
    userId: "user-2"
  }
];

const runningUpload: AttachmentTransferState = {
  attachmentId: "att-1",
  direction: "UPLOAD",
  error: null,
  localUri: null,
  progress: 0.42,
  sessionId: null,
  status: "RUNNING",
  totalBytes: 100,
  transferredBytes: 42,
  updatedAt: "2026-03-27T10:00:00.000Z"
};

describe("useChatMessagePresentation", () => {
  it("builds message summary and sender/meta helpers", () => {
    const { result } = renderHook(() =>
      useChatMessagePresentation({
        attachmentTransferStates: { [baseAttachment.attachmentId]: runningUpload },
        canManageMessages: true,
        chatType: "GROUP",
        currentUserId: "user-1",
        members
      })
    );

    expect(
      result.current.describeMessage({
        ...baseMessage,
        attachments: [baseAttachment],
        text: ""
      })
    ).toBe("Voice message");
    expect(result.current.resolveDisplaySenderName(baseMessage)).toBe("You");
    expect(result.current.resolveDisplaySenderName({ ...baseMessage, senderId: "user-2" })).toBe("Alice");
    expect(result.current.renderMessageMeta(baseMessage)).toContain("delivered");
    expect(result.current.getAttachmentTransferMeta(baseAttachment)).toBe("Uploading 42%");
    expect(result.current.renderWaveform(baseAttachment, "#166534")).not.toBeNull();
  });

  it("allows closing polls for authors or managers in group chats", () => {
    const ownGroup = renderHook(() =>
      useChatMessagePresentation({
        attachmentTransferStates: {},
        canManageMessages: false,
        chatType: "GROUP",
        currentUserId: "user-1",
        members
      })
    );
    const managerGroup = renderHook(() =>
      useChatMessagePresentation({
        attachmentTransferStates: {},
        canManageMessages: true,
        chatType: "GROUP",
        currentUserId: "user-1",
        members
      })
    );
    const directChat = renderHook(() =>
      useChatMessagePresentation({
        attachmentTransferStates: {},
        canManageMessages: true,
        chatType: "DIRECT",
        currentUserId: "user-1",
        members
      })
    );

    const ownPollMessage: ChatMessage = {
      ...baseMessage,
      poll: {
        closed: false,
        multipleChoice: false,
        options: [],
        pollId: "poll-1",
        question: "Ship it?",
        totalVoters: 0
      }
    };
    const peerPollMessage: ChatMessage = {
      ...ownPollMessage,
      senderId: "user-2"
    };

    expect(ownGroup.result.current.canClosePoll(ownPollMessage)).toBe(true);
    expect(managerGroup.result.current.canClosePoll(peerPollMessage)).toBe(true);
    expect(directChat.result.current.canClosePoll(peerPollMessage)).toBe(false);
    expect(
      managerGroup.result.current.canClosePoll({
        ...peerPollMessage,
        deliveryStatus: "QUEUED"
      })
    ).toBe(false);
  });
});
