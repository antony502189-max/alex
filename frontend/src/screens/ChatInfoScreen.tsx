import React, { useEffect, useMemo, useState } from "react";
import { Share, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../components/Avatar";
import { AppActionTile } from "../components/ui/AppActionTile";
import { AppHeader } from "../components/ui/AppHeader";
import { AppPanel } from "../components/ui/AppPanel";
import { AppScreen } from "../components/ui/AppScreen";
import { AppTextField } from "../components/ui/AppTextField";
import { ScreenFeedback } from "../components/ui/ScreenFeedback";
import { ScreenScrollView } from "../components/ui/ScreenScrollView";
import { api } from "../services/api";
import { canStartCallsFromChat } from "../services/chatCapabilities";
import { buildPublicChatShareUrl } from "../services/chatLinks";
import { appColors, appSpacing } from "../theme/tokens";
import type { BotCommand, ChatSummary } from "../types";
import { getChatInfoPresentation } from "./chatInfoPresentation";

type ChatInfoScreenProps = {
  chat: ChatSummary;
  currentUserId: string;
  token: string;
  onChatUpdated?: (chat: ChatSummary) => void;
  onChatLeft?: (chatId: string) => void;
  onClose: () => void;
  onHistoryCleared?: (chatId: string) => void;
  onOpenDiscussionChat?: (chatId: string) => void;
  onOpenMembers?: (chat: ChatSummary) => void;
  onOpenBotMiniApp?: (
    botUserId: string,
    title: string,
    chatId?: string | null,
    startParameter?: string | null
  ) => void;
  onStartCall?: (kind: "VOICE" | "VIDEO") => void;
  onOpenSharedMedia?: (chat: ChatSummary) => void;
};

const MUTE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;

function describeError(error: unknown) {
  return error instanceof Error ? error.message : "Request failed";
}

export function ChatInfoScreen({
  chat,
  currentUserId,
  token,
  onChatUpdated,
  onChatLeft,
  onClose,
  onHistoryCleared,
  onOpenDiscussionChat,
  onOpenMembers,
  onOpenBotMiniApp,
  onStartCall,
  onOpenSharedMedia
}: ChatInfoScreenProps) {
  const [blocked, setBlocked] = useState(false);
  const [loadingBlocked, setLoadingBlocked] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reportDetails, setReportDetails] = useState("");
  const [botCommands, setBotCommands] = useState<BotCommand[]>([]);
  const [botCommandsError, setBotCommandsError] = useState<string | null>(null);
  const [loadingBotCommands, setLoadingBotCommands] = useState(false);
  const [botCommandsReloadKey, setBotCommandsReloadKey] = useState(0);

  const presentation = useMemo(() => getChatInfoPresentation(chat), [chat]);
  const publicChatShareUrl = buildPublicChatShareUrl(chat.publicUsername);
  const isMuted =
    Boolean(chat.mutedUntil) && new Date(chat.mutedUntil ?? "").getTime() > Date.now();
  const showReportDetails =
    presentation.showReportUserAction || presentation.showReportChatAction;
  const isDirectBotChat =
    chat.chatType === "DIRECT" && chat.peerIsBot && Boolean(chat.peerUserId);
  const canStartCalls = canStartCallsFromChat(chat);

  useEffect(() => {
    if (!presentation.showBlockUserAction || !chat.peerUserId) {
      setBlocked(false);
      setLoadingBlocked(false);
      return;
    }

    let cancelled = false;
    setLoadingBlocked(true);
    void api
      .getBlockedUsers(token)
      .then((blockedUsers) => {
        if (cancelled) {
          return;
        }
        setBlocked(blockedUsers.some((user) => user.userId === chat.peerUserId));
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(describeError(loadError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingBlocked(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [chat.peerUserId, presentation.showBlockUserAction, token]);

  useEffect(() => {
    if (!isDirectBotChat || !chat.peerUserId) {
      setBotCommands([]);
      setBotCommandsError(null);
      setLoadingBotCommands(false);
      return;
    }

    let cancelled = false;
    setLoadingBotCommands(true);
    setBotCommandsError(null);
    void api
      .getBotCommands(token, chat.peerUserId)
      .then((commands) => {
        if (!cancelled) {
          setBotCommands(commands);
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setBotCommands([]);
          setBotCommandsError(describeError(loadError));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingBotCommands(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [botCommandsReloadKey, chat.peerUserId, isDirectBotChat, token]);

  function applyChatSummary(summary: ChatSummary) {
    onChatUpdated?.(summary);
  }

  async function refreshChatSummary() {
    const chats = await api.getChats(token);
    const refreshed = chats.find((candidate) => candidate.chatId === chat.chatId) ?? null;
    if (refreshed) {
      applyChatSummary(refreshed);
    }
    return refreshed;
  }

  async function runAction(actionId: string, action: () => Promise<void>) {
    setBusyAction(actionId);
    setError(null);
    setNotice(null);
    try {
      await action();
    } catch (actionError) {
      setError(describeError(actionError));
    } finally {
      setBusyAction(null);
    }
  }

  async function handleToggleMute() {
    await runAction("mute", async () => {
      const summary = await api.muteChat(
        token,
        chat.chatId,
        isMuted ? null : new Date(Date.now() + MUTE_WINDOW_MS).toISOString()
      );
      applyChatSummary(summary);
      setNotice(
        isMuted ? "Notifications enabled for this chat." : "Chat muted for the next 7 days."
      );
    });
  }

  async function handleToggleArchive() {
    await runAction("archive", async () => {
      const summary = await api.setChatArchived(token, chat.chatId, !chat.archived);
      applyChatSummary(summary);
      setNotice(chat.archived ? "Chat moved back to the main list." : "Chat archived.");
    });
  }

  async function handleTogglePin() {
    await runAction("pin", async () => {
      const summary = chat.pinned
        ? await api.unpinChatFromList(token, chat.chatId)
        : await api.pinChatToList(token, chat.chatId);
      applyChatSummary(summary);
      setNotice(chat.pinned ? "Chat unpinned." : "Chat pinned to the top.");
    });
  }

  async function handleToggleMarkedUnread() {
    await runAction("unread", async () => {
      const summary = await api.markChatUnread(token, chat.chatId, !chat.markedUnread);
      applyChatSummary(summary);
      setNotice(chat.markedUnread ? "Unread marker cleared." : "Chat marked as unread.");
    });
  }

  async function handleToggleBlock() {
    const peerUserId = chat.peerUserId;
    if (!peerUserId) {
      return;
    }

    await runAction("block", async () => {
      if (blocked) {
        await api.unblockUser(token, peerUserId);
      } else {
        await api.blockUser(token, peerUserId);
      }
      setBlocked((current) => !current);
      setNotice(blocked ? "User unblocked." : "User blocked.");
    });
  }

  async function handleReportUser() {
    const peerUserId = chat.peerUserId;
    if (!peerUserId) {
      return;
    }

    await runAction("report-user", async () => {
      await api.reportUser(token, {
        reportedUserId: peerUserId,
        category: "ABUSE",
        details: reportDetails.trim() || undefined
      });
      setNotice("Report submitted.");
      setReportDetails("");
    });
  }

  async function handleReportChat() {
    await runAction("report-chat", async () => {
      await api.reportChat(token, chat.chatId, {
        category: "ABUSE",
        details: reportDetails.trim() || undefined
      });
      setNotice("Chat reported.");
      setReportDetails("");
    });
  }

  async function handleLeaveChat() {
    await runAction("leave-chat", async () => {
      await api.leaveChat(token, chat.chatId);
      setNotice("You left the chat.");
      onChatLeft?.(chat.chatId);
      onClose();
    });
  }

  async function handleClearHistory() {
    await runAction("clear-history", async () => {
      await api.clearHistory(token, chat.chatId);
      onHistoryCleared?.(chat.chatId);
      await refreshChatSummary().catch(() => null);
      setNotice("Chat history cleared on this device state.");
    });
  }

  function handleSharePublicLink() {
    if (!publicChatShareUrl) {
      return;
    }

    void Share.share({
      message: publicChatShareUrl,
      url: publicChatShareUrl
    }).catch(() => undefined);
  }

  return (
    <AppScreen backgroundColor="#f8fafc" edges={["top", "bottom"]}>
      <ScreenScrollView
        gap="lg"
        keyboardShouldPersistTaps="handled"
        paddingBottom="xl"
        paddingHorizontal="xl"
        paddingTop="lg"
        showsVerticalScrollIndicator={false}
      >
        <AppHeader
          onBack={onClose}
          subtitle={presentation.screenSubtitle}
          title="Chat info"
        />

        <ScreenFeedback error={error} loading={loadingBlocked} notice={notice} />

        <View style={styles.hero}>
          <Avatar title={presentation.heroTitle} uri={chat.photoUrl} size={88} />
          <View style={styles.heroText}>
            <Text style={styles.heroTitle}>{presentation.heroTitle}</Text>
            <Text style={styles.heroSubtitle}>{presentation.heroSubtitle}</Text>
            {presentation.heroMeta.map((item) => (
              <Text key={item} style={styles.heroMeta}>
                {item}
              </Text>
            ))}
          </View>
        </View>

        <AppPanel
          description={presentation.profileDescription}
          title={presentation.profileTitle}
          tone="brand"
        >
          <Text style={styles.inlineLabel}>{presentation.profileLabel}</Text>
        </AppPanel>

        {presentation.detailItems.length > 0 ? (
          <AppPanel
            description="Key chat settings visible from the current summary."
            title="Chat details"
          >
            <View style={styles.detailList}>
              {presentation.detailItems.map((item) => (
                <Text key={item} style={styles.detailItem}>
                  {item}
                </Text>
              ))}
            </View>
          </AppPanel>
        ) : null}

        <AppPanel
          description="List controls that affect how this chat appears in your inbox."
          title="List actions"
        >
          <View style={styles.tileGrid}>
            <AppActionTile
              body={
                isMuted
                  ? "Restore notifications for this conversation."
                  : "Mute this chat for the next 7 days."
              }
              disabled={busyAction !== null}
              onPress={() => void handleToggleMute()}
              title={isMuted ? "Unmute chat" : "Mute chat"}
            />
            <AppActionTile
              body={
                chat.archived
                  ? "Bring the chat back into the main list."
                  : "Move the chat into Archived."
              }
              disabled={busyAction !== null}
              onPress={() => void handleToggleArchive()}
              title={chat.archived ? "Unarchive" : "Archive"}
            />
            <AppActionTile
              body={
                chat.pinned
                  ? "Remove it from the pinned section."
                  : "Keep this chat at the top of the list."
              }
              disabled={busyAction !== null}
              onPress={() => void handleTogglePin()}
              title={chat.pinned ? "Unpin chat" : "Pin chat"}
            />
            <AppActionTile
              body={
                chat.markedUnread
                  ? "Clear the manual unread marker."
                  : "Mark the conversation unread for follow-up."
              }
              disabled={busyAction !== null}
              onPress={() => void handleToggleMarkedUnread()}
              title={chat.markedUnread ? "Clear unread" : "Mark unread"}
            />
          </View>
        </AppPanel>

        <AppPanel description={presentation.quickActionsDescription} title="Quick actions">
          <View style={styles.tileGrid}>
            <AppActionTile
              body="Browse photos, videos, files and voice messages shared in this chat."
              disabled={!onOpenSharedMedia}
              onPress={() => onOpenSharedMedia?.(chat)}
              title="Shared media"
            />
            {publicChatShareUrl ? (
              <AppActionTile
                body="Share this chat's public join link."
                onPress={handleSharePublicLink}
                title="Share public link"
              />
            ) : null}
            {chat.chatType === "CHANNEL" && chat.linkedDiscussionChatId && onOpenDiscussionChat ? (
              <AppActionTile
                body={
                  chat.linkedDiscussionChatTitle
                    ? `Open ${chat.linkedDiscussionChatTitle} for comments and discussion threads.`
                    : "Open the linked discussion group for comments and discussion threads."
                }
                onPress={() => onOpenDiscussionChat(chat.linkedDiscussionChatId!)}
                title="Open discussion group"
              />
            ) : null}
            {onStartCall && canStartCalls ? (
              <AppActionTile
                body="Start a voice call from this chat context."
                disabled={busyAction !== null}
                onPress={() => onStartCall("VOICE")}
                title="Voice call"
              />
            ) : null}
            {onStartCall && canStartCalls ? (
              <AppActionTile
                body="Start a video call from this chat context."
                disabled={busyAction !== null}
                onPress={() => onStartCall("VIDEO")}
                title="Video call"
              />
            ) : null}
            {presentation.showOpenMembersAction ? (
              <AppActionTile
                body={presentation.manageActionBody ?? "Open members and management settings."}
                disabled={!onOpenMembers}
                onPress={() => onOpenMembers?.(chat)}
                title={presentation.manageActionTitle ?? "Manage chat"}
              />
            ) : null}
            {presentation.showOpenMiniAppAction ? (
              <AppActionTile
                body="Launch the bot mini app in the current chat context."
                disabled={!onOpenBotMiniApp}
                onPress={() =>
                  onOpenBotMiniApp?.(
                    chat.peerUserId ?? currentUserId,
                    chat.peerDisplayName ?? chat.title,
                    chat.chatId,
                    null
                  )
                }
                title="Open mini app"
              />
            ) : null}
          </View>
        </AppPanel>

        {isDirectBotChat ? (
          <AppPanel
            description="Published command shortcuts and bot capabilities available in this conversation."
            title="Bot commands"
            tone="brand"
          >
            {loadingBotCommands ? (
              <Text style={styles.botMeta}>Loading bot commands...</Text>
            ) : botCommandsError ? (
              <View style={styles.botCommandsState}>
                <Text style={styles.botMeta}>{botCommandsError}</Text>
                <AppActionTile
                  body="Try loading the published command list again."
                  onPress={() => setBotCommandsReloadKey((current) => current + 1)}
                  title="Retry commands"
                />
              </View>
            ) : botCommands.length === 0 ? (
              <Text style={styles.botMeta}>This bot has no published command shortcuts right now.</Text>
            ) : (
              <View style={styles.commandList}>
                {botCommands.map((command) => (
                  <View key={command.command} style={styles.commandItem}>
                    <Text style={styles.commandTitle}>{command.command}</Text>
                    <Text style={styles.commandDescription}>
                      {command.description?.trim() || "No command description provided."}
                    </Text>
                  </View>
                ))}
              </View>
            )}
          </AppPanel>
        ) : null}

        <AppPanel
          description={presentation.safetyDescription}
          title="Safety"
          tone="warning"
        >
          <View style={styles.tileGrid}>
            {presentation.showBlockUserAction ? (
              <AppActionTile
                body={
                  blocked
                    ? "Allow this user to contact you again."
                    : "Block the user and stop direct interaction."
                }
                disabled={busyAction !== null || !chat.peerUserId}
                onPress={() => void handleToggleBlock()}
                title={blocked ? "Unblock user" : "Block user"}
                tone={blocked ? "default" : "danger"}
              />
            ) : null}
            {presentation.showReportUserAction ? (
              <AppActionTile
                body="Submit a user report with optional context for moderation review."
                disabled={busyAction !== null || !chat.peerUserId}
                onPress={() => void handleReportUser()}
                title="Report user"
                tone="danger"
              />
            ) : null}
            {presentation.showLeaveChatAction ? (
              <AppActionTile
                body="Leave this chat while keeping the conversation available to other participants."
                disabled={busyAction !== null}
                onPress={() => void handleLeaveChat()}
                title="Leave chat"
                tone="danger"
              />
            ) : null}
            {presentation.showReportChatAction ? (
              <AppActionTile
                body="Report this group or channel with optional moderation context."
                disabled={busyAction !== null}
                onPress={() => void handleReportChat()}
                title="Report chat"
                tone="danger"
              />
            ) : null}
            {presentation.showClearHistoryAction ? (
              <AppActionTile
                body="Remove locally loaded conversation history and reset the visible thread."
                disabled={busyAction !== null}
                onPress={() => void handleClearHistory()}
                title="Clear history"
                tone="danger"
              />
            ) : null}
          </View>

          {showReportDetails ? (
            <AppTextField
              editable={busyAction == null}
              multiline
              onChangeText={setReportDetails}
              placeholder={presentation.reportDetailsPlaceholder}
              value={reportDetails}
            />
          ) : null}
        </AppPanel>
      </ScreenScrollView>
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  botCommandsState: {
    gap: appSpacing.sm
  },
  botMeta: {
    color: "#334155",
    fontSize: 14,
    lineHeight: 20
  },
  commandDescription: {
    color: "#475569",
    fontSize: 12,
    lineHeight: 18
  },
  commandItem: {
    backgroundColor: "#eff6ff",
    borderRadius: 14,
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  commandList: {
    gap: appSpacing.sm
  },
  commandTitle: {
    color: "#1d4ed8",
    fontSize: 14,
    fontWeight: "700"
  },
  detailItem: {
    color: "#334155",
    fontSize: 14,
    lineHeight: 20
  },
  detailList: {
    gap: appSpacing.xs
  },
  hero: {
    alignItems: "center",
    backgroundColor: "#e2e8f0",
    borderRadius: 28,
    flexDirection: "row",
    gap: appSpacing.lg,
    padding: appSpacing.xl
  },
  heroMeta: {
    color: "#475569",
    fontSize: 14
  },
  heroSubtitle: {
    color: "#0f766e",
    fontSize: 15,
    fontWeight: "600"
  },
  heroText: {
    flex: 1,
    gap: appSpacing.xs
  },
  heroTitle: {
    color: appColors.textPrimary,
    fontSize: 24,
    fontWeight: "800"
  },
  inlineLabel: {
    color: "#334155",
    fontSize: 13,
    fontWeight: "600"
  },
  tileGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  }
});
