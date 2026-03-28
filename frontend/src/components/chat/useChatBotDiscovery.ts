import { useEffect, useState } from "react";
import { api } from "../../services/api";
import type { ChatSummary, BotCommand, InlineBotResult } from "../../types";
import type { ActiveInlineBotQuery } from "./chatScreenUtils";

type UseChatBotDiscoveryParams = {
  activeInlineQuery: ActiveInlineBotQuery | null;
  chatType: ChatSummary["chatType"];
  peerIsBot: boolean;
  peerUserId: string | null;
  token: string;
};

export function useChatBotDiscovery({
  activeInlineQuery,
  chatType,
  peerIsBot,
  peerUserId,
  token
}: UseChatBotDiscoveryParams) {
  const [botCommands, setBotCommands] = useState<BotCommand[]>([]);
  const [botCommandsError, setBotCommandsError] = useState<string | null>(null);
  const [loadingBotCommands, setLoadingBotCommands] = useState(false);
  const [inlineBotResults, setInlineBotResults] = useState<InlineBotResult[]>([]);
  const [inlineBotResultsError, setInlineBotResultsError] = useState<string | null>(null);
  const [loadingInlineBotResults, setLoadingInlineBotResults] = useState(false);
  const [botCommandsReloadKey, setBotCommandsReloadKey] = useState(0);
  const [inlineResultsReloadKey, setInlineResultsReloadKey] = useState(0);

  useEffect(() => {
    if (chatType !== "DIRECT" || !peerIsBot || !peerUserId) {
      setBotCommands([]);
      setBotCommandsError(null);
      setLoadingBotCommands(false);
      return;
    }

    let cancelled = false;
    setLoadingBotCommands(true);
    setBotCommandsError(null);
    api.getBotCommands(token, peerUserId)
      .then((commands) => {
        if (!cancelled) {
          setBotCommands(commands);
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setBotCommands([]);
          setBotCommandsError(
            loadError instanceof Error ? loadError.message : "Unable to load bot commands"
          );
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingBotCommands(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [botCommandsReloadKey, chatType, peerIsBot, peerUserId, token]);

  useEffect(() => {
    if (!activeInlineQuery) {
      setInlineBotResults([]);
      setInlineBotResultsError(null);
      setLoadingInlineBotResults(false);
      return;
    }

    let cancelled = false;
    const timeoutId = setTimeout(() => {
      setLoadingInlineBotResults(true);
      setInlineBotResultsError(null);
      api.getInlineBotResults(token, activeInlineQuery.botUsername, activeInlineQuery.query)
        .then((results) => {
          if (!cancelled) {
            setInlineBotResults(results);
          }
        })
        .catch((loadError) => {
          if (!cancelled) {
            setInlineBotResults([]);
            setInlineBotResultsError(
              loadError instanceof Error
                ? loadError.message
                : "Unable to load inline bot results"
            );
          }
        })
        .finally(() => {
          if (!cancelled) {
            setLoadingInlineBotResults(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [activeInlineQuery, inlineResultsReloadKey, token]);

  function retryBotCommands() {
    setBotCommandsReloadKey((current) => current + 1);
  }

  function retryInlineBotResults() {
    if (!activeInlineQuery) {
      return;
    }
    setInlineResultsReloadKey((current) => current + 1);
  }

  return {
    botCommands,
    botCommandsError,
    inlineBotResults,
    inlineBotResultsError,
    loadingBotCommands,
    loadingInlineBotResults,
    retryBotCommands,
    retryInlineBotResults,
    setInlineBotResults
  };
}
