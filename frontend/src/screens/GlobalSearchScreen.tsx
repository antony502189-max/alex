import React, { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import { formatPresenceStatus } from "../services/presence";
import type { ChatSummary, GlobalMessageSearchResult, GlobalSearchResponse, UserSearchResult } from "../types";

type GlobalSearchScreenProps = {
  token: string;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenMessageResult: (chat: ChatSummary, message: GlobalMessageSearchResult["message"]) => void;
};

export function GlobalSearchScreen({
  token,
  onClose,
  onOpenChat,
  onOpenMessageResult
}: GlobalSearchScreenProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<GlobalSearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [openingUserId, setOpeningUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const normalizedQuery = query.trim();
  const hasResults = Boolean(
    results &&
      (results.users.length > 0 || results.chats.length > 0 || results.messages.length > 0)
  );

  useEffect(() => {
    let cancelled = false;
    if (normalizedQuery.length < 2) {
      setResults(null);
      setLoading(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      setLoading(true);
      setError(null);
      api.searchGlobal(token, normalizedQuery, 12)
        .then((nextResults) => {
          if (!cancelled) {
            setResults(nextResults);
          }
        })
        .catch((searchError) => {
          if (!cancelled) {
            setError(searchError instanceof Error ? searchError.message : "Unable to run global search");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setLoading(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [normalizedQuery, token]);

  const resultSummary = useMemo(() => {
    if (!results) {
      return null;
    }
    const total = results.users.length + results.chats.length + results.messages.length;
    return `${total} result${total === 1 ? "" : "s"}`;
  }, [results]);

  async function handleOpenUser(user: UserSearchResult) {
    setOpeningUserId(user.userId);
    setError(null);
    try {
      const chat = await api.createDirectChat(token, user.userId);
      onOpenChat(chat);
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open chat");
    } finally {
      setOpeningUserId(null);
    }
  }

  function describeChat(chat: ChatSummary) {
    if (chat.chatType === "DIRECT") {
      return [
        chat.peerIsBot
          ? "bot"
          : formatPresenceStatus(
              { online: chat.peerOnline, lastSeenAt: chat.peerLastSeenAt },
              "status hidden"
            ),
        chat.peerPhoneNumber ?? chat.peerDisplayName ?? "Direct chat"
      ]
        .filter(Boolean)
        .join(" - ");
    }
    if (chat.chatType === "SAVED") {
      return "Private notes";
    }
    const parts = [
      chat.publicUsername ? `@${chat.publicUsername}` : null,
      `${chat.memberCount} members`,
      chat.forumEnabled ? `${chat.topicCount} topics` : null
    ].filter(Boolean);
    return parts.join(" - ");
  }

  function describeMessage(result: GlobalMessageSearchResult) {
    const message = result.message;
    if (message.serviceMessage?.text) {
      return message.serviceMessage.text;
    }
    if (message.messageType === "LOCATION") {
      const title = message.location?.title?.trim();
      const address = message.location?.address?.trim();
      if (title && address) {
        return `${title} - ${address}`;
      }
      if (title) {
        return title;
      }
      if (address) {
        return address;
      }
      return "Location";
    }
    if (message.messageType === "CONTACT_CARD") {
      const fullName = [message.contactCard?.firstName, message.contactCard?.lastName]
        .filter((part) => !!part)
        .join(" ")
        .trim();
      return fullName || message.contactCard?.phoneNumber || "Contact";
    }
    if (message.poll) {
      return `Poll: ${message.poll.question}`;
    }
    if (message.sticker) {
      return `Sticker: ${message.sticker.emoji} ${message.sticker.label}`;
    }
    if (message.caption) {
      return message.caption;
    }
    if (message.text) {
      return message.text;
    }
    if (message.attachments.length > 0) {
      return message.attachments.length > 1
        ? "Attachment album"
        : message.attachments[0].kind.toLowerCase().replace("_", " ");
    }
    return "Message";
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Global search</Text>
      </View>

      <TextInput
        autoCapitalize="none"
        onChangeText={setQuery}
        placeholder="Search chats, people, messages"
        style={styles.input}
        value={query}
      />

      {normalizedQuery.length >= 2 ? (
        <View style={styles.infoBar}>
          <Text style={styles.infoText}>
            {loading ? "Searching..." : resultSummary ?? `No results for "${normalizedQuery}"`}
          </Text>
        </View>
      ) : (
        <Text style={styles.hintText}>
          Type at least 2 characters to search across users, chats, and messages.
        </Text>
      )}

      {error ? <Text style={styles.errorText}>{error}</Text> : null}
      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}

      <ScrollView contentContainerStyle={styles.content}>
        {results?.users.length ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>People</Text>
            <View style={styles.sectionList}>
              {results.users.map((user) => (
                <View key={user.userId} style={styles.card}>
                  <Avatar uri={user.photoUrl} size={52} title={user.displayName} />
                  <View style={styles.cardBody}>
                    <Text style={styles.cardTitle}>{user.displayName}</Text>
                    <Text style={styles.cardMeta}>
                      {user.username ? `@${user.username}` : "no username"}
                      {user.bot ? " - bot" : ""}
                    </Text>
                    <Text style={styles.cardMeta}>
                      {[
                        user.bot
                          ? "bot account"
                          : formatPresenceStatus(
                              { online: user.online, lastSeenAt: user.lastSeenAt },
                              "status hidden"
                            ),
                        user.phoneNumber ?? "phone hidden"
                      ]
                        .filter(Boolean)
                        .join(" - ")}
                    </Text>
                  </View>
                  <Pressable
                    disabled={openingUserId === user.userId}
                    onPress={() => void handleOpenUser(user)}
                    style={[styles.primaryMiniButton, openingUserId === user.userId && styles.disabled]}
                  >
                    <Text style={styles.primaryMiniButtonText}>
                      {openingUserId === user.userId ? "..." : "Chat"}
                    </Text>
                  </Pressable>
                </View>
              ))}
            </View>
          </View>
        ) : null}

        {results?.chats.length ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Chats</Text>
            <View style={styles.sectionList}>
              {results.chats.map((chat) => (
                <Pressable
                  key={chat.chatId}
                  onPress={() => onOpenChat(chat)}
                  style={styles.card}
                >
                  <Avatar uri={chat.photoUrl} size={52} title={chat.title} />
                  <View style={styles.cardBody}>
                    <Text style={styles.cardTitle}>{chat.title}</Text>
                    <Text style={styles.cardMeta}>{describeChat(chat)}</Text>
                    {chat.about ? (
                      <Text numberOfLines={1} style={styles.cardMeta}>{chat.about}</Text>
                    ) : null}
                  </View>
                </Pressable>
              ))}
            </View>
          </View>
        ) : null}

        {results?.messages.length ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Messages</Text>
            <View style={styles.sectionList}>
              {results.messages.map((result) => (
                <Pressable
                  key={`${result.chat.chatId}:${result.message.messageId}`}
                  onPress={() => onOpenMessageResult(result.chat, result.message)}
                  style={styles.card}
                >
                  <Avatar uri={result.chat.photoUrl} size={52} title={result.chat.title} />
                  <View style={styles.cardBody}>
                    <Text style={styles.cardTitle}>{result.chat.title}</Text>
                    <Text numberOfLines={2} style={styles.messageSnippet}>
                      {describeMessage(result)}
                    </Text>
                    <Text style={styles.cardMeta}>
                      {new Date(result.message.createdAt).toLocaleString()}
                    </Text>
                  </View>
                  <View style={styles.messageActionBadge}>
                    <Text style={styles.messageActionBadgeText}>Jump</Text>
                  </View>
                </Pressable>
              ))}
            </View>
          </View>
        ) : null}

        {normalizedQuery.length >= 2 && !loading && !hasResults ? (
          <Text style={styles.emptyState}>No matches found.</Text>
        ) : null}
      </ScrollView>
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
    alignItems: "center",
    gap: 12,
    marginBottom: 16
  },
  title: {
    fontSize: 24,
    fontWeight: "700",
    color: "#0f172a"
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff"
  },
  infoBar: {
    marginTop: 12,
    borderRadius: 14,
    backgroundColor: "#ecfeff",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  infoText: {
    color: "#155e75",
    fontWeight: "600"
  },
  hintText: {
    marginTop: 12,
    color: "#64748b"
  },
  errorText: {
    marginTop: 12,
    color: "#b91c1c"
  },
  loader: {
    marginTop: 12
  },
  content: {
    gap: 16,
    paddingTop: 16,
    paddingBottom: 32
  },
  section: {
    gap: 10
  },
  sectionTitle: {
    color: "#0f172a",
    fontSize: 18,
    fontWeight: "700"
  },
  sectionList: {
    gap: 10
  },
  card: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 12
  },
  cardBody: {
    flex: 1,
    gap: 4
  },
  cardTitle: {
    fontSize: 17,
    fontWeight: "600",
    color: "#0f172a"
  },
  cardMeta: {
    color: "#64748b"
  },
  messageSnippet: {
    color: "#334155"
  },
  messageActionBadge: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 10,
    paddingVertical: 8
  },
  messageActionBadgeText: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  primaryMiniButton: {
    borderRadius: 12,
    backgroundColor: "#0f172a",
    paddingHorizontal: 12,
    paddingVertical: 10,
    alignItems: "center"
  },
  primaryMiniButtonText: {
    color: "#ffffff",
    fontWeight: "600"
  },
  emptyState: {
    color: "#64748b",
    paddingTop: 8
  },
  disabled: {
    opacity: 0.6
  }
});
