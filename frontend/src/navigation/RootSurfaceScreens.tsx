import React from "react";
import { AuthScreen } from "../screens/AuthScreen";
import { ArchivedChatsScreen } from "../screens/ArchivedChatsScreen";
import { BotDeveloperScreen } from "../screens/BotDeveloperScreen";
import { BotMiniAppScreen } from "../screens/BotMiniAppScreen";
import { CallScreen } from "../screens/CallScreen";
import { ChatInfoScreen } from "../screens/ChatInfoScreen";
import { ChatScreen } from "../screens/ChatScreen";
import { CreateChatScreen } from "../screens/CreateChatScreen";
import { CreateStoryScreen } from "../screens/CreateStoryScreen";
import { FoldersScreen } from "../screens/FoldersScreen";
import { ForumTopicsScreen } from "../screens/ForumTopicsScreen";
import { GlobalSearchScreen } from "../screens/GlobalSearchScreen";
import { JoinChatByLinkScreen } from "../screens/JoinChatByLinkScreen";
import { MediaViewerScreen } from "../screens/MediaViewerScreen";
import { MembersScreen } from "../screens/MembersScreen";
import { SettingsSectionScreen } from "../screens/SettingsSectionScreen";
import { SessionsScreen } from "../screens/SessionsScreen";
import { SharedMediaScreen } from "../screens/SharedMediaScreen";
import type { ParsedDeepLink } from "./deepLinks";
import type {
  AuthSession,
  CallMediaState,
  CallSession,
  CallSignalEvent,
  ChatMessage,
  ChatSummary,
  ForumTopic,
  MessageAttachment,
  Story
} from "../types";

type DiscussionThreadSelection = {
  rootMessageId: string;
  originChatId: string;
  title: string | null;
};

type MessageFocusTarget = {
  chatId: string;
  messageId: string;
  createdAt: string;
};

type MediaViewerRoute = {
  attachments: MessageAttachment[];
  attachmentSources?: Array<{
    attachmentId: string;
    createdAt: string;
    messageId: string;
  }>;
  attachmentId: string;
  chatTitle: string;
};

type BotMiniAppRoute = {
  botUserId: string;
  chatId: string | null;
  startParameter: string | null;
  title: string;
};

export function RootChatScreenSurface({
  session,
  chats,
  selectedChat,
  selectedForumTopic,
  selectedDiscussionThread,
  pendingChatFocus,
  botsEnabled,
  directCallsEnabled,
  groupCallsEnabled,
  onBack,
  onConsumeInitialFocus,
  onOpenMediaViewer,
  onOpenChatInfo,
  onOpenMembers,
  onOpenBotMiniApp,
  onOpenParsedLink,
  onRefreshChats,
  onStartCall,
  onOpenDiscussionThread
}: {
  session: AuthSession;
  chats: ChatSummary[];
  selectedChat: ChatSummary | null;
  selectedForumTopic: ForumTopic | null;
  selectedDiscussionThread: DiscussionThreadSelection | null;
  pendingChatFocus: MessageFocusTarget | null;
  botsEnabled: boolean;
  directCallsEnabled: boolean;
  groupCallsEnabled: boolean;
  onBack: () => void;
  onConsumeInitialFocus: () => void;
  onOpenMediaViewer: (payload: {
    attachments: MessageAttachment[];
    attachmentSources?: Array<{
      attachmentId: string;
      createdAt: string;
      messageId: string;
    }>;
    initialAttachmentId: string;
    chatTitle: string;
  }) => void;
  onOpenChatInfo: () => void;
  onOpenMembers: () => void;
  onOpenBotMiniApp: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
  onRefreshChats: () => void;
  onStartCall: (kind: "VOICE" | "VIDEO") => void;
  onOpenDiscussionThread: (message: ChatMessage) => void;
}) {
  if (!selectedChat) {
    return null;
  }

  const canStartCalls =
    selectedChat.chatType === "DIRECT"
      ? directCallsEnabled
      : selectedChat.chatType === "GROUP" || selectedChat.chatType === "CHANNEL"
        ? groupCallsEnabled
        : false;

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
      onBack={onBack}
      onConsumeInitialFocus={onConsumeInitialFocus}
      onOpenChatInfo={selectedChat.chatType !== "SAVED" ? onOpenChatInfo : undefined}
      onOpenMediaViewer={onOpenMediaViewer}
      onOpenMembers={onOpenMembers}
      onOpenBotMiniApp={
        botsEnabled &&
        selectedChat.chatType === "DIRECT" &&
        selectedChat.peerIsBot &&
        selectedChat.peerUserId
          ? onOpenBotMiniApp
          : undefined
      }
      onOpenParsedLink={onOpenParsedLink}
      onRefreshChats={onRefreshChats}
      onStartCall={canStartCalls ? onStartCall : undefined}
      onOpenDiscussionThread={onOpenDiscussionThread}
      threadRootMessageId={selectedDiscussionThread?.rootMessageId ?? null}
      threadTitle={selectedDiscussionThread?.title ?? null}
      topic={selectedForumTopic}
      token={session.token}
    />
  );
}

export function RootForumTopicsScreenSurface({
  session,
  selectedChat,
  selectedForumTopic,
  selectedDiscussionThread,
  onBack,
  onOpenTopic,
  onRefreshChats
}: {
  session: AuthSession;
  selectedChat: ChatSummary | null;
  selectedForumTopic: ForumTopic | null;
  selectedDiscussionThread: DiscussionThreadSelection | null;
  onBack: () => void;
  onOpenTopic: (topic: ForumTopic) => void;
  onRefreshChats: () => void;
}) {
  if (
    !selectedChat ||
    !selectedChat.forumEnabled ||
    selectedForumTopic ||
    selectedDiscussionThread
  ) {
    return null;
  }

  return (
    <ForumTopicsScreen
      chat={selectedChat}
      currentUserId={session.userId}
      onBack={onBack}
      onOpenTopic={onOpenTopic}
      onRefreshChats={onRefreshChats}
      token={session.token}
    />
  );
}

export function RootMembersScreenSurface({
  session,
  membersChat,
  onOpenDiscussionChat,
  onOpenSharedMedia,
  onChatUpdated,
  onChatLeft,
  onClose,
  onHistoryCleared
}: {
  session: AuthSession;
  membersChat: ChatSummary | null;
  onOpenDiscussionChat: (chatId: string) => void;
  onOpenSharedMedia: (chat: ChatSummary) => void;
  onChatUpdated: (chat: ChatSummary) => void;
  onChatLeft: (chatId: string) => void;
  onClose: () => void;
  onHistoryCleared: (chatId: string) => void;
}) {
  if (!membersChat) {
    return null;
  }

  return (
    <MembersScreen
      chat={membersChat}
      currentUserId={session.userId}
      onOpenDiscussionChat={onOpenDiscussionChat}
      onOpenSharedMedia={onOpenSharedMedia}
      onChatUpdated={onChatUpdated}
      onChatLeft={onChatLeft}
      onClose={onClose}
      onHistoryCleared={onHistoryCleared}
      token={session.token}
    />
  );
}

export function RootChatInfoScreenSurface({
  chatInfoChat,
  directCallsEnabled,
  groupCallsEnabled,
  onChatUpdated,
  onChatLeft,
  onClose,
  onHistoryCleared,
  onOpenDiscussionChat,
  onOpenMembers,
  onOpenBotMiniApp,
  onStartCall,
  onOpenSharedMedia,
  session
}: {
  chatInfoChat: ChatSummary | null;
  directCallsEnabled: boolean;
  groupCallsEnabled: boolean;
  onChatUpdated: (chat: ChatSummary) => void;
  onChatLeft: (chatId: string) => void;
  onClose: () => void;
  onHistoryCleared: (chatId: string) => void;
  onOpenDiscussionChat: (chatId: string) => void;
  onOpenMembers: (chat: ChatSummary) => void;
  onOpenBotMiniApp: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
  onStartCall: (kind: "VOICE" | "VIDEO") => void;
  onOpenSharedMedia: (chat: ChatSummary) => void;
  session: AuthSession | null;
}) {
  if (!session || !chatInfoChat) {
    return null;
  }

  const canStartCalls =
    chatInfoChat.chatType === "DIRECT"
      ? directCallsEnabled
      : chatInfoChat.chatType === "GROUP" || chatInfoChat.chatType === "CHANNEL"
        ? groupCallsEnabled
        : false;

  return (
    <ChatInfoScreen
      chat={chatInfoChat}
      currentUserId={session.userId}
      onChatUpdated={onChatUpdated}
      onChatLeft={onChatLeft}
      onClose={onClose}
      onHistoryCleared={onHistoryCleared}
      onOpenDiscussionChat={onOpenDiscussionChat}
      onOpenMembers={onOpenMembers}
      onOpenBotMiniApp={onOpenBotMiniApp}
      onStartCall={canStartCalls ? onStartCall : undefined}
      onOpenSharedMedia={onOpenSharedMedia}
      token={session.token}
    />
  );
}

export function RootCallScreenSurface({
  session,
  chats,
  currentCall,
  currentCallLinks,
  callMediaState,
  callJoinLinksEnabled,
  callModerationEnabled,
  callScreenSharingEnabled,
  recentCallSignals,
  deriveCallPhoto,
  deriveCallTitle,
  onAccept,
  onDecline,
  onLeave,
  onToggleMute,
  onToggleSpeaker,
  onToggleVideo,
  onToggleScreenShare,
  onSetAdaptationProfile,
  onCreateCallLink,
  onModerateParticipant
}: {
  session: AuthSession;
  chats: ChatSummary[];
  currentCall: CallSession | null;
  currentCallLinks: import("../types").CallJoinLink[];
  callMediaState: CallMediaState;
  callJoinLinksEnabled: boolean;
  callModerationEnabled: boolean;
  callScreenSharingEnabled: boolean;
  recentCallSignals: CallSignalEvent[];
  deriveCallPhoto: (call: CallSession, chats: ChatSummary[], currentUserId: string) => string | null;
  deriveCallTitle: (call: CallSession, chats: ChatSummary[], currentUserId: string) => string;
  onAccept: () => void;
  onDecline: () => void;
  onLeave: () => void;
  onToggleMute: () => void;
  onToggleSpeaker: () => void;
  onToggleVideo: () => void;
  onToggleScreenShare: () => void;
  onSetAdaptationProfile: (profile: import("../types").CallAdaptationProfile) => void;
  onCreateCallLink: (kind: "VOICE" | "VIDEO") => void;
  onModerateParticipant: (
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      videoPublishingAllowed?: boolean;
      screenShareAllowed?: boolean;
      removeParticipant?: boolean;
    }
  ) => void;
}) {
  if (!currentCall) {
    return null;
  }

  return (
    <CallScreen
      call={currentCall}
      callLinks={currentCallLinks}
      callJoinLinksEnabled={callJoinLinksEnabled}
      callModerationEnabled={callModerationEnabled}
      callScreenSharingEnabled={callScreenSharingEnabled}
      chatPhotoUrl={deriveCallPhoto(currentCall, chats, session.userId)}
      chatTitle={deriveCallTitle(currentCall, chats, session.userId)}
      currentUserId={session.userId}
      mediaState={callMediaState}
      onAccept={onAccept}
      onDecline={onDecline}
      onLeave={onLeave}
      onToggleMute={onToggleMute}
      onToggleSpeaker={onToggleSpeaker}
      onToggleVideo={onToggleVideo}
      onToggleScreenShare={onToggleScreenShare}
      onSetAdaptationProfile={onSetAdaptationProfile}
      onCreateCallLink={onCreateCallLink}
      onModerateParticipant={onModerateParticipant}
      recentSignals={recentCallSignals}
    />
  );
}

export function RootCreateChatScreenSurface({
  session,
  composeMode,
  onClose,
  onCreated
}: {
  session: AuthSession | null;
  composeMode: "direct" | "group" | "channel" | null;
  onClose: () => void;
  onCreated: (chat: ChatSummary) => void;
}) {
  if (!session || !composeMode) {
    return null;
  }

  return (
    <CreateChatScreen
      mode={composeMode}
      onClose={onClose}
      onCreated={onCreated}
      token={session.token}
    />
  );
}

export function RootAddAccountScreenSurface({
  onAuthenticated,
  onCancel
}: {
  onAuthenticated: (session: AuthSession) => void;
  onCancel: () => void;
}) {
  return (
    <AuthScreen
      mode="ADD_ACCOUNT"
      onAuthenticated={onAuthenticated}
      onCancel={onCancel}
    />
  );
}

export function RootBotDeveloperScreenSurface({
  session,
  onClose
}: {
  session: AuthSession | null;
  onClose: () => void;
}) {
  if (!session) {
    return null;
  }

  return <BotDeveloperScreen onClose={onClose} token={session.token} />;
}

export function RootSessionsScreenSurface({
  session,
  onClose
}: {
  session: AuthSession | null;
  onClose: () => void;
}) {
  if (!session) {
    return null;
  }

  return (
    <SessionsScreen
      currentSessionId={session.sessionId}
      onClose={onClose}
      token={session.token}
    />
  );
}

export function RootSettingsSectionScreenSurface({
  session,
  section,
  onClose,
  onOpenAddAccount,
  onOpenBotDeveloper,
  onOpenSessions
}: {
  session: AuthSession | null;
  section: import("./types").SettingsSectionId | null;
  onClose: () => void;
  onOpenAddAccount: () => void;
  onOpenBotDeveloper: () => void;
  onOpenSessions: () => void;
}) {
  if (!session || !section) {
    return null;
  }

  return (
    <SettingsSectionScreen
      onAddAccount={onOpenAddAccount}
      onClose={onClose}
      onOpenBotDeveloper={onOpenBotDeveloper}
      onOpenSessions={onOpenSessions}
      section={section}
      token={session.token}
    />
  );
}

export function RootGlobalSearchScreenSurface({
  availableChats,
  session,
  onClose,
  onOpenChat,
  onOpenMessageResult,
  onOpenParsedLink
}: {
  availableChats: ChatSummary[];
  session: AuthSession | null;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenMessageResult: (chat: ChatSummary, message: ChatMessage) => void;
  onOpenParsedLink: (parsedLink: import("./deepLinks").ParsedDeepLink) => void;
}) {
  if (!session) {
    return null;
  }

  return (
    <GlobalSearchScreen
      availableChats={availableChats}
      onClose={onClose}
      onOpenChat={onOpenChat}
      onOpenMessageResult={onOpenMessageResult}
      onOpenParsedLink={onOpenParsedLink}
      token={session.token}
    />
  );
}

export function RootCreateStoryScreenSurface({
  session,
  onClose,
  onCreated
}: {
  session: AuthSession | null;
  onClose: () => void;
  onCreated: (story: Story) => void;
}) {
  if (!session) {
    return null;
  }

  return <CreateStoryScreen onClose={onClose} onCreated={onCreated} token={session.token} />;
}

export function RootJoinByLinkScreenSurface({
  availableChats,
  session,
  joinByLinkSeedToken,
  onClose,
  onOpenDiscoveryChat,
  onJoined,
  onOpenParsedLink
}: {
  availableChats: ChatSummary[];
  session: AuthSession | null;
  joinByLinkSeedToken: string | null;
  onClose: () => void;
  onOpenDiscoveryChat: (chatId: string) => void;
  onJoined: (chat: ChatSummary) => void;
  onOpenParsedLink: (parsedLink: import("./deepLinks").ParsedDeepLink) => void;
}) {
  if (!session) {
    return null;
  }

  return (
    <JoinChatByLinkScreen
      availableChats={availableChats}
      initialInviteToken={joinByLinkSeedToken}
      onClose={onClose}
      onOpenDiscoveryChat={onOpenDiscoveryChat}
      onJoined={onJoined}
      onOpenParsedLink={onOpenParsedLink}
      token={session.token}
    />
  );
}

export function RootMediaViewerScreenSurface({
  session,
  mediaViewer,
  onClose,
  onOpenMessage
}: {
  session: AuthSession | null;
  mediaViewer: MediaViewerRoute | null;
  onClose: () => void;
  onOpenMessage?: (messageId: string, createdAt: string) => void;
}) {
  if (!session || !mediaViewer) {
    return null;
  }

  return (
    <MediaViewerScreen
      attachments={mediaViewer.attachments}
      attachmentSources={mediaViewer.attachmentSources}
      chatTitle={mediaViewer.chatTitle}
      initialAttachmentId={mediaViewer.attachmentId}
      onClose={onClose}
      onOpenMessage={onOpenMessage}
      token={session.token}
    />
  );
}

export function RootSharedMediaScreenSurface({
  session,
  sharedMediaChat,
  onClose,
  onOpenMediaViewer,
  onOpenMessage,
  onOpenParsedLink
}: {
  session: AuthSession | null;
  sharedMediaChat: ChatSummary | null;
  onClose: () => void;
  onOpenMediaViewer: (payload: {
    attachments: MessageAttachment[];
    attachmentSources?: Array<{
      attachmentId: string;
      createdAt: string;
      messageId: string;
    }>;
    initialAttachmentId: string;
    chatTitle: string;
    }) => void;
  onOpenMessage: (messageId: string, createdAt: string) => void;
  onOpenParsedLink: (parsedLink: import("./deepLinks").ParsedDeepLink) => void;
}) {
  if (!session || !sharedMediaChat) {
    return null;
  }

  return (
    <SharedMediaScreen
      chat={sharedMediaChat}
      onClose={onClose}
      onOpenMessage={onOpenMessage}
      onOpenMediaViewer={onOpenMediaViewer}
      onOpenParsedLink={onOpenParsedLink}
      token={session.token}
    />
  );
}

export function RootArchivedScreenSurface({
  session,
  onClose,
  onOpenChat
}: {
  session: AuthSession | null;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
}) {
  if (!session) {
    return null;
  }

  return (
    <ArchivedChatsScreen
      onClose={onClose}
      onOpenChat={onOpenChat}
      token={session.token}
    />
  );
}

export function RootFoldersScreenSurface({
  session,
  onClose
}: {
  session: AuthSession | null;
  onClose: () => void;
}) {
  if (!session) {
    return null;
  }

  return <FoldersScreen onClose={onClose} token={session.token} />;
}

export function RootBotMiniAppScreenSurface({
  session,
  selectedBotMiniApp,
  onClose
}: {
  session: AuthSession | null;
  selectedBotMiniApp: BotMiniAppRoute | null;
  onClose: () => void;
}) {
  if (!session || !selectedBotMiniApp) {
    return null;
  }

  return (
    <BotMiniAppScreen
      botUserId={selectedBotMiniApp.botUserId}
      chatId={selectedBotMiniApp.chatId}
      onClose={onClose}
      startParameter={selectedBotMiniApp.startParameter}
      title={selectedBotMiniApp.title}
      token={session.token}
    />
  );
}
