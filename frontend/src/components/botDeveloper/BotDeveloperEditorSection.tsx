import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { DeveloperBot } from "../../types";
import { AppBanner } from "../ui/AppBanner";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { AppToggleCard } from "../ui/AppToggleCard";
import { SectionCard } from "../ui/SectionCard";
import {
  buildWebhookSecretStatus,
  buildWebhookStatus,
  type BotFormState
} from "./botDeveloperPresentation";

type BotDeveloperEditorSectionProps = {
  canSave: boolean;
  form: BotFormState;
  issuedToken: string | null;
  onClearWebhook: () => void;
  onRotateToken: () => void;
  onSave: () => void;
  onSaveWebhook: () => void;
  onUpdateForm: <K extends keyof BotFormState>(key: K, value: BotFormState[K]) => void;
  saving: boolean;
  selectedBot: DeveloperBot | null;
};

export function BotDeveloperEditorSection({
  canSave,
  form,
  issuedToken,
  onClearWebhook,
  onRotateToken,
  onSave,
  onSaveWebhook,
  onUpdateForm,
  saving,
  selectedBot
}: BotDeveloperEditorSectionProps) {
  return (
    <SectionCard
      title={selectedBot ? `Edit @${selectedBot.username}` : "Create bot"}
      description="Username must end with `bot` and should stay stable after launch."
    >
      <AppTextField
        onChangeText={(value) => onUpdateForm("displayName", value)}
        placeholder="Display name"
        value={form.displayName}
      />
      <AppTextField
        autoCapitalize="none"
        onChangeText={(value) => onUpdateForm("username", value)}
        placeholder="Username (example: weatherbot)"
        value={form.username}
      />
      <AppTextField
        onChangeText={(value) => onUpdateForm("description", value)}
        placeholder="Short bot description"
        value={form.description}
      />
      <AppTextField
        multiline
        onChangeText={(value) => onUpdateForm("about", value)}
        placeholder="About"
        value={form.about}
      />
      <AppTextField
        autoCapitalize="none"
        onChangeText={(value) => onUpdateForm("webAppUrl", value)}
        placeholder="Mini app URL (optional)"
        value={form.webAppUrl}
      />

      <AppToggleCard
        active={form.supportsInline}
        activeLabel="Enabled"
        description="Allow the bot to answer inline queries from other chats."
        inactiveLabel="Disabled"
        onPress={() => onUpdateForm("supportsInline", !form.supportsInline)}
        title="Inline mode"
      />

      <AppButton
        disabled={saving || !canSave}
        fullWidth
        onPress={onSave}
        variant="primary"
      >
        {saving ? "Saving..." : selectedBot ? "Save bot" : "Create bot"}
      </AppButton>

      {selectedBot ? (
        <>
          <AppButton disabled={saving} fullWidth onPress={onRotateToken}>
            Rotate API token
          </AppButton>

          <SectionCard
            description="Webhook updates are applied immediately to the current bot."
            style={styles.webhookCard}
            title="Webhook"
          >
            <AppTextField
              autoCapitalize="none"
              onChangeText={(value) => onUpdateForm("webhookUrl", value)}
              placeholder="https://example.com/bot-webhook"
              value={form.webhookUrl}
            />
            <AppTextField
              autoCapitalize="none"
              onChangeText={(value) => onUpdateForm("webhookSecret", value)}
              placeholder="Webhook secret token (optional)"
              value={form.webhookSecret}
            />
            <View style={styles.rowActions}>
              <AppButton
                disabled={saving || form.webhookUrl.trim().length === 0}
                onPress={onSaveWebhook}
                size="sm"
                variant="primary"
              >
                Save webhook
              </AppButton>
              <AppButton
                disabled={saving || !selectedBot.webhookEnabled}
                onPress={onClearWebhook}
                size="sm"
                variant="danger"
              >
                Clear webhook
              </AppButton>
            </View>
            <Text style={styles.meta}>Token prefix: {selectedBot.apiTokenPrefix}...</Text>
            <Text style={styles.meta}>Webhook: {buildWebhookStatus(selectedBot)}</Text>
            <Text style={styles.meta}>Secret: {buildWebhookSecretStatus(selectedBot)}</Text>
            {selectedBot.lastWebhookDeliveryAt ? (
              <Text style={styles.meta}>
                Last delivery: {new Date(selectedBot.lastWebhookDeliveryAt).toLocaleString()}
              </Text>
            ) : null}
            {selectedBot.lastWebhookError ? (
              <AppBanner message={`Last webhook error: ${selectedBot.lastWebhookError}`} tone="danger" />
            ) : null}
          </SectionCard>
        </>
      ) : null}

      {issuedToken ? (
        <View style={styles.tokenCard}>
          <Text style={styles.tokenTitle}>Issued token</Text>
          <Text style={styles.tokenText}>{issuedToken}</Text>
          <Text style={styles.meta}>This token is shown once. Rotate it if it is exposed.</Text>
        </View>
      ) : null}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  webhookCard: {
    backgroundColor: appColors.background,
    padding: appSpacing.md
  },
  rowActions: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  meta: {
    color: appColors.textSecondary
  },
  tokenCard: {
    backgroundColor: "#eff6ff",
    borderRadius: appRadii.lg,
    gap: appSpacing.sm,
    padding: appSpacing.md
  },
  tokenTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  tokenText: {
    color: appColors.textPrimary,
    fontFamily: "monospace"
  }
});
