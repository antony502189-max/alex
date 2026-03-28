import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { ProfileAccountsSection } from "./ProfileAccountsSection";
import { ProfilePhotoSection } from "./ProfilePhotoSection";
import type { ProfileScreenController } from "./useProfileScreenController";
import type { SettingsSectionId } from "../../navigation/types";
import { AppActionTile } from "../ui/AppActionTile";
import { AppBanner } from "../ui/AppBanner";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";

type SettingsHomeScreenContentProps = {
  controller: ProfileScreenController;
  onAddAccount?: () => void;
  onOpenBotDeveloper?: () => void;
  onOpenSection: (section: SettingsSectionId) => void;
  onOpenSessions: () => void;
};

const SECTION_CARDS: Array<{
  body: string;
  section: SettingsSectionId;
  title: string;
}> = [
  {
    section: "PROFILE",
    title: "Profile",
    body: "Name, username, about, photo and main public identity fields."
  },
  {
    section: "PRIVACY_SECURITY",
    title: "Privacy & Security",
    body: "Visibility rules, 2FA, phone change, account export and deletion."
  },
  {
    section: "DEVICES",
    title: "Devices",
    body: "Passkeys, device push token, current device state and sessions handoff."
  },
  {
    section: "NOTIFICATIONS",
    title: "Notifications & Sounds",
    body: "Local consumer notification preferences stored per account on this device."
  },
  {
    section: "DATA_STORAGE",
    title: "Data & Storage",
    body: "Auto-download, autoplay, call data policy and local media retention."
  },
  {
    section: "APPEARANCE",
    title: "Appearance & Chat",
    body: "Compact lists, avatars, link previews and input behavior."
  },
  {
    section: "LANGUAGE",
    title: "Language",
    body: "Preferred UI language and translation target preferences."
  },
  {
    section: "BLOCKED_PRIVACY",
    title: "Blocked Users & Exceptions",
    body: "Blocked people, allow/deny lists and explicit privacy overrides."
  },
  {
    section: "HELP",
    title: "Help & Privacy",
    body: "Consumer privacy disclosure and explanation of server-side storage."
  }
];

export function SettingsHomeScreenContent({
  controller,
  onAddAccount,
  onOpenBotDeveloper,
  onOpenSection,
  onOpenSessions
}: SettingsHomeScreenContentProps) {
  const {
    acknowledgePrivacyDisclosure,
    appearanceSettings,
    dataStorageSettings,
    disclosureState,
    error,
    identity,
    localAccounts,
    notice,
    notificationSettings,
    session,
    setError,
    setNotice
  } = controller;

  const privacyDisclosureAcknowledged = Boolean(disclosureState.privacyAcknowledgedAt);

  return (
    <ScreenScrollView gap="md" paddingBottom="xxl">
      <ScreenFeedback error={error} notice={notice} />

      <ProfilePhotoSection
        onRemovePhoto={() => void identity.handleRemovePhoto()}
        onUploadPhoto={() => void identity.handleUploadPhoto()}
        photoUrl={identity.photoUrl}
        removingPhoto={identity.removingPhoto}
        title={identity.displayName || session?.displayName || "Settings"}
        uploadingPhoto={identity.uploadingPhoto}
      />

      <SectionCard
        description="Consumer messaging profile with server-side protection and audited compliance export outside the user app."
        title={identity.displayName || session?.displayName || "Account"}
      >
        <View style={styles.metaStack}>
          {session?.username ? <Text style={styles.metaText}>@{session.username}</Text> : null}
          {session?.phoneNumber ? <Text style={styles.metaText}>{session.phoneNumber}</Text> : null}
          <View style={styles.chipRow}>
            <AppChip tone="brand">Settings</AppChip>
            <AppChip tone="default">
              {localAccounts.localAccounts.length} local account
              {localAccounts.localAccounts.length === 1 ? "" : "s"}
            </AppChip>
            {notificationSettings.includeMessagePreview ? (
              <AppChip tone="success">Preview ON</AppChip>
            ) : (
              <AppChip tone="warning">Preview OFF</AppChip>
            )}
          </View>
        </View>
      </SectionCard>

      {!privacyDisclosureAcknowledged ? (
        <SectionCard
          description="This consumer app does not expose secret chats or end-to-end guarantees. Message content is available to the service and export is handled only through an internal audited workflow."
          title="Privacy disclosure"
        >
          <AppBanner
            message="Use the app only with the expectation of server-side message storage and controlled lawful export outside this UI."
            tone="info"
          />
          <View style={styles.inlineActions}>
            <AppButton onPress={() => onOpenSection("HELP")} variant="secondary">
              Read details
            </AppButton>
            <AppButton
              onPress={() => {
                acknowledgePrivacyDisclosure();
                setNotice("Privacy disclosure acknowledged for this account.");
              }}
              variant="primary"
            >
              Acknowledge
            </AppButton>
          </View>
        </SectionCard>
      ) : null}

      <SectionCard
        description="Telegram-like settings hierarchy with account-scoped local consumer preferences."
        title="Settings"
      >
        <View style={styles.sectionGrid}>
          {SECTION_CARDS.map((item) => (
            <AppActionTile
              body={item.body}
              key={item.section}
              onPress={() => onOpenSection(item.section)}
              title={item.title}
            />
          ))}
        </View>
      </SectionCard>

      <SectionCard
        description="Quick status snapshot for the current account on this device."
        title="Local preferences"
      >
        <View style={styles.preferenceList}>
          <Text style={styles.preferenceText}>
            Notifications: private {notificationSettings.privateChatsEnabled ? "on" : "off"}, groups{" "}
            {notificationSettings.groupChatsEnabled ? "on" : "off"}, stories{" "}
            {notificationSettings.storyNotificationsEnabled ? "on" : "off"}
          </Text>
          <Text style={styles.preferenceText}>
            Data & storage: cellular download {dataStorageSettings.autoDownloadOnCellular ? "on" : "off"}, Wi-Fi download{" "}
            {dataStorageSettings.autoDownloadOnWifi ? "on" : "off"}, keep media {dataStorageSettings.keepDownloadedMediaDays} days
          </Text>
          <Text style={styles.preferenceText}>
            Appearance: compact list {appearanceSettings.compactChatList ? "on" : "off"}, avatars{" "}
            {appearanceSettings.showChatAvatars ? "on" : "off"}, enter sends{" "}
            {appearanceSettings.enterSendsMessage ? "on" : "off"}
          </Text>
        </View>
      </SectionCard>

      <SectionCard
        description="Device-local account switching remains separate from server sessions."
        title="Account shortcuts"
      >
        <View style={styles.inlineActions}>
          <AppButton onPress={onOpenSessions} variant="secondary">
            Active sessions
          </AppButton>
          {onOpenBotDeveloper ? (
            <AppButton onPress={onOpenBotDeveloper} variant="secondary">
              Bot developer
            </AppButton>
          ) : null}
        </View>
      </SectionCard>

      <ProfileAccountsSection
        activeAccountId={localAccounts.activeAccountId}
        localAccounts={localAccounts.localAccounts}
        onAddAccount={onAddAccount}
        onRemoveCurrentAccount={() =>
          localAccounts.handleRemoveLocalAccount(
            localAccounts.activeAccountId ?? "",
            setNotice,
            setError
          )
        }
        onRemoveLocalAccount={(accountId) =>
          localAccounts.handleRemoveLocalAccount(accountId, setNotice, setError)
        }
        onSwitchAccount={(accountId) =>
          localAccounts.handleSwitchAccount(accountId, setNotice, setError)
        }
        removingAccountId={localAccounts.removingAccountId}
        switchingAccountId={localAccounts.switchingAccountId}
      />
    </ScreenScrollView>
  );
}

const styles = StyleSheet.create({
  metaStack: {
    gap: appSpacing.sm
  },
  metaText: {
    color: appColors.textSecondary
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  inlineActions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  sectionGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  preferenceList: {
    gap: appSpacing.sm
  },
  preferenceText: {
    color: appColors.textSecondary,
    lineHeight: 20
  }
});
