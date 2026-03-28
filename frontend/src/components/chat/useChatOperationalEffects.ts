import { useEffect, useMemo } from "react";
import { api } from "../../services/api";
import type { ChatMember, ChatMessage, ChatReadEvent, ChatSummary } from "../../types";

type UseChatOperationalEffectsParams = {
  canPost: boolean;
  chatId: string;
  currentUserId: string;
  draft: string;
  editingMessageId: string | null;
  handleReadEvent: (event: ChatReadEvent) => void;
  isTypingRef: React.MutableRefObject<boolean>;
  members: ChatMember[];
  messages: ChatMessage[];
  onRefreshChats?: () => Promise<void> | void;
  persistedDraftRef: React.MutableRefObject<string>;
  setCurrentTimeMs: React.Dispatch<React.SetStateAction<number>>;
  setError: (value: string | null) => void;
  slowModeEndsAt: number | null;
  token: string;
  typingResetRef: React.MutableRefObject<ReturnType<typeof setTimeout> | null>;
  typingUserIds: string[];
  upsertChat: (summary: ChatSummary) => void;
};

export function useChatOperationalEffects({
  canPost,
  chatId,
  currentUserId,
  draft,
  editingMessageId,
  handleReadEvent,
  isTypingRef,
  members,
  messages,
  onRefreshChats,
  persistedDraftRef,
  setCurrentTimeMs,
  setError,
  slowModeEndsAt,
  token,
  typingResetRef,
  typingUserIds,
  upsertChat
}: UseChatOperationalEffectsParams) {
  useEffect(() => {
    if (!slowModeEndsAt) {
      return;
    }
    const intervalId = setInterval(() => {
      setCurrentTimeMs(Date.now());
    }, 1000);
    return () => {
      clearInterval(intervalId);
    };
  }, [setCurrentTimeMs, slowModeEndsAt]);

  useEffect(() => {
    if (!canPost || editingMessageId) {
      return;
    }

    const normalizedDraft = draft.trim();
    if (normalizedDraft === persistedDraftRef.current) {
      return;
    }

    let cancelled = false;
    const timeoutId = setTimeout(() => {
      const syncRequest = normalizedDraft
        ? api.saveDraft(token, chatId, normalizedDraft)
        : api.clearDraft(token, chatId);

      syncRequest
        .then((summary) => {
          if (cancelled) {
            return;
          }
          persistedDraftRef.current = summary.draftText?.trim() ?? "";
          upsertChat(summary);
        })
        .catch((draftError) => {
          if (!cancelled) {
            setError(draftError instanceof Error ? draftError.message : "Unable to sync draft");
          }
        });
    }, 450);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [canPost, chatId, draft, editingMessageId, persistedDraftRef, setError, token, upsertChat]);

  useEffect(() => {
    const latest = messages[messages.length - 1];
    if (!latest || latest.senderId === currentUserId) {
      return;
    }
    void api.markRead(token, chatId, latest.messageId)
      .then(async (event) => {
        handleReadEvent(event);
        await onRefreshChats?.();
      })
      .catch(() => undefined);
  }, [chatId, currentUserId, handleReadEvent, messages, onRefreshChats, token]);

  useEffect(() => {
    if (!canPost) {
      return;
    }
    if (!draft.trim()) {
      if (isTypingRef.current) {
        void api.sendTyping(token, chatId, false).catch(() => undefined);
        isTypingRef.current = false;
      }
      if (typingResetRef.current) {
        clearTimeout(typingResetRef.current);
      }
      return;
    }
    if (!isTypingRef.current) {
      isTypingRef.current = true;
      void api.sendTyping(token, chatId, true).catch(() => undefined);
    }
    if (typingResetRef.current) {
      clearTimeout(typingResetRef.current);
    }
    typingResetRef.current = setTimeout(() => {
      void api.sendTyping(token, chatId, false).catch(() => undefined);
      isTypingRef.current = false;
    }, 1200);
  }, [canPost, chatId, draft, isTypingRef, token, typingResetRef]);

  const typingLabel = useMemo(() => {
    const names = typingUserIds
      .map((userId) => members.find((member) => member.userId === userId)?.displayName)
      .filter(Boolean);
    return names.length > 0 ? `${names.join(", ")} typing...` : null;
  }, [members, typingUserIds]);

  return {
    typingLabel
  };
}
