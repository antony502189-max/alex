import React, { useEffect, useState } from "react";
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
import { formatPresenceStatus } from "../services/presence";
import type { ChatSummary, UserSearchResult } from "../types";

type CreateChatScreenProps = {
  mode: "direct" | "group" | "channel";
  token: string;
  onClose: () => void;
  onCreated: (chat: ChatSummary) => void;
};

export function CreateChatScreen({
  mode,
  token,
  onClose,
  onCreated
}: CreateChatScreenProps) {
  const [query, setQuery] = useState("");
  const [groupTitle, setGroupTitle] = useState("");
  const [groupAbout, setGroupAbout] = useState("");
  const [autoDeleteSeconds, setAutoDeleteSeconds] = useState("");
  const [forumEnabled, setForumEnabled] = useState(false);
  const [joinRequiresApproval, setJoinRequiresApproval] = useState(false);
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (mode !== "group") {
      setForumEnabled(false);
    }
    if (mode === "direct") {
      setJoinRequiresApproval(false);
    }
  }, [mode]);

  useEffect(() => {
    let cancelled = false;

    async function search() {
      const normalized = query.trim();
      if (normalized.length < 2) {
        setResults([]);
        return;
      }

      setLoading(true);
      setError(null);

      try {
        const nextResults = await api.searchUsers(token, normalized);
        if (!cancelled) {
          setResults(nextResults);
        }
      } catch (searchError) {
        if (!cancelled) {
          setError(searchError instanceof Error ? searchError.message : "Unable to search users");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void search();

    return () => {
      cancelled = true;
    };
  }, [query, token]);

  function toggleUser(userId: string) {
    setSelectedUserIds((current) =>
      current.includes(userId)
        ? current.filter((id) => id !== userId)
        : [...current, userId]
    );
  }

  async function handleSelectDirect(userId: string) {
    setSubmitting(true);
    setError(null);
    try {
      const chat = await api.createDirectChat(token, userId);
      onCreated(chat);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Unable to create chat");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCreateCollectionChat() {
    if (!groupTitle.trim()) {
      return;
    }

    if (mode === "group" && selectedUserIds.length === 0) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const chat =
        mode === "channel"
          ? await api.createChannel(token, {
              title: groupTitle.trim(),
              about: groupAbout.trim() || undefined,
              autoDeleteSeconds: autoDeleteSeconds.trim()
                ? Number.parseInt(autoDeleteSeconds.trim(), 10)
                : undefined,
              joinRequiresApproval,
              subscriberIds: selectedUserIds
            })
          : await api.createGroupChat(token, {
              title: groupTitle.trim(),
              about: groupAbout.trim() || undefined,
              autoDeleteSeconds: autoDeleteSeconds.trim()
                ? Number.parseInt(autoDeleteSeconds.trim(), 10)
                : undefined,
              forumEnabled,
              joinRequiresApproval,
              memberIds: selectedUserIds
            });
      onCreated(chat);
    } catch (createError) {
      setError(
        createError instanceof Error
          ? createError.message
          : mode === "channel"
            ? "Unable to create channel"
            : "Unable to create group"
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <View>
          <Text style={styles.title}>
            {mode === "group"
              ? "New group"
              : mode === "channel"
                ? "New channel"
                : "New direct chat"}
          </Text>
          <Text style={styles.subtitle}>Search by phone or display name</Text>
        </View>
      </View>

      {mode !== "direct" ? (
        <View>
          <TextInput
            onChangeText={setGroupTitle}
            placeholder={mode === "channel" ? "Channel title" : "Group title"}
            style={styles.input}
            value={groupTitle}
          />
          <TextInput
            multiline
            onChangeText={setGroupAbout}
            placeholder={mode === "channel" ? "Channel description" : "Group description"}
            style={[styles.input, styles.aboutInput]}
            value={groupAbout}
          />
          <TextInput
            keyboardType="number-pad"
            onChangeText={setAutoDeleteSeconds}
            placeholder="Auto-delete seconds (optional)"
            style={styles.input}
            value={autoDeleteSeconds}
          />
          {mode === "group" ? (
            <Pressable
              onPress={() => setForumEnabled((current) => !current)}
              style={[styles.toggleCard, forumEnabled && styles.toggleCardActive]}
            >
              <View style={styles.toggleBody}>
                <Text style={styles.toggleTitle}>Enable topics</Text>
                <Text style={styles.toggleHint}>
                  Split the group into Telegram-style forum threads.
                </Text>
              </View>
              <View style={[styles.toggleBadge, forumEnabled && styles.toggleBadgeActive]}>
                <Text style={[styles.toggleBadgeText, forumEnabled && styles.toggleBadgeTextActive]}>
                  {forumEnabled ? "ON" : "OFF"}
                </Text>
              </View>
            </Pressable>
          ) : null}
          <Pressable
            onPress={() => setJoinRequiresApproval((current) => !current)}
            style={[styles.toggleCard, joinRequiresApproval && styles.toggleCardActive]}
          >
            <View style={styles.toggleBody}>
              <Text style={styles.toggleTitle}>Join requests</Text>
              <Text style={styles.toggleHint}>
                New members will wait for admin approval before entering this chat.
              </Text>
            </View>
            <View style={[styles.toggleBadge, joinRequiresApproval && styles.toggleBadgeActive]}>
              <Text
                style={[
                  styles.toggleBadgeText,
                  joinRequiresApproval && styles.toggleBadgeTextActive
                ]}
              >
                {joinRequiresApproval ? "ON" : "OFF"}
              </Text>
            </View>
          </Pressable>
        </View>
      ) : null}

      <TextInput
        autoCapitalize="none"
        onChangeText={setQuery}
        placeholder="Search users"
        style={styles.input}
        value={query}
      />

      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={results}
        keyExtractor={(item) => item.userId}
        ListEmptyComponent={
          <Text style={styles.emptyState}>
            Start typing at least two characters to find users.
          </Text>
        }
        renderItem={({ item }) => {
          const selected = selectedUserIds.includes(item.userId);
          return (
            <Pressable
              onPress={() =>
                mode === "direct" ? void handleSelectDirect(item.userId) : toggleUser(item.userId)
              }
              style={[styles.resultCard, selected && styles.resultCardSelected]}
            >
              <Avatar uri={item.photoUrl} title={item.displayName} size={48} />
              <View style={styles.resultBody}>
                <Text style={styles.resultName}>{item.displayName}</Text>
                <Text style={styles.resultMeta}>
                  {[
                    item.bot
                      ? "bot"
                      : formatPresenceStatus(
                          { online: item.online, lastSeenAt: item.lastSeenAt },
                          "status hidden"
                        ),
                    item.phoneNumber ?? "phone hidden"
                  ]
                    .filter(Boolean)
                    .join(" - ")}
                </Text>
              </View>
            </Pressable>
          );
        }}
      />

      {mode !== "direct" ? (
        <Pressable
          disabled={
            submitting ||
            !groupTitle.trim() ||
            (mode === "group" && selectedUserIds.length === 0)
          }
          onPress={handleCreateCollectionChat}
          style={[
            styles.primaryButton,
            (submitting ||
              !groupTitle.trim() ||
              (mode === "group" && selectedUserIds.length === 0)) &&
              styles.buttonDisabled
          ]}
        >
          <Text style={styles.primaryButtonText}>
            {submitting
              ? "Creating..."
              : mode === "channel"
                ? `Create channel (${selectedUserIds.length})`
                : `Create group (${selectedUserIds.length})`}
          </Text>
        </Pressable>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f8fafc",
    padding: 20
  },
  header: {
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
    marginBottom: 16
  },
  title: {
    fontSize: 24,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    marginTop: 4,
    fontSize: 14,
    color: "#475569"
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff",
    marginBottom: 12
  },
  aboutInput: {
    minHeight: 84,
    textAlignVertical: "top"
  },
  toggleCard: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 16,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 12
  },
  toggleCardActive: {
    borderColor: "#0f172a",
    backgroundColor: "#e2e8f0"
  },
  toggleBody: {
    flex: 1,
    gap: 4
  },
  toggleTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  toggleHint: {
    color: "#475569",
    lineHeight: 18
  },
  toggleBadge: {
    minWidth: 48,
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 10,
    paddingVertical: 6,
    alignItems: "center"
  },
  toggleBadgeActive: {
    backgroundColor: "#0f172a"
  },
  toggleBadgeText: {
    color: "#0f172a",
    fontWeight: "700",
    fontSize: 12
  },
  toggleBadgeTextActive: {
    color: "#ffffff"
  },
  loader: {
    marginBottom: 12
  },
  errorText: {
    color: "#b91c1c",
    marginBottom: 12
  },
  listContent: {
    gap: 12,
    paddingBottom: 20
  },
  resultCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 12,
    flexDirection: "row",
    alignItems: "center"
  },
  resultCardSelected: {
    borderWidth: 2,
    borderColor: "#0f172a"
  },
  resultBody: { flex: 1, gap: 4 },
  resultName: {
    fontSize: 18,
    fontWeight: "600",
    color: "#0f172a"
  },
  resultMeta: {
    fontSize: 13,
    color: "#64748b"
  },
  emptyState: {
    color: "#64748b",
    lineHeight: 22,
    paddingTop: 24
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center",
    marginTop: 12
  },
  primaryButtonText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "600"
  },
  secondaryButton: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
    backgroundColor: "#e2e8f0"
  },
  secondaryButtonText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  buttonDisabled: {
    opacity: 0.6
  }
});
