import type { Dispatch, ReactElement, SetStateAction } from "react";
import type { AppModalRoute, RootTab } from "./types";
import type {
  DiscussionThreadSelection,
  MessageFocusTarget
} from "./rootNavigatorState";
import type {
  AuthSession,
  CallJoinLink,
  CallMediaState,
  CallSession,
  CallSignalEvent,
  ChatMessage,
  ChatSummary,
  ForumTopic
} from "../types";

export type FocusPayload = {
  messageId: string;
  createdAt: string;
};

export type StoryFocusTarget = {
  ownerUserId: string;
  storyId: string;
};

export type RootScreenRenderersInput = {
  acceptCurrentCall: () => Promise<void>;
  activeRootTab: RootTab;
  botsEnabled: boolean;
  broadcastCallSignal: (
    signalType: string,
    payload: Record<string, unknown>
  ) => Promise<void>;
  callMediaState: CallMediaState;
  callsEnabled: boolean;
  callJoinLinksEnabled: boolean;
  callModerationEnabled: boolean;
  callScreenSharingEnabled: boolean;
  chats: ChatSummary[];
  chatInfoRoute: Extract<AppModalRoute, { type: "CHAT_INFO" }> | null;
  composeMode: "direct" | "group" | "channel" | null;
  createCurrentCallLink: (kind: "VOICE" | "VIDEO") => Promise<void>;
  currentCall: CallSession | null;
  currentCallLinks: CallJoinLink[];
  declineCurrentCall: () => Promise<void>;
  directCallsEnabled: boolean;
  groupCallsEnabled: boolean;
  joinByLinkSeedToken: string | null;
  joinCallByLink: (rawToken: string) => Promise<void>;
  leaveCurrentCall: () => Promise<void>;
  modalRoute?: AppModalRoute | null;
  mediaViewer: Extract<AppModalRoute, { type: "MEDIA_VIEWER" }> | null;
  membersChat: ChatSummary | null;
  moderateCurrentCallParticipant: (
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      videoPublishingAllowed?: boolean;
      screenShareAllowed?: boolean;
      removeParticipant?: boolean;
    }
  ) => Promise<void>;
  openChat: (chat: ChatSummary, focus?: FocusPayload | null) => void;
  openChatFromNotification: (
    sessionToken: string,
    chatId: string,
    currentUserId: string,
    topicId?: string | null,
    focus?: FocusPayload | null
  ) => Promise<void>;
  openDiscussionThread: (message: ChatMessage) => Promise<void>;
  pendingChatFocus: MessageFocusTarget | null;
  pendingCreatedStoryFocus: StoryFocusTarget | null;
  recentCallSignals: CallSignalEvent[];
  refreshChats: (
    sessionToken: string,
    currentUserId?: string
  ) => Promise<ChatSummary[]>;
  selectedBotMiniApp: Extract<AppModalRoute, { type: "BOT_MINI_APP" }> | null;
  selectedChat: ChatSummary | null;
  selectedDiscussionThread: DiscussionThreadSelection | null;
  selectedForumTopic: ForumTopic | null;
  session: AuthSession | null;
  setActiveRootTab: (tab: RootTab) => void;
  setChatMessages: (chatId: string, messages: ChatMessage[]) => void;
  setMembersChat: Dispatch<SetStateAction<ChatSummary | null>>;
  setModalRoute: Dispatch<SetStateAction<AppModalRoute | null>>;
  setPendingChatFocus: Dispatch<SetStateAction<MessageFocusTarget | null>>;
  setPendingCreatedStoryFocus: Dispatch<SetStateAction<StoryFocusTarget | null>>;
  setSelectedChat: Dispatch<SetStateAction<ChatSummary | null>>;
  setSelectedDiscussionThread: Dispatch<SetStateAction<DiscussionThreadSelection | null>>;
  setSelectedForumTopic: Dispatch<SetStateAction<ForumTopic | null>>;
  sharedMediaChat: ChatSummary | null;
  startChatCall: (chatId: string, kind: "VOICE" | "VIDEO") => Promise<void>;
  storiesEnabled: boolean;
  toggleCurrentScreenShare: () => Promise<void>;
  upsertChat: (chat: ChatSummary) => void;
};

export type RootPrimaryScreenRenderers = {
  renderAuthScreen: () => ReactElement | null;
  renderMainTabsScreen: () => ReactElement | null;
  renderChatScreen: () => ReactElement | null;
  renderForumTopicsScreen: () => ReactElement | null;
  renderMembersScreen: () => ReactElement | null;
  renderCallScreen: () => ReactElement | null;
};

export type RootModalScreenRenderers = {
  renderCreateChatScreen: () => ReactElement | null;
  renderAddAccountScreen: () => ReactElement | null;
  renderBotDeveloperScreen: () => ReactElement | null;
  renderSessionsScreen: () => ReactElement | null;
  renderSettingsSectionScreen: () => ReactElement | null;
  renderGlobalSearchScreen: () => ReactElement | null;
  renderCreateStoryScreen: () => ReactElement | null;
  renderJoinByLinkScreen: () => ReactElement | null;
  renderMediaViewerScreen: () => ReactElement | null;
  renderSharedMediaScreen: () => ReactElement | null;
  renderChatInfoScreen: () => ReactElement | null;
  renderArchivedScreen: () => ReactElement | null;
  renderFoldersScreen: () => ReactElement | null;
  renderBotMiniAppScreen: () => ReactElement | null;
};

export type RootScreenRenderers = RootPrimaryScreenRenderers & RootModalScreenRenderers;
