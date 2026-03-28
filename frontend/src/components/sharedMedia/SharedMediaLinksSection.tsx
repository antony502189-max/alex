import React from "react";
import { StyleSheet, Text, View } from "react-native";
import type { SharedMediaLink } from "../../types";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";

type SharedMediaLinksSectionProps = {
  entries: SharedMediaLink[];
  onOpenMessage: (messageId: string, createdAt: string) => void;
  onOpenLink: (linkId: string, url: string) => void;
  openingLinkId: string | null;
};

export function SharedMediaLinksSection({
  entries,
  onOpenMessage,
  onOpenLink,
  openingLinkId
}: SharedMediaLinksSectionProps) {
  return (
    <SectionCard
      description="Links extracted from recent text and captions."
      title="Links"
    >
      {entries.length > 0 ? (
        <View style={styles.list}>
          {entries.map((entry) => (
            <View key={entry.linkId} style={styles.card}>
              <View style={styles.copy}>
                <Text style={styles.url}>{entry.url}</Text>
                {entry.label ? (
                  <Text numberOfLines={2} style={styles.label}>
                    {entry.label}
                  </Text>
                ) : null}
                <Text style={styles.meta}>{new Date(entry.createdAt).toLocaleString()}</Text>
              </View>
              <View style={styles.actions}>
                <AppButton
                  disabled={openingLinkId === entry.linkId}
                  onPress={() => onOpenLink(entry.linkId, entry.url)}
                  size="sm"
                >
                  {openingLinkId === entry.linkId ? "Opening..." : "Open"}
                </AppButton>
                <AppButton
                  onPress={() => onOpenMessage(entry.messageId, entry.createdAt)}
                  size="sm"
                  variant="secondary"
                >
                  View in chat
                </AppButton>
              </View>
            </View>
          ))}
        </View>
      ) : (
        <Text style={styles.emptyText}>No links found in recent history.</Text>
      )}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  list: {
    gap: appSpacing.sm + 2
  },
  card: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.lg
  },
  copy: {
    flex: 1,
    gap: appSpacing.xs
  },
  actions: {
    alignItems: "flex-end",
    gap: appSpacing.sm
  },
  url: {
    color: appColors.brandText,
    fontWeight: "700"
  },
  label: {
    color: appColors.textPrimary
  },
  meta: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  emptyText: {
    color: appColors.textSecondary
  }
});
