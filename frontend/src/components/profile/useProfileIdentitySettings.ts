import { useEffect, useState } from "react";
import { api } from "../../services/api";
import { pickSingleImage } from "../../services/imagePicker";
import { useAppStore } from "../../store/useAppStore";
import type {
  AuthSession,
  PrivacyExceptions,
  UserSearchResult
} from "../../types";
import type { ProfilePrivacyValue } from "./profilePresentation";

type UseProfileIdentitySettingsParams = {
  onError: (value: string | null) => void;
  token: string;
  setSession: (session: AuthSession) => void;
};

export function useProfileIdentitySettings({
  onError,
  token,
  setSession
}: UseProfileIdentitySettingsParams) {
  const [activePrivacyList, setActivePrivacyList] = useState<keyof PrivacyExceptions>(
    "phoneAllowedUserIds"
  );
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [about, setAbout] = useState("");
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [phonePrivacy, setPhonePrivacy] = useState<ProfilePrivacyValue>("EVERYBODY");
  const [lastSeenPrivacy, setLastSeenPrivacy] = useState<ProfilePrivacyValue>("EVERYBODY");
  const [storyPrivacy, setStoryPrivacy] = useState<ProfilePrivacyValue>("EVERYBODY");
  const [phoneAllowedUserIds, setPhoneAllowedUserIds] = useState<string[]>([]);
  const [phoneDisallowedUserIds, setPhoneDisallowedUserIds] = useState<string[]>([]);
  const [lastSeenAllowedUserIds, setLastSeenAllowedUserIds] = useState<string[]>([]);
  const [lastSeenDisallowedUserIds, setLastSeenDisallowedUserIds] = useState<string[]>([]);
  const [storyAllowedUserIds, setStoryAllowedUserIds] = useState<string[]>([]);
  const [storyDisallowedUserIds, setStoryDisallowedUserIds] = useState<string[]>([]);
  const [preferredLanguage, setPreferredLanguage] = useState("");
  const [translationTargetLanguage, setTranslationTargetLanguage] = useState("");
  const [privacySearchQuery, setPrivacySearchQuery] = useState("");
  const [privacySearchResults, setPrivacySearchResults] = useState<UserSearchResult[]>([]);
  const [knownUserLabels, setKnownUserLabels] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [searchingPrivacyUsers, setSearchingPrivacyUsers] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setLoading(true);
      onError(null);
      try {
        const [profile, privacyExceptions, languagePreferences] = await Promise.all([
          api.getMe(token),
          api.getPrivacyExceptions(token),
          api.getLanguagePreferences(token)
        ]);
        if (!cancelled) {
          setDisplayName(profile.displayName);
          setUsername(profile.username ?? "");
          setAbout(profile.about ?? "");
          setPhotoUrl(profile.photoUrl);
          setPhonePrivacy(profile.phonePrivacy);
          setLastSeenPrivacy(profile.lastSeenPrivacy);
          setStoryPrivacy(profile.storyPrivacy);
          setPhoneAllowedUserIds(privacyExceptions.phoneAllowedUserIds);
          setPhoneDisallowedUserIds(privacyExceptions.phoneDisallowedUserIds);
          setLastSeenAllowedUserIds(privacyExceptions.lastSeenAllowedUserIds);
          setLastSeenDisallowedUserIds(privacyExceptions.lastSeenDisallowedUserIds);
          setStoryAllowedUserIds(privacyExceptions.storyAllowedUserIds);
          setStoryDisallowedUserIds(privacyExceptions.storyDisallowedUserIds);
          setPreferredLanguage(languagePreferences.preferredLanguage ?? "");
          setTranslationTargetLanguage(languagePreferences.translationTargetLanguage ?? "");
        }
      } catch (loadError) {
        if (!cancelled) {
          onError(loadError instanceof Error ? loadError.message : "Unable to load profile");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      cancelled = true;
    };
  }, [onError, token]);

  useEffect(() => {
    let cancelled = false;
    const normalized = privacySearchQuery.trim();

    if (normalized.length < 2) {
      setPrivacySearchResults([]);
      setSearchingPrivacyUsers(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      setSearchingPrivacyUsers(true);
      onError(null);
      api.searchUsers(token, normalized)
        .then((results) => {
          if (cancelled) {
            return;
          }
          setPrivacySearchResults(results);
          setKnownUserLabels((current) => {
            const next = { ...current };
            for (const user of results) {
              next[user.userId] = user.username
                ? `${user.displayName} (@${user.username})`
                : user.displayName;
            }
            return next;
          });
        })
        .catch((searchError) => {
          if (!cancelled) {
            onError(searchError instanceof Error ? searchError.message : "Unable to search users");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setSearchingPrivacyUsers(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [onError, privacySearchQuery, token]);

  function buildPrivacyExceptions(): PrivacyExceptions {
    return {
      phoneAllowedUserIds,
      phoneDisallowedUserIds,
      lastSeenAllowedUserIds,
      lastSeenDisallowedUserIds,
      storyAllowedUserIds,
      storyDisallowedUserIds
    };
  }

  function removeFromList(list: string[], userId: string) {
    return list.filter((value) => value !== userId);
  }

  function addToList(list: string[], userId: string) {
    return list.includes(userId) ? list : [...list, userId];
  }

  function updatePrivacyExceptions(
    target: keyof PrivacyExceptions,
    userId: string,
    mode: "add" | "remove"
  ) {
    const current = buildPrivacyExceptions();
    const next = {
      ...current
    };

    next[target] = mode === "add" ? addToList(current[target], userId) : removeFromList(current[target], userId);

    if (mode === "add") {
      const oppositeTarget =
        target === "phoneAllowedUserIds"
          ? "phoneDisallowedUserIds"
          : target === "phoneDisallowedUserIds"
            ? "phoneAllowedUserIds"
            : target === "lastSeenAllowedUserIds"
              ? "lastSeenDisallowedUserIds"
              : target === "lastSeenDisallowedUserIds"
                ? "lastSeenAllowedUserIds"
                : target === "storyAllowedUserIds"
                  ? "storyDisallowedUserIds"
                  : "storyAllowedUserIds";
      next[oppositeTarget] = removeFromList(current[oppositeTarget], userId);
    }

    setPhoneAllowedUserIds(next.phoneAllowedUserIds);
    setPhoneDisallowedUserIds(next.phoneDisallowedUserIds);
    setLastSeenAllowedUserIds(next.lastSeenAllowedUserIds);
    setLastSeenDisallowedUserIds(next.lastSeenDisallowedUserIds);
    setStoryAllowedUserIds(next.storyAllowedUserIds);
    setStoryDisallowedUserIds(next.storyDisallowedUserIds);
  }

  function handleAddPrivacyException(user: UserSearchResult) {
    setKnownUserLabels((current) => ({
      ...current,
      [user.userId]: user.username ? `${user.displayName} (@${user.username})` : user.displayName
    }));
    updatePrivacyExceptions(activePrivacyList, user.userId, "add");
  }

  function handleRemovePrivacyException(target: keyof PrivacyExceptions, userId: string) {
    updatePrivacyExceptions(target, userId, "remove");
  }

  function resolvePrivacyUserLabel(userId: string) {
    return knownUserLabels[userId] ?? userId.slice(0, 8);
  }

  async function handleSave() {
    setSaving(true);
    onError(null);
    try {
      const [profile] = await Promise.all([
        api.updateMe(token, {
          displayName: displayName.trim(),
          username: username.trim() || undefined,
          about: about.trim()
        }),
        api.updatePrivacy(token, {
          phonePrivacy,
          lastSeenPrivacy,
          storyPrivacy
        }),
        api.updatePrivacyExceptions(token, buildPrivacyExceptions()),
        api.updateLanguagePreferences(token, {
          preferredLanguage: preferredLanguage.trim() || null,
          translationTargetLanguage: translationTargetLanguage.trim() || null
        })
      ]);
      const latestSession = useAppStore.getState().session;
      if (latestSession) {
        setSession({
          ...latestSession,
          displayName: profile.displayName,
          username: profile.username
        });
      }
      setPhotoUrl(profile.photoUrl);
    } catch (saveError) {
      onError(saveError instanceof Error ? saveError.message : "Unable to save profile");
    } finally {
      setSaving(false);
    }
  }

  async function handleUploadPhoto() {
    if (uploadingPhoto) {
      return;
    }

    const file = await pickSingleImage();
    if (!file) {
      return;
    }

    setUploadingPhoto(true);
    onError(null);
    try {
      const profile = await api.uploadMyPhoto(token, file);
      setPhotoUrl(profile.photoUrl);
    } catch (uploadError) {
      onError(
        uploadError instanceof Error ? uploadError.message : "Unable to upload profile photo"
      );
    } finally {
      setUploadingPhoto(false);
    }
  }

  async function handleRemovePhoto() {
    if (removingPhoto) {
      return;
    }

    setRemovingPhoto(true);
    onError(null);
    try {
      const profile = await api.deleteMyPhoto(token);
      setPhotoUrl(profile.photoUrl);
    } catch (removeError) {
      onError(
        removeError instanceof Error ? removeError.message : "Unable to remove profile photo"
      );
    } finally {
      setRemovingPhoto(false);
    }
  }

  return {
    activePrivacyList,
    about,
    displayName,
    handleAddPrivacyException,
    handleRemovePhoto,
    handleRemovePrivacyException,
    handleSave,
    handleUploadPhoto,
    lastSeenPrivacy,
    lastSeenAllowedUserIds,
    lastSeenDisallowedUserIds,
    loading,
    phonePrivacy,
    phoneAllowedUserIds,
    phoneDisallowedUserIds,
    photoUrl,
    preferredLanguage,
    privacySearchQuery,
    privacySearchResults,
    removingPhoto,
    resolvePrivacyUserLabel,
    saving,
    searchingPrivacyUsers,
    setActivePrivacyList,
    setAbout,
    setDisplayName,
    setLastSeenPrivacy,
    setPhonePrivacy,
    setPreferredLanguage,
    setPrivacySearchQuery,
    setStoryPrivacy,
    setTranslationTargetLanguage,
    setUsername,
    storyPrivacy,
    storyAllowedUserIds,
    storyDisallowedUserIds,
    translationTargetLanguage,
    uploadingPhoto,
    username
  };
}
