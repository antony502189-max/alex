import type {
  AppModalRoute,
  RootStackParamList
} from "./types";
import type {
  AuthSession,
  CallSession,
  ChatSummary,
  ForumTopic
} from "../types";

export type RootStackRoute =
  | { name: "AUTH" }
  | { name: "MAIN_TABS" }
  | { name: "CHAT"; params: RootStackParamList["CHAT"] }
  | { name: "FORUM_TOPICS"; params: RootStackParamList["FORUM_TOPICS"] }
  | { name: "MEMBERS"; params: RootStackParamList["MEMBERS"] }
  | { name: "CHAT_INFO"; params: RootStackParamList["CHAT_INFO"] }
  | { name: "CALL"; params: RootStackParamList["CALL"] }
  | { name: "CREATE_CHAT"; params: RootStackParamList["CREATE_CHAT"] }
  | { name: "AUTH_ADD_ACCOUNT" }
  | { name: "BOT_DEVELOPER" }
  | { name: "SESSIONS"; params: RootStackParamList["SESSIONS"] }
  | { name: "SETTINGS_SECTION"; params: RootStackParamList["SETTINGS_SECTION"] }
  | { name: "GLOBAL_SEARCH" }
  | { name: "CREATE_STORY" }
  | { name: "JOIN_BY_LINK"; params: RootStackParamList["JOIN_BY_LINK"] }
  | { name: "MEDIA_VIEWER"; params: RootStackParamList["MEDIA_VIEWER"] }
  | { name: "SHARED_MEDIA"; params: RootStackParamList["SHARED_MEDIA"] }
  | { name: "ARCHIVED" }
  | { name: "FOLDERS" }
  | { name: "BOT_MINI_APP"; params: RootStackParamList["BOT_MINI_APP"] };

type BuildDesiredRoutesInput = {
  session: AuthSession | null;
  selectedChat: ChatSummary | null;
  selectedForumTopic: ForumTopic | null;
  selectedDiscussionThread: { rootMessageId: string } | null;
  membersChat: ChatSummary | null;
  composeMode: "direct" | "group" | "channel" | null;
  modalRoute: AppModalRoute | null;
  mediaViewer: {
    chatId: string;
    attachmentId: string;
    returnToSharedMediaChatId?: string | null;
  } | null;
  sharedMediaChat: ChatSummary | null;
  selectedBotMiniApp: {
    botUserId: string;
    chatId: string | null;
    startParameter: string | null;
    title: string;
  } | null;
  currentCall: CallSession | null;
};

function routeParamsEqual(
  left: object | undefined,
  right: object | undefined
) {
  return JSON.stringify(left ?? null) === JSON.stringify(right ?? null);
}

export function routeStacksEqual(
  left:
    | ReadonlyArray<{
        name: string;
        params?: object;
      }>
    | undefined,
  right: RootStackRoute[]
) {
  if (!left || left.length !== right.length) {
    return false;
  }

  return left.every((route, index) => {
    const nextRoute = right[index];
    return (
      route.name === nextRoute.name &&
      routeParamsEqual(route.params, "params" in nextRoute ? nextRoute.params : undefined)
    );
  });
}

export function buildDesiredRoutes({
  session,
  selectedChat,
  selectedForumTopic,
  selectedDiscussionThread,
  membersChat,
  composeMode,
  modalRoute,
  mediaViewer,
  sharedMediaChat,
  selectedBotMiniApp,
  currentCall
}: BuildDesiredRoutesInput): RootStackRoute[] {
  if (!session) {
    return [{ name: "AUTH" }];
  }

  const routes: RootStackRoute[] = [{ name: "MAIN_TABS" }];

  if (selectedChat) {
    if (selectedChat.forumEnabled && !selectedForumTopic && !selectedDiscussionThread) {
      routes.push({
        name: "FORUM_TOPICS",
        params: {
          chatId: selectedChat.chatId
        }
      });
    } else {
      routes.push({
        name: "CHAT",
        params: {
          chatId: selectedChat.chatId,
          topicId: selectedForumTopic?.topicId ?? null,
          threadRootMessageId: selectedDiscussionThread?.rootMessageId ?? null
        }
      });
    }

    if (membersChat) {
      routes.push({
        name: "MEMBERS",
        params: {
          chatId: membersChat.chatId
        }
      });
    }
  }

  if (composeMode) {
    routes.push({
      name: "CREATE_CHAT",
      params: {
        mode: composeMode
      }
    });
  } else if (modalRoute?.type === "AUTH") {
    routes.push({ name: "AUTH_ADD_ACCOUNT" });
  } else if (modalRoute?.type === "BOT_DEVELOPER") {
    routes.push({ name: "BOT_DEVELOPER" });
  } else if (modalRoute?.type === "SESSIONS") {
    routes.push({
      name: "SESSIONS",
      params: {
        currentSessionId: session.sessionId
      }
    });
  } else if (modalRoute?.type === "SETTINGS_SECTION") {
    routes.push({
      name: "SETTINGS_SECTION",
      params: {
        section: modalRoute.section
      }
    });
  } else if (modalRoute?.type === "GLOBAL_SEARCH") {
    routes.push({ name: "GLOBAL_SEARCH" });
  } else if (modalRoute?.type === "CREATE_STORY") {
    routes.push({ name: "CREATE_STORY" });
  } else if (modalRoute?.type === "JOIN_BY_LINK") {
    routes.push({
      name: "JOIN_BY_LINK",
      params: {
        seedToken: modalRoute.seedToken ?? null
      }
    });
  } else if (mediaViewer) {
    if (mediaViewer.returnToSharedMediaChatId) {
      routes.push({
        name: "SHARED_MEDIA",
        params: {
          chatId: mediaViewer.returnToSharedMediaChatId
        }
      });
    }
    routes.push({
      name: "MEDIA_VIEWER",
      params: {
        chatId: mediaViewer.chatId,
        attachmentId: mediaViewer.attachmentId
      }
    });
  } else if (sharedMediaChat) {
    routes.push({
      name: "SHARED_MEDIA",
      params: {
        chatId: sharedMediaChat.chatId
      }
    });
  } else if (modalRoute?.type === "CHAT_INFO") {
    routes.push({
      name: "CHAT_INFO",
      params: {
        chatId: modalRoute.chatId
      }
    });
  } else if (modalRoute?.type === "ARCHIVED") {
    routes.push({ name: "ARCHIVED" });
  } else if (modalRoute?.type === "FOLDERS") {
    routes.push({ name: "FOLDERS" });
  } else if (selectedBotMiniApp) {
    routes.push({
      name: "BOT_MINI_APP",
      params: {
        botUserId: selectedBotMiniApp.botUserId,
        chatId: selectedBotMiniApp.chatId,
        startParameter: selectedBotMiniApp.startParameter,
        title: selectedBotMiniApp.title
      }
    });
  }

  if (currentCall) {
    routes.push({
      name: "CALL",
      params: {
        callId: currentCall.callId
      }
    });
  }

  return routes;
}
