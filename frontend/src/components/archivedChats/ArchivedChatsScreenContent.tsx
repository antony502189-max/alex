import React from "react";
import { ArchivedChatsList } from "./ArchivedChatsList";
import type { ArchivedChatsScreenController } from "./useArchivedChatsController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import type { ChatSummary } from "../../types";

type ArchivedChatsScreenContentProps = {
  controller: ArchivedChatsScreenController;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
};

export function ArchivedChatsScreenContent({
  controller,
  onClose,
  onOpenChat
}: ArchivedChatsScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        rightSlot={(
          <AppButton onPress={() => void controller.loadArchivedChats()} size="sm">
            Refresh
          </AppButton>
        )}
        title="Archived"
      />
      <ScreenFeedback error={controller.error} loading={controller.loading} />

      <ArchivedChatsList chats={controller.chats} onOpenChat={onOpenChat} />
    </>
  );
}
