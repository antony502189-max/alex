import { useEffect, useEffectEvent } from "react";
import { api } from "../services/api";
import { callMediaSession } from "../services/callMediaSession";
import { toQueuedMessageId } from "../services/clientMessageIds";
import { localDatabase } from "../services/localDatabase";
import { secretChatLocalCleanup } from "../services/secretChatLocalCleanup";
import { wsService } from "../services/ws";
import { useAppStore } from "../store/useAppStore";
import { isLiveCall } from "./rootCallUtils";
import type { Dispatch, SetStateAction } from "react";
import type { AppModalRoute } from "./types";
import type {
  DiscussionThreadSelection,
  MessageFocusTarget
} from "./rootNavigatorState";
import type {
  AuthSession,
  CallInboxEvent,
  CallJoinLink,
  CallSession,
  CallSignalEvent,
  ChatMessage,
  ChatSummary,
  ForumTopic
} from "../types";

type UseRootSessionBootstrapInput = {
  chats: ChatSummary[];
  clearMediaBuckets: () => void;
  currentCallRef: { current: CallSession | null };
  flushOutbox: (sessionToken: string, currentUserId: string) => Promise<void>;
  handledInitialLinkRef: { current: boolean };
  lastAuthenticatedUserIdRef: { current: string | null };
  refreshActiveCalls: (
    sessionToken: string,
    currentUserId: string
  ) => Promise<CallSession | null>;
  refreshChats: (
    sessionToken: string,
    currentUserId?: string
  ) => Promise<ChatSummary[]>;
  removeMessage: (chatId: string, messageId: string) => void;
  replaceMessage: (
    chatId: string,
    messageId: string,
    message: ChatMessage
  ) => void;
  resetNavigationState: () => void;
  session: AuthSession | null;
  setChats: (chats: ChatSummary[]) => void;
  setCurrentCall: Dispatch<SetStateAction<CallSession | null>>;
  setCurrentCallLinks: Dispatch<SetStateAction<CallJoinLink[]>>;
  setMembersChat: Dispatch<SetStateAction<ChatSummary | null>>;
  setModalRoute: Dispatch<SetStateAction<AppModalRoute | null>>;
  setPendingChatFocus: Dispatch<SetStateAction<MessageFocusTarget | null>>;
  setRecentCallSignals: Dispatch<SetStateAction<CallSignalEvent[]>>;
  setSelectedChat: Dispatch<SetStateAction<ChatSummary | null>>;
  setSelectedDiscussionThread: Dispatch<SetStateAction<DiscussionThreadSelection | null>>;
  setSelectedForumTopic: Dispatch<SetStateAction<ForumTopic | null>>;
  syncOpenChatTargets: (nextChats: ChatSummary[]) => void;
  upsertMessage: (message: ChatMessage) => void;
};

export function useRootSessionBootstrap({
  chats,
  clearMediaBuckets,
  currentCallRef,
  flushOutbox,
  handledInitialLinkRef,
  lastAuthenticatedUserIdRef,
  refreshActiveCalls,
  refreshChats,
  removeMessage,
  replaceMessage,
  resetNavigationState,
  session,
  setChats,
  setCurrentCall,
  setCurrentCallLinks,
  setMembersChat,
  setModalRoute,
  setPendingChatFocus,
  setRecentCallSignals,
  setSelectedChat,
  setSelectedDiscussionThread,
  setSelectedForumTopic,
  syncOpenChatTargets,
  upsertMessage
}: UseRootSessionBootstrapInput) {
  const handleFlushOutbox = useEffectEvent(flushOutbox);
  const handleRefreshActiveCalls = useEffectEvent(refreshActiveCalls);
  const handleRefreshChats = useEffectEvent(refreshChats);
  const handleRemoveMessage = useEffectEvent(removeMessage);
  const handleReplaceMessage = useEffectEvent(replaceMessage);
  const handleSyncOpenChatTargets = useEffectEvent(syncOpenChatTargets);
  const handleUpsertMessage = useEffectEvent(upsertMessage);

  useEffect(() => {
    void localDatabase.init().catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!session || chats.length === 0) {
      return;
    }

    void localDatabase.upsertChats(session.userId, chats).catch(() => undefined);
  }, [chats, session]);

  useEffect(() => {
    handleSyncOpenChatTargets(chats);
  }, [chats]);

  useEffect(() => {
    if (!session) {
      const previousUserId = lastAuthenticatedUserIdRef.current;
      if (previousUserId) {
        void secretChatLocalCleanup.clearAllSecretState(previousUserId).catch(() => undefined);
        lastAuthenticatedUserIdRef.current = null;
      }

      void callMediaSession.stop().catch(() => undefined);
      wsService.disconnect();
      setSelectedChat(null);
      setMembersChat(null);
      setModalRoute(null);
      setSelectedForumTopic(null);
      setSelectedDiscussionThread(null);
      setCurrentCall(null);
      setCurrentCallLinks([]);
      setRecentCallSignals([]);
      setPendingChatFocus(null);
      clearMediaBuckets();
      handledInitialLinkRef.current = false;
      resetNavigationState();
      return;
    }

    const sessionToken = session.token;
    const currentUserId = session.userId;
    let cancelled = false;
    let unsubscribeConnection: () => void = () => {};
    let unsubscribeCalls: () => void = () => {};
    let syncQueue: Promise<void> = Promise.resolve();

    const hydrateFeatureProfile = async () => {
      const profile = await api.getFeatureProfile(sessionToken);
      if (!cancelled) {
        useAppStore.getState().setFeatureProfile(profile);
      }
    };

    const setStoredCursor = async (cursor: number | null) => {
      await localDatabase.setSyncCursor(currentUserId, cursor).catch(() => undefined);
    };

    const applySyncedMessage = async (message: ChatMessage) => {
      if (message.senderId === currentUserId && message.clientMessageId) {
        const queuedMessageId = toQueuedMessageId(message.clientMessageId);
        handleReplaceMessage(message.chatId, queuedMessageId, message);
        await localDatabase
          .replaceQueuedMessage(currentUserId, message.chatId, queuedMessageId, message)
          .catch(() => localDatabase.upsertMessages(currentUserId, [message]));
        return;
      }

      handleUpsertMessage(message);
      await localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
    };

    const applySyncEvents = async (
      events: Array<{
        canonicalEventType: string;
        entityId: string | null;
        chatId: string | null;
      }>
    ) => {
      let shouldRefreshChats = false;

      for (const event of events) {
        if (cancelled) {
          return shouldRefreshChats;
        }

        switch (event.canonicalEventType) {
          case "MESSAGE_UPSERT": {
            if (event.entityId) {
              const message = await api.getMessage(sessionToken, event.entityId).catch(() => null);
              if (message) {
                await applySyncedMessage(message);
              }
            }
            shouldRefreshChats = true;
            break;
          }
          case "MESSAGE_DELETED": {
            if (event.chatId && event.entityId) {
              handleRemoveMessage(event.chatId, event.entityId);
              await localDatabase
                .removeMessage(currentUserId, event.chatId, event.entityId)
                .catch(() => undefined);
            }
            shouldRefreshChats = true;
            break;
          }
          case "CHAT_READ":
          case "CHAT_HISTORY_CLEARED":
          case "CHAT_REMOVED":
          case "CHAT_UPSERT":
          case "MEMBER_STATE_CHANGED":
            shouldRefreshChats = true;
            break;
          default:
            break;
        }
      }

      return shouldRefreshChats;
    };

    const syncRealtimeState = async (forceBootstrap: boolean) => {
      await handleFlushOutbox(sessionToken, currentUserId);
      await handleRefreshActiveCalls(sessionToken, currentUserId);

      let cursor = await localDatabase.getSyncCursor(currentUserId).catch(() => null);
      let requiresPostSyncRefresh = false;

      if (forceBootstrap && cursor == null) {
        await handleRefreshChats(sessionToken, currentUserId);
      }

      while (!cancelled) {
        const slice = await api.getSyncEvents(sessionToken, cursor, 100, false);
        if (cancelled) {
          return;
        }

        if (slice.staleCursor) {
          await handleRefreshChats(sessionToken, currentUserId);
          cursor = slice.resetCursor ?? null;
          await setStoredCursor(cursor);
        }

        requiresPostSyncRefresh =
          (await applySyncEvents(slice.events)) || requiresPostSyncRefresh;

        const fallbackCursor =
          slice.events.length > 0
            ? slice.events[slice.events.length - 1]?.cursor ?? cursor ?? 0
            : slice.resetCursor ?? cursor ?? 0;
        const nextCursor = slice.nextCursor ?? fallbackCursor;
        const cursorAdvanced = nextCursor !== cursor;

        cursor = nextCursor;
        await setStoredCursor(cursor);

        if (!slice.hasMore || (!cursorAdvanced && slice.events.length === 0)) {
          break;
        }
      }

      if (requiresPostSyncRefresh && !cancelled) {
        await handleRefreshChats(sessionToken, currentUserId);
      }
    };

    const enqueueSync = (forceBootstrap: boolean) => {
      syncQueue = syncQueue
        .then(() => syncRealtimeState(forceBootstrap))
        .catch(() => undefined);
      return syncQueue;
    };

    void localDatabase.getChats(currentUserId)
      .then((cachedChats) => {
        if (cancelled || cachedChats.length === 0) {
          return;
        }

        setChats(cachedChats);
        handleSyncOpenChatTargets(cachedChats);
      })
      .catch(() => undefined);

    void hydrateFeatureProfile().catch(() => undefined);

    wsService.connect(sessionToken, (message) => {
      if (message.senderId === currentUserId && message.clientMessageId) {
        handleReplaceMessage(
          message.chatId,
          toQueuedMessageId(message.clientMessageId),
          message
        );
        void localDatabase.replaceQueuedMessage(
          currentUserId,
          message.chatId,
          toQueuedMessageId(message.clientMessageId),
          message
        ).catch(() => undefined);
      } else {
        handleUpsertMessage(message);
        void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
      }

      void handleRefreshChats(sessionToken, currentUserId).catch(() => undefined);
    });

    unsubscribeCalls = wsService.subscribe("/user/queue/calls", (payload) => {
      const event = JSON.parse(payload) as CallInboxEvent;
      if (event.signal?.callId && currentCallRef.current?.callId === event.signal.callId) {
        setRecentCallSignals((current) => [...current.slice(-19), event.signal as CallSignalEvent]);
        void callMediaSession.handleSignal(event.signal).catch(() => undefined);
      }

      if (!event.call) {
        return;
      }

      if (isLiveCall(event.call, currentUserId)) {
        if (currentCallRef.current?.callId !== event.call.callId) {
          setRecentCallSignals([]);
        }
        setCurrentCall(event.call);
        return;
      }

      if (currentCallRef.current?.callId === event.call.callId) {
        setCurrentCall(null);
        setRecentCallSignals([]);
        void handleRefreshActiveCalls(sessionToken, currentUserId).catch(() => undefined);
      }
    });

    unsubscribeConnection = wsService.onConnectionChange((connected) => {
      if (!connected) {
        return;
      }

      void enqueueSync(false);
    });

    void enqueueSync(true);

    return () => {
      cancelled = true;
      unsubscribeConnection();
      unsubscribeCalls();
      wsService.disconnect();
    };
  }, [clearMediaBuckets, currentCallRef, handledInitialLinkRef, lastAuthenticatedUserIdRef, resetNavigationState, session, setChats, setCurrentCall, setCurrentCallLinks, setMembersChat, setModalRoute, setPendingChatFocus, setRecentCallSignals, setSelectedChat, setSelectedDiscussionThread, setSelectedForumTopic]);
}
