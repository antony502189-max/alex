import type { PickedMediaFile } from "../../services/imagePicker";
import type { Contact } from "../../types";

export type StoryPreset = {
  backgroundFrom: string;
  backgroundTo: string;
  textColor: string;
};

export const STORY_PRESETS = [
  { backgroundFrom: "#0f172a", backgroundTo: "#2563eb", textColor: "#ffffff" },
  { backgroundFrom: "#7c3aed", backgroundTo: "#ec4899", textColor: "#ffffff" },
  { backgroundFrom: "#facc15", backgroundTo: "#fb923c", textColor: "#0f172a" },
  { backgroundFrom: "#14b8a6", backgroundTo: "#0f766e", textColor: "#ffffff" }
] satisfies StoryPreset[];

export const STORY_AUDIENCE_OPTIONS = [
  { value: "DEFAULT", label: "Account" },
  { value: "EVERYBODY", label: "Everybody" },
  { value: "CONTACTS", label: "Contacts" },
  { value: "CLOSE_FRIENDS", label: "Close friends" },
  { value: "CUSTOM", label: "Custom" }
] as const;

export type StoryAudience = (typeof STORY_AUDIENCE_OPTIONS)[number]["value"];

export function inferStoryDurationMs(file: PickedMediaFile | null) {
  if (!file || !file.type.toLowerCase().startsWith("video/")) {
    return null;
  }

  return file.durationMs ?? 15_000;
}

export function getCreateStoryPreviewLabel(
  text: string,
  selectedMedia: PickedMediaFile | null
) {
  const normalized = text.trim();
  if (normalized) {
    return normalized;
  }

  return selectedMedia ? "Add a caption if you want" : "Your story preview";
}

export function getCreateStoryAudienceHint(audience: StoryAudience) {
  switch (audience) {
    case "DEFAULT":
      return "Use the account-wide story privacy configured in your profile.";
    case "CONTACTS":
      return "Only saved contacts can watch this story.";
    case "CLOSE_FRIENDS":
      return "Pick a short-list for this story.";
    case "CUSTOM":
      return "Hand-pick who can view this story.";
    default:
      return "Visible to every account that can reach your profile.";
  }
}

export function getCreateStoryAudienceTitle(audience: StoryAudience) {
  return audience === "CLOSE_FRIENDS" ? "Close friends" : "Custom viewers";
}

export function getSelectedStoryContactsLabel(selectedContacts: Contact[]) {
  if (selectedContacts.length === 0) {
    return null;
  }

  return `Selected: ${selectedContacts.map((contact) => contact.displayName).join(", ")}`;
}

export function getCreateStoryMediaKind(selectedMedia: PickedMediaFile | null) {
  if (!selectedMedia) {
    return null;
  }

  const normalizedType = selectedMedia.type.toLowerCase();
  if (normalizedType.startsWith("image/")) {
    return "IMAGE";
  }
  if (normalizedType.startsWith("video/")) {
    return "VIDEO";
  }

  return null;
}
