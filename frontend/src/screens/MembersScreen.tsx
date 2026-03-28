import React from "react";
import { MembersScreenContent } from "../components/members/MembersScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useMembersScreenController } from "../components/members/useMembersScreenController";
import type { ChatSummary } from "../types";

type MembersScreenProps = {
  chat: ChatSummary;
  currentUserId: string;
  token: string;
  onClose: () => void;
  onOpenDiscussionChat?: (chatId: string) => void;
  onOpenSharedMedia?: (chat: ChatSummary) => void;
  onChatUpdated?: (chat: ChatSummary) => void;
  onChatLeft?: (chatId: string) => void;
  onHistoryCleared?: (chatId: string) => void;
};

export function MembersScreen({
  chat,
  currentUserId,
  token,
  onClose,
  onOpenDiscussionChat,
  onOpenSharedMedia,
  onChatUpdated,
  onChatLeft,
  onHistoryCleared
}: MembersScreenProps) {
  const controller = useMembersScreenController({
    chat,
    currentUserId,
    onChatLeft,
    onChatUpdated,
    onClose,
    onHistoryCleared,
    token
  });

  return (
    <AppScreen padding="xl">
      <MembersScreenContent
        chat={chat}
        controller={controller}
        currentUserId={currentUserId}
        onClose={onClose}
        onOpenDiscussionChat={onOpenDiscussionChat}
        onOpenSharedMedia={onOpenSharedMedia}
      />
    </AppScreen>
  );
}
