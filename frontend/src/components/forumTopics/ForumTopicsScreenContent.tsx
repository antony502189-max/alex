import React from "react";
import { ForumTopicComposer } from "./ForumTopicComposer";
import { ForumTopicsList } from "./ForumTopicsList";
import { buildForumTopicsSubtitle } from "./forumTopicsPresentation";
import type { ForumTopicsScreenController } from "./useForumTopicsController";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import type { ChatSummary, ForumTopic } from "../../types";

type ForumTopicsScreenContentProps = {
  chat: ChatSummary;
  controller: ForumTopicsScreenController;
  onBack: () => void;
  onOpenTopic: (topic: ForumTopic) => void;
};

export function ForumTopicsScreenContent({
  chat,
  controller,
  onBack,
  onOpenTopic
}: ForumTopicsScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onBack}
        subtitle={buildForumTopicsSubtitle(controller.topics.length)}
        title={chat.title}
      />

      <ForumTopicComposer
        iconEmoji={controller.iconEmoji}
        onCreateTopic={() => void controller.handleCreateTopic()}
        onIconEmojiChange={controller.setIconEmoji}
        onTitleChange={controller.setTitle}
        saving={controller.saving}
        title={controller.title}
      />
      <ScreenFeedback error={controller.error} loading={controller.loading} />

      <ForumTopicsList
        onOpenTopic={onOpenTopic}
        onToggleTopicClosed={(topic) => void controller.handleToggleTopicClosed(topic)}
        saving={controller.saving}
        topics={controller.topics}
      />
    </>
  );
}
