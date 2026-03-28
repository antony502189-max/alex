import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { AppShortcutCard } from "../ui/AppShortcutCard";
import { AppTextField } from "../ui/AppTextField";
import { appColors, appSpacing } from "../../theme/tokens";
import type { ChatFolder } from "../../types";
import type { ChatFilter, ChatsFeatureFlags } from "./chatsListPresentation";

export type ChatsQuickAction = {
  caption: string;
  key: string;
  onPress: () => void;
  title: string;
  tone?: "brand" | "dark" | "warm";
};

type ChatsOverviewSectionProps = {
  archivedChatsCount: number;
  directChatsCount: number;
  features: ChatsFeatureFlags;
  filterOptions: Array<{ id: ChatFilter; label: string }>;
  folders: ChatFolder[];
  onCreateChannel: () => void;
  onCreateGroup: () => void;
  onCreateStory: () => void;
  onOpenArchived: () => void;
  onOpenFolders: () => void;
  onOpenGlobalSearch: () => void;
  onOpenJoinByLink: () => void;
  onSearchQueryChange: (value: string) => void;
  onSelectFilter: (value: ChatFilter) => void;
  onSelectFolder: (value: string | null) => void;
  quickActions: ChatsQuickAction[];
  searchQuery: string;
  selectedFilter: ChatFilter;
  selectedFolderId: string | null;
  unreadChatsCount: number;
  unreadMessagesCount: number;
};

export function ChatsOverviewSection({
  archivedChatsCount,
  directChatsCount,
  features,
  filterOptions,
  folders,
  onCreateChannel,
  onCreateGroup,
  onCreateStory,
  onOpenArchived,
  onOpenFolders,
  onOpenGlobalSearch,
  onOpenJoinByLink,
  onSearchQueryChange,
  onSelectFilter,
  onSelectFolder,
  quickActions,
  searchQuery,
  selectedFilter,
  selectedFolderId,
  unreadChatsCount,
  unreadMessagesCount
}: ChatsOverviewSectionProps) {
  return (
    <>
      <View style={styles.heroCard}>
        <View style={styles.heroTopRow}>
          <View style={styles.heroStat}>
            <Text style={styles.heroStatValue}>{unreadChatsCount}</Text>
            <Text style={styles.heroStatLabel}>active dialogs</Text>
          </View>
          <View style={styles.heroStat}>
            <Text style={styles.heroStatValue}>{unreadMessagesCount}</Text>
            <Text style={styles.heroStatLabel}>unread messages</Text>
          </View>
          <View style={styles.heroStat}>
            <Text style={styles.heroStatValue}>{directChatsCount}</Text>
            <Text style={styles.heroStatLabel}>personal chats</Text>
          </View>
        </View>
        <View style={styles.heroActionsRow}>
          <AppButton
            onPress={onOpenGlobalSearch}
            style={styles.heroPrimaryAction}
            textStyle={styles.heroPrimaryActionText}
          >
            Global Search
          </AppButton>
          <AppButton
            onPress={onCreateGroup}
            style={styles.heroSecondaryAction}
            textStyle={styles.heroSecondaryActionText}
            variant="secondary"
          >
            New Group
          </AppButton>
          <AppButton
            onPress={onCreateChannel}
            style={styles.heroSecondaryAction}
            textStyle={styles.heroSecondaryActionText}
            variant="secondary"
          >
            New Channel
          </AppButton>
        </View>
      </View>

      <AppTextField
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={onSearchQueryChange}
        placeholder="Search chats, usernames, phones, drafts"
        style={styles.searchInput}
        value={searchQuery}
      />

      <View style={styles.filterRow}>
        {filterOptions.map((filter) => (
          <AppChip
            active={selectedFilter === filter.id}
            key={filter.id}
            onPress={() => onSelectFilter(filter.id)}
          >
            {filter.label}
          </AppChip>
        ))}
      </View>

      <View style={styles.folderRow}>
        <AppChip active={!selectedFolderId} onPress={() => onSelectFolder(null)}>
          All
        </AppChip>
        {folders.map((folder) => (
          <AppChip
            active={selectedFolderId === folder.folderId}
            key={folder.folderId}
            onPress={() => onSelectFolder(folder.folderId)}
          >
            {folder.title}
          </AppChip>
        ))}
        <AppButton onPress={onOpenFolders} size="sm" variant="secondary">
          Manage
        </AppButton>
      </View>

      <View style={styles.quickActionsGrid}>
        {quickActions.map((action) => (
          <AppShortcutCard
            caption={action.caption}
            key={action.key}
            onPress={action.onPress}
            title={action.title}
            tone={action.tone ?? "default"}
          />
        ))}
      </View>

      <View style={styles.utilityRow}>
        <AppButton onPress={onOpenJoinByLink} size="sm" variant="secondary">
          Join Link
        </AppButton>
        {features.stories ? (
          <AppButton onPress={onCreateStory} size="sm" variant="secondary">
            New Story
          </AppButton>
        ) : null}
        <AppButton onPress={onOpenArchived} size="sm" variant="secondary">
          {`Archive ${archivedChatsCount}`}
        </AppButton>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  heroCard: {
    borderRadius: 24,
    backgroundColor: appColors.textPrimary,
    marginBottom: appSpacing.md,
    padding: appSpacing.lg
  },
  heroTopRow: {
    flexDirection: "row",
    gap: appSpacing.md,
    marginBottom: appSpacing.lg
  },
  heroStat: {
    flex: 1
  },
  heroStatValue: {
    color: appColors.inverse,
    fontSize: 26,
    fontWeight: "800"
  },
  heroStatLabel: {
    marginTop: appSpacing.xs,
    color: "#cbd5e1",
    fontSize: 12
  },
  heroActionsRow: {
    flexDirection: "row",
    gap: appSpacing.sm + 2
  },
  heroPrimaryAction: {
    flex: 1.3,
    backgroundColor: appColors.brand,
    borderRadius: 16
  },
  heroPrimaryActionText: {
    color: appColors.inverse,
    fontWeight: "700"
  },
  heroSecondaryAction: {
    flex: 1,
    backgroundColor: "#1e293b",
    borderColor: "#334155",
    borderRadius: 16,
    borderWidth: 1
  },
  heroSecondaryActionText: {
    color: "#e2e8f0",
    fontWeight: "700"
  },
  searchInput: {
    marginBottom: appSpacing.md
  },
  filterRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginBottom: appSpacing.sm + 2
  },
  folderRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginBottom: appSpacing.md
  },
  quickActionsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm + 2,
    marginBottom: appSpacing.sm + 2
  },
  utilityRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm,
    marginBottom: appSpacing.sm + 2
  }
});
