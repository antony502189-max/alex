import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { ProfileAccountDeletionSection } from "./ProfileAccountDeletionSection";
import { ProfileAccountExportSection } from "./ProfileAccountExportSection";
import { ProfileAccountsSection } from "./ProfileAccountsSection";
import { ProfileDeviceSection } from "./ProfileDeviceSection";
import { ProfilePasskeysSection } from "./ProfilePasskeysSection";
import { ProfilePhoneChangeSection } from "./ProfilePhoneChangeSection";
import { ProfilePhotoSection } from "./ProfilePhotoSection";
import { ProfilePrivacyCard } from "./ProfilePrivacyCard";
import { ProfileSecurityEventsSection } from "./ProfileSecurityEventsSection";
import { ProfileTwoFactorSection } from "./ProfileTwoFactorSection";
import type { ProfileScreenController } from "./useProfileScreenController";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { AppPanel } from "../ui/AppPanel";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";
import { AppTextField } from "../ui/AppTextField";
import { appColors, appSpacing } from "../../theme/tokens";

type ProfileScreenContentProps = {
  controller: ProfileScreenController;
  onAddAccount?: () => void;
  onOpenBotDeveloper?: () => void;
  onOpenSessions: () => void;
};

export function ProfileScreenContent({
  controller,
  onAddAccount,
  onOpenBotDeveloper,
  onOpenSessions
}: ProfileScreenContentProps) {
  const {
    error,
    identity,
    localAccounts,
    notice,
    passkeys,
    security,
    session,
    setError,
    setNotice
  } = controller;

  const privacyTargets: Array<{
    key: typeof identity.activePrivacyList;
    label: string;
    tone: "success" | "danger";
  }> = [
    { key: "phoneAllowedUserIds", label: "Phone allow", tone: "success" as const },
    { key: "phoneDisallowedUserIds", label: "Phone deny", tone: "danger" as const },
    { key: "lastSeenAllowedUserIds", label: "Last seen allow", tone: "success" as const },
    { key: "lastSeenDisallowedUserIds", label: "Last seen deny", tone: "danger" as const },
    { key: "storyAllowedUserIds", label: "Story allow", tone: "success" as const },
    { key: "storyDisallowedUserIds", label: "Story deny", tone: "danger" as const }
  ];

  const privacyLists = [
    {
      key: "phoneAllowedUserIds" as const,
      label: "Phone allow list",
      tone: "success" as const,
      userIds: identity.phoneAllowedUserIds
    },
    {
      key: "phoneDisallowedUserIds" as const,
      label: "Phone deny list",
      tone: "danger" as const,
      userIds: identity.phoneDisallowedUserIds
    },
    {
      key: "lastSeenAllowedUserIds" as const,
      label: "Last seen allow list",
      tone: "success" as const,
      userIds: identity.lastSeenAllowedUserIds
    },
    {
      key: "lastSeenDisallowedUserIds" as const,
      label: "Last seen deny list",
      tone: "danger" as const,
      userIds: identity.lastSeenDisallowedUserIds
    },
    {
      key: "storyAllowedUserIds" as const,
      label: "Story allow list",
      tone: "success" as const,
      userIds: identity.storyAllowedUserIds
    },
    {
      key: "storyDisallowedUserIds" as const,
      label: "Story deny list",
      tone: "danger" as const,
      userIds: identity.storyDisallowedUserIds
    }
  ];

  return (
    <ScreenScrollView gap="md" paddingBottom="xxl">
      {identity.loading ? <Text style={styles.metaText}>Loading...</Text> : null}
      <ScreenFeedback error={error} notice={notice} />

      <ProfilePhotoSection
        onRemovePhoto={() => void identity.handleRemovePhoto()}
        onUploadPhoto={() => void identity.handleUploadPhoto()}
        photoUrl={identity.photoUrl}
        removingPhoto={identity.removingPhoto}
        title={identity.displayName || session?.displayName || "Profile"}
        uploadingPhoto={identity.uploadingPhoto}
      />

      <AppTextField
        onChangeText={identity.setDisplayName}
        placeholder="Display name"
        value={identity.displayName}
      />
      <AppTextField
        autoCapitalize="none"
        onChangeText={identity.setUsername}
        placeholder="Username"
        value={identity.username}
      />
      <AppTextField
        multiline
        onChangeText={identity.setAbout}
        placeholder="About"
        style={styles.aboutInput}
        value={identity.about}
      />

      <ProfilePrivacyCard
        label="Phone privacy"
        onChange={identity.setPhonePrivacy}
        value={identity.phonePrivacy}
      />
      <ProfilePrivacyCard
        label="Last seen privacy"
        onChange={identity.setLastSeenPrivacy}
        value={identity.lastSeenPrivacy}
      />
      <ProfilePrivacyCard
        label="Story privacy"
        onChange={identity.setStoryPrivacy}
        value={identity.storyPrivacy}
      />

      <AppPanel
        description="Choose a target list, search a user and tap the result to add them to an explicit allow or deny exception."
        title="Privacy exceptions"
      >
        <View style={styles.chipRow}>
          {privacyTargets.map((target) => (
            <AppChip
              active={identity.activePrivacyList === target.key}
              key={target.key}
              onPress={() => identity.setActivePrivacyList(target.key)}
              tone={target.tone}
            >
              {target.label}
            </AppChip>
          ))}
        </View>

        <AppTextField
          autoCapitalize="none"
          onChangeText={identity.setPrivacySearchQuery}
          placeholder="Search users for privacy exceptions"
          value={identity.privacySearchQuery}
        />

        {identity.searchingPrivacyUsers ? <Text style={styles.metaText}>Searching users...</Text> : null}

        {identity.privacySearchResults.length > 0 ? (
          <View style={styles.searchResults}>
            {identity.privacySearchResults.map((user) => (
              <AppButton
                key={user.userId}
                onPress={() => identity.handleAddPrivacyException(user)}
                size="sm"
                variant="secondary"
              >
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
                  <AppChip
                    key={`${list.key}:${userId}`}
                    onPress={() => identity.handleRemovePrivacyException(list.key, userId)}
                    tone={list.tone}
                  >
                    {identity.resolvePrivacyUserLabel(userId)} x
                  </AppChip>
                ))}
              </View>
            ) : (
              <Text style={styles.metaText}>No users added.</Text>
            )}
          </View>
        ))}
      </AppPanel>

      <AppPanel
        description="Set default UI language and preferred translation target language."
        title="Languages"
      >
        <AppTextField
          autoCapitalize="none"
          onChangeText={identity.setPreferredLanguage}
          placeholder="Preferred language, for example en or ru"
          value={identity.preferredLanguage}
        />
        <AppTextField
          autoCapitalize="none"
          onChangeText={identity.setTranslationTargetLanguage}
          placeholder="Translation target language"
          value={identity.translationTargetLanguage}
        />
      </AppPanel>

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

      <ProfileSecurityEventsSection events={security.securityEvents} />

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

      <AppButton fullWidth onPress={onOpenSessions} variant="secondary">
        Active sessions
      </AppButton>

      {onOpenBotDeveloper ? (
        <AppButton fullWidth onPress={onOpenBotDeveloper} variant="secondary">
          Bot developer console
        </AppButton>
      ) : null}

      <AppButton
        disabled={identity.saving}
        fullWidth
        onPress={() => void identity.handleSave()}
        variant="primary"
      >
        {identity.saving ? "Saving..." : "Save profile"}
      </AppButton>
    </ScreenScrollView>
  );
}

const styles = StyleSheet.create({
  aboutInput: {
    minHeight: 100
  },
  chipRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  metaText: {
    color: appColors.textSecondary
  },
  privacyList: {
    gap: appSpacing.sm
  },
  privacyListTitle: {
    color: appColors.textPrimary,
    fontSize: 14,
    fontWeight: "700"
  },
  searchResults: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  }
});
