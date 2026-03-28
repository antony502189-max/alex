import { useEffect, useEffectEvent } from "react";
import { AppState } from "react-native";
import { api } from "../services/api";
import {
  getDirectChatPresenceUserIds,
  mergePresenceIntoChats
} from "../services/presenceSync";
import { useAppStore } from "../store/useAppStore";
import type { AuthSession, ChatSummary } from "../types";

const PRESENCE_POLL_INTERVAL_MS = 45_000;

type UseRootPresenceSyncInput = {
  chats: ChatSummary[];
  session: AuthSession | null;
  setChats: (chats: ChatSummary[]) => void;
};

export function useRootPresenceSync({
  chats,
  session,
  setChats
}: UseRootPresenceSyncInput) {
  const handleRefreshPresence = useEffectEvent(async () => {
    if (!session) {
      return;
    }

    const currentChats = useAppStore.getState().chats;
    const userIds = getDirectChatPresenceUserIds(currentChats);
    if (userIds.length === 0) {
      return;
    }

    const statuses = await api.getUsersPresence(session.token, userIds);
    const nextChats = mergePresenceIntoChats(currentChats, statuses);
    const changed = nextChats.some((chat, index) => chat !== currentChats[index]);

    if (changed) {
      setChats(nextChats);
    }
  });

  useEffect(() => {
    if (!session) {
      return;
    }

    void handleRefreshPresence().catch(() => undefined);

    const appStateSubscription = AppState.addEventListener("change", (nextState) => {
      if (nextState !== "active") {
        return;
      }

      void handleRefreshPresence().catch(() => undefined);
    });
    const intervalId = setInterval(() => {
      void handleRefreshPresence().catch(() => undefined);
    }, PRESENCE_POLL_INTERVAL_MS);

    return () => {
      appStateSubscription.remove();
      clearInterval(intervalId);
    };
  }, [session]);

  useEffect(() => {
    if (!session || chats.length === 0) {
      return;
    }

    void handleRefreshPresence().catch(() => undefined);
  }, [chats, session]);
}
