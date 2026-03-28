import { isPendingLocalAttachment } from "../../services/attachmentDrafts";
import type { AttachmentTransferState } from "../../store/useAttachmentTransferStore";
import type { MessageAttachment } from "../../types";

export function formatProgressPercent(progress: number) {
  const safeProgress = Number.isFinite(progress) ? Math.max(0, Math.min(1, progress)) : 0;
  return `${Math.round(safeProgress * 100)}%`;
}

export function getAttachmentTransferMeta(transfer: AttachmentTransferState | null | undefined) {
  if (!transfer) {
    return null;
  }

  if (transfer.direction === "UPLOAD") {
    if (transfer.status === "RUNNING") {
      return `Uploading ${formatProgressPercent(transfer.progress)}`;
    }
    if (transfer.status === "FAILED") {
      return "Upload interrupted. Retry on send.";
    }
    return null;
  }

  if (transfer.status === "RUNNING") {
    return `Downloading ${formatProgressPercent(transfer.progress)} - tap to pause`;
  }
  if (transfer.status === "PAUSED") {
    return `Download paused at ${formatProgressPercent(transfer.progress)} - tap to resume`;
  }
  if (transfer.status === "FAILED") {
    return "Download failed - tap to retry";
  }
  if (transfer.status === "COMPLETED") {
    return "Open local copy";
  }
  return null;
}

export function isImageAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "IMAGE" ||
    attachment.kind === "GIF" ||
    attachment.contentType.startsWith("image/")
  );
}

export function isAudioAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "VOICE" ||
    attachment.kind === "AUDIO" ||
    attachment.contentType.startsWith("audio/")
  );
}

export function isVideoAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "VIDEO" ||
    attachment.kind === "VIDEO_NOTE" ||
    attachment.contentType.startsWith("video/")
  );
}

export function isTrimEligibleAttachment(attachment: MessageAttachment) {
  return (
    (attachment.kind === "VIDEO" || attachment.kind === "VIDEO_NOTE") &&
    (attachment.durationMs ?? 0) > 1000
  );
}

export function attachmentTitle(attachment: MessageAttachment) {
  switch (attachment.kind) {
    case "VOICE":
      return "Voice message";
    case "AUDIO":
      return "Audio";
    case "VIDEO":
      return "Video";
    case "VIDEO_NOTE":
      return "Video note";
    case "GIF":
      return "GIF";
    case "IMAGE":
      return "Photo";
    default:
      return attachment.originalFileName;
  }
}

export function isQueuedUploadAttachment(attachment: MessageAttachment) {
  return isPendingLocalAttachment(attachment);
}
