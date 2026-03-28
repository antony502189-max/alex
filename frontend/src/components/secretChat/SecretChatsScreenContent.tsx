import React from "react";
import { SecretChatsList } from "./SecretChatsList";
import { SecretChatsSeedCard } from "./SecretChatsSeedCard";
import type { SecretChatsScreenController } from "./useSecretChatsController";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenStack } from "../ui/ScreenStack";
import type { SecretChatSummary } from "../../types";

type SecretChatsScreenContentProps = {
  controller: SecretChatsScreenController;
  onClose: () => void;
  onOpenSecretChat: (chat: SecretChatSummary) => void;
  seedPeerDisplayName?: string | null;
  seedPeerUserId?: string | null;
};

export function SecretChatsScreenContent({
  controller,
  onClose,
  onOpenSecretChat,
  seedPeerDisplayName,
  seedPeerUserId
}: SecretChatsScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        subtitle="Device-bound end-to-end sessions"
        title="Secret Chats"
      />

      <SecretChatsSeedCard
        activeForSeedPeer={controller.activeForSeedPeer}
        creating={controller.creating}
        onCreateSecretChat={() => void controller.handleCreateSecretChat()}
        onOpenSecretChat={onOpenSecretChat}
        seedPeerDisplayName={seedPeerDisplayName}
        seedPeerUserId={seedPeerUserId}
      />
      <ScreenFeedback error={controller.error} loading={controller.loading} />

      <ScreenStack flex={1}>
        <SecretChatsList
          handlingChatId={controller.handlingChatId}
          onAccept={(secretChat) => void controller.handleAccept(secretChat)}
          onDecline={(secretChat) => void controller.handleDecline(secretChat)}
          onOpenSecretChat={onOpenSecretChat}
          secretChats={controller.secretChats}
        />
      </ScreenStack>
    </>
  );
}
