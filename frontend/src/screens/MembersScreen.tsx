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
import { pickSingleImage } from "../services/imagePicker";
import { useAppStore } from "../store/useAppStore";
import type {
  ChatAnalytics,
  ChatBan,
  ChatInviteLink,
  ChatJoinRequest,
  ChatMember,
  ChatSummary,
  UserSearchResult
} from "../types";

type MembersScreenProps = {
  chat: ChatSummary;
  currentUserId: string;
  token: string;
  onClose: () => void;
};

export function MembersScreen({
  chat,
  currentUserId,
  token,
  onClose
}: MembersScreenProps) {
  const chats = useAppStore((state) => state.chats);
  const upsertChat = useAppStore((state) => state.upsertChat);
  const [members, setMembers] = useState<ChatMember[]>([]);
  const [query, setQuery] = useState("");
  const [chatTitle, setChatTitle] = useState(chat.title);
  const [chatPhotoUrl, setChatPhotoUrl] = useState<string | null>(chat.photoUrl);
  const [chatAbout, setChatAbout] = useState(chat.about ?? "");
  const [autoDeleteSeconds, setAutoDeleteSeconds] = useState(
    chat.autoDeleteSeconds ? String(chat.autoDeleteSeconds) : ""
  );
  const [slowModeSeconds, setSlowModeSeconds] = useState(
    chat.slowModeSeconds ? String(chat.slowModeSeconds) : ""
  );
  const [forumEnabled, setForumEnabled] = useState(chat.forumEnabled);
  const [joinRequiresApproval, setJoinRequiresApproval] = useState(chat.joinRequiresApproval);
  const [commentsEnabled, setCommentsEnabled] = useState(chat.commentsEnabled);
  const [reactionsEnabled, setReactionsEnabled] = useState(chat.reactionsEnabled);
  const [crossPostingEnabled, setCrossPostingEnabled] = useState(chat.crossPostingEnabled);
  const [discussionChatId, setDiscussionChatId] = useState<string | null>(
    chat.linkedDiscussionChatId
  );
  const [inviteLabel, setInviteLabel] = useState("");
  const [inviteUsageLimit, setInviteUsageLimit] = useState("");
  const [publicUsername, setPublicUsername] = useState(chat.publicUsername ?? "");
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [inviteLinks, setInviteLinks] = useState<ChatInviteLink[]>([]);
  const [joinRequests, setJoinRequests] = useState<ChatJoinRequest[]>([]);
  const [bannedMembers, setBannedMembers] = useState<ChatBan[]>([]);
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([]);
  const [loadingMembers, setLoadingMembers] = useState(false);
  const [loadingInviteLinks, setLoadingInviteLinks] = useState(false);
  const [loadingJoinRequests, setLoadingJoinRequests] = useState(false);
  const [loadingBans, setLoadingBans] = useState(false);
  const [loadingAnalytics, setLoadingAnalytics] = useState(false);
  const [searching, setSearching] = useState(false);
  const [mutating, setMutating] = useState(false);
  const [creatingInviteLink, setCreatingInviteLink] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);
  const [savingPublicUsername, setSavingPublicUsername] = useState(false);
  const [revokingInviteLinkId, setRevokingInviteLinkId] = useState<string | null>(null);
  const [processingJoinRequestUserId, setProcessingJoinRequestUserId] = useState<string | null>(null);
  const [restrictingUserId, setRestrictingUserId] = useState<string | null>(null);
  const [banningUserId, setBanningUserId] = useState<string | null>(null);
  const [unbanningUserId, setUnbanningUserId] = useState<string | null>(null);
  const [updatingPermissionsUserId, setUpdatingPermissionsUserId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [analytics, setAnalytics] = useState<ChatAnalytics | null>(null);

  async function loadMembers() {
    setLoadingMembers(true);
    setError(null);
    try {
      const nextMembers = await api.getChatMembers(token, chat.chatId);
      setMembers(nextMembers);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load members");
    } finally {
      setLoadingMembers(false);
    }
  }

  async function loadInviteLinks(allowInviteLinkAccess: boolean) {
    if (
      (chat.chatType !== "GROUP" && chat.chatType !== "CHANNEL") ||
      !allowInviteLinkAccess
    ) {
      setInviteLinks([]);
      return;
    }

    setLoadingInviteLinks(true);
    try {
      const nextLinks = await api.getChatInviteLinks(token, chat.chatId);
      setInviteLinks(nextLinks);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load invite links");
    } finally {
      setLoadingInviteLinks(false);
    }
  }

  async function loadJoinRequests() {
    if (chat.chatType !== "GROUP" && chat.chatType !== "CHANNEL") {
      setJoinRequests([]);
      return;
    }

    setLoadingJoinRequests(true);
    try {
      const nextJoinRequests = await api.getChatJoinRequests(token, chat.chatId);
      setJoinRequests(nextJoinRequests);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load join requests");
    } finally {
      setLoadingJoinRequests(false);
    }
  }

  async function loadBans(allowModeration: boolean) {
    if (
      (chat.chatType !== "GROUP" && chat.chatType !== "CHANNEL") ||
      !allowModeration
    ) {
      setBannedMembers([]);
      return;
    }

    setLoadingBans(true);
    try {
      const nextBans = await api.getChatBans(token, chat.chatId);
      setBannedMembers(nextBans);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load banned users");
    } finally {
      setLoadingBans(false);
    }
  }

  async function loadAnalytics(allowAnalyticsAccess: boolean) {
    if (
      (chat.chatType !== "GROUP" && chat.chatType !== "CHANNEL") ||
      !allowAnalyticsAccess
    ) {
      setAnalytics(null);
      return;
    }

    setLoadingAnalytics(true);
    try {
      const nextAnalytics = await api.getChatAnalytics(token, chat.chatId);
      setAnalytics(nextAnalytics);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load analytics");
    } finally {
      setLoadingAnalytics(false);
    }
  }

  useEffect(() => {
    void loadMembers();
    setChatTitle(chat.title);
    setChatPhotoUrl(chat.photoUrl);
    setChatAbout(chat.about ?? "");
    setAutoDeleteSeconds(chat.autoDeleteSeconds ? String(chat.autoDeleteSeconds) : "");
    setSlowModeSeconds(chat.slowModeSeconds ? String(chat.slowModeSeconds) : "");
    setForumEnabled(chat.forumEnabled);
    setJoinRequiresApproval(chat.joinRequiresApproval);
    setCommentsEnabled(chat.commentsEnabled);
    setReactionsEnabled(chat.reactionsEnabled);
    setCrossPostingEnabled(chat.crossPostingEnabled);
    setDiscussionChatId(chat.linkedDiscussionChatId);
    setPublicUsername(chat.publicUsername ?? "");
    setJoinRequests([]);
    setBannedMembers([]);
    setAnalytics(null);
  }, [
    chat.about,
    chat.autoDeleteSeconds,
    chat.chatId,
    chat.commentsEnabled,
    chat.crossPostingEnabled,
    chat.forumEnabled,
    chat.joinRequiresApproval,
    chat.linkedDiscussionChatId,
    chat.photoUrl,
    chat.publicUsername,
    chat.reactionsEnabled,
    chat.slowModeSeconds,
    chat.title,
    token
  ]);

  const myMembership = useMemo(
    () => members.find((member) => member.userId === currentUserId) ?? null,
    [members, currentUserId]
  );

  const canManageMembers =
    chat.chatType !== "DIRECT" &&
    chat.chatType !== "SAVED" &&
    Boolean(myMembership?.canManageMembers);
  const canManageInviteLinks =
    (chat.chatType === "GROUP" || chat.chatType === "CHANNEL") &&
    Boolean(myMembership?.canManageInviteLinks);
  const canApproveJoinRequests =
    (chat.chatType === "GROUP" || chat.chatType === "CHANNEL") &&
    Boolean(myMembership?.canApproveJoinRequests);
  const canModerateMessages =
    (chat.chatType === "GROUP" || chat.chatType === "CHANNEL") &&
    Boolean(myMembership?.canManageMessages);
  const canViewAnalytics =
    (chat.chatType === "GROUP" || chat.chatType === "CHANNEL") &&
    (canManageMembers || canManageInviteLinks || canModerateMessages);

  useEffect(() => {
    void loadInviteLinks(canManageInviteLinks);
  }, [canManageInviteLinks, chat.chatId, token]);

  useEffect(() => {
    void loadBans(canModerateMessages);
  }, [canModerateMessages, chat.chatId, token]);

  useEffect(() => {
    void loadAnalytics(canViewAnalytics);
  }, [canViewAnalytics, chat.chatId, token]);

  useEffect(() => {
    if (!canApproveJoinRequests) {
      setJoinRequests([]);
      return;
    }

    void loadJoinRequests();
  }, [canApproveJoinRequests, chat.chatId, joinRequiresApproval, token]);

  const existingUserIds = useMemo(
    () => new Set(members.map((member) => member.userId)),
    [members]
  );

  const availableDiscussionChats = useMemo(
    () =>
      chats.filter(
        (item) =>
          item.chatType === "GROUP" &&
          !item.forumEnabled &&
          item.chatId !== chat.chatId
      ),
    [chat.chatId, chats]
  );

  useEffect(() => {
    let cancelled = false;

    async function searchUsers() {
      const normalized = query.trim();
      if (!canManageMembers || normalized.length < 2) {
        setResults([]);
        setSelectedUserIds([]);
        return;
      }

      setSearching(true);
      setError(null);
      try {
        const nextResults = await api.searchUsers(token, normalized);
        if (!cancelled) {
          setResults(nextResults.filter((item) => !existingUserIds.has(item.userId)));
        }
      } catch (searchError) {
        if (!cancelled) {
          setError(searchError instanceof Error ? searchError.message : "Unable to search users");
        }
      } finally {
        if (!cancelled) {
          setSearching(false);
        }
      }
    }

    void searchUsers();

    return () => {
      cancelled = true;
    };
  }, [canManageMembers, existingUserIds, query, token]);

  function toggleCandidate(userId: string) {
    setSelectedUserIds((current) =>
      current.includes(userId)
        ? current.filter((id) => id !== userId)
        : [...current, userId]
    );
  }

  async function handleAddMembers() {
    if (!canManageMembers || selectedUserIds.length === 0) {
      return;
    }

    setMutating(true);
    setError(null);
    try {
      const nextMembers = await api.addChatMembers(token, chat.chatId, selectedUserIds);
      setMembers(nextMembers);
      setSelectedUserIds([]);
      setQuery("");
      setResults([]);
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "Unable to add members");
    } finally {
      setMutating(false);
    }
  }

  async function handleRoleChange(userId: string, role: "ADMIN" | "MEMBER") {
    if (!canManageMembers) {
      return;
    }

    setMutating(true);
    setError(null);
    try {
      await api.updateMemberRole(token, chat.chatId, userId, role);
      await loadMembers();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to update member role"
      );
    } finally {
      setMutating(false);
    }
  }

  async function handleRemoveMember(userId: string) {
    if (!canManageMembers) {
      return;
    }

    setMutating(true);
    setError(null);
    try {
      await api.removeChatMember(token, chat.chatId, userId);
      await loadMembers();
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "Unable to remove member");
    } finally {
      setMutating(false);
    }
  }

  async function handleApproveJoinRequest(userId: string) {
    if (!canApproveJoinRequests) {
      return;
    }

    setProcessingJoinRequestUserId(userId);
    setError(null);
    try {
      await api.approveChatJoinRequest(token, chat.chatId, userId);
      await Promise.all([loadMembers(), loadJoinRequests()]);
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to approve join request"
      );
    } finally {
      setProcessingJoinRequestUserId(null);
    }
  }

  async function handleDeclineJoinRequest(userId: string) {
    if (!canApproveJoinRequests) {
      return;
    }

    setProcessingJoinRequestUserId(userId);
    setError(null);
    try {
      await api.declineChatJoinRequest(token, chat.chatId, userId);
      await loadJoinRequests();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to decline join request"
      );
    } finally {
      setProcessingJoinRequestUserId(null);
    }
  }

  async function handleRestrictionChange(
    userId: string,
    nextCanSendMessages: boolean,
    restrictedUntil?: string | null,
    restrictionReason?: string | null
  ) {
    if (!canModerateMessages) {
      return;
    }

    setRestrictingUserId(userId);
    setError(null);
    try {
      await api.updateChatMemberRestriction(token, chat.chatId, userId, {
        canSendMessages: nextCanSendMessages,
        restrictedUntil,
        restrictionReason
      });
      await loadMembers();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to update restriction"
      );
    } finally {
      setRestrictingUserId(null);
    }
  }

  async function handleBanMember(userId: string) {
    if (!canModerateMessages) {
      return;
    }

    setBanningUserId(userId);
    setError(null);
    try {
      await api.banChatMember(token, chat.chatId, userId, {
        bannedUntil: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        reason: "Banned by admin"
      });
      await Promise.all([
        loadMembers(),
        loadBans(true),
        canApproveJoinRequests ? loadJoinRequests() : Promise.resolve()
      ]);
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "Unable to ban member");
    } finally {
      setBanningUserId(null);
    }
  }

  async function handleUnbanMember(userId: string) {
    if (!canModerateMessages) {
      return;
    }

    setUnbanningUserId(userId);
    setError(null);
    try {
      await api.unbanChatMember(token, chat.chatId, userId);
      await loadBans(true);
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "Unable to unban member");
    } finally {
      setUnbanningUserId(null);
    }
  }

  async function handlePermissionChange(
    userId: string,
    payload: {
      canManageMembers?: boolean;
      canManageInviteLinks?: boolean;
      canManageMessages?: boolean;
      canPinMessages?: boolean;
      canApproveJoinRequests?: boolean;
      canPostMessages?: boolean;
      anonymousAdmin?: boolean;
    }
  ) {
    if (!canManageMembers) {
      return;
    }

    setUpdatingPermissionsUserId(userId);
    setError(null);
    try {
      await api.updateChatMemberPermissions(token, chat.chatId, userId, payload);
      await loadMembers();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to update member permissions"
      );
    } finally {
      setUpdatingPermissionsUserId(null);
    }
  }

  async function handleCreateInviteLink() {
    if (!canManageInviteLinks || creatingInviteLink) {
      return;
    }

    const normalizedUsageLimit = inviteUsageLimit.trim();
    const usageLimit = normalizedUsageLimit
      ? Number.parseInt(normalizedUsageLimit, 10)
      : undefined;
    if (
      normalizedUsageLimit &&
      (typeof usageLimit !== "number" || !Number.isFinite(usageLimit) || usageLimit <= 0)
    ) {
      setError("Usage limit must be a positive integer");
      return;
    }

    setCreatingInviteLink(true);
    setError(null);
    try {
      const inviteLink = await api.createChatInviteLink(token, chat.chatId, {
        label: inviteLabel.trim() || undefined,
        usageLimit
      });
      setInviteLinks((current) => [inviteLink, ...current]);
      setInviteLabel("");
      setInviteUsageLimit("");
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Unable to create invite link");
    } finally {
      setCreatingInviteLink(false);
    }
  }

  async function handleSaveProfile() {
    if (!canManageInviteLinks || savingProfile) {
      return;
    }

    setSavingProfile(true);
    setError(null);
    try {
      const summary = await api.updateChatProfile(token, chat.chatId, {
        title: chatTitle.trim() || undefined,
        about: chatAbout.trim() || null,
        autoDeleteSeconds: autoDeleteSeconds.trim()
          ? Number.parseInt(autoDeleteSeconds.trim(), 10)
          : null,
        slowModeSeconds: slowModeSeconds.trim()
          ? Number.parseInt(slowModeSeconds.trim(), 10)
          : null,
        forumEnabled: chat.chatType === "GROUP" ? forumEnabled : undefined,
        joinRequiresApproval,
        commentsEnabled: chat.chatType === "CHANNEL" ? commentsEnabled : undefined,
        reactionsEnabled:
          chat.chatType === "GROUP" || chat.chatType === "CHANNEL"
            ? reactionsEnabled
            : undefined,
        crossPostingEnabled: chat.chatType === "CHANNEL" ? crossPostingEnabled : undefined,
        linkedDiscussionChatId:
          chat.chatType === "CHANNEL" &&
          discussionChatId &&
          discussionChatId !== chat.linkedDiscussionChatId
            ? discussionChatId
            : undefined
      });
      upsertChat(summary);
      setChatTitle(summary.title);
      setChatPhotoUrl(summary.photoUrl);
      setChatAbout(summary.about ?? "");
      setAutoDeleteSeconds(summary.autoDeleteSeconds ? String(summary.autoDeleteSeconds) : "");
      setSlowModeSeconds(summary.slowModeSeconds ? String(summary.slowModeSeconds) : "");
      setForumEnabled(summary.forumEnabled);
      setJoinRequiresApproval(summary.joinRequiresApproval);
      setCommentsEnabled(summary.commentsEnabled);
      setReactionsEnabled(summary.reactionsEnabled);
      setCrossPostingEnabled(summary.crossPostingEnabled);
      setDiscussionChatId(summary.linkedDiscussionChatId);
      await loadAnalytics(canViewAnalytics);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to save chat profile");
    } finally {
      setSavingProfile(false);
    }
  }

  async function handleUploadPhoto() {
    if (!canManageInviteLinks || uploadingPhoto) {
      return;
    }

    const file = await pickSingleImage();
    if (!file) {
      return;
    }

    setUploadingPhoto(true);
    setError(null);
    try {
      const summary = await api.uploadChatPhoto(token, chat.chatId, file);
      upsertChat(summary);
      setChatPhotoUrl(summary.photoUrl);
      setChatTitle(summary.title);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Unable to upload chat photo");
    } finally {
      setUploadingPhoto(false);
    }
  }

  async function handleRemovePhoto() {
    if (!canManageInviteLinks || removingPhoto) {
      return;
    }

    setRemovingPhoto(true);
    setError(null);
    try {
      const summary = await api.deleteChatPhoto(token, chat.chatId);
      upsertChat(summary);
      setChatPhotoUrl(summary.photoUrl);
      setChatTitle(summary.title);
    } catch (removeError) {
      setError(removeError instanceof Error ? removeError.message : "Unable to remove chat photo");
    } finally {
      setRemovingPhoto(false);
    }
  }

  async function handleSavePublicUsername() {
    if (!canManageInviteLinks || savingPublicUsername) {
      return;
    }

    setSavingPublicUsername(true);
    setError(null);
    try {
      const summary = await api.updateChatPublicUsername(
        token,
        chat.chatId,
        publicUsername.trim() || null
      );
      upsertChat(summary);
      setPublicUsername(summary.publicUsername ?? "");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to save public username");
    } finally {
      setSavingPublicUsername(false);
    }
  }

  async function handleRevokeInviteLink(inviteLinkId: string) {
    if (!canManageInviteLinks || revokingInviteLinkId) {
      return;
    }

    setRevokingInviteLinkId(inviteLinkId);
    setError(null);
    try {
      const updated = await api.revokeChatInviteLink(token, chat.chatId, inviteLinkId);
      setInviteLinks((current) =>
        current.map((link) => (link.inviteLinkId === inviteLinkId ? updated : link))
      );
    } catch (revokeError) {
      setError(revokeError instanceof Error ? revokeError.message : "Unable to revoke invite link");
    } finally {
      setRevokingInviteLinkId(null);
    }
  }

  const orderedMembers = useMemo(() => {
    const rank: Record<string, number> = {
      OWNER: 0,
      ADMIN: 1,
      MEMBER: 2
    };

    return [...members].sort((left, right) => {
      const leftRank = rank[left.role] ?? 99;
      const rightRank = rank[right.role] ?? 99;
      if (leftRank !== rightRank) {
        return leftRank - rightRank;
      }
      return left.displayName.localeCompare(right.displayName);
    });
  }, [members]);

  const restrictedMembers = useMemo(
    () => orderedMembers.filter((member) => !member.canSendMessages),
    [orderedMembers]
  );

  function getPermissionLabels(member: ChatMember) {
    const labels: string[] = [];
    if (member.canManageMembers) {
      labels.push("Members");
    }
    if (member.canManageInviteLinks) {
      labels.push("Invite links");
    }
    if (member.canManageMessages) {
      labels.push("Moderation");
    }
    if (member.canPinMessages) {
      labels.push("Pins");
    }
    if (member.canApproveJoinRequests) {
      labels.push("Join requests");
    }
    if (member.anonymousAdmin) {
      labels.push("Anonymous");
    }
    if (chat.chatType === "CHANNEL" && member.canPostMessages) {
      labels.push("Can post");
    }
    return labels;
  }

  const headerContent = (
    <View style={styles.headerContent}>
      <View style={styles.chatIdentityCard}>
        <Avatar uri={chatPhotoUrl} title={chatTitle} size={92} />
        <View style={styles.chatIdentityText}>
          <Text style={styles.chatIdentityTitle}>{chatTitle}</Text>
          <Text style={styles.chatIdentityMeta}>
            {chat.chatType === "CHANNEL" ? "Channel" : "Group"}
          </Text>
        </View>
        {canManageInviteLinks ? (
          <View style={styles.photoActions}>
            <Pressable
              disabled={uploadingPhoto}
              onPress={() => void handleUploadPhoto()}
              style={[styles.inlineButton, uploadingPhoto && styles.buttonDisabled]}
            >
              <Text style={styles.inlineButtonText}>
                {uploadingPhoto ? "Uploading..." : "Change photo"}
              </Text>
            </Pressable>
            <Pressable
              disabled={removingPhoto || !chatPhotoUrl}
              onPress={() => void handleRemovePhoto()}
              style={[styles.inlineDangerButton, (removingPhoto || !chatPhotoUrl) && styles.buttonDisabled]}
            >
              <Text style={styles.inlineDangerText}>
                {removingPhoto ? "Removing..." : "Remove photo"}
              </Text>
            </Pressable>
          </View>
        ) : null}
      </View>

      {canViewAnalytics ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Analytics</Text>
          <Text style={styles.selectorHint}>
            Basic public-surface metrics for the last 24 hours.
          </Text>
          {loadingAnalytics ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
          {analytics ? (
            <View style={styles.analyticsGrid}>
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.memberCount}</Text>
                <Text style={styles.analyticsLabel}>
                  {chat.chatType === "CHANNEL" ? "Subscribers" : "Members"}
                </Text>
              </View>
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.adminCount}</Text>
                <Text style={styles.analyticsLabel}>Admins</Text>
              </View>
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.pendingJoinRequestCount}</Text>
                <Text style={styles.analyticsLabel}>Pending joins</Text>
              </View>
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.activeInviteLinkCount}</Text>
                <Text style={styles.analyticsLabel}>Active links</Text>
              </View>
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.messagesLast24h}</Text>
                <Text style={styles.analyticsLabel}>Posts 24h</Text>
              </View>
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.reactionsLast24h}</Text>
                <Text style={styles.analyticsLabel}>Reactions 24h</Text>
              </View>
              {chat.chatType === "CHANNEL" ? (
                <View style={styles.analyticsTile}>
                  <Text style={styles.analyticsValue}>{analytics.commentsLast24h}</Text>
                  <Text style={styles.analyticsLabel}>Comments 24h</Text>
                </View>
              ) : null}
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.restrictedCount}</Text>
                <Text style={styles.analyticsLabel}>Restricted</Text>
              </View>
              <View style={styles.analyticsTile}>
                <Text style={styles.analyticsValue}>{analytics.bannedCount}</Text>
                <Text style={styles.analyticsLabel}>Banned</Text>
              </View>
            </View>
          ) : null}
        </View>
      ) : null}

      {canManageMembers ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Add people</Text>
          <TextInput
            autoCapitalize="none"
            onChangeText={setQuery}
            placeholder="Search users"
            style={styles.input}
            value={query}
          />

          {searching ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}

          {results.length > 0 ? (
            <FlatList
              contentContainerStyle={styles.candidatesContent}
              data={results}
              horizontal
              keyExtractor={(item) => item.userId}
              renderItem={({ item }) => {
                const selected = selectedUserIds.includes(item.userId);
                return (
                  <Pressable
                    onPress={() => toggleCandidate(item.userId)}
                    style={[styles.candidateCard, selected && styles.candidateCardSelected]}
                  >
                    <Avatar uri={item.photoUrl} title={item.displayName} size={46} />
                    <Text style={styles.candidateName}>{item.displayName}</Text>
                    <Text style={styles.candidateMeta}>{item.phoneNumber}</Text>
                  </Pressable>
                );
              }}
              showsHorizontalScrollIndicator={false}
            />
          ) : null}

          <Pressable
            disabled={mutating || selectedUserIds.length === 0}
            onPress={handleAddMembers}
            style={[
              styles.primaryButton,
              (mutating || selectedUserIds.length === 0) && styles.buttonDisabled
            ]}
          >
            <Text style={styles.primaryButtonText}>
              {mutating ? "Updating..." : `Add selected (${selectedUserIds.length})`}
            </Text>
          </Pressable>
        </View>
      ) : null}

      {canManageInviteLinks ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Chat profile</Text>
          <TextInput
            onChangeText={setChatTitle}
            placeholder="Chat title"
            style={styles.input}
            value={chatTitle}
          />
          <TextInput
            multiline
            onChangeText={setChatAbout}
            placeholder="Description"
            style={[styles.input, styles.aboutInput]}
            value={chatAbout}
          />
          <TextInput
            keyboardType="number-pad"
            onChangeText={setAutoDeleteSeconds}
            placeholder="Auto-delete seconds"
            style={styles.input}
            value={autoDeleteSeconds}
          />
          <TextInput
            keyboardType="number-pad"
            onChangeText={setSlowModeSeconds}
            placeholder="Slow mode seconds"
            style={styles.input}
            value={slowModeSeconds}
          />
          {chat.chatType === "GROUP" ? (
            <Pressable
              onPress={() => setForumEnabled((current) => !current)}
              style={[styles.toggleCard, forumEnabled && styles.toggleCardActive]}
            >
              <View style={styles.toggleBody}>
                <Text style={styles.toggleTitle}>Forum topics</Text>
                <Text style={styles.toggleHint}>
                  Turn this group into a Telegram-style forum with topics.
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
                New members must be approved by an admin before they can access this chat.
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
          {(chat.chatType === "GROUP" || chat.chatType === "CHANNEL") ? (
            <Pressable
              onPress={() => setReactionsEnabled((current) => !current)}
              style={[styles.toggleCard, reactionsEnabled && styles.toggleCardActive]}
            >
              <View style={styles.toggleBody}>
                <Text style={styles.toggleTitle}>Reactions</Text>
                <Text style={styles.toggleHint}>
                  Control whether members can react to posts and messages in this chat.
                </Text>
              </View>
              <View style={[styles.toggleBadge, reactionsEnabled && styles.toggleBadgeActive]}>
                <Text
                  style={[
                    styles.toggleBadgeText,
                    reactionsEnabled && styles.toggleBadgeTextActive
                  ]}
                >
                  {reactionsEnabled ? "ON" : "OFF"}
                </Text>
              </View>
            </Pressable>
          ) : null}
          {chat.chatType === "CHANNEL" ? (
            <>
              <Pressable
                onPress={() => setCommentsEnabled((current) => !current)}
                style={[styles.toggleCard, commentsEnabled && styles.toggleCardActive]}
              >
                <View style={styles.toggleBody}>
                  <Text style={styles.toggleTitle}>Comments</Text>
                  <Text style={styles.toggleHint}>
                    Allow subscribers to open discussion threads for new channel posts.
                  </Text>
                </View>
                <View style={[styles.toggleBadge, commentsEnabled && styles.toggleBadgeActive]}>
                  <Text
                    style={[
                      styles.toggleBadgeText,
                      commentsEnabled && styles.toggleBadgeTextActive
                    ]}
                  >
                    {commentsEnabled ? "ON" : "OFF"}
                  </Text>
                </View>
              </Pressable>
              <Pressable
                onPress={() => setCrossPostingEnabled((current) => !current)}
                style={[styles.toggleCard, crossPostingEnabled && styles.toggleCardActive]}
              >
                <View style={styles.toggleBody}>
                  <Text style={styles.toggleTitle}>Cross-posting</Text>
                  <Text style={styles.toggleHint}>
                    Mirror new channel posts into the linked discussion group as thread roots.
                  </Text>
                </View>
                <View
                  style={[styles.toggleBadge, crossPostingEnabled && styles.toggleBadgeActive]}
                >
                  <Text
                    style={[
                      styles.toggleBadgeText,
                      crossPostingEnabled && styles.toggleBadgeTextActive
                    ]}
                  >
                    {crossPostingEnabled ? "ON" : "OFF"}
                  </Text>
                </View>
              </Pressable>
              <View style={styles.selectorGroup}>
                <Text style={styles.selectorLabel}>Discussion group</Text>
                <Text style={styles.selectorHint}>
                  Link a group that will host channel comments and discussion threads.
                </Text>
                {availableDiscussionChats.length === 0 ? (
                  <Text style={styles.selectorEmpty}>No groups available in your chat list yet.</Text>
                ) : (
                  <View style={styles.selectorOptions}>
                    {availableDiscussionChats.map((item) => {
                      const selected = discussionChatId === item.chatId;
                      return (
                        <Pressable
                          key={item.chatId}
                          onPress={() => setDiscussionChatId(item.chatId)}
                          style={[styles.selectorOption, selected && styles.selectorOptionActive]}
                        >
                          <Text
                            style={[
                              styles.selectorOptionText,
                              selected && styles.selectorOptionTextActive
                            ]}
                          >
                            {item.title}
                          </Text>
                        </Pressable>
                      );
                    })}
                  </View>
                )}
                {chat.linkedDiscussionChatTitle ? (
                  <Text style={styles.selectorCurrent}>
                    Current: {chat.linkedDiscussionChatTitle}
                  </Text>
                ) : null}
              </View>
            </>
          ) : null}
          <Pressable
            disabled={savingProfile}
            onPress={() => void handleSaveProfile()}
            style={[styles.primaryButton, savingProfile && styles.buttonDisabled]}
          >
            <Text style={styles.primaryButtonText}>
              {savingProfile ? "Saving..." : "Save chat profile"}
            </Text>
          </Pressable>
        </View>
      ) : null}

      {canManageInviteLinks ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Public username</Text>
          <TextInput
            autoCapitalize="none"
            autoCorrect={false}
            onChangeText={setPublicUsername}
            placeholder="@public_name"
            style={styles.input}
            value={publicUsername}
          />
          <Pressable
            disabled={savingPublicUsername}
            onPress={() => void handleSavePublicUsername()}
            style={[styles.primaryButton, savingPublicUsername && styles.buttonDisabled]}
          >
            <Text style={styles.primaryButtonText}>
              {savingPublicUsername ? "Saving..." : "Save public username"}
            </Text>
          </Pressable>
        </View>
      ) : null}

      {canApproveJoinRequests && (joinRequiresApproval || joinRequests.length > 0) ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Join requests</Text>
          <Text style={styles.selectorHint}>
            Review pending approvals for public links, invite links, and discoverable usernames.
          </Text>
          {loadingJoinRequests ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
          {joinRequests.length === 0 && !loadingJoinRequests ? (
            <Text style={styles.selectorEmpty}>No pending join requests.</Text>
          ) : null}
          <View style={styles.inviteLinksList}>
            {joinRequests.map((request) => (
              <View key={request.userId} style={styles.inviteLinkCard}>
                <View style={styles.memberHeader}>
                  <Avatar uri={request.photoUrl} title={request.displayName} size={42} />
                  <View style={styles.memberInfo}>
                    <Text style={styles.memberName}>{request.displayName}</Text>
                    <Text style={styles.memberMeta}>
                      {request.username ? `@${request.username}` : request.phoneNumber ?? "phone-hidden"}
                    </Text>
                    <Text style={styles.inviteLinkMeta}>
                      Source: {request.source} · {new Date(request.requestedAt).toLocaleString()}
                    </Text>
                  </View>
                </View>
                <View style={styles.actionsRow}>
                  <Pressable
                    disabled={processingJoinRequestUserId === request.userId}
                    onPress={() => void handleApproveJoinRequest(request.userId)}
                    style={[
                      styles.inlineButton,
                      processingJoinRequestUserId === request.userId && styles.buttonDisabled
                    ]}
                  >
                    <Text style={styles.inlineButtonText}>Approve</Text>
                  </Pressable>
                  <Pressable
                    disabled={processingJoinRequestUserId === request.userId}
                    onPress={() => void handleDeclineJoinRequest(request.userId)}
                    style={[
                      styles.inlineDangerButton,
                      processingJoinRequestUserId === request.userId && styles.buttonDisabled
                    ]}
                  >
                    <Text style={styles.inlineDangerText}>
                      {processingJoinRequestUserId === request.userId ? "Working..." : "Decline"}
                    </Text>
                  </Pressable>
                </View>
              </View>
            ))}
          </View>
        </View>
      ) : null}

      {canModerateMessages && restrictedMembers.length > 0 ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Restricted members</Text>
          <Text style={styles.selectorHint}>
            {restrictedMembers.length} member{restrictedMembers.length === 1 ? "" : "s"} currently
            in read-only mode.
          </Text>
        </View>
      ) : null}

      {canModerateMessages ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Banned users</Text>
          <Text style={styles.selectorHint}>
            Users banned from this chat cannot join by invite link or public username until unbanned.
          </Text>
          {loadingBans ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
          {bannedMembers.length === 0 && !loadingBans ? (
            <Text style={styles.selectorEmpty}>No banned users.</Text>
          ) : null}
          <View style={styles.inviteLinksList}>
            {bannedMembers.map((ban) => (
              <View key={ban.userId} style={styles.inviteLinkCard}>
                <View style={styles.memberHeader}>
                  <Avatar uri={ban.photoUrl} title={ban.displayName} size={42} />
                  <View style={styles.memberInfo}>
                    <Text style={styles.memberName}>{ban.displayName}</Text>
                    <Text style={styles.memberMeta}>
                      {ban.username ? `@${ban.username}` : ban.phoneNumber ?? "phone-hidden"}
                    </Text>
                    <Text style={styles.inviteLinkMeta}>
                      {ban.bannedUntil
                        ? `Banned until ${new Date(ban.bannedUntil).toLocaleString()}`
                        : "Permanent ban"}
                    </Text>
                    {ban.reason ? (
                      <Text style={styles.inviteLinkMeta}>{ban.reason}</Text>
                    ) : null}
                  </View>
                </View>
                <View style={styles.actionsRow}>
                  <Pressable
                    disabled={unbanningUserId === ban.userId}
                    onPress={() => void handleUnbanMember(ban.userId)}
                    style={[
                      styles.inlineButton,
                      unbanningUserId === ban.userId && styles.buttonDisabled
                    ]}
                  >
                    <Text style={styles.inlineButtonText}>
                      {unbanningUserId === ban.userId ? "Working..." : "Unban"}
                    </Text>
                  </Pressable>
                </View>
              </View>
            ))}
          </View>
        </View>
      ) : null}

      {canManageInviteLinks ? (
        <View style={styles.adminCard}>
          <Text style={styles.sectionTitle}>Invite links</Text>
          <TextInput
            onChangeText={setInviteLabel}
            placeholder="Label (optional)"
            style={styles.input}
            value={inviteLabel}
          />
          <TextInput
            keyboardType="number-pad"
            onChangeText={setInviteUsageLimit}
            placeholder="Usage limit (optional)"
            style={styles.input}
            value={inviteUsageLimit}
          />
          <Pressable
            disabled={creatingInviteLink}
            onPress={() => void handleCreateInviteLink()}
            style={[styles.primaryButton, creatingInviteLink && styles.buttonDisabled]}
          >
            <Text style={styles.primaryButtonText}>
              {creatingInviteLink ? "Creating..." : "Create invite link"}
            </Text>
          </Pressable>

          {loadingInviteLinks ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}

          <View style={styles.inviteLinksList}>
            {inviteLinks.map((link) => (
              <View key={link.inviteLinkId} style={styles.inviteLinkCard}>
                <Text style={styles.inviteLinkTitle}>{link.label || "Invite link"}</Text>
                <Text style={styles.inviteLinkMeta}>{link.shareUrl}</Text>
                <Text style={styles.inviteLinkMeta}>
                  Uses: {link.usageCount}
                  {typeof link.usageLimit === "number" ? ` / ${link.usageLimit}` : ""}
                </Text>
                {link.expiresAt ? (
                  <Text style={styles.inviteLinkMeta}>
                    Expires: {new Date(link.expiresAt).toLocaleString()}
                  </Text>
                ) : null}
                {link.revoked ? (
                  <Text style={styles.inviteLinkRevoked}>Revoked</Text>
                ) : (
                  <Pressable
                    disabled={revokingInviteLinkId === link.inviteLinkId}
                    onPress={() => void handleRevokeInviteLink(link.inviteLinkId)}
                    style={[
                      styles.inlineDangerButton,
                      revokingInviteLinkId === link.inviteLinkId && styles.buttonDisabled
                    ]}
                  >
                    <Text style={styles.inlineDangerText}>
                      {revokingInviteLinkId === link.inviteLinkId ? "Revoking..." : "Revoke"}
                    </Text>
                  </Pressable>
                )}
              </View>
            ))}
          </View>
        </View>
      ) : null}
    </View>
  );

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <View style={styles.headerText}>
          <Text style={styles.title}>Members</Text>
          <Text style={styles.subtitle}>{chatTitle}</Text>
        </View>
      </View>

      {loadingMembers ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={orderedMembers}
        keyExtractor={(item) => item.userId}
        ListHeaderComponent={headerContent}
        renderItem={({ item }) => {
          const isOwner = item.role === "OWNER";
          const isSelf = item.userId === currentUserId;
          const canMutateMember = canManageMembers && !isOwner && !isSelf;
          const canRestrictMember = canModerateMessages && !isOwner && !isSelf;
          const canEditPermissions = canManageMembers && !isOwner && !isSelf;
          const memberRestricted = !item.canSendMessages;
          const restrictionActive = restrictingUserId === item.userId;
          const permissionUpdateActive = updatingPermissionsUserId === item.userId;
          const permissionLabels = getPermissionLabels(item);

          return (
            <View style={styles.memberCard}>
              <View style={styles.memberHeader}>
                <Avatar uri={item.photoUrl} title={item.displayName} size={48} />
                <View style={styles.memberInfo}>
                  <Text style={styles.memberName}>{item.displayName}</Text>
                  <Text style={styles.memberMeta}>{item.phoneNumber ?? "phone-hidden"}</Text>
                  <Text style={styles.memberRole}>{item.role}</Text>
                </View>
                {isSelf ? <Text style={styles.meBadge}>You</Text> : null}
              </View>

              {permissionLabels.length > 0 ? (
                <View style={styles.permissionBadges}>
                  {permissionLabels.map((label) => (
                    <View key={`${item.userId}-${label}`} style={styles.permissionBadge}>
                      <Text style={styles.permissionBadgeText}>{label}</Text>
                    </View>
                  ))}
                </View>
              ) : null}

              {memberRestricted ? (
                <View style={styles.restrictionCard}>
                  <Text style={styles.restrictionTitle}>Read-only mode</Text>
                  <Text style={styles.restrictionText}>
                    {item.restrictedUntil
                      ? `Until ${new Date(item.restrictedUntil).toLocaleString()}`
                      : "No end time"}
                  </Text>
                  {item.restrictionReason ? (
                    <Text style={styles.restrictionText}>{item.restrictionReason}</Text>
                  ) : null}
                </View>
              ) : null}

              {canEditPermissions && item.role === "ADMIN" ? (
                <View style={styles.permissionEditor}>
                  <Text style={styles.permissionTitle}>Admin rights</Text>
                  <View style={styles.selectorOptions}>
                    <Pressable
                      disabled={permissionUpdateActive}
                      onPress={() =>
                        void handlePermissionChange(item.userId, {
                          canManageMembers: !item.canManageMembers
                        })
                      }
                      style={[
                        styles.selectorOption,
                        item.canManageMembers && styles.selectorOptionActive,
                        permissionUpdateActive && styles.buttonDisabled
                      ]}
                    >
                      <Text
                        style={[
                          styles.selectorOptionText,
                          item.canManageMembers && styles.selectorOptionTextActive
                        ]}
                      >
                        Members
                      </Text>
                    </Pressable>
                    <Pressable
                      disabled={permissionUpdateActive}
                      onPress={() =>
                        void handlePermissionChange(item.userId, {
                          canManageInviteLinks: !item.canManageInviteLinks
                        })
                      }
                      style={[
                        styles.selectorOption,
                        item.canManageInviteLinks && styles.selectorOptionActive,
                        permissionUpdateActive && styles.buttonDisabled
                      ]}
                    >
                      <Text
                        style={[
                          styles.selectorOptionText,
                          item.canManageInviteLinks && styles.selectorOptionTextActive
                        ]}
                      >
                        Invite links
                      </Text>
                    </Pressable>
                    <Pressable
                      disabled={permissionUpdateActive}
                      onPress={() =>
                        void handlePermissionChange(item.userId, {
                          canManageMessages: !item.canManageMessages
                        })
                      }
                      style={[
                        styles.selectorOption,
                        item.canManageMessages && styles.selectorOptionActive,
                        permissionUpdateActive && styles.buttonDisabled
                      ]}
                    >
                      <Text
                        style={[
                          styles.selectorOptionText,
                          item.canManageMessages && styles.selectorOptionTextActive
                        ]}
                      >
                        Moderate
                      </Text>
                    </Pressable>
                    <Pressable
                      disabled={permissionUpdateActive}
                      onPress={() =>
                        void handlePermissionChange(item.userId, {
                          canPinMessages: !item.canPinMessages
                        })
                      }
                      style={[
                        styles.selectorOption,
                        item.canPinMessages && styles.selectorOptionActive,
                        permissionUpdateActive && styles.buttonDisabled
                      ]}
                    >
                      <Text
                        style={[
                          styles.selectorOptionText,
                          item.canPinMessages && styles.selectorOptionTextActive
                        ]}
                      >
                        Pins
                      </Text>
                    </Pressable>
                      <Pressable
                        disabled={permissionUpdateActive}
                        onPress={() =>
                          void handlePermissionChange(item.userId, {
                            canApproveJoinRequests: !item.canApproveJoinRequests
                        })
                      }
                      style={[
                        styles.selectorOption,
                        item.canApproveJoinRequests && styles.selectorOptionActive,
                        permissionUpdateActive && styles.buttonDisabled
                      ]}
                    >
                      <Text
                        style={[
                          styles.selectorOptionText,
                          item.canApproveJoinRequests && styles.selectorOptionTextActive
                        ]}
                        >
                          Join requests
                        </Text>
                      </Pressable>
                      <Pressable
                        disabled={permissionUpdateActive}
                        onPress={() =>
                          void handlePermissionChange(item.userId, {
                            anonymousAdmin: !item.anonymousAdmin
                          })
                        }
                        style={[
                          styles.selectorOption,
                          item.anonymousAdmin && styles.selectorOptionActive,
                          permissionUpdateActive && styles.buttonDisabled
                        ]}
                      >
                        <Text
                          style={[
                            styles.selectorOptionText,
                            item.anonymousAdmin && styles.selectorOptionTextActive
                          ]}
                        >
                          Anonymous
                        </Text>
                      </Pressable>
                    </View>
                  </View>
                ) : null}

              {canEditPermissions && chat.chatType === "CHANNEL" ? (
                <View style={styles.permissionEditor}>
                  <Text style={styles.permissionTitle}>Posting rights</Text>
                  <Pressable
                    disabled={permissionUpdateActive}
                    onPress={() =>
                      void handlePermissionChange(item.userId, {
                        canPostMessages: !item.canPostMessages
                      })
                    }
                    style={[
                      styles.toggleCard,
                      item.canPostMessages && styles.toggleCardActive,
                      permissionUpdateActive && styles.buttonDisabled
                    ]}
                  >
                    <View style={styles.toggleBody}>
                      <Text style={styles.toggleTitle}>
                        {item.canPostMessages ? "Can publish in channel" : "Read-only subscriber"}
                      </Text>
                      <Text style={styles.toggleHint}>
                        Toggle whether this member can publish posts to the channel feed.
                      </Text>
                    </View>
                    <View
                      style={[styles.toggleBadge, item.canPostMessages && styles.toggleBadgeActive]}
                    >
                      <Text
                        style={[
                          styles.toggleBadgeText,
                          item.canPostMessages && styles.toggleBadgeTextActive
                        ]}
                      >
                        {item.canPostMessages ? "POST" : "READ"}
                      </Text>
                    </View>
                  </Pressable>
                </View>
              ) : null}

              {canMutateMember || canRestrictMember ? (
                <View style={styles.actionsRow}>
                  {canMutateMember && item.role === "MEMBER" ? (
                    <Pressable
                      onPress={() => void handleRoleChange(item.userId, "ADMIN")}
                      style={styles.inlineButton}
                    >
                      <Text style={styles.inlineButtonText}>Promote</Text>
                    </Pressable>
                  ) : null}

                  {canMutateMember && item.role !== "MEMBER" ? (
                    <Pressable
                      onPress={() => void handleRoleChange(item.userId, "MEMBER")}
                      style={styles.inlineButton}
                    >
                      <Text style={styles.inlineButtonText}>Demote</Text>
                    </Pressable>
                  ) : null}

                  {canRestrictMember ? (
                    <Pressable
                      disabled={restrictionActive}
                      onPress={() =>
                        void handleRestrictionChange(
                          item.userId,
                          memberRestricted,
                          memberRestricted
                            ? null
                            : new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
                          memberRestricted ? null : "Posting restricted by admin"
                        )
                      }
                      style={[styles.inlineButton, restrictionActive && styles.buttonDisabled]}
                    >
                      <Text style={styles.inlineButtonText}>
                        {restrictionActive
                          ? "Updating..."
                          : memberRestricted
                            ? "Unrestrict"
                            : "Restrict 24h"}
                      </Text>
                    </Pressable>
                  ) : null}

                  {canRestrictMember ? (
                    <Pressable
                      disabled={banningUserId === item.userId}
                      onPress={() => void handleBanMember(item.userId)}
                      style={[
                        styles.inlineDangerButton,
                        banningUserId === item.userId && styles.buttonDisabled
                      ]}
                    >
                      <Text style={styles.inlineDangerText}>
                        {banningUserId === item.userId ? "Banning..." : "Ban 7d"}
                      </Text>
                    </Pressable>
                  ) : null}

                  {canMutateMember ? (
                    <Pressable
                      onPress={() => void handleRemoveMember(item.userId)}
                      style={styles.inlineDangerButton}
                    >
                      <Text style={styles.inlineDangerText}>Remove</Text>
                    </Pressable>
                  ) : null}
                </View>
              ) : null}
            </View>
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
    gap: 12,
    marginBottom: 16
  },
  headerText: {
    flex: 1
  },
  title: {
    fontSize: 24,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    marginTop: 4,
    color: "#475569"
  },
  headerContent: {
    gap: 16
  },
  chatIdentityCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 14,
    alignItems: "center"
  },
  chatIdentityText: {
    alignItems: "center",
    gap: 4
  },
  chatIdentityTitle: {
    fontSize: 20,
    fontWeight: "700",
    color: "#0f172a"
  },
  chatIdentityMeta: {
    color: "#64748b"
  },
  photoActions: {
    width: "100%",
    gap: 10
  },
  adminCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16
  },
  analyticsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
    marginTop: 12
  },
  analyticsTile: {
    minWidth: 108,
    flexGrow: 1,
    borderRadius: 14,
    backgroundColor: "#f8fafc",
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 4
  },
  analyticsValue: {
    color: "#0f172a",
    fontSize: 20,
    fontWeight: "700"
  },
  analyticsLabel: {
    color: "#475569",
    fontSize: 12,
    fontWeight: "600"
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#0f172a",
    marginBottom: 10
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff",
    marginBottom: 10
  },
  aboutInput: {
    minHeight: 88,
    textAlignVertical: "top"
  },
  toggleCard: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#cbd5e1",
    backgroundColor: "#f8fafc",
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 10,
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
  selectorGroup: {
    gap: 8,
    marginBottom: 10
  },
  selectorLabel: {
    color: "#0f172a",
    fontWeight: "700"
  },
  selectorHint: {
    color: "#475569",
    lineHeight: 18
  },
  selectorOptions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8
  },
  selectorOption: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  selectorOptionActive: {
    backgroundColor: "#0f172a"
  },
  selectorOptionText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  selectorOptionTextActive: {
    color: "#ffffff"
  },
  selectorEmpty: {
    color: "#64748b"
  },
  selectorCurrent: {
    color: "#1d4ed8",
    fontWeight: "600"
  },
  loader: {
    marginVertical: 12
  },
  candidatesContent: {
    gap: 12,
    paddingVertical: 12
  },
  candidateCard: {
    width: 180,
    borderRadius: 16,
    backgroundColor: "#f8fafc",
    padding: 14,
    alignItems: "center"
  },
  candidateCardSelected: {
    borderWidth: 2,
    borderColor: "#0f172a"
  },
  candidateName: {
    fontSize: 16,
    fontWeight: "600",
    color: "#0f172a"
  },
  candidateMeta: {
    marginTop: 4,
    color: "#64748b"
  },
  inviteLinksList: {
    gap: 10,
    marginTop: 12
  },
  inviteLinkCard: {
    borderRadius: 14,
    backgroundColor: "#f8fafc",
    padding: 12,
    gap: 4
  },
  inviteLinkTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  inviteLinkMeta: {
    color: "#475569",
    fontSize: 12
  },
  inviteLinkRevoked: {
    color: "#b91c1c",
    fontWeight: "700",
    marginTop: 6
  },
  listContent: {
    gap: 12,
    paddingBottom: 20
  },
  memberCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 10
  },
  memberHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 12
  },
  memberInfo: {
    flex: 1
  },
  memberName: {
    fontSize: 18,
    fontWeight: "600",
    color: "#0f172a"
  },
  memberMeta: {
    marginTop: 2,
    color: "#64748b"
  },
  memberRole: {
    marginTop: 6,
    color: "#0f766e",
    fontWeight: "700"
  },
  permissionBadges: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8
  },
  permissionBadge: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  permissionBadgeText: {
    color: "#0f172a",
    fontSize: 12,
    fontWeight: "600"
  },
  permissionEditor: {
    gap: 8
  },
  permissionTitle: {
    color: "#475569",
    fontWeight: "700"
  },
  restrictionCard: {
    borderRadius: 14,
    backgroundColor: "#fff7ed",
    padding: 12,
    gap: 4
  },
  restrictionTitle: {
    color: "#9a3412",
    fontWeight: "700"
  },
  restrictionText: {
    color: "#9a3412",
    lineHeight: 18
  },
  meBadge: {
    alignSelf: "flex-start",
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    color: "#1d4ed8",
    overflow: "hidden",
    paddingHorizontal: 10,
    paddingVertical: 6,
    fontWeight: "700"
  },
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
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
  inlineButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  inlineButtonText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  inlineDangerButton: {
    borderRadius: 12,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  inlineDangerText: {
    color: "#b91c1c",
    fontWeight: "600"
  },
  buttonDisabled: {
    opacity: 0.6
  },
  errorText: {
    color: "#b91c1c",
    marginBottom: 12
  }
});
