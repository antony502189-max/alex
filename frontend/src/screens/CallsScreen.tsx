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
import { localDatabase } from "../services/localDatabase";
import type { CallHistoryEntry } from "../types";

type CallsScreenProps = {
  token: string;
  currentUserId: string;
  onClose: () => void;
  onJoinCallLink: (rawToken: string) => void;
  onOpenChat: (chatId: string) => void;
  onCallBack: (chatId: string, kind: "VOICE" | "VIDEO") => void;
};

function formatRelativeDate(value: string) {
  const timestamp = new Date(value);
  return timestamp.toLocaleString();
}

function formatDuration(call: CallHistoryEntry) {
  if (!call.answeredAt || !call.endedAt) {
    return null;
  }
  const durationSeconds = Math.max(
    0,
    Math.floor((new Date(call.endedAt).getTime() - new Date(call.answeredAt).getTime()) / 1000)
  );
  const minutes = Math.floor(durationSeconds / 60);
  const seconds = durationSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

function buildSubtitle(call: CallHistoryEntry) {
  const direction = call.direction === "INCOMING" ? "Incoming" : "Outgoing";
  const kind = call.kind === "VIDEO" ? "video" : "voice";
  if (call.missed) {
    return `Missed ${kind} call`;
  }
  const duration = formatDuration(call);
  if (duration) {
    return `${direction} ${kind} call - ${duration}`;
  }
  return `${direction} ${kind} call`;
}

export function CallsScreen({
  token,
  currentUserId,
  onClose,
  onJoinCallLink,
  onOpenChat,
  onCallBack
}: CallsScreenProps) {
  const [recentCalls, setRecentCalls] = useState<CallHistoryEntry[]>([]);
  const [callLinkToken, setCallLinkToken] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadRecentCalls() {
    setRefreshing(true);
    setError(null);
    try {
      const nextCalls = await api.getRecentCalls(token, 60);
      setRecentCalls(nextCalls);
      await localDatabase.replaceRecentCalls(currentUserId, nextCalls);
    } catch (loadError) {
      setError(
        recentCalls.length > 0
          ? "Offline mode. Showing cached calls."
          : loadError instanceof Error
            ? loadError.message
            : "Unable to load calls"
      );
    } finally {
      setRefreshing(false);
    }
  }

  useEffect(() => {
    void localDatabase.getRecentCalls(currentUserId)
      .then((cachedCalls) => {
        if (cachedCalls.length > 0) {
          setRecentCalls(cachedCalls);
        }
      })
      .catch(() => undefined);

    void loadRecentCalls();
  }, [currentUserId, token]);

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <View style={styles.headerText}>
          <Text style={styles.title}>Calls</Text>
          <Text style={styles.subtitle}>
            {recentCalls.filter((call) => call.missed).length} missed
          </Text>
        </View>
        <Pressable onPress={() => void loadRecentCalls()} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>{refreshing ? "..." : "Refresh"}</Text>
        </Pressable>
      </View>

      {refreshing ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <View style={styles.joinCard}>
        <Text style={styles.joinTitle}>Join by call link</Text>
        <TextInput
          autoCapitalize="none"
          autoCorrect={false}
          onChangeText={setCallLinkToken}
          placeholder="alex://call/token or raw token"
          placeholderTextColor="#94a3b8"
          style={styles.joinInput}
          value={callLinkToken}
        />
        <Pressable
          onPress={() => {
            const normalized = callLinkToken.trim();
            if (!normalized) {
              return;
            }
            onJoinCallLink(normalized);
          }}
          style={styles.joinButton}
        >
          <Text style={styles.joinButtonText}>Join call</Text>
        </Pressable>
      </View>

      <FlatList
        contentContainerStyle={styles.listContent}
        data={recentCalls}
        keyExtractor={(item) => item.callId}
        ListEmptyComponent={
          <Text style={styles.emptyState}>
            No calls yet. Start a voice or video call from any dialog.
          </Text>
        }
        renderItem={({ item }) => {
          return (
            <Pressable
              onPress={() => onOpenChat(item.chatId)}
              style={styles.callCard}
            >
              <Avatar uri={item.photoUrl} title={item.title} size={56} />
              <View style={styles.callBody}>
                <View style={styles.callTopRow}>
                  <Text style={styles.callTitle}>{item.title}</Text>
                  {item.missed ? (
                    <View style={styles.missedBadge}>
                      <Text style={styles.missedBadgeText}>Missed</Text>
                    </View>
                  ) : null}
                </View>
                <Text style={[styles.callMeta, item.missed && styles.callMetaMissed]}>
                  {buildSubtitle(item)}
                </Text>
                <Text style={styles.callMeta}>
                  {item.mode === "GROUP" ? `${item.participantCount} participants` : "Direct"}
                  {" - "}
                  {formatRelativeDate(item.endedAt ?? item.answeredAt ?? item.startedAt)}
                </Text>
              </View>
              <Pressable
                onPress={() => onCallBack(item.chatId, item.kind)}
                style={styles.callBackButton}
              >
                <Text style={styles.callBackButtonText}>
                  {item.kind === "VIDEO" ? "Video" : "Call"}
                </Text>
              </Pressable>
            </Pressable>
          );
        }}
      />
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
    justifyContent: "space-between",
    gap: 12,
    marginBottom: 16
  },
  headerText: {
    flex: 1
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    marginTop: 4,
    color: "#64748b"
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
  loader: {
    marginBottom: 12
  },
  joinCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 10,
    marginBottom: 14
  },
  joinTitle: {
    color: "#0f172a",
    fontWeight: "700",
    fontSize: 16
  },
  joinInput: {
    borderRadius: 14,
    backgroundColor: "#f8fafc",
    borderWidth: 1,
    borderColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: "#0f172a"
  },
  joinButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 12,
    alignItems: "center"
  },
  joinButtonText: {
    color: "#ffffff",
    fontWeight: "700"
  },
  errorText: {
    color: "#b91c1c",
    marginBottom: 12
  },
  listContent: {
    gap: 12,
    paddingBottom: 24
  },
  callCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    flexDirection: "row",
    gap: 14,
    alignItems: "center"
  },
  callBody: {
    flex: 1,
    gap: 4
  },
  callTopRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10
  },
  callTitle: {
    flex: 1,
    color: "#0f172a",
    fontSize: 18,
    fontWeight: "700"
  },
  callMeta: {
    color: "#64748b",
    fontSize: 13
  },
  callMetaMissed: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  missedBadge: {
    borderRadius: 999,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 10,
    paddingVertical: 5
  },
  missedBadgeText: {
    color: "#b91c1c",
    fontWeight: "700",
    fontSize: 12
  },
  callBackButton: {
    borderRadius: 12,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 14,
    paddingVertical: 12
  },
  callBackButtonText: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  emptyState: {
    color: "#64748b",
    lineHeight: 22,
    paddingTop: 24
  }
});
