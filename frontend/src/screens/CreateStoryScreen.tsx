import React, { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Image,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { api } from "../services/api";
import { pickSingleStoryMedia, type PickedMediaFile } from "../services/imagePicker";
import type { Contact, Story } from "../types";

type CreateStoryScreenProps = {
  onClose: () => void;
  onCreated: (story: Story) => void;
  token: string;
};

type StoryPreset = {
  backgroundFrom: string;
  backgroundTo: string;
  textColor: string;
};

const PRESETS = [
  { backgroundFrom: "#0f172a", backgroundTo: "#2563eb", textColor: "#ffffff" },
  { backgroundFrom: "#7c3aed", backgroundTo: "#ec4899", textColor: "#ffffff" },
  { backgroundFrom: "#facc15", backgroundTo: "#fb923c", textColor: "#0f172a" },
  { backgroundFrom: "#14b8a6", backgroundTo: "#0f766e", textColor: "#ffffff" }
] satisfies StoryPreset[];

const AUDIENCE_OPTIONS = [
  { value: "DEFAULT", label: "Account" },
  { value: "EVERYBODY", label: "Everybody" },
  { value: "CONTACTS", label: "Contacts" },
  { value: "CLOSE_FRIENDS", label: "Close friends" },
  { value: "CUSTOM", label: "Custom" }
] as const;

type StoryAudience = (typeof AUDIENCE_OPTIONS)[number]["value"];

function inferStoryDurationMs(file: PickedMediaFile | null) {
  if (!file || !file.type.toLowerCase().startsWith("video/")) {
    return null;
  }
  return 15_000;
}

export function CreateStoryScreen({
  onClose,
  onCreated,
  token
}: CreateStoryScreenProps) {
  const [text, setText] = useState("");
  const [preset, setPreset] = useState<StoryPreset>(PRESETS[0]);
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

  useEffect(() => {
    if (!requiresSelectedContacts) {
      return;
    }
    if (contacts.length > 0 || loadingContacts) {
      return;
    }

    let cancelled = false;
    setLoadingContacts(true);
    setError(null);
    api.getContacts(token)
      .then((nextContacts) => {
        if (!cancelled) {
          setContacts(nextContacts);
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Unable to load contacts");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingContacts(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [contacts.length, loadingContacts, requiresSelectedContacts, token]);

  async function handlePickMedia() {
    const file = await pickSingleStoryMedia();
    if (!file) {
      return;
    }
    setSelectedMedia(file);
  }

  function toggleViewer(userId: string) {
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
        text: text.trim() || null,
        backgroundFrom: preset.backgroundFrom,
        backgroundTo: preset.backgroundTo,
        textColor: preset.textColor,
        audience,
        allowedViewerUserIds: requiresSelectedContacts ? selectedViewerIds : undefined
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

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>New story</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View
          style={[
            styles.previewCard,
            {
              backgroundColor: preset.backgroundFrom,
              borderColor: preset.backgroundTo
            }
          ]}
        >
          {selectedMedia?.type.toLowerCase().startsWith("image/") ? (
            <Image source={{ uri: selectedMedia.uri }} style={styles.previewImage} resizeMode="cover" />
          ) : null}
          {selectedMedia?.type.toLowerCase().startsWith("video/") ? (
            <View style={styles.videoPreview}>
              <Text style={styles.videoPreviewTitle}>Video story</Text>
              <Text style={styles.videoPreviewText}>{selectedMedia.name}</Text>
              <Text style={styles.videoPreviewMeta}>Playback-ready after upload</Text>
            </View>
          ) : null}
          <View style={styles.previewOverlay}>
            <Text style={[styles.previewText, { color: preset.textColor }]}>
              {text.trim() || (selectedMedia ? "Add a caption if you want" : "Your story preview")}
            </Text>
          </View>
        </View>

        <View style={styles.actionsRow}>
          <Pressable onPress={() => void handlePickMedia()} style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>
              {selectedMedia ? "Replace media" : "Add photo/video"}
            </Text>
          </Pressable>
          {selectedMedia ? (
            <Pressable onPress={() => setSelectedMedia(null)} style={styles.secondaryButton}>
              <Text style={styles.secondaryButtonText}>Remove media</Text>
            </Pressable>
          ) : null}
        </View>

        <TextInput
          multiline
          onChangeText={setText}
          placeholder={selectedMedia ? "Caption (optional)" : "What is happening?"}
          style={styles.input}
          value={text}
        />

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Story look</Text>
          <View style={styles.presetsRow}>
            {PRESETS.map((item, index) => (
              <Pressable
                key={`story-preset-${index}`}
                onPress={() => setPreset(item)}
                style={[
                  styles.presetChip,
                  {
                    backgroundColor: item.backgroundFrom,
                    borderColor: item.backgroundTo
                  },
                  preset === item && styles.presetChipActive
                ]}
              >
                <Text style={[styles.presetChipText, { color: item.textColor }]}>Aa</Text>
              </Pressable>
            ))}
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Audience</Text>
          <View style={styles.audienceRow}>
            {AUDIENCE_OPTIONS.map((option) => (
              <Pressable
                key={option.value}
                onPress={() => setAudience(option.value)}
                style={[
                  styles.audienceChip,
                  audience === option.value && styles.audienceChipActive
                ]}
              >
                <Text
                  style={[
                    styles.audienceChipText,
                    audience === option.value && styles.audienceChipTextActive
                  ]}
                >
                  {option.label}
                </Text>
              </Pressable>
            ))}
          </View>
          <Text style={styles.sectionHint}>
            {audience === "DEFAULT"
              ? "Use the account-wide story privacy configured in your profile."
              : audience === "CONTACTS"
                ? "Only saved contacts can watch this story."
                : audience === "CLOSE_FRIENDS"
                  ? "Pick a short-list for this story."
                  : audience === "CUSTOM"
                    ? "Hand-pick who can view this story."
                    : "Visible to every account that can reach your profile."}
          </Text>
        </View>

        {requiresSelectedContacts ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>
              {audience === "CLOSE_FRIENDS" ? "Close friends" : "Custom viewers"}
            </Text>
            {loadingContacts ? <ActivityIndicator color="#0f172a" /> : null}
            {!loadingContacts && contacts.length === 0 ? (
              <Text style={styles.sectionHint}>Add contacts first to target a private story audience.</Text>
            ) : null}
            <View style={styles.contactGrid}>
              {contacts.map((contact) => {
                const selected = selectedViewerIds.includes(contact.userId);
                return (
                  <Pressable
                    key={contact.userId}
                    onPress={() => toggleViewer(contact.userId)}
                    style={[styles.contactChip, selected && styles.contactChipActive]}
                  >
                    <Text
                      style={[styles.contactChipText, selected && styles.contactChipTextActive]}
                    >
                      {contact.displayName}
                    </Text>
                    <Text
                      style={[styles.contactChipMeta, selected && styles.contactChipTextActive]}
                    >
                      {contact.username ? `@${contact.username}` : contact.phoneNumber ?? "contact"}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
            {selectedContacts.length > 0 ? (
              <Text style={styles.sectionHint}>
                Selected: {selectedContacts.map((contact) => contact.displayName).join(", ")}
              </Text>
            ) : null}
          </View>
        ) : null}

        {error ? <Text style={styles.errorText}>{error}</Text> : null}

        <Pressable
          disabled={!canSubmit}
          onPress={() => void handleCreate()}
          style={[styles.primaryButton, !canSubmit && styles.disabled]}
        >
          <Text style={styles.primaryButtonText}>
            {submitting ? "Publishing..." : "Publish story"}
          </Text>
        </Pressable>
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
  content: {
    gap: 18,
    paddingBottom: 32
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
  previewCard: {
    minHeight: 320,
    borderRadius: 28,
    borderWidth: 3,
    overflow: "hidden",
    justifyContent: "flex-end"
  },
  previewImage: {
    ...StyleSheet.absoluteFillObject
  },
  videoPreview: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "#0f172a",
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 24
  },
  videoPreviewTitle: {
    color: "#ffffff",
    fontSize: 26,
    fontWeight: "700"
  },
  videoPreviewText: {
    color: "#cbd5e1",
    marginTop: 10,
    textAlign: "center"
  },
  videoPreviewMeta: {
    color: "#94a3b8",
    marginTop: 8
  },
  previewOverlay: {
    padding: 24,
    backgroundColor: "rgba(15, 23, 42, 0.28)"
  },
  previewText: {
    fontSize: 26,
    fontWeight: "700",
    textAlign: "center"
  },
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12
  },
  input: {
    minHeight: 120,
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 18,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 12,
    textAlignVertical: "top"
  },
  section: {
    gap: 10
  },
  sectionTitle: {
    color: "#0f172a",
    fontSize: 16,
    fontWeight: "700"
  },
  sectionHint: {
    color: "#64748b",
    lineHeight: 18
  },
  presetsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12
  },
  presetChip: {
    width: 56,
    height: 56,
    borderRadius: 999,
    borderWidth: 3,
    alignItems: "center",
    justifyContent: "center"
  },
  presetChipActive: {
    transform: [{ scale: 1.08 }]
  },
  presetChipText: {
    fontWeight: "700"
  },
  audienceRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8
  },
  audienceChip: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  audienceChipActive: {
    backgroundColor: "#0f172a"
  },
  audienceChipText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  audienceChipTextActive: {
    color: "#ffffff"
  },
  contactGrid: {
    gap: 10
  },
  contactChip: {
    borderRadius: 16,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 12
  },
  contactChipActive: {
    backgroundColor: "#dbeafe",
    borderWidth: 1,
    borderColor: "#2563eb"
  },
  contactChipText: {
    color: "#0f172a",
    fontWeight: "700"
  },
  contactChipMeta: {
    color: "#64748b",
    marginTop: 4,
    fontSize: 12
  },
  contactChipTextActive: {
    color: "#1d4ed8"
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: {
    color: "#ffffff",
    fontWeight: "600"
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
  errorText: {
    color: "#b91c1c"
  },
  disabled: {
    opacity: 0.6
  }
});
