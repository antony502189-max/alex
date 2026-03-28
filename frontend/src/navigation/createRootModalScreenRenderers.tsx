import React from "react";
import {
  RootAddAccountScreenSurface,
  RootArchivedScreenSurface,
  RootBotDeveloperScreenSurface,
  RootBotMiniAppScreenSurface,
  RootChatInfoScreenSurface,
  RootCreateChatScreenSurface,
  RootCreateStoryScreenSurface,
  RootFoldersScreenSurface,
  RootGlobalSearchScreenSurface,
  RootJoinByLinkScreenSurface,
  RootMediaViewerScreenSurface,
  RootSettingsSectionScreenSurface,
  RootSessionsScreenSurface,
  RootSharedMediaScreenSurface
} from "./RootSurfaceScreens";
import type {
  RootModalScreenRenderers,
  RootScreenRenderersInput
} from "./rootScreenRendererTypes";
import { handleParsedLinkIntent } from "./parsedLinkNavigation";
import type { Story } from "../types";

export function createRootModalScreenRenderers({
  chatInfoRoute,
  chats,
  composeMode,
  directCallsEnabled,
  groupCallsEnabled,
  joinByLinkSeedToken,
  joinCallByLink,
  modalRoute,
  mediaViewer,
  openChat,
  openChatFromNotification,
  selectedBotMiniApp,
  selectedChat,
  session,
  setActiveRootTab,
  setChatMessages,
  setMembersChat,
  setModalRoute,
  setPendingChatFocus,
  setPendingCreatedStoryFocus,
  setSelectedChat,
  startChatCall,
  sharedMediaChat,
  storiesEnabled,
  upsertChat
}: RootScreenRenderersInput): RootModalScreenRenderers {
  const mediaViewerChat =
    mediaViewer
      ? chats.find((chat) => chat.chatId === mediaViewer.chatId) ?? null
      : null;
  const settingsSection = modalRoute?.type === "SETTINGS_SECTION" ? modalRoute.section : null;

  function handleParsedLink(parsedLink: import("./deepLinks").ParsedDeepLink) {
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

  function renderCreateChatScreen() {
    return (
      <RootCreateChatScreenSurface
        composeMode={composeMode}
        onClose={() => setModalRoute(null)}
        onCreated={(chat) => {
          upsertChat(chat);
          setModalRoute(null);
          openChat(chat);
        }}
        session={session}
      />
    );
  }

  function renderAddAccountScreen() {
    return (
      <RootAddAccountScreenSurface
        onAuthenticated={() => setModalRoute(null)}
        onCancel={() => setModalRoute(null)}
      />
    );
  }

  function renderBotDeveloperScreen() {
    return (
      <RootBotDeveloperScreenSurface
        onClose={() => setModalRoute(null)}
        session={session}
      />
    );
  }

  function renderSessionsScreen() {
    return (
      <RootSessionsScreenSurface
        onClose={() => setModalRoute(null)}
        session={session}
      />
    );
  }

  function renderSettingsSectionScreen() {
    return (
      <RootSettingsSectionScreenSurface
        onClose={() => setModalRoute(null)}
        onOpenAddAccount={() =>
          setModalRoute({
            type: "AUTH",
            intent: "ADD_ACCOUNT"
          })
        }
        onOpenBotDeveloper={() => setModalRoute({ type: "BOT_DEVELOPER" })}
        onOpenSessions={() => setModalRoute({ type: "SESSIONS" })}
        section={settingsSection}
        session={session}
      />
    );
  }

  function renderGlobalSearchScreen() {
    if (!session) {
      return null;
    }

    return (
      <RootGlobalSearchScreenSurface
        availableChats={chats}
        onClose={() => setModalRoute(null)}
        onOpenChat={(chat) => {
          upsertChat(chat);
          setModalRoute(null);
          openChat(chat);
        }}
        onOpenMessageResult={(chat, message) => {
          upsertChat(chat);
          setModalRoute(null);
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
        onOpenParsedLink={handleParsedLink}
        session={session}
      />
    );
  }

  function renderCreateStoryScreen() {
    return (
      <RootCreateStoryScreenSurface
        onClose={() => setModalRoute(null)}
        onCreated={(story: Story) => {
          setModalRoute(null);
          setPendingCreatedStoryFocus({
            ownerUserId: story.ownerUserId,
            storyId: story.storyId
          });
          if (storiesEnabled) {
            setActiveRootTab("STORIES");
          }
        }}
        session={session}
      />
    );
  }

  function renderJoinByLinkScreen() {
    return (
      <RootJoinByLinkScreenSurface
        availableChats={chats}
        joinByLinkSeedToken={joinByLinkSeedToken}
        onClose={() => setModalRoute(null)}
        onOpenDiscoveryChat={(chatId) => {
          setModalRoute(null);
          const discoveredChat = chats.find((chat) => chat.chatId === chatId) ?? null;
          if (discoveredChat) {
            openChat(discoveredChat);
            return;
          }
          if (!session) {
            return;
          }
          void openChatFromNotification(session.token, chatId, session.userId).catch(
            () => undefined
          );
        }}
        onJoined={(chat) => {
          upsertChat(chat);
          setModalRoute(null);
          openChat(chat);
        }}
        onOpenParsedLink={handleParsedLink}
        session={session}
      />
    );
  }

  function renderMediaViewerScreen() {
    return (
      <RootMediaViewerScreenSurface
        mediaViewer={mediaViewer}
        onClose={() =>
          setModalRoute(
            mediaViewer?.returnToSharedMediaChatId
              ? {
                  type: "SHARED_MEDIA",
                  chatId: mediaViewer.returnToSharedMediaChatId
                }
              : null
          )
        }
        onOpenMessage={(messageId, createdAt) => {
          if (mediaViewer?.returnToSharedMediaChatId && sharedMediaChat) {
            openChat(sharedMediaChat, {
              messageId,
              createdAt
            });
            return;
          }

          if (!mediaViewer) {
            return;
          }

          if (selectedChat?.chatId === mediaViewer.chatId) {
            setModalRoute(null);
            setActiveRootTab("CHATS");
            setPendingChatFocus({
              chatId: mediaViewer.chatId,
              messageId,
              createdAt
            });
            return;
          }

          if (mediaViewerChat) {
            openChat(mediaViewerChat, {
              messageId,
              createdAt
            });
          }
        }}
        session={session}
      />
    );
  }

  function renderSharedMediaScreen() {
    return (
      <RootSharedMediaScreenSurface
        onClose={() => setModalRoute(null)}
        onOpenMessage={(messageId, createdAt) => {
          if (!sharedMediaChat) {
            return;
          }
          openChat(sharedMediaChat, {
            messageId,
            createdAt
          });
        }}
        onOpenMediaViewer={(payload) => {
          if (!sharedMediaChat) {
            return;
          }
          setModalRoute({
            type: "MEDIA_VIEWER",
            attachments: payload.attachments,
            attachmentSources: payload.attachmentSources,
            attachmentId: payload.initialAttachmentId,
            chatId: sharedMediaChat.chatId,
            chatTitle: payload.chatTitle,
            returnToSharedMediaChatId: sharedMediaChat.chatId
          });
        }}
        onOpenParsedLink={handleParsedLink}
        session={session}
        sharedMediaChat={sharedMediaChat}
      />
    );
  }

  function renderChatInfoScreen() {
    const chatInfoChat =
      chatInfoRoute
        ? chats.find((chat) => chat.chatId === chatInfoRoute.chatId) ?? null
        : null;

    return (
      <RootChatInfoScreenSurface
        chatInfoChat={chatInfoChat}
        directCallsEnabled={directCallsEnabled}
        groupCallsEnabled={groupCallsEnabled}
        onChatUpdated={(updatedChat) => {
          upsertChat(updatedChat);
          setSelectedChat((current) =>
            current?.chatId === updatedChat.chatId ? updatedChat : current
          );
        }}
        onChatLeft={(chatId) => {
          setModalRoute(null);
          setMembersChat((current) => (current?.chatId === chatId ? null : current));
          setSelectedChat((current) => (current?.chatId === chatId ? null : current));
        }}
        onClose={() => setModalRoute(null)}
        onHistoryCleared={(chatId) => {
          setChatMessages(chatId, []);
        }}
        onOpenDiscussionChat={(discussionChatId) => {
          setModalRoute(null);
          const discussionChat =
            chats.find((chat) => chat.chatId === discussionChatId) ?? null;
          if (discussionChat) {
            openChat(discussionChat);
            return;
          }
          if (!session) {
            return;
          }
          void openChatFromNotification(
            session.token,
            discussionChatId,
            session.userId
          ).catch(() => undefined);
        }}
        onOpenMembers={(chat) => {
          setModalRoute(null);
          setMembersChat(chat);
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
        onStartCall={(kind) => {
          if (!chatInfoChat) {
            return;
          }
          setModalRoute(null);
          void startChatCall(chatInfoChat.chatId, kind).catch(() => undefined);
        }}
        onOpenSharedMedia={(chat) =>
          setModalRoute({
            type: "SHARED_MEDIA",
            chatId: chat.chatId
          })
        }
        session={session}
      />
    );
  }

  function renderArchivedScreen() {
    return (
      <RootArchivedScreenSurface
        onClose={() => setModalRoute(null)}
        onOpenChat={(chat) => {
          setModalRoute(null);
          openChat(chat);
        }}
        session={session}
      />
    );
  }

  function renderFoldersScreen() {
    return (
      <RootFoldersScreenSurface
        onClose={() => setModalRoute(null)}
        session={session}
      />
    );
  }

  function renderBotMiniAppScreen() {
    return (
      <RootBotMiniAppScreenSurface
        onClose={() => setModalRoute(null)}
        selectedBotMiniApp={selectedBotMiniApp}
        session={session}
      />
    );
  }

  return {
    renderCreateChatScreen,
    renderAddAccountScreen,
    renderBotDeveloperScreen,
    renderSessionsScreen,
    renderSettingsSectionScreen,
    renderGlobalSearchScreen,
    renderCreateStoryScreen,
    renderJoinByLinkScreen,
    renderMediaViewerScreen,
    renderSharedMediaScreen,
    renderChatInfoScreen,
    renderArchivedScreen,
    renderFoldersScreen,
    renderBotMiniAppScreen
  };
}
