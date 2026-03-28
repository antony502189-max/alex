import React from "react";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ChatsCollectionList } from "./ChatsCollectionList";
import { ChatsOverviewSection } from "./ChatsOverviewSection";
import type { ChatsListController } from "./useChatsListController";
import type { ChatSummary } from "../../types";

type ChatsListScreenContentProps = {
  controller: ChatsListController;
  onCreateChannel: () => void;
  onCreateGroup: () => void;
  onCreateStory: () => void;
  onOpenArchived: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenFolders: () => void;
  onOpenGlobalSearch: () => void;
  onOpenJoinByLink: () => void;
};

export function ChatsListScreenContent({
  controller,
  onCreateChannel,
  onCreateGroup,
  onCreateStory,
  onOpenArchived,
  onOpenChat,
  onOpenFolders,
  onOpenGlobalSearch,
  onOpenJoinByLink
}: ChatsListScreenContentProps) {
  if (!controller.session) {
    return null;
  }

  return (
    <>
      <AppHeader
        rightSlot={
          <AppButton
            disabled={controller.signingOut}
            onPress={() => void controller.handleLogout()}
            size="sm"
            variant="secondary"
          >
            {controller.signingOut ? "..." : "Sign out"}
          </AppButton>
        }
        subtitle={`Messenger | ${controller.session.displayName}${
          controller.session.username ? ` | @${controller.session.username}` : ""
        }`}
        title="Chats"
      />

      <ChatsOverviewSection
        archivedChatsCount={controller.archivedChatsCount}
        directChatsCount={controller.directChatsCount}
        features={controller.features}
        filterOptions={controller.filterOptions}
        folders={controller.folders}
        onCreateChannel={onCreateChannel}
        onCreateGroup={onCreateGroup}
        onCreateStory={onCreateStory}
        onOpenArchived={onOpenArchived}
        onOpenFolders={onOpenFolders}
        onOpenGlobalSearch={onOpenGlobalSearch}
        onOpenJoinByLink={onOpenJoinByLink}
        onSearchQueryChange={controller.setSearchQuery}
        onSelectFilter={controller.setSelectedFilter}
        onSelectFolder={controller.setSelectedFolderId}
        quickActions={controller.quickActions}
        searchQuery={controller.searchQuery}
        selectedFilter={controller.selectedFilter}
        selectedFolderId={controller.selectedFolderId}
        unreadChatsCount={controller.unreadChatsCount}
        unreadMessagesCount={controller.unreadMessagesCount}
      />

      <ChatsCollectionList
        appearanceSettings={controller.appearanceSettings}
        chats={controller.displayedChats}
        expandedChatId={controller.expandedChatId}
        error={controller.error}
        mutatingChatId={controller.mutatingChatId}
        onChatAction={(chat, action) => void controller.handleChatAction(chat, action)}
        onOpenChat={onOpenChat}
        onToggleChatActions={controller.handleToggleChatActions}
        onRefresh={() => void controller.handleRefresh()}
        refreshing={controller.refreshing}
      />
    </>
  );
}
