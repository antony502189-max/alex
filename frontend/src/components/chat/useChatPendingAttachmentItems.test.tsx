import { renderHook } from "@testing-library/react-native";
import type { MessageAttachment } from "../../types";
import { useChatPendingAttachmentItems } from "./useChatPendingAttachmentItems";

const audioAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "audio-1",
  contentType: "audio/ogg",
  downloadUrl: "",
  durationMs: 3200,
  fileSizeBytes: 2048,
  height: null,
  kind: "VOICE",
  localUri: "file:///tmp/audio.ogg",
  originalFileName: "audio.ogg",
  previewUrl: null,
  requiresAuthorization: false,
  streamingSupported: false,
  thumbnailUrl: null,
  uploadState: "PENDING_UPLOAD",
  waveform: [15, 60, 35],
  width: null
};

const imageAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "image-1",
  contentType: "image/jpeg",
  downloadUrl: "",
  durationMs: null,
  fileSizeBytes: 4096,
  height: 720,
  kind: "IMAGE",
  localUri: "file:///tmp/photo.jpg",
  originalFileName: "photo.jpg",
  previewUrl: null,
  requiresAuthorization: false,
  streamingSupported: false,
  thumbnailUrl: "https://example.test/thumb.jpg",
  uploadState: "UPLOADED",
  waveform: null,
  width: 1280
};

const videoAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "video-1",
  contentType: "video/mp4",
  downloadUrl: "",
  durationMs: 12000,
  fileSizeBytes: 8192,
  height: 720,
  kind: "VIDEO",
  localUri: "file:///tmp/clip.mp4",
  originalFileName: "clip.mp4",
  previewUrl: null,
  requiresAuthorization: false,
  streamingSupported: true,
  thumbnailUrl: "https://example.test/video-thumb.jpg",
  uploadState: "UPLOADED",
  waveform: null,
  width: 1280
};

describe("useChatPendingAttachmentItems", () => {
  it("builds retryable pending items with media-specific labels and a batch summary", () => {
    const { result } = renderHook(() =>
      useChatPendingAttachmentItems({
        attachmentTitle: (attachment) =>
          attachment.kind === "VOICE"
            ? "Voice message"
            : attachment.kind === "VIDEO"
              ? "Video"
              : "Photo",
        formatDuration: () => "0:03",
        formatFileSize: (size) => `${size} B`,
        getAttachmentTransferMeta: (attachment) =>
          attachment.attachmentId === "audio-1" ? "Upload interrupted. Retry on send." : null,
        isAudioAttachment: (attachment) => attachment.kind === "VOICE",
        isImageAttachment: (attachment) => attachment.kind === "IMAGE",
        isQueuedUploadAttachment: (attachment) => attachment.uploadState === "PENDING_UPLOAD",
        isTrimEligibleAttachment: (attachment) => attachment.kind === "VIDEO",
        isVideoAttachment: (attachment) => attachment.kind === "VIDEO",
        pendingAttachments: [audioAttachment, imageAttachment, videoAttachment],
        renderWaveform: (attachment) => `waveform:${attachment.attachmentId}`,
        transferStates: {
          "audio-1": {
            direction: "UPLOAD",
            error: "Upload interrupted",
            localUri: "file:///tmp/audio.ogg",
            progress: 0.42,
            sessionId: null,
            status: "FAILED",
            totalBytes: 2048,
            transferredBytes: 860,
            updatedAt: "2026-03-28T10:00:00.000Z",
            attachmentId: "audio-1"
          },
          "video-1": {
            direction: "UPLOAD",
            error: null,
            localUri: null,
            progress: 0.5,
            sessionId: "session-1",
            status: "RUNNING",
            totalBytes: 8192,
            transferredBytes: 4096,
            updatedAt: "2026-03-28T10:01:00.000Z",
            attachmentId: "video-1"
          }
        },
        uploadingAttachments: false
      })
    );

    expect(result.current.summary).toEqual({
      description:
        "2 visual items will send together as one media batch. 1 attachment is still uploading. 1 attachment is staged locally and will retry on send. 1 upload needs attention.",
      title: "3 attachments ready",
      tone: "warning"
    });
    expect(result.current.items).toHaveLength(3);
    expect(result.current.items[0]).toMatchObject({
      canMoveEarlier: false,
      canMoveLater: true,
      canRetryUpload: true,
      canTrim: false,
      metaLabel: expect.stringContaining("2048 B"),
      progress: null,
      progressLabel: null,
      statusLabel: "Retry available",
      statusTone: "warning",
      title: "Voice message",
      transferMeta: "Upload interrupted. Retry on send.",
      waveform: "waveform:audio-1"
    });
    expect(result.current.items[1]).toMatchObject({
      canMoveEarlier: true,
      canMoveLater: true,
      canRetryUpload: false,
      canTrim: false,
      dimensionLabel: "1280x720",
      imagePreviewUrl: "file:///tmp/photo.jpg",
      metaLabel: expect.stringContaining("4096 B"),
      progress: null,
      progressLabel: null,
      statusLabel: "Ready to send",
      statusTone: "success",
      title: "Photo",
      transferMeta: null,
      waveform: null
    });
    expect(result.current.items[2]).toMatchObject({
      canMoveEarlier: true,
      canMoveLater: false,
      canRetryUpload: false,
      canTrim: true,
      dimensionLabel: "1280x720",
      imagePreviewUrl: "https://example.test/video-thumb.jpg",
      metaLabel: "Video - 8192 B",
      progress: 0.5,
      progressLabel: "4096 B / 8192 B",
      statusLabel: "Uploading now",
      statusTone: "brand",
      title: "Video",
      transferMeta: null,
      waveform: null
    });
  });
});
