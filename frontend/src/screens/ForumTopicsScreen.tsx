import React from "react";
import { ForumTopicsScreenContent } from "../components/forumTopics/ForumTopicsScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useForumTopicsController } from "../components/forumTopics/useForumTopicsController";
import type { ChatSummary, ForumTopic } from "../types";

type ForumTopicsScreenProps = {
  chat: ChatSummary;
  currentUserId: string;
  token: string;
  onBack: () => void;
  onOpenTopic: (topic: ForumTopic) => void;
  onRefreshChats?: () => Promise<void> | void;
};

export function ForumTopicsScreen({
  chat,
  currentUserId,
  token,
  onBack,
  onOpenTopic,
  onRefreshChats
}: ForumTopicsScreenProps) {
  const controller = useForumTopicsController({
    chat,
    currentUserId,
    onRefreshChats,
    token
  });

  return (
    <AppScreen padding="xl">
      <ForumTopicsScreenContent
        chat={chat}
        controller={controller}
        onBack={onBack}
        onOpenTopic={onOpenTopic}
      />
    </AppScreen>
  );
}
