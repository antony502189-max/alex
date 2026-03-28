import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type { ChatSummary, SharedMediaBuckets } from "../../types";
import {
  buildSharedMediaCountLine,
  buildSharedMediaUpdatedLine
} from "./sharedMediaPresentation";

type SharedMediaOverviewCardProps = {
  buckets: SharedMediaBuckets | null;
  chat: ChatSummary;
};

export function SharedMediaOverviewCard({
  buckets,
  chat
}: SharedMediaOverviewCardProps) {
  return (
    <SectionCard>
      <View style={styles.row}>
        <Avatar uri={chat.photoUrl} size={72} title={chat.title} />
        <View style={styles.copy}>
          <Text style={styles.title}>{chat.title}</Text>
          <Text style={styles.meta}>{buildSharedMediaCountLine(buckets)}</Text>
          <Text style={styles.meta}>{buildSharedMediaUpdatedLine(buckets)}</Text>
        </View>
      </View>
      <View style={styles.chips}>
        <AppChip tone="brand">{buckets?.media.length ?? 0} media</AppChip>
        <AppChip tone="default">{buckets?.files.length ?? 0} files</AppChip>
        <AppChip tone="muted">{buckets?.links.length ?? 0} links</AppChip>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  row: {
    alignItems: "center",
    flexDirection: "row",
    gap: appSpacing.lg
  },
  copy: {
    flex: 1,
    gap: appSpacing.xs
  },
  title: {
    color: appColors.textPrimary,
    fontSize: 20,
    fontWeight: "700"
  },
  meta: {
    color: appColors.textSecondary
  },
  chips: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  }
});
