import React from "react";
import { ContactsScreenContent } from "../components/contacts/ContactsScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useContactsController } from "../components/contacts/useContactsController";
import type { ChatSummary } from "../types";

type ContactsScreenProps = {
  token: string;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
};

export function ContactsScreen({
  token,
  onClose,
  onOpenChat,
  onOpenBotMiniApp
}: ContactsScreenProps) {
  const controller = useContactsController({
    onOpenChat,
    token
  });

  return (
    <AppScreen padding="xl">
      <ContactsScreenContent
        controller={controller}
        onClose={onClose}
        onOpenBotMiniApp={onOpenBotMiniApp}
      />
    </AppScreen>
  );
}
