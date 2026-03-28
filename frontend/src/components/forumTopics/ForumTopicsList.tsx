import React from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import type { ForumTopic } from "../../types";
import { AppButton } from "../ui/AppButton";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import {
  buildTopicMeta,
  buildTopicTitle
} from "./forumTopicsPresentation";

type ForumTopicsListProps = {
  onOpenTopic: (topic: ForumTopic) => void;
  onToggleTopicClosed: (topic: ForumTopic) => void;
  saving: boolean;
  topics: ForumTopic[];
};

export function ForumTopicsList({
  onOpenTopic,
  onToggleTopicClosed,
  saving,
  topics
}: ForumTopicsListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.listContent}
      data={topics}
      keyExtractor={(item) => item.topicId}
      renderItem={({ item }) => (
        <View style={styles.card}>
          <Pressable onPress={() => onOpenTopic(item)} style={styles.cardBody}>
            <Text style={styles.cardTitle}>{buildTopicTitle(item)}</Text>
            <Text style={styles.cardMeta}>{buildTopicMeta(item)}</Text>
          </Pressable>
          {!item.generalTopic ? (
            <AppButton
              disabled={saving}
              onPress={() => onToggleTopicClosed(item)}
              size="sm"
            >
              {item.closed ? "Reopen" : "Close"}
            </AppButton>
          ) : null}
        </View>
      )}
    />
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: appSpacing.sm + 2,
    paddingBottom: appSpacing.xl
  },
  card: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderRadius: appRadii.md,
    flexDirection: "row",
    gap: appSpacing.md,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.md
  },
  cardBody: {
    flex: 1
  },
  cardTitle: {
    color: appColors.textPrimary,
    fontSize: 16,
    fontWeight: "700"
  },
  cardMeta: {
    color: appColors.textSecondary,
    marginTop: 4
  }
});
