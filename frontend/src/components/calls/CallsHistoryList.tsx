import React from "react";
import { FlatList, Pressable, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { CallHistoryEntry } from "../../types";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import {
  buildCallHistoryMeta,
  buildCallHistoryStatusLabel,
  buildCallHistorySubtitle
} from "./callsPresentation";

type CallsHistoryListProps = {
  calls: CallHistoryEntry[];
  emptyStateDescription: string;
  emptyStateTitle: string;
  onCallBack: (chatId: string, kind: "VOICE" | "VIDEO") => void;
  onOpenChat: (chatId: string) => void;
};

export function CallsHistoryList({
  calls,
  emptyStateDescription,
  emptyStateTitle,
  onCallBack,
  onOpenChat
}: CallsHistoryListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.listContent}
      data={calls}
      keyExtractor={(item) => item.callId}
      ListEmptyComponent={
        <SectionCard
          description={emptyStateDescription}
          title={emptyStateTitle}
        />
      }
      renderItem={({ item }) => (
        <Pressable onPress={() => onOpenChat(item.chatId)} style={({ pressed }) => [styles.callCard, pressed && styles.pressed]}>
          <Avatar size={56} title={item.title} uri={item.photoUrl} />
          <View style={styles.callBody}>
            <View style={styles.callTopRow}>
              <Text style={styles.callTitle}>{item.title}</Text>
              {buildCallHistoryStatusLabel(item) ? (
                <View
                  style={[
                    styles.statusBadge,
                    item.missed ? styles.statusBadgeMissed : styles.statusBadgeNeutral
                  ]}
                >
                  <Text
                    style={[
                      styles.statusBadgeText,
                      item.missed ? styles.statusBadgeTextMissed : styles.statusBadgeTextNeutral
                    ]}
                  >
                    {buildCallHistoryStatusLabel(item)}
                  </Text>
                </View>
              ) : null}
            </View>
            <Text style={[styles.callMeta, item.missed && styles.callMetaMissed]}>
              {buildCallHistorySubtitle(item)}
            </Text>
            <Text style={styles.callMeta}>{buildCallHistoryMeta(item)}</Text>
          </View>
          <AppButton onPress={() => onCallBack(item.chatId, item.kind)} size="sm">
            {item.kind === "VIDEO" ? "Video" : "Call"}
          </AppButton>
        </Pressable>
      )}
    />
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: appSpacing.md,
    paddingBottom: appSpacing.xxl
  },
  callCard: {
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
  callBody: {
    flex: 1,
    gap: appSpacing.xs
  },
  callTopRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: appSpacing.sm
  },
  callTitle: {
    color: appColors.textPrimary,
    flex: 1,
    fontSize: 18,
    fontWeight: "700"
  },
  callMeta: {
    color: appColors.textSecondary,
    fontSize: 13
  },
  callMetaMissed: {
    color: appColors.danger,
    fontWeight: "700"
  },
  statusBadge: {
    borderRadius: appRadii.pill,
    paddingHorizontal: 10,
    paddingVertical: 5
  },
  statusBadgeMissed: {
    backgroundColor: "#fee2e2"
  },
  statusBadgeNeutral: {
    backgroundColor: appColors.surfaceAccent
  },
  statusBadgeText: {
    fontSize: 12,
    fontWeight: "700"
  },
  statusBadgeTextMissed: {
    color: appColors.danger,
  },
  statusBadgeTextNeutral: {
    color: appColors.brandText
  }
});
