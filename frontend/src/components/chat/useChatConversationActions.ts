import { useCallback } from "react";
import * as Sharing from "expo-sharing";
import { attachmentTransfers } from "../../services/attachmentTransfers";
import type { AttachmentTransferState } from "../../store/useAttachmentTransferStore";
import { api } from "../../services/api";
import type { MessageAttachment } from "../../types";
import { isImageAttachment, isQueuedUploadAttachment, isVideoAttachment } from "./chatAttachmentHelpers";

type MediaViewerPayload = {
  attachments: MessageAttachment[];
  attachmentSources?: Array<{
    attachmentId: string;
    createdAt: string;
    messageId: string;
  }>;
  chatTitle: string;
  initialAttachmentId: string;
};

type UseChatConversationActionsParams = {
  attachmentTransferStates: Record<string, AttachmentTransferState>;
  chatArchived: boolean;
  chatId: string;
  chatMutedUntil: string | null | undefined;
  chatTitle: string;
  onBack: () => void;
  onOpenMediaViewer?: (payload: MediaViewerPayload) => void;
  onRefreshChats?: () => Promise<void> | void;
  setError: (value: string | null) => void;
  setOpeningAttachmentId: React.Dispatch<React.SetStateAction<string | null>>;
  token: string;
  upsertChat: (chat: any) => void;
};

export function useChatConversationActions({
  attachmentTransferStates,
  chatArchived,
  chatId,
  chatMutedUntil,
  chatTitle,
  onBack,
  onOpenMediaViewer,
  onRefreshChats,
  setError,
  setOpeningAttachmentId,
  token,
  upsertChat
}: UseChatConversationActionsParams) {
  const handleOpenAttachment = useCallback(async (
    attachment: MessageAttachment,
    messageAttachments: MessageAttachment[] = [attachment],
    sourceMessage?: {
      createdAt: string;
      messageId: string;
    }
  ) => {
    const transfer = attachmentTransferStates[attachment.attachmentId];
    if (transfer?.direction === "DOWNLOAD" && transfer.status === "RUNNING") {
      setError(null);
      try {
        await attachmentTransfers.pauseDownload(attachment.attachmentId);
      } catch (downloadError) {
        setError(
          downloadError instanceof Error ? downloadError.message : "Unable to pause download"
        );
      }
      return;
    }

    const mediaAttachments = messageAttachments.filter(
      (item) => isImageAttachment(item) || isVideoAttachment(item)
    );
    if (
      onOpenMediaViewer &&
      mediaAttachments.some((item) => item.attachmentId === attachment.attachmentId)
    ) {
      onOpenMediaViewer({
        attachments: mediaAttachments,
        attachmentSources: sourceMessage
          ? mediaAttachments.map((item) => ({
              attachmentId: item.attachmentId,
              createdAt: sourceMessage.createdAt,
              messageId: sourceMessage.messageId
            }))
          : undefined,
        initialAttachmentId: attachment.attachmentId,
        chatTitle
      });
      return;
    }

    setOpeningAttachmentId(attachment.attachmentId);
    setError(null);
    try {
      const uri = isQueuedUploadAttachment(attachment)
        ? attachment.localUri ?? null
        : await attachmentTransfers.downloadAttachment(token, attachment);
      if (!uri) {
        return;
      }
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(uri);
      } else {
        setError("Opening attachments is not available on this platform");
      }
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open attachment");
    } finally {
      setOpeningAttachmentId(null);
    }
  }, [
    attachmentTransferStates,
    chatTitle,
    onOpenMediaViewer,
    setError,
    setOpeningAttachmentId,
    token
  ]);

  const handleArchiveChat = useCallback(async () => {
    setError(null);
    try {
      await api.setChatArchived(token, chatId, !chatArchived);
      await onRefreshChats?.();
      onBack();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : "Unable to update archive state");
    }
  }, [chatArchived, chatId, onBack, onRefreshChats, setError, token]);

  const handleMuteChat = useCallback(async () => {
    setError(null);
    try {
      const isMuted = !!chatMutedUntil && new Date(chatMutedUntil).getTime() > Date.now();
      const mutedUntil = isMuted
        ? null
        : new Date(Date.now() + 60 * 60 * 1000).toISOString();
      upsertChat(await api.muteChat(token, chatId, mutedUntil));
      await onRefreshChats?.();
    } catch (muteError) {
      setError(muteError instanceof Error ? muteError.message : "Unable to update mute state");
    }
  }, [chatId, chatMutedUntil, onRefreshChats, setError, token, upsertChat]);

  return {
    handleArchiveChat,
    handleMuteChat,
    handleOpenAttachment
  };
}
