import React from "react";
import { SharedMediaFilesSection } from "./SharedMediaFilesSection";
import { SharedMediaLinksSection } from "./SharedMediaLinksSection";
import { SharedMediaMediaSection } from "./SharedMediaMediaSection";
import { SharedMediaOverviewCard } from "./SharedMediaOverviewCard";
import type { SharedMediaScreenController } from "./useSharedMediaController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";
import type { ChatSummary, MessageAttachment } from "../../types";

type SharedMediaScreenContentProps = {
  chat: ChatSummary;
  controller: SharedMediaScreenController;
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
};

export function SharedMediaScreenContent({
  chat,
  controller,
  onClose,
  onOpenMediaViewer,
  onOpenMessage
}: SharedMediaScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        rightSlot={
          <AppButton
            disabled={controller.loading || controller.refreshing}
            onPress={() => void controller.handleRefresh()}
            size="sm"
          >
            {controller.refreshing ? "Refreshing..." : "Refresh"}
          </AppButton>
        }
        subtitle={chat.title}
        title="Shared media"
      />
      <ScreenFeedback error={controller.error} loading={controller.loading} />

      <ScreenScrollView gap="md" paddingBottom="xl">
        <SharedMediaOverviewCard buckets={controller.buckets} chat={chat} />
        <SharedMediaMediaSection
          chatTitle={chat.title}
          entries={controller.buckets?.media ?? []}
          mediaAttachments={controller.mediaAttachments}
          onOpenMessage={onOpenMessage}
          onOpenMediaViewer={onOpenMediaViewer}
        />
        <SharedMediaFilesSection
          entries={controller.buckets?.files ?? []}
          onOpenAttachment={(attachment) => void controller.handleOpenFileAttachment(attachment)}
          onOpenMessage={onOpenMessage}
          onToggleAudioAttachment={(attachment) =>
            void controller.handleToggleAudioAttachment(attachment)
          }
          loadingAudioAttachmentId={controller.loadingAudioAttachmentId}
          openingAttachmentId={controller.openingAttachmentId}
          playingAudioAttachmentId={controller.playingAudioAttachmentId}
        />
        <SharedMediaLinksSection
          entries={controller.buckets?.links ?? []}
          onOpenMessage={onOpenMessage}
          onOpenLink={(linkId, url) => void controller.handleOpenLink(linkId, url)}
          openingLinkId={controller.openingLinkId}
        />
      </ScreenScrollView>
    </>
  );
}
