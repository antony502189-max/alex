import type { AttachmentTransferState } from "../../store/useAttachmentTransferStore";
import type { MessageAttachment } from "../../types";
import {
  attachmentTitle,
  formatProgressPercent,
  getAttachmentTransferMeta,
  isAudioAttachment,
  isImageAttachment,
  isQueuedUploadAttachment,
  isTrimEligibleAttachment,
  isVideoAttachment
} from "./chatAttachmentHelpers";

const baseAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "att-1",
  contentType: "application/octet-stream",
  downloadUrl: "",
  durationMs: null,
  fileSizeBytes: 1024,
  height: null,
  kind: "FILE",
  originalFileName: "document.bin",
  previewUrl: null,
  requiresAuthorization: false,
  streamingSupported: false,
  thumbnailUrl: null,
  uploadState: "UPLOADED",
  waveform: null,
  width: null
};

describe("chatAttachmentHelpers", () => {
  it("formats transfer progress and metadata", () => {
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

    expect(formatProgressPercent(0.42)).toBe("42%");
    expect(getAttachmentTransferMeta(runningUpload)).toBe("Uploading 42%");
    expect(
      getAttachmentTransferMeta({
        ...runningUpload,
        direction: "DOWNLOAD",
        status: "PAUSED"
      })
    ).toBe("Download paused at 42% - tap to resume");
  });

  it("detects image, audio, and video attachments", () => {
    expect(isImageAttachment({ ...baseAttachment, kind: "IMAGE", contentType: "image/jpeg" })).toBe(true);
    expect(isAudioAttachment({ ...baseAttachment, kind: "VOICE", contentType: "audio/ogg" })).toBe(true);
    expect(isVideoAttachment({ ...baseAttachment, kind: "VIDEO", contentType: "video/mp4" })).toBe(true);
    expect(
      isTrimEligibleAttachment({
        ...baseAttachment,
        kind: "VIDEO_NOTE",
        contentType: "video/mp4",
        durationMs: 8_000
      })
    ).toBe(true);
    expect(
      isTrimEligibleAttachment({
        ...baseAttachment,
        kind: "VOICE",
        contentType: "audio/ogg",
        durationMs: 8_000
      })
    ).toBe(false);
  });

  it("formats attachment titles and queued uploads", () => {
    expect(attachmentTitle({ ...baseAttachment, kind: "VOICE" })).toBe("Voice message");
    expect(attachmentTitle({ ...baseAttachment, kind: "IMAGE" })).toBe("Photo");
    expect(
      isQueuedUploadAttachment({ ...baseAttachment, localUri: "file:///tmp/doc.bin", uploadState: "PENDING_UPLOAD" })
    ).toBe(true);
  });
});
