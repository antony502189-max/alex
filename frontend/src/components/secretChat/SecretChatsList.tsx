import React from "react";
import { FlatList, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { SecretChatSummary } from "../../types";
import {
  buildSecretChatPeerMeta,
  formatSecretChatListState
} from "./secretChatsPresentation";

type SecretChatsListProps = {
  handlingChatId: string | null;
  onAccept: (secretChat: SecretChatSummary) => void;
  onDecline: (secretChat: SecretChatSummary) => void;
  onOpenSecretChat: (secretChat: SecretChatSummary) => void;
  secretChats: SecretChatSummary[];
};

export function SecretChatsList({
  handlingChatId,
  onAccept,
  onDecline,
  onOpenSecretChat,
  secretChats
}: SecretChatsListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.listContent}
      data={secretChats}
      keyExtractor={(item) => item.secretChatId}
      ListEmptyComponent={
        <SectionCard
          description="No secret chats yet. Open a direct chat and start one from the header."
          title="No secret chats"
        />
      }
      renderItem={({ item }) => (
        <View style={styles.secretChatCard}>
          <Avatar size={56} title={item.peerDisplayName} uri={item.peerPhotoUrl} />
          <View style={styles.secretChatBody}>
            <Text style={styles.secretChatTitle}>{item.peerDisplayName}</Text>
            <Text style={styles.secretChatMeta}>{formatSecretChatListState(item)}</Text>
            <Text style={styles.secretChatMeta}>{buildSecretChatPeerMeta(item)}</Text>
          </View>
          {item.status === "PENDING" && item.direction === "INCOMING" ? (
            <View style={styles.inlineActions}>
              <AppButton
                disabled={handlingChatId === item.secretChatId}
                onPress={() => onAccept(item)}
                size="sm"
                variant="primary"
              >
                Accept
              </AppButton>
              <AppButton
                disabled={handlingChatId === item.secretChatId}
                onPress={() => onDecline(item)}
                size="sm"
                variant="danger"
              >
                Decline
              </AppButton>
            </View>
          ) : (
            <AppButton
              onPress={() => {
                if (item.status === "ACTIVE" || item.status === "PENDING") {
                  onOpenSecretChat(item);
                }
              }}
              size="sm"
            >
              {item.status === "ACTIVE" ? "Open" : "Details"}
            </AppButton>
          )}
        </View>
      )}
    />
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: appSpacing.sm,
    paddingBottom: appSpacing.xl
  },
  secretChatCard: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderRadius: appRadii.lg,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.md
  },
  secretChatBody: {
    flex: 1,
    gap: 4
  },
  secretChatTitle: {
    color: appColors.textPrimary,
    fontSize: 18,
    fontWeight: "700"
  },
  secretChatMeta: {
    color: appColors.textSecondary,
    fontSize: 13
  },
  inlineActions: {
    gap: appSpacing.xs
  }
});
