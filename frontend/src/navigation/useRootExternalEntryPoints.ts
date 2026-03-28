import { useEffect, useEffectEvent } from "react";
import * as Notifications from "expo-notifications";
import { AppState, Linking } from "react-native";
import { parseAlexDeepLink } from "./deepLinks";
import { api } from "../services/api";
import { registerForPushNotificationsAsync } from "../services/notifications";
import { wsService } from "../services/ws";
import type { AppModalRoute, RootTab } from "./types";
import type { AuthSession } from "../types";

type UseRootExternalEntryPointsInput = {
  activeRootTab: RootTab;
  callsEnabled: boolean;
  handledInitialLinkRef: { current: boolean };
  onForegroundResume: (session: AuthSession) => Promise<void> | void;
  onJoinCallByLink: (rawToken: string) => Promise<void> | void;
  onOpenChatFromNotification: (
    session: AuthSession,
    chatId: string,
    topicId?: string | null
  ) => Promise<void> | void;
  session: AuthSession | null;
  setActiveRootTab: (tab: RootTab) => void;
  setModalRoute: (route: AppModalRoute | null) => void;
  storiesEnabled: boolean;
};

export function useRootExternalEntryPoints({
  activeRootTab,
  callsEnabled,
  handledInitialLinkRef,
  onForegroundResume,
  onJoinCallByLink,
  onOpenChatFromNotification,
  session,
  setActiveRootTab,
  setModalRoute,
  storiesEnabled
}: UseRootExternalEntryPointsInput) {
  const handleForegroundResume = useEffectEvent(onForegroundResume);
  const handleJoinCallByLink = useEffectEvent(onJoinCallByLink);
  const handleOpenChatFromNotification = useEffectEvent(onOpenChatFromNotification);

  useEffect(() => {
    if (!session) {
      return;
    }

    wsService.setForegroundState(AppState.currentState === "active");
    const subscription = AppState.addEventListener("change", (nextState) => {
      const active = nextState === "active";
      wsService.setForegroundState(active);
      if (!active) {
        return;
      }

      void Promise.resolve(handleForegroundResume(session)).catch(() => undefined);
    });

    return () => {
      subscription.remove();
    };
  }, [session]);

  useEffect(() => {
    if (!session) {
      return;
    }

    let cancelled = false;
    void registerForPushNotificationsAsync()
      .then((pushToken) => {
        if (cancelled || !pushToken) {
          return;
        }
        return api.updateCurrentPushToken(session.token, {
          provider: "EXPO",
          pushToken
        });
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [session]);

  useEffect(() => {
    if (!session) {
      return;
    }

    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      const rawChatId = response.notification.request.content.data?.chatId;
      const rawTopicId = response.notification.request.content.data?.topicId;
      const chatId = typeof rawChatId === "string" ? rawChatId : null;
      const topicId =
        typeof rawTopicId === "string" && rawTopicId.trim().length > 0 ? rawTopicId : null;
      if (!chatId) {
        return;
      }

      void Promise.resolve(
        handleOpenChatFromNotification(session, chatId, topicId)
      ).catch(() => undefined);
    });

    return () => {
      subscription.remove();
    };
  }, [session]);

  useEffect(() => {
    if ((activeRootTab === "CALLS" && !callsEnabled) || (activeRootTab === "STORIES" && !storiesEnabled)) {
      setActiveRootTab("CHATS");
    }
  }, [activeRootTab, callsEnabled, setActiveRootTab, storiesEnabled]);

  useEffect(() => {
    if (!session) {
      return;
    }

    const handleIncomingUrl = (incomingUrl: string | null | undefined) => {
      const parsed = parseAlexDeepLink(incomingUrl);
      if (!parsed) {
        return;
      }

      if (parsed.type === "JOIN") {
        setActiveRootTab("CHATS");
        setModalRoute({
          type: "JOIN_BY_LINK",
          seedToken: parsed.token
        });
        return;
      }

      if (parsed.type === "CALL") {
        if (!callsEnabled) {
          return;
        }
        setActiveRootTab("CALLS");
        void Promise.resolve(handleJoinCallByLink(parsed.token)).catch(() => undefined);
        return;
      }

      setActiveRootTab("CHATS");
      void Promise.resolve(
        handleOpenChatFromNotification(session, parsed.chatId, parsed.topicId)
      ).catch(() => undefined);
    };

    if (!handledInitialLinkRef.current) {
      handledInitialLinkRef.current = true;
      void Linking.getInitialURL()
        .then((incomingUrl) => {
          handleIncomingUrl(incomingUrl);
        })
        .catch(() => undefined);
    }

    const subscription = Linking.addEventListener("url", ({ url }) => {
      handleIncomingUrl(url);
    });

    return () => {
      subscription.remove();
    };
  }, [callsEnabled, handledInitialLinkRef, session, setActiveRootTab, setModalRoute]);
}
