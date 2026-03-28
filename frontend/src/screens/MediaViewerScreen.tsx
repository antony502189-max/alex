import React from "react";
import { MediaViewerScreenContent } from "../components/mediaViewer/MediaViewerScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useMediaViewerController } from "../components/mediaViewer/useMediaViewerController";
import type { MessageAttachment } from "../types";

type MediaViewerScreenProps = {
  attachments: MessageAttachment[];
  attachmentSources?: Array<{
    attachmentId: string;
    createdAt: string;
    messageId: string;
  }>;
  initialAttachmentId: string;
  chatTitle: string;
  token: string;
  onClose: () => void;
  onOpenMessage?: (messageId: string, createdAt: string) => void;
};

export function MediaViewerScreen({
  attachments,
  attachmentSources = [],
  initialAttachmentId,
  chatTitle,
  token,
  onClose,
  onOpenMessage
}: MediaViewerScreenProps) {
  const controller = useMediaViewerController({
    attachments,
    initialAttachmentId,
    token
  });
  const sourceMessage = controller.currentAttachment
    ? attachmentSources.find(
        (source) => source.attachmentId === controller.currentAttachment?.attachmentId
      ) ?? null
    : null;

  return (
    <AppScreen backgroundColor="#020617" padding="xl">
      <MediaViewerScreenContent
        attachmentCount={attachments.length}
        chatTitle={chatTitle}
        controller={controller}
        onClose={onClose}
        onOpenMessage={onOpenMessage}
        sourceMessage={sourceMessage}
      />
    </AppScreen>
  );
}
