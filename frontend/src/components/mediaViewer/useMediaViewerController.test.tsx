jest.mock("expo-sharing", () => ({
  isAvailableAsync: jest.fn(),
  shareAsync: jest.fn()
}));

jest.mock("../../services/attachmentTransfers", () => ({
  attachmentTransfers: {
    downloadAttachment: jest.fn()
  }
}));

import * as Sharing from "expo-sharing";
import { act, renderHook, waitFor } from "@testing-library/react-native";
import { attachmentTransfers } from "../../services/attachmentTransfers";
import type { MessageAttachment } from "../../types";
import { useMediaViewerController } from "./useMediaViewerController";

function createAttachment(overrides: Partial<MessageAttachment> = {}): MessageAttachment {
  return {
    attachmentId: "attachment-1",
    originalFileName: "photo.jpg",
    contentType: "image/jpeg",
    kind: "IMAGE",
    fileSizeBytes: 2048,
    durationMs: null,
    downloadUrl: "/attachments/1",
    previewUrl: "https://cdn.example/preview.jpg",
    thumbnailUrl: "https://cdn.example/thumb.jpg",
    width: 800,
    height: 600,
    waveform: null,
    accessExpiresAt: null,
    requiresAuthorization: true,
    streamingSupported: false,
    ...overrides
  };
}

describe("useMediaViewerController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("resolves the initial attachment and navigates between attachments", async () => {
    const attachments = [
      createAttachment({ attachmentId: "attachment-1" }),
      createAttachment({ attachmentId: "attachment-2", originalFileName: "second.jpg" })
    ];

    const { result } = renderHook(() =>
      useMediaViewerController({
        attachments,
        initialAttachmentId: "attachment-2",
        token: "token-1"
      })
    );

    expect(result.current.currentIndex).toBe(1);
    expect(result.current.currentAttachment?.attachmentId).toBe("attachment-2");

    act(() => {
      result.current.handlePrevious();
    });

    expect(result.current.currentIndex).toBe(0);
    expect(result.current.hasPrevious).toBe(false);
    expect(result.current.hasNext).toBe(true);
  });

  it("preloads video attachments and shares the current attachment", async () => {
    const attachments = [
      createAttachment({
        attachmentId: "attachment-video",
        originalFileName: "clip.mp4",
        kind: "VIDEO",
        contentType: "video/mp4",
        previewUrl: null,
        thumbnailUrl: null
      })
    ];

    (attachmentTransfers.downloadAttachment as jest.Mock).mockResolvedValue("file:///clip.mp4");
    (Sharing.isAvailableAsync as jest.Mock).mockResolvedValue(true);
    (Sharing.shareAsync as jest.Mock).mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useMediaViewerController({
        attachments,
        initialAttachmentId: "attachment-video",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(attachmentTransfers.downloadAttachment).toHaveBeenCalledWith(
        "token-1",
        attachments[0]
      );
      expect(result.current.currentUri).toBe("file:///clip.mp4");
    });

    await act(async () => {
      await result.current.handleShareCurrent();
    });

    expect(Sharing.shareAsync).toHaveBeenCalledWith("file:///clip.mp4");
  });

  it("preloads image attachments when no preview uri is available", async () => {
    const attachments = [
      createAttachment({
        attachmentId: "attachment-image",
        originalFileName: "scan.png",
        kind: "IMAGE",
        contentType: "image/png",
        previewUrl: null,
        thumbnailUrl: null,
        localUri: null
      })
    ];

    (attachmentTransfers.downloadAttachment as jest.Mock).mockResolvedValue("file:///scan.png");

    const { result } = renderHook(() =>
      useMediaViewerController({
        attachments,
        initialAttachmentId: "attachment-image",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(attachmentTransfers.downloadAttachment).toHaveBeenCalledWith(
        "token-1",
        attachments[0]
      );
      expect(result.current.currentUri).toBe("file:///scan.png");
    });
  });
});
