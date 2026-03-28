import { act, renderHook } from "@testing-library/react-native";
import * as Sharing from "expo-sharing";
import { attachmentTransfers } from "../../services/attachmentTransfers";
import type { MessageAttachment } from "../../types";
import { useChatConversationActions } from "./useChatConversationActions";

jest.mock("expo-sharing", () => ({
  isAvailableAsync: jest.fn(),
  shareAsync: jest.fn()
}));

jest.mock("../../services/attachmentTransfers", () => ({
  attachmentTransfers: {
    downloadAttachment: jest.fn(),
    pauseDownload: jest.fn()
  }
}));

jest.mock("../../services/api", () => ({
  api: {
    muteChat: jest.fn(),
    setChatArchived: jest.fn()
  }
}));

const mockedSharing = jest.mocked(Sharing);
const mockedAttachmentTransfers = jest.mocked(attachmentTransfers);

const baseAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "attachment-1",
  contentType: "image/jpeg",
  downloadUrl: "/attachments/1",
  durationMs: null,
  fileSizeBytes: 2048,
  height: 600,
  kind: "IMAGE",
  originalFileName: "photo.jpg",
  previewUrl: "https://cdn.example/preview.jpg",
  requiresAuthorization: true,
  streamingSupported: false,
  thumbnailUrl: "https://cdn.example/thumb.jpg",
  waveform: null,
  width: 800
};

describe("useChatConversationActions", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedSharing.isAvailableAsync.mockResolvedValue(true);
    mockedAttachmentTransfers.downloadAttachment.mockResolvedValue("file:///attachment.jpg");
  });

  it("passes source message metadata into the media viewer payload", async () => {
    const onOpenMediaViewer = jest.fn();

    const { result } = renderHook(() =>
      useChatConversationActions({
        attachmentTransferStates: {},
        chatArchived: false,
        chatId: "chat-1",
        chatMutedUntil: null,
        chatTitle: "Team chat",
        onBack: jest.fn(),
        onOpenMediaViewer,
        onRefreshChats: jest.fn(),
        setError: jest.fn(),
        setOpeningAttachmentId: jest.fn(),
        token: "token-1",
        upsertChat: jest.fn()
      })
    );

    await act(async () => {
      await result.current.handleOpenAttachment(
        baseAttachment,
        [baseAttachment],
        {
          createdAt: "2026-03-27T12:00:00.000Z",
          messageId: "message-1"
        }
      );
    });

    expect(onOpenMediaViewer).toHaveBeenCalledWith({
      attachments: [baseAttachment],
      attachmentSources: [
        {
          attachmentId: "attachment-1",
          createdAt: "2026-03-27T12:00:00.000Z",
          messageId: "message-1"
        }
      ],
      chatTitle: "Team chat",
      initialAttachmentId: "attachment-1"
    });
    expect(mockedAttachmentTransfers.downloadAttachment).not.toHaveBeenCalled();
    expect(mockedSharing.shareAsync).not.toHaveBeenCalled();
  });
});
