import React, { useEffect, useState } from "react";
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import { pickSingleImage } from "../services/imagePicker";
import { useAppStore } from "../store/useAppStore";

type PrivacyValue = "EVERYBODY" | "CONTACTS" | "NOBODY";

type ProfileScreenProps = {
  token: string;
  onClose: () => void;
  onOpenSessions: () => void;
  onOpenBotDeveloper: () => void;
};

export function ProfileScreen({
  token,
  onClose,
  onOpenSessions,
  onOpenBotDeveloper
}: ProfileScreenProps) {
  const session = useAppStore((state) => state.session);
  const setSession = useAppStore((state) => state.setSession);

  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [about, setAbout] = useState("");
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [phonePrivacy, setPhonePrivacy] = useState<PrivacyValue>("EVERYBODY");
  const [lastSeenPrivacy, setLastSeenPrivacy] = useState<PrivacyValue>("EVERYBODY");
  const [storyPrivacy, setStoryPrivacy] = useState<PrivacyValue>("EVERYBODY");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false);
  const [twoFactorHint, setTwoFactorHint] = useState("");
  const [twoFactorEnabledAt, setTwoFactorEnabledAt] = useState<string | null>(null);
  const [twoFactorPassword, setTwoFactorPassword] = useState("");
  const [twoFactorDisablePassword, setTwoFactorDisablePassword] = useState("");
  const [updatingTwoFactor, setUpdatingTwoFactor] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const [profile, twoFactorStatus] = await Promise.all([
          api.getMe(token),
          api.getTwoFactorStatus(token)
        ]);
        if (!cancelled) {
          setDisplayName(profile.displayName);
          setUsername(profile.username ?? "");
          setAbout(profile.about ?? "");
          setPhotoUrl(profile.photoUrl);
          setPhonePrivacy(profile.phonePrivacy);
          setLastSeenPrivacy(profile.lastSeenPrivacy);
          setStoryPrivacy(profile.storyPrivacy);
          setTwoFactorEnabled(twoFactorStatus.enabled);
          setTwoFactorHint(twoFactorStatus.hint ?? "");
          setTwoFactorEnabledAt(twoFactorStatus.enabledAt);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Unable to load profile");
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
  }, [token]);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const profile = await api.updateMe(token, {
        displayName: displayName.trim(),
        username: username.trim() || undefined,
        about: about.trim()
      });
      const next = await api.updatePrivacy(token, {
        phonePrivacy,
        lastSeenPrivacy,
        storyPrivacy
      });
      const latestSession = useAppStore.getState().session;
      if (latestSession) {
        setSession({
          ...latestSession,
          displayName: profile.displayName,
          username: next.username
        });
      }
      setPhotoUrl(next.photoUrl);
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to save profile");
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
    setError(null);
    try {
      const profile = await api.uploadMyPhoto(token, file);
      setPhotoUrl(profile.photoUrl);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Unable to upload profile photo");
    } finally {
      setUploadingPhoto(false);
    }
  }

  async function handleRemovePhoto() {
    if (removingPhoto) {
      return;
    }

    setRemovingPhoto(true);
    setError(null);
    try {
      const profile = await api.deleteMyPhoto(token);
      setPhotoUrl(profile.photoUrl);
    } catch (removeError) {
      setError(removeError instanceof Error ? removeError.message : "Unable to remove profile photo");
    } finally {
      setRemovingPhoto(false);
    }
  }

  async function handleEnableTwoFactor() {
    setUpdatingTwoFactor(true);
    setError(null);
    try {
      const nextStatus = await api.enableTwoFactor(token, {
        password: twoFactorPassword,
        hint: twoFactorHint.trim() || undefined
      });
      setTwoFactorEnabled(nextStatus.enabled);
      setTwoFactorHint(nextStatus.hint ?? "");
      setTwoFactorEnabledAt(nextStatus.enabledAt);
      setTwoFactorPassword("");
      const latestSession = useAppStore.getState().session;
      if (latestSession) {
        setSession({
          ...latestSession,
          trustedSession: true
        });
      }
    } catch (twoFactorError) {
      setError(twoFactorError instanceof Error ? twoFactorError.message : "Unable to enable two-factor");
    } finally {
      setUpdatingTwoFactor(false);
    }
  }

  async function handleDisableTwoFactor() {
    setUpdatingTwoFactor(true);
    setError(null);
    try {
      const nextStatus = await api.disableTwoFactor(token, {
        password: twoFactorDisablePassword
      });
      setTwoFactorEnabled(nextStatus.enabled);
      setTwoFactorHint(nextStatus.hint ?? "");
      setTwoFactorEnabledAt(nextStatus.enabledAt);
      setTwoFactorDisablePassword("");
    } catch (twoFactorError) {
      setError(twoFactorError instanceof Error ? twoFactorError.message : "Unable to disable two-factor");
    } finally {
      setUpdatingTwoFactor(false);
    }
  }

  function renderPrivacyRow(
    label: string,
    value: PrivacyValue,
    onChange: (next: PrivacyValue) => void
  ) {
    return (
      <View style={styles.privacySection}>
        <Text style={styles.sectionTitle}>{label}</Text>
        <View style={styles.privacyOptions}>
          {(["EVERYBODY", "CONTACTS", "NOBODY"] as PrivacyValue[]).map((option) => (
            <Pressable
              key={option}
              onPress={() => onChange(option)}
              style={[styles.choiceChip, value === option && styles.choiceChipActive]}
            >
              <Text
                style={[styles.choiceChipText, value === option && styles.choiceChipTextActive]}
              >
                {option}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Profile</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {loading ? <Text style={styles.metaText}>Loading...</Text> : null}
        {error ? <Text style={styles.errorText}>{error}</Text> : null}

        <View style={styles.photoCard}>
          <Avatar uri={photoUrl} title={displayName || session?.displayName || "Profile"} size={92} />
          <View style={styles.photoActions}>
            <Pressable
              disabled={uploadingPhoto}
              onPress={() => void handleUploadPhoto()}
              style={[styles.secondaryCta, uploadingPhoto && styles.disabled]}
            >
              <Text style={styles.secondaryCtaText}>
                {uploadingPhoto ? "Uploading..." : "Change photo"}
              </Text>
            </Pressable>
            <Pressable
              disabled={removingPhoto || !photoUrl}
              onPress={() => void handleRemovePhoto()}
              style={[styles.dangerButton, (removingPhoto || !photoUrl) && styles.disabled]}
            >
              <Text style={styles.dangerButtonText}>
                {removingPhoto ? "Removing..." : "Remove photo"}
              </Text>
            </Pressable>
          </View>
        </View>

        <TextInput
          onChangeText={setDisplayName}
          placeholder="Display name"
          style={styles.input}
          value={displayName}
        />
        <TextInput
          autoCapitalize="none"
          onChangeText={setUsername}
          placeholder="Username"
          style={styles.input}
          value={username}
        />
        <TextInput
          multiline
          onChangeText={setAbout}
          placeholder="About"
          style={[styles.input, styles.aboutInput]}
          value={about}
        />

        {renderPrivacyRow("Phone privacy", phonePrivacy, setPhonePrivacy)}
        {renderPrivacyRow("Last seen privacy", lastSeenPrivacy, setLastSeenPrivacy)}
        {renderPrivacyRow("Story privacy", storyPrivacy, setStoryPrivacy)}

        <View style={styles.privacySection}>
          <Text style={styles.sectionTitle}>Two-factor password</Text>
          <Text style={styles.metaText}>
            {twoFactorEnabled
              ? `Enabled${twoFactorEnabledAt ? ` since ${new Date(twoFactorEnabledAt).toLocaleString()}` : ""}.`
              : "Require a password after OTP verification."}
          </Text>
          <Text style={styles.metaText}>
            Trusted sessions can approve QR logins for new devices.
          </Text>
          {twoFactorEnabled ? (
            <>
              {twoFactorHint ? <Text style={styles.metaText}>Hint: {twoFactorHint}</Text> : null}
              <TextInput
                onChangeText={setTwoFactorDisablePassword}
                placeholder="Current two-factor password"
                secureTextEntry
                style={styles.input}
                value={twoFactorDisablePassword}
              />
              <Pressable
                disabled={updatingTwoFactor || !twoFactorDisablePassword.trim()}
                onPress={() => void handleDisableTwoFactor()}
                style={[styles.dangerButton, (updatingTwoFactor || !twoFactorDisablePassword.trim()) && styles.disabled]}
              >
                <Text style={styles.dangerButtonText}>
                  {updatingTwoFactor ? "Updating..." : "Disable two-factor"}
                </Text>
              </Pressable>
            </>
          ) : (
            <>
              <TextInput
                onChangeText={setTwoFactorPassword}
                placeholder="New two-factor password"
                secureTextEntry
                style={styles.input}
                value={twoFactorPassword}
              />
              <TextInput
                onChangeText={setTwoFactorHint}
                placeholder="Password hint (optional)"
                style={styles.input}
                value={twoFactorHint}
              />
              <Pressable
                disabled={updatingTwoFactor || !twoFactorPassword.trim()}
                onPress={() => void handleEnableTwoFactor()}
                style={[styles.secondaryCta, (updatingTwoFactor || !twoFactorPassword.trim()) && styles.disabled]}
              >
                <Text style={styles.secondaryCtaText}>
                  {updatingTwoFactor ? "Updating..." : "Enable two-factor"}
                </Text>
              </Pressable>
            </>
          )}
        </View>

        <Pressable onPress={onOpenSessions} style={styles.secondaryCta}>
          <Text style={styles.secondaryCtaText}>Active sessions</Text>
        </Pressable>

        <Pressable onPress={onOpenBotDeveloper} style={styles.secondaryCta}>
          <Text style={styles.secondaryCtaText}>Bot developer console</Text>
        </Pressable>

        <Pressable
          disabled={saving}
          onPress={handleSave}
          style={[styles.primaryButton, saving && styles.disabled]}
        >
          <Text style={styles.primaryButtonText}>{saving ? "Saving..." : "Save profile"}</Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#f8fafc", padding: 20 },
  header: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 16 },
  title: { fontSize: 24, fontWeight: "700", color: "#0f172a" },
  content: { gap: 12, paddingBottom: 24 },
  photoCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 18,
    gap: 14,
    alignItems: "center"
  },
  photoActions: {
    width: "100%",
    gap: 10
  },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff"
  },
  aboutInput: { minHeight: 100, textAlignVertical: "top" },
  privacySection: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16
  },
  sectionTitle: { color: "#0f172a", fontWeight: "700", marginBottom: 10 },
  privacyOptions: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  choiceChip: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  choiceChipActive: { backgroundColor: "#0f172a" },
  choiceChipText: { color: "#0f172a", fontWeight: "600" },
  choiceChipTextActive: { color: "#ffffff" },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: { color: "#ffffff", fontWeight: "600" },
  secondaryCta: {
    borderRadius: 14,
    backgroundColor: "#dbeafe",
    paddingVertical: 14,
    alignItems: "center"
  },
  secondaryCtaText: { color: "#1d4ed8", fontWeight: "700" },
  dangerButton: {
    borderRadius: 14,
    backgroundColor: "#fee2e2",
    paddingVertical: 14,
    alignItems: "center"
  },
  dangerButtonText: {
    color: "#b91c1c",
    fontWeight: "700"
  },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: { color: "#0f172a", fontWeight: "600" },
  metaText: { color: "#64748b" },
  errorText: { color: "#b91c1c" },
  disabled: { opacity: 0.6 }
});
