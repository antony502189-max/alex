import React from "react";
import { Image, Pressable, StyleSheet, Text, View } from "react-native";
import { FormattedMessageText } from "../FormattedMessageText";
import { AppButton } from "../ui/AppButton";
import { resolveAttachmentPreviewUri } from "../../services/attachmentPreviews";
import type { ChatMessage, MessageAttachment, MessageContactCard } from "../../types";

type MessageJumpTarget = {
  messageId: string;
  createdAt: string;
};

type ChatMessageBubbleProps = {
  attachmentTitle: (attachment: MessageAttachment) => string;
  canClosePoll: (message: ChatMessage) => boolean;
  canOpenDiscussionThread: boolean;
  closingPollMessageId: string | null;
  currentUserId: string;
  describeMessage: (message: ChatMessage) => string;
  displaySenderName: string | null;
  formatContactName: (contactCard: MessageContactCard | null) => string;
  formatDuration: (durationMs: number | null | undefined) => string;
  formatFileSize: (fileSizeBytes: number) => string;
  getAttachmentTransferMeta: (attachment: MessageAttachment) => string | null;
  getImagePreviewHeight: (attachment: MessageAttachment) => number;
  isAudioAttachment: (attachment: MessageAttachment) => boolean;
  isHighlighted: boolean;
  isImageAttachment: (attachment: MessageAttachment) => boolean;
  isPinned: boolean;
  isQueuedUploadAttachment: (attachment: MessageAttachment) => boolean;
  isSelected: boolean;
  isVideoAttachment: (attachment: MessageAttachment) => boolean;
  message: ChatMessage;
  onClosePoll: (message: ChatMessage) => void | Promise<void>;
  onEnsureMessageVisible: (target: MessageJumpTarget) => void | Promise<void>;
  onLongPress: () => void;
  onPress: () => void;
  onOpenLink: (url: string) => void;
  onOpenAttachment: (
    attachment: MessageAttachment,
    mediaAlbum: MessageAttachment[],
    sourceMessage?: MessageJumpTarget
  ) => void | Promise<void>;
  onOpenDiscussionThread?: (message: ChatMessage) => void;
  onToggleReaction: (emoji: string, message: ChatMessage) => void | Promise<void>;
  onToggleVoicePlayback: (attachment: MessageAttachment) => void | Promise<void>;
  onVotePoll: (message: ChatMessage, optionId: string) => void | Promise<void>;
  openingAttachmentId: string | null;
  playingVoiceAttachmentId: string | null;
  reactionsEnabled: boolean;
  renderMessageMeta: (message: ChatMessage) => string;
  renderWaveform: (attachment: MessageAttachment, color: string) => React.ReactNode;
  replyPreview: ChatMessage | null;
  replyPreviewSenderName: string | null;
  selectionActive: boolean;
  showOpenInTimeline: boolean;
  showSenderLabel: boolean;
  showUnreadDivider: boolean;
  votingMessageId: string | null;
};

export function ChatMessageBubble({
  attachmentTitle,
  canClosePoll,
  canOpenDiscussionThread,
  closingPollMessageId,
  currentUserId,
  describeMessage,
  displaySenderName,
  formatContactName,
  formatDuration,
  formatFileSize,
  getAttachmentTransferMeta,
  getImagePreviewHeight,
  isAudioAttachment,
  isHighlighted,
  isImageAttachment,
  isPinned,
  isQueuedUploadAttachment,
  isSelected,
  isVideoAttachment,
  message,
  onClosePoll,
  onEnsureMessageVisible,
  onLongPress,
  onPress,
  onOpenLink,
  onOpenAttachment,
  onOpenDiscussionThread,
  onToggleReaction,
  onToggleVoicePlayback,
  onVotePoll,
  openingAttachmentId,
  playingVoiceAttachmentId,
  reactionsEnabled,
  renderMessageMeta,
  renderWaveform,
  replyPreview,
  replyPreviewSenderName,
  selectionActive,
  showOpenInTimeline,
  showSenderLabel,
  showUnreadDivider,
  votingMessageId
}: ChatMessageBubbleProps) {
  const isMine = message.senderId === currentUserId;
  const mediaAlbum = message.attachments.filter(
    (attachment) => isImageAttachment(attachment) || isVideoAttachment(attachment)
  );
  const handleSelectionAwareLinkOpen = (url: string) => {
    if (selectionActive) {
      onPress();
      return;
    }
    onOpenLink(url);
  };

  return (
    <Pressable
      onLongPress={onLongPress}
      onPress={selectionActive ? onPress : undefined}
      style={[
        styles.messageBubble,
        isMine ? styles.ownBubble : styles.peerBubble,
        selectionActive && styles.selectionModeBubble,
        isSelected && styles.selectedBubble,
        isPinned && styles.pinnedBubble,
        isHighlighted && styles.highlightedBubble
      ]}
    >
      {showUnreadDivider ? (
        <View style={styles.unreadDivider}>
          <Text style={styles.unreadDividerText}>Unread from here</Text>
        </View>
      ) : null}
      {showSenderLabel ? (
        <Text style={styles.authorLabel}>
          {displaySenderName}
          {message.anonymousSender ? " - anonymous admin" : ""}
        </Text>
      ) : null}
      {message.forwardedFromMessageId ? (
        <Text style={[styles.badgeText, isMine && styles.ownMessageText]}>Forwarded</Text>
      ) : null}
      {message.viaBotUserId ? (
        <Text style={[styles.badgeText, isMine && styles.ownMessageText]}>Via bot</Text>
      ) : null}
      {selectionActive && isSelected ? (
        <View style={[styles.selectionChip, styles.selectionChipActive]}>
          <Text style={[styles.selectionChipText, styles.selectionChipTextActive]}>
            Selected
          </Text>
        </View>
      ) : null}
      {replyPreview ? (
        <Pressable
          onPress={() => {
            if (selectionActive) {
              onPress();
              return;
            }
            void onEnsureMessageVisible({
              createdAt: replyPreview.createdAt,
              messageId: replyPreview.messageId
            });
          }}
          style={styles.replyPreview}
        >
          {replyPreviewSenderName ? (
            <Text style={styles.replyPreviewAuthor}>
              {replyPreviewSenderName}
              {replyPreview.anonymousSender ? " - anonymous admin" : ""}
            </Text>
          ) : null}
          {replyPreview.deletedAt ? (
            <Text style={styles.replyPreviewText}>Message deleted</Text>
          ) : replyPreview.text ? (
            <FormattedMessageText
              entities={replyPreview.entities}
              numberOfLines={2}
              onOpenLink={handleSelectionAwareLinkOpen}
              style={styles.replyPreviewText}
              text={replyPreview.text}
              variant="muted"
            />
          ) : replyPreview.serviceMessage?.text ? (
            <FormattedMessageText
              numberOfLines={2}
              onOpenLink={handleSelectionAwareLinkOpen}
              style={styles.replyPreviewText}
              text={replyPreview.serviceMessage.text}
              variant="muted"
            />
          ) : (
            <Text style={styles.replyPreviewText}>{describeMessage(replyPreview)}</Text>
          )}
        </Pressable>
      ) : null}
      {message.deletedAt ? (
        <Text style={[styles.messageText, isMine && styles.ownMessageText]}>Message deleted</Text>
      ) : message.text && !message.poll ? (
        <FormattedMessageText
          entities={message.entities}
          onOpenLink={handleSelectionAwareLinkOpen}
          style={[styles.messageText, isMine && styles.ownMessageText]}
          text={message.text}
          variant={isMine ? "inverse" : "default"}
        />
      ) : null}
      {!message.deletedAt && message.serviceMessage ? (
        <View style={styles.serviceCard}>
          <Text style={styles.serviceTitle}>
            {message.serviceMessage.serviceType ?? "Service update"}
          </Text>
          {message.serviceMessage.text ? (
            <FormattedMessageText
              onOpenLink={handleSelectionAwareLinkOpen}
              style={styles.serviceText}
              text={message.serviceMessage.text}
            />
          ) : null}
        </View>
      ) : null}
      {!message.deletedAt && message.poll ? (
        <View style={styles.pollCard}>
          <Text style={styles.pollQuestion}>{message.poll.question}</Text>
          {message.poll.options.map((option) => {
            const percent =
              message.poll && message.poll.totalVoters > 0
                ? Math.round((option.voteCount * 100) / message.poll.totalVoters)
                : 0;
            const disabled =
              message.poll?.closed ||
              message.deliveryStatus === "QUEUED" ||
              votingMessageId === message.messageId;

            return (
              <Pressable
                disabled={disabled && !selectionActive}
                key={option.optionId}
                onPress={() => {
                  if (selectionActive) {
                    onPress();
                    return;
                  }
                  void onVotePoll(message, option.optionId);
                }}
                style={[
                  styles.pollOption,
                  option.selectedByMe && styles.pollOptionSelected,
                  disabled && styles.disabled
                ]}
              >
                <Text style={styles.pollOptionText}>{option.text}</Text>
                <Text style={styles.pollOptionMeta}>
                  {option.voteCount} votes - {percent}%{option.selectedByMe ? " - you" : ""}
                </Text>
              </Pressable>
            );
          })}
          <Text style={styles.pollFooter}>
            {message.poll.totalVoters} voters
            {message.poll.multipleChoice ? " - multiple choice" : ""}
            {message.poll.closed ? " - closed" : ""}
          </Text>
          {canClosePoll(message) ? (
            <AppButton
              disabled={closingPollMessageId === message.messageId}
              onPress={() => {
                if (selectionActive) {
                  onPress();
                  return;
                }
                void onClosePoll(message);
              }}
              size="sm"
              variant="danger"
            >
              {closingPollMessageId === message.messageId ? "Closing..." : "Close poll"}
            </AppButton>
          ) : null}
        </View>
      ) : null}
      {!message.deletedAt && message.location ? (
        <View style={styles.structuredCard}>
          <Text style={styles.structuredTitle}>{message.location.title ?? "Location"}</Text>
          {message.location.address ? (
            <Text style={styles.structuredBody}>{message.location.address}</Text>
          ) : null}
          <Text style={styles.structuredMeta}>
            {message.location.latitude.toFixed(5)}, {message.location.longitude.toFixed(5)}
          </Text>
        </View>
      ) : null}
      {!message.deletedAt && message.liveLocation ? (
        <View style={styles.structuredCard}>
          <Text style={styles.structuredTitle}>{message.liveLocation.title ?? "Live location"}</Text>
          {message.liveLocation.address ? (
            <Text style={styles.structuredBody}>{message.liveLocation.address}</Text>
          ) : null}
          <Text style={styles.structuredMeta}>
            {message.liveLocation.latitude.toFixed(5)}, {message.liveLocation.longitude.toFixed(5)}
          </Text>
          <Text style={styles.structuredMeta}>
            {message.liveLocation.stoppedAt
              ? `Stopped ${new Date(message.liveLocation.stoppedAt).toLocaleString()}`
              : message.liveLocation.livePeriodSeconds
                ? `Live for ${Math.round(message.liveLocation.livePeriodSeconds / 60)} minutes`
                : "Live location"}
          </Text>
        </View>
      ) : null}
      {!message.deletedAt && message.contactCard ? (
        <View style={styles.structuredCard}>
          <Text style={styles.structuredTitle}>{formatContactName(message.contactCard)}</Text>
          {message.contactCard.phoneNumber ? (
            <Text style={styles.structuredBody}>{message.contactCard.phoneNumber}</Text>
          ) : null}
          {message.contactCard.userId ? (
            <Text style={styles.structuredMeta}>User id: {message.contactCard.userId}</Text>
          ) : null}
        </View>
      ) : null}
      {!message.deletedAt && message.sticker ? (
        <View
          style={[
            styles.stickerBubble,
            {
              backgroundColor: message.sticker.backgroundFrom,
              borderColor: message.sticker.backgroundTo
            }
          ]}
        >
          <Text style={styles.stickerEmoji}>{message.sticker.emoji}</Text>
          <Text style={[styles.stickerLabel, { color: message.sticker.textColor }]}>
            {message.sticker.label}
          </Text>
          <Text style={[styles.stickerPackLabel, { color: message.sticker.textColor }]}>
            {message.sticker.packTitle}
          </Text>
        </View>
      ) : null}
      {!message.deletedAt && message.attachments.length > 0 ? (
        <View style={styles.attachmentsColumn}>
          {message.attachments.map((attachment) => {
            const attachmentPreviewUri = resolveAttachmentPreviewUri(attachment);

            return (
            isAudioAttachment(attachment) ? (
              <Pressable
                key={attachment.attachmentId}
                onPress={() => {
                  if (selectionActive) {
                    onPress();
                    return;
                  }
                  void onToggleVoicePlayback(attachment);
                }}
                style={styles.voiceCard}
              >
                <Text style={styles.voiceTitle}>{attachmentTitle(attachment)}</Text>
                {renderWaveform(attachment, "#166534")}
                <Text style={styles.voiceMeta}>
                  {formatDuration(attachment.durationMs)} - {formatFileSize(attachment.fileSizeBytes)}
                </Text>
                <Text style={styles.voiceMeta}>
                  {getAttachmentTransferMeta(attachment) ??
                    (isQueuedUploadAttachment(attachment)
                      ? "Queued upload"
                      : playingVoiceAttachmentId === attachment.attachmentId
                        ? "Stop"
                        : "Play")}
                </Text>
              </Pressable>
            ) : (isImageAttachment(attachment) || isVideoAttachment(attachment)) &&
              attachmentPreviewUri ? (
              <Pressable
                key={attachment.attachmentId}
                onPress={() => {
                  if (selectionActive) {
                    onPress();
                    return;
                  }
                  void onOpenAttachment(attachment, mediaAlbum, {
                    createdAt: message.createdAt,
                    messageId: message.messageId
                  });
                }}
                style={styles.imageCard}
              >
                <Image
                  source={{ uri: attachmentPreviewUri }}
                  style={[styles.imageAttachment, { height: getImagePreviewHeight(attachment) }]}
                />
                {isVideoAttachment(attachment) ? (
                  <View style={styles.previewBadge}>
                    <Text style={styles.previewBadgeText}>{attachmentTitle(attachment)}</Text>
                  </View>
                ) : null}
                <Text style={styles.attachmentMeta}>
                  {openingAttachmentId === attachment.attachmentId
                    ? "Opening..."
                    : `${attachmentTitle(attachment)} - ${formatFileSize(attachment.fileSizeBytes)}`}
                </Text>
                {attachment.width && attachment.height ? (
                  <Text style={styles.attachmentMeta}>
                    {attachment.width}x{attachment.height}
                  </Text>
                ) : null}
                {getAttachmentTransferMeta(attachment) ? (
                  <Text style={styles.attachmentMeta}>
                    {getAttachmentTransferMeta(attachment)}
                  </Text>
                ) : null}
              </Pressable>
            ) : (
              <Pressable
                key={attachment.attachmentId}
                onPress={() => {
                  if (selectionActive) {
                    onPress();
                    return;
                  }
                  void onOpenAttachment(attachment, mediaAlbum, {
                    createdAt: message.createdAt,
                    messageId: message.messageId
                  });
                }}
                style={styles.attachmentCard}
              >
                <Text style={styles.attachmentName}>{attachmentTitle(attachment)}</Text>
                <Text style={styles.attachmentMeta}>
                  {attachment.contentType} - {formatFileSize(attachment.fileSizeBytes)}
                </Text>
                <Text style={styles.attachmentMeta}>
                  {getAttachmentTransferMeta(attachment) ??
                    (isQueuedUploadAttachment(attachment)
                      ? "Queued upload"
                      : openingAttachmentId === attachment.attachmentId
                        ? "Opening..."
                        : attachment.streamingSupported || isVideoAttachment(attachment)
                          ? "Open / stream"
                          : "Open")}
                </Text>
              </Pressable>
            )
          );
          })}
        </View>
      ) : null}
      {!message.deletedAt && message.reactions.length > 0 ? (
        <View style={styles.rowWrap}>
          {message.reactions.map((reaction) => (
            <Pressable
              disabled={!reactionsEnabled && !selectionActive}
              key={`${message.messageId}-${reaction.emoji}`}
              onPress={() => {
                if (selectionActive) {
                  onPress();
                  return;
                }
                void onToggleReaction(reaction.emoji, message);
              }}
              style={[styles.reactionChip, !reactionsEnabled && styles.disabled]}
            >
              <Text style={styles.reactionChipText}>
                {reaction.emoji} {reaction.count}
              </Text>
            </Pressable>
          ))}
        </View>
      ) : null}
      {canOpenDiscussionThread &&
      message.discussionChatId &&
      message.discussionRootMessageId &&
      onOpenDiscussionThread ? (
        <View style={styles.rowWrap}>
          <AppButton
            onPress={() => {
              if (selectionActive) {
                onPress();
                return;
              }
              onOpenDiscussionThread(message);
            }}
            size="sm"
            variant="secondary"
          >
            {message.commentCount > 0
              ? `${message.commentCount} comment${message.commentCount === 1 ? "" : "s"}`
              : "Discuss"}
          </AppButton>
        </View>
      ) : null}
      {showOpenInTimeline ? (
        <View style={styles.rowWrap}>
          <AppButton
            onPress={() => {
              if (selectionActive) {
                onPress();
                return;
              }
              void onEnsureMessageVisible({
                createdAt: message.createdAt,
                messageId: message.messageId
              });
            }}
            size="sm"
            variant="secondary"
          >
            Open in timeline
          </AppButton>
        </View>
      ) : null}
      <Text style={[styles.messageTime, isMine && styles.ownMessageTime]}>
        {renderMessageMeta(message)}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  messageBubble: {
    borderRadius: 18,
    maxWidth: "80%",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  selectionModeBubble: {
    opacity: 0.92
  },
  ownBubble: {
    alignSelf: "flex-end",
    backgroundColor: "#0f172a"
  },
  peerBubble: {
    alignSelf: "flex-start",
    backgroundColor: "#ffffff"
  },
  selectedBubble: {
    borderColor: "#f59e0b",
    borderWidth: 2
  },
  pinnedBubble: {
    borderColor: "#0284c7",
    borderWidth: 2
  },
  highlightedBubble: {
    borderColor: "#22c55e",
    borderWidth: 2
  },
  unreadDivider: {
    alignSelf: "stretch",
    backgroundColor: "#dbeafe",
    borderRadius: 999,
    marginBottom: 8,
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  unreadDividerText: {
    color: "#1d4ed8",
    fontSize: 12,
    fontWeight: "700",
    textAlign: "center"
  },
  authorLabel: {
    color: "#0369a1",
    fontSize: 12,
    fontWeight: "700",
    marginBottom: 6
  },
  badgeText: {
    color: "#475569",
    fontSize: 12,
    fontWeight: "600",
    marginBottom: 6
  },
  selectionChip: {
    alignSelf: "flex-start",
    backgroundColor: "#e2e8f0",
    borderRadius: 999,
    marginBottom: 8,
    paddingHorizontal: 10,
    paddingVertical: 4
  },
  selectionChipActive: {
    backgroundColor: "#f59e0b"
  },
  selectionChipText: {
    color: "#475569",
    fontSize: 11,
    fontWeight: "700"
  },
  selectionChipTextActive: {
    color: "#ffffff"
  },
  ownMessageText: {
    color: "#ffffff"
  },
  replyPreview: {
    borderLeftColor: "#38bdf8",
    borderLeftWidth: 3,
    marginBottom: 8,
    paddingLeft: 8
  },
  replyPreviewAuthor: {
    color: "#0f766e",
    fontSize: 12,
    fontWeight: "700",
    marginBottom: 4
  },
  replyPreviewText: {
    color: "#475569",
    fontSize: 12
  },
  messageText: {
    color: "#0f172a"
  },
  serviceCard: {
    backgroundColor: "#e2e8f0",
    borderRadius: 16,
    marginTop: 8,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  serviceTitle: {
    color: "#334155",
    fontWeight: "700"
  },
  serviceText: {
    color: "#475569",
    marginTop: 4
  },
  pollCard: {
    gap: 8,
    marginTop: 8
  },
  pollQuestion: {
    color: "#0f172a",
    fontWeight: "700"
  },
  pollOption: {
    backgroundColor: "#eff6ff",
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  pollOptionSelected: {
    borderColor: "#2563eb",
    borderWidth: 2
  },
  pollOptionText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  pollOptionMeta: {
    color: "#475569",
    fontSize: 12,
    marginTop: 4
  },
  pollFooter: {
    color: "#64748b",
    fontSize: 12,
    fontWeight: "600"
  },
  structuredCard: {
    backgroundColor: "#fef3c7",
    borderRadius: 16,
    marginTop: 8,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  structuredTitle: {
    color: "#92400e",
    fontWeight: "700"
  },
  structuredBody: {
    color: "#92400e",
    marginTop: 4
  },
  structuredMeta: {
    color: "#b45309",
    fontSize: 12,
    marginTop: 4
  },
  stickerBubble: {
    alignItems: "center",
    borderRadius: 24,
    borderWidth: 2,
    marginTop: 8,
    paddingHorizontal: 18,
    paddingVertical: 14
  },
  stickerEmoji: {
    fontSize: 42
  },
  stickerLabel: {
    fontSize: 16,
    fontWeight: "700",
    marginTop: 8
  },
  stickerPackLabel: {
    fontSize: 12,
    fontWeight: "600",
    marginTop: 4,
    opacity: 0.9
  },
  attachmentsColumn: {
    gap: 8,
    marginTop: 10
  },
  imageCard: {
    gap: 8,
    marginTop: 8
  },
  imageAttachment: {
    backgroundColor: "#dbeafe",
    borderRadius: 18,
    height: 220,
    width: 220
  },
  previewBadge: {
    alignSelf: "flex-start",
    backgroundColor: "rgba(15, 23, 42, 0.72)",
    borderRadius: 999,
    marginTop: -40,
    marginLeft: 12,
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  previewBadgeText: {
    color: "#f8fafc",
    fontSize: 12,
    fontWeight: "700"
  },
  attachmentCard: {
    backgroundColor: "#eff6ff",
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  attachmentName: {
    color: "#1e3a8a",
    fontWeight: "700"
  },
  attachmentMeta: {
    color: "#475569",
    fontSize: 12,
    marginTop: 2
  },
  voiceCard: {
    backgroundColor: "#dcfce7",
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  voiceTitle: {
    color: "#166534",
    fontWeight: "700"
  },
  voiceMeta: {
    color: "#166534",
    fontSize: 12,
    marginTop: 4
  },
  rowWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8
  },
  reactionChip: {
    backgroundColor: "#dbeafe",
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  reactionChipText: {
    color: "#1e3a8a",
    fontWeight: "600"
  },
  messageTime: {
    color: "#94a3b8",
    fontSize: 11,
    marginTop: 6
  },
  ownMessageTime: {
    color: "#cbd5e1"
  },
  disabled: {
    opacity: 0.6
  }
});
