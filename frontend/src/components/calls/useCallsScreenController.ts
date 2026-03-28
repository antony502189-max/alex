import { useEffect, useMemo, useState } from "react";
import { parseAlexDeepLink } from "../../navigation/deepLinks";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import type { CallHistoryEntry } from "../../types";
import { getMissedCallsCount, normalizeCallLinkToken } from "./callsPresentation";

type UseCallsScreenControllerParams = {
  currentUserId: string;
  token: string;
};

export function useCallsScreenController({
  currentUserId,
  token
}: UseCallsScreenControllerParams) {
  const [recentCalls, setRecentCalls] = useState<CallHistoryEntry[]>([]);
  const [callLinkToken, setCallLinkToken] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const parsedLink = useMemo(
    () => parseAlexDeepLink(callLinkToken),
    [callLinkToken]
  );

  async function loadRecentCalls() {
    setRefreshing(true);
    setError(null);
    try {
      const nextCalls = await api.getRecentCalls(token, 60);
      setRecentCalls(nextCalls);
      await localDatabase.replaceRecentCalls(currentUserId, nextCalls);
    } catch (loadError) {
      setError(
        recentCalls.length > 0
          ? "Offline mode. Showing cached calls."
          : loadError instanceof Error
            ? loadError.message
            : "Unable to load calls"
      );
    } finally {
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void localDatabase.getRecentCalls(currentUserId)
      .then((cachedCalls) => {
        if (cachedCalls.length > 0) {
          setRecentCalls(cachedCalls);
        }
      })
      .catch(() => undefined);

    void loadRecentCalls();
  }, [currentUserId, token]);

  const missedCallsCount = useMemo(
    () => getMissedCallsCount(recentCalls),
    [recentCalls]
  );

  function canJoinCallLink() {
    return normalizeCallLinkToken(callLinkToken).length > 0 && (!parsedLink || parsedLink.type === "CALL");
  }

  return {
    callLinkToken,
    canJoinCallLink: canJoinCallLink(),
    error,
    handleCallLinkTokenChange: setCallLinkToken,
    loadRecentCalls,
    missedCallsCount,
    parsedLink,
    recentCalls,
    refreshing
  };
}

export type CallsScreenController = ReturnType<typeof useCallsScreenController>;
