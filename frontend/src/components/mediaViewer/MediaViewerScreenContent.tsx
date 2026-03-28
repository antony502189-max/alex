import React from "react";
import { MediaViewerHeader } from "./MediaViewerHeader";
import { MediaViewerMetaCard } from "./MediaViewerMetaCard";
import { MediaViewerNavigation } from "./MediaViewerNavigation";
import { MediaViewerStage } from "./MediaViewerStage";
import type { MediaViewerScreenController } from "./useMediaViewerController";
import { AppButton } from "../ui/AppButton";
import { ScreenFeedback } from "../ui/ScreenFeedback";

type MediaViewerScreenContentProps = {
  attachmentCount: number;
  chatTitle: string;
  controller: MediaViewerScreenController;
  onClose: () => void;
  onOpenMessage?: (messageId: string, createdAt: string) => void;
  sourceMessage: {
    createdAt: string;
    messageId: string;
  } | null;
};

export function MediaViewerScreenContent({
  attachmentCount,
  chatTitle,
  controller,
  onClose,
  onOpenMessage,
  sourceMessage
}: MediaViewerScreenContentProps) {
  return (
    <>
      <MediaViewerHeader
        attachmentCount={attachmentCount}
        chatTitle={chatTitle}
        currentIndex={controller.currentIndex}
        onClose={onClose}
        onShare={() => void controller.handleShareCurrent()}
        shareDisabled={!controller.currentAttachment || controller.sharingAttachmentId !== null}
        sharing={controller.sharingAttachmentId !== null}
      />

      <ScreenFeedback error={controller.error} />

      <MediaViewerStage
        attachment={controller.currentAttachment}
        currentUri={controller.currentUri}
        loadingLocalAttachmentId={controller.loadingLocalAttachmentId}
        onToggleVideoPlayback={controller.handleToggleVideoPlayback}
        videoPlaying={controller.videoPlaying}
      />

      <MediaViewerMetaCard
        actionSlot={
          sourceMessage && onOpenMessage
            ? (
                <AppButton
                  onPress={() => onOpenMessage(sourceMessage.messageId, sourceMessage.createdAt)}
                  size="sm"
                >
                  View in chat
                </AppButton>
              )
            : null
        }
        attachment={controller.currentAttachment}
      />
      <MediaViewerNavigation
        hasNext={controller.hasNext}
        hasPrevious={controller.hasPrevious}
        onNext={controller.handleNext}
        onPrevious={controller.handlePrevious}
        visible={attachmentCount > 1}
      />
    </>
  );
}
