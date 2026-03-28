import React from "react";
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View
} from "react-native";
import { FormattedMessageText } from "../FormattedMessageText";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import {
  formatPinnedHistoryEntryMeta,
  formatPinnedHistoryEntryPreview,
  formatScheduledMessageMeta
} from "./chatScreenPresentation";
import type {
  ChatMessage,
  PinnedMessageHistoryEntry,
  ScheduledMessage
} from "../../types";

type MessageJumpTarget = {
  createdAt: string;
  messageId: string;
};

type ChatTimelinePanelsProps = {
  activePinnedHistoryEntry: PinnedMessageHistoryEntry | null;
  activeThreadRootMessageId: string | null;
  cancelingScheduledMessageId: string | null;
  canPinMessages: boolean;
  channelPostingDisabled: boolean;
  currentUserId: string;
  describeMessage: (message: ChatMessage | ScheduledMessage) => string;
  editingMessageId: string | null;
  firstUnreadMessage: ChatMessage | null;
  loadingPinnedHistory: boolean;
  onCancelComposerModes: () => void;
  onCancelScheduledMessage: (scheduledMessageId: string) => void | Promise<void>;
  onCloseSelectedMessage: () => void;
  onDeleteSelected: () => void;
  onEditSelected: () => void;
  onEnsureMessageVisible: (target: MessageJumpTarget) => void | Promise<void>;
  onForwardSelected: () => void;
  onOpenLink: (url: string) => void;
  onPinSelected: () => void;
  onRefreshLiveLocation: () => void;
  onReportSelected: () => void;
  onReplySelected: () => void;
  onShareSelected: () => void;
  onStopLiveLocation: () => void;
  onToggleReaction: (emoji: string, message: ChatMessage) => void | Promise<void>;
  pinnedHistory: PinnedMessageHistoryEntry[];
  pinnedPreviewMessage: ChatMessage | null;
  pinnedPreviewText: string;
  reactionChoices: string[];
  reactionsEnabled: boolean;
  replyPanelTitle: string;
  replyTarget: ChatMessage | null;
  replyToMessageId: string | null;
  scheduledMessages: ScheduledMessage[];
  scheduledPanelTitle: string;
  selectedMessage: ChatMessage | null;
  selectedMessages: ChatMessage[];
  showPinnedHistory: boolean;
  showPinnedPanel: boolean;
  showReplyPanel: boolean;
  showScheduledPanel: boolean;
  showUnreadPanel: boolean;
  slowModeLabel: string | null;
  threadRootMessage: ChatMessage | null;
  topicClosed: boolean;
  unreadCount: number;
};

export function ChatTimelinePanels({
  activePinnedHistoryEntry,
  activeThreadRootMessageId,
  cancelingScheduledMessageId,
  canPinMessages,
  channelPostingDisabled,
  currentUserId,
  describeMessage,
  editingMessageId,
  firstUnreadMessage,
  loadingPinnedHistory,
  onCancelComposerModes,
  onCancelScheduledMessage,
  onCloseSelectedMessage,
  onDeleteSelected,
  onEditSelected,
  onEnsureMessageVisible,
  onForwardSelected,
  onOpenLink,
  onPinSelected,
  onRefreshLiveLocation,
  onReportSelected,
  onReplySelected,
  onShareSelected,
  onStopLiveLocation,
  onToggleReaction,
  pinnedHistory,
  pinnedPreviewMessage,
  pinnedPreviewText,
  reactionChoices,
  reactionsEnabled,
  replyPanelTitle,
  replyTarget,
  replyToMessageId,
  scheduledMessages,
  scheduledPanelTitle,
  selectedMessage,
  selectedMessages,
  showPinnedHistory,
  showPinnedPanel,
  showReplyPanel,
  showScheduledPanel,
  showUnreadPanel,
  slowModeLabel,
  threadRootMessage,
  topicClosed,
  unreadCount
}: ChatTimelinePanelsProps) {
  const selectionCount = selectedMessages.length;
  const singleSelectedMessage = selectionCount === 1 ? selectedMessage : null;
  const hasDeletableSelection = selectedMessages.some(
    (message) => message.senderId === currentUserId && !message.deletedAt
  );
  const hasForwardableSelection = selectedMessages.some(
    (message) => !message.deletedAt && message.deliveryStatus !== "QUEUED"
  );
  const canEditSelectedMessage = Boolean(
    singleSelectedMessage &&
      singleSelectedMessage.senderId === currentUserId &&
      !singleSelectedMessage.deletedAt
  );
  const canPinSelectedMessage = Boolean(
    singleSelectedMessage &&
      !singleSelectedMessage.deletedAt &&
      singleSelectedMessage.deliveryStatus !== "QUEUED" &&
      canPinMessages
  );
  const canReportSelectedMessage = Boolean(
    singleSelectedMessage &&
      singleSelectedMessage.senderId !== currentUserId &&
      !singleSelectedMessage.deletedAt &&
      singleSelectedMessage.deliveryStatus !== "QUEUED"
  );
  const canManageSelectedLiveLocation = Boolean(
    singleSelectedMessage &&
      singleSelectedMessage.senderId === currentUserId &&
      singleSelectedMessage.deliveryStatus !== "QUEUED" &&
      !singleSelectedMessage.deletedAt &&
      singleSelectedMessage.liveLocation &&
      !singleSelectedMessage.liveLocation.stoppedAt &&
      singleSelectedMessage.liveLocation.active !== false
  );

  return (
    <>
      {showPinnedPanel ? (
        <AppPanel style={styles.infoBar} title="Pinned" titleStyle={styles.infoTitle} tone="info">
          {pinnedPreviewMessage?.deletedAt ? (
            <Text style={styles.infoText}>Message deleted</Text>
          ) : pinnedPreviewMessage?.text ? (
            <FormattedMessageText
              entities={pinnedPreviewMessage.entities}
              onOpenLink={onOpenLink}
              style={styles.infoText}
              text={pinnedPreviewMessage.text}
              variant="muted"
            />
          ) : pinnedPreviewMessage?.serviceMessage?.text ? (
            <FormattedMessageText
              onOpenLink={onOpenLink}
              style={styles.infoText}
              text={pinnedPreviewMessage.serviceMessage.text}
              variant="muted"
            />
          ) : (
            <Text style={styles.infoText}>{pinnedPreviewText}</Text>
          )}
          {activePinnedHistoryEntry ? (
            <Text style={styles.infoMetaText}>
              {activePinnedHistoryEntry.pinnedByDisplayName} pinned this on{" "}
              {new Date(activePinnedHistoryEntry.pinnedAt).toLocaleString()}
            </Text>
          ) : null}
          {pinnedPreviewMessage ? (
            <View style={styles.rowWrap}>
              <AppButton
                onPress={() =>
                  void onEnsureMessageVisible({
                    createdAt: pinnedPreviewMessage.createdAt,
                    messageId: pinnedPreviewMessage.messageId
                  })
                }
                size="sm"
                variant="secondary"
              >
                Open pinned message
              </AppButton>
            </View>
          ) : null}
        </AppPanel>
      ) : null}

      {showPinnedHistory ? (
        <AppPanel
          style={styles.selectionBar}
          title="Pinned history"
          titleStyle={styles.selectionTitle}
          tone="warning"
        >
          <Text style={styles.selectionBody}>
            Recent pin actions for this chat. The active pin is marked separately.
          </Text>
          {loadingPinnedHistory ? (
            <ActivityIndicator color="#92400e" style={styles.loader} />
          ) : pinnedHistory.length === 0 ? (
            <Text style={styles.selectionBody}>No pinned messages yet.</Text>
          ) : (
            <View style={styles.scheduledList}>
              {pinnedHistory.map((entry) => (
                <Pressable
                  key={entry.pinEventId}
                  onPress={() => {
                    if (!entry.message) {
                      return;
                    }
                    void onEnsureMessageVisible({
                      createdAt: entry.message.createdAt,
                      messageId: entry.message.messageId
                    });
                  }}
                  style={[
                    styles.scheduledCard,
                    entry.active && styles.activePinnedHistoryCard
                  ]}
                >
                  <View style={styles.pinnedHistoryHeader}>
                    <Text style={styles.scheduledText}>
                      {entry.active ? "Current pin" : "Pinned"}
                    </Text>
                    <Text style={styles.scheduledMeta}>
                      {new Date(entry.pinnedAt).toLocaleString()}
                    </Text>
                  </View>
                  {entry.message?.deletedAt ? (
                    <Text style={styles.selectionBody}>Message deleted</Text>
                  ) : entry.message?.text ? (
                    <FormattedMessageText
                      entities={entry.message.entities}
                      onOpenLink={onOpenLink}
                      style={styles.selectionBody}
                      text={entry.message.text}
                      variant="muted"
                    />
                  ) : entry.message?.serviceMessage?.text ? (
                    <FormattedMessageText
                      onOpenLink={onOpenLink}
                      style={styles.selectionBody}
                      text={entry.message.serviceMessage.text}
                      variant="muted"
                    />
                  ) : (
                    <Text style={styles.selectionBody}>
                      {formatPinnedHistoryEntryPreview(entry, describeMessage)}
                    </Text>
                  )}
                  <Text style={styles.scheduledMeta}>
                    {formatPinnedHistoryEntryMeta(entry)}
                  </Text>
                </Pressable>
              ))}
            </View>
          )}
        </AppPanel>
      ) : null}

      {topicClosed ? (
        <AppPanel style={styles.infoBar} title="Topic locked" titleStyle={styles.infoTitle} tone="info">
          <Text style={styles.infoText}>
            This topic is closed. Reopen it from Topics to send new messages.
          </Text>
        </AppPanel>
      ) : null}

      {showScheduledPanel ? (
        <AppPanel
          style={styles.selectionBar}
          title={scheduledPanelTitle}
          titleStyle={styles.selectionTitle}
          tone="warning"
        >
          <Text style={styles.selectionBody}>
            Messages below will be delivered automatically at the scheduled time. Direct chats can also hold messages until the recipient comes online.
          </Text>
          <View style={styles.scheduledList}>
            {scheduledMessages.length === 0 ? (
              <Text style={styles.selectionBody}>No pending scheduled messages.</Text>
            ) : (
              scheduledMessages.map((message) => (
                <View key={message.scheduledMessageId} style={styles.scheduledCard}>
                  {message.text ? (
                    <FormattedMessageText
                      entities={message.entities}
                      onOpenLink={onOpenLink}
                      style={styles.scheduledText}
                      text={message.text}
                    />
                  ) : message.serviceMessage?.text ? (
                    <FormattedMessageText
                      onOpenLink={onOpenLink}
                      style={styles.scheduledText}
                      text={message.serviceMessage.text}
                    />
                  ) : (
                    <Text style={styles.scheduledText}>{describeMessage(message)}</Text>
                  )}
                  <Text style={styles.scheduledMeta}>{formatScheduledMessageMeta(message)}</Text>
                  <AppButton
                    disabled={cancelingScheduledMessageId === message.scheduledMessageId}
                    onPress={() => void onCancelScheduledMessage(message.scheduledMessageId)}
                    size="sm"
                    variant="danger"
                  >
                    {cancelingScheduledMessageId === message.scheduledMessageId
                      ? "Canceling..."
                      : "Cancel"}
                  </AppButton>
                </View>
              ))
            )}
          </View>
        </AppPanel>
      ) : null}

      {selectionCount > 0 ? (
        <AppPanel
          style={styles.selectionBar}
          title={selectionCount === 1 ? "Selected message" : `${selectionCount} selected`}
          titleStyle={styles.selectionTitle}
          tone="warning"
        >
          <Text style={styles.selectionBody}>
            {selectionCount === 1
              ? "Use the actions below to reply, edit, pin, share, or manage the selected message."
              : "Batch actions apply to the selected messages in this chat. Single-message actions stay available only when one message is selected."}
          </Text>
          {selectionCount > 1 && hasDeletableSelection && !selectedMessages.every((message) => message.senderId === currentUserId) ? (
            <Text style={styles.selectionMeta}>
              Delete is available only for messages sent by the current account.
            </Text>
          ) : null}
          <View style={styles.rowWrap}>
            {singleSelectedMessage ? (
              <AppButton onPress={onReplySelected} size="sm" variant="secondary">
                Reply
              </AppButton>
            ) : null}
            {hasForwardableSelection ? (
              <AppButton onPress={onForwardSelected} size="sm" variant="secondary">
                Forward
              </AppButton>
            ) : null}
            <AppButton onPress={onShareSelected} size="sm" variant="secondary">
              Share
            </AppButton>
            {canPinSelectedMessage ? (
              <AppButton onPress={onPinSelected} size="sm" variant="secondary">
                Pin
              </AppButton>
            ) : null}
            {canEditSelectedMessage ? (
              <AppButton onPress={onEditSelected} size="sm" variant="secondary">
                Edit
              </AppButton>
            ) : null}
            {hasDeletableSelection ? (
              <AppButton onPress={onDeleteSelected} size="sm" variant="danger">
                Delete
              </AppButton>
            ) : null}
            {canManageSelectedLiveLocation ? (
              <AppButton onPress={onRefreshLiveLocation} size="sm" variant="secondary">
                Refresh live
              </AppButton>
            ) : null}
            {canManageSelectedLiveLocation ? (
              <AppButton onPress={onStopLiveLocation} size="sm" variant="danger">
                Stop live
              </AppButton>
            ) : null}
            {canReportSelectedMessage ? (
              <AppButton onPress={onReportSelected} size="sm" variant="danger">
                Report
              </AppButton>
            ) : null}
            <AppButton onPress={onCloseSelectedMessage} size="sm" variant="secondary">
              Clear
            </AppButton>
          </View>
          {singleSelectedMessage && !singleSelectedMessage.deletedAt && reactionsEnabled ? (
            <View style={styles.rowWrap}>
              {reactionChoices.map((emoji) => (
                <AppButton
                  key={emoji}
                  onPress={() => void onToggleReaction(emoji, singleSelectedMessage)}
                  size="sm"
                  variant="secondary"
                >
                  {emoji}
                </AppButton>
              ))}
            </View>
          ) : null}
        </AppPanel>
      ) : null}

      {showReplyPanel ? (
        <AppPanel
          style={styles.selectionBar}
          title={replyPanelTitle}
          titleStyle={styles.selectionTitle}
          tone="warning"
        >
          {editingMessageId ? (
            <Text style={styles.selectionBody}>Update the selected message</Text>
          ) : activeThreadRootMessageId && !replyToMessageId ? (
            threadRootMessage?.text ? (
              <FormattedMessageText
                entities={threadRootMessage.entities}
                onOpenLink={onOpenLink}
                style={styles.selectionBody}
                text={threadRootMessage.text}
                variant="muted"
              />
            ) : (
              <Text style={styles.selectionBody}>Send a comment to this post.</Text>
            )
          ) : replyTarget ? (
            replyTarget.deletedAt ? (
              <Text style={styles.selectionBody}>Message deleted</Text>
            ) : replyTarget.text ? (
              <FormattedMessageText
                entities={replyTarget.entities}
                onOpenLink={onOpenLink}
                style={styles.selectionBody}
                text={replyTarget.text}
                variant="muted"
              />
            ) : replyTarget.serviceMessage?.text ? (
              <FormattedMessageText
                onOpenLink={onOpenLink}
                style={styles.selectionBody}
                text={replyTarget.serviceMessage.text}
                variant="muted"
              />
            ) : (
              <Text style={styles.selectionBody}>{describeMessage(replyTarget)}</Text>
            )
          ) : (
            <Text style={styles.selectionBody}>Reply target is outside the loaded window</Text>
          )}
          <View style={styles.rowWrap}>
            <AppButton onPress={onCancelComposerModes} size="sm" variant="secondary">
              Cancel
            </AppButton>
          </View>
        </AppPanel>
      ) : null}

      {channelPostingDisabled ? (
        <AppPanel style={styles.infoBar} title="Channel" titleStyle={styles.infoTitle} tone="info">
          <Text style={styles.infoText}>Publishing is disabled for this member in the channel feed.</Text>
        </AppPanel>
      ) : null}

      {slowModeLabel ? (
        <AppPanel style={styles.infoBar} title="Slow mode" titleStyle={styles.infoTitle} tone="info">
          <Text style={styles.infoText}>{slowModeLabel}</Text>
        </AppPanel>
      ) : null}

      {!reactionsEnabled ? (
        <AppPanel style={styles.infoBar} title="Reactions" titleStyle={styles.infoTitle} tone="info">
          <Text style={styles.infoText}>Reactions are disabled by chat admins.</Text>
        </AppPanel>
      ) : null}

      {showUnreadPanel && firstUnreadMessage ? (
        <AppPanel
          style={styles.infoBar}
          title="Unread messages"
          titleStyle={styles.infoTitle}
          tone="info"
        >
          <Text style={styles.infoText}>
            Jump back to the first unread message in this conversation.
          </Text>
          <View style={styles.rowWrap}>
            <AppButton
              onPress={() =>
                void onEnsureMessageVisible({
                  createdAt: firstUnreadMessage.createdAt,
                  messageId: firstUnreadMessage.messageId
                })
              }
              size="sm"
              variant="secondary"
            >
              {`Jump to unread (${unreadCount})`}
            </AppButton>
          </View>
        </AppPanel>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  activePinnedHistoryCard: {
    borderColor: "#f59e0b",
    borderWidth: 2
  },
  infoBar: {
    backgroundColor: "#e0f2fe",
    borderRadius: 14,
    marginBottom: 8,
    marginHorizontal: 16,
    padding: 12
  },
  infoMetaText: {
    color: "#0c4a6e",
    fontSize: 12,
    fontWeight: "600",
    marginTop: 6
  },
  infoText: {
    color: "#0c4a6e",
    marginTop: 4
  },
  infoTitle: {
    color: "#075985",
    fontWeight: "700"
  },
  loader: {
    marginTop: 12
  },
  pinnedHistoryHeader: {
    alignItems: "center",
    flexDirection: "row",
    gap: 12,
    justifyContent: "space-between"
  },
  rowWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8
  },
  scheduledCard: {
    backgroundColor: "#ffffff",
    borderRadius: 12,
    gap: 4,
    padding: 10
  },
  scheduledList: {
    gap: 8,
    marginTop: 10
  },
  scheduledMeta: {
    color: "#64748b",
    fontSize: 12
  },
  scheduledText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  selectionBar: {
    backgroundColor: "#fef3c7",
    borderRadius: 14,
    marginBottom: 8,
    marginHorizontal: 16,
    padding: 12
  },
  selectionBody: {
    color: "#92400e",
    marginTop: 4
  },
  selectionMeta: {
    color: "#b45309",
    fontSize: 12,
    marginTop: 6
  },
  selectionTitle: {
    color: "#92400e",
    fontWeight: "700"
  }
});
