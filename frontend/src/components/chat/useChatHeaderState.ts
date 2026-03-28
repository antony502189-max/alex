import { useMemo } from "react";
import {
  buildChatHeaderPresentation,
  getArchiveToggleLabel,
  getMuteToggleLabel,
  getPinnedHistoryToggleLabel,
  getPinnedPreviewText,
  getReplyPanelTitle,
  getScheduledPanelTitle
} from "./chatScreenPresentation";
import { formatPresenceStatus } from "../../services/presence";
import type {
  ChatMessage,
  ChatSummary,
  ForumTopic,
  ScheduledMessage
} from "../../types";

type UseChatHeaderStateParams = {
  activeThreadRootMessageId: string | null;
  chat: ChatSummary;
  describeMessage: (message: ChatMessage | ScheduledMessage) => string;
  editingMessageId: string | null;
  loadingPinnedHistory: boolean;
  memberCount: number;
  pinnedHistoryLength: number;
  pinnedPreviewMessage: ChatMessage | null;
  showPinnedHistory: boolean;
  threadCommentCount: number | null | undefined;
  threadTitle: string | null | undefined;
  topic: ForumTopic | null | undefined;
};

export function useChatHeaderState({
  activeThreadRootMessageId,
  chat,
  describeMessage,
  editingMessageId,
  loadingPinnedHistory,
  memberCount,
  pinnedHistoryLength,
  pinnedPreviewMessage,
  showPinnedHistory,
  threadCommentCount,
  threadTitle,
  topic
}: UseChatHeaderStateParams) {
  const directPresenceLabel = useMemo(
    () =>
      chat.peerIsBot
        ? "bot"
        : formatPresenceStatus(
            { online: chat.peerOnline, lastSeenAt: chat.peerLastSeenAt },
            "status hidden"
          ),
    [chat.peerIsBot, chat.peerLastSeenAt, chat.peerOnline]
  );

  const headerPresentation = useMemo(
    () =>
      buildChatHeaderPresentation({
        activeThreadRootMessageId,
        chat,
        directPresenceLabel,
        memberCount,
        threadCommentCount: threadCommentCount ?? null,
        threadTitle: threadTitle ?? null,
        topic: topic ?? null
      }),
    [
      activeThreadRootMessageId,
      chat,
      directPresenceLabel,
      memberCount,
      threadCommentCount,
      threadTitle,
      topic
    ]
  );

  const muteToggleLabel = useMemo(
    () => getMuteToggleLabel(chat.mutedUntil),
    [chat.mutedUntil]
  );
  const archiveToggleLabel = useMemo(
    () => getArchiveToggleLabel(chat.archived),
    [chat.archived]
  );
  const pinnedHistoryToggleLabel = useMemo(
    () => getPinnedHistoryToggleLabel(showPinnedHistory, pinnedHistoryLength),
    [pinnedHistoryLength, showPinnedHistory]
  );
  const pinnedPreviewText = useMemo(
    () =>
      getPinnedPreviewText({
        describeMessage,
        loadingPinnedHistory,
        pinnedPreviewMessage
      }),
    [describeMessage, loadingPinnedHistory, pinnedPreviewMessage]
  );
  const scheduledPanelTitle = useMemo(
    () =>
      getScheduledPanelTitle({
        activeThreadRootMessageId,
        threadTitle: threadTitle ?? null,
        topic: topic ?? null
      }),
    [activeThreadRootMessageId, threadTitle, topic]
  );
  const replyPanelTitle = useMemo(
    () => getReplyPanelTitle(editingMessageId, activeThreadRootMessageId),
    [activeThreadRootMessageId, editingMessageId]
  );

  return {
    archiveToggleLabel,
    directPresenceLabel,
    headerPresentation,
    muteToggleLabel,
    pinnedHistoryToggleLabel,
    pinnedPreviewText,
    replyPanelTitle,
    scheduledPanelTitle
  };
}
