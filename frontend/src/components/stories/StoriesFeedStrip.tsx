import React from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { StoryFeedItem } from "../../types";
import { getStoryFeedStatusLabel } from "./storiesPresentation";

type StoriesFeedStripProps = {
  feed: StoryFeedItem[];
  onSelectOwner: (item: StoryFeedItem) => void;
  selectedOwnerId: string | null;
};

export function StoriesFeedStrip({
  feed,
  onSelectOwner,
  selectedOwnerId
}: StoriesFeedStripProps) {
  if (feed.length === 0) {
    return null;
  }

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.content}
    >
      {feed.map((item) => (
        <Pressable
          key={item.ownerUserId}
          onPress={() => onSelectOwner(item)}
          style={({ pressed }) => [
            styles.card,
            selectedOwnerId === item.ownerUserId && styles.cardActive,
            pressed && styles.cardPressed
          ]}
        >
          <Text style={styles.title}>{item.ownerDisplayName}</Text>
          <View style={styles.metaRow}>
            <Text style={styles.meta}>{getStoryFeedStatusLabel(item)}</Text>
            <Text style={styles.meta}>
              {item.stories.length} story{item.stories.length === 1 ? "" : "s"}
            </Text>
          </View>
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: appSpacing.sm,
    paddingBottom: appSpacing.md
  },
  card: {
    backgroundColor: appColors.surface,
    borderColor: appColors.border,
    borderRadius: appRadii.lg,
    borderWidth: 1,
    gap: appSpacing.xs,
    minWidth: 132,
    paddingHorizontal: appSpacing.lg,
    paddingVertical: appSpacing.md
  },
  cardActive: {
    borderColor: appColors.textPrimary,
    borderWidth: 2
  },
  cardPressed: {
    opacity: 0.9
  },
  title: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  metaRow: {
    gap: appSpacing.xs
  },
  meta: {
    color: appColors.textSecondary,
    fontSize: 12
  }
});
