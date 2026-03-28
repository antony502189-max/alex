import React from "react";
import { StyleSheet, Text } from "react-native";
import type { SecretChatSummary } from "../../types";
import { appColors, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";

type SecretChatsSeedCardProps = {
  activeForSeedPeer: SecretChatSummary | null;
  creating: boolean;
  onCreateSecretChat: () => void;
  onOpenSecretChat: (chat: SecretChatSummary) => void;
  seedPeerDisplayName?: string | null;
  seedPeerUserId?: string | null;
};

export function SecretChatsSeedCard({
  activeForSeedPeer,
  creating,
  onCreateSecretChat,
  onOpenSecretChat,
  seedPeerDisplayName,
  seedPeerUserId
}: SecretChatsSeedCardProps) {
  if (!seedPeerUserId) {
    return null;
  }

  return (
    <SectionCard
      description="Secret chats are bound to one device and use end-to-end encryption."
      style={styles.card}
      title={seedPeerDisplayName ? `Start with ${seedPeerDisplayName}` : "Start secret chat"}
    >
      {activeForSeedPeer ? (
        <AppButton
          fullWidth
          onPress={() => onOpenSecretChat(activeForSeedPeer)}
          variant="primary"
        >
          {activeForSeedPeer.status === "ACTIVE" ? "Open secret chat" : "Open request"}
        </AppButton>
      ) : (
        <AppButton
          disabled={creating}
          fullWidth
          onPress={onCreateSecretChat}
          variant="primary"
        >
          {creating ? "Creating..." : "Create secret chat"}
        </AppButton>
      )}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: appSpacing.md
  }
});
