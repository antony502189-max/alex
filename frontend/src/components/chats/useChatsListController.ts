import { useCallback, useEffect, useMemo, useState } from "react";
import type { ClientFeatureFlags } from "../../config/featureFlags";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import { useAppStore } from "../../store/useAppStore";
import type { ChatSummary } from "../../types";
import type { ChatsQuickAction } from "./ChatsOverviewSection";
import {
  buildChatFilterOptions,
  buildChatsFeatureFlags,
  matchesFilter,
  matchesSearch,
  summarizeUnread,
  type ChatFilter
} from "./chatsListPresentation";

type UseChatsListControllerParams = {
  featureFlags?: Partial<ClientFeatureFlags>;
  onOpenCalls: () => void;
  onOpenContacts: () => void;
  onOpenProfile: () => void;
  onOpenSavedMessages: () => void;
  onOpenStories: () => void;
  onCreateDirect: () => void;
};

export function useChatsListController({
  featureFlags,
  onOpenCalls,
  onOpenContacts,
  onOpenProfile,
  onOpenSavedMessages,
  onOpenStories,
  onCreateDirect
}: UseChatsListControllerParams) {
  const session = useAppStore((state) => state.session);
  const chats = useAppStore((state) => state.chats);
  const folders = useAppStore((state) => state.folders);
  const appearanceSettings = useAppStore((state) => state.appearanceSettings);
  const chatListState = useAppStore((state) => state.chatListState);
  const setChats = useAppStore((state) => state.setChats);
  const setFolders = useAppStore((state) => state.setFolders);
  const updateChatListState = useAppStore((state) => state.updateChatListState);
  const logout = useAppStore((state) => state.logout);

  const [refreshing, setRefreshing] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [expandedChatId, setExpandedChatId] = useState<string | null>(null);
  const [mutatingChatId, setMutatingChatId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const features = useMemo(() => buildChatsFeatureFlags(featureFlags), [featureFlags]);
  const filterOptions = useMemo(() => buildChatFilterOptions(features), [features]);

  const loadChats = useCallback(
    async (token: string) => {
      if (!session) {
        return;
      }

      setRefreshing(true);
      setError(null);

      try {
        const [nextChats, nextFolders] = await Promise.all([
          api.getChats(token),
          api.getFolders(token)
        ]);

        setChats(nextChats);
        setFolders(nextFolders);

        await Promise.all([
          localDatabase.replaceChats(session.userId, nextChats),
          localDatabase.replaceFolders(session.userId, nextFolders)
        ]);
      } catch (loadError) {
        setError(
          chats.length > 0 || folders.length > 0
            ? "Offline mode. Showing cached chats."
            : loadError instanceof Error
              ? loadError.message
              : "Unable to load chats"
        );
      } finally {
        setRefreshing(false);
      }
    },
    [chats.length, folders.length, session, setChats, setFolders]
  );

  useEffect(() => {
    if (!session) {
      return;
    }

    void Promise.all([
      localDatabase.getChats(session.userId),
      localDatabase.getFolders(session.userId)
    ])
      .then(([cachedChats, cachedFolders]) => {
        if (cachedChats.length > 0) {
          setChats(cachedChats);
        }
        if (cachedFolders.length > 0) {
          setFolders(cachedFolders);
        }
      })
      .catch(() => undefined);

    void loadChats(session.token);
  }, [loadChats, session, setChats, setFolders]);

  const selectedFolderId = chatListState.selectedFolderId;
  const selectedFilter = chatListState.selectedFilter;
  const searchQuery = chatListState.searchQuery;

  const setSelectedFolderId = useCallback(
    (value: string | null) => {
      updateChatListState({ selectedFolderId: value });
    },
    [updateChatListState]
  );

  const setSelectedFilter = useCallback(
    (value: ChatFilter) => {
      updateChatListState({ selectedFilter: value });
    },
    [updateChatListState]
  );

  const setSearchQuery = useCallback(
    (value: string) => {
      updateChatListState({ searchQuery: value });
    },
    [updateChatListState]
  );

  const handleLogout = useCallback(async () => {
    if (!session) {
      return;
    }

    setSigningOut(true);

    try {
      await api.revokeSession(session.token, session.sessionId);
    } catch {
    } finally {
      logout();
      setSigningOut(false);
    }
  }, [logout, session]);

  const handleRefresh = useCallback(async () => {
    if (!session) {
      return;
    }

    await loadChats(session.token);
  }, [loadChats, session]);

  const folderScopedChats = useMemo(() => {
    if (!selectedFolderId) {
      return chats;
    }

    const folder = folders.find((item) => item.folderId === selectedFolderId);
    return chats.filter((chat) => folder?.chatIds.includes(chat.chatId));
  }, [chats, folders, selectedFolderId]);

  const displayedChats = useMemo(
    () =>
      folderScopedChats.filter(
        (chat: ChatSummary) => matchesFilter(chat, selectedFilter) && matchesSearch(chat, searchQuery)
      ),
    [folderScopedChats, searchQuery, selectedFilter]
  );

  const unreadChatsCount = chats.filter((chat) => chat.unreadCount > 0).length;
  const unreadMessagesCount = summarizeUnread(chats);
  const archivedChatsCount = chats.filter((chat) => chat.archived).length;
  const directChatsCount = chats.filter(
    (chat) => chat.chatType === "DIRECT" || chat.chatType === "SAVED"
  ).length;

  const handleToggleChatActions = useCallback((chatId: string) => {
    setExpandedChatId((current) => (current === chatId ? null : chatId));
  }, []);

  const handleChatAction = useCallback(
    async (chat: ChatSummary, action: "ARCHIVE" | "MUTE" | "UNREAD" | "PIN") => {
      if (!session) {
        return;
      }

      setMutatingChatId(chat.chatId);
      setError(null);
      try {
        let updatedChat: ChatSummary;
        switch (action) {
          case "ARCHIVE":
            updatedChat = await api.setChatArchived(session.token, chat.chatId, !chat.archived);
            break;
          case "MUTE":
            updatedChat = await api.muteChat(
              session.token,
              chat.chatId,
              chat.mutedUntil && new Date(chat.mutedUntil).getTime() > Date.now()
                ? null
                : new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString()
            );
            break;
          case "UNREAD":
            updatedChat = await api.markChatUnread(
              session.token,
              chat.chatId,
              !chat.markedUnread
            );
            break;
          case "PIN":
            updatedChat = chat.pinned
              ? await api.unpinChatFromList(session.token, chat.chatId)
              : await api.pinChatToList(session.token, chat.chatId);
            break;
          default:
            updatedChat = chat;
        }

        const nextChats = chats.map((item) =>
          item.chatId === updatedChat.chatId ? updatedChat : item
        );
        setChats(nextChats);
        await localDatabase.replaceChats(session.userId, nextChats);
        setExpandedChatId(null);
      } catch (actionError) {
        setError(actionError instanceof Error ? actionError.message : "Unable to update chat");
      } finally {
        setMutatingChatId(null);
      }
    },
    [chats, session, setChats]
  );

  const quickActions = useMemo<ChatsQuickAction[]>(
    () => [
      {
        key: "saved",
        title: "Saved Messages",
        caption: "Notes, links, drafts",
        onPress: onOpenSavedMessages,
        tone: "brand"
      },
      {
        key: "contacts",
        title: "Contacts",
        caption: "People, bots, search",
        onPress: onOpenContacts
      },
      ...(features.calls
        ? [{
            key: "calls",
            title: "Calls",
            caption: "Recent voice and video",
            onPress: onOpenCalls
          }]
        : []),
      ...(features.stories
        ? [{
            key: "stories",
            title: "Stories",
            caption: "Open feed or post new",
            onPress: onOpenStories
          }]
        : []),
      {
        key: "new-chat",
        title: "New Chat",
        caption: "Start a direct dialog",
        onPress: onCreateDirect,
        tone: "dark"
      },
      {
        key: "more",
        title: "More",
        caption: "Profile and settings",
        onPress: onOpenProfile,
        tone: "warm"
      }
    ],
    [
      features.calls,
      features.stories,
      onCreateDirect,
      onOpenCalls,
      onOpenContacts,
      onOpenProfile,
      onOpenSavedMessages,
      onOpenStories
    ]
  );

  return {
    archivedChatsCount,
    appearanceSettings,
    directChatsCount,
    displayedChats,
    expandedChatId,
    error,
    features,
    filterOptions,
    handleChatAction,
    handleLogout,
    handleRefresh,
    handleToggleChatActions,
    mutatingChatId,
    quickActions,
    refreshing,
    searchQuery,
    selectedFilter,
    selectedFolderId,
    session,
    setSearchQuery,
    setSelectedFilter,
    setSelectedFolderId,
    signingOut,
    unreadChatsCount,
    unreadMessagesCount,
    folders
  };
}

export type ChatsListController = ReturnType<typeof useChatsListController>;
