import React from "react";
import { FlatList } from "react-native";
import type { StyleProp, ViewStyle } from "react-native";
import { ChatMessageBubble } from "./ChatMessageBubble";
import type {
  ChatMessage,
  ChatSummary,
  MessageSelectionState
} from "../../types";

type SharedBubbleProps = Omit<
  React.ComponentProps<typeof ChatMessageBubble>,
  | "displaySenderName"
  | "isHighlighted"
  | "isPinned"
  | "isSelected"
  | "message"
  | "onPress"
  | "onLongPress"
  | "replyPreview"
  | "replyPreviewSenderName"
  | "selectionActive"
  | "showOpenInTimeline"
  | "showSenderLabel"
  | "showUnreadDivider"
>;

type ChatTimelineListProps = {
  activeThreadRootMessageId: string | null;
  allChatMessages: ChatMessage[];
  bubbleProps: SharedBubbleProps;
  chatType: ChatSummary["chatType"];
  contentContainerStyle?: StyleProp<ViewStyle>;
  currentUserId: string;
  firstUnreadMessageId: string | null;
  highlightedMessageId: string | null;
  listRef: React.RefObject<FlatList<ChatMessage> | null>;
  messages: ChatMessage[];
  onMessageLongPress: (messageId: string) => void;
  onMessagePress: (messageId: string) => void;
  pinnedMessageId: string | null;
  resolveDisplaySenderName: (message: ChatMessage | null | undefined) => string | null;
  searchQuery: string;
  selectionState: MessageSelectionState;
};

export function ChatTimelineList({
  activeThreadRootMessageId,
  allChatMessages,
  bubbleProps,
  chatType,
  contentContainerStyle,
  currentUserId,
  firstUnreadMessageId,
  highlightedMessageId,
  listRef,
  messages,
  onMessageLongPress,
  onMessagePress,
  pinnedMessageId,
  resolveDisplaySenderName,
  searchQuery,
  selectionState
}: ChatTimelineListProps) {
  return (
    <FlatList
      ref={listRef}
      contentContainerStyle={contentContainerStyle}
      data={messages}
      keyboardShouldPersistTaps="handled"
      keyExtractor={(item) => item.messageId}
      onScrollToIndexFailed={({ averageItemLength, index }) => {
        if (!averageItemLength) {
          return;
        }
        listRef.current?.scrollToOffset({
          offset: Math.max(0, averageItemLength * index),
          animated: true
        });
        setTimeout(() => {
          listRef.current?.scrollToIndex({
            index,
            animated: true,
            viewPosition: 0.45
          });
        }, 160);
      }}
      renderItem={({ item }) => {
        const replyPreview = item.replyToMessageId
          ? allChatMessages.find((message) => message.messageId === item.replyToMessageId) ?? null
          : null;
        const displaySenderName = resolveDisplaySenderName(item);
        const replyPreviewSenderName = resolveDisplaySenderName(replyPreview);
        const shouldShowSenderLabel =
          item.senderId !== currentUserId &&
          Boolean(displaySenderName) &&
          (chatType === "GROUP" || activeThreadRootMessageId != null);
        const isSelected = selectionState.selectedMessageIds.includes(item.messageId);

        return (
          <ChatMessageBubble
            {...bubbleProps}
            displaySenderName={displaySenderName}
            isHighlighted={highlightedMessageId === item.messageId}
            isPinned={pinnedMessageId === item.messageId}
            isSelected={isSelected}
            message={item}
            onLongPress={() => onMessageLongPress(item.messageId)}
            onPress={() => onMessagePress(item.messageId)}
            replyPreview={replyPreview}
            replyPreviewSenderName={replyPreviewSenderName}
            selectionActive={selectionState.active}
            showOpenInTimeline={searchQuery.trim().length >= 2}
            showSenderLabel={shouldShowSenderLabel}
            showUnreadDivider={
              firstUnreadMessageId === item.messageId && searchQuery.trim().length < 2
            }
          />
        );
      }}
    />
  );
}
