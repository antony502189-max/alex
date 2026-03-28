import React from "react";
import { GlobalSearchScreenContent } from "../components/globalSearch/GlobalSearchScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useGlobalSearchController } from "../components/globalSearch/useGlobalSearchController";
import type { ParsedDeepLink } from "../navigation/deepLinks";
import type {
  ChatSummary,
  GlobalMessageSearchResult
} from "../types";

type GlobalSearchScreenProps = {
  availableChats: ChatSummary[];
  token: string;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenMessageResult: (chat: ChatSummary, message: GlobalMessageSearchResult["message"]) => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
};

export function GlobalSearchScreen({
  availableChats,
  token,
  onClose,
  onOpenChat,
  onOpenMessageResult,
  onOpenParsedLink
}: GlobalSearchScreenProps) {
  const controller = useGlobalSearchController({
    availableChats,
    onOpenChat,
    token
  });

  return (
    <AppScreen padding="xl">
      <GlobalSearchScreenContent
        controller={controller}
        onClose={onClose}
        onOpenChat={onOpenChat}
        onOpenMessageResult={onOpenMessageResult}
        onOpenParsedLink={onOpenParsedLink}
      />
    </AppScreen>
  );
}
