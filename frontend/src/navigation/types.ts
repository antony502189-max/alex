import type { MessageAttachment } from "../types";

export const ROOT_TABS = ["CHATS", "CALLS", "CONTACTS", "STORIES", "SETTINGS"] as const;

export type RootTab = (typeof ROOT_TABS)[number];
export const SETTINGS_SECTIONS = [
  "PROFILE",
  "PRIVACY_SECURITY",
  "DEVICES",
  "NOTIFICATIONS",
  "DATA_STORAGE",
  "APPEARANCE",
  "LANGUAGE",
  "BLOCKED_PRIVACY",
  "HELP"
] as const;

export type SettingsSectionId = (typeof SETTINGS_SECTIONS)[number];

export type MainTabsParamList = {
  CHATS: undefined;
  CALLS: undefined;
  CONTACTS: undefined;
  STORIES: undefined;
  SETTINGS: undefined;
};

export type RootStackParamList = {
  AUTH: undefined;
  MAIN_TABS: undefined;
  CHAT: {
    chatId: string;
    topicId?: string | null;
    threadRootMessageId?: string | null;
  };
  FORUM_TOPICS: {
    chatId: string;
  };
  MEMBERS: {
    chatId: string;
  };
  CHAT_INFO: {
    chatId: string;
  };
  CALL: {
    callId: string;
  };
  CREATE_CHAT: {
    mode: "direct" | "group" | "channel";
  };
  AUTH_ADD_ACCOUNT: undefined;
  BOT_DEVELOPER: undefined;
  SESSIONS: {
    currentSessionId: string;
  };
  SETTINGS_SECTION: {
    section: SettingsSectionId;
  };
  GLOBAL_SEARCH: undefined;
  CREATE_STORY: undefined;
  JOIN_BY_LINK: {
    seedToken?: string | null;
  };
  MEDIA_VIEWER: {
    chatId: string;
    attachmentId: string;
  };
  SHARED_MEDIA: {
    chatId: string;
  };
  ARCHIVED: undefined;
  FOLDERS: undefined;
  BOT_MINI_APP: {
    botUserId: string;
    chatId: string | null;
    startParameter: string | null;
    title: string;
  };
};

export type AppModalRoute =
  | { type: "AUTH"; intent: "ADD_ACCOUNT" }
  | { type: "GLOBAL_SEARCH" }
  | { type: "CREATE_STORY" }
  | { type: "JOIN_BY_LINK"; seedToken?: string | null }
  | { type: "CREATE_CHAT"; mode: "direct" | "group" | "channel" }
  | {
      type: "MEDIA_VIEWER";
      chatId: string;
      chatTitle: string;
      attachmentId: string;
      attachments: MessageAttachment[];
      attachmentSources?: Array<{
        attachmentId: string;
        createdAt: string;
        messageId: string;
      }>;
      returnToSharedMediaChatId?: string | null;
    }
  | { type: "SHARED_MEDIA"; chatId: string }
  | { type: "CHAT_INFO"; chatId: string }
  | { type: "SESSIONS" }
  | { type: "SETTINGS_SECTION"; section: SettingsSectionId }
  | { type: "BOT_DEVELOPER" }
  | { type: "ARCHIVED" }
  | { type: "FOLDERS" }
  | {
      type: "BOT_MINI_APP";
      botUserId: string;
      chatId: string | null;
      startParameter: string | null;
      title: string;
    };

export type ChatRoute =
  | { type: "CHAT"; chatId: string }
  | { type: "FORUM"; chatId: string }
  | { type: "MEMBERS"; chatId: string }
  | { type: "CHAT_INFO"; chatId: string };

export type ActiveRouteState = {
  activeRootTab: RootTab;
  modalRoute: AppModalRoute | null;
  chatRoute: ChatRoute | null;
};
