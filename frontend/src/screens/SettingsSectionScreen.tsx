import React, { useEffect, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import { ProfileAccountDeletionSection } from "../components/profile/ProfileAccountDeletionSection";
import { ProfileAccountExportSection } from "../components/profile/ProfileAccountExportSection";
import { ProfileDeviceSection } from "../components/profile/ProfileDeviceSection";
import { ProfilePasskeysSection } from "../components/profile/ProfilePasskeysSection";
import { ProfilePhoneChangeSection } from "../components/profile/ProfilePhoneChangeSection";
import { ProfilePhotoSection } from "../components/profile/ProfilePhotoSection";
import { ProfilePrivacyCard } from "../components/profile/ProfilePrivacyCard";
import { ProfileSecurityEventsSection } from "../components/profile/ProfileSecurityEventsSection";
import { ProfileTwoFactorSection } from "../components/profile/ProfileTwoFactorSection";
import { useProfileScreenController } from "../components/profile/useProfileScreenController";
import { AppBanner } from "../components/ui/AppBanner";
import { AppButton } from "../components/ui/AppButton";
import { AppChip } from "../components/ui/AppChip";
import { AppHeader } from "../components/ui/AppHeader";
import { AppScreen } from "../components/ui/AppScreen";
import { AppTextField } from "../components/ui/AppTextField";
import { AppToggleCard } from "../components/ui/AppToggleCard";
import { ScreenFeedback } from "../components/ui/ScreenFeedback";
import { ScreenScrollView } from "../components/ui/ScreenScrollView";
import { SectionCard } from "../components/ui/SectionCard";
import { api } from "../services/api";
import { appColors, appSpacing } from "../theme/tokens";
import type { SettingsSectionId } from "../navigation/types";
import type { BlockedUser, PrivacyExceptions } from "../types";

type SettingsSectionScreenProps = {
  onAddAccount?: () => void;
  onClose: () => void;
  onOpenBotDeveloper?: () => void;
  onOpenSessions?: () => void;
  section: SettingsSectionId;
  token: string;
};

type PrivacyTarget = keyof PrivacyExceptions;

const PRIVACY_TARGETS: Array<{ key: PrivacyTarget; label: string; tone: "success" | "danger" }> = [
  { key: "phoneAllowedUserIds", label: "Phone allow", tone: "success" },
  { key: "phoneDisallowedUserIds", label: "Phone deny", tone: "danger" },
  { key: "lastSeenAllowedUserIds", label: "Last seen allow", tone: "success" },
  { key: "lastSeenDisallowedUserIds", label: "Last seen deny", tone: "danger" },
  { key: "storyAllowedUserIds", label: "Story allow", tone: "success" },
  { key: "storyDisallowedUserIds", label: "Story deny", tone: "danger" }
];

function getSectionMeta(section: SettingsSectionId) {
  switch (section) {
    case "PROFILE":
      return { title: "Profile", subtitle: "Identity, photo and public-facing account fields" };
    case "PRIVACY_SECURITY":
      return { title: "Privacy & Security", subtitle: "Visibility rules, 2FA, phone change and account controls" };
    case "DEVICES":
      return { title: "Devices", subtitle: "Current device permissions, passkeys and sessions" };
    case "NOTIFICATIONS":
      return { title: "Notifications & Sounds", subtitle: "Local delivery, previews, sounds and vibration" };
    case "DATA_STORAGE":
      return { title: "Data & Storage", subtitle: "Auto-download, autoplay and local media retention" };
    case "APPEARANCE":
      return { title: "Appearance & Chat", subtitle: "List density, previews and message input behavior" };
    case "LANGUAGE":
      return { title: "Language", subtitle: "Preferred UI and translation target languages" };
    case "BLOCKED_PRIVACY":
      return { title: "Blocked Users & Exceptions", subtitle: "Blocked contacts and explicit privacy allow or deny lists" };
    case "HELP":
      return { title: "Help & Privacy", subtitle: "Transparent consumer disclosure for server-side storage" };
    default:
      return { title: "Settings", subtitle: null };
  }
}

function SectionSaveButton({
  busy,
  label,
  onPress
}: {
  busy?: boolean;
  label: string;
  onPress: () => void;
}) {
  return <AppButton onPress={onPress} variant="primary">{busy ? "Saving..." : label}</AppButton>;
}

export function SettingsSectionScreen({
  onAddAccount,
  onClose,
  onOpenBotDeveloper,
  onOpenSessions,
  section,
  token
}: SettingsSectionScreenProps) {
  const controller = useProfileScreenController({ token });
  const meta = getSectionMeta(section);
  const [blockedUsers, setBlockedUsers] = useState<BlockedUser[]>([]);
  const [loadingBlockedUsers, setLoadingBlockedUsers] = useState(false);
  const [unblockingUserId, setUnblockingUserId] = useState<string | null>(null);
  const {
    acknowledgePrivacyDisclosure,
    appearanceSettings,
    dataStorageSettings,
    disclosureState,
    error,
    identity,
    notice,
    passkeys,
    notificationSettings,
    security,
    session,
    setError,
    setNotice,
    updateAppearanceSettings,
    updateDataStorageSettings,
    updateNotificationSettings
  } = controller;

  useEffect(() => {
    if (section !== "BLOCKED_PRIVACY") {
      return;
    }

    let cancelled = false;
    setLoadingBlockedUsers(true);
    setError(null);
    void api
      .getBlockedUsers(token)
      .then((nextBlockedUsers) => {
        if (!cancelled) {
          setBlockedUsers(nextBlockedUsers);
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Unable to load blocked users");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingBlockedUsers(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [section, setError, token]);

  async function handleUnblockUser(userId: string) {
    setUnblockingUserId(userId);
    setError(null);
    setNotice(null);
    try {
      const nextBlockedUsers = await api.unblockUser(token, userId);
      setBlockedUsers(nextBlockedUsers);
      setNotice("User unblocked.");
    } catch (unblockError) {
      setError(unblockError instanceof Error ? unblockError.message : "Unable to unblock user");
    } finally {
      setUnblockingUserId(null);
    }
  }

  function renderProfileSection() {
    return (
      <>
        <ProfilePhotoSection
          onRemovePhoto={() => void identity.handleRemovePhoto()}
          onUploadPhoto={() => void identity.handleUploadPhoto()}
          photoUrl={identity.photoUrl}
          removingPhoto={identity.removingPhoto}
          title={identity.displayName || session?.displayName || "Profile"}
          uploadingPhoto={identity.uploadingPhoto}
        />
        <SectionCard description="Main fields shown to other people across chats and search." title="Identity">
          <AppTextField onChangeText={identity.setDisplayName} placeholder="Display name" value={identity.displayName} />
          <AppTextField autoCapitalize="none" onChangeText={identity.setUsername} placeholder="Username" value={identity.username} />
          <AppTextField multiline onChangeText={identity.setAbout} placeholder="About" style={styles.multiLine} value={identity.about} />
          <SectionSaveButton busy={identity.saving} label="Save profile" onPress={() => void identity.handleSave()} />
        </SectionCard>
      </>
    );
  }

  function renderPrivacySecuritySection() {
    return (
      <>
        <ProfilePrivacyCard label="Phone privacy" onChange={identity.setPhonePrivacy} value={identity.phonePrivacy} />
        <ProfilePrivacyCard label="Last seen privacy" onChange={identity.setLastSeenPrivacy} value={identity.lastSeenPrivacy} />
        <ProfilePrivacyCard label="Story privacy" onChange={identity.setStoryPrivacy} value={identity.storyPrivacy} />
        <SectionCard description="Persist the current visibility rules to the server." title="Privacy rules">
          <SectionSaveButton busy={identity.saving} label="Save privacy" onPress={() => void identity.handleSave()} />
        </SectionCard>
        <ProfileTwoFactorSection
          onChangeDisablePassword={security.setTwoFactorDisablePassword}
          onChangeHint={security.setTwoFactorHint}
          onChangePassword={security.setTwoFactorPassword}
          onDisableTwoFactor={() => void security.handleDisableTwoFactor()}
          onEnableTwoFactor={() => void security.handleEnableTwoFactor()}
          twoFactorDisablePassword={security.twoFactorDisablePassword}
          twoFactorEnabled={security.twoFactorEnabled}
          twoFactorEnabledAt={security.twoFactorEnabledAt}
          twoFactorHint={security.twoFactorHint}
          twoFactorPassword={security.twoFactorPassword}
          updatingTwoFactor={security.updatingTwoFactor}
        />
        <ProfilePhoneChangeSection
          changingPhone={security.changingPhone}
          newPhoneNumber={security.newPhoneNumber}
          onChangeCode={security.setPhoneChangeCode}
          onChangePhoneNumber={security.setNewPhoneNumber}
          onRequestCode={() => void security.handleRequestPhoneChange()}
          onVerifyCode={() => void security.handleVerifyPhoneChange()}
          phoneChangeChallenge={security.phoneChangeChallenge}
          phoneChangeCode={security.phoneChangeCode}
        />
        <ProfileAccountExportSection
          accountExportJob={security.accountExportJob}
          exportingAccount={security.exportingAccount}
          onExportAccount={() => void security.handleExportAccount()}
        />
        <ProfileAccountDeletionSection
          accountDeletionJob={security.accountDeletionJob}
          deletionDelayDays={security.deletionDelayDays}
          deletionReason={security.deletionReason}
          onChangeDelayDays={security.setDeletionDelayDays}
          onChangeReason={security.setDeletionReason}
          onScheduleDeletion={() => void security.handleScheduleDeletion()}
          schedulingDeletion={security.schedulingDeletion}
        />
        <ProfileSecurityEventsSection events={security.securityEvents} />
      </>
    );
  }

  function renderDevicesSection() {
    return (
      <>
        <ProfileDeviceSection
          clearingPush={security.clearingPush}
          onClearPushToken={() => void security.handleClearPushToken()}
          onRefreshPushToken={() => void security.handleRefreshPushToken()}
          refreshingPush={security.refreshingPush}
        />
        <ProfilePasskeysSection
          loadingPasskeys={passkeys.loadingPasskeys}
          localPasskeys={passkeys.localPasskeys}
          onChangePasskeyLabel={passkeys.setPasskeyLabel}
          onRefreshPasskeys={() => void passkeys.refreshDevicePasskeys()}
          onRegisterPasskey={() => void passkeys.handleRegisterPasskey()}
          onRemovePasskey={(credentialId) => void passkeys.handleRemovePasskey(credentialId)}
          passkeyLabel={passkeys.passkeyLabel}
          registeringPasskey={passkeys.registeringPasskey}
          removingPasskeyId={passkeys.removingPasskeyId}
        />
        <SectionCard description="Server sessions stay separate from locally cached accounts on this device." title="Sessions">
          <View style={styles.inlineActions}>
            {onOpenSessions ? <AppButton onPress={onOpenSessions} variant="secondary">Open active sessions</AppButton> : null}
            {onAddAccount ? <AppButton onPress={onAddAccount} variant="secondary">Add account</AppButton> : null}
            {onOpenBotDeveloper ? <AppButton onPress={onOpenBotDeveloper} variant="secondary">Bot developer</AppButton> : null}
          </View>
        </SectionCard>
      </>
    );
  }

  function renderNotificationsSection() {
    return (
      <SectionCard description="These toggles are stored locally per account until dedicated backend settings endpoints are wired." title="Notifications">
        <View style={styles.cardStack}>
          <AppToggleCard active={notificationSettings.privateChatsEnabled} description="Receive notifications for private dialogs." onPress={() => updateNotificationSettings({ privateChatsEnabled: !notificationSettings.privateChatsEnabled })} title="Private chats" />
          <AppToggleCard active={notificationSettings.groupChatsEnabled} description="Receive notifications for groups." onPress={() => updateNotificationSettings({ groupChatsEnabled: !notificationSettings.groupChatsEnabled })} title="Groups" />
          <AppToggleCard active={notificationSettings.channelChatsEnabled} description="Receive notifications for channels." onPress={() => updateNotificationSettings({ channelChatsEnabled: !notificationSettings.channelChatsEnabled })} title="Channels" />
          <AppToggleCard active={notificationSettings.storyNotificationsEnabled} description="Receive notifications for stories and story interactions." onPress={() => updateNotificationSettings({ storyNotificationsEnabled: !notificationSettings.storyNotificationsEnabled })} title="Stories" />
          <AppToggleCard active={notificationSettings.includeMessagePreview} description="Show sender and message previews in local notifications." onPress={() => updateNotificationSettings({ includeMessagePreview: !notificationSettings.includeMessagePreview })} title="Message previews" />
          <AppToggleCard active={notificationSettings.reactionNotificationsEnabled} description="Notify when reactions or quick feedback arrive." onPress={() => updateNotificationSettings({ reactionNotificationsEnabled: !notificationSettings.reactionNotificationsEnabled })} title="Reaction notifications" />
          <AppToggleCard active={notificationSettings.inAppSoundsEnabled} description="Play sounds while using the app." onPress={() => updateNotificationSettings({ inAppSoundsEnabled: !notificationSettings.inAppSoundsEnabled })} title="In-app sounds" />
          <AppToggleCard active={notificationSettings.vibrateEnabled} description="Use vibration for notification delivery on supported devices." onPress={() => updateNotificationSettings({ vibrateEnabled: !notificationSettings.vibrateEnabled })} title="Vibration" />
        </View>
      </SectionCard>
    );
  }

  function renderDataStorageSection() {
    return (
      <>
        <SectionCard description="These preferences stay local to the active account on this device." title="Downloads and playback">
          <View style={styles.cardStack}>
            <AppToggleCard active={dataStorageSettings.autoDownloadOnCellular} description="Allow media downloads on cellular networks." onPress={() => updateDataStorageSettings({ autoDownloadOnCellular: !dataStorageSettings.autoDownloadOnCellular })} title="Auto-download on cellular" />
            <AppToggleCard active={dataStorageSettings.autoDownloadOnWifi} description="Allow media downloads on Wi-Fi." onPress={() => updateDataStorageSettings({ autoDownloadOnWifi: !dataStorageSettings.autoDownloadOnWifi })} title="Auto-download on Wi-Fi" />
            <AppToggleCard active={dataStorageSettings.autoplayGifs} description="Autoplay GIF attachments in chats and shared media." onPress={() => updateDataStorageSettings({ autoplayGifs: !dataStorageSettings.autoplayGifs })} title="Autoplay GIFs" />
            <AppToggleCard active={dataStorageSettings.autoplayVideos} description="Autoplay inline videos when supported." onPress={() => updateDataStorageSettings({ autoplayVideos: !dataStorageSettings.autoplayVideos })} title="Autoplay videos" />
            <AppToggleCard active={dataStorageSettings.saveIncomingPhotosToGallery} description="Store downloaded incoming photos in the system gallery." onPress={() => updateDataStorageSettings({ saveIncomingPhotosToGallery: !dataStorageSettings.saveIncomingPhotosToGallery })} title="Save incoming photos" />
            <AppToggleCard active={dataStorageSettings.useLessDataForCalls} description="Prefer reduced bandwidth in voice and video calls." onPress={() => updateDataStorageSettings({ useLessDataForCalls: !dataStorageSettings.useLessDataForCalls })} title="Use less data for calls" />
          </View>
        </SectionCard>
        <SectionCard description="Choose how long locally downloaded media should be kept." title="Retention">
          <AppTextField keyboardType="numeric" onChangeText={(value) => {
            const parsed = Number.parseInt(value, 10);
            updateDataStorageSettings({ keepDownloadedMediaDays: Number.isFinite(parsed) && parsed > 0 ? parsed : 30 });
          }} placeholder="Days to keep downloaded media" value={String(dataStorageSettings.keepDownloadedMediaDays)} />
        </SectionCard>
      </>
    );
  }

  function renderAppearanceSection() {
    return (
      <SectionCard description="Account-scoped chat presentation settings for this device." title="Appearance">
        <View style={styles.cardStack}>
          <AppToggleCard active={appearanceSettings.compactChatList} description="Reduce chat row spacing in the inbox." onPress={() => updateAppearanceSettings({ compactChatList: !appearanceSettings.compactChatList })} title="Compact chat list" />
          <AppToggleCard active={appearanceSettings.showChatAvatars} description="Show chat avatars in the conversation list." onPress={() => updateAppearanceSettings({ showChatAvatars: !appearanceSettings.showChatAvatars })} title="Show avatars" />
          <AppToggleCard active={appearanceSettings.showLinkPreviews} description="Expand link previews when message content supports it." onPress={() => updateAppearanceSettings({ showLinkPreviews: !appearanceSettings.showLinkPreviews })} title="Link previews" />
          <AppToggleCard active={appearanceSettings.enterSendsMessage} description="Send a message when pressing Enter instead of creating a line break." onPress={() => updateAppearanceSettings({ enterSendsMessage: !appearanceSettings.enterSendsMessage })} title="Enter sends message" />
          <AppToggleCard active={appearanceSettings.largeEmojiEnabled} description="Render larger emoji-heavy messages when eligible." onPress={() => updateAppearanceSettings({ largeEmojiEnabled: !appearanceSettings.largeEmojiEnabled })} title="Large emoji" />
          <AppToggleCard active={appearanceSettings.animatedStickerLoops} description="Allow animated sticker and emoji loops." onPress={() => updateAppearanceSettings({ animatedStickerLoops: !appearanceSettings.animatedStickerLoops })} title="Animated sticker loops" />
        </View>
      </SectionCard>
    );
  }

  function renderLanguageSection() {
    return (
      <SectionCard description="These language values are already backed by the existing profile APIs." title="Language">
        <AppTextField autoCapitalize="none" onChangeText={identity.setPreferredLanguage} placeholder="Preferred language, for example en or ru" value={identity.preferredLanguage} />
        <AppTextField autoCapitalize="none" onChangeText={identity.setTranslationTargetLanguage} placeholder="Translation target language" value={identity.translationTargetLanguage} />
        <SectionSaveButton busy={identity.saving} label="Save language" onPress={() => void identity.handleSave()} />
      </SectionCard>
    );
  }

  function renderBlockedPrivacySection() {
    const privacyLists = [
      { key: "phoneAllowedUserIds" as const, label: "Phone allow list", tone: "success" as const, userIds: identity.phoneAllowedUserIds },
      { key: "phoneDisallowedUserIds" as const, label: "Phone deny list", tone: "danger" as const, userIds: identity.phoneDisallowedUserIds },
      { key: "lastSeenAllowedUserIds" as const, label: "Last seen allow list", tone: "success" as const, userIds: identity.lastSeenAllowedUserIds },
      { key: "lastSeenDisallowedUserIds" as const, label: "Last seen deny list", tone: "danger" as const, userIds: identity.lastSeenDisallowedUserIds },
      { key: "storyAllowedUserIds" as const, label: "Story allow list", tone: "success" as const, userIds: identity.storyAllowedUserIds },
      { key: "storyDisallowedUserIds" as const, label: "Story deny list", tone: "danger" as const, userIds: identity.storyDisallowedUserIds }
    ];

    return (
      <>
        <SectionCard description="Blocked contacts are loaded from the existing user APIs." title="Blocked users">
          {loadingBlockedUsers ? <Text style={styles.metaText}>Loading blocked users...</Text> : null}
          {blockedUsers.length === 0 && !loadingBlockedUsers ? <Text style={styles.metaText}>No blocked users.</Text> : null}
          <View style={styles.blockedList}>
            {blockedUsers.map((user) => (
              <View key={user.userId} style={styles.blockedUserCard}>
                <View style={styles.blockedUserBody}>
                  <Text style={styles.blockedUserName}>{user.displayName}</Text>
                  <Text style={styles.metaText}>{user.username ? `@${user.username}` : user.phoneNumber ?? "No public identity"}</Text>
                </View>
                <AppButton disabled={unblockingUserId === user.userId} onPress={() => void handleUnblockUser(user.userId)} size="sm" variant="secondary">
                  {unblockingUserId === user.userId ? "..." : "Unblock"}
                </AppButton>
              </View>
            ))}
          </View>
        </SectionCard>

        <SectionCard description="Choose a target list, search a user, then save changes to persist the updated exceptions." title="Privacy exceptions">
          <View style={styles.chipRow}>
            {PRIVACY_TARGETS.map((target) => (
              <AppChip active={identity.activePrivacyList === target.key} key={target.key} onPress={() => identity.setActivePrivacyList(target.key)} tone={target.tone}>
                {target.label}
              </AppChip>
            ))}
          </View>
          <AppTextField autoCapitalize="none" onChangeText={identity.setPrivacySearchQuery} placeholder="Search users for privacy exceptions" value={identity.privacySearchQuery} />
          {identity.searchingPrivacyUsers ? <Text style={styles.metaText}>Searching users...</Text> : null}
          {identity.privacySearchResults.length > 0 ? (
            <View style={styles.searchResults}>
              {identity.privacySearchResults.map((user) => (
                <AppButton key={user.userId} onPress={() => identity.handleAddPrivacyException(user)} size="sm" variant="secondary">
                  {user.username ? `${user.displayName} (@${user.username})` : user.displayName}
                </AppButton>
              ))}
            </View>
          ) : null}
          {privacyLists.map((list) => (
            <View key={list.key} style={styles.privacyList}>
              <Text style={styles.privacyListTitle}>{list.label}</Text>
              {list.userIds.length > 0 ? (
                <View style={styles.chipRow}>
                  {list.userIds.map((userId) => (
                    <AppChip key={`${list.key}:${userId}`} onPress={() => identity.handleRemovePrivacyException(list.key, userId)} tone={list.tone}>
                      {identity.resolvePrivacyUserLabel(userId)} x
                    </AppChip>
                  ))}
                </View>
              ) : (
                <Text style={styles.metaText}>No users added.</Text>
              )}
            </View>
          ))}
          <SectionSaveButton busy={identity.saving} label="Save blocked & exception changes" onPress={() => void identity.handleSave()} />
        </SectionCard>
      </>
    );
  }

  function renderHelpSection() {
    return (
      <SectionCard description="This consumer app intentionally avoids misleading privacy claims." title="Privacy contract">
        <AppBanner message="Messages are stored server-side. The app does not expose secret chats, end-to-end guarantees or any admin/compliance tools." tone="info" />
        <View style={styles.cardStack}>
          <Text style={styles.preferenceText}>If lawful export is required, it happens outside this application through an internal audited process. Users cannot browse other users&apos; data from this interface.</Text>
          <Text style={styles.preferenceText}>Stories, calls and bots remain feature-flagged consumer surfaces. adminCompliance, lawfulDirectExport and secretChats stay hidden in the consumer profile.</Text>
        </View>
        <View style={styles.inlineActions}>
          <AppButton onPress={() => {
            acknowledgePrivacyDisclosure();
            setNotice("Privacy disclosure acknowledged for this account.");
          }} variant="primary">
            {disclosureState.privacyAcknowledgedAt ? "Acknowledged" : "Acknowledge"}
          </AppButton>
          {onOpenSessions ? <AppButton onPress={onOpenSessions} variant="secondary">Active sessions</AppButton> : null}
        </View>
        {disclosureState.privacyAcknowledgedAt ? <Text style={styles.metaText}>Acknowledged {new Date(disclosureState.privacyAcknowledgedAt).toLocaleString()}</Text> : null}
      </SectionCard>
    );
  }

  function renderContent() {
    switch (section) {
      case "PROFILE":
        return renderProfileSection();
      case "PRIVACY_SECURITY":
        return renderPrivacySecuritySection();
      case "DEVICES":
        return renderDevicesSection();
      case "NOTIFICATIONS":
        return renderNotificationsSection();
      case "DATA_STORAGE":
        return renderDataStorageSection();
      case "APPEARANCE":
        return renderAppearanceSection();
      case "LANGUAGE":
        return renderLanguageSection();
      case "BLOCKED_PRIVACY":
        return renderBlockedPrivacySection();
      case "HELP":
        return renderHelpSection();
      default:
        return null;
    }
  }

  return (
    <AppScreen padding="xl">
      <AppHeader onBack={onClose} subtitle={meta.subtitle} title={meta.title} />
      <ScreenFeedback error={error} loading={identity.loading} notice={notice} />
      <ScreenScrollView gap="md" paddingBottom="xxl">
        {renderContent()}
      </ScreenScrollView>
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  multiLine: {
    minHeight: 96
  },
  cardStack: {
    gap: appSpacing.sm
  },
  inlineActions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  searchResults: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  privacyList: {
    gap: appSpacing.sm
  },
  privacyListTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  blockedList: {
    gap: appSpacing.sm
  },
  blockedUserCard: {
    alignItems: "center",
    borderColor: appColors.border,
    borderRadius: 16,
    borderWidth: 1,
    flexDirection: "row",
    gap: appSpacing.md,
    justifyContent: "space-between",
    padding: appSpacing.md
  },
  blockedUserBody: {
    flex: 1,
    gap: 4
  },
  blockedUserName: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  metaText: {
    color: appColors.textSecondary
  },
  preferenceText: {
    color: appColors.textSecondary,
    lineHeight: 20
  }
});
