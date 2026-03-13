import * as FileSystem from "expo-file-system/legacy";
import { api } from "./api";
import { generateClientMessageId } from "./clientMessageIds";
import { secretChatCrypto } from "./secretChatCrypto";
import type {
  SecretAttachmentUpload,
  SecretChatAttachment,
  SecretChatSummary
} from "../types";

type PrepareSecretAttachmentParams = {
  kind: "FILE" | "IMAGE" | "VOICE" | "VIDEO";
  uri: string;
  name: string;
  contentType?: string;
  durationMs?: number | null;
};

export type PreparedSecretAttachmentUpload = {
  encryptedUploadUri: string;
  kind: "FILE" | "IMAGE" | "VOICE" | "VIDEO";
  originalFileName: string;
  contentType: string;
  fileSizeBytes: number;
  fileNonce: string;
  durationMs: number | null;
  previewUri: string | null;
};

export type PendingSecretAttachmentDraft = SecretChatAttachment & {
  previewUri: string | null;
};

const SECRET_ENCRYPTED_UPLOADS_DIR = "secret-chat-uploads";
const SECRET_DECRYPTED_ATTACHMENTS_DIR = "secret-chat-attachments";

function sanitizeFileName(fileName: string) {
  const trimmed = fileName.trim();
  const normalized = trimmed.length > 0 ? trimmed : "attachment";
  return normalized.replace(/[^A-Za-z0-9._-]+/g, "_");
}

async function ensureDirectory(directoryName: string) {
  const baseDirectory = FileSystem.cacheDirectory ?? FileSystem.documentDirectory ?? null;
  if (!baseDirectory) {
    throw new Error("No writable filesystem is available");
  }

  const directory = `${baseDirectory}${directoryName}`;
  const info = await FileSystem.getInfoAsync(directory);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(directory, { intermediates: true });
  }
  return directory;
}

function extensionFromFileName(fileName: string) {
  const dotIndex = fileName.lastIndexOf(".");
  if (dotIndex < 0 || dotIndex === fileName.length - 1) {
    return "";
  }
  return fileName.slice(dotIndex);
}

export async function prepareSecretAttachmentUpload(
  summary: SecretChatSummary,
  params: PrepareSecretAttachmentParams
): Promise<PreparedSecretAttachmentUpload> {
  const uploadDirectory = await ensureDirectory(SECRET_ENCRYPTED_UPLOADS_DIR);
  const safeFileName = sanitizeFileName(params.name);
  const tempId = generateClientMessageId();
  const encryptedUploadUri = `${uploadDirectory}/${tempId}-${safeFileName}.enc`;

  const info = await FileSystem.getInfoAsync(params.uri);
  const fileSizeBytes =
    info.exists && !info.isDirectory && typeof info.size === "number"
      ? info.size
      : 0;
  const { fileNonce } = await secretChatCrypto.encryptAttachmentToFile(
    summary,
    params.uri,
    encryptedUploadUri
  );

  return {
    encryptedUploadUri,
    kind: params.kind,
    originalFileName: params.name,
    contentType: params.contentType ?? "application/octet-stream",
    fileSizeBytes,
    fileNonce,
    durationMs:
      typeof params.durationMs === "number" && Number.isFinite(params.durationMs)
        ? Math.max(1, Math.round(params.durationMs))
        : null,
    previewUri: params.kind === "IMAGE" ? params.uri : null
  };
}

export async function uploadPreparedSecretAttachment(
  token: string,
  secretChatId: string,
  preparedAttachment: PreparedSecretAttachmentUpload
): Promise<PendingSecretAttachmentDraft> {
  const uploadedAttachment: SecretAttachmentUpload = await api.uploadSecretChatAttachment(token, secretChatId, {
    uri: preparedAttachment.encryptedUploadUri,
    name: `${sanitizeFileName(preparedAttachment.originalFileName)}.enc`,
    type: "application/octet-stream",
    kind: preparedAttachment.kind
  });

  return {
    attachmentId: uploadedAttachment.attachmentId,
    kind: preparedAttachment.kind,
    originalFileName: preparedAttachment.originalFileName,
    contentType: preparedAttachment.contentType,
    fileSizeBytes: preparedAttachment.fileSizeBytes,
    fileNonce: preparedAttachment.fileNonce,
    durationMs: preparedAttachment.durationMs,
    previewUri: preparedAttachment.previewUri
  };
}

export async function cleanupPreparedSecretAttachment(preparedAttachment: PreparedSecretAttachmentUpload) {
  const info = await FileSystem.getInfoAsync(preparedAttachment.encryptedUploadUri);
  if (!info.exists) {
    return;
  }
  await FileSystem.deleteAsync(preparedAttachment.encryptedUploadUri, { idempotent: true });
}

export async function removePendingSecretAttachment(
  token: string,
  attachment: PendingSecretAttachmentDraft
) {
  await api.removeSecretChatAttachment(token, attachment.attachmentId);
}

async function buildDecryptedAttachmentUri(attachment: SecretChatAttachment) {
  const decryptedDirectory = await ensureDirectory(SECRET_DECRYPTED_ATTACHMENTS_DIR);
  const safeFileName = sanitizeFileName(attachment.originalFileName);
  return `${decryptedDirectory}/${attachment.attachmentId}-${safeFileName}`;
}

export async function ensureDecryptedSecretAttachment(
  token: string,
  summary: SecretChatSummary,
  attachment: SecretChatAttachment
) {
  const targetUri = await buildDecryptedAttachmentUri(attachment);
  const cachedInfo = await FileSystem.getInfoAsync(targetUri);
  if (cachedInfo.exists) {
    return targetUri;
  }

  const access = await api.getSecretChatAttachmentAccess(token, attachment.attachmentId);
  const downloadUrl = access.downloadUrl.startsWith("http")
    ? access.downloadUrl
    : access.downloadUrl;
  const encryptedDirectory = await ensureDirectory(SECRET_ENCRYPTED_UPLOADS_DIR);
  const encryptedFileUri = `${encryptedDirectory}/${attachment.attachmentId}.enc`;
  await FileSystem.downloadAsync(
    downloadUrl,
    encryptedFileUri,
    access.requiresAuthorization
      ? {
          headers: {
            Authorization: `Bearer ${token}`
          }
        }
      : undefined
  );

  try {
    await secretChatCrypto.decryptAttachmentToFile(
      summary,
      encryptedFileUri,
      targetUri,
      attachment.fileNonce
    );
  } finally {
    await FileSystem.deleteAsync(encryptedFileUri, { idempotent: true }).catch(() => undefined);
  }

  return targetUri;
}

export async function getCachedSecretAttachmentUri(attachment: SecretChatAttachment) {
  const targetUri = await buildDecryptedAttachmentUri(attachment);
  const info = await FileSystem.getInfoAsync(targetUri);
  return info.exists ? targetUri : null;
}

export async function clearCachedSecretAttachment(attachment: SecretChatAttachment) {
  const targetUri = await buildDecryptedAttachmentUri(attachment);
  const info = await FileSystem.getInfoAsync(targetUri);
  if (!info.exists) {
    return;
  }
  await FileSystem.deleteAsync(targetUri, { idempotent: true });
}

export async function clearAllSecretAttachmentCache() {
  const directories = [SECRET_ENCRYPTED_UPLOADS_DIR, SECRET_DECRYPTED_ATTACHMENTS_DIR];
  for (const directoryName of directories) {
    try {
      const directory = await ensureDirectory(directoryName);
      await FileSystem.deleteAsync(directory, { idempotent: true });
    } catch {
      continue;
    }
  }
}

export function guessSecretAttachmentExtension(attachment: SecretChatAttachment) {
  return extensionFromFileName(attachment.originalFileName);
}
