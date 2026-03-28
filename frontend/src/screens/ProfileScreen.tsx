import React from "react";
import { SettingsHomeScreenContent } from "../components/profile/SettingsHomeScreenContent";
import { useProfileScreenController } from "../components/profile/useProfileScreenController";
import { AppHeader } from "../components/ui/AppHeader";
import { AppScreen } from "../components/ui/AppScreen";
import type { SettingsSectionId } from "../navigation/types";

type ProfileScreenProps = {
  token: string;
  onClose: () => void;
  onAddAccount?: () => void;
  onOpenSessions: () => void;
  onOpenBotDeveloper?: () => void;
  onOpenSettingsSection?: (section: SettingsSectionId) => void;
};

export function ProfileScreen({
  token,
  onClose,
  onAddAccount,
  onOpenSessions,
  onOpenBotDeveloper,
  onOpenSettingsSection
}: ProfileScreenProps) {
  const controller = useProfileScreenController({ token });

  return (
    <AppScreen padding="xl">
      <AppHeader
        onBack={onClose}
        subtitle="Consumer account, privacy, device and local preferences"
        title="Settings"
      />
      <SettingsHomeScreenContent
        controller={controller}
        onAddAccount={onAddAccount}
        onOpenBotDeveloper={onOpenBotDeveloper}
        onOpenSection={(section) => onOpenSettingsSection?.(section)}
        onOpenSessions={onOpenSessions}
      />
    </AppScreen>
  );
}
