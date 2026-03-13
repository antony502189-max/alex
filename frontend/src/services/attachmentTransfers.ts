import * as FileSystem from "expo-file-system/legacy";
import { API_BASE_URL } from "../config/env";
import { api } from "./api";
import { useAttachmentTransferStore } from "../store/useAttachmentTransferStore";
import type { MessageAttachment } from "../types";

type ActiveDownloadEntry = {
  resumable: FileSystem.DownloadResumable;
  localUri: string;
  paused: boolean;
};

const DOWNLOADS_DIRECTORY = "downloads";
const activeDownloads = new Map<string, ActiveDownloadEntry>();
const activeUploads = new Map<string, Promise<MessageAttachment>>();

function clampProgress(transferredBytes: number, totalBytes: number | null) {
  if (!totalBytes || totalBytes <= 0) {
    return 0;
  }
  return Math.max(0, Math.min(1, transferredBytes / totalBytes));
}

function sanitizeFileName(fileName: string) {
  const trimmed = fileName.trim();
  const normalized = trimmed.length > 0 ? trimmed : "attachment";
  return normalized.replace(/[^A-Za-z0-9._-]+/g, "_");
}

async function ensureDownloadsDirectory() {
  const baseDirectory = FileSystem.documentDirectory ?? FileSystem.cacheDirectory ?? null;
  if (!baseDirectory) {
    throw new Error("No writable filesystem is available");
  }

  const directory = `${baseDirectory}${DOWNLOADS_DIRECTORY}`;
  const info = await FileSystem.getInfoAsync(directory);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(directory, { intermediates: true });
  }
  return directory;
}

async function resolveDownloadTargetUri(attachment: MessageAttachment) {
  const directory = await ensureDownloadsDirectory();
  return `${directory}/${attachment.attachmentId}-${sanitizeFileName(attachment.originalFileName)}`;
}

export const attachmentTransfers = {
  async uploadPendingAttachment(
    token: string,
    chatId: string,
    attachment: MessageAttachment
  ) {
    const inFlightUpload = activeUploads.get(attachment.attachmentId);
    if (inFlightUpload) {
      return inFlightUpload;
    }

    const uploadPromise = (async () => {
    const existingTransfer =
      useAttachmentTransferStore.getState().transfers[attachment.attachmentId];

    useAttachmentTransferStore.getState().upsertTransfer(attachment.attachmentId, {
      direction: "UPLOAD",
      status: "RUNNING",
      progress: clampProgress(0, attachment.fileSizeBytes),
      transferredBytes: 0,
      totalBytes: attachment.fileSizeBytes,
      sessionId:
        existingTransfer?.direction === "UPLOAD" ? existingTransfer.sessionId : null,
      localUri: attachment.localUri ?? null,
      error: null
    });

    try {
      const uploadedAttachment = await api.uploadAttachment(
        token,
        chatId,
        {
          uri: attachment.localUri ?? "",
          name: attachment.originalFileName,
          type: attachment.contentType,
          kind: attachment.kind,
          durationMs: attachment.durationMs ?? undefined,
          width: attachment.width ?? undefined,
          height: attachment.height ?? undefined,
          waveform: attachment.waveform ?? undefined
        },
        {
          existingSessionId:
            existingTransfer?.direction === "UPLOAD" ? existingTransfer.sessionId : null,
          onSessionId: (sessionId) => {
            useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
              sessionId
            });
          },
          onProgress: ({ transferredBytes, totalBytes }) => {
            useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
              status: "RUNNING",
              transferredBytes,
              totalBytes,
              progress: clampProgress(transferredBytes, totalBytes),
              error: null
            });
          }
        }
      );

      useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
        status: "COMPLETED",
        transferredBytes: attachment.fileSizeBytes,
        totalBytes: attachment.fileSizeBytes,
        progress: 1,
        sessionId: null,
        error: null
      });

      return uploadedAttachment;
    } catch (error) {
      useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
        status: "FAILED",
        error: error instanceof Error ? error.message : "Unable to upload attachment"
      });
      throw error;
    }
    })();

    activeUploads.set(attachment.attachmentId, uploadPromise);
    try {
      return await uploadPromise;
    } finally {
      activeUploads.delete(attachment.attachmentId);
    }
  },

  async downloadAttachment(token: string, attachment: MessageAttachment) {
    const transfer = useAttachmentTransferStore.getState().transfers[attachment.attachmentId];
    const existingDownload = activeDownloads.get(attachment.attachmentId);

    if (existingDownload) {
      if (existingDownload.paused) {
        useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
          status: "RUNNING",
          error: null
        });
        const resumed = await existingDownload.resumable.resumeAsync();
        if (!resumed?.uri) {
          throw new Error("Unable to resume attachment download");
        }
        existingDownload.paused = false;
        useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
          status: "COMPLETED",
          progress: 1,
          transferredBytes: attachment.fileSizeBytes,
          totalBytes: attachment.fileSizeBytes,
          localUri: resumed.uri,
          error: null
        });
        activeDownloads.delete(attachment.attachmentId);
        return resumed.uri;
      }

      return null;
    }

    if (
      transfer?.direction === "DOWNLOAD" &&
      transfer.status === "COMPLETED" &&
      transfer.localUri
    ) {
      const info = await FileSystem.getInfoAsync(transfer.localUri);
      if (info.exists) {
        return transfer.localUri;
      }
    }

    const access = await api.getAttachmentAccess(token, attachment.attachmentId);
    const downloadUrl = access.downloadUrl.startsWith("http")
      ? access.downloadUrl
      : `${API_BASE_URL}${access.downloadUrl.replace(/^\/api/, "")}`;
    const activeToken = token;
    const localUri = await resolveDownloadTargetUri(attachment);
    const totalBytes = attachment.fileSizeBytes || null;

    const resumable = FileSystem.createDownloadResumable(
      downloadUrl,
      localUri,
      access.requiresAuthorization && activeToken
        ? {
            headers: {
              Authorization: `Bearer ${activeToken}`
            }
          }
        : undefined,
      (progressEvent) => {
        useAttachmentTransferStore.getState().upsertTransfer(attachment.attachmentId, {
          direction: "DOWNLOAD",
          status: "RUNNING",
          progress: clampProgress(
            progressEvent.totalBytesWritten,
            progressEvent.totalBytesExpectedToWrite > 0
              ? progressEvent.totalBytesExpectedToWrite
              : totalBytes
          ),
          transferredBytes: progressEvent.totalBytesWritten,
          totalBytes:
            progressEvent.totalBytesExpectedToWrite > 0
              ? progressEvent.totalBytesExpectedToWrite
              : totalBytes,
          sessionId: null,
          localUri,
          error: null
        });
      }
    );

    activeDownloads.set(attachment.attachmentId, {
      resumable,
      localUri,
      paused: false
    });
    useAttachmentTransferStore.getState().upsertTransfer(attachment.attachmentId, {
      direction: "DOWNLOAD",
      status: "RUNNING",
      progress: 0,
      transferredBytes: 0,
      totalBytes,
      sessionId: null,
      localUri,
      error: null
    });

    try {
      const result = await resumable.downloadAsync();
      if (!result?.uri) {
        throw new Error("Unable to download attachment");
      }
      useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
        status: "COMPLETED",
        progress: 1,
        transferredBytes: totalBytes ?? 0,
        totalBytes,
        localUri: result.uri,
        error: null
      });
      activeDownloads.delete(attachment.attachmentId);
      return result.uri;
    } catch (error) {
      activeDownloads.delete(attachment.attachmentId);
      useAttachmentTransferStore.getState().patchTransfer(attachment.attachmentId, {
        status: "FAILED",
        error: error instanceof Error ? error.message : "Unable to download attachment"
      });
      throw error;
    }
  },

  async pauseDownload(attachmentId: string) {
    const activeDownload = activeDownloads.get(attachmentId);
    if (!activeDownload || activeDownload.paused) {
      return;
    }

    await activeDownload.resumable.pauseAsync();
    activeDownload.paused = true;
    useAttachmentTransferStore.getState().patchTransfer(attachmentId, {
      status: "PAUSED"
    });
  }
};
