import { useEffect, useMemo, useState } from "react";
import { pickSingleStoryMedia, type PickedMediaFile } from "../../services/imagePicker";
import { api } from "../../services/api";
import type { Contact, Story } from "../../types";
import {
  inferStoryDurationMs,
  STORY_PRESETS,
  type StoryAudience,
  type StoryPreset
} from "./createStoryPresentation";

type UseCreateStoryControllerParams = {
  onCreated: (story: Story) => void;
  token: string;
};

export function useCreateStoryController({
  onCreated,
  token
}: UseCreateStoryControllerParams) {
  const [text, setText] = useState("");
  const [preset, setPreset] = useState<StoryPreset>(STORY_PRESETS[0]);
  const [audience, setAudience] = useState<StoryAudience>("DEFAULT");
  const [selectedMedia, setSelectedMedia] = useState<PickedMediaFile | null>(null);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selectedViewerIds, setSelectedViewerIds] = useState<string[]>([]);
  const [loadingContacts, setLoadingContacts] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requiresSelectedContacts = audience === "CLOSE_FRIENDS" || audience === "CUSTOM";
  const canSubmit =
    !submitting &&
    (Boolean(text.trim()) || selectedMedia != null) &&
    (!requiresSelectedContacts || selectedViewerIds.length > 0);

  const selectedContacts = useMemo(
    () => contacts.filter((contact) => selectedViewerIds.includes(contact.userId)),
    [contacts, selectedViewerIds]
  );

  async function loadContacts() {
    if (contacts.length > 0 || loadingContacts) {
      return;
    }

    setLoadingContacts(true);
    setError(null);
    try {
      setContacts(await api.getContacts(token));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load contacts");
    } finally {
      setLoadingContacts(false);
    }
  }

  useEffect(() => {
    if (!requiresSelectedContacts) {
      return;
    }

    void loadContacts();
  }, [requiresSelectedContacts]);

  function handleSelectAudience(nextAudience: StoryAudience) {
    setAudience(nextAudience);
    if ((nextAudience === "CLOSE_FRIENDS" || nextAudience === "CUSTOM") && contacts.length === 0) {
      void loadContacts();
    }
  }

  async function handlePickMedia() {
    setError(null);
    const file = await pickSingleStoryMedia();
    if (!file) {
      return;
    }

    setSelectedMedia(file);
  }

  function handleRemoveMedia() {
    setSelectedMedia(null);
  }

  function handleToggleViewer(userId: string) {
    setSelectedViewerIds((current) =>
      current.includes(userId)
        ? current.filter((item) => item !== userId)
        : [...current, userId]
    );
  }

  async function handleCreate() {
    if (!canSubmit) {
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const payload = {
        allowedViewerUserIds: requiresSelectedContacts ? selectedViewerIds : undefined,
        audience,
        backgroundFrom: preset.backgroundFrom,
        backgroundTo: preset.backgroundTo,
        text: text.trim() || null,
        textColor: preset.textColor
      };

      const story = selectedMedia
        ? await api.createStoryWithMedia(token, {
            ...payload,
            durationMs: inferStoryDurationMs(selectedMedia),
            file: selectedMedia
          })
        : await api.createStory(token, payload);

      onCreated(story);
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : "Unable to create story");
    } finally {
      setSubmitting(false);
    }
  }

  return {
    audience,
    canSubmit,
    contacts,
    error,
    handleCreate,
    handlePickMedia,
    handleRemoveMedia,
    handleToggleViewer,
    loadingContacts,
    preset,
    requiresSelectedContacts,
    selectedContacts,
    selectedMedia,
    selectedViewerIds,
    setAudience: handleSelectAudience,
    setPreset,
    setText,
    submitting,
    text
  };
}

export type CreateStoryScreenController = ReturnType<typeof useCreateStoryController>;
