import React, { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import { localDatabase } from "../services/localDatabase";
import { useAppStore } from "../store/useAppStore";

type FoldersScreenProps = {
  token: string;
  onClose: () => void;
};

export function FoldersScreen({ token, onClose }: FoldersScreenProps) {
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

    void localDatabase.getFolders(session.userId)
      .then((cachedFolders) => {
        if (cachedFolders.length > 0) {
          setFolders(cachedFolders);
        }
      })
      .catch(() => undefined);

    void loadFolders();
  }, [session, setFolders, token]);

  function formatChatMeta(chat: (typeof chats)[number]) {
    if (chat.chatType === "DIRECT") {
      return chat.peerPhoneNumber ?? "phone-hidden";
    }
    if (chat.chatType === "SAVED") {
      return "private notes";
    }

    const parts = [
      `${chat.memberCount} members`,
      chat.forumEnabled ? `${chat.topicCount} topic${chat.topicCount === 1 ? "" : "s"}` : null
    ].filter(Boolean);

    return parts.join(" - ");
  }

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

  function toggleChat(chatId: string) {
    setSelectedChatIds((current) =>
      current.includes(chatId)
        ? current.filter((id) => id !== chatId)
        : [...current, chatId]
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
            title: title.trim(),
            chatIds: selectedChatIds
          })
        : await api.createFolder(token, {
            title: title.trim(),
            position: folders.length,
            chatIds: selectedChatIds
          });

      const nextFolders = selectedFolderId
        ? folders.map((folder) => (folder.folderId === saved.folderId ? saved : folder))
        : [...folders, saved];

      const sortedFolders = nextFolders.sort((left, right) => left.position - right.position);
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

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Folders</Text>
      </View>

      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <View style={styles.folderStrip}>
        <Pressable
          onPress={() => setSelectedFolderId(null)}
          style={[styles.folderChip, !selectedFolderId && styles.folderChipActive]}
        >
          <Text style={[styles.folderChipText, !selectedFolderId && styles.folderChipTextActive]}>
            New folder
          </Text>
        </Pressable>
        {folders.map((folder) => (
          <Pressable
            key={folder.folderId}
            onPress={() => setSelectedFolderId(folder.folderId)}
            style={[styles.folderChip, selectedFolderId === folder.folderId && styles.folderChipActive]}
          >
            <Text
              style={[
                styles.folderChipText,
                selectedFolderId === folder.folderId && styles.folderChipTextActive
              ]}
            >
              {folder.title}
            </Text>
          </Pressable>
        ))}
      </View>

      <TextInput
        onChangeText={setTitle}
        placeholder="Folder title"
        style={styles.input}
        value={title}
      />

      <FlatList
        contentContainerStyle={styles.listContent}
        data={chats}
        keyExtractor={(item) => item.chatId}
        renderItem={({ item }) => {
          const selected = selectedChatIds.includes(item.chatId);
          return (
            <Pressable
              onPress={() => toggleChat(item.chatId)}
              style={[styles.chatCard, selected && styles.chatCardSelected]}
            >
              <Avatar uri={item.photoUrl} title={item.title} size={44} />
              <View style={styles.chatBody}>
                <View style={styles.chatTopRow}>
                  <Text style={styles.chatTitle}>{item.title}</Text>
                  {item.forumEnabled ? (
                    <View style={styles.forumBadge}>
                      <Text style={styles.forumBadgeText}>Forum</Text>
                    </View>
                  ) : null}
                </View>
                <Text style={styles.chatMeta}>{formatChatMeta(item)}</Text>
              </View>
            </Pressable>
          );
        }}
      />

      <View style={styles.actions}>
        <Pressable
          disabled={saving || !title.trim()}
          onPress={() => void handleSave()}
          style={[styles.primaryButton, (saving || !title.trim()) && styles.disabled]}
        >
          <Text style={styles.primaryButtonText}>{saving ? "Saving..." : "Save folder"}</Text>
        </Pressable>
        {selectedFolderId ? (
          <Pressable
            disabled={saving}
            onPress={() => void handleDelete()}
            style={[styles.dangerButton, saving && styles.disabled]}
          >
            <Text style={styles.dangerButtonText}>Delete folder</Text>
          </Pressable>
        ) : null}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#f8fafc", padding: 20 },
  header: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 16 },
  title: { fontSize: 24, fontWeight: "700", color: "#0f172a" },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: { color: "#0f172a", fontWeight: "600" },
  loader: { marginBottom: 12 },
  errorText: { color: "#b91c1c", marginBottom: 12 },
  folderStrip: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 },
  folderChip: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  folderChipActive: { backgroundColor: "#0f172a" },
  folderChipText: { color: "#0f172a", fontWeight: "600" },
  folderChipTextActive: { color: "#ffffff" },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff",
    marginBottom: 12
  },
  listContent: { gap: 10, paddingBottom: 20 },
  chatCard: {
    borderRadius: 16,
    backgroundColor: "#ffffff",
    padding: 14,
    flexDirection: "row",
    gap: 12,
    alignItems: "center"
  },
  chatCardSelected: {
    borderWidth: 2,
    borderColor: "#0f172a"
  },
  chatBody: { flex: 1 },
  chatTopRow: {
    flexDirection: "row",
    alignItems: "center",
    flexWrap: "wrap",
    gap: 8
  },
  chatTitle: { color: "#0f172a", fontWeight: "700" },
  chatMeta: { color: "#64748b", marginTop: 4 },
  forumBadge: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 8,
    paddingVertical: 4
  },
  forumBadgeText: {
    color: "#1d4ed8",
    fontSize: 11,
    fontWeight: "700"
  },
  actions: { gap: 10, marginTop: 12 },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: { color: "#ffffff", fontWeight: "600" },
  dangerButton: {
    borderRadius: 14,
    backgroundColor: "#fee2e2",
    paddingVertical: 14,
    alignItems: "center"
  },
  dangerButtonText: { color: "#b91c1c", fontWeight: "600" },
  disabled: { opacity: 0.6 }
});
