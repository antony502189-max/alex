export const ROOT_TABS = ["CHATS", "CALLS", "CONTACTS", "STORIES", "SETTINGS"] as const;

export type RootTab = (typeof ROOT_TABS)[number];

export type AppModalRoute =
  | { type: "GLOBAL_SEARCH" }
  | { type: "CREATE_STORY" }
  | { type: "JOIN_BY_LINK"; seedToken?: string | null }
  | { type: "CREATE_CHAT"; mode: "direct" | "group" | "channel" }
  | { type: "MEDIA_VIEWER"; chatId: string; attachmentId: string }
  | { type: "SHARED_MEDIA"; chatId: string }
  | { type: "SESSIONS" }
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
  | { type: "SECRET_CHAT"; secretChatId: string };
