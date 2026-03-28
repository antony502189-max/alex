import type { MessageAttachment } from "../../types";

export function isImageAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "IMAGE" ||
    attachment.kind === "GIF" ||
    attachment.contentType.startsWith("image/")
  );
}

export function isVideoAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "VIDEO" ||
    attachment.kind === "VIDEO_NOTE" ||
    attachment.contentType.startsWith("video/")
  );
}

export function formatMediaViewerFileSize(fileSizeBytes: number) {
  if (fileSizeBytes < 1024) {
    return `${fileSizeBytes} B`;
  }
  if (fileSizeBytes < 1024 * 1024) {
    return `${(fileSizeBytes / 1024).toFixed(1)} KB`;
  }
  if (fileSizeBytes < 1024 * 1024 * 1024) {
    return `${(fileSizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(fileSizeBytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function resolveInitialMediaIndex(
  attachments: MessageAttachment[],
  initialAttachmentId: string
) {
  const resolvedIndex = attachments.findIndex(
    (attachment) => attachment.attachmentId === initialAttachmentId
  );
  return resolvedIndex >= 0 ? resolvedIndex : 0;
}

export function buildMediaViewerSubtitle(
  chatTitle: string,
  currentIndex: number,
  attachmentCount: number
) {
  return attachmentCount > 0 ? `${chatTitle} - ${currentIndex + 1}/${attachmentCount}` : chatTitle;
}

export function resolveMediaViewerCurrentUri(
  attachment: MessageAttachment | null,
  resolvedUris: Record<string, string>
) {
  if (!attachment) {
    return null;
  }

  return (
    attachment.localUri ??
    resolvedUris[attachment.attachmentId] ??
    attachment.previewUrl ??
    attachment.thumbnailUrl ??
    null
  );
}
