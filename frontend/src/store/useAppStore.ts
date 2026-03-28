import { create } from "zustand";
import { applyConsumerFeatureProfilePolicy } from "../config/featureFlags";
import {
  defaultAppearanceSettings,
  defaultChatListState,
  defaultDataStorageSettings,
  defaultDisclosureState,
  defaultNotificationSettings
} from "../config/localSettings";
import { accountRegistry } from "../services/accountRegistry";
import { localDatabase } from "../services/localDatabase";
import { secretChatLocalCleanup } from "../services/secretChatLocalCleanup";
import type {
  AccountRegistry,
  AccountState,
  AppearanceSettings,
  AuthSession,
  ChatListState,
  ChatFolder,
  ChatMessage,
  ChatReadEvent,
  ChatSummary,
  DataStorageSettings,
  DisclosureState,
  FeatureProfile
} from "../types";
import type { NotificationSettings } from "../types";

type SessionUpdateOptions = {
  switchTo?: boolean;
};

type State = {
  hydrated: boolean;
  hydrating: boolean;
  activeAccountId: string | null;
  accountsById: Record<string, AccountState>;
  session: AuthSession | null;
  featureProfile: FeatureProfile | null;
  chats: ChatSummary[];
  folders: ChatFolder[];
  messagesByChat: Record<string, ChatMessage[]>;
  notificationSettings: NotificationSettings;
  dataStorageSettings: DataStorageSettings;
  appearanceSettings: AppearanceSettings;
  chatListState: ChatListState;
  disclosureState: DisclosureState;
  hydrate: () => Promise<void>;
  setSession: (session: AuthSession, options?: SessionUpdateOptions) => void;
  setFeatureProfile: (profile: FeatureProfile | null) => void;
  switchAccount: (accountId: string) => void;
  removeAccount: (accountId: string) => void;
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
  updateNotificationSettings: (patch: Partial<NotificationSettings>) => void;
  updateDataStorageSettings: (patch: Partial<DataStorageSettings>) => void;
  updateAppearanceSettings: (patch: Partial<AppearanceSettings>) => void;
  updateChatListState: (patch: Partial<ChatListState>) => void;
  acknowledgePrivacyDisclosure: () => void;
  logout: () => void;
};

let hydratePromise: Promise<void> | undefined;

function accountIdFromSession(session: AuthSession) {
  return session.userId;
}

function mergeMessages(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
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

function sortChats(chats: ChatSummary[]) {
  return [...chats].sort((left, right) => {
    if (left.pinned !== right.pinned) {
      return left.pinned ? -1 : 1;
    }

    const leftPinOrder = left.pinOrder ?? Number.MAX_SAFE_INTEGER;
    const rightPinOrder = right.pinOrder ?? Number.MAX_SAFE_INTEGER;
    if (leftPinOrder !== rightPinOrder) {
      return leftPinOrder - rightPinOrder;
    }

    return right.lastMessageAt.localeCompare(left.lastMessageAt);
  });
}

function deriveActiveState(
  accountsById: Record<string, AccountState>,
  activeAccountId: string | null
) {
  const activeAccount =
    activeAccountId && accountsById[activeAccountId]
      ? accountsById[activeAccountId]
      : null;

  return {
    session: activeAccount?.session ?? null,
    featureProfile: activeAccount?.featureProfile ?? null,
    chats: activeAccount?.chats ?? [],
    folders: activeAccount?.folders ?? [],
    messagesByChat: activeAccount?.messagesByChat ?? {},
    notificationSettings: activeAccount?.notificationSettings ?? defaultNotificationSettings,
    dataStorageSettings: activeAccount?.dataStorageSettings ?? defaultDataStorageSettings,
    appearanceSettings: activeAccount?.appearanceSettings ?? defaultAppearanceSettings,
    chatListState: activeAccount?.chatListState ?? defaultChatListState,
    disclosureState: activeAccount?.disclosureState ?? defaultDisclosureState
  };
}

function buildPersistedRegistry(state: Pick<State, "accountsById" | "activeAccountId">): AccountRegistry {
  return {
    activeAccountId: state.activeAccountId,
    accounts: Object.entries(state.accountsById)
      .map(([accountId, account]) => ({
        accountId,
        session: account.session,
        featureProfile: account.featureProfile,
        notificationSettings: account.notificationSettings,
        dataStorageSettings: account.dataStorageSettings,
        appearanceSettings: account.appearanceSettings,
        chatListState: account.chatListState,
        disclosureState: account.disclosureState,
        lastActivatedAt: account.lastActivatedAt
      }))
      .sort((left, right) => right.lastActivatedAt.localeCompare(left.lastActivatedAt))
  };
}

function persistAccountRegistry(state: Pick<State, "accountsById" | "activeAccountId">) {
  const nextRegistry = buildPersistedRegistry(state);
  if (nextRegistry.accounts.length === 0) {
    void accountRegistry.clear().catch(() => undefined);
    return;
  }
  void accountRegistry.save(nextRegistry).catch(() => undefined);
}

function buildAccountState(session: AuthSession, existing?: AccountState | null): AccountState {
  return {
    session,
    featureProfile: existing?.featureProfile ?? null,
    chats: existing?.chats ?? [],
    folders: existing?.folders ?? [],
    messagesByChat: existing?.messagesByChat ?? {},
    notificationSettings: existing?.notificationSettings ?? defaultNotificationSettings,
    dataStorageSettings: existing?.dataStorageSettings ?? defaultDataStorageSettings,
    appearanceSettings: existing?.appearanceSettings ?? defaultAppearanceSettings,
    chatListState: existing?.chatListState ?? defaultChatListState,
    disclosureState: existing?.disclosureState ?? defaultDisclosureState,
    lastActivatedAt: new Date().toISOString()
  };
}

export const useAppStore = create<State>((set, get) => ({
  hydrated: false,
  hydrating: false,
  activeAccountId: null,
  accountsById: {},
  session: null,
  featureProfile: null,
  chats: [],
  folders: [],
  messagesByChat: {},
  notificationSettings: defaultNotificationSettings,
  dataStorageSettings: defaultDataStorageSettings,
  appearanceSettings: defaultAppearanceSettings,
  chatListState: defaultChatListState,
  disclosureState: defaultDisclosureState,
  hydrate: async () => {
    if (get().hydrated) {
      return;
    }
    if (hydratePromise) {
      return hydratePromise;
    }

    set({ hydrating: true });
    hydratePromise = accountRegistry
      .load()
      .then((registry) => {
        set((state) => {
          const accountsById = registry.accounts.reduce<Record<string, AccountState>>(
            (result, account) => {
              const existing = state.accountsById[account.accountId];
              result[account.accountId] = {
                session: account.session,
                featureProfile: account.featureProfile,
                chats: existing?.chats ?? [],
                folders: existing?.folders ?? [],
                messagesByChat: existing?.messagesByChat ?? {},
                notificationSettings:
                  account.notificationSettings ?? existing?.notificationSettings ?? defaultNotificationSettings,
                dataStorageSettings:
                  account.dataStorageSettings ?? existing?.dataStorageSettings ?? defaultDataStorageSettings,
                appearanceSettings:
                  account.appearanceSettings ?? existing?.appearanceSettings ?? defaultAppearanceSettings,
                chatListState:
                  account.chatListState ?? existing?.chatListState ?? defaultChatListState,
                disclosureState:
                  account.disclosureState ?? existing?.disclosureState ?? defaultDisclosureState,
                lastActivatedAt: account.lastActivatedAt
              };
              return result;
            },
            {}
          );

          const activeAccountId =
            registry.activeAccountId && accountsById[registry.activeAccountId]
              ? registry.activeAccountId
              : registry.accounts[0]?.accountId ?? null;

          return {
            hydrated: true,
            hydrating: false,
            activeAccountId,
            accountsById,
            ...deriveActiveState(accountsById, activeAccountId)
          };
        });
      })
      .catch(() => {
        set({
          hydrated: true,
          hydrating: false
        });
      })
      .finally(() => {
        hydratePromise = undefined;
      });

    return hydratePromise;
  },
  setSession: (session, options) => {
    const switchTo = options?.switchTo ?? true;
    set((state) => {
      const accountId = accountIdFromSession(session);
      const accountsById = {
        ...state.accountsById,
        [accountId]: buildAccountState(session, state.accountsById[accountId] ?? null)
      };
      const activeAccountId = switchTo ? accountId : state.activeAccountId ?? accountId;
      return {
        accountsById,
        activeAccountId,
        ...deriveActiveState(accountsById, activeAccountId)
      };
    });
    persistAccountRegistry(get());
  },
  setFeatureProfile: (featureProfile) => {
    const nextFeatureProfile = applyConsumerFeatureProfilePolicy(featureProfile);
    set((state) => {
      if (!state.activeAccountId) {
        return {
          featureProfile: nextFeatureProfile
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      if (!currentAccount) {
        return {
          featureProfile: nextFeatureProfile
        };
      }

      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: {
            ...currentAccount,
            featureProfile: nextFeatureProfile
          }
        },
        featureProfile: nextFeatureProfile
      };
    });
    persistAccountRegistry(get());
  },
  switchAccount: (accountId) => {
    set((state) => {
      const existing = state.accountsById[accountId];
      if (!existing) {
        return state;
      }

      const accountsById = {
        ...state.accountsById,
        [accountId]: {
          ...existing,
          lastActivatedAt: new Date().toISOString()
        }
      };
      return {
        accountsById,
        activeAccountId: accountId,
        ...deriveActiveState(accountsById, accountId)
      };
    });
    persistAccountRegistry(get());
  },
  removeAccount: (accountId) => {
    const currentState = get();
    const removedAccount = currentState.accountsById[accountId] ?? null;
    set((state) => {
      if (!state.accountsById[accountId]) {
        return state;
      }

      const nextAccountsById = { ...state.accountsById };
      delete nextAccountsById[accountId];

      const nextActiveAccountId =
        state.activeAccountId === accountId
          ? Object.entries(nextAccountsById)
              .sort((left, right) =>
                right[1].lastActivatedAt.localeCompare(left[1].lastActivatedAt)
              )[0]?.[0] ?? null
          : state.activeAccountId && nextAccountsById[state.activeAccountId]
            ? state.activeAccountId
            : Object.keys(nextAccountsById)[0] ?? null;

      return {
        accountsById: nextAccountsById,
        activeAccountId: nextActiveAccountId,
        ...deriveActiveState(nextAccountsById, nextActiveAccountId)
      };
    });

    persistAccountRegistry(get());
    if (removedAccount) {
      void localDatabase.purgeAccountData(accountId).catch(() => undefined);
      void secretChatLocalCleanup.clearAllSecretState(accountId).catch(() => undefined);
    }
  },
  setChats: (chats) =>
    set((state) => {
      if (!state.activeAccountId) {
        return {
          chats: sortChats(chats)
        };
      }

      const nextAccount = {
        ...state.accountsById[state.activeAccountId],
        chats: sortChats(chats)
      };
      const accountsById = {
        ...state.accountsById,
        [state.activeAccountId]: nextAccount
      };
      return {
        accountsById,
        chats: nextAccount.chats
      };
    }),
  setFolders: (folders) =>
    set((state) => {
      if (!state.activeAccountId) {
        return {
          folders: sortFolders(folders)
        };
      }

      const nextAccount = {
        ...state.accountsById[state.activeAccountId],
        folders: sortFolders(folders)
      };
      const accountsById = {
        ...state.accountsById,
        [state.activeAccountId]: nextAccount
      };
      return {
        accountsById,
        folders: nextAccount.folders
      };
    }),
  upsertChat: (chat) =>
    set((state) => {
      if (!state.activeAccountId) {
        return state;
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextChats = currentAccount.chats.filter((item) => item.chatId !== chat.chatId);
      const nextAccount = {
        ...currentAccount,
        chats: sortChats([chat, ...nextChats])
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        chats: nextAccount.chats
      };
    }),
  upsertFolder: (folder) =>
    set((state) => {
      if (!state.activeAccountId) {
        return state;
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextAccount = {
        ...currentAccount,
        folders: sortFolders([
          ...currentAccount.folders.filter((item) => item.folderId !== folder.folderId),
          folder
        ])
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        folders: nextAccount.folders
      };
    }),
  removeFolder: (folderId) =>
    set((state) => {
      if (!state.activeAccountId) {
        return state;
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextAccount = {
        ...currentAccount,
        folders: currentAccount.folders.filter((folder) => folder.folderId !== folderId)
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        folders: nextAccount.folders
      };
    }),
  setChatMessages: (chatId, messages) =>
    set((state) => {
      if (!state.activeAccountId) {
        return {
          messagesByChat: {
            ...state.messagesByChat,
            [chatId]: mergeMessages(state.messagesByChat[chatId] ?? [], messages)
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextMessagesByChat = {
        ...currentAccount.messagesByChat,
        [chatId]: mergeMessages(currentAccount.messagesByChat[chatId] ?? [], messages)
      };
      const nextAccount = {
        ...currentAccount,
        messagesByChat: nextMessagesByChat
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        messagesByChat: nextMessagesByChat
      };
    }),
  upsertMessage: (message) =>
    set((state) => {
      if (!state.activeAccountId) {
        return {
          messagesByChat: {
            ...state.messagesByChat,
            [message.chatId]: mergeMessages(state.messagesByChat[message.chatId] ?? [], [message])
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextMessagesByChat = {
        ...currentAccount.messagesByChat,
        [message.chatId]: mergeMessages(
          currentAccount.messagesByChat[message.chatId] ?? [],
          [message]
        )
      };
      const nextAccount = {
        ...currentAccount,
        messagesByChat: nextMessagesByChat
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        messagesByChat: nextMessagesByChat
      };
    }),
  replaceMessage: (chatId, queuedMessageId, message) =>
    set((state) => {
      if (!state.activeAccountId) {
        return {
          messagesByChat: {
            ...state.messagesByChat,
            [chatId]: mergeMessages(
              (state.messagesByChat[chatId] ?? []).filter(
                (current) => current.messageId !== queuedMessageId
              ),
              [message]
            )
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextMessagesByChat = {
        ...currentAccount.messagesByChat,
        [chatId]: mergeMessages(
          (currentAccount.messagesByChat[chatId] ?? []).filter(
            (current) => current.messageId !== queuedMessageId
          ),
          [message]
        )
      };
      const nextAccount = {
        ...currentAccount,
        messagesByChat: nextMessagesByChat
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        messagesByChat: nextMessagesByChat
      };
    }),
  removeMessage: (chatId, messageId) =>
    set((state) => {
      if (!state.activeAccountId) {
        return {
          messagesByChat: {
            ...state.messagesByChat,
            [chatId]: (state.messagesByChat[chatId] ?? []).filter(
              (message) => message.messageId !== messageId
            )
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextMessagesByChat = {
        ...currentAccount.messagesByChat,
        [chatId]: (currentAccount.messagesByChat[chatId] ?? []).filter(
          (message) => message.messageId !== messageId
        )
      };
      const nextAccount = {
        ...currentAccount,
        messagesByChat: nextMessagesByChat
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        messagesByChat: nextMessagesByChat
      };
    }),
  applyReadEvent: (event) =>
    set((state) => {
      if (!state.activeAccountId) {
        return state;
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const currentMessages = currentAccount.messagesByChat[event.chatId] ?? [];
      const currentUserId = currentAccount.session.userId;
      const nextChats =
        currentUserId === event.userId
          ? sortChats(
              currentAccount.chats.map((chat) =>
                chat.chatId === event.chatId
                  ? {
                      ...chat,
                      lastReadAt: event.readAt,
                      unreadCount: 0,
                      mentionCount: 0,
                      replyCount: 0,
                      markedUnread: false
                    }
                  : chat
              )
            )
          : currentAccount.chats;
      const nextMessagesByChat = {
        ...currentAccount.messagesByChat,
        [event.chatId]: currentMessages.map((message) =>
          message.recipientId === event.userId &&
          message.createdAt.localeCompare(event.readAt) <= 0
            ? {
                ...message,
                deliveryStatus: "READ" as const,
                deliveredAt: message.deliveredAt ?? event.readAt,
                readAt: event.readAt
              }
            : message
        )
      };
      const nextAccount = {
        ...currentAccount,
        chats: nextChats,
        messagesByChat: nextMessagesByChat
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        chats: nextChats,
        messagesByChat: nextMessagesByChat
      };
    }),
  updateNotificationSettings: (patch) => {
    set((state) => {
      if (!state.activeAccountId) {
        return {
          notificationSettings: {
            ...state.notificationSettings,
            ...patch
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextNotificationSettings = {
        ...(currentAccount.notificationSettings ?? defaultNotificationSettings),
        ...patch
      };
      const nextAccount = {
        ...currentAccount,
        notificationSettings: nextNotificationSettings
      };
      const accountsById = {
        ...state.accountsById,
        [state.activeAccountId]: nextAccount
      };
      return {
        accountsById,
        notificationSettings: nextNotificationSettings
      };
    });
    persistAccountRegistry(get());
  },
  updateDataStorageSettings: (patch) => {
    set((state) => {
      if (!state.activeAccountId) {
        return {
          dataStorageSettings: {
            ...state.dataStorageSettings,
            ...patch
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextDataStorageSettings = {
        ...(currentAccount.dataStorageSettings ?? defaultDataStorageSettings),
        ...patch
      };
      const nextAccount = {
        ...currentAccount,
        dataStorageSettings: nextDataStorageSettings
      };
      const accountsById = {
        ...state.accountsById,
        [state.activeAccountId]: nextAccount
      };
      return {
        accountsById,
        dataStorageSettings: nextDataStorageSettings
      };
    });
    persistAccountRegistry(get());
  },
  updateAppearanceSettings: (patch) => {
    set((state) => {
      if (!state.activeAccountId) {
        return {
          appearanceSettings: {
            ...state.appearanceSettings,
            ...patch
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextAppearanceSettings = {
        ...(currentAccount.appearanceSettings ?? defaultAppearanceSettings),
        ...patch
      };
      const nextAccount = {
        ...currentAccount,
        appearanceSettings: nextAppearanceSettings
      };
      const accountsById = {
        ...state.accountsById,
        [state.activeAccountId]: nextAccount
      };
      return {
        accountsById,
        appearanceSettings: nextAppearanceSettings
      };
    });
    persistAccountRegistry(get());
  },
  updateChatListState: (patch) => {
    set((state) => {
      if (!state.activeAccountId) {
        return {
          chatListState: {
            ...state.chatListState,
            ...patch
          }
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextChatListState = {
        ...(currentAccount.chatListState ?? defaultChatListState),
        ...patch
      };
      const nextAccount = {
        ...currentAccount,
        chatListState: nextChatListState
      };
      const accountsById = {
        ...state.accountsById,
        [state.activeAccountId]: nextAccount
      };
      return {
        accountsById,
        chatListState: nextChatListState
      };
    });
    persistAccountRegistry(get());
  },
  acknowledgePrivacyDisclosure: () => {
    set((state) => {
      const nextDisclosureState = {
        ...(state.activeAccountId
          ? state.accountsById[state.activeAccountId]?.disclosureState ?? defaultDisclosureState
          : state.disclosureState),
        privacyAcknowledgedAt: new Date().toISOString()
      };

      if (!state.activeAccountId || !state.accountsById[state.activeAccountId]) {
        return {
          disclosureState: nextDisclosureState
        };
      }

      const currentAccount = state.accountsById[state.activeAccountId];
      const nextAccount = {
        ...currentAccount,
        disclosureState: nextDisclosureState
      };
      return {
        accountsById: {
          ...state.accountsById,
          [state.activeAccountId]: nextAccount
        },
        disclosureState: nextDisclosureState
      };
    });
    persistAccountRegistry(get());
  },
  logout: () => {
    const activeAccountId = get().activeAccountId;
    if (!activeAccountId) {
      return;
    }
    get().removeAccount(activeAccountId);
  }
}));
