import { useEffect, useMemo, useState } from "react";
import { parseAlexDeepLink } from "../../navigation/deepLinks";
import { api } from "../../services/api";
import type {
  ChatSummary,
  GlobalMessageSearchResult,
  GlobalSearchResponse,
  UserSearchResult
} from "../../types";
import {
  buildGlobalSearchSummary,
  findExactPublicChatMatch,
  hasGlobalSearchResults
} from "./globalSearchPresentation";

type UseGlobalSearchControllerParams = {
  availableChats: ChatSummary[];
  onOpenChat: (chat: ChatSummary) => void;
  token: string;
};

export function useGlobalSearchController({
  availableChats,
  onOpenChat,
  token
}: UseGlobalSearchControllerParams) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GlobalSearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [openingUserId, setOpeningUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const normalizedQuery = query.trim();
  const parsedLink = useMemo(
    () => parseAlexDeepLink(query),
    [query]
  );
  const exactPublicChatMatch = useMemo(
    () => findExactPublicChatMatch(availableChats, parsedLink),
    [availableChats, parsedLink]
  );

  useEffect(() => {
    let cancelled = false;
    if (normalizedQuery.length < 2) {
      setResults(null);
      setLoading(false);
      setError(null);
      return;
    }

    if (parsedLink) {
      setResults(null);
      setLoading(false);
      setError(null);
      return;
    }

    const timeoutId = setTimeout(() => {
      setLoading(true);
      setError(null);
      api.searchGlobal(token, normalizedQuery, 12)
        .then((nextResults) => {
          if (!cancelled) {
            setResults(nextResults);
          }
        })
        .catch((searchError) => {
          if (!cancelled) {
            setError(
              searchError instanceof Error
                ? searchError.message
                : "Unable to run global search"
            );
          }
        })
        .finally(() => {
          if (!cancelled) {
            setLoading(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [normalizedQuery, parsedLink, token]);

  const resultSummary = useMemo(
    () => buildGlobalSearchSummary(results),
    [results]
  );

  const hasResults = useMemo(
    () => hasGlobalSearchResults(results),
    [results]
  );

  async function handleOpenUser(user: UserSearchResult) {
    setOpeningUserId(user.userId);
    setError(null);
    try {
      const chat = await api.createDirectChat(token, user.userId);
      onOpenChat(chat);
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open chat");
    } finally {
      setOpeningUserId(null);
    }
  }

  function handleOpenChat(chat: ChatSummary) {
    onOpenChat(chat);
  }

  function handleOpenMessageResult(
    chat: ChatSummary,
    message: GlobalMessageSearchResult["message"],
    onOpenMessageResult: (chat: ChatSummary, message: GlobalMessageSearchResult["message"]) => void
  ) {
    onOpenMessageResult(chat, message);
  }

  return {
    error,
    handleOpenChat,
    handleOpenMessageResult,
    handleOpenUser,
    hasResults,
    loading,
    normalizedQuery,
    openingUserId,
    exactPublicChatMatch,
    parsedLink,
    query,
    resultSummary,
    results,
    setQuery
  };
}

export type GlobalSearchScreenController = ReturnType<typeof useGlobalSearchController>;
