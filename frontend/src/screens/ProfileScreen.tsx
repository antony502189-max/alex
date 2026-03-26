import React, { useEffect, useState } from "react";
import {
  Platform,
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
import { devicePasskeys } from "../services/devicePasskeys";
import { pickSingleImage } from "../services/imagePicker";
import { registerForPushNotificationsAsync } from "../services/notifications";
import { useAppStore } from "../store/useAppStore";
import type {
  AccountDeletionJob,
  AccountExportJob,
  AuthFlowResult,
  AuthSecurityEvent,
  AuthSession,
  DevicePasskey,
  PhoneChangeChallenge
} from "../types";

type PrivacyValue = "EVERYBODY" | "CONTACTS" | "NOBODY";

type ProfileScreenProps = {
  token: string;
  onClose: () => void;
  onOpenSessions: () => void;
  onOpenBotDeveloper?: () => void;
};

function toAuthSession(result: AuthFlowResult): AuthSession | null {
  if (
    !result.authenticated ||
    !result.token ||
    !result.sessionId ||
    !result.userId ||
    !result.phoneNumber ||
    !result.displayName
  ) {
    return null;
  }

  return {
    token: result.token,
    refreshToken: result.refreshToken,
    sessionId: result.sessionId,
    userId: result.userId,
    phoneNumber: result.phoneNumber,
    displayName: result.displayName,
    username: result.username,
    accessTokenExpiresAt: result.accessTokenExpiresAt,
    refreshTokenExpiresAt: result.refreshTokenExpiresAt,
    authMethod: result.authMethod,
    trustedSession: Boolean(result.trustedSession)
  };
}

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
  const [securityEvents, setSecurityEvents] = useState<AuthSecurityEvent[]>([]);
  const [newPhoneNumber, setNewPhoneNumber] = useState("");
  const [phoneChangeChallenge, setPhoneChangeChallenge] = useState<PhoneChangeChallenge | null>(null);
  const [phoneChangeCode, setPhoneChangeCode] = useState("");
  const [changingPhone, setChangingPhone] = useState(false);
  const [accountExportJob, setAccountExportJob] = useState<AccountExportJob | null>(null);
  const [accountDeletionJob, setAccountDeletionJob] = useState<AccountDeletionJob | null>(null);
  const [deletionReason, setDeletionReason] = useState("");
  const [deletionDelayDays, setDeletionDelayDays] = useState("30");
  const [exportingAccount, setExportingAccount] = useState(false);
  const [schedulingDeletion, setSchedulingDeletion] = useState(false);
  const [refreshingPush, setRefreshingPush] = useState(false);
  const [clearingPush, setClearingPush] = useState(false);
  const [localPasskeys, setLocalPasskeys] = useState<DevicePasskey[]>([]);
  const [passkeyLabel, setPasskeyLabel] = useState("");
  const [loadingPasskeys, setLoadingPasskeys] = useState(false);
  const [registeringPasskey, setRegisteringPasskey] = useState(false);
  const [removingPasskeyId, setRemovingPasskeyId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function refreshDevicePasskeys(phoneNumber = session?.phoneNumber ?? null) {
    if (!phoneNumber?.trim()) {
      setLocalPasskeys([]);
      setLoadingPasskeys(false);
      return;
    }

    setLoadingPasskeys(true);
    try {
      const nextPasskeys = await devicePasskeys.listForPhoneNumber(phoneNumber);
      setLocalPasskeys(nextPasskeys);
    } catch (passkeyError) {
      setError(passkeyError instanceof Error ? passkeyError.message : "Unable to load device passkeys");
    } finally {
      setLoadingPasskeys(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const [profile, twoFactorStatus, nextSecurityEvents] = await Promise.all([
          api.getMe(token),
          api.getTwoFactorStatus(token),
          api.getSecurityEvents(token)
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
          setSecurityEvents(nextSecurityEvents);
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

  useEffect(() => {
    void refreshDevicePasskeys();
  }, [session?.phoneNumber]);

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

  async function handleRefreshPushToken() {
    setRefreshingPush(true);
    setError(null);
    setNotice(null);
    try {
      const pushToken = await registerForPushNotificationsAsync();
      if (!pushToken) {
        setNotice("Push token was not granted on this device.");
        return;
      }
      await api.updateCurrentPushToken(token, {
        provider: "EXPO",
        pushToken
      });
      setNotice("Push notifications refreshed for this device.");
    } catch (pushError) {
      setError(pushError instanceof Error ? pushError.message : "Unable to refresh push token");
    } finally {
      setRefreshingPush(false);
    }
  }

  async function handleClearPushToken() {
    setClearingPush(true);
    setError(null);
    setNotice(null);
    try {
      await api.clearCurrentPushToken(token);
      setNotice("Push notifications disabled for this device.");
    } catch (pushError) {
      setError(pushError instanceof Error ? pushError.message : "Unable to clear push token");
    } finally {
      setClearingPush(false);
    }
  }

  async function handleRegisterPasskey() {
    if (!session?.phoneNumber?.trim()) {
      return;
    }

    setRegisteringPasskey(true);
    setError(null);
    setNotice(null);
    let localPasskey: DevicePasskey | null = null;
    try {
      const options = await api.requestPasskeyRegistrationOptions(token);
      localPasskey = await devicePasskeys.create(
        session.phoneNumber,
        passkeyLabel.trim() || `${Platform.OS} device key`
      );
      await api.verifyPasskeyRegistration(token, {
        challengeId: options.challengeId,
        challenge: options.challenge,
        credentialId: localPasskey.credentialId,
        publicKey: localPasskey.publicKey,
        label: localPasskey.label ?? undefined,
        transports: "internal",
        signCount: 0
      });
      await refreshDevicePasskeys(session.phoneNumber);
      setPasskeyLabel("");
      setNotice("Device passkey registered for this phone.");
    } catch (passkeyError) {
      if (localPasskey) {
        await devicePasskeys.remove(localPasskey.credentialId).catch(() => undefined);
      }
      setError(passkeyError instanceof Error ? passkeyError.message : "Unable to register passkey");
    } finally {
      setRegisteringPasskey(false);
    }
  }

  async function handleRemovePasskey(credentialId: string) {
    setRemovingPasskeyId(credentialId);
    setError(null);
    setNotice(null);
    try {
      await devicePasskeys.remove(credentialId);
      await refreshDevicePasskeys();
      setNotice("Local device passkey removed from this phone.");
    } catch (passkeyError) {
      setError(passkeyError instanceof Error ? passkeyError.message : "Unable to remove passkey");
    } finally {
      setRemovingPasskeyId(null);
    }
  }

  async function handleRequestPhoneChange() {
    if (!newPhoneNumber.trim()) {
      return;
    }

    setChangingPhone(true);
    setError(null);
    setNotice(null);
    try {
      const challenge = await api.requestPhoneChange(token, {
        newPhoneNumber: newPhoneNumber.trim()
      });
      setPhoneChangeChallenge(challenge);
      setPhoneChangeCode(challenge.debugCode ?? "");
      setNotice(`Verification code requested for ${challenge.newPhoneNumber}.`);
    } catch (phoneError) {
      setError(phoneError instanceof Error ? phoneError.message : "Unable to request phone change");
    } finally {
      setChangingPhone(false);
    }
  }

  async function handleVerifyPhoneChange() {
    if (!phoneChangeChallenge || !phoneChangeCode.trim()) {
      return;
    }

    setChangingPhone(true);
    setError(null);
    setNotice(null);
    try {
      const result = await api.verifyPhoneChange(token, {
        challengeId: phoneChangeChallenge.challengeId,
        code: phoneChangeCode.trim()
      });
      const nextSession = toAuthSession(result);
      if (!nextSession) {
        throw new Error("Phone change completed without a valid session payload");
      }
      setSession(nextSession);
      setPhoneChangeChallenge(null);
      setPhoneChangeCode("");
      setNewPhoneNumber("");
      setNotice(`Phone number updated to ${nextSession.phoneNumber}.`);
    } catch (phoneError) {
      setError(phoneError instanceof Error ? phoneError.message : "Unable to verify phone change");
    } finally {
      setChangingPhone(false);
    }
  }

  async function handleExportAccount() {
    setExportingAccount(true);
    setError(null);
    setNotice(null);
    try {
      const job = await api.exportAccount(token, {
        format: "JSON",
        includeAttachmentsMetadata: true
      });
      setAccountExportJob(job);
      setNotice("Account export requested.");
    } catch (exportError) {
      setError(exportError instanceof Error ? exportError.message : "Unable to request account export");
    } finally {
      setExportingAccount(false);
    }
  }

  async function handleScheduleDeletion() {
    const parsedDelayDays = Number.parseInt(deletionDelayDays.trim(), 10);
    if (!Number.isFinite(parsedDelayDays) || parsedDelayDays <= 0) {
      setError("Deletion delay must be a positive number of days.");
      return;
    }

    setSchedulingDeletion(true);
    setError(null);
    setNotice(null);
    try {
      const job = await api.scheduleAccountDeletion(token, {
        reason: deletionReason.trim() || undefined,
        delayDays: parsedDelayDays
      });
      setAccountDeletionJob(job);
      setNotice("Account deletion scheduled.");
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Unable to schedule account deletion");
    } finally {
      setSchedulingDeletion(false);
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
        {notice ? <Text style={styles.noticeText}>{notice}</Text> : null}

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

        <View style={styles.privacySection}>
          <Text style={styles.sectionTitle}>This device</Text>
          <Text style={styles.metaText}>
            Refresh or disable push notifications for the current mobile session.
          </Text>
          <View style={styles.buttonGroup}>
            <Pressable
              disabled={refreshingPush}
              onPress={() => void handleRefreshPushToken()}
              style={[styles.secondaryCta, refreshingPush && styles.disabled]}
            >
              <Text style={styles.secondaryCtaText}>
                {refreshingPush ? "Refreshing..." : "Refresh push token"}
              </Text>
            </Pressable>
            <Pressable
              disabled={clearingPush}
              onPress={() => void handleClearPushToken()}
              style={[styles.dangerButton, clearingPush && styles.disabled]}
            >
              <Text style={styles.dangerButtonText}>
                {clearingPush ? "Disabling..." : "Disable push on this device"}
              </Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.privacySection}>
          <Text style={styles.sectionTitle}>Device passkeys</Text>
          <Text style={styles.metaText}>
            Register a passkey on this device for faster sign-in later. This MVP stores the device credential locally and links it to the current account.
          </Text>
          <TextInput
            onChangeText={setPasskeyLabel}
            placeholder="Passkey label (optional)"
            style={styles.input}
            value={passkeyLabel}
          />
          <View style={styles.buttonGroup}>
            <Pressable
              disabled={registeringPasskey}
              onPress={() => void handleRegisterPasskey()}
              style={[styles.secondaryCta, registeringPasskey && styles.disabled]}
            >
              <Text style={styles.secondaryCtaText}>
                {registeringPasskey ? "Registering..." : "Register passkey"}
              </Text>
            </Pressable>
            <Pressable
              disabled={loadingPasskeys}
              onPress={() => void refreshDevicePasskeys()}
              style={[styles.secondaryButton, loadingPasskeys && styles.disabled]}
            >
              <Text style={styles.secondaryButtonText}>
                {loadingPasskeys ? "Refreshing..." : "Refresh passkeys"}
              </Text>
            </Pressable>
          </View>
          <View style={styles.securityEventsList}>
            {localPasskeys.length === 0 ? (
              <Text style={styles.metaText}>No device passkeys registered on this phone yet.</Text>
            ) : (
              localPasskeys.map((passkey) => (
                <View key={passkey.credentialId} style={styles.securityEventCard}>
                  <Text style={styles.securityEventTitle}>
                    {passkey.label ?? "Unnamed device passkey"}
                  </Text>
                  <Text style={styles.securityEventMeta}>
                    Added {new Date(passkey.createdAt).toLocaleString()}
                  </Text>
                  <Text style={styles.securityEventMeta}>
                    {passkey.lastUsedAt
                      ? `Last used ${new Date(passkey.lastUsedAt).toLocaleString()}`
                      : "Not used yet"}
                  </Text>
                  <Text style={styles.securityEventMeta}>
                    Removing it here clears only the local device copy.
                  </Text>
                  <Pressable
                    disabled={removingPasskeyId === passkey.credentialId}
                    onPress={() => void handleRemovePasskey(passkey.credentialId)}
                    style={[
                      styles.dangerButton,
                      removingPasskeyId === passkey.credentialId && styles.disabled
                    ]}
                  >
                    <Text style={styles.dangerButtonText}>
                      {removingPasskeyId === passkey.credentialId ? "Removing..." : "Remove local passkey"}
                    </Text>
                  </Pressable>
                </View>
              ))
            )}
          </View>
        </View>

        <View style={styles.privacySection}>
          <Text style={styles.sectionTitle}>Change phone number</Text>
          <Text style={styles.metaText}>
            Request a verification code for a new phone number, then confirm it here.
          </Text>
          <TextInput
            keyboardType="phone-pad"
            onChangeText={setNewPhoneNumber}
            placeholder="New phone number"
            style={styles.input}
            value={newPhoneNumber}
          />
          {phoneChangeChallenge ? (
            <>
              <Text style={styles.metaText}>
                Code requested for {phoneChangeChallenge.newPhoneNumber}. Expires at{" "}
                {new Date(phoneChangeChallenge.expiresAt).toLocaleString()}.
              </Text>
              {phoneChangeChallenge.debugCode ? (
                <Text style={styles.metaText}>Debug code: {phoneChangeChallenge.debugCode}</Text>
              ) : null}
              <TextInput
                keyboardType="number-pad"
                onChangeText={setPhoneChangeCode}
                placeholder="Verification code"
                style={styles.input}
                value={phoneChangeCode}
              />
            </>
          ) : null}
          <View style={styles.buttonGroup}>
            <Pressable
              disabled={changingPhone || !newPhoneNumber.trim()}
              onPress={() => void handleRequestPhoneChange()}
              style={[styles.secondaryCta, (changingPhone || !newPhoneNumber.trim()) && styles.disabled]}
            >
              <Text style={styles.secondaryCtaText}>
                {changingPhone && !phoneChangeChallenge ? "Requesting..." : "Request code"}
              </Text>
            </Pressable>
            {phoneChangeChallenge ? (
              <Pressable
                disabled={changingPhone || !phoneChangeCode.trim()}
                onPress={() => void handleVerifyPhoneChange()}
                style={[styles.primaryButton, (changingPhone || !phoneChangeCode.trim()) && styles.disabled]}
              >
                <Text style={styles.primaryButtonText}>
                  {changingPhone ? "Verifying..." : "Verify new number"}
                </Text>
              </Pressable>
            ) : null}
          </View>
        </View>

        <View style={styles.privacySection}>
          <Text style={styles.sectionTitle}>Security events</Text>
          <Text style={styles.metaText}>Recent authentication and device activity.</Text>
          <View style={styles.securityEventsList}>
            {securityEvents.length === 0 ? (
              <Text style={styles.metaText}>No recent security events.</Text>
            ) : (
              securityEvents.slice(0, 8).map((event) => (
                <View key={event.eventId} style={styles.securityEventCard}>
                  <Text style={styles.securityEventTitle}>
                    {event.eventType} {event.severity ? `• ${event.severity}` : ""}
                  </Text>
                  <Text style={styles.securityEventMeta}>
                    {[event.deviceName, event.platform, event.appVersion].filter(Boolean).join(" • ") || "Unknown device"}
                  </Text>
                  <Text style={styles.securityEventMeta}>
                    {[event.ipAddress, event.userAgent].filter(Boolean).join(" • ")}
                  </Text>
                  {event.details ? <Text style={styles.securityEventMeta}>{event.details}</Text> : null}
                  <Text style={styles.securityEventMeta}>
                    {new Date(event.createdAt).toLocaleString()}
                  </Text>
                </View>
              ))
            )}
          </View>
        </View>

        <View style={styles.privacySection}>
          <Text style={styles.sectionTitle}>Account export</Text>
          <Text style={styles.metaText}>
            Request a JSON export of your account data and attachment metadata.
          </Text>
          {accountExportJob ? (
            <View style={styles.inlineCard}>
              <Text style={styles.metaText}>Status: {accountExportJob.status}</Text>
              <Text style={styles.metaText}>Messages: {accountExportJob.messageCount}</Text>
              {accountExportJob.artifactLocation ? (
                <Text style={styles.metaText}>Artifact: {accountExportJob.artifactLocation}</Text>
              ) : null}
            </View>
          ) : null}
          <Pressable
            disabled={exportingAccount}
            onPress={() => void handleExportAccount()}
            style={[styles.secondaryCta, exportingAccount && styles.disabled]}
          >
            <Text style={styles.secondaryCtaText}>
              {exportingAccount ? "Requesting..." : "Request account export"}
            </Text>
          </Pressable>
        </View>

        <View style={styles.privacySection}>
          <Text style={styles.sectionTitle}>Account deletion</Text>
          <Text style={styles.metaText}>
            Schedule account deletion with an optional reason and delay window.
          </Text>
          <TextInput
            onChangeText={setDeletionReason}
            placeholder="Reason (optional)"
            style={styles.input}
            value={deletionReason}
          />
          <TextInput
            keyboardType="number-pad"
            onChangeText={setDeletionDelayDays}
            placeholder="Delay in days"
            style={styles.input}
            value={deletionDelayDays}
          />
          {accountDeletionJob ? (
            <View style={styles.inlineCard}>
              <Text style={styles.metaText}>Status: {accountDeletionJob.status}</Text>
              {accountDeletionJob.scheduledFor ? (
                <Text style={styles.metaText}>
                  Scheduled for: {new Date(accountDeletionJob.scheduledFor).toLocaleString()}
                </Text>
              ) : null}
              {accountDeletionJob.reason ? (
                <Text style={styles.metaText}>Reason: {accountDeletionJob.reason}</Text>
              ) : null}
            </View>
          ) : null}
          <Pressable
            disabled={schedulingDeletion}
            onPress={() => void handleScheduleDeletion()}
            style={[styles.dangerButton, schedulingDeletion && styles.disabled]}
          >
            <Text style={styles.dangerButtonText}>
              {schedulingDeletion ? "Scheduling..." : "Schedule account deletion"}
            </Text>
          </Pressable>
        </View>

        <Pressable onPress={onOpenSessions} style={styles.secondaryCta}>
          <Text style={styles.secondaryCtaText}>Active sessions</Text>
        </Pressable>

        {onOpenBotDeveloper ? (
          <Pressable onPress={onOpenBotDeveloper} style={styles.secondaryCta}>
            <Text style={styles.secondaryCtaText}>Bot developer console</Text>
          </Pressable>
        ) : null}

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
  noticeText: { color: "#166534" },
  buttonGroup: { gap: 10, marginTop: 12 },
  inlineCard: {
    borderRadius: 14,
    backgroundColor: "#eff6ff",
    padding: 12,
    gap: 4
  },
  securityEventsList: {
    gap: 10,
    marginTop: 12
  },
  securityEventCard: {
    borderRadius: 14,
    backgroundColor: "#f8fafc",
    padding: 12,
    gap: 4
  },
  securityEventTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  securityEventMeta: {
    color: "#64748b",
    fontSize: 12,
    lineHeight: 18
  },
  errorText: { color: "#b91c1c" },
  disabled: { opacity: 0.6 }
});
