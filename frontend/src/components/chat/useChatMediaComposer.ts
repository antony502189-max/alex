import { useCallback, useState } from "react";
import * as DocumentPicker from "expo-document-picker";
import { Image } from "react-native";
import { api } from "../../services/api";
import {
  cleanupStagedAttachment,
  stageAttachment,
  uploadPendingAttachment
} from "../../services/attachmentDrafts";
import {
  captureChatPhoto,
  captureChatVideo,
  pickChatLibraryMedia,
  type PickedMediaFile
} from "../../services/imagePicker";
import { messageOutbox } from "../../services/messageOutbox";
import type { MessageAttachment } from "../../types";
import {
  isQueuedUploadAttachment,
  isTrimEligibleAttachment
} from "./chatAttachmentHelpers";

type UploadOrStageAttachmentParams = {
  uri: string;
  name: string;
  contentType?: string;
  kind: "FILE" | "VOICE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF" | "VIDEO_NOTE";
  durationMs?: number;
  width?: number | null;
  height?: number | null;
  waveform?: number[] | null;
};

type PickDocumentKind = "FILE" | "IMAGE" | "VIDEO" | "AUDIO" | "GIF";

type UseChatMediaComposerParams = {
  canPost: boolean;
  chatId: string;
  editingMessageId: string | null;
  recordingVoice: boolean;
  setError: (value: string | null) => void;
  setPendingAttachments: React.Dispatch<React.SetStateAction<MessageAttachment[]>>;
  setShowGifPicker: React.Dispatch<React.SetStateAction<boolean>>;
  setUploadingAttachments: React.Dispatch<React.SetStateAction<boolean>>;
  token: string;
  uploadingAttachments: boolean;
};

export class PendingAttachmentUploadError extends Error {
  constructor(
    message: string,
    public readonly attachments: MessageAttachment[],
    public readonly cause: unknown
  ) {
    super(message);
  }
}

function loadImageDimensions(uri: string): Promise<{ width: number; height: number } | null> {
  return new Promise((resolve) => {
    Image.getSize(
      uri,
      (width, height) => resolve({ width, height }),
      () => resolve(null)
    );
  });
}

export function useChatMediaComposer({
  canPost,
  chatId,
  editingMessageId,
  recordingVoice,
  setError,
  setPendingAttachments,
  setShowGifPicker,
  setUploadingAttachments,
  token,
  uploadingAttachments
}: UseChatMediaComposerParams) {
  const [trimmingAttachmentId, setTrimmingAttachmentId] = useState<string | null>(null);

  const uploadOrStageAttachment = useCallback(async (params: UploadOrStageAttachmentParams) => {
    try {
      return await api.uploadAttachment(token, chatId, {
        uri: params.uri,
        name: params.name,
        type: params.contentType,
        kind: params.kind,
        durationMs: params.durationMs,
        width: params.width ?? undefined,
        height: params.height ?? undefined,
        waveform: params.waveform ?? undefined
      });
    } catch (uploadError) {
      if (!messageOutbox.isRetryable(uploadError)) {
        throw uploadError;
      }

      return stageAttachment({
        uri: params.uri,
        name: params.name,
        contentType: params.contentType,
        kind: params.kind,
        durationMs: params.durationMs,
        width: params.width ?? null,
        height: params.height ?? null,
        waveform: params.waveform ?? null
      });
    }
  }, [chatId, token]);

  const resolvePendingAttachmentsForSend = useCallback(async (attachments: MessageAttachment[]) => {
    const resolvedAttachments: MessageAttachment[] = [];

    for (let index = 0; index < attachments.length; index += 1) {
      const attachment = attachments[index];
      if (!isQueuedUploadAttachment(attachment)) {
        resolvedAttachments.push(attachment);
        continue;
      }

      try {
        const uploadedAttachment = await uploadPendingAttachment(token, chatId, attachment);
        resolvedAttachments.push(uploadedAttachment);
      } catch (uploadError) {
        throw new PendingAttachmentUploadError(
          "Unable to upload queued attachment",
          [...resolvedAttachments, attachment, ...attachments.slice(index + 1)],
          uploadError
        );
      }
    }

    return resolvedAttachments;
  }, [chatId, token]);

  const removePendingAttachment = useCallback(async (attachment: MessageAttachment) => {
    if (isQueuedUploadAttachment(attachment)) {
      await cleanupStagedAttachment(attachment).catch(() => undefined);
    }
    setPendingAttachments((current) =>
      current.filter((item) => item.attachmentId !== attachment.attachmentId)
    );
  }, [setPendingAttachments]);

  const movePendingAttachment = useCallback((
    attachmentId: string,
    direction: "EARLIER" | "LATER"
  ) => {
    setPendingAttachments((current) => {
      const currentIndex = current.findIndex((item) => item.attachmentId === attachmentId);
      if (currentIndex < 0) {
        return current;
      }

      const targetIndex = direction === "EARLIER" ? currentIndex - 1 : currentIndex + 1;
      if (targetIndex < 0 || targetIndex >= current.length) {
        return current;
      }

      const next = [...current];
      [next[currentIndex], next[targetIndex]] = [next[targetIndex], next[currentIndex]];
      return next;
    });
  }, [setPendingAttachments]);

  const retryPendingAttachmentUpload = useCallback(async (attachment: MessageAttachment) => {
    if (!isQueuedUploadAttachment(attachment)) {
      return;
    }

    setUploadingAttachments(true);
    setError(null);
    try {
      const uploadedAttachment = await uploadPendingAttachment(token, chatId, attachment);
      setPendingAttachments((current) =>
        current.map((item) =>
          item.attachmentId === attachment.attachmentId ? uploadedAttachment : item
        )
      );
    } catch (retryError) {
      setError(
        retryError instanceof Error
          ? retryError.message
          : "Unable to retry attachment upload"
      );
    } finally {
      setUploadingAttachments(false);
    }
  }, [chatId, setError, setPendingAttachments, setUploadingAttachments, token]);

  const trimPendingAttachment = useCallback(async (
    attachment: MessageAttachment,
    startMs: number,
    endMs: number
  ) => {
    if (isQueuedUploadAttachment(attachment) || !isTrimEligibleAttachment(attachment)) {
      return false;
    }

    setTrimmingAttachmentId(attachment.attachmentId);
    setError(null);
    try {
      const trimmedAttachment = await api.trimAttachment(token, attachment.attachmentId, {
        startMs,
        endMs
      });
      setPendingAttachments((current) =>
        current.map((item) =>
          item.attachmentId === attachment.attachmentId ? trimmedAttachment : item
        )
      );
      return true;
    } catch (trimError) {
      setError(trimError instanceof Error ? trimError.message : "Unable to trim attachment");
      return false;
    } finally {
      setTrimmingAttachmentId(null);
    }
  }, [setError, setPendingAttachments, token]);

  const addPickedMediaFiles = useCallback(async (
    kind: "IMAGE" | "VIDEO",
    assets: PickedMediaFile[]
  ) => {
    if (assets.length === 0) {
      return;
    }

    setUploadingAttachments(true);
    setError(null);
    try {
      const uploaded: MessageAttachment[] = [];
      let stagedCount = 0;
      for (const asset of assets) {
        const attachment = await uploadOrStageAttachment({
          uri: asset.uri,
          name: asset.name ?? (kind === "VIDEO" ? "video.mp4" : "photo.jpg"),
          contentType: asset.type ?? (kind === "VIDEO" ? "video/mp4" : "image/jpeg"),
          width: asset.width ?? null,
          height: asset.height ?? null,
          kind
        });
        if (isQueuedUploadAttachment(attachment)) {
          stagedCount += 1;
        }
        uploaded.push(attachment);
      }
      setPendingAttachments((current) => [...current, ...uploaded]);
      if (stagedCount > 0) {
        setError("No connection. Attachment upload queued until send.");
      }
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Unable to upload attachment");
    } finally {
      setUploadingAttachments(false);
    }
  }, [setError, setPendingAttachments, setUploadingAttachments, uploadOrStageAttachment]);

  const handlePickDocuments = useCallback(async (kind: PickDocumentKind, type: string) => {
    if (!canPost || uploadingAttachments || editingMessageId || recordingVoice) {
      return;
    }
    setError(null);
    const selection = await DocumentPicker.getDocumentAsync({
      multiple: true,
      copyToCacheDirectory: true,
      type
    });
    if (selection.canceled) {
      return;
    }
    setUploadingAttachments(true);
    try {
      const uploaded: MessageAttachment[] = [];
      let stagedCount = 0;
      for (const asset of selection.assets) {
        const contentType =
          asset.mimeType ??
          (
            kind === "IMAGE"
              ? "image/jpeg"
              : kind === "VIDEO"
                ? "video/mp4"
                : kind === "AUDIO"
                  ? "audio/mpeg"
                  : kind === "GIF"
                    ? "image/gif"
                    : undefined
          );
        const dimensions =
          kind === "IMAGE" || kind === "GIF"
            ? await loadImageDimensions(asset.uri)
            : null;
        const attachment = await uploadOrStageAttachment({
          uri: asset.uri,
          name:
            asset.name ??
            (
              kind === "IMAGE"
                ? "photo.jpg"
                : kind === "VIDEO"
                  ? "video.mp4"
                  : kind === "AUDIO"
                    ? "audio.m4a"
                    : kind === "GIF"
                      ? "animation.gif"
                      : "attachment"
            ),
          contentType,
          width: dimensions?.width ?? null,
          height: dimensions?.height ?? null,
          kind
        });
        if (isQueuedUploadAttachment(attachment)) {
          stagedCount += 1;
        }
        uploaded.push(attachment);
      }
      setPendingAttachments((current) => [...current, ...uploaded]);
      if (stagedCount > 0) {
        setError("No connection. Attachment upload queued until send.");
      }
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Unable to upload attachment");
    } finally {
      setUploadingAttachments(false);
    }
  }, [
    canPost,
    editingMessageId,
    recordingVoice,
    setError,
    setPendingAttachments,
    setUploadingAttachments,
    uploadOrStageAttachment,
    uploadingAttachments
  ]);

  const handlePickAttachments = useCallback(async () => {
    await handlePickDocuments("FILE", "*/*");
  }, [handlePickDocuments]);

  const handleCapturePhoto = useCallback(async () => {
    if (!canPost || uploadingAttachments || editingMessageId || recordingVoice) {
      return;
    }

    const file = await captureChatPhoto();
    if (!file) {
      return;
    }
    await addPickedMediaFiles("IMAGE", [file]);
  }, [addPickedMediaFiles, canPost, editingMessageId, recordingVoice, uploadingAttachments]);

  const handleCaptureVideo = useCallback(async (
    kind: "VIDEO" | "VIDEO_NOTE" = "VIDEO"
  ) => {
    if (!canPost || uploadingAttachments || editingMessageId || recordingVoice) {
      return;
    }

    const file = await captureChatVideo();
    if (!file) {
      return;
    }

    setUploadingAttachments(true);
    setError(null);
    try {
      const attachment = await uploadOrStageAttachment({
        uri: file.uri,
        name: file.name ?? "camera-video.mp4",
        contentType: file.type ?? "video/mp4",
        durationMs: file.durationMs ?? undefined,
        width: file.width ?? null,
        height: file.height ?? null,
        kind
      });
      setPendingAttachments((current) => [...current, attachment]);
      if (isQueuedUploadAttachment(attachment)) {
        setError("No connection. Attachment upload queued until send.");
      }
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Unable to upload attachment");
    } finally {
      setUploadingAttachments(false);
    }
  }, [
    canPost,
    editingMessageId,
    recordingVoice,
    setError,
    setPendingAttachments,
    setUploadingAttachments,
    uploadOrStageAttachment,
    uploadingAttachments
  ]);

  const handlePickPhotos = useCallback(async () => {
    if (!canPost || uploadingAttachments || editingMessageId || recordingVoice) {
      return;
    }

    const files = await pickChatLibraryMedia("IMAGE", {
      allowsMultipleSelection: true,
      maxSelection: 10
    });
    await addPickedMediaFiles("IMAGE", files);
  }, [addPickedMediaFiles, canPost, editingMessageId, recordingVoice, uploadingAttachments]);

  const handlePickVideos = useCallback(async () => {
    if (!canPost || uploadingAttachments || editingMessageId || recordingVoice) {
      return;
    }

    const files = await pickChatLibraryMedia("VIDEO", {
      allowsMultipleSelection: true,
      maxSelection: 4
    });
    await addPickedMediaFiles("VIDEO", files);
  }, [addPickedMediaFiles, canPost, editingMessageId, recordingVoice, uploadingAttachments]);

  const handlePickAudioFiles = useCallback(async () => {
    await handlePickDocuments("AUDIO", "audio/*");
  }, [handlePickDocuments]);

  const handlePickGifs = useCallback(async () => {
    await handlePickDocuments("GIF", "image/gif");
  }, [handlePickDocuments]);

  const handleUploadGifFromDevice = useCallback(async () => {
    setShowGifPicker(false);
    await handlePickGifs();
  }, [handlePickGifs, setShowGifPicker]);

  const handleInsertRecentGif = useCallback((attachment: MessageAttachment) => {
    setPendingAttachments((current) =>
      current.some((item) => item.attachmentId === attachment.attachmentId)
        ? current
        : [...current, attachment]
    );
    setShowGifPicker(false);
    setError(null);
  }, [setError, setPendingAttachments, setShowGifPicker]);

  return {
    handleCapturePhoto,
    handleCaptureVideo,
    handleCaptureVideoNote: () => handleCaptureVideo("VIDEO_NOTE"),
    handleInsertRecentGif,
    handlePickAttachments,
    handlePickAudioFiles,
    handlePickGifs,
    handlePickPhotos,
    handlePickVideos,
    handleUploadGifFromDevice,
    movePendingAttachment,
    removePendingAttachment,
    resolvePendingAttachmentsForSend,
    retryPendingAttachmentUpload,
    trimPendingAttachment,
    trimmingAttachmentId,
    uploadOrStageAttachment
  };
}
