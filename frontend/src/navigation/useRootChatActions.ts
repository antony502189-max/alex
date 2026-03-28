import { api } from "../services/api";
import { localDatabase } from "../services/localDatabase";
import { messageOutbox } from "../services/messageOutbox";
import { scheduledMessageOutbox } from "../services/scheduledMessageOutbox";
import type { Dispatch, SetStateAction } from "react";
import type { AppModalRoute, RootTab } from "./types";
import type {
  AuthSession,
  ChatMessage,
  ChatSummary,
  ForumTopic
} from "../types";
import type {
  DiscussionThreadSelection,
  MessageFocusTarget
} from "./rootNavigatorState";

type UseRootChatActionsInput = {
  chats: ChatSummary[];
  removeMessage: (chatId: string, messageId: string) => void;
  replaceMessage: (
    chatId: string,
    messageId: string,
    message: ChatMessage
  ) => void;
  selectedChat: ChatSummary | null;
  session: AuthSession | null;
  setActiveRootTab: (tab: RootTab) => void;
  setChats: (chats: ChatSummary[]) => void;
  setMembersChat: Dispatch<SetStateAction<ChatSummary | null>>;
  setModalRoute: Dispatch<SetStateAction<AppModalRoute | null>>;
  setPendingChatFocus: Dispatch<SetStateAction<MessageFocusTarget | null>>;
  setSelectedChat: Dispatch<SetStateAction<ChatSummary | null>>;
  setSelectedDiscussionThread: Dispatch<SetStateAction<DiscussionThreadSelection | null>>;
  setSelectedForumTopic: Dispatch<SetStateAction<ForumTopic | null>>;
};

export function useRootChatActions({
  chats,
  removeMessage,
  replaceMessage,
  selectedChat,
  session,
  setActiveRootTab,
  setChats,
  setMembersChat,
  setModalRoute,
  setPendingChatFocus,
  setSelectedChat,
  setSelectedDiscussionThread,
  setSelectedForumTopic
}: UseRootChatActionsInput) {
  function syncOpenChatTargets(nextChats: ChatSummary[]) {
    setSelectedChat((current) =>
      current ? nextChats.find((chat) => chat.chatId === current.chatId) ?? current : current
    );
    setMembersChat((current) =>
      current ? nextChats.find((chat) => chat.chatId === current.chatId) ?? current : current
    );
  }

  function openChat(
    chat: ChatSummary,
    focus?: { messageId: string; createdAt: string } | null
  ) {
    setSelectedDiscussionThread(null);
    setSelectedForumTopic(null);
    setModalRoute(null);
    setActiveRootTab("CHATS");
    setPendingChatFocus(
      focus
        ? {
            chatId: chat.chatId,
            messageId: focus.messageId,
            createdAt: focus.createdAt
          }
        : null
    );
    setSelectedChat(chat);
  }

  async function refreshChats(sessionToken: string, currentUserId?: string) {
    const nextChats = await api.getChats(sessionToken);
    setChats(nextChats);
    syncOpenChatTargets(nextChats);
    if (currentUserId) {
      await localDatabase.replaceChats(currentUserId, nextChats);
    }
    return nextChats;
  }

  async function flushOutbox(sessionToken: string, currentUserId: string) {
    await messageOutbox.flush(sessionToken, currentUserId, {
      onSynced: (queuedMessageId, message) => {
        replaceMessage(message.chatId, queuedMessageId, message);
        void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
      },
      onDropped: (queuedMessageId, chatId) => {
        removeMessage(chatId, queuedMessageId);
      }
    });
    await scheduledMessageOutbox.flush(sessionToken, currentUserId, {
      onSynced: (_queuedScheduledMessageId, message) => {
        void localDatabase.upsertScheduledMessages(currentUserId, [message]).catch(() => undefined);
      },
      onDropped: (_queuedScheduledMessageId, _chatId) => undefined
    });
  }

  async function openChatFromNotification(
    sessionToken: string,
    chatId: string,
    currentUserId: string,
    topicId?: string | null,
    focus?: { messageId: string; createdAt: string } | null
  ) {
    setMembersChat(null);
    setModalRoute(null);
    setSelectedForumTopic(null);
    setSelectedDiscussionThread(null);
    setPendingChatFocus(
      focus
        ? {
            chatId,
            messageId: focus.messageId,
            createdAt: focus.createdAt
          }
        : null
    );
    setActiveRootTab("CHATS");

    const existing = chats.find((chat) => chat.chatId === chatId);
    if (existing) {
      setSelectedChat(existing);
      if (existing.forumEnabled && topicId) {
        try {
          const topics = await api.getForumTopics(sessionToken, existing.chatId);
          const topic = topics.find((item) => item.topicId === topicId) ?? null;
          if (topic) {
            setSelectedForumTopic(topic);
          }
        } catch {
        }
      }
      return;
    }

    const nextChats = await refreshChats(sessionToken, currentUserId);
    const targetChat = nextChats.find((chat) => chat.chatId === chatId) ?? null;
    if (targetChat) {
      setSelectedChat(targetChat);
      if (targetChat.forumEnabled && topicId) {
        try {
          const topics = await api.getForumTopics(sessionToken, targetChat.chatId);
          const topic = topics.find((item) => item.topicId === topicId) ?? null;
          if (topic) {
            setSelectedForumTopic(topic);
          }
        } catch {
        }
      }
    }
  }

  async function openDiscussionThread(message: ChatMessage) {
    if (
      !session ||
      !selectedChat ||
      !message.discussionChatId ||
      !message.discussionRootMessageId
    ) {
      return;
    }

    let discussionChat =
      chats.find((chat) => chat.chatId === message.discussionChatId) ?? null;
    if (!discussionChat) {
      const nextChats = await refreshChats(session.token, session.userId);
      discussionChat =
        nextChats.find((chat) => chat.chatId === message.discussionChatId) ?? null;
    }
    if (!discussionChat) {
      return;
    }

    setMembersChat(null);
    setSelectedForumTopic(null);
    setSelectedDiscussionThread({
      discussionChatId: discussionChat.chatId,
      rootMessageId: message.discussionRootMessageId,
      originChatId: selectedChat.chatId,
      title: null
    });
    setSelectedChat(discussionChat);
  }

  return {
    flushOutbox,
    openChat,
    openChatFromNotification,
    openDiscussionThread,
    refreshChats,
    syncOpenChatTargets
  };
}
