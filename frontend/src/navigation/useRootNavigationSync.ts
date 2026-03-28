import { useEffect } from "react";
import type { NavigationContainerRefWithCurrent } from "@react-navigation/native";
import { routeStacksEqual } from "./rootRouteState";
import type { AppModalRoute, ChatRoute, RootStackParamList } from "./types";
import type { ChatSummary, ForumTopic } from "../types";
import type { RootStackRoute } from "./rootRouteState";

type UseRootNavigationSyncInput = {
  desiredRoutes: RootStackRoute[];
  modalRoute: AppModalRoute | null;
  membersChat: ChatSummary | null;
  navigationRef: NavigationContainerRefWithCurrent<RootStackParamList>;
  selectedChat: ChatSummary | null;
  selectedDiscussionThread: { rootMessageId: string } | null;
  selectedForumTopic: ForumTopic | null;
  setTrackedChatRoute: (route: ChatRoute | null) => void;
  setTrackedModalRoute: (route: AppModalRoute | null) => void;
};

export function useRootNavigationSync({
  desiredRoutes,
  modalRoute,
  membersChat,
  navigationRef,
  selectedChat,
  selectedDiscussionThread,
  selectedForumTopic,
  setTrackedChatRoute,
  setTrackedModalRoute
}: UseRootNavigationSyncInput) {
  useEffect(() => {
    setTrackedModalRoute(modalRoute);
  }, [modalRoute, setTrackedModalRoute]);

  useEffect(() => {
    if (membersChat) {
      setTrackedChatRoute({
        type: "MEMBERS",
        chatId: membersChat.chatId
      });
      return;
    }

    if (selectedChat) {
      setTrackedChatRoute({
        type:
          selectedChat.forumEnabled && !selectedForumTopic && !selectedDiscussionThread
            ? "FORUM"
            : "CHAT",
        chatId: selectedChat.chatId
      });
      return;
    }

    setTrackedChatRoute(null);
  }, [
    membersChat,
    selectedChat,
    selectedDiscussionThread,
    selectedForumTopic,
    setTrackedChatRoute
  ]);

  useEffect(() => {
    if (!navigationRef.isReady()) {
      return;
    }

    const currentRoutes = navigationRef.getRootState()?.routes;
    if (routeStacksEqual(currentRoutes, desiredRoutes)) {
      return;
    }

    navigationRef.resetRoot({
      index: desiredRoutes.length - 1,
      routes: desiredRoutes
    });
  }, [desiredRoutes, navigationRef]);
}
