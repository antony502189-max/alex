import React from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { ChatSummary } from "../../types";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import {
  buildArchivedChatMeta,
  buildArchivedChatPreview,
  formatArchivedChatAutoDelete
} from "./archivedChatsPresentation";

type ArchivedChatsListProps = {
  chats: ChatSummary[];
  onOpenChat: (chat: ChatSummary) => void;
};

export function ArchivedChatsList({
  chats,
  onOpenChat
}: ArchivedChatsListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.content}
      data={chats}
      keyExtractor={(item) => item.chatId}
      ListEmptyComponent={
        <SectionCard
          description="Archive any chat from the main list and it will appear here."
          title="No archived chats"
        />
      }
      renderItem={({ item }) => {
        const preview = buildArchivedChatPreview(item);
        const ttlLabel = formatArchivedChatAutoDelete(item.autoDeleteSeconds);

        return (
          <Pressable onPress={() => onOpenChat(item)} style={({ pressed }) => [styles.card, pressed && styles.pressed]}>
            <Avatar size={52} title={item.title} uri={item.photoUrl} />
            <View style={styles.body}>
              <View style={styles.topRow}>
                <Text style={styles.title}>{item.title}</Text>
                {item.forumEnabled ? <AppChip tone="brand">Forum</AppChip> : null}
              </View>
              <Text style={styles.meta}>{buildArchivedChatMeta(item)}</Text>
              {ttlLabel ? <Text style={styles.timer}>{ttlLabel}</Text> : null}
              {preview ? (
                <Text numberOfLines={1} style={item.draftText ? styles.draft : styles.meta}>
                  {preview}
                </Text>
              ) : null}
            </View>
          </Pressable>
        );
      }}
    />
  );
}

const styles = StyleSheet.create({
  content: {
    gap: appSpacing.md,
    paddingBottom: appSpacing.xl
  },
  card: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    flexDirection: "row",
    gap: appSpacing.lg,
    padding: appSpacing.lg
  },
  pressed: {
    opacity: 0.92
  },
  body: {
    flex: 1,
    gap: appSpacing.xs
  },
  topRow: {
    alignItems: "center",
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 18,
    fontWeight: "600"
  },
  meta: {
    color: appColors.textSecondary
  },
  timer: {
    color: appColors.success,
    fontWeight: "700"
  },
  draft: {
    color: "#b45309",
    fontWeight: "600"
  }
});
