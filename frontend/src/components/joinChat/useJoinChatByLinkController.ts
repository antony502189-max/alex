import { useEffect, useMemo, useState } from "react";
import { parseAlexDeepLink } from "../../navigation/deepLinks";
import { api } from "../../services/api";
import { findExactPublicChatMatch } from "../../services/publicChatMatches";
import type {
  ChatSummary,
  PublicChatDiscovery
} from "../../types";
import {
  buildJoinRequestStatusMessage,
  getPublicChatDiscoveryQuery,
  normalizeInviteToken,
  shouldDiscoverPublicChats
} from "./joinChatPresentation";

type UseJoinChatByLinkControllerParams = {
  availableChats: ChatSummary[];
  initialInviteToken?: string | null;
  onJoined: (chat: ChatSummary) => void;
  onOpenDiscoveryChat: (chatId: string) => void;
  token: string;
};

export function useJoinChatByLinkController({
  availableChats,
  initialInviteToken,
  onJoined,
  onOpenDiscoveryChat,
  token
}: UseJoinChatByLinkControllerParams) {
  const [inviteToken, setInviteToken] = useState(initialInviteToken ?? "");
  const [joining, setJoining] = useState(false);
  const [discovering, setDiscovering] = useState(false);
  const [discoveries, setDiscoveries] = useState<PublicChatDiscovery[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const parsedLink = useMemo(
    () => parseAlexDeepLink(inviteToken),
    [inviteToken]
  );
  const exactPublicChatMatch = useMemo(
    () => findExactPublicChatMatch(availableChats, parsedLink),
    [availableChats, parsedLink]
  );
  const normalizedInviteToken = useMemo(
    () => normalizeInviteToken(inviteToken),
    [inviteToken]
  );
  const canJoin = normalizedInviteToken.length > 0 && (!parsedLink || parsedLink.type === "JOIN");

  function resetDiscoveryState() {
    setDiscoveries((current) => (current.length === 0 ? current : []));
    setDiscovering((current) => (current ? false : current));
  }

  useEffect(() => {
    setInviteToken(initialInviteToken ?? "");
  }, [initialInviteToken]);

  useEffect(() => {
    if (parsedLink && parsedLink.type !== "JOIN") {
      resetDiscoveryState();
      return;
    }

    if (exactPublicChatMatch) {
      resetDiscoveryState();
      return;
    }

    if (!shouldDiscoverPublicChats(inviteToken)) {
      resetDiscoveryState();
      return;
    }

    let cancelled = false;
    const timeoutId = setTimeout(() => {
      setDiscovering(true);
      api.searchPublicChats(token, getPublicChatDiscoveryQuery(inviteToken), 8)
        .then((results) => {
          if (!cancelled) {
            setDiscoveries(results);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setDiscoveries([]);
          }
        })
        .finally(() => {
          if (!cancelled) {
            setDiscovering(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [exactPublicChatMatch, inviteToken, parsedLink, token]);

  function handleInviteTokenChange(value: string) {
    setInviteToken(value);
    setStatusMessage(null);
  }

  function handleOpenExactPublicChat() {
    if (!exactPublicChatMatch) {
      return;
    }

    onOpenDiscoveryChat(exactPublicChatMatch.chatId);
  }

  async function performJoin(normalized: string) {
    const result = normalized.startsWith("@")
      ? await api.joinChatByUsername(token, normalized)
      : await api.joinChatByLink(token, normalized);

    if (result.status === "JOINED" && result.chat) {
      onJoined(result.chat);
      return;
    }

    setStatusMessage(buildJoinRequestStatusMessage(result));
  }

  async function handleJoin() {
    if (!canJoin || joining) {
      return;
    }

    setJoining(true);
    setError(null);
    setStatusMessage(null);
    try {
      await performJoin(normalizedInviteToken);
    } catch (joinError) {
      setError(joinError instanceof Error ? joinError.message : "Unable to join chat");
    } finally {
      setJoining(false);
    }
  }

  async function handleJoinDiscoveredChat(chat: PublicChatDiscovery) {
    if (joining) {
      return;
    }

    if (chat.joined) {
      onOpenDiscoveryChat(chat.chatId);
      return;
    }

    if (!chat.publicUsername) {
      return;
    }

    setJoining(true);
    setError(null);
    setStatusMessage(null);
    try {
      await performJoin(`@${chat.publicUsername}`);
    } catch (joinError) {
      setError(joinError instanceof Error ? joinError.message : "Unable to join chat");
    } finally {
      setJoining(false);
    }
  }

  return {
    discoveries,
    discovering,
    error,
    canJoin,
    exactPublicChatMatch,
    handleInviteTokenChange,
    handleJoin,
    handleJoinDiscoveredChat,
    handleOpenExactPublicChat,
    inviteToken,
    joining,
    normalizedInviteToken,
    parsedLink,
    statusMessage
  };
}

export type JoinChatByLinkScreenController = ReturnType<typeof useJoinChatByLinkController>;
