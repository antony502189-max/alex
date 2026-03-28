import React from "react";
import { ChatsListScreenContent } from "../components/chats/ChatsListScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useChatsListController } from "../components/chats/useChatsListController";
import type { ClientFeatureFlags } from "../config/featureFlags";
import { appColors } from "../theme/tokens";
import type { ChatSummary } from "../types";

type ChatsListScreenProps = {
  featureFlags?: Partial<ClientFeatureFlags>;
  onOpenArchived: () => void;
  onOpenCalls: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenContacts: () => void;
  onOpenFolders: () => void;
  onOpenGlobalSearch: () => void;
  onOpenJoinByLink: () => void;
  onOpenProfile: () => void;
  onOpenSavedMessages: () => void;
  onOpenStories: () => void;
  onCreateChannel: () => void;
  onCreateDirect: () => void;
  onCreateGroup: () => void;
  onCreateStory: () => void;
};

export function ChatsListScreen({
  featureFlags,
  onOpenArchived,
  onOpenCalls,
  onOpenChat,
  onOpenContacts,
  onOpenFolders,
  onOpenGlobalSearch,
  onOpenJoinByLink,
  onOpenProfile,
  onOpenSavedMessages,
  onOpenStories,
  onCreateChannel,
  onCreateDirect,
  onCreateGroup,
  onCreateStory
}: ChatsListScreenProps) {
  const controller = useChatsListController({
    featureFlags,
    onCreateDirect,
    onOpenCalls,
    onOpenContacts,
    onOpenProfile,
    onOpenSavedMessages,
    onOpenStories
  });

  return (
    <AppScreen
      backgroundColor={appColors.surfaceMuted}
      paddingHorizontal="lg"
      paddingTop="md"
    >
      <ChatsListScreenContent
        controller={controller}
        onCreateChannel={onCreateChannel}
        onCreateGroup={onCreateGroup}
        onCreateStory={onCreateStory}
        onOpenArchived={onOpenArchived}
        onOpenChat={onOpenChat}
        onOpenFolders={onOpenFolders}
        onOpenGlobalSearch={onOpenGlobalSearch}
        onOpenJoinByLink={onOpenJoinByLink}
      />
    </AppScreen>
  );
}
