import { useCallback, useEffect, useMemo, useState } from "react";
import { useFocusEffect } from "@react-navigation/native";
import { AppState } from "react-native";
import { api } from "../../services/api";
import { pickSingleImage } from "../../services/imagePicker";
import {
  getUniquePresenceUserIds,
  mapPresenceByUserId
} from "../../services/presenceSync";
import { useAppStore } from "../../store/useAppStore";
import type {
  ChatAnalytics,
  ChatBan,
  ChatInviteLink,
  ChatJoinRequest,
  ChatMember,
  ChatSummary,
  UserPresenceStatus,
  UserSearchResult
} from "../../types";
import { sortMembers } from "./membersPresentation";

type UseMembersScreenControllerParams = {
  chat: ChatSummary;
  currentUserId: string;
  onChatLeft?: (chatId: string) => void;
  onChatUpdated?: (chat: ChatSummary) => void;
  onClose: () => void;
  onHistoryCleared?: (chatId: string) => void;
  token: string;
};

type UpdateMemberPermissionsPayload = {
  canManageMembers?: boolean;
  canManageInviteLinks?: boolean;
  canManageMessages?: boolean;
  canPinMessages?: boolean;
  canApproveJoinRequests?: boolean;
  canPostMessages?: boolean;
  anonymousAdmin?: boolean;
};

export function useMembersScreenController({
  chat,
  currentUserId,
  onChatLeft,
  onChatUpdated,
  onClose,
  onHistoryCleared,
  token
}: UseMembersScreenControllerParams) {
  const chats = useAppStore((state) => state.chats);
  const upsertChat = useAppStore((state) => state.upsertChat);

  const [members, setMembers] = useState<ChatMember[]>([]);
  const [memberPresenceByUserId, setMemberPresenceByUserId] = useState<
    Record<string, UserPresenceStatus>
  >({});
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
  const [updatingChatAction, setUpdatingChatAction] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [analytics, setAnalytics] = useState<ChatAnalytics | null>(null);

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
  const canLeaveChat = chat.chatType !== "SAVED";
  const resolvedMuted =
    Boolean(chat.mutedUntil) && new Date(chat.mutedUntil!).getTime() > Date.now();

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

  const orderedMembers = useMemo(() => sortMembers(members), [members]);
  const restrictedMembers = useMemo(
    () => orderedMembers.filter((member) => !member.canSendMessages),
    [orderedMembers]
  );

  function applyChatSummary(summary: ChatSummary) {
    upsertChat(summary);
    onChatUpdated?.(summary);
  }

  const refreshMembersPresence = useCallback(async (seedMembers?: ChatMember[]) => {
    const source = seedMembers ?? members;
    const userIds = getUniquePresenceUserIds(source.map((member) => member.userId));
    if (userIds.length === 0) {
      setMemberPresenceByUserId({});
      return;
    }

    try {
      const statuses = await api.getUsersPresence(token, userIds);
      setMemberPresenceByUserId(mapPresenceByUserId(statuses));
    } catch {
    }
  }, [members, token]);

  async function loadMembers() {
    setLoadingMembers(true);
    setError(null);
    try {
      const nextMembers = await api.getChatMembers(token, chat.chatId);
      setMembers(nextMembers);
      void refreshMembersPresence(nextMembers);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load members");
    } finally {
      setLoadingMembers(false);
    }
  }

  async function loadInviteLinks(allowInviteLinkAccess: boolean) {
    if ((chat.chatType !== "GROUP" && chat.chatType !== "CHANNEL") || !allowInviteLinkAccess) {
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
    if ((chat.chatType !== "GROUP" && chat.chatType !== "CHANNEL") || !allowModeration) {
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
    if ((chat.chatType !== "GROUP" && chat.chatType !== "CHANNEL") || !allowAnalyticsAccess) {
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
    setMemberPresenceByUserId({});
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

  useEffect(() => {
    const appStateSubscription = AppState.addEventListener("change", (nextState) => {
      if (nextState !== "active") {
        return;
      }

      void refreshMembersPresence().catch(() => undefined);
    });
    const intervalId = setInterval(() => {
      void refreshMembersPresence().catch(() => undefined);
    }, 45_000);

    return () => {
      appStateSubscription.remove();
      clearInterval(intervalId);
    };
  }, [refreshMembersPresence]);

  useFocusEffect(
    useCallback(() => {
      void refreshMembersPresence().catch(() => undefined);
    }, [refreshMembersPresence])
  );

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
      current.includes(userId) ? current.filter((id) => id !== userId) : [...current, userId]
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

  async function handlePermissionChange(userId: string, payload: UpdateMemberPermissionsPayload) {
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
        mutationError instanceof Error
          ? mutationError.message
          : "Unable to update member permissions"
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

  async function handleArchiveToggle() {
    setUpdatingChatAction("archive");
    setError(null);
    setNotice(null);
    try {
      const summary = await api.setChatArchived(token, chat.chatId, !chat.archived);
      applyChatSummary(summary);
      setNotice(summary.archived ? "Chat archived." : "Chat restored from archive.");
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to update archive state"
      );
    } finally {
      setUpdatingChatAction(null);
    }
  }

  async function handleMuteToggle() {
    setUpdatingChatAction("mute");
    setError(null);
    setNotice(null);
    try {
      const mutedUntil = resolvedMuted
        ? null
        : new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
      const summary = await api.muteChat(token, chat.chatId, mutedUntil);
      applyChatSummary(summary);
      setNotice(mutedUntil ? "Chat muted for 24 hours." : "Chat unmuted.");
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to update mute state"
      );
    } finally {
      setUpdatingChatAction(null);
    }
  }

  async function handleMarkUnread() {
    setUpdatingChatAction("unread");
    setError(null);
    setNotice(null);
    try {
      const summary = await api.markChatUnread(token, chat.chatId, true);
      applyChatSummary(summary);
      setNotice("Chat marked as unread.");
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to mark chat as unread"
      );
    } finally {
      setUpdatingChatAction(null);
    }
  }

  async function handlePinToggle() {
    setUpdatingChatAction("pin");
    setError(null);
    setNotice(null);
    try {
      const summary = chat.pinned
        ? await api.unpinChatFromList(token, chat.chatId)
        : await api.pinChatToList(token, chat.chatId);
      applyChatSummary(summary);
      setNotice(summary.pinned ? "Chat pinned to the top of the list." : "Chat unpinned.");
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "Unable to update chat pin");
    } finally {
      setUpdatingChatAction(null);
    }
  }

  async function handleClearHistory() {
    setUpdatingChatAction("clear");
    setError(null);
    setNotice(null);
    try {
      const result = await api.clearHistory(token, chat.chatId);
      onHistoryCleared?.(chat.chatId);
      setNotice(`History cleared: ${result.clearedMessageCount} messages removed.`);
    } catch (mutationError) {
      setError(
        mutationError instanceof Error ? mutationError.message : "Unable to clear chat history"
      );
    } finally {
      setUpdatingChatAction(null);
    }
  }

  async function handleLeaveChat() {
    if (!canLeaveChat) {
      return;
    }

    setUpdatingChatAction("leave");
    setError(null);
    setNotice(null);
    try {
      await api.leaveChat(token, chat.chatId);
      setNotice("You left the chat.");
      onChatLeft?.(chat.chatId);
      onClose();
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "Unable to leave chat");
    } finally {
      setUpdatingChatAction(null);
    }
  }

  async function handleReportChat() {
    setUpdatingChatAction("report");
    setError(null);
    setNotice(null);
    try {
      await api.reportChat(token, chat.chatId, {
        category: "ABUSE"
      });
      setNotice("Chat reported.");
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "Unable to report chat");
    } finally {
      setUpdatingChatAction(null);
    }
  }

  return {
    analytics,
    autoDeleteSeconds,
    availableDiscussionChats,
    bannedMembers,
    banningUserId,
    canApproveJoinRequests,
    canLeaveChat,
    canManageInviteLinks,
    canManageMembers,
    canModerateMessages,
    canViewAnalytics,
    chatAbout,
    chatPhotoUrl,
    chatTitle,
    commentsEnabled,
    creatingInviteLink,
    crossPostingEnabled,
    discussionChatId,
    error,
    forumEnabled,
    handleAddMembers,
    handleApproveJoinRequest,
    handleArchiveToggle,
    handleBanMember,
    handleClearHistory,
    handleCreateInviteLink,
    handleDeclineJoinRequest,
    handleLeaveChat,
    handleMarkUnread,
    handleMuteToggle,
    handlePermissionChange,
    handlePinToggle,
    handleRemoveMember,
    handleRemovePhoto,
    handleReportChat,
    handleRestrictionChange,
    handleRevokeInviteLink,
    handleRoleChange,
    handleSaveProfile,
    handleSavePublicUsername,
    handleUnbanMember,
    handleUploadPhoto,
    inviteLabel,
    inviteLinks,
    inviteUsageLimit,
    joinRequests,
    joinRequiresApproval,
    loadingAnalytics,
    loadingBans,
    loadingInviteLinks,
    loadingJoinRequests,
    loadingMembers,
    memberPresenceByUserId,
    mutating,
    notice,
    orderedMembers,
    processingJoinRequestUserId,
    publicUsername,
    query,
    reactionsEnabled,
    removingPhoto,
    resolvedMuted,
    restrictedMembers,
    restrictingUserId,
    results,
    revokingInviteLinkId,
    savingProfile,
    savingPublicUsername,
    searching,
    selectedUserIds,
    setAutoDeleteSeconds,
    setChatAbout,
    setChatTitle,
    setCommentsEnabled,
    setCrossPostingEnabled,
    setDiscussionChatId,
    setForumEnabled,
    setInviteLabel,
    setInviteUsageLimit,
    setJoinRequiresApproval,
    setPublicUsername,
    setQuery,
    setReactionsEnabled,
    setSlowModeSeconds,
    slowModeSeconds,
    toggleCandidate,
    unbanningUserId,
    updatingChatAction,
    updatingPermissionsUserId,
    uploadingPhoto
  };
}

export type MembersScreenController = ReturnType<typeof useMembersScreenController>;
