export type DiscussionThreadSelection = {
  discussionChatId: string;
  rootMessageId: string;
  originChatId: string;
  title: string | null;
};

export type MessageFocusTarget = {
  chatId: string;
  messageId: string;
  createdAt: string;
};
