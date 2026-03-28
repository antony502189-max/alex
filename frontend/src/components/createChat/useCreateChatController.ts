import { useEffect, useMemo, useState } from "react";
import { api } from "../../services/api";
import type { ChatSummary, UserSearchResult } from "../../types";
import type { CreateChatMode } from "./createChatPresentation";
import { canSubmitCollectionChat } from "./createChatPresentation";

type UseCreateChatControllerParams = {
  mode: CreateChatMode;
  onCreated: (chat: ChatSummary) => void;
  token: string;
};

export function useCreateChatController({
  mode,
  onCreated,
  token
}: UseCreateChatControllerParams) {
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
    const normalized = query.trim();

    if (normalized.length < 2) {
      setResults([]);
      setLoading(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      setLoading(true);
      setError(null);
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
            setLoading(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [query, token]);

  function toggleUser(userId: string) {
    setSelectedUserIds((current) =>
      current.includes(userId) ? current.filter((id) => id !== userId) : [...current, userId]
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
    if (mode === "direct" || !canSubmitCollectionChat(mode, groupTitle, selectedUserIds.length)) {
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

  const canSubmit = useMemo(
    () =>
      mode !== "direct" && canSubmitCollectionChat(mode, groupTitle, selectedUserIds.length),
    [groupTitle, mode, selectedUserIds.length]
  );

  return {
    autoDeleteSeconds,
    canSubmit,
    error,
    forumEnabled,
    groupAbout,
    groupTitle,
    handleCreateCollectionChat,
    handleSelectDirect,
    joinRequiresApproval,
    loading,
    query,
    results,
    selectedUserIds,
    setAutoDeleteSeconds,
    setForumEnabled,
    setGroupAbout,
    setGroupTitle,
    setJoinRequiresApproval,
    setQuery,
    submitting,
    toggleUser
  };
}

export type CreateChatScreenController = ReturnType<typeof useCreateChatController>;
