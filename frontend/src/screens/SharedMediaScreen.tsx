import React from "react";
import { SharedMediaScreenContent } from "../components/sharedMedia/SharedMediaScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useSharedMediaController } from "../components/sharedMedia/useSharedMediaController";
import type { ParsedDeepLink } from "../navigation/deepLinks";
import type { ChatSummary, MessageAttachment } from "../types";

type SharedMediaScreenProps = {
  chat: ChatSummary;
  token: string;
  onClose: () => void;
  onOpenMediaViewer: (payload: {
    attachments: MessageAttachment[];
    attachmentSources?: Array<{
      attachmentId: string;
      createdAt: string;
      messageId: string;
    }>;
    initialAttachmentId: string;
    chatTitle: string;
  }) => void;
  onOpenMessage: (messageId: string, createdAt: string) => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
};

export function SharedMediaScreen({
  chat,
  token,
  onClose,
  onOpenMediaViewer,
  onOpenMessage,
  onOpenParsedLink
}: SharedMediaScreenProps) {
  const controller = useSharedMediaController({
    chat,
    onOpenParsedLink,
    token
  });

  return (
    <AppScreen padding="xl">
      <SharedMediaScreenContent
        chat={chat}
        controller={controller}
        onClose={onClose}
        onOpenMessage={onOpenMessage}
        onOpenMediaViewer={onOpenMediaViewer}
      />
    </AppScreen>
  );
}
