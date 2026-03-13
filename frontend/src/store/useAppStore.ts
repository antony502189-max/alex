import { create } from "zustand";
import type { AuthSession, ChatFolder, ChatMessage, ChatReadEvent, ChatSummary } from "../types";

type State = {
  session: AuthSession | null;
  chats: ChatSummary[];
  folders: ChatFolder[];
  messagesByChat: Record<string, ChatMessage[]>;
  setSession: (session: AuthSession) => void;
  setChats: (chats: ChatSummary[]) => void;
  setFolders: (folders: ChatFolder[]) => void;
  upsertChat: (chat: ChatSummary) => void;
  upsertFolder: (folder: ChatFolder) => void;
  removeFolder: (folderId: string) => void;
  setChatMessages: (chatId: string, messages: ChatMessage[]) => void;
  upsertMessage: (message: ChatMessage) => void;
  replaceMessage: (chatId: string, queuedMessageId: string, message: ChatMessage) => void;
  removeMessage: (chatId: string, messageId: string) => void;
  applyReadEvent: (event: ChatReadEvent) => void;
  logout: () => void;
};

function mergeMessages(
  current: ChatMessage[],
  incoming: ChatMessage[]
): ChatMessage[] {
  const map = new Map<string, ChatMessage>();

  function buildKey(message: ChatMessage) {
    return message.clientMessageId
      ? `client:${message.clientMessageId}`
      : `message:${message.messageId}`;
  }

  function preferMessage(left: ChatMessage, right: ChatMessage) {
    if (left.deliveryStatus === "QUEUED" && right.deliveryStatus !== "QUEUED") {
      return right;
    }
    if (right.deliveryStatus === "QUEUED" && left.deliveryStatus !== "QUEUED") {
      return left;
    }
    return right.createdAt.localeCompare(left.createdAt) >= 0 ? right : left;
  }

  for (const message of current) {
    map.set(buildKey(message), message);
  }

  for (const message of incoming) {
    const key = buildKey(message);
    const existing = map.get(key);
    map.set(key, existing ? preferMessage(existing, message) : message);
  }

  return [...map.values()].sort((left, right) =>
    left.createdAt.localeCompare(right.createdAt)
  );
}

function sortFolders(folders: ChatFolder[]) {
  return [...folders].sort((left, right) => left.position - right.position);
}

export const useAppStore = create<State>((set) => ({
  session: null,
  chats: [],
  folders: [],
  messagesByChat: {},
  setSession: (session) => set({ session }),
  setChats: (chats) => set({ chats }),
  setFolders: (folders) => set({ folders: sortFolders(folders) }),
  upsertChat: (chat) =>
    set((state) => {
      const nextChats = state.chats.filter((item) => item.chatId !== chat.chatId);
      return {
        chats: [chat, ...nextChats].sort((left, right) =>
          right.lastMessageAt.localeCompare(left.lastMessageAt)
        )
      };
    }),
  upsertFolder: (folder) =>
    set((state) => ({
      folders: sortFolders([
        ...state.folders.filter((item) => item.folderId !== folder.folderId),
        folder
      ])
    })),
  removeFolder: (folderId) =>
    set((state) => ({
      folders: state.folders.filter((folder) => folder.folderId !== folderId)
    })),
  setChatMessages: (chatId, messages) =>
    set((state) => ({
      messagesByChat: {
        ...state.messagesByChat,
        [chatId]: mergeMessages(state.messagesByChat[chatId] ?? [], messages)
      }
    })),
  upsertMessage: (message) =>
    set((state) => ({
      messagesByChat: {
        ...state.messagesByChat,
        [message.chatId]: mergeMessages(
          state.messagesByChat[message.chatId] ?? [],
          [message]
        )
      }
    })),
  replaceMessage: (chatId, queuedMessageId, message) =>
    set((state) => ({
      messagesByChat: {
        ...state.messagesByChat,
        [chatId]: mergeMessages(
          (state.messagesByChat[chatId] ?? []).filter(
            (current) => current.messageId !== queuedMessageId
          ),
          [message]
        )
      }
    })),
  removeMessage: (chatId, messageId) =>
    set((state) => ({
      messagesByChat: {
        ...state.messagesByChat,
        [chatId]: (state.messagesByChat[chatId] ?? []).filter(
          (message) => message.messageId !== messageId
        )
      }
    })),
  applyReadEvent: (event) =>
    set((state) => {
      const current = state.messagesByChat[event.chatId] ?? [];
      const currentUserId = state.session?.userId ?? null;
      return {
        chats:
          currentUserId === event.userId
            ? state.chats.map((chat) =>
                chat.chatId === event.chatId
                  ? {
                      ...chat,
                      lastReadAt: event.readAt,
                      unreadCount: 0,
                      mentionCount: 0,
                      replyCount: 0
                    }
                  : chat
              )
            : state.chats,
        messagesByChat: {
          ...state.messagesByChat,
          [event.chatId]: current.map((message) =>
            message.recipientId === event.userId &&
            message.createdAt.localeCompare(event.readAt) <= 0
              ? {
                  ...message,
                  deliveryStatus: "READ",
                  deliveredAt: message.deliveredAt ?? event.readAt,
                  readAt: event.readAt
                }
              : message
          )
        }
      };
    }),
  logout: () =>
    set({
      session: null,
      chats: [],
      folders: [],
      messagesByChat: {}
    })
}));
