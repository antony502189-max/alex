import React from "react";
import { JoinChatByLinkScreenContent } from "../components/joinChat/JoinChatByLinkScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useJoinChatByLinkController } from "../components/joinChat/useJoinChatByLinkController";
import type { ParsedDeepLink } from "../navigation/deepLinks";
import type { ChatSummary } from "../types";

type JoinChatByLinkScreenProps = {
  availableChats: ChatSummary[];
  token: string;
  initialInviteToken?: string | null;
  onClose: () => void;
  onJoined: (chat: ChatSummary) => void;
  onOpenDiscoveryChat: (chatId: string) => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
};

export function JoinChatByLinkScreen({
  availableChats,
  token,
  initialInviteToken,
  onClose,
  onJoined,
  onOpenDiscoveryChat,
  onOpenParsedLink
}: JoinChatByLinkScreenProps) {
  const controller = useJoinChatByLinkController({
    availableChats,
    initialInviteToken,
    onJoined,
    onOpenDiscoveryChat,
    token
  });

  return (
    <AppScreen padding="xl">
      <JoinChatByLinkScreenContent
        controller={controller}
        onClose={onClose}
        onOpenParsedLink={onOpenParsedLink}
      />
    </AppScreen>
  );
}
