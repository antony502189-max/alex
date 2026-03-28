import { resolveAttachmentPreviewUri } from "./attachmentPreviews";
import type { MessageAttachment } from "../types";

function createAttachment(overrides: Partial<MessageAttachment> = {}): MessageAttachment {
  return {
    accessExpiresAt: null,
    attachmentId: "attachment-1",
    contentType: "image/jpeg",
    downloadUrl: "",
    durationMs: null,
    fileSizeBytes: 2048,
    height: 720,
    kind: "IMAGE",
    localUri: null,
    originalFileName: "photo.jpg",
    previewUrl: null,
    requiresAuthorization: false,
    streamingSupported: false,
    thumbnailUrl: null,
    uploadState: "UPLOADED",
    waveform: null,
    width: 1280,
    ...overrides
  };
}

describe("resolveAttachmentPreviewUri", () => {
  it("prefers local image URIs before remote preview assets", () => {
    expect(
      resolveAttachmentPreviewUri(
        createAttachment({
          localUri: "file:///tmp/photo.jpg",
          previewUrl: "https://example.test/preview.jpg",
          thumbnailUrl: "https://example.test/thumb.jpg"
        })
      )
    ).toBe("file:///tmp/photo.jpg");
  });

  it("uses remote preview or thumbnail for video attachments instead of a local file URI", () => {
    expect(
      resolveAttachmentPreviewUri(
        createAttachment({
          contentType: "video/mp4",
          kind: "VIDEO",
          localUri: "file:///tmp/clip.mp4",
          originalFileName: "clip.mp4",
          thumbnailUrl: "https://example.test/video-thumb.jpg"
        })
      )
    ).toBe("https://example.test/video-thumb.jpg");
  });
});
