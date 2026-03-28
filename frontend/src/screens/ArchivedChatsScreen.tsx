import React from "react";
import { ArchivedChatsScreenContent } from "../components/archivedChats/ArchivedChatsScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useArchivedChatsController } from "../components/archivedChats/useArchivedChatsController";
import type { ChatSummary } from "../types";

type ArchivedChatsScreenProps = {
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  token: string;
};

export function ArchivedChatsScreen({
  onClose,
  onOpenChat,
  token
}: ArchivedChatsScreenProps) {
  const controller = useArchivedChatsController({ token });

  return (
    <AppScreen padding="xl">
      <ArchivedChatsScreenContent
        controller={controller}
        onClose={onClose}
        onOpenChat={onOpenChat}
      />
    </AppScreen>
  );
}
