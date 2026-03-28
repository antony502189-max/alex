import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { ChatTimelinePanels } from "./ChatTimelinePanels";
import type { ChatMessage, PinnedMessageHistoryEntry, ScheduledMessage } from "../../types";

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
    senderId: "user-1",
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

function createScheduledMessage(overrides: Partial<ScheduledMessage> = {}): ScheduledMessage {
  return {
    attachments: [],
    caption: null,
    chatId: "chat-1",
    clientMessageId: null,
    contactCard: null,
    createdAt: "2026-03-28T10:00:00.000Z",
    discussionChatId: null,
    discussionRootMessageId: null,
    entities: [],
    location: null,
    liveLocation: null,
    messageType: "SERVICE",
    replyToMessageId: null,
    scheduledAt: "2026-03-28T11:00:00.000Z",
    scheduledMessageId: "scheduled-1",
    senderId: "user-1",
    serviceMessage: null,
    silent: false,
    status: "PENDING",
    stickerId: null,
    text: "",
    threadRootMessageId: null,
    topicId: null,
    ...overrides
  };
}

describe("ChatTimelinePanels", () => {
  it("opens links from pinned, history, scheduled, and reply service previews", () => {
    const onOpenLink = jest.fn();
    const pinnedPreviewMessage = createMessage({
      messageId: "pin-1",
      serviceMessage: {
        serviceType: "Pinned",
        text: "Pinned t.me/team"
      }
    });
    const pinnedHistoryEntry: PinnedMessageHistoryEntry = {
      active: false,
      chatId: "chat-1",
      message: createMessage({
        messageId: "history-1",
        serviceMessage: {
          serviceType: "History",
          text: "History tg://call/room-77"
        }
      }),
      messageId: "history-1",
      pinEventId: "event-1",
      pinnedAt: "2026-03-28T10:10:00.000Z",
      pinnedByDisplayName: "Alex",
      pinnedByUserId: "user-1",
      unpinnedAt: null
    };
    const scheduledMessage = createScheduledMessage({
      serviceMessage: {
        serviceType: "Scheduled",
        text: "Later alex://chat/chat-7?topicId=topic-2"
      }
    });
    const replyTarget = createMessage({
      messageId: "reply-1",
      serviceMessage: {
        serviceType: "Reply",
        text: "Reply @teamroom"
      }
    });

    const screen = render(
      <ChatTimelinePanels
        activePinnedHistoryEntry={null}
        activeThreadRootMessageId={null}
        cancelingScheduledMessageId={null}
        canPinMessages={true}
        channelPostingDisabled={false}
        currentUserId="user-1"
        describeMessage={() => "preview"}
        editingMessageId={null}
        firstUnreadMessage={null}
        loadingPinnedHistory={false}
        onCancelComposerModes={jest.fn()}
        onCancelScheduledMessage={jest.fn()}
        onCloseSelectedMessage={jest.fn()}
        onDeleteSelected={jest.fn()}
        onEditSelected={jest.fn()}
        onEnsureMessageVisible={jest.fn()}
        onForwardSelected={jest.fn()}
        onOpenLink={onOpenLink}
        onPinSelected={jest.fn()}
        onRefreshLiveLocation={jest.fn()}
        onReportSelected={jest.fn()}
        onReplySelected={jest.fn()}
        onShareSelected={jest.fn()}
        onStopLiveLocation={jest.fn()}
        onToggleReaction={jest.fn()}
        pinnedHistory={[pinnedHistoryEntry]}
        pinnedPreviewMessage={pinnedPreviewMessage}
        pinnedPreviewText="fallback"
        reactionChoices={["+1"]}
        reactionsEnabled={true}
        replyPanelTitle="Replying"
        replyTarget={replyTarget}
        replyToMessageId="reply-1"
        scheduledMessages={[scheduledMessage]}
        scheduledPanelTitle="Scheduled messages"
        selectedMessage={null}
        selectedMessages={[]}
        showPinnedHistory={true}
        showPinnedPanel={true}
        showReplyPanel={true}
        showScheduledPanel={true}
        showUnreadPanel={false}
        slowModeLabel={null}
        threadRootMessage={null}
        topicClosed={false}
        unreadCount={0}
      />
    );

    fireEvent.press(screen.getByText("t.me/team"));
    fireEvent.press(screen.getByText("tg://call/room-77"));
    fireEvent.press(screen.getByText("alex://chat/chat-7?topicId=topic-2"));
    fireEvent.press(screen.getByText("@teamroom"));

    expect(onOpenLink).toHaveBeenNthCalledWith(1, "t.me/team");
    expect(onOpenLink).toHaveBeenNthCalledWith(2, "tg://call/room-77");
    expect(onOpenLink).toHaveBeenNthCalledWith(3, "alex://chat/chat-7?topicId=topic-2");
    expect(onOpenLink).toHaveBeenNthCalledWith(4, "@teamroom");
  });

  it("shows batch selection actions and hides single-message controls for multi-select", () => {
    const screen = render(
      <ChatTimelinePanels
        activePinnedHistoryEntry={null}
        activeThreadRootMessageId={null}
        cancelingScheduledMessageId={null}
        canPinMessages={true}
        channelPostingDisabled={false}
        currentUserId="user-1"
        describeMessage={() => "preview"}
        editingMessageId={null}
        firstUnreadMessage={null}
        loadingPinnedHistory={false}
        onCancelComposerModes={jest.fn()}
        onCancelScheduledMessage={jest.fn()}
        onCloseSelectedMessage={jest.fn()}
        onDeleteSelected={jest.fn()}
        onEditSelected={jest.fn()}
        onEnsureMessageVisible={jest.fn()}
        onForwardSelected={jest.fn()}
        onOpenLink={jest.fn()}
        onPinSelected={jest.fn()}
        onRefreshLiveLocation={jest.fn()}
        onReportSelected={jest.fn()}
        onReplySelected={jest.fn()}
        onShareSelected={jest.fn()}
        onStopLiveLocation={jest.fn()}
        onToggleReaction={jest.fn()}
        pinnedHistory={[]}
        pinnedPreviewMessage={null}
        pinnedPreviewText="fallback"
        reactionChoices={["+1"]}
        reactionsEnabled={true}
        replyPanelTitle="Replying"
        replyTarget={null}
        replyToMessageId={null}
        scheduledMessages={[]}
        scheduledPanelTitle="Scheduled messages"
        selectedMessage={createMessage({
          messageId: "message-2",
          senderId: "user-2",
          text: "Second selected"
        })}
        selectedMessages={[
          createMessage({
            messageId: "message-1",
            senderId: "user-1",
            text: "First selected"
          }),
          createMessage({
            messageId: "message-2",
            senderId: "user-2",
            text: "Second selected"
          })
        ]}
        showPinnedHistory={false}
        showPinnedPanel={false}
        showReplyPanel={false}
        showScheduledPanel={false}
        showUnreadPanel={false}
        slowModeLabel={null}
        threadRootMessage={null}
        topicClosed={false}
        unreadCount={0}
      />
    );

    expect(screen.getByText("2 selected")).toBeTruthy();
    expect(screen.getByText("Share")).toBeTruthy();
    expect(screen.getByText("Forward")).toBeTruthy();
    expect(screen.getByText("Delete")).toBeTruthy();
    expect(screen.queryByText("Reply")).toBeNull();
    expect(screen.queryByText("Edit")).toBeNull();
    expect(screen.queryByText("Pin")).toBeNull();
  });
});
