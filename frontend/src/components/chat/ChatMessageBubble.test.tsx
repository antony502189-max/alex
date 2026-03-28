import React from "react";
import { Image } from "react-native";
import { fireEvent, render } from "@testing-library/react-native";
import { ChatMessageBubble } from "./ChatMessageBubble";
import type { ChatMessage } from "../../types";

function createMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    anonymousSender: false,
    attachments: [],
    caption: null,
    chatId: "chat-1",
    clientMessageId: null,
    commentCount: 0,
    contactCard: null,
    createdAt: "2026-03-28T10:00:00.000Z",
    deletedAt: null,
    deliveredAt: null,
    deliveryStatus: "DELIVERED",
    discussionChatId: null,
    discussionRootMessageId: null,
    displaySenderName: "Alex",
    displaySenderPhotoAccessExpiresAt: null,
    displaySenderPhotoUrl: null,
    editedAt: null,
    entities: [],
    expiresAt: null,
    forwardedFromChatId: null,
    forwardedFromMessageId: null,
    location: null,
    liveLocation: null,
    messageId: "message-1",
    messageType: "TEXT",
    poll: null,
    readAt: null,
    reactions: [],
    recipientId: null,
    replyToMessageId: null,
    senderId: "user-2",
    serviceMessage: null,
    silent: false,
    sticker: null,
    text: "",
    threadRootMessageId: null,
    topicId: null,
    viaBotUserId: null,
    ...overrides
  };
}

describe("ChatMessageBubble", () => {
  it("opens links embedded in service message text through the shared handler", () => {
    const onOpenLink = jest.fn();

    const screen = render(
      <ChatMessageBubble
        attachmentTitle={() => "Attachment"}
        canClosePoll={() => false}
        canOpenDiscussionThread={false}
        closingPollMessageId={null}
        currentUserId="user-1"
        describeMessage={() => "Service update"}
        displaySenderName="Alex"
        formatContactName={() => "Contact"}
        formatDuration={() => "0:03"}
        formatFileSize={() => "2 KB"}
        getAttachmentTransferMeta={() => null}
        getImagePreviewHeight={() => 180}
        isAudioAttachment={() => false}
        isHighlighted={false}
        isImageAttachment={() => false}
        isPinned={false}
        isQueuedUploadAttachment={() => false}
        isSelected={false}
        isVideoAttachment={() => false}
        message={createMessage({
          messageType: "SERVICE",
          serviceMessage: {
            serviceType: "Invite",
            text: "Open t.me/team"
          }
        })}
        onClosePoll={jest.fn()}
        onEnsureMessageVisible={jest.fn()}
        onLongPress={jest.fn()}
        onPress={jest.fn()}
        onOpenAttachment={jest.fn()}
        onOpenDiscussionThread={jest.fn()}
        onOpenLink={onOpenLink}
        onToggleReaction={jest.fn()}
        onToggleVoicePlayback={jest.fn()}
        onVotePoll={jest.fn()}
        openingAttachmentId={null}
        playingVoiceAttachmentId={null}
        reactionsEnabled={true}
        renderMessageMeta={() => "10:00"}
        renderWaveform={() => null}
        replyPreview={null}
        replyPreviewSenderName={null}
        selectionActive={false}
        showOpenInTimeline={false}
        showSenderLabel={false}
        showUnreadDivider={false}
        votingMessageId={null}
      />
    );

    fireEvent.press(screen.getByText("t.me/team"));

    expect(onOpenLink).toHaveBeenCalledWith("t.me/team");
  });

  it("renders attachment previews from a local image URI when no preview URL exists", () => {
    const onOpenAttachment = jest.fn();

    const screen = render(
      <ChatMessageBubble
        attachmentTitle={() => "Photo"}
        canClosePoll={() => false}
        canOpenDiscussionThread={false}
        closingPollMessageId={null}
        currentUserId="user-1"
        describeMessage={() => "Photo"}
        displaySenderName="Alex"
        formatContactName={() => "Contact"}
        formatDuration={() => "0:03"}
        formatFileSize={() => "2 KB"}
        getAttachmentTransferMeta={() => null}
        getImagePreviewHeight={() => 180}
        isAudioAttachment={() => false}
        isHighlighted={false}
        isImageAttachment={(attachment) => attachment.kind === "IMAGE"}
        isPinned={false}
        isQueuedUploadAttachment={() => false}
        isSelected={false}
        isVideoAttachment={() => false}
        message={createMessage({
          attachments: [
            {
              accessExpiresAt: null,
              attachmentId: "image-1",
              contentType: "image/jpeg",
              downloadUrl: "",
              durationMs: null,
              fileSizeBytes: 2048,
              height: 600,
              kind: "IMAGE",
              localUri: "file:///tmp/photo.jpg",
              originalFileName: "photo.jpg",
              previewUrl: null,
              requiresAuthorization: false,
              streamingSupported: false,
              thumbnailUrl: null,
              uploadState: "UPLOADED",
              waveform: null,
              width: 800
            }
          ]
        })}
        onClosePoll={jest.fn()}
        onEnsureMessageVisible={jest.fn()}
        onLongPress={jest.fn()}
        onPress={jest.fn()}
        onOpenAttachment={onOpenAttachment}
        onOpenDiscussionThread={jest.fn()}
        onOpenLink={jest.fn()}
        onToggleReaction={jest.fn()}
        onToggleVoicePlayback={jest.fn()}
        onVotePoll={jest.fn()}
        openingAttachmentId={null}
        playingVoiceAttachmentId={null}
        reactionsEnabled={true}
        renderMessageMeta={() => "10:00"}
        renderWaveform={() => null}
        replyPreview={null}
        replyPreviewSenderName={null}
        selectionActive={false}
        showOpenInTimeline={false}
        showSenderLabel={false}
        showUnreadDivider={false}
        votingMessageId={null}
      />
    );

    expect(screen.UNSAFE_getByType(Image).props.source).toEqual({
      uri: "file:///tmp/photo.jpg"
    });

    fireEvent.press(screen.getByText("Photo - 2 KB"));

    expect(onOpenAttachment).toHaveBeenCalledWith(
      expect.objectContaining({ attachmentId: "image-1" }),
      [expect.objectContaining({ attachmentId: "image-1" })],
      {
        createdAt: "2026-03-28T10:00:00.000Z",
        messageId: "message-1"
      }
    );
  });

  it("routes nested link presses into selection toggling while selection mode is active", () => {
    const onOpenLink = jest.fn();
    const onPress = jest.fn();

    const screen = render(
      <ChatMessageBubble
        attachmentTitle={() => "Attachment"}
        canClosePoll={() => false}
        canOpenDiscussionThread={false}
        closingPollMessageId={null}
        currentUserId="user-1"
        describeMessage={() => "Service update"}
        displaySenderName="Alex"
        formatContactName={() => "Contact"}
        formatDuration={() => "0:03"}
        formatFileSize={() => "2 KB"}
        getAttachmentTransferMeta={() => null}
        getImagePreviewHeight={() => 180}
        isAudioAttachment={() => false}
        isHighlighted={false}
        isImageAttachment={() => false}
        isPinned={false}
        isQueuedUploadAttachment={() => false}
        isSelected={false}
        isVideoAttachment={() => false}
        message={createMessage({
          messageType: "SERVICE",
          serviceMessage: {
            serviceType: "Invite",
            text: "Open t.me/team"
          }
        })}
        onClosePoll={jest.fn()}
        onEnsureMessageVisible={jest.fn()}
        onLongPress={jest.fn()}
        onPress={onPress}
        onOpenAttachment={jest.fn()}
        onOpenDiscussionThread={jest.fn()}
        onOpenLink={onOpenLink}
        onToggleReaction={jest.fn()}
        onToggleVoicePlayback={jest.fn()}
        onVotePoll={jest.fn()}
        openingAttachmentId={null}
        playingVoiceAttachmentId={null}
        reactionsEnabled={true}
        renderMessageMeta={() => "10:00"}
        renderWaveform={() => null}
        replyPreview={null}
        replyPreviewSenderName={null}
        selectionActive={true}
        showOpenInTimeline={false}
        showSenderLabel={false}
        showUnreadDivider={false}
        votingMessageId={null}
      />
    );

    fireEvent.press(screen.getByText("t.me/team"));

    expect(onPress).toHaveBeenCalledTimes(1);
    expect(onOpenLink).not.toHaveBeenCalled();
  });
});
