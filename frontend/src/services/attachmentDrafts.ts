import * as FileSystem from "expo-file-system/legacy";
import { attachmentTransfers } from "./attachmentTransfers";
import type { MessageAttachment } from "../types";

const QUEUED_ATTACHMENTS_DIR = "queued-attachments";

type StageAttachmentParams = {
  uri: string;
  name: string;
  contentType?: string;
  kind: "FILE" | "VOICE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF" | "VIDEO_NOTE";
  durationMs?: number;
  width?: number | null;
  height?: number | null;
  waveform?: number[] | null;
};

function sanitizeFileName(fileName: string) {
  const trimmed = fileName.trim();
  const normalized = trimmed.length > 0 ? trimmed : "attachment";
  return normalized.replace(/[^A-Za-z0-9._-]+/g, "_");
}

async function getQueuedAttachmentsDirectory() {
  const baseDirectory = FileSystem.documentDirectory ?? FileSystem.cacheDirectory ?? null;
  if (!baseDirectory) {
    throw new Error("No writable filesystem is available");
  }

  const directory = `${baseDirectory}${QUEUED_ATTACHMENTS_DIR}`;
  const info = await FileSystem.getInfoAsync(directory);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(directory, { intermediates: true });
  }
  return directory;
}

function buildLocalAttachmentId() {
  return `local:${Date.now()}:${Math.random().toString(36).slice(2, 10)}`;
}

export function isPendingLocalAttachment(attachment: MessageAttachment) {
  return attachment.uploadState === "PENDING_UPLOAD" && !!attachment.localUri;
}

export async function stageAttachment(params: StageAttachmentParams): Promise<MessageAttachment> {
  const directory = await getQueuedAttachmentsDirectory();
  const attachmentId = buildLocalAttachmentId();
  const safeName = sanitizeFileName(params.name);
  const targetUri = `${directory}/${attachmentId}-${safeName}`;

  await FileSystem.copyAsync({
    from: params.uri,
    to: targetUri
  });

  const info = await FileSystem.getInfoAsync(targetUri);
  const fileSizeBytes =
    info.exists && !info.isDirectory && typeof info.size === "number"
      ? info.size
      : 0;

  return {
    attachmentId,
    originalFileName: params.name,
    contentType: params.contentType ?? "application/octet-stream",
    kind: params.kind,
    fileSizeBytes,
    durationMs: params.durationMs ?? null,
    downloadUrl: "",
    previewUrl: params.kind === "IMAGE" || params.kind === "GIF" ? targetUri : null,
    thumbnailUrl: params.kind === "IMAGE" || params.kind === "GIF" ? targetUri : null,
    width: params.width ?? null,
    height: params.height ?? null,
    waveform: params.waveform ?? null,
    accessExpiresAt: null,
    requiresAuthorization: false,
    streamingSupported: params.kind === "VOICE" || params.kind === "AUDIO" || params.kind === "VIDEO" || params.kind === "VIDEO_NOTE",
    localUri: targetUri,
    uploadState: "PENDING_UPLOAD"
  };
}

export async function cleanupStagedAttachment(attachment: MessageAttachment) {
  if (!attachment.localUri) {
    return;
  }

  const info = await FileSystem.getInfoAsync(attachment.localUri);
  if (!info.exists) {
    return;
  }

  await FileSystem.deleteAsync(attachment.localUri, { idempotent: true });
}

export async function uploadPendingAttachment(
  token: string,
  chatId: string,
  attachment: MessageAttachment
) {
  if (!isPendingLocalAttachment(attachment)) {
    return attachment;
  }

  const uploadedAttachment = await attachmentTransfers.uploadPendingAttachment(
    token,
    chatId,
    attachment
  );

  await cleanupStagedAttachment(attachment).catch(() => undefined);
  return uploadedAttachment;
}
