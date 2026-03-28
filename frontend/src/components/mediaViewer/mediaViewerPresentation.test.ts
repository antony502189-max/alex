import type { MessageAttachment } from "../../types";
import {
  buildMediaViewerSubtitle,
  formatMediaViewerFileSize,
  isImageAttachment,
  isVideoAttachment,
  resolveInitialMediaIndex,
  resolveMediaViewerCurrentUri
} from "./mediaViewerPresentation";

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

describe("mediaViewerPresentation", () => {
  it("detects attachment kinds and formats metadata", () => {
    expect(isImageAttachment(createAttachment())).toBe(true);
    expect(isVideoAttachment(createAttachment({ kind: "VIDEO", contentType: "video/mp4" }))).toBe(true);
    expect(isVideoAttachment(createAttachment({ kind: "FILE", contentType: "application/pdf" }))).toBe(false);
    expect(formatMediaViewerFileSize(512)).toBe("512 B");
    expect(formatMediaViewerFileSize(2048)).toBe("2.0 KB");
    expect(buildMediaViewerSubtitle("Team", 1, 3)).toBe("Team - 2/3");
  });

  it("resolves initial index and current uri priority", () => {
    const attachments = [
      createAttachment({ attachmentId: "attachment-1" }),
      createAttachment({ attachmentId: "attachment-2", localUri: "file:///local.jpg" })
    ];

    expect(resolveInitialMediaIndex(attachments, "attachment-2")).toBe(1);
    expect(resolveInitialMediaIndex(attachments, "missing")).toBe(0);
    expect(
      resolveMediaViewerCurrentUri(attachments[1], { "attachment-2": "file:///resolved.jpg" })
    ).toBe("file:///local.jpg");
    expect(
      resolveMediaViewerCurrentUri(
        createAttachment({
          attachmentId: "attachment-3",
          localUri: null,
          previewUrl: null,
          thumbnailUrl: "https://cdn.example/thumb.jpg"
        }),
        { "attachment-3": "file:///resolved.jpg" }
      )
    ).toBe("file:///resolved.jpg");
  });
});
