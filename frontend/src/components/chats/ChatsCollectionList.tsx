import React from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../Avatar";
import { AppBanner } from "../ui/AppBanner";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { AppearanceSettings, ChatSummary } from "../../types";
import {
  buildChatRowViewModel
} from "./chatsListPresentation";

type ChatsCollectionListProps = {
  appearanceSettings: AppearanceSettings;
  chats: ChatSummary[];
  expandedChatId: string | null;
  error: string | null;
  mutatingChatId: string | null;
  onChatAction: (chat: ChatSummary, action: "ARCHIVE" | "MUTE" | "UNREAD" | "PIN") => void;
  onOpenChat: (chat: ChatSummary) => void;
  onToggleChatActions: (chatId: string) => void;
  onRefresh: () => void;
  refreshing: boolean;
};

function renderChatBadge(
  label: string,
  tone: "brand" | "default" | "muted" | "success" | "warning"
) {
  return (
    <AppChip key={label} tone={tone}>
      {label}
    </AppChip>
  );
}

export function ChatsCollectionList({
  appearanceSettings,
  chats,
  expandedChatId,
  error,
  mutatingChatId,
  onChatAction,
  onOpenChat,
  onToggleChatActions,
  onRefresh,
  refreshing
}: ChatsCollectionListProps) {
  return (
    <>
      {refreshing ? <ActivityIndicator color={appColors.brand} style={styles.loader} /> : null}
      {error ? <AppBanner message={error} tone="danger" /> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={chats}
        keyExtractor={(item) => item.chatId}
        refreshControl={
          <RefreshControl onRefresh={onRefresh} refreshing={refreshing} tintColor={appColors.brand} />
        }
        ListEmptyComponent={
          <SectionCard
            description="Try clearing the search, switching folder/filter chips, or create a new dialog."
            style={styles.emptyStateCard}
            title="No chats match the current view"
          />
        }
        renderItem={({ item }) => (
          <Pressable
            onLongPress={() => onToggleChatActions(item.chatId)}
            onPress={() => onOpenChat(item)}
            style={[
              styles.chatCard,
              appearanceSettings.compactChatList && styles.chatCardCompact
            ]}
          >
            {appearanceSettings.showChatAvatars ? (
              <Avatar uri={item.photoUrl} title={item.title} size={appearanceSettings.compactChatList ? 48 : 56} />
            ) : null}
            <View style={styles.chatBody}>
              {(() => {
                const row = buildChatRowViewModel(item);
                return (
                  <>
                    <View style={styles.chatTopRow}>
                      <Text numberOfLines={1} style={styles.chatName}>
                        {row.title}
                      </Text>
                      <Text style={styles.chatTime}>{row.trailingLabel}</Text>
                    </View>
                    <View style={styles.chatBadgeRow}>
                      {row.badges.map((badge) => renderChatBadge(badge.label, badge.tone))}
                    </View>
                    <Text numberOfLines={1} style={styles.chatMeta}>
                      {row.subtitle}
                    </Text>
                    {row.autoDeleteLabel ? (
                      <Text style={styles.chatTimer}>{row.autoDeleteLabel}</Text>
                    ) : null}
                    {row.draftLabel ? (
                      <Text numberOfLines={1} style={styles.chatDraft}>
                        {row.draftLabel}
                      </Text>
                    ) : row.aboutLabel ? (
                      <Text numberOfLines={1} style={styles.chatAbout}>
                        {row.aboutLabel}
                      </Text>
                    ) : null}
                  </>
                );
              })()}
              {expandedChatId === item.chatId ? (
                <View style={styles.actionRail}>
                  <AppButton
                    disabled={mutatingChatId === item.chatId}
                    onPress={() => onChatAction(item, "ARCHIVE")}
                    size="sm"
                    variant="secondary"
                  >
                    {item.archived ? "Unarchive" : "Archive"}
                  </AppButton>
                  <AppButton
                    disabled={mutatingChatId === item.chatId}
                    onPress={() => onChatAction(item, "MUTE")}
                    size="sm"
                    variant="secondary"
                  >
                    {item.mutedUntil && new Date(item.mutedUntil).getTime() > Date.now()
                      ? "Unmute"
                      : "Mute"}
                  </AppButton>
                  <AppButton
                    disabled={mutatingChatId === item.chatId}
                    onPress={() => onChatAction(item, "UNREAD")}
                    size="sm"
                    variant="secondary"
                  >
                    {item.markedUnread ? "Read" : "Unread"}
                  </AppButton>
                  <AppButton
                    disabled={mutatingChatId === item.chatId}
                    onPress={() => onChatAction(item, "PIN")}
                    size="sm"
                    variant="secondary"
                  >
                    {item.pinned ? "Unpin" : "Pin"}
                  </AppButton>
                </View>
              ) : (
                <Text style={styles.actionHint}>Long press for inbox actions</Text>
              )}
            </View>
          </Pressable>
        )}
      />
    </>
  );
}

const styles = StyleSheet.create({
  loader: {
    marginVertical: appSpacing.sm
  },
  listContent: {
    gap: appSpacing.sm + 2,
    paddingBottom: appSpacing.xl
  },
  emptyStateCard: {
    marginTop: appSpacing.sm
  },
  chatCard: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderColor: appColors.border,
    borderRadius: appRadii.xl,
    borderWidth: 1,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.md
  },
  chatCardCompact: {
    paddingVertical: appSpacing.sm,
    paddingHorizontal: appSpacing.md
  },
  chatBody: {
    flex: 1,
    gap: 5
  },
  chatTopRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: appSpacing.sm + 2
  },
  chatName: {
    flex: 1,
    color: appColors.textPrimary,
    fontSize: 17,
    fontWeight: "700"
  },
  chatTime: {
    color: appColors.textSecondary,
    fontSize: 12,
    fontWeight: "600"
  },
  chatBadgeRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6
  },
  chatMeta: {
    color: appColors.textSecondary,
    fontSize: 13
  },
  chatTimer: {
    fontSize: 12,
    color: "#0f766e",
    fontWeight: "700"
  },
  chatDraft: {
    color: "#c2410c",
    fontSize: 13,
    fontWeight: "700"
  },
  chatAbout: {
    color: appColors.textSecondary,
    fontSize: 13
  },
  actionHint: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  actionRail: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginTop: appSpacing.xs
  }
});
