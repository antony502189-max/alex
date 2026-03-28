import { useMemo } from "react";
import type {
  PendingAttachmentBarItem,
  PendingAttachmentBarSummary
} from "./PendingAttachmentBar";
import { resolveAttachmentPreviewUri } from "../../services/attachmentPreviews";
import type { AttachmentTransferState } from "../../store/useAttachmentTransferStore";
import type { MessageAttachment } from "../../types";

type UseChatPendingAttachmentItemsParams = {
  attachmentTitle: (attachment: MessageAttachment) => string;
  formatDuration: (durationMs: number | null | undefined) => string;
  formatFileSize: (fileSizeBytes: number) => string;
  getAttachmentTransferMeta: (attachment: MessageAttachment) => string | null;
  isAudioAttachment: (attachment: MessageAttachment) => boolean;
  isImageAttachment: (attachment: MessageAttachment) => boolean;
  isQueuedUploadAttachment: (attachment: MessageAttachment) => boolean;
  isTrimEligibleAttachment: (attachment: MessageAttachment) => boolean;
  isVideoAttachment: (attachment: MessageAttachment) => boolean;
  pendingAttachments: MessageAttachment[];
  renderWaveform: (attachment: MessageAttachment, color: string) => React.ReactNode;
  uploadingAttachments: boolean;
  transferStates: Record<string, AttachmentTransferState | undefined>;
};

export function useChatPendingAttachmentItems({
  attachmentTitle,
  formatDuration,
  formatFileSize,
  getAttachmentTransferMeta,
  isAudioAttachment,
  isImageAttachment,
  isQueuedUploadAttachment,
  isTrimEligibleAttachment,
  isVideoAttachment,
  pendingAttachments,
  renderWaveform,
  transferStates,
  uploadingAttachments
}: UseChatPendingAttachmentItemsParams) {
  return useMemo<{
    items: PendingAttachmentBarItem[];
    summary: PendingAttachmentBarSummary;
  }>(() => {
    const visualAttachmentCount = pendingAttachments.filter(
      (attachment) => isImageAttachment(attachment) || isVideoAttachment(attachment)
    ).length;
    const queuedAttachmentCount = pendingAttachments.filter((attachment) =>
      isQueuedUploadAttachment(attachment)
    ).length;
    const runningUploadCount = pendingAttachments.filter((attachment) => {
      const transfer = transferStates[attachment.attachmentId];
      return transfer?.direction === "UPLOAD" && transfer.status === "RUNNING";
    }).length;
    const failedUploadCount = pendingAttachments.filter((attachment) => {
      const transfer = transferStates[attachment.attachmentId];
      return transfer?.direction === "UPLOAD" && transfer.status === "FAILED";
    }).length;

    const summaryParts: string[] = [];
    if (visualAttachmentCount > 1) {
      summaryParts.push(`${visualAttachmentCount} visual items will send together as one media batch.`);
    } else if (pendingAttachments.length > 1) {
      summaryParts.push(`${pendingAttachments.length} attachments are queued for the next message.`);
    }
    if (runningUploadCount > 0) {
      summaryParts.push(
        runningUploadCount === 1
          ? "1 attachment is still uploading."
          : `${runningUploadCount} attachments are still uploading.`
      );
    }
    if (queuedAttachmentCount > 0) {
      summaryParts.push(
        queuedAttachmentCount === 1
          ? "1 attachment is staged locally and will retry on send."
          : `${queuedAttachmentCount} attachments are staged locally and will retry on send.`
      );
    }
    if (failedUploadCount > 0) {
      summaryParts.push(
        failedUploadCount === 1
          ? "1 upload needs attention."
          : `${failedUploadCount} uploads need attention.`
      );
    }

    const summary: PendingAttachmentBarSummary =
      pendingAttachments.length === 0
        ? null
        : {
            description:
              summaryParts.join(" ") ||
              "Everything below is ready to send with the next message.",
            title:
              pendingAttachments.length === 1
                ? "1 attachment ready"
                : `${pendingAttachments.length} attachments ready`,
            tone:
              failedUploadCount > 0 || queuedAttachmentCount > 0
                ? "warning"
                : runningUploadCount > 0
                  ? "brand"
                  : "success"
          };

    const items = pendingAttachments.map((attachment, index) => {
      const transfer = transferStates[attachment.attachmentId];
      const canRetryUpload =
        isQueuedUploadAttachment(attachment) &&
        !uploadingAttachments &&
        (!transfer || transfer.direction !== "UPLOAD" || transfer.status === "FAILED");
      const hasVisualPreview = isImageAttachment(attachment) || isVideoAttachment(attachment);
      const uploadProgress =
        transfer?.direction === "UPLOAD" && transfer.status === "RUNNING"
          ? transfer.progress
          : null;
      const uploadProgressLabel =
        transfer?.direction === "UPLOAD" && transfer.status === "RUNNING"
          ? transfer.totalBytes && transfer.totalBytes > 0
            ? `${formatFileSize(transfer.transferredBytes)} / ${formatFileSize(transfer.totalBytes)}`
            : "Uploading"
          : null;
      const statusLabel =
        transfer?.direction === "UPLOAD" && transfer.status === "RUNNING"
          ? "Uploading now"
          : transfer?.direction === "UPLOAD" && transfer.status === "FAILED"
            ? "Retry available"
            : isQueuedUploadAttachment(attachment)
              ? "Stored locally"
              : "Ready to send";
      const statusTone =
        transfer?.direction === "UPLOAD" && transfer.status === "RUNNING"
          ? ("brand" as const)
          : transfer?.direction === "UPLOAD" && transfer.status === "FAILED"
            ? ("warning" as const)
            : isQueuedUploadAttachment(attachment)
              ? ("warning" as const)
              : ("success" as const);

      return {
        attachment,
        canMoveEarlier: index > 0,
        canMoveLater: index < pendingAttachments.length - 1,
        canRetryUpload,
        canTrim: isTrimEligibleAttachment(attachment) && !isQueuedUploadAttachment(attachment),
        dimensionLabel:
          attachment.width && attachment.height && hasVisualPreview
            ? `${attachment.width}x${attachment.height}`
            : null,
        imagePreviewUrl: hasVisualPreview ? resolveAttachmentPreviewUri(attachment) : null,
        metaLabel: isAudioAttachment(attachment)
          ? `${formatDuration(attachment.durationMs)} - ${formatFileSize(attachment.fileSizeBytes)}`
          : hasVisualPreview
            ? `${attachmentTitle(attachment)} - ${formatFileSize(attachment.fileSizeBytes)}`
            : formatFileSize(attachment.fileSizeBytes),
        progress: uploadProgress,
        progressLabel: uploadProgressLabel,
        statusLabel,
        statusTone,
        title: attachmentTitle(attachment),
        transferMeta: getAttachmentTransferMeta(attachment),
        waveform: isAudioAttachment(attachment) ? renderWaveform(attachment, "#166534") : null
      };
    });

    return {
      items,
      summary
    };
  }, [
      attachmentTitle,
      formatDuration,
      formatFileSize,
      getAttachmentTransferMeta,
      isAudioAttachment,
      isImageAttachment,
      isQueuedUploadAttachment,
      isTrimEligibleAttachment,
      isVideoAttachment,
      pendingAttachments,
      renderWaveform,
      transferStates,
      uploadingAttachments
    ]);
}
