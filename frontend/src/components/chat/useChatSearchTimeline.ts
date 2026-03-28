import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FlatList } from "react-native";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import type { ChatMessage } from "../../types";

export type MessageJumpTarget = {
  messageId: string;
  createdAt: string;
};

type UseChatSearchTimelineParams = {
  activeThreadRootMessageId: string | null;
  chatId: string;
  currentUserId: string;
  initialFocusMessage?: MessageJumpTarget | null;
  listRef: React.RefObject<FlatList<ChatMessage> | null>;
  loadingHistory: boolean;
  messages: ChatMessage[];
  onConsumeInitialFocus?: () => void;
  pageSize: number;
  setChatMessages: (chatId: string, messages: ChatMessage[]) => void;
  setError: (value: string | null) => void;
  token: string;
  topicId: string | null;
};

export function useChatSearchTimeline({
  activeThreadRootMessageId,
  chatId,
  currentUserId,
  initialFocusMessage,
  listRef,
  loadingHistory,
  messages,
  onConsumeInitialFocus,
  pageSize,
  setChatMessages,
  setError,
  token,
  topicId
}: UseChatSearchTimelineParams) {
  const highlightTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ChatMessage[]>([]);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasMoreHistory, setHasMoreHistory] = useState(true);
  const [searching, setSearching] = useState(false);
  const [pendingJumpTarget, setPendingJumpTarget] = useState<MessageJumpTarget | null>(null);
  const [jumpingToMessage, setJumpingToMessage] = useState(false);
  const [highlightedMessageId, setHighlightedMessageId] = useState<string | null>(null);

  const latestMessageId = messages[messages.length - 1]?.messageId ?? null;

  const highlightMessage = useCallback((messageId: string) => {
    setHighlightedMessageId(messageId);
    if (highlightTimeoutRef.current) {
      clearTimeout(highlightTimeoutRef.current);
    }
    highlightTimeoutRef.current = setTimeout(() => {
      setHighlightedMessageId((current) => (current === messageId ? null : current));
      highlightTimeoutRef.current = null;
    }, 3200);
  }, []);

  const scrollToTimelineMessage = useCallback((messageId: string) => {
    const index = messages.findIndex((message) => message.messageId === messageId);
    if (index < 0) {
      return false;
    }
    requestAnimationFrame(() => {
      listRef.current?.scrollToIndex({
        index,
        animated: true,
        viewPosition: 0.45
      });
    });
    highlightMessage(messageId);
    return true;
  }, [highlightMessage, listRef, messages]);

  const ensureMessageVisible = useCallback(async (target: MessageJumpTarget) => {
    setSearchQuery("");
    setPendingJumpTarget(target);

    if (messages.some((message) => message.messageId === target.messageId)) {
      setJumpingToMessage(false);
      return;
    }

    const oldestLoadedMessage = messages[0];
    if (!oldestLoadedMessage) {
      setJumpingToMessage(false);
      return;
    }

    setJumpingToMessage(true);
    try {
      let cursor = oldestLoadedMessage.createdAt;
      let found = false;

      while (!found) {
        const older = await api.getMessagesBefore(
          token,
          chatId,
          cursor,
          pageSize,
          topicId,
          activeThreadRootMessageId
        );

        if (older.length === 0) {
          break;
        }

        setChatMessages(chatId, older);
        void localDatabase.upsertMessages(currentUserId, older).catch(() => undefined);

        found = older.some((message) => message.messageId === target.messageId);
        cursor = older[0]?.createdAt ?? cursor;

        if (!found && cursor.localeCompare(target.createdAt) <= 0) {
          break;
        }
      }
    } catch (jumpError) {
      setError(jumpError instanceof Error ? jumpError.message : "Unable to jump to message");
      setPendingJumpTarget(null);
      setJumpingToMessage(false);
    }
  }, [
    activeThreadRootMessageId,
    chatId,
    currentUserId,
    messages,
    pageSize,
    setChatMessages,
    setError,
    token,
    topicId
  ]);

  const handleLoadOlder = useCallback(async () => {
    const oldest = messages[0];
    if (!oldest || loadingOlder || !hasMoreHistory) {
      return;
    }
    setLoadingOlder(true);
    setError(null);
    try {
      const older = await api.getMessagesBefore(
        token,
        chatId,
        oldest.createdAt,
        pageSize,
        topicId,
        activeThreadRootMessageId
      );
      setChatMessages(chatId, older);
      void localDatabase.upsertMessages(currentUserId, older).catch(() => undefined);
      setHasMoreHistory(older.length === pageSize);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load older messages");
    } finally {
      setLoadingOlder(false);
    }
  }, [
    activeThreadRootMessageId,
    chatId,
    currentUserId,
    hasMoreHistory,
    loadingOlder,
    messages,
    pageSize,
    setChatMessages,
    setError,
    token,
    topicId
  ]);

  const resetTimelineSearchState = useCallback(() => {
    setSearchQuery("");
    setSearchResults([]);
    setHasMoreHistory(true);
    setSearching(false);
    setPendingJumpTarget(null);
    setJumpingToMessage(false);
    setHighlightedMessageId(null);
  }, []);

  useEffect(() => {
    return () => {
      if (highlightTimeoutRef.current) {
        clearTimeout(highlightTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (!initialFocusMessage) {
      return;
    }
    onConsumeInitialFocus?.();
    void ensureMessageVisible(initialFocusMessage);
  }, [
    ensureMessageVisible,
    onConsumeInitialFocus,
    initialFocusMessage?.createdAt,
    initialFocusMessage?.messageId
  ]);

  useEffect(() => {
    if (!pendingJumpTarget || loadingHistory) {
      return;
    }

    if (messages.some((message) => message.messageId === pendingJumpTarget.messageId)) {
      scrollToTimelineMessage(pendingJumpTarget.messageId);
      setPendingJumpTarget(null);
      setJumpingToMessage(false);
      return;
    }

    const oldestLoadedMessage = messages[0];
    if (
      oldestLoadedMessage &&
      oldestLoadedMessage.createdAt.localeCompare(pendingJumpTarget.createdAt) <= 0
    ) {
      setError("Target message is outside the loaded history window.");
      setPendingJumpTarget(null);
      setJumpingToMessage(false);
    }
  }, [loadingHistory, messages, pendingJumpTarget, scrollToTimelineMessage, setError]);

  useEffect(() => {
    if (!latestMessageId || searchQuery.trim().length >= 2) {
      return;
    }
    listRef.current?.scrollToEnd({ animated: true });
  }, [latestMessageId, listRef, searchQuery]);

  useEffect(() => {
    let cancelled = false;
    const normalized = searchQuery.trim();
    if (normalized.length < 2) {
      setSearchResults([]);
      setSearching(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      setSearching(true);
      api.searchMessages(token, chatId, normalized, 20, topicId, activeThreadRootMessageId)
        .then((response) => {
          if (!cancelled) {
            setSearchResults(response.messages);
          }
        })
        .catch((searchError) => {
          if (!cancelled) {
            setError(searchError instanceof Error ? searchError.message : "Unable to search messages");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setSearching(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [activeThreadRootMessageId, chatId, searchQuery, setError, token, topicId]);

  const displayedMessages = useMemo(
    () => (searchQuery.trim().length >= 2 ? searchResults : messages),
    [messages, searchQuery, searchResults]
  );

  return {
    displayedMessages,
    ensureMessageVisible,
    handleLoadOlder,
    hasMoreHistory,
    highlightedMessageId,
    jumpingToMessage,
    loadingOlder,
    resetTimelineSearchState,
    searchQuery,
    searchResults,
    searching,
    setHasMoreHistory,
    setSearchResults,
    setSearchQuery
  };
}
