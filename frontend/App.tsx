import React, { useEffect, useRef, useState } from "react";
import * as Notifications from "expo-notifications";
import { AppState, Linking, StyleSheet, View } from "react-native";
import { RootTabBar } from "./src/components/RootTabBar";
import { parseAlexDeepLink } from "./src/navigation/deepLinks";
import { AuthScreen } from "./src/screens/AuthScreen";
import { BotDeveloperScreen } from "./src/screens/BotDeveloperScreen";
import { BotMiniAppScreen } from "./src/screens/BotMiniAppScreen";
import { CallScreen } from "./src/screens/CallScreen";
import { CallsScreen } from "./src/screens/CallsScreen";
import { ChatScreen } from "./src/screens/ChatScreen";
import { ChatsListScreen } from "./src/screens/ChatsListScreen";
import { CreateChatScreen } from "./src/screens/CreateChatScreen";
import { MediaViewerScreen } from "./src/screens/MediaViewerScreen";
import { MembersScreen } from "./src/screens/MembersScreen";
import { ProfileScreen } from "./src/screens/ProfileScreen";
import { ContactsScreen } from "./src/screens/ContactsScreen";
import { ArchivedChatsScreen } from "./src/screens/ArchivedChatsScreen";
import { FoldersScreen } from "./src/screens/FoldersScreen";
import { ForumTopicsScreen } from "./src/screens/ForumTopicsScreen";
import { GlobalSearchScreen } from "./src/screens/GlobalSearchScreen";
import { SessionsScreen } from "./src/screens/SessionsScreen";
import { SecretChatScreen } from "./src/screens/SecretChatScreen";
import { SecretChatsScreen } from "./src/screens/SecretChatsScreen";
import { SharedMediaScreen } from "./src/screens/SharedMediaScreen";
import { StoriesScreen } from "./src/screens/StoriesScreen";
import { CreateStoryScreen } from "./src/screens/CreateStoryScreen";
import { JoinChatByLinkScreen } from "./src/screens/JoinChatByLinkScreen";
import { useFeatureFlagsStore } from "./src/store/useFeatureFlagsStore";
import { useMediaStore } from "./src/store/useMediaStore";
import { useNavigationStore } from "./src/store/useNavigationStore";
import { api } from "./src/services/api";
import { callMediaSession } from "./src/services/callMediaSession";
import { toQueuedMessageId } from "./src/services/clientMessageIds";
import { localDatabase } from "./src/services/localDatabase";
import { messageOutbox } from "./src/services/messageOutbox";
import { registerForPushNotificationsAsync } from "./src/services/notifications";
import { scheduledMessageOutbox } from "./src/services/scheduledMessageOutbox";
import { secretChatLocalCleanup } from "./src/services/secretChatLocalCleanup";
import { wsService } from "./src/services/ws";
import { useAppStore } from "./src/store/useAppStore";
import type {
  CallJoinLink,
  CallInboxEvent,
  CallMediaState,
  CallSession,
  CallSignalEvent,
  ChatMessage,
  MessageAttachment,
  ChatSummary,
  ForumTopic,
  SecretChatSummary,
  Story
} from "./src/types";

type DiscussionThreadSelection = {
  discussionChatId: string;
  rootMessageId: string;
  originChatId: string;
  title: string | null;
};

type BotMiniAppSelection = {
  botUserId: string;
  chatId: string | null;
  startParameter: string | null;
  title: string;
};

type MessageFocusTarget = {
  chatId: string;
  messageId: string;
  createdAt: string;
};

type MediaViewerSelection = {
  attachments: MessageAttachment[];
  initialAttachmentId: string;
  chatTitle: string;
};

function isLiveCall(call: CallSession | null, currentUserId: string) {
  if (!call || !["RINGING", "ACTIVE"].includes(call.status)) {
    return false;
  }
  const myParticipant = call.participants.find((participant) => participant.userId === currentUserId);
  return !!myParticipant && !["LEFT", "DECLINED", "MISSED"].includes(myParticipant.state);
}

function pickPreferredCall(calls: CallSession[], currentUserId: string) {
  return [...calls]
    .filter((call) => isLiveCall(call, currentUserId))
    .sort((left, right) =>
      (right.answeredAt ?? right.startedAt).localeCompare(left.answeredAt ?? left.startedAt)
    )[0] ?? null;
}

function deriveCallTitle(call: CallSession, chats: ChatSummary[], currentUserId: string) {
  const chat = chats.find((item) => item.chatId === call.chatId);
  if (chat) {
    return chat.title;
  }
  const others = call.participants.filter((participant) => participant.userId !== currentUserId);
  if (others.length === 0) {
    return "Call";
  }
  if (others.length === 1) {
    return others[0].displayName;
  }
  return others.map((participant) => participant.displayName).join(", ");
}

function deriveCallPhoto(call: CallSession, chats: ChatSummary[], currentUserId: string) {
  const chat = chats.find((item) => item.chatId === call.chatId);
  if (chat?.photoUrl) {
    return chat.photoUrl;
  }
  return call.participants.find((participant) => participant.userId !== currentUserId)?.photoUrl ?? null;
}

export default function App() {
  const session = useAppStore((state) => state.session);
  const chats = useAppStore((state) => state.chats);
  const setChats = useAppStore((state) => state.setChats);
  const setChatMessages = useAppStore((state) => state.setChatMessages);
  const upsertMessage = useAppStore((state) => state.upsertMessage);
  const upsertChat = useAppStore((state) => state.upsertChat);
  const replaceMessage = useAppStore((state) => state.replaceMessage);
  const removeMessage = useAppStore((state) => state.removeMessage);
  const activeRootTab = useNavigationStore((state) => state.activeRootTab);
  const setActiveRootTab = useNavigationStore((state) => state.setActiveRootTab);
  const clearMediaBuckets = useMediaStore((state) => state.clearAll);
  const storiesEnabled = useFeatureFlagsStore((state) => state.stories);
  const callsEnabled = useFeatureFlagsStore((state) => state.calls);
  const secretChatsEnabled = useFeatureFlagsStore((state) => state.secretChats);
  const botsEnabled = useFeatureFlagsStore((state) => state.bots);
  const [selectedChat, setSelectedChat] = useState<ChatSummary | null>(null);
  const [membersChat, setMembersChat] = useState<ChatSummary | null>(null);
  const [showBotDeveloper, setShowBotDeveloper] = useState(false);
  const [showSessions, setShowSessions] = useState(false);
  const [showGlobalSearch, setShowGlobalSearch] = useState(false);
  const [showCreateStory, setShowCreateStory] = useState(false);
  const [showJoinByLink, setShowJoinByLink] = useState(false);
  const [showSecretChats, setShowSecretChats] = useState(false);
  const [sharedMediaChat, setSharedMediaChat] = useState<ChatSummary | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const [showFolders, setShowFolders] = useState(false);
  const [joinByLinkSeedToken, setJoinByLinkSeedToken] = useState<string | null>(null);
  const [selectedForumTopic, setSelectedForumTopic] = useState<ForumTopic | null>(null);
  const [selectedDiscussionThread, setSelectedDiscussionThread] =
    useState<DiscussionThreadSelection | null>(null);
  const [selectedBotMiniApp, setSelectedBotMiniApp] = useState<BotMiniAppSelection | null>(null);
  const [mediaViewer, setMediaViewer] = useState<MediaViewerSelection | null>(null);
  const [secretChatSeed, setSecretChatSeed] = useState<{ peerUserId: string; peerDisplayName: string } | null>(null);
  const [selectedSecretChat, setSelectedSecretChat] = useState<SecretChatSummary | null>(null);
  const [composeMode, setComposeMode] = useState<"direct" | "group" | "channel" | null>(null);
  const [pendingChatFocus, setPendingChatFocus] = useState<MessageFocusTarget | null>(null);
  const [currentCall, setCurrentCall] = useState<CallSession | null>(null);
  const [currentCallLinks, setCurrentCallLinks] = useState<CallJoinLink[]>([]);
  const [recentCallSignals, setRecentCallSignals] = useState<CallSignalEvent[]>([]);
  const [callMediaState, setCallMediaState] = useState<CallMediaState>(callMediaSession.getState());
  const currentCallRef = useRef<CallSession | null>(null);
  const lastAuthenticatedUserIdRef = useRef<string | null>(null);
  const handledInitialLinkRef = useRef(false);

  useEffect(() => {
    currentCallRef.current = currentCall;
  }, [currentCall]);

  useEffect(() => callMediaSession.subscribe(setCallMediaState), []);

  useEffect(() => {
    if (session?.userId) {
      lastAuthenticatedUserIdRef.current = session.userId;
    }
  }, [session]);

  function syncOpenChatTargets(nextChats: ChatSummary[]) {
    setSelectedChat((current) =>
      current ? nextChats.find((chat) => chat.chatId === current.chatId) ?? current : current
    );
    setMembersChat((current) =>
      current ? nextChats.find((chat) => chat.chatId === current.chatId) ?? current : current
    );
    setSharedMediaChat((current) =>
      current ? nextChats.find((chat) => chat.chatId === current.chatId) ?? current : current
    );
  }

  function openChat(
    chat: ChatSummary,
    focus?: { messageId: string; createdAt: string } | null
  ) {
    setSelectedDiscussionThread(null);
    setSelectedForumTopic(null);
    setSelectedBotMiniApp(null);
    setActiveRootTab("CHATS");
    setPendingChatFocus(
      focus
        ? {
            chatId: chat.chatId,
            messageId: focus.messageId,
            createdAt: focus.createdAt
          }
        : null
    );
    setSelectedChat(chat);
  }

  async function refreshChats(sessionToken: string, currentUserId?: string) {
    const nextChats = await api.getChats(sessionToken);
    setChats(nextChats);
    syncOpenChatTargets(nextChats);
    if (currentUserId) {
      await localDatabase.replaceChats(currentUserId, nextChats);
    }
    return nextChats;
  }

  async function refreshActiveCalls(sessionToken: string, currentUserId: string) {
    const activeCalls = await api.getActiveCalls(sessionToken);
    const nextCall = pickPreferredCall(activeCalls, currentUserId);
    if (currentCallRef.current?.callId !== nextCall?.callId) {
      setRecentCallSignals([]);
    }
    setCurrentCall(nextCall);
    return nextCall;
  }

  async function refreshCurrentCallLinks(call: CallSession | null, sessionToken: string) {
    if (!call || call.mode !== "GROUP" || !call.viewerCanManageLinks) {
      setCurrentCallLinks([]);
      return [];
    }

    const links = await api.getCallLinks(sessionToken, call.chatId);
    setCurrentCallLinks(links);
    return links;
  }

  async function flushOutbox(sessionToken: string, currentUserId: string) {
    await messageOutbox.flush(sessionToken, currentUserId, {
      onSynced: (queuedMessageId, message) => {
        replaceMessage(message.chatId, queuedMessageId, message);
        void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
      },
      onDropped: (queuedMessageId, chatId) => {
        removeMessage(chatId, queuedMessageId);
      }
    });
    await scheduledMessageOutbox.flush(sessionToken, currentUserId, {
      onSynced: (_queuedScheduledMessageId, message) => {
        void localDatabase.upsertScheduledMessages(currentUserId, [message]).catch(() => undefined);
      },
      onDropped: (_queuedScheduledMessageId, _chatId) => undefined
    });
  }

  async function openChatFromNotification(
    sessionToken: string,
    chatId: string,
    currentUserId: string,
    topicId?: string | null,
    focus?: { messageId: string; createdAt: string } | null
  ) {
    setMembersChat(null);
    setShowBotDeveloper(false);
    setShowSessions(false);
    setShowGlobalSearch(false);
    setShowCreateStory(false);
    setShowJoinByLink(false);
    setShowSecretChats(false);
    setSecretChatSeed(null);
    setSelectedSecretChat(null);
    setShowArchived(false);
    setShowFolders(false);
    setSelectedForumTopic(null);
    setSelectedDiscussionThread(null);
    setSelectedBotMiniApp(null);
    setComposeMode(null);
    setJoinByLinkSeedToken(null);
    setSharedMediaChat(null);
    setMediaViewer(null);
    setPendingChatFocus(
      focus
        ? {
            chatId,
            messageId: focus.messageId,
            createdAt: focus.createdAt
          }
        : null
    );
    setActiveRootTab("CHATS");

    const existing = chats.find((chat) => chat.chatId === chatId);
    if (existing) {
      setSelectedChat(existing);
      if (existing.forumEnabled && topicId) {
        try {
          const topics = await api.getForumTopics(sessionToken, existing.chatId);
          const topic = topics.find((item) => item.topicId === topicId) ?? null;
          if (topic) {
            setSelectedForumTopic(topic);
          }
        } catch {
        }
      }
      return;
    }

    const nextChats = await refreshChats(sessionToken, currentUserId);
    const targetChat = nextChats.find((chat) => chat.chatId === chatId) ?? null;
    if (targetChat) {
      setSelectedChat(targetChat);
      if (targetChat.forumEnabled && topicId) {
        try {
          const topics = await api.getForumTopics(sessionToken, targetChat.chatId);
          const topic = topics.find((item) => item.topicId === topicId) ?? null;
          if (topic) {
            setSelectedForumTopic(topic);
          }
        } catch {
        }
      }
    }
  }

  async function openDiscussionThread(message: ChatMessage) {
    if (
      !session ||
      !selectedChat ||
      !message.discussionChatId ||
      !message.discussionRootMessageId
    ) {
      return;
    }

    let discussionChat =
      chats.find((chat) => chat.chatId === message.discussionChatId) ?? null;
    if (!discussionChat) {
      const nextChats = await refreshChats(session.token, session.userId);
      discussionChat =
        nextChats.find((chat) => chat.chatId === message.discussionChatId) ?? null;
    }
    if (!discussionChat) {
      return;
    }

    setMembersChat(null);
    setSelectedForumTopic(null);
    setSelectedDiscussionThread({
      discussionChatId: discussionChat.chatId,
      rootMessageId: message.discussionRootMessageId,
      originChatId: selectedChat.chatId,
      title: null
    });
    setSelectedChat(discussionChat);
  }

  async function startChatCall(chatId: string, kind: "VOICE" | "VIDEO") {
    if (!session) {
      return;
    }
    try {
      const call = await api.startCall(session.token, { chatId, kind });
      if (currentCallRef.current?.callId !== call.callId) {
        setRecentCallSignals([]);
      }
      setCurrentCall(call);
      void refreshCurrentCallLinks(call, session.token).catch(() => undefined);
    } catch {
      void refreshActiveCalls(session.token, session.userId).catch(() => undefined);
    }
  }

  async function joinCallByLink(rawToken: string) {
    if (!session) {
      return;
    }
    const call = await api.joinCallLink(session.token, rawToken);
    if (currentCallRef.current?.callId !== call.callId) {
      setRecentCallSignals([]);
    }
    setCurrentCall(call);
    void refreshCurrentCallLinks(call, session.token).catch(() => undefined);
  }

  async function acceptCurrentCall() {
    if (!session || !currentCallRef.current) {
      return;
    }
    const call = await api.acceptCall(session.token, currentCallRef.current.callId);
    setCurrentCall(call);
  }

  async function declineCurrentCall() {
    if (!session || !currentCallRef.current) {
      return;
    }
    const call = await api.declineCall(session.token, currentCallRef.current.callId);
    if (isLiveCall(call, session.userId)) {
      setCurrentCall(call);
      return;
    }
    setCurrentCall(null);
    setRecentCallSignals([]);
    void refreshActiveCalls(session.token, session.userId).catch(() => undefined);
  }

  async function leaveCurrentCall() {
    if (!session || !currentCallRef.current) {
      return;
    }
    const call = await api.leaveCall(session.token, currentCallRef.current.callId);
    if (isLiveCall(call, session.userId)) {
      setCurrentCall(call);
      return;
    }
    setCurrentCall(null);
    setCurrentCallLinks([]);
    setRecentCallSignals([]);
    void refreshActiveCalls(session.token, session.userId).catch(() => undefined);
  }

  async function createCurrentCallLink(kind: "VOICE" | "VIDEO") {
    if (!session || !currentCallRef.current) {
      return;
    }
    const created = await api.createCallLink(session.token, {
      chatId: currentCallRef.current.chatId,
      kind
    });
    setCurrentCallLinks((current) => [created, ...current.filter((item) => item.linkId !== created.linkId)]);
  }

  async function moderateCurrentCallParticipant(
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      videoPublishingAllowed?: boolean;
      screenShareAllowed?: boolean;
      removeParticipant?: boolean;
    }
  ) {
    if (!session || !currentCallRef.current) {
      return;
    }
    const call = await api.moderateCallParticipant(session.token, currentCallRef.current.callId, userId, payload);
    setCurrentCall(call);
  }

  async function toggleCurrentScreenShare() {
    if (!session || !currentCallRef.current) {
      return;
    }

    const nextSharing = !callMediaState.localScreenSharing;
    if (nextSharing) {
      await callMediaSession.startScreenShare();
    } else {
      await callMediaSession.stopScreenShare();
    }

    try {
      const call = nextSharing
        ? await api.startScreenShare(session.token, currentCallRef.current.callId)
        : await api.stopScreenShare(session.token, currentCallRef.current.callId);
      setCurrentCall(call);
      await broadcastCallSignal(nextSharing ? "SCREEN_SHARE_ON" : "SCREEN_SHARE_OFF", {
        screenSharing: nextSharing
      });
    } catch (error) {
      if (nextSharing) {
        await callMediaSession.stopScreenShare().catch(() => undefined);
      }
      throw error;
    }
  }

  async function sendCallSignalToUser(
    toUserId: string,
    signalType: string,
    payload: Record<string, unknown>
  ) {
    if (!session || !currentCallRef.current) {
      return;
    }

    await api.sendCallSignal(session.token, currentCallRef.current.callId, {
      toUserId,
      signalType,
      payload: JSON.stringify(payload)
    });
  }

  async function broadcastCallSignal(
    signalType: string,
    payload: Record<string, unknown>
  ) {
    if (!session || !currentCallRef.current) {
      return;
    }

    const call = currentCallRef.current;
    const recipients = call.participants.filter(
      (participant) =>
        participant.userId !== session.userId &&
        !["LEFT", "DECLINED", "MISSED"].includes(participant.state)
    );
    if (recipients.length === 0) {
      return;
    }

    const serializedPayload = JSON.stringify(payload);
    await Promise.allSettled(
      recipients.map((participant) =>
        api.sendCallSignal(session.token, call.callId, {
          toUserId: participant.userId,
          signalType,
          payload: serializedPayload
        })
      )
    );
  }

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
    syncOpenChatTargets(chats);
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
      setShowBotDeveloper(false);
      setShowSessions(false);
      setShowGlobalSearch(false);
      setShowCreateStory(false);
      setShowJoinByLink(false);
      setShowSecretChats(false);
      setSharedMediaChat(null);
      setShowArchived(false);
      setShowFolders(false);
      setSelectedForumTopic(null);
      setSelectedDiscussionThread(null);
      setSelectedBotMiniApp(null);
      setMediaViewer(null);
      setSecretChatSeed(null);
      setSelectedSecretChat(null);
      setComposeMode(null);
      setCurrentCall(null);
      setCurrentCallLinks([]);
      setRecentCallSignals([]);
      setJoinByLinkSeedToken(null);
      setPendingChatFocus(null);
      clearMediaBuckets();
      handledInitialLinkRef.current = false;
      setActiveRootTab("CHATS");
      return;
    }

    const sessionToken = session.token;
    const currentUserId = session.userId;
    let cancelled = false;
    let unsubscribeConnection: () => void = () => {};
    let unsubscribeCalls: () => void = () => {};
    const flushIntervalId = setInterval(() => {
      if (!wsService.isConnected()) {
        return;
      }
      void flushOutbox(sessionToken, currentUserId)
        .then(() => Promise.all([
          refreshChats(sessionToken, currentUserId),
          refreshActiveCalls(sessionToken, currentUserId)
        ]))
        .catch(() => undefined);
    }, 15000);

    void localDatabase.getChats(currentUserId)
      .then((cachedChats) => {
        if (cancelled || cachedChats.length === 0) {
          return;
        }
        setChats(cachedChats);
        syncOpenChatTargets(cachedChats);
      })
      .catch(() => undefined);

    wsService.connect(sessionToken, (message) => {
      if (message.senderId === currentUserId && message.clientMessageId) {
        replaceMessage(message.chatId, toQueuedMessageId(message.clientMessageId), message);
        void localDatabase.replaceQueuedMessage(
          currentUserId,
          message.chatId,
          toQueuedMessageId(message.clientMessageId),
          message
        ).catch(() => undefined);
      } else {
        upsertMessage(message);
        void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
      }
      void refreshChats(sessionToken, currentUserId).catch(() => undefined);
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
        void refreshActiveCalls(sessionToken, currentUserId).catch(() => undefined);
      }
    });

    unsubscribeConnection = wsService.onConnectionChange((connected) => {
      if (!connected) {
        return;
      }

      void flushOutbox(sessionToken, currentUserId)
        .then(() => Promise.all([
          refreshChats(sessionToken, currentUserId),
          refreshActiveCalls(sessionToken, currentUserId)
        ]))
        .catch(() => undefined);
    });

    void Promise.all([
      refreshChats(sessionToken, currentUserId),
      refreshActiveCalls(sessionToken, currentUserId)
    ]).catch(() => undefined);

    return () => {
      cancelled = true;
      unsubscribeConnection();
      unsubscribeCalls();
      clearInterval(flushIntervalId);
      wsService.disconnect();
    };
  }, [clearMediaBuckets, removeMessage, replaceMessage, session, setChats, upsertMessage]);

  useEffect(() => {
    if (!session || !currentCall || !isLiveCall(currentCall, session.userId)) {
      void callMediaSession.stop().catch(() => undefined);
      return;
    }

    const shouldStartMedia =
      currentCall.createdByUserId === session.userId || currentCall.status === "ACTIVE";
    if (!shouldStartMedia) {
      void callMediaSession.stop().catch(() => undefined);
      return;
    }

    void callMediaSession.start(
      currentCall,
      session.userId,
      session.token,
      sendCallSignalToUser
    ).catch(() => undefined);
  }, [currentCall, session]);

  useEffect(() => {
    if (!session || !currentCall) {
      setCurrentCallLinks([]);
      return;
    }
    void refreshCurrentCallLinks(currentCall, session.token).catch(() => undefined);
  }, [currentCall, session]);

  useEffect(() => {
    if (!session) {
      return;
    }

    wsService.setForegroundState(AppState.currentState === "active");
    const subscription = AppState.addEventListener("change", (nextState) => {
      const active = nextState === "active";
      wsService.setForegroundState(active);
      if (!active) {
        return;
      }

      void flushOutbox(session.token, session.userId)
        .then(() => Promise.all([
          refreshChats(session.token, session.userId),
          refreshActiveCalls(session.token, session.userId)
        ]))
        .catch(() => undefined);
    });

    return () => {
      subscription.remove();
    };
  }, [removeMessage, replaceMessage, session]);

  useEffect(() => {
    if (!session) {
      return;
    }

    let cancelled = false;
    void registerForPushNotificationsAsync()
      .then((pushToken) => {
        if (cancelled || !pushToken) {
          return;
        }
        return api.updateCurrentPushToken(session.token, {
          provider: "EXPO",
          pushToken
        });
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [session]);

  useEffect(() => {
    if (!session) {
      return;
    }

    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      const rawChatId = response.notification.request.content.data?.chatId;
      const rawTopicId = response.notification.request.content.data?.topicId;
      const chatId = typeof rawChatId === "string" ? rawChatId : null;
      const topicId =
        typeof rawTopicId === "string" && rawTopicId.trim().length > 0 ? rawTopicId : null;
      if (!chatId) {
        return;
      }
      void openChatFromNotification(session.token, chatId, session.userId, topicId).catch(() => undefined);
    });

    return () => {
      subscription.remove();
    };
  }, [session, chats]);

  useEffect(() => {
    if ((activeRootTab === "CALLS" && !callsEnabled) || (activeRootTab === "STORIES" && !storiesEnabled)) {
      setActiveRootTab("CHATS");
    }
  }, [activeRootTab, callsEnabled, setActiveRootTab, storiesEnabled]);

  useEffect(() => {
    if (!session) {
      return;
    }

    const handleIncomingUrl = (incomingUrl: string | null | undefined) => {
      const parsed = parseAlexDeepLink(incomingUrl);
      if (!parsed) {
        return;
      }

      if (parsed.type === "JOIN") {
        setActiveRootTab("CHATS");
        setJoinByLinkSeedToken(parsed.token);
        setShowJoinByLink(true);
        return;
      }

      if (parsed.type === "CALL") {
        if (!callsEnabled) {
          return;
        }
        setActiveRootTab("CALLS");
        void joinCallByLink(parsed.token).catch(() => undefined);
        return;
      }

      setActiveRootTab("CHATS");
      void openChatFromNotification(session.token, parsed.chatId, session.userId, parsed.topicId).catch(
        () => undefined
      );
    };

    if (!handledInitialLinkRef.current) {
      handledInitialLinkRef.current = true;
      void Linking.getInitialURL()
        .then((incomingUrl) => {
          handleIncomingUrl(incomingUrl);
        })
        .catch(() => undefined);
    }

    const subscription = Linking.addEventListener("url", ({ url }) => {
      handleIncomingUrl(url);
    });

    return () => {
      subscription.remove();
    };
  }, [callsEnabled, session]);

  if (!session) {
    return <AuthScreen />;
  }

  if (currentCall) {
    return (
      <CallScreen
        call={currentCall}
        callLinks={currentCallLinks}
        chatPhotoUrl={deriveCallPhoto(currentCall, chats, session.userId)}
        chatTitle={deriveCallTitle(currentCall, chats, session.userId)}
        currentUserId={session.userId}
        mediaState={callMediaState}
        onAccept={() => void acceptCurrentCall().catch(() => undefined)}
        onDecline={() => void declineCurrentCall().catch(() => undefined)}
        onLeave={() => void leaveCurrentCall().catch(() => undefined)}
        onToggleMute={() => {
          const nextEnabled = !callMediaState.localAudioEnabled;
          void callMediaSession.setAudioEnabled(nextEnabled).catch(() => undefined);
          void broadcastCallSignal(nextEnabled ? "UNMUTE" : "MUTE", { muted: !nextEnabled }).catch(() => undefined);
        }}
        onToggleSpeaker={() => {
          const nextSpeakerOn = !callMediaState.speakerOn;
          callMediaSession.setSpeakerEnabled(nextSpeakerOn);
          void broadcastCallSignal(nextSpeakerOn ? "SPEAKER_ON" : "SPEAKER_OFF", { speakerOn: nextSpeakerOn }).catch(() => undefined);
        }}
        onToggleVideo={() => {
          const nextVideoEnabled = !callMediaState.localVideoEnabled;
          void callMediaSession.setVideoEnabled(nextVideoEnabled).catch(() => undefined);
          void broadcastCallSignal(nextVideoEnabled ? "CAMERA_ON" : "CAMERA_OFF", { videoEnabled: nextVideoEnabled }).catch(() => undefined);
        }}
        onToggleScreenShare={() => {
          void toggleCurrentScreenShare().catch(() => undefined);
        }}
        onSetAdaptationProfile={(profile) => {
          void callMediaSession.setAdaptationProfile(profile).catch(() => undefined);
        }}
        onCreateCallLink={(kind) => {
          void createCurrentCallLink(kind).catch(() => undefined);
        }}
        onModerateParticipant={(userId, payload) => {
          void moderateCurrentCallParticipant(userId, payload).catch(() => undefined);
        }}
        recentSignals={recentCallSignals}
      />
    );
  }

  if (composeMode) {
    return (
      <CreateChatScreen
        mode={composeMode}
        onClose={() => setComposeMode(null)}
        onCreated={(chat) => {
          upsertChat(chat);
          setComposeMode(null);
          openChat(chat);
        }}
        token={session.token}
      />
    );
  }

  if (showBotDeveloper) {
    return (
      <BotDeveloperScreen
        onClose={() => setShowBotDeveloper(false)}
        token={session.token}
      />
    );
  }

  if (showSessions) {
    return (
      <SessionsScreen
        currentSessionId={session.sessionId}
        onClose={() => setShowSessions(false)}
        token={session.token}
      />
    );
  }

  if (showGlobalSearch) {
    return (
      <GlobalSearchScreen
        onClose={() => setShowGlobalSearch(false)}
        onOpenChat={(chat) => {
          upsertChat(chat);
          setShowGlobalSearch(false);
          openChat(chat);
        }}
        onOpenMessageResult={(chat, message) => {
          upsertChat(chat);
          setShowGlobalSearch(false);
          if (chat.forumEnabled && message.topicId) {
            void openChatFromNotification(
              session.token,
              chat.chatId,
              session.userId,
              message.topicId,
              {
                messageId: message.messageId,
                createdAt: message.createdAt
              }
            ).catch(() => undefined);
            return;
          }
          openChat(chat, {
            messageId: message.messageId,
            createdAt: message.createdAt
          });
        }}
        token={session.token}
      />
    );
  }

  if (showCreateStory) {
    return (
      <CreateStoryScreen
        onClose={() => setShowCreateStory(false)}
        onCreated={(_story: Story) => {
          setShowCreateStory(false);
          if (storiesEnabled) {
            setActiveRootTab("STORIES");
          }
        }}
        token={session.token}
      />
    );
  }

  if (showJoinByLink) {
    return (
      <JoinChatByLinkScreen
        initialInviteToken={joinByLinkSeedToken}
        onClose={() => {
          setShowJoinByLink(false);
          setJoinByLinkSeedToken(null);
        }}
        onJoined={(chat) => {
          upsertChat(chat);
          setShowJoinByLink(false);
          setJoinByLinkSeedToken(null);
          openChat(chat);
        }}
        token={session.token}
      />
    );
  }

  if (mediaViewer) {
    return (
      <MediaViewerScreen
        attachments={mediaViewer.attachments}
        chatTitle={mediaViewer.chatTitle}
        initialAttachmentId={mediaViewer.initialAttachmentId}
        onClose={() => setMediaViewer(null)}
        token={session.token}
      />
    );
  }

  if (sharedMediaChat) {
    return (
      <SharedMediaScreen
        chat={sharedMediaChat}
        onClose={() => setSharedMediaChat(null)}
        onOpenMediaViewer={(payload) => {
          setMediaViewer(payload);
        }}
        token={session.token}
      />
    );
  }

  if (selectedSecretChat) {
    return (
      <SecretChatScreen
        currentUserId={session.userId}
        onBack={() => setSelectedSecretChat(null)}
        onSummaryChange={(secretChat) => {
          if (["DECLINED", "CLOSED"].includes(secretChat.status)) {
            setSelectedSecretChat(null);
            void secretChatLocalCleanup
              .purgeSecretChat(session.userId, secretChat.secretChatId)
              .catch(() => undefined);
            return;
          }
          setSelectedSecretChat(secretChat);
          void localDatabase.upsertSecretChat(session.userId, secretChat).catch(() => undefined);
        }}
        secretChat={selectedSecretChat}
        token={session.token}
      />
    );
  }

  if (showSecretChats) {
    return (
      <SecretChatsScreen
        currentUserId={session.userId}
        currentSessionId={session.sessionId}
        onClose={() => {
          setShowSecretChats(false);
          setSecretChatSeed(null);
          setActiveRootTab("CHATS");
        }}
        onOpenSecretChat={(secretChat) => setSelectedSecretChat(secretChat)}
        seedPeerDisplayName={secretChatSeed?.peerDisplayName ?? null}
        seedPeerUserId={secretChatSeed?.peerUserId ?? null}
        token={session.token}
      />
    );
  }

  if (showArchived) {
    return (
      <ArchivedChatsScreen
        onClose={() => setShowArchived(false)}
        onOpenChat={(chat) => {
          setShowArchived(false);
          openChat(chat);
        }}
        token={session.token}
      />
    );
  }

  if (showFolders) {
    return (
      <FoldersScreen
        onClose={() => setShowFolders(false)}
        token={session.token}
      />
    );
  }

  if (membersChat) {
    return (
      <MembersScreen
        chat={membersChat}
        currentUserId={session.userId}
        onOpenSharedMedia={(chat) => {
          setSharedMediaChat(chat);
        }}
        onChatUpdated={(updatedChat) => {
          upsertChat(updatedChat);
          setMembersChat(updatedChat);
          setSharedMediaChat((current) =>
            current?.chatId === updatedChat.chatId ? updatedChat : current
          );
          setSelectedChat((current) =>
            current?.chatId === updatedChat.chatId ? updatedChat : current
          );
        }}
        onChatLeft={(chatId) => {
          setSharedMediaChat((current) => (current?.chatId === chatId ? null : current));
          setMembersChat((current) => (current?.chatId === chatId ? null : current));
          setSelectedChat((current) => (current?.chatId === chatId ? null : current));
        }}
        onClose={() => setMembersChat(null)}
        onHistoryCleared={(chatId) => {
          setChatMessages(chatId, []);
        }}
        token={session.token}
      />
    );
  }

  if (selectedBotMiniApp) {
    return (
      <BotMiniAppScreen
        botUserId={selectedBotMiniApp.botUserId}
        chatId={selectedBotMiniApp.chatId}
        onClose={() => setSelectedBotMiniApp(null)}
        startParameter={selectedBotMiniApp.startParameter}
        title={selectedBotMiniApp.title}
        token={session.token}
      />
    );
  }

  if (selectedChat && selectedChat.forumEnabled && !selectedForumTopic && !selectedDiscussionThread) {
    return (
      <ForumTopicsScreen
        chat={selectedChat}
        currentUserId={session.userId}
        onBack={() => {
          setSelectedDiscussionThread(null);
          setSelectedChat(null);
          setSelectedForumTopic(null);
        }}
        onOpenTopic={(topic) => setSelectedForumTopic(topic)}
        onRefreshChats={() => refreshChats(session.token, session.userId).then(() => undefined)}
        token={session.token}
      />
    );
  }

  if (selectedChat) {
    return (
      <ChatScreen
        chat={selectedChat}
        currentUserId={session.userId}
        initialFocusMessage={
          pendingChatFocus?.chatId === selectedChat.chatId
            ? {
                messageId: pendingChatFocus.messageId,
                createdAt: pendingChatFocus.createdAt
              }
            : null
        }
        onBack={() => {
          if (selectedDiscussionThread) {
            const originChat =
              chats.find((chat) => chat.chatId === selectedDiscussionThread.originChatId) ?? null;
            setSelectedDiscussionThread(null);
            setSelectedForumTopic(null);
            setSelectedChat(originChat);
            return;
          }
          if (selectedForumTopic) {
            setSelectedForumTopic(null);
            return;
          }
          setSelectedChat(null);
        }}
        onConsumeInitialFocus={() => {
          setPendingChatFocus((current) =>
            current?.chatId === selectedChat.chatId ? null : current
          );
        }}
        onOpenMediaViewer={(payload) => {
          setMediaViewer(payload);
        }}
        onOpenMembers={() => setMembersChat(selectedChat)}
        onOpenSecretChat={
          secretChatsEnabled && selectedChat.chatType === "DIRECT" && selectedChat.peerUserId
            ? () => {
                setSecretChatSeed({
                  peerUserId: selectedChat.peerUserId!,
                  peerDisplayName: selectedChat.peerDisplayName ?? selectedChat.title
                });
                setSelectedSecretChat(null);
                setShowSecretChats(true);
              }
            : undefined
        }
        onOpenBotMiniApp={
          botsEnabled &&
          selectedChat.chatType === "DIRECT" &&
          selectedChat.peerIsBot &&
          selectedChat.peerUserId
            ? (botUserId, title, chatId, startParameter) =>
                setSelectedBotMiniApp({
                  botUserId,
                  chatId: chatId ?? null,
                  startParameter: startParameter ?? null,
                  title
                })
            : undefined
        }
        onRefreshChats={() => refreshChats(session.token, session.userId).then(() => undefined)}
        onStartCall={(kind) => {
          void startChatCall(selectedChat.chatId, kind);
        }}
        onOpenDiscussionThread={(message) => {
          void openDiscussionThread(message).catch(() => undefined);
        }}
        threadRootMessageId={selectedDiscussionThread?.rootMessageId ?? null}
        threadTitle={selectedDiscussionThread?.title ?? null}
        topic={selectedForumTopic}
        token={session.token}
      />
    );
  }

  return (
    <View style={styles.screen}>
      <View style={styles.rootContent}>
        {activeRootTab === "CHATS" ? (
          <ChatsListScreen
            featureFlags={{
              bots: botsEnabled,
              calls: callsEnabled,
              secretChats: secretChatsEnabled,
              stories: storiesEnabled
            }}
            onCreateChannel={() => setComposeMode("channel")}
            onOpenJoinByLink={() => {
              setJoinByLinkSeedToken(null);
              setShowJoinByLink(true);
            }}
            onOpenGlobalSearch={() => setShowGlobalSearch(true)}
            onCreateDirect={() => setComposeMode("direct")}
            onCreateGroup={() => setComposeMode("group")}
            onOpenCalls={() => {
              if (callsEnabled) {
                setActiveRootTab("CALLS");
              }
            }}
            onOpenSecretChats={() => {
              if (!secretChatsEnabled) {
                return;
              }
              setSecretChatSeed(null);
              setSelectedSecretChat(null);
              setShowSecretChats(true);
            }}
            onOpenArchived={() => setShowArchived(true)}
            onOpenChat={openChat}
            onOpenStories={() => {
              if (storiesEnabled) {
                setActiveRootTab("STORIES");
              }
            }}
            onCreateStory={() => setShowCreateStory(true)}
            onOpenContacts={() => setActiveRootTab("CONTACTS")}
            onOpenFolders={() => setShowFolders(true)}
            onOpenProfile={() => setActiveRootTab("SETTINGS")}
            onOpenSavedMessages={() => {
              void api.createSavedMessages(session.token).then((chat) => {
                upsertChat(chat);
                openChat(chat);
              }).catch(() => undefined);
            }}
          />
        ) : null}

        {activeRootTab === "CALLS" && callsEnabled ? (
          <CallsScreen
            currentUserId={session.userId}
            onCallBack={(chatId, kind) => {
              void startChatCall(chatId, kind);
            }}
            onClose={() => setActiveRootTab("CHATS")}
            onJoinCallLink={(rawToken) => {
              void joinCallByLink(rawToken).catch(() => undefined);
            }}
            onOpenChat={(chatId) => {
              void openChatFromNotification(session.token, chatId, session.userId).catch(
                () => undefined
              );
            }}
            token={session.token}
          />
        ) : null}

        {activeRootTab === "CONTACTS" ? (
          <ContactsScreen
            onClose={() => setActiveRootTab("CHATS")}
            onOpenChat={(chat) => {
              upsertChat(chat);
              openChat(chat);
            }}
            onOpenBotMiniApp={
              botsEnabled
                ? (botUserId, title, chatId, startParameter) =>
                    setSelectedBotMiniApp({
                      botUserId,
                      chatId: chatId ?? null,
                      startParameter: startParameter ?? null,
                      title
                    })
                : undefined
            }
            token={session.token}
          />
        ) : null}

        {activeRootTab === "STORIES" && storiesEnabled ? (
          <StoriesScreen
            onClose={() => setActiveRootTab("CHATS")}
            onCreateStory={() => {
              setShowCreateStory(true);
            }}
            token={session.token}
          />
        ) : null}

        {activeRootTab === "SETTINGS" ? (
          <ProfileScreen
            onClose={() => setActiveRootTab("CHATS")}
            onOpenBotDeveloper={
              botsEnabled
                ? () => {
                    setShowBotDeveloper(true);
                  }
                : undefined
            }
            onOpenSessions={() => {
              setShowSessions(true);
            }}
            token={session.token}
          />
        ) : null}
      </View>
      <RootTabBar
        activeTab={activeRootTab}
        hiddenTabs={{
          CALLS: !callsEnabled,
          STORIES: !storiesEnabled
        }}
        onSelectTab={setActiveRootTab}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1
  },
  rootContent: {
    flex: 1
  }
});
