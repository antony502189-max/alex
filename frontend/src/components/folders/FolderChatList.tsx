import React from "react";
import { FlatList, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { ChatSummary } from "../../types";
import {
  buildFolderEmptyState,
  formatFolderChatMeta
} from "./foldersPresentation";

type FolderChatListProps = {
  chats: ChatSummary[];
  onToggleChat: (chatId: string) => void;
  selectedChatIds: string[];
};

export function FolderChatList({
  chats,
  onToggleChat,
  selectedChatIds
}: FolderChatListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.listContent}
      data={chats}
      keyExtractor={(item) => item.chatId}
      keyboardShouldPersistTaps="handled"
      ListEmptyComponent={
        <SectionCard description={buildFolderEmptyState()} title="No chats available" />
      }
      renderItem={({ item }) => {
        const selected = selectedChatIds.includes(item.chatId);
        return (
          <View style={[styles.chatCard, selected && styles.chatCardSelected]}>
            <Avatar size={44} title={item.title} uri={item.photoUrl} />
            <View style={styles.chatBody}>
              <View style={styles.chatTopRow}>
                <Text style={styles.chatTitle}>{item.title}</Text>
                {item.forumEnabled ? <AppChip tone="brand">Forum</AppChip> : null}
              </View>
              <Text style={styles.chatMeta}>{formatFolderChatMeta(item)}</Text>
            </View>
            <AppButton
              onPress={() => onToggleChat(item.chatId)}
              size="sm"
              variant={selected ? "primary" : "secondary"}
            >
              {selected ? "Selected" : "Add"}
            </AppButton>
          </View>
        );
      }}
    />
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: appSpacing.sm,
    paddingBottom: appSpacing.xl
  },
  chatCard: {
    alignItems: "center",
    backgroundColor: "#ffffff",
    borderColor: appColors.border,
    borderRadius: appRadii.lg,
    borderWidth: 1,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.md
  },
  chatCardSelected: {
    borderColor: appColors.textPrimary,
    borderWidth: 2
  },
  chatBody: {
    flex: 1,
    gap: appSpacing.xs
  },
  chatTopRow: {
    alignItems: "center",
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  chatTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  chatMeta: {
    color: appColors.textSecondary
  }
});
