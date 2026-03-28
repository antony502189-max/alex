import React from "react";
import { BotDeveloperEditorSection } from "./BotDeveloperEditorSection";
import { BotDeveloperOwnedBotsSection } from "./BotDeveloperOwnedBotsSection";
import type { BotDeveloperScreenController } from "./useBotDeveloperController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";

type BotDeveloperScreenContentProps = {
  controller: BotDeveloperScreenController;
  onClose: () => void;
};

export function BotDeveloperScreenContent({
  controller,
  onClose
}: BotDeveloperScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        rightSlot={(
          <AppButton onPress={() => void controller.loadBots()} size="sm">
            Refresh
          </AppButton>
        )}
        title="Bot Console"
      />
      <ScreenFeedback
        error={controller.error}
        loading={controller.loading}
        notice={controller.notice}
      />

      <ScreenScrollView gap="md" paddingBottom="xxl">
        <BotDeveloperOwnedBotsSection
          bots={controller.bots}
          onCreateNew={() => controller.handleSelectBot(null)}
          onSelectBot={controller.handleSelectBot}
          selectedBotId={controller.selectedBotId}
        />

        <BotDeveloperEditorSection
          canSave={controller.canSave}
          form={controller.form}
          issuedToken={controller.issuedToken}
          onClearWebhook={() => void controller.handleClearWebhook()}
          onRotateToken={() => void controller.handleRotateToken()}
          onSave={() => void controller.handleSave()}
          onSaveWebhook={() => void controller.handleSaveWebhook()}
          onUpdateForm={controller.updateForm}
          saving={controller.saving}
          selectedBot={controller.selectedBot}
        />
      </ScreenScrollView>
    </>
  );
}
