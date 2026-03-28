import { useEffect, useMemo, useState } from "react";
import * as Sharing from "expo-sharing";
import { attachmentTransfers } from "../../services/attachmentTransfers";
import type { MessageAttachment } from "../../types";
import {
  isImageAttachment,
  isVideoAttachment,
  resolveInitialMediaIndex,
  resolveMediaViewerCurrentUri
} from "./mediaViewerPresentation";

type UseMediaViewerControllerParams = {
  attachments: MessageAttachment[];
  initialAttachmentId: string;
  token: string;
};

export function useMediaViewerController({
  attachments,
  initialAttachmentId,
  token
}: UseMediaViewerControllerParams) {
  const initialIndex = useMemo(
    () => resolveInitialMediaIndex(attachments, initialAttachmentId),
    [attachments, initialAttachmentId]
  );

  const [currentIndex, setCurrentIndex] = useState(initialIndex);
  const [resolvedUris, setResolvedUris] = useState<Record<string, string>>({});
  const [loadingLocalAttachmentId, setLoadingLocalAttachmentId] = useState<string | null>(null);
  const [sharingAttachmentId, setSharingAttachmentId] = useState<string | null>(null);
  const [videoPlaying, setVideoPlaying] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setCurrentIndex(initialIndex);
  }, [initialIndex]);

  const currentAttachment = attachments[currentIndex] ?? null;
  const currentUri = useMemo(
    () => resolveMediaViewerCurrentUri(currentAttachment, resolvedUris),
    [currentAttachment, resolvedUris]
  );

  useEffect(() => {
    setVideoPlaying(true);
    if (
      !currentAttachment ||
      (!isVideoAttachment(currentAttachment) && !isImageAttachment(currentAttachment))
    ) {
      return;
    }

    const existingUri = resolveMediaViewerCurrentUri(currentAttachment, resolvedUris);
    if (existingUri) {
      return;
    }

    let cancelled = false;
    setLoadingLocalAttachmentId(currentAttachment.attachmentId);
    setError(null);
    attachmentTransfers
      .downloadAttachment(token, currentAttachment)
      .then((uri) => {
        if (!uri || cancelled) {
          return;
        }
        setResolvedUris((current) => ({
          ...current,
          [currentAttachment.attachmentId]: uri
        }));
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Unable to prepare media");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingLocalAttachmentId(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [currentAttachment, resolvedUris, token]);

  async function handleShareCurrent() {
    if (!currentAttachment) {
      return;
    }

    setSharingAttachmentId(currentAttachment.attachmentId);
    setError(null);
    try {
      const uri =
        currentAttachment.localUri ??
        resolvedUris[currentAttachment.attachmentId] ??
        (await attachmentTransfers.downloadAttachment(token, currentAttachment));
      if (!uri) {
        return;
      }
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(uri);
        return;
      }
      setError("Sharing is not available on this platform.");
    } catch (shareError) {
      setError(shareError instanceof Error ? shareError.message : "Unable to share attachment");
    } finally {
      setSharingAttachmentId(null);
    }
  }

  function handlePrevious() {
    setCurrentIndex((current) => Math.max(0, current - 1));
  }

  function handleNext() {
    setCurrentIndex((current) => Math.min(attachments.length - 1, current + 1));
  }

  function handleToggleVideoPlayback() {
    setVideoPlaying((current) => !current);
  }

  return {
    currentAttachment,
    currentIndex,
    currentUri,
    error,
    handleNext,
    handlePrevious,
    handleShareCurrent,
    handleToggleVideoPlayback,
    hasNext: currentIndex < attachments.length - 1,
    hasPrevious: currentIndex > 0,
    loadingLocalAttachmentId,
    sharingAttachmentId,
    videoPlaying
  };
}

export type MediaViewerScreenController = ReturnType<typeof useMediaViewerController>;
