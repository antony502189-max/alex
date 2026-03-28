import type { MessageAttachment } from "../types";

function isImageLikeAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "IMAGE" ||
    attachment.kind === "GIF" ||
    attachment.contentType.startsWith("image/")
  );
}

export function resolveAttachmentPreviewUri(attachment: MessageAttachment) {
  if (isImageLikeAttachment(attachment)) {
    return (
      attachment.localUri ??
      attachment.previewUrl ??
      attachment.thumbnailUrl ??
      null
    );
  }

  return attachment.previewUrl ?? attachment.thumbnailUrl ?? null;
}
