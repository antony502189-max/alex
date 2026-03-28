import type { Dispatch, SetStateAction } from "react";
import type { ParsedDeepLink } from "./deepLinks";
import type { AppModalRoute, RootTab } from "./types";
import { findExactPublicChatMatch } from "../services/publicChatMatches";
import type { AuthSession, ChatSummary } from "../types";

type ParsedLinkIntentHandlerParams = {
  availableChats: ChatSummary[];
  joinCallByLink: (rawToken: string) => Promise<void>;
  openChat: (chat: ChatSummary) => void;
  openChatFromNotification: (
    sessionToken: string,
    chatId: string,
    currentUserId: string,
    topicId?: string | null
  ) => Promise<void>;
  parsedLink: ParsedDeepLink;
  session: AuthSession | null;
  setActiveRootTab: (tab: RootTab) => void;
  setModalRoute: Dispatch<SetStateAction<AppModalRoute | null>>;
};

export function handleParsedLinkIntent({
  availableChats,
  joinCallByLink,
  openChat,
  openChatFromNotification,
  parsedLink,
  session,
  setActiveRootTab,
  setModalRoute
}: ParsedLinkIntentHandlerParams) {
  if (!session) {
    return;
  }

  if (parsedLink.type === "JOIN") {
    const exactPublicChatMatch = findExactPublicChatMatch(availableChats, parsedLink);
    if (exactPublicChatMatch) {
      setModalRoute(null);
      setActiveRootTab("CHATS");
      openChat(exactPublicChatMatch);
      return;
    }

    setActiveRootTab("CHATS");
    setModalRoute({
      type: "JOIN_BY_LINK",
      seedToken: parsedLink.token
    });
    return;
  }

  if (parsedLink.type === "CALL") {
    setModalRoute(null);
    setActiveRootTab("CALLS");
    void Promise.resolve(joinCallByLink(parsedLink.token)).catch(() => undefined);
    return;
  }

  setModalRoute(null);
  setActiveRootTab("CHATS");
  void Promise.resolve(
    openChatFromNotification(
      session.token,
      parsedLink.chatId,
      session.userId,
      parsedLink.topicId
    )
  ).catch(() => undefined);
}
