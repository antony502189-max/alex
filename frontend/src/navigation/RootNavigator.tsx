import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  NavigationContainer,
  useNavigationContainerRef
} from "@react-navigation/native";
import { buildDesiredRoutes, routeStacksEqual } from "./rootRouteState";
import {
  DiscussionThreadSelection,
  MessageFocusTarget
} from "./rootNavigatorState";
import { RootStackScreens } from "./RootStackScreens";
import { useRootCallEffects } from "./useRootCallEffects";
import { useRootCallActions } from "./useRootCallActions";
import { useRootChatActions } from "./useRootChatActions";
import { useRootExternalEntryPoints } from "./useRootExternalEntryPoints";
import { useRootNavigationSync } from "./useRootNavigationSync";
import { useRootPresenceSync } from "./useRootPresenceSync";
import { useRootScreenRenderers } from "./useRootScreenRenderers";
import { useRootSessionBootstrap } from "./useRootSessionBootstrap";
import { useMediaStore } from "../store/useMediaStore";
import { useNavigationStore } from "../store/useNavigationStore";
import { callMediaSession } from "../services/callMediaSession";
import { useAppStore } from "../store/useAppStore";
import type { AppModalRoute, RootStackParamList } from "./types";
import type { StoryFocusTarget } from "./rootScreenRendererTypes";
import type {
  CallJoinLink,
  CallMediaState,
  CallSession,
  CallSignalEvent,
  ChatSummary,
  ForumTopic
} from "../types";

export function RootNavigator() {
  const session = useAppStore((state) => state.session);
  const featureProfile = useAppStore((state) => state.featureProfile);
  const chats = useAppStore((state) => state.chats);
  const setChats = useAppStore((state) => state.setChats);
  const setChatMessages = useAppStore((state) => state.setChatMessages);
  const upsertMessage = useAppStore((state) => state.upsertMessage);
  const upsertChat = useAppStore((state) => state.upsertChat);
  const replaceMessage = useAppStore((state) => state.replaceMessage);
  const removeMessage = useAppStore((state) => state.removeMessage);
  const activeRootTab = useNavigationStore((state) => state.activeRootTab);
  const setActiveRootTab = useNavigationStore((state) => state.setActiveRootTab);
  const setTrackedModalRoute = useNavigationStore((state) => state.setModalRoute);
  const setTrackedChatRoute = useNavigationStore((state) => state.setChatRoute);
  const resetNavigationState = useNavigationStore((state) => state.reset);
  const clearMediaBuckets = useMediaStore((state) => state.clearAll);
  const storiesEnabled = Boolean(featureProfile?.stories);
  const callsEnabled = featureProfile ? featureProfile.calls : true;
  const directCallsEnabled =
    callsEnabled && (featureProfile ? featureProfile.directCalls : true);
  const groupCallsEnabled = callsEnabled && Boolean(featureProfile?.groupCalls);
  const callJoinLinksEnabled = callsEnabled && Boolean(featureProfile?.callJoinLinks);
  const callModerationEnabled = callsEnabled && Boolean(featureProfile?.callModeration);
  const callScreenSharingEnabled =
    callsEnabled && Boolean(featureProfile?.callScreenSharing);
  const botsEnabled = Boolean(featureProfile?.bots);
  const [selectedChat, setSelectedChat] = useState<ChatSummary | null>(null);
  const [membersChat, setMembersChat] = useState<ChatSummary | null>(null);
  const [modalRoute, setModalRoute] = useState<AppModalRoute | null>(null);
  const [selectedForumTopic, setSelectedForumTopic] = useState<ForumTopic | null>(null);
  const [selectedDiscussionThread, setSelectedDiscussionThread] =
    useState<DiscussionThreadSelection | null>(null);
  const [pendingChatFocus, setPendingChatFocus] = useState<MessageFocusTarget | null>(null);
  const [pendingCreatedStoryFocus, setPendingCreatedStoryFocus] = useState<StoryFocusTarget | null>(null);
  const [currentCall, setCurrentCall] = useState<CallSession | null>(null);
  const [currentCallLinks, setCurrentCallLinks] = useState<CallJoinLink[]>([]);
  const [recentCallSignals, setRecentCallSignals] = useState<CallSignalEvent[]>([]);
  const [callMediaState, setCallMediaState] = useState<CallMediaState>(callMediaSession.getState());
  const currentCallRef = useRef<CallSession | null>(null);
  const lastAuthenticatedUserIdRef = useRef<string | null>(null);
  const handledInitialLinkRef = useRef(false);
  const navigationRef = useNavigationContainerRef<RootStackParamList>();
  const composeMode = modalRoute?.type === "CREATE_CHAT" ? modalRoute.mode : null;
  const joinByLinkSeedToken = modalRoute?.type === "JOIN_BY_LINK" ? modalRoute.seedToken ?? null : null;
  const selectedBotMiniApp = modalRoute?.type === "BOT_MINI_APP" ? modalRoute : null;
  const chatInfoRoute = modalRoute?.type === "CHAT_INFO" ? modalRoute : null;
  const mediaViewer = modalRoute?.type === "MEDIA_VIEWER" ? modalRoute : null;
  const sharedMediaChatId =
    modalRoute?.type === "SHARED_MEDIA"
      ? modalRoute.chatId
      : mediaViewer?.returnToSharedMediaChatId ?? null;
  const sharedMediaChat =
    sharedMediaChatId
      ? chats.find((chat) => chat.chatId === sharedMediaChatId) ?? null
      : null;
  const {
    acceptCurrentCall,
    broadcastCallSignal,
    createCurrentCallLink,
    declineCurrentCall,
    joinCallByLink,
    leaveCurrentCall,
    moderateCurrentCallParticipant,
    refreshActiveCalls,
    refreshCurrentCallLinks,
    sendCallSignalToUser,
    startChatCall,
    toggleCurrentScreenShare
  } = useRootCallActions({
    callMediaState,
    currentCallRef,
    session,
    setCurrentCall,
    setCurrentCallLinks,
    setRecentCallSignals
  });
  const {
    flushOutbox,
    openChat,
    openChatFromNotification,
    openDiscussionThread,
    refreshChats,
    syncOpenChatTargets
  } = useRootChatActions({
    chats,
    removeMessage,
    replaceMessage,
    selectedChat,
    session,
    setActiveRootTab,
    setChats,
    setMembersChat,
    setModalRoute,
    setPendingChatFocus,
    setSelectedChat,
    setSelectedDiscussionThread,
    setSelectedForumTopic
  });

  const desiredRoutes = useMemo(() =>
    buildDesiredRoutes({
      composeMode,
      currentCall,
      mediaViewer:
        mediaViewer
          ? {
              attachmentId: mediaViewer.attachmentId,
              chatId: mediaViewer.chatId,
              returnToSharedMediaChatId: mediaViewer.returnToSharedMediaChatId ?? null
            }
          : null,
      membersChat,
      modalRoute,
      selectedBotMiniApp,
      selectedChat,
      selectedDiscussionThread,
      selectedForumTopic,
      session,
      sharedMediaChat,
    }), [
    composeMode,
    currentCall,
    mediaViewer,
    membersChat,
    modalRoute,
    selectedBotMiniApp,
    selectedChat,
    selectedDiscussionThread,
    selectedForumTopic,
    session,
    sharedMediaChat
  ]);

  useRootNavigationSync({
    desiredRoutes,
    membersChat,
    modalRoute,
    navigationRef,
    selectedChat,
    selectedDiscussionThread,
    selectedForumTopic,
    setTrackedChatRoute,
    setTrackedModalRoute
  });

  useRootCallEffects({
    currentCall,
    currentCallRef,
    refreshCurrentCallLinks,
    sendCallSignalToUser,
    session,
    setCallMediaState,
    setCurrentCallLinks
  });

  useRootExternalEntryPoints({
    activeRootTab,
    callsEnabled,
    handledInitialLinkRef,
    onForegroundResume: async (activeSession) => {
      await flushOutbox(activeSession.token, activeSession.userId);
      await refreshActiveCalls(activeSession.token, activeSession.userId);
    },
    onJoinCallByLink: joinCallByLink,
    onOpenChatFromNotification: async (activeSession, chatId, topicId) => {
      await openChatFromNotification(
        activeSession.token,
        chatId,
        activeSession.userId,
        topicId
      );
    },
    session,
    setActiveRootTab,
    setModalRoute,
    storiesEnabled
  });

  useRootSessionBootstrap({
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
  });

  useRootPresenceSync({
    chats,
    session,
    setChats
  });

  useEffect(() => {
    if (session?.userId) {
      lastAuthenticatedUserIdRef.current = session.userId;
    }
  }, [session]);
  const {
    renderAddAccountScreen,
    renderArchivedScreen,
    renderAuthScreen,
    renderBotDeveloperScreen,
    renderBotMiniAppScreen,
    renderCallScreen,
    renderChatScreen,
    renderChatInfoScreen,
    renderCreateChatScreen,
    renderCreateStoryScreen,
    renderFoldersScreen,
    renderForumTopicsScreen,
    renderGlobalSearchScreen,
    renderJoinByLinkScreen,
    renderMainTabsScreen,
    renderMediaViewerScreen,
    renderMembersScreen,
    renderSessionsScreen,
    renderSettingsSectionScreen,
    renderSharedMediaScreen
  } = useRootScreenRenderers({
    acceptCurrentCall,
    activeRootTab,
    botsEnabled,
    broadcastCallSignal,
    callMediaState,
    callsEnabled,
    callJoinLinksEnabled,
    callModerationEnabled,
    callScreenSharingEnabled,
    chatInfoRoute,
    chats,
    composeMode,
    createCurrentCallLink,
    currentCall,
    currentCallLinks,
    declineCurrentCall,
    directCallsEnabled,
    groupCallsEnabled,
    joinByLinkSeedToken,
    joinCallByLink,
    leaveCurrentCall,
    modalRoute,
    mediaViewer,
    membersChat,
    moderateCurrentCallParticipant,
    openChat,
    openChatFromNotification,
    openDiscussionThread,
    pendingChatFocus,
    pendingCreatedStoryFocus,
    recentCallSignals,
    refreshChats,
    selectedBotMiniApp,
    selectedChat,
    selectedDiscussionThread,
    selectedForumTopic,
    session,
    setActiveRootTab,
    setChatMessages,
    setMembersChat,
    setModalRoute,
    setPendingChatFocus,
    setPendingCreatedStoryFocus,
    setSelectedChat,
    setSelectedDiscussionThread,
    setSelectedForumTopic,
    sharedMediaChat,
    startChatCall,
    storiesEnabled,
    toggleCurrentScreenShare,
    upsertChat
  });

  return (
    <NavigationContainer
      onReady={() => {
        const currentRoutes = navigationRef.getRootState()?.routes;
        if (!routeStacksEqual(currentRoutes, desiredRoutes)) {
          navigationRef.resetRoot({
            index: desiredRoutes.length - 1,
            routes: desiredRoutes
          });
        }
      }}
      ref={navigationRef}
    >
      <RootStackScreens
        renderAddAccountScreen={renderAddAccountScreen}
        renderArchivedScreen={renderArchivedScreen}
        renderAuthScreen={renderAuthScreen}
        renderBotDeveloperScreen={renderBotDeveloperScreen}
        renderBotMiniAppScreen={renderBotMiniAppScreen}
        renderCallScreen={renderCallScreen}
        renderChatScreen={renderChatScreen}
        renderCreateChatScreen={renderCreateChatScreen}
        renderCreateStoryScreen={renderCreateStoryScreen}
        renderFoldersScreen={renderFoldersScreen}
        renderForumTopicsScreen={renderForumTopicsScreen}
        renderGlobalSearchScreen={renderGlobalSearchScreen}
        renderJoinByLinkScreen={renderJoinByLinkScreen}
        renderMainTabsScreen={renderMainTabsScreen}
        renderChatInfoScreen={renderChatInfoScreen}
        renderMediaViewerScreen={renderMediaViewerScreen}
        renderMembersScreen={renderMembersScreen}
        renderSessionsScreen={renderSessionsScreen}
        renderSettingsSectionScreen={renderSettingsSectionScreen}
        renderSharedMediaScreen={renderSharedMediaScreen}
      />
    </NavigationContainer>
  );
}
