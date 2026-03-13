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
import type {
  BlockedUser,
  BotSummary,
  Contact,
  ChatSummary,
  UserSearchResult
} from "../types";

type ContactsScreenProps = {
  token: string;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
};

export function ContactsScreen({
  token,
  onClose,
  onOpenChat,
  onOpenBotMiniApp
}: ContactsScreenProps) {
  const [bots, setBots] = useState<BotSummary[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [blockedUsers, setBlockedUsers] = useState<BlockedUser[]>([]);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searching, setSearching] = useState(false);
  const [actionUserKey, setActionUserKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadContacts() {
    setLoading(true);
    setError(null);
    setNotice(null);
    try {
      const [nextContacts, nextBots, nextBlockedUsers] = await Promise.all([
        api.getContacts(token),
        api.getBots(token),
        api.getBlockedUsers(token)
      ]);
      setContacts(nextContacts);
      setBots(nextBots);
      setBlockedUsers(nextBlockedUsers);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load contacts");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadContacts();
  }, [token]);

  useEffect(() => {
    let cancelled = false;
    const normalized = query.trim();
    if (normalized.length < 2) {
      setResults([]);
      setSearching(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      setSearching(true);
      setError(null);
      setNotice(null);
      api.searchUsers(token, normalized)
        .then((nextResults) => {
          if (!cancelled) {
            setResults(nextResults);
          }
        })
        .catch((searchError) => {
          if (!cancelled) {
            setError(searchError instanceof Error ? searchError.message : "Unable to search users");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setSearching(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [query, token]);

  function isBlocked(userId: string) {
    return blockedUsers.some((user) => user.userId === userId);
  }

  async function handleOpenDirect(userId: string) {
    setError(null);
    setNotice(null);
    try {
      const chat = await api.createDirectChat(token, userId);
      onOpenChat(chat);
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open chat");
    }
  }

  async function handleAddContact(user: UserSearchResult) {
    setError(null);
    setNotice(null);
    try {
      setContacts(
        await api.addContact(token, {
          contactUserId: user.userId,
          contactName: user.displayName
        })
      );
      setNotice("Contact added.");
    } catch (addError) {
      setError(addError instanceof Error ? addError.message : "Unable to add contact");
    }
  }

  async function handleRemoveContact(userId: string) {
    setActionUserKey(`remove:${userId}`);
    setError(null);
    setNotice(null);
    try {
      setContacts(await api.removeContact(token, userId));
      setNotice("Contact removed.");
    } catch (removeError) {
      setError(removeError instanceof Error ? removeError.message : "Unable to remove contact");
    } finally {
      setActionUserKey(null);
    }
  }

  async function handleBlockUser(userId: string) {
    setActionUserKey(`block:${userId}`);
    setError(null);
    setNotice(null);
    try {
      setBlockedUsers(await api.blockUser(token, userId));
      setNotice("User blocked.");
    } catch (blockError) {
      setError(blockError instanceof Error ? blockError.message : "Unable to block user");
    } finally {
      setActionUserKey(null);
    }
  }

  async function handleUnblockUser(userId: string) {
    setActionUserKey(`unblock:${userId}`);
    setError(null);
    setNotice(null);
    try {
      setBlockedUsers(await api.unblockUser(token, userId));
      setNotice("User unblocked.");
    } catch (unblockError) {
      setError(unblockError instanceof Error ? unblockError.message : "Unable to unblock user");
    } finally {
      setActionUserKey(null);
    }
  }

  async function handleReportUser(userId: string) {
    setActionUserKey(`report:${userId}`);
    setError(null);
    setNotice(null);
    try {
      await api.reportUser(token, {
        reportedUserId: userId,
        category: "ABUSE"
      });
      setNotice("Report submitted.");
    } catch (reportError) {
      setError(reportError instanceof Error ? reportError.message : "Unable to report user");
    } finally {
      setActionUserKey(null);
    }
  }

  function handleOpenBotMiniApp(botUserId: string, title: string) {
    onOpenBotMiniApp?.(botUserId, title, null, null);
  }

  function renderUserMeta(user: {
    username: string | null;
    phoneNumber: string | null;
    bot: boolean;
    botSupportsInline?: boolean;
    userId: string;
    online: boolean;
    lastSeenAt: string | null;
  }) {
    const presence = user.bot
      ? "bot account"
      : formatPresenceStatus(
          { online: user.online, lastSeenAt: user.lastSeenAt },
          "status hidden"
        );
    return (
      <>
        <Text style={styles.cardMeta}>
          {user.username ? `@${user.username}` : "no username"}
          {user.bot ? " - bot" : ""}
          {user.botSupportsInline ? " - inline" : ""}
          {isBlocked(user.userId) ? " - blocked" : ""}
        </Text>
        <Text style={styles.cardMeta}>
          {[presence, user.phoneNumber ?? "phone hidden"].filter(Boolean).join(" - ")}
        </Text>
      </>
    );
  }

  function renderDefaultHeader() {
    if (bots.length === 0 && blockedUsers.length === 0) {
      return null;
    }

    return (
      <View style={styles.sectionList}>
        {bots.length > 0 ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Bots</Text>
            <View style={styles.sectionList}>
              {bots.map((bot) => (
                <View key={bot.userId} style={styles.card}>
                  <Avatar uri={bot.photoUrl} title={bot.displayName} size={52} />
                  <View style={styles.cardInfo}>
                    <Text style={styles.cardTitle}>{bot.displayName}</Text>
                    <Text style={styles.cardMeta}>
                      @{bot.username} - bot{bot.supportsInline ? " - inline" : ""}
                    </Text>
                    {bot.description ? (
                      <Text style={styles.cardMeta}>{bot.description}</Text>
                    ) : null}
                  </View>
                  <View style={styles.cardActions}>
                    {bot.webAppUrl ? (
                      <Pressable
                        onPress={() => handleOpenBotMiniApp(bot.userId, bot.displayName)}
                        style={styles.inlineButton}
                      >
                        <Text style={styles.inlineButtonText}>Mini App</Text>
                      </Pressable>
                    ) : null}
                    <Pressable onPress={() => void handleOpenDirect(bot.userId)} style={styles.primaryMiniButton}>
                      <Text style={styles.primaryMiniButtonText}>Chat</Text>
                    </Pressable>
                  </View>
                </View>
              ))}
            </View>
          </View>
        ) : null}

        {blockedUsers.length > 0 ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Blocked</Text>
            <View style={styles.sectionList}>
              {blockedUsers.map((user) => (
                <View key={user.userId} style={styles.card}>
                  <Avatar uri={user.photoUrl} title={user.displayName} size={52} />
                  <View style={styles.cardInfo}>
                    <Text style={styles.cardTitle}>{user.displayName}</Text>
                    {renderUserMeta({
                      username: user.username,
                      phoneNumber: user.phoneNumber,
                      bot: user.bot,
                      botSupportsInline: user.botSupportsInline,
                      userId: user.userId,
                      online: user.online,
                      lastSeenAt: user.lastSeenAt
                    })}
                  </View>
                  <View style={styles.cardActions}>
                    <Pressable onPress={() => void handleOpenDirect(user.userId)} style={styles.primaryMiniButton}>
                      <Text style={styles.primaryMiniButtonText}>Chat</Text>
                    </Pressable>
                    <Pressable
                      disabled={actionUserKey === `unblock:${user.userId}`}
                      onPress={() => void handleUnblockUser(user.userId)}
                      style={[styles.inlineButton, actionUserKey === `unblock:${user.userId}` && styles.disabled]}
                    >
                      <Text style={styles.inlineButtonText}>
                        {actionUserKey === `unblock:${user.userId}` ? "..." : "Unblock"}
                      </Text>
                    </Pressable>
                  </View>
                </View>
              ))}
            </View>
          </View>
        ) : null}
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Contacts</Text>
      </View>

      <TextInput
        autoCapitalize="none"
        onChangeText={setQuery}
        placeholder="Search users"
        style={styles.input}
        value={query}
      />

      {loading || searching ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
      {notice ? <Text style={styles.noticeText}>{notice}</Text> : null}

      {query.trim().length >= 2 ? (
        <FlatList
          contentContainerStyle={styles.listContent}
          data={results}
          keyExtractor={(item) => item.userId}
          renderItem={({ item }) => (
            <View style={styles.card}>
              <Avatar uri={item.photoUrl} title={item.displayName} size={52} />
              <View style={styles.cardInfo}>
                <Text style={styles.cardTitle}>{item.displayName}</Text>
                {renderUserMeta(item)}
                {item.botDescription ? (
                  <Text style={styles.cardMeta}>{item.botDescription}</Text>
                ) : null}
              </View>
              <View style={styles.cardActions}>
                {!item.bot ? (
                  <Pressable onPress={() => void handleAddContact(item)} style={styles.inlineButton}>
                    <Text style={styles.inlineButtonText}>Add</Text>
                  </Pressable>
                ) : null}
                {!item.bot ? (
                  <Pressable
                    disabled={actionUserKey === `report:${item.userId}`}
                    onPress={() => void handleReportUser(item.userId)}
                    style={[styles.inlineButton, actionUserKey === `report:${item.userId}` && styles.disabled]}
                  >
                    <Text style={styles.inlineButtonText}>
                      {actionUserKey === `report:${item.userId}` ? "..." : "Report"}
                    </Text>
                  </Pressable>
                ) : null}
                {!item.bot ? (
                  isBlocked(item.userId) ? (
                    <Pressable
                      disabled={actionUserKey === `unblock:${item.userId}`}
                      onPress={() => void handleUnblockUser(item.userId)}
                      style={[styles.inlineButton, actionUserKey === `unblock:${item.userId}` && styles.disabled]}
                    >
                      <Text style={styles.inlineButtonText}>
                        {actionUserKey === `unblock:${item.userId}` ? "..." : "Unblock"}
                      </Text>
                    </Pressable>
                  ) : (
                    <Pressable
                      disabled={actionUserKey === `block:${item.userId}`}
                      onPress={() => void handleBlockUser(item.userId)}
                      style={[styles.dangerMiniButton, actionUserKey === `block:${item.userId}` && styles.disabled]}
                    >
                      <Text style={styles.dangerMiniButtonText}>
                        {actionUserKey === `block:${item.userId}` ? "..." : "Block"}
                      </Text>
                    </Pressable>
                  )
                ) : null}
                {item.bot && item.botWebAppUrl ? (
                  <Pressable
                    onPress={() => handleOpenBotMiniApp(item.userId, item.displayName)}
                    style={styles.inlineButton}
                  >
                    <Text style={styles.inlineButtonText}>Mini App</Text>
                  </Pressable>
                ) : null}
                <Pressable onPress={() => void handleOpenDirect(item.userId)} style={styles.primaryMiniButton}>
                  <Text style={styles.primaryMiniButtonText}>Chat</Text>
                </Pressable>
              </View>
            </View>
          )}
        />
      ) : (
        <FlatList
          contentContainerStyle={styles.listContent}
          data={contacts}
          keyExtractor={(item) => item.userId}
          ListHeaderComponent={renderDefaultHeader()}
          ListEmptyComponent={<Text style={styles.emptyState}>No contacts yet.</Text>}
          renderItem={({ item }) => (
            <View style={styles.card}>
              <Avatar uri={item.photoUrl} title={item.contactName} size={52} />
              <View style={styles.cardInfo}>
                <Text style={styles.cardTitle}>{item.contactName}</Text>
                {renderUserMeta({
                  username: item.username,
                  phoneNumber: item.phoneNumber,
                  bot: item.bot,
                  botSupportsInline: item.botSupportsInline,
                  userId: item.userId,
                  online: item.online,
                  lastSeenAt: item.lastSeenAt
                })}
                {item.botDescription ? (
                  <Text style={styles.cardMeta}>{item.botDescription}</Text>
                ) : null}
              </View>
              <View style={styles.cardActions}>
                {item.bot && item.botWebAppUrl ? (
                  <Pressable
                    onPress={() => handleOpenBotMiniApp(item.userId, item.displayName)}
                    style={styles.inlineButton}
                  >
                    <Text style={styles.inlineButtonText}>Mini App</Text>
                  </Pressable>
                ) : null}
                <Pressable onPress={() => void handleOpenDirect(item.userId)} style={styles.primaryMiniButton}>
                  <Text style={styles.primaryMiniButtonText}>Chat</Text>
                </Pressable>
                {!item.bot ? (
                  isBlocked(item.userId) ? (
                    <Pressable
                      disabled={actionUserKey === `unblock:${item.userId}`}
                      onPress={() => void handleUnblockUser(item.userId)}
                      style={[styles.inlineButton, actionUserKey === `unblock:${item.userId}` && styles.disabled]}
                    >
                      <Text style={styles.inlineButtonText}>
                        {actionUserKey === `unblock:${item.userId}` ? "..." : "Unblock"}
                      </Text>
                    </Pressable>
                  ) : (
                    <Pressable
                      disabled={actionUserKey === `block:${item.userId}`}
                      onPress={() => void handleBlockUser(item.userId)}
                      style={[styles.dangerMiniButton, actionUserKey === `block:${item.userId}` && styles.disabled]}
                    >
                      <Text style={styles.dangerMiniButtonText}>
                        {actionUserKey === `block:${item.userId}` ? "..." : "Block"}
                      </Text>
                    </Pressable>
                  )
                ) : null}
                <Pressable
                  disabled={actionUserKey === `remove:${item.userId}`}
                  onPress={() => void handleRemoveContact(item.userId)}
                  style={[styles.dangerMiniButton, actionUserKey === `remove:${item.userId}` && styles.disabled]}
                >
                  <Text style={styles.dangerMiniButtonText}>
                    {actionUserKey === `remove:${item.userId}` ? "..." : "Remove"}
                  </Text>
                </Pressable>
              </View>
            </View>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#f8fafc", padding: 20 },
  header: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 16 },
  title: { fontSize: 24, fontWeight: "700", color: "#0f172a" },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff",
    marginBottom: 12
  },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: { color: "#0f172a", fontWeight: "600" },
  loader: { marginBottom: 12 },
  errorText: { color: "#b91c1c", marginBottom: 12 },
  noticeText: { color: "#0f766e", marginBottom: 12, fontWeight: "600" },
  listContent: { gap: 12, paddingBottom: 20 },
  section: { gap: 12 },
  sectionTitle: { color: "#0f172a", fontSize: 18, fontWeight: "700" },
  sectionList: { gap: 12 },
  card: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    flexDirection: "row",
    gap: 12,
    alignItems: "center"
  },
  cardInfo: { flex: 1 },
  cardTitle: { fontSize: 18, fontWeight: "600", color: "#0f172a" },
  cardMeta: { color: "#64748b", marginTop: 3 },
  cardActions: { gap: 8 },
  inlineButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 10,
    alignItems: "center"
  },
  inlineButtonText: { color: "#0f172a", fontWeight: "600" },
  primaryMiniButton: {
    borderRadius: 12,
    backgroundColor: "#0f172a",
    paddingHorizontal: 12,
    paddingVertical: 10,
    alignItems: "center"
  },
  primaryMiniButtonText: { color: "#ffffff", fontWeight: "600" },
  dangerMiniButton: {
    borderRadius: 12,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 12,
    paddingVertical: 10,
    alignItems: "center"
  },
  dangerMiniButtonText: { color: "#b91c1c", fontWeight: "600" },
  emptyState: { color: "#64748b", paddingTop: 24 },
  disabled: { opacity: 0.6 }
});
