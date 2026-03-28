import React from "react";
import { api } from "../services/api";
import { callMediaSession } from "../services/callMediaSession";
import {
  deriveCallPhoto,
  deriveCallTitle
} from "./rootCallUtils";
import { handleParsedLinkIntent } from "./parsedLinkNavigation";
import { AuthScreen } from "../screens/AuthScreen";
import { RootMainTabsScreen } from "./RootMainTabsScreen";
import {
  RootCallScreenSurface,
  RootChatScreenSurface,
  RootForumTopicsScreenSurface,
  RootMembersScreenSurface
} from "./RootSurfaceScreens";
import type {
  RootPrimaryScreenRenderers,
  RootScreenRenderersInput
} from "./rootScreenRendererTypes";

export function createRootPrimaryScreenRenderers({
  acceptCurrentCall,
  activeRootTab,
  botsEnabled,
  broadcastCallSignal,
  callMediaState,
  callsEnabled,
  callJoinLinksEnabled,
  callModerationEnabled,
  callScreenSharingEnabled,
  chats,
  createCurrentCallLink,
  currentCall,
  currentCallLinks,
  declineCurrentCall,
  directCallsEnabled,
  groupCallsEnabled,
  joinCallByLink,
  leaveCurrentCall,
  membersChat,
  moderateCurrentCallParticipant,
  openChat,
  openChatFromNotification,
  openDiscussionThread,
  pendingChatFocus,
  pendingCreatedStoryFocus,
  recentCallSignals,
  refreshChats,
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
  startChatCall,
  storiesEnabled,
  toggleCurrentScreenShare,
  upsertChat
}: RootScreenRenderersInput): RootPrimaryScreenRenderers {
  function handleRootParsedLink(parsedLink: import("./deepLinks").ParsedDeepLink) {
    handleParsedLinkIntent({
      availableChats: chats,
      joinCallByLink,
      openChat,
      openChatFromNotification,
      parsedLink,
      session,
      setActiveRootTab,
      setModalRoute
    });
  }

  function renderAuthScreen() {
    return <AuthScreen />;
  }

  function renderMainTabsScreen() {
    if (!session) {
      return null;
    }

    return (
      <RootMainTabsScreen
        activeRootTab={activeRootTab}
        availableChats={chats}
        botsEnabled={botsEnabled}
        callsEnabled={callsEnabled}
        callJoinLinksEnabled={callJoinLinksEnabled}
        onJoinCallLink={(rawToken) => {
          void joinCallByLink(rawToken).catch(() => undefined);
        }}
        onOpenCallParsedLink={handleRootParsedLink}
        onOpenAddAccount={() => {
          setModalRoute({
            type: "AUTH",
            intent: "ADD_ACCOUNT"
          });
        }}
        onOpenArchived={() => setModalRoute({ type: "ARCHIVED" })}
        onOpenBotDeveloper={() => {
          setModalRoute({ type: "BOT_DEVELOPER" });
        }}
        onOpenCallChat={(chatId) => {
          void openChatFromNotification(session.token, chatId, session.userId).catch(
            () => undefined
          );
        }}
        onOpenChat={openChat}
        onOpenContactBotMiniApp={(botUserId, title, chatId, startParameter) =>
          setModalRoute({
            type: "BOT_MINI_APP",
            botUserId,
            chatId: chatId ?? null,
            startParameter: startParameter ?? null,
            title
          })
        }
        onOpenContactChat={(chat) => {
          upsertChat(chat);
          openChat(chat);
        }}
        onOpenCreateChat={(mode) => setModalRoute({ type: "CREATE_CHAT", mode })}
        onOpenCreateStory={() => setModalRoute({ type: "CREATE_STORY" })}
        onOpenFolders={() => setModalRoute({ type: "FOLDERS" })}
        onOpenGlobalSearch={() => setModalRoute({ type: "GLOBAL_SEARCH" })}
        onOpenJoinByLink={() => setModalRoute({ type: "JOIN_BY_LINK" })}
        onOpenSavedMessages={() => {
          void api.createSavedMessages(session.token)
            .then((chat) => {
              upsertChat(chat);
              openChat(chat);
            })
            .catch(() => undefined);
        }}
        onOpenSessions={() => {
          setModalRoute({ type: "SESSIONS" });
        }}
        onOpenSettingsSection={(section) => {
          setModalRoute({ type: "SETTINGS_SECTION", section });
        }}
        onConsumeCreatedStoryFocus={() => setPendingCreatedStoryFocus(null)}
        onSelectRootTab={setActiveRootTab}
        pendingCreatedStoryFocus={pendingCreatedStoryFocus}
        onStartChatCall={(chatId, kind) => {
          void startChatCall(chatId, kind);
        }}
        session={session}
        storiesEnabled={storiesEnabled}
      />
    );
  }

  function renderChatScreen() {
    if (!session) {
      return null;
    }

    function handleChatParsedLink(parsedLink: import("./deepLinks").ParsedDeepLink) {
      handleRootParsedLink(parsedLink);
    }

    return (
      <RootChatScreenSurface
        botsEnabled={botsEnabled}
        chats={chats}
        directCallsEnabled={directCallsEnabled}
        groupCallsEnabled={groupCallsEnabled}
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
            current?.chatId === selectedChat?.chatId ? null : current
          );
        }}
        onOpenChatInfo={() => {
          if (!selectedChat || selectedChat.chatType === "SAVED") {
            return;
          }
          setModalRoute({
            type: "CHAT_INFO",
            chatId: selectedChat.chatId
          });
        }}
        onOpenBotMiniApp={(botUserId, title, chatId, startParameter) =>
          setModalRoute({
            type: "BOT_MINI_APP",
            botUserId,
            chatId: chatId ?? null,
            startParameter: startParameter ?? null,
            title
          })
        }
        onOpenParsedLink={handleChatParsedLink}
        onOpenDiscussionThread={(message) => {
          void openDiscussionThread(message).catch(() => undefined);
        }}
        onOpenMediaViewer={(payload) => {
          if (!selectedChat) {
            return;
          }
          setModalRoute({
            type: "MEDIA_VIEWER",
            attachments: payload.attachments,
            attachmentSources: payload.attachmentSources,
            attachmentId: payload.initialAttachmentId,
            chatId: selectedChat.chatId,
            chatTitle: payload.chatTitle
          });
        }}
        onOpenMembers={() => {
          if (selectedChat) {
            setMembersChat(selectedChat);
          }
        }}
        onRefreshChats={() => {
          void refreshChats(session.token, session.userId).then(() => undefined);
        }}
        onStartCall={(kind) => {
          if (selectedChat) {
            void startChatCall(selectedChat.chatId, kind);
          }
        }}
        pendingChatFocus={pendingChatFocus}
        selectedChat={selectedChat}
        selectedDiscussionThread={selectedDiscussionThread}
        selectedForumTopic={selectedForumTopic}
        session={session}
      />
    );
  }

  function renderForumTopicsScreen() {
    if (!session) {
      return null;
    }

    return (
      <RootForumTopicsScreenSurface
        onBack={() => {
          setSelectedDiscussionThread(null);
          setSelectedChat(null);
          setSelectedForumTopic(null);
        }}
        onOpenTopic={setSelectedForumTopic}
        onRefreshChats={() => {
          void refreshChats(session.token, session.userId).then(() => undefined);
        }}
        selectedChat={selectedChat}
        selectedDiscussionThread={selectedDiscussionThread}
        selectedForumTopic={selectedForumTopic}
        session={session}
      />
    );
  }

  function renderMembersScreen() {
    if (!session) {
      return null;
    }

    return (
      <RootMembersScreenSurface
        membersChat={membersChat}
        onChatUpdated={(updatedChat) => {
          upsertChat(updatedChat);
          setMembersChat(updatedChat);
          setSelectedChat((current) =>
            current?.chatId === updatedChat.chatId ? updatedChat : current
          );
        }}
        onChatLeft={(chatId) => {
          setModalRoute((current) =>
            current?.type === "SHARED_MEDIA" && current.chatId === chatId ? null : current
          );
          setMembersChat((current) => (current?.chatId === chatId ? null : current));
          setSelectedChat((current) => (current?.chatId === chatId ? null : current));
        }}
        onClose={() => setMembersChat(null)}
        onHistoryCleared={(chatId) => {
          setChatMessages(chatId, []);
        }}
        onOpenDiscussionChat={(discussionChatId) => {
          setMembersChat(null);
          const discussionChat =
            chats.find((chat) => chat.chatId === discussionChatId) ?? null;
          if (discussionChat) {
            openChat(discussionChat);
            return;
          }
          void openChatFromNotification(session.token, discussionChatId, session.userId).catch(
            () => undefined
          );
        }}
        onOpenSharedMedia={(chat) => {
          setModalRoute({
            type: "SHARED_MEDIA",
            chatId: chat.chatId
          });
        }}
        session={session}
      />
    );
  }

  function renderCallScreen() {
    if (!session) {
      return null;
    }

    return (
      <RootCallScreenSurface
        callMediaState={callMediaState}
        callJoinLinksEnabled={callJoinLinksEnabled}
        callModerationEnabled={callModerationEnabled}
        callScreenSharingEnabled={callScreenSharingEnabled}
        chats={chats}
        currentCall={currentCall}
        currentCallLinks={currentCallLinks}
        deriveCallPhoto={deriveCallPhoto}
        deriveCallTitle={deriveCallTitle}
        onAccept={() => void acceptCurrentCall().catch(() => undefined)}
        onCreateCallLink={(kind) => {
          void createCurrentCallLink(kind).catch(() => undefined);
        }}
        onDecline={() => void declineCurrentCall().catch(() => undefined)}
        onLeave={() => void leaveCurrentCall().catch(() => undefined)}
        onModerateParticipant={(userId, payload) => {
          void moderateCurrentCallParticipant(userId, payload).catch(() => undefined);
        }}
        onSetAdaptationProfile={(profile) => {
          void callMediaSession.setAdaptationProfile(profile).catch(() => undefined);
        }}
        onToggleMute={() => {
          const nextEnabled = !callMediaState.localAudioEnabled;
          void callMediaSession.setAudioEnabled(nextEnabled).catch(() => undefined);
          void broadcastCallSignal(nextEnabled ? "UNMUTE" : "MUTE", {
            muted: !nextEnabled
          }).catch(() => undefined);
        }}
        onToggleScreenShare={() => {
          void toggleCurrentScreenShare().catch(() => undefined);
        }}
        onToggleSpeaker={() => {
          const nextSpeakerOn = !callMediaState.speakerOn;
          callMediaSession.setSpeakerEnabled(nextSpeakerOn);
          void broadcastCallSignal(nextSpeakerOn ? "SPEAKER_ON" : "SPEAKER_OFF", {
            speakerOn: nextSpeakerOn
          }).catch(() => undefined);
        }}
        onToggleVideo={() => {
          const nextVideoEnabled = !callMediaState.localVideoEnabled;
          void callMediaSession.setVideoEnabled(nextVideoEnabled).catch(() => undefined);
          void broadcastCallSignal(nextVideoEnabled ? "CAMERA_ON" : "CAMERA_OFF", {
            videoEnabled: nextVideoEnabled
          }).catch(() => undefined);
        }}
        recentCallSignals={recentCallSignals}
        session={session}
      />
    );
  }

  return {
    renderAuthScreen,
    renderMainTabsScreen,
    renderChatScreen,
    renderForumTopicsScreen,
    renderMembersScreen,
    renderCallScreen
  };
}
