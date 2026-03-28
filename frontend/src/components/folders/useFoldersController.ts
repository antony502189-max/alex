import { useEffect, useMemo, useState } from "react";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import { useAppStore } from "../../store/useAppStore";
import { sortFoldersByPosition } from "./foldersPresentation";

type UseFoldersControllerParams = {
  token: string;
};

export function useFoldersController({ token }: UseFoldersControllerParams) {
  const session = useAppStore((state) => state.session);
  const chats = useAppStore((state) => state.chats);
  const folders = useAppStore((state) => state.folders);
  const setFolders = useAppStore((state) => state.setFolders);

  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [selectedChatIds, setSelectedChatIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadFolders() {
    if (!session) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const nextFolders = await api.getFolders(token);
      setFolders(nextFolders);
      await localDatabase.replaceFolders(session.userId, nextFolders);
    } catch (loadError) {
      setError(
        folders.length > 0
          ? "Offline mode. Showing cached folders."
          : loadError instanceof Error
            ? loadError.message
            : "Unable to load folders"
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (!session) {
      return;
    }

    void localDatabase
      .getFolders(session.userId)
      .then((cachedFolders) => {
        if (cachedFolders.length > 0) {
          setFolders(cachedFolders);
        }
      })
      .catch(() => undefined);

    void loadFolders();
  }, [session, setFolders, token]);

  const selectedFolder = useMemo(
    () => folders.find((folder) => folder.folderId === selectedFolderId) ?? null,
    [folders, selectedFolderId]
  );

  useEffect(() => {
    if (!selectedFolder) {
      setTitle("");
      setSelectedChatIds([]);
      return;
    }
    setTitle(selectedFolder.title);
    setSelectedChatIds(selectedFolder.chatIds);
  }, [selectedFolder]);

  function handleSelectFolder(folderId: string | null) {
    setSelectedFolderId(folderId);
    setError(null);
  }

  function toggleChat(chatId: string) {
    setSelectedChatIds((current) =>
      current.includes(chatId) ? current.filter((id) => id !== chatId) : [...current, chatId]
    );
  }

  async function handleSave() {
    if (!title.trim()) {
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const saved = selectedFolderId
        ? await api.updateFolder(token, selectedFolderId, {
            chatIds: selectedChatIds,
            title: title.trim()
          })
        : await api.createFolder(token, {
            chatIds: selectedChatIds,
            position: folders.length,
            title: title.trim()
          });

      const nextFolders = selectedFolderId
        ? folders.map((folder) => (folder.folderId === saved.folderId ? saved : folder))
        : [...folders, saved];

      const sortedFolders = sortFoldersByPosition(nextFolders);
      setFolders(sortedFolders);
      if (session) {
        await localDatabase.replaceFolders(session.userId, sortedFolders);
      }
      setSelectedFolderId(saved.folderId);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to save folder");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!selectedFolderId) {
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const nextFolders = await api.deleteFolder(token, selectedFolderId);
      setFolders(nextFolders);
      if (session) {
        await localDatabase.replaceFolders(session.userId, nextFolders);
      }
      setSelectedFolderId(null);
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Unable to delete folder");
    } finally {
      setSaving(false);
    }
  }

  return {
    chats,
    error,
    folders,
    handleDelete,
    handleSave,
    handleSelectFolder,
    loading,
    saving,
    selectedChatIds,
    selectedFolderId,
    setTitle,
    title,
    toggleChat,
    canSave: Boolean(title.trim())
  };
}

export type FoldersScreenController = ReturnType<typeof useFoldersController>;
