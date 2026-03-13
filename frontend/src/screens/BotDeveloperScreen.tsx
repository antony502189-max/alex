import React, { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
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
import type { DeveloperBot } from "../types";

type BotDeveloperScreenProps = {
  token: string;
  onClose: () => void;
};

type BotFormState = {
  displayName: string;
  username: string;
  description: string;
  about: string;
  webAppUrl: string;
  webhookUrl: string;
  webhookSecret: string;
  supportsInline: boolean;
};

const EMPTY_FORM: BotFormState = {
  displayName: "",
  username: "",
  description: "",
  about: "",
  webAppUrl: "",
  webhookUrl: "",
  webhookSecret: "",
  supportsInline: false
};

function toFormState(bot: DeveloperBot): BotFormState {
  return {
    displayName: bot.displayName,
    username: bot.username,
    description: bot.description ?? "",
    about: bot.about ?? "",
    webAppUrl: bot.webAppUrl ?? "",
    webhookUrl: bot.webhookUrl ?? "",
    webhookSecret: "",
    supportsInline: bot.supportsInline
  };
}

export function BotDeveloperScreen({ token, onClose }: BotDeveloperScreenProps) {
  const [bots, setBots] = useState<DeveloperBot[]>([]);
  const [selectedBotId, setSelectedBotId] = useState<string | null>(null);
  const [form, setForm] = useState<BotFormState>(EMPTY_FORM);
  const [issuedToken, setIssuedToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const selectedBot = useMemo(
    () => bots.find((bot) => bot.botUserId === selectedBotId) ?? null,
    [bots, selectedBotId]
  );

  async function loadBots() {
    setLoading(true);
    setError(null);
    try {
      const nextBots = await api.getDeveloperBots(token);
      setBots(nextBots);
      if (selectedBotId) {
        const refreshed = nextBots.find((bot) => bot.botUserId === selectedBotId) ?? null;
        if (refreshed) {
          setForm((current) =>
            current.displayName === "" && current.username === "" ? toFormState(refreshed) : current
          );
        } else {
          setSelectedBotId(null);
          setForm(EMPTY_FORM);
        }
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load bots");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadBots();
  }, [token]);

  function selectBot(bot: DeveloperBot | null) {
    setSelectedBotId(bot?.botUserId ?? null);
    setForm(bot ? toFormState(bot) : EMPTY_FORM);
    setIssuedToken(null);
    setError(null);
    setNotice(null);
  }

  function updateForm<K extends keyof BotFormState>(key: K, value: BotFormState[K]) {
    setForm((current) => ({
      ...current,
      [key]: value
    }));
  }

  function upsertBot(nextBot: DeveloperBot) {
    setBots((current) => {
      const existing = current.find((bot) => bot.botUserId === nextBot.botUserId);
      if (!existing) {
        return [nextBot, ...current];
      }
      return current.map((bot) => (bot.botUserId === nextBot.botUserId ? nextBot : bot));
    });
  }

  async function handleSave() {
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      if (selectedBot) {
        const updated = await api.updateDeveloperBot(token, selectedBot.botUserId, {
          displayName: form.displayName.trim(),
          username: form.username.trim(),
          description: form.description.trim() || undefined,
          about: form.about.trim() || undefined,
          supportsInline: form.supportsInline,
          webAppUrl: form.webAppUrl.trim() || undefined
        });
        upsertBot(updated);
        selectBot(updated);
        setNotice("Bot profile updated.");
        return;
      }

      const created = await api.createDeveloperBot(token, {
        displayName: form.displayName.trim(),
        username: form.username.trim(),
        description: form.description.trim() || undefined,
        about: form.about.trim() || undefined,
        supportsInline: form.supportsInline,
        webAppUrl: form.webAppUrl.trim() || undefined
      });
      upsertBot(created.bot);
      setSelectedBotId(created.bot.botUserId);
      setForm(toFormState(created.bot));
      setIssuedToken(created.apiToken);
      setNotice("Bot created. The token is shown once.");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to save bot");
    } finally {
      setSaving(false);
    }
  }

  async function handleRotateToken() {
    if (!selectedBot) {
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const rotated = await api.rotateDeveloperBotToken(token, selectedBot.botUserId);
      upsertBot(rotated.bot);
      setSelectedBotId(rotated.bot.botUserId);
      setForm(toFormState(rotated.bot));
      setIssuedToken(rotated.apiToken);
      setNotice("Token rotated. The previous token is now invalid.");
    } catch (rotationError) {
      setError(rotationError instanceof Error ? rotationError.message : "Unable to rotate token");
    } finally {
      setSaving(false);
    }
  }

  async function handleSaveWebhook() {
    if (!selectedBot) {
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await api.updateDeveloperBotWebhook(token, selectedBot.botUserId, {
        webhookUrl: form.webhookUrl.trim(),
        secretToken: form.webhookSecret.trim() || undefined
      });
      upsertBot(updated);
      setForm((current) => ({ ...current, webhookSecret: "" }));
      setSelectedBotId(updated.botUserId);
      setNotice("Webhook updated.");
    } catch (webhookError) {
      setError(webhookError instanceof Error ? webhookError.message : "Unable to update webhook");
    } finally {
      setSaving(false);
    }
  }

  async function handleClearWebhook() {
    if (!selectedBot) {
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      const updated = await api.clearDeveloperBotWebhook(token, selectedBot.botUserId);
      upsertBot(updated);
      setForm((current) => ({ ...current, webhookUrl: "", webhookSecret: "" }));
      setSelectedBotId(updated.botUserId);
      setNotice("Webhook cleared.");
    } catch (clearError) {
      setError(clearError instanceof Error ? clearError.message : "Unable to clear webhook");
    } finally {
      setSaving(false);
    }
  }

  const canSave = form.displayName.trim().length > 0 && form.username.trim().length > 0;

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Bot Console</Text>
        <Pressable onPress={() => void loadBots()} style={styles.inlineButton}>
          <Text style={styles.inlineButtonText}>Refresh</Text>
        </Pressable>
      </View>

      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}
      {notice ? <Text style={styles.noticeText}>{notice}</Text> : null}

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.panel}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Owned bots</Text>
            <Pressable onPress={() => selectBot(null)} style={styles.secondaryButton}>
              <Text style={styles.secondaryButtonText}>New bot</Text>
            </Pressable>
          </View>
          {bots.length === 0 ? (
            <Text style={styles.metaText}>No bots yet.</Text>
          ) : (
            <View style={styles.botList}>
              {bots.map((bot) => {
                const selected = bot.botUserId === selectedBotId;
                return (
                  <Pressable
                    key={bot.botUserId}
                    onPress={() => selectBot(bot)}
                    style={[styles.botCard, selected && styles.botCardActive]}
                  >
                    <Avatar uri={bot.photoUrl} title={bot.displayName} size={44} />
                    <View style={styles.botInfo}>
                      <Text style={styles.botTitle}>{bot.displayName}</Text>
                      <Text style={styles.metaText}>@{bot.username}</Text>
                      <Text style={styles.metaText}>
                        token {bot.apiTokenPrefix}...{bot.supportsInline ? " - inline" : ""}
                      </Text>
                    </View>
                  </Pressable>
                );
              })}
            </View>
          )}
        </View>

        <View style={styles.panel}>
          <Text style={styles.sectionTitle}>
            {selectedBot ? `Edit @${selectedBot.username}` : "Create bot"}
          </Text>
          <Text style={styles.metaText}>
            Username must end with <Text style={styles.metaStrong}>bot</Text>.
          </Text>

          <TextInput
            value={form.displayName}
            onChangeText={(value) => updateForm("displayName", value)}
            placeholder="Display name"
            style={styles.input}
          />
          <TextInput
            value={form.username}
            onChangeText={(value) => updateForm("username", value)}
            placeholder="Username (example: weatherbot)"
            autoCapitalize="none"
            style={styles.input}
          />
          <TextInput
            value={form.description}
            onChangeText={(value) => updateForm("description", value)}
            placeholder="Short bot description"
            style={styles.input}
          />
          <TextInput
            value={form.about}
            onChangeText={(value) => updateForm("about", value)}
            placeholder="About"
            multiline
            style={[styles.input, styles.multilineInput]}
          />
          <TextInput
            value={form.webAppUrl}
            onChangeText={(value) => updateForm("webAppUrl", value)}
            placeholder="Mini app URL (optional)"
            autoCapitalize="none"
            style={styles.input}
          />

          <View style={styles.toggleRow}>
            <Text style={styles.sectionSubtitle}>Inline mode</Text>
            <Pressable
              onPress={() => updateForm("supportsInline", !form.supportsInline)}
              style={[styles.choiceChip, form.supportsInline && styles.choiceChipActive]}
            >
              <Text
                style={[styles.choiceChipText, form.supportsInline && styles.choiceChipTextActive]}
              >
                {form.supportsInline ? "Enabled" : "Disabled"}
              </Text>
            </Pressable>
          </View>

          <Pressable
            disabled={saving || !canSave}
            onPress={() => void handleSave()}
            style={[styles.primaryButton, (saving || !canSave) && styles.disabled]}
          >
            <Text style={styles.primaryButtonText}>
              {saving ? "Saving..." : selectedBot ? "Save bot" : "Create bot"}
            </Text>
          </Pressable>

          {selectedBot ? (
            <>
              <Pressable
                disabled={saving}
                onPress={() => void handleRotateToken()}
                style={[styles.warningButton, saving && styles.disabled]}
              >
                <Text style={styles.warningButtonText}>Rotate API token</Text>
              </Pressable>

              <Text style={styles.sectionSubtitle}>Webhook</Text>
              <TextInput
                value={form.webhookUrl}
                onChangeText={(value) => updateForm("webhookUrl", value)}
                placeholder="https://example.com/bot-webhook"
                autoCapitalize="none"
                style={styles.input}
              />
              <TextInput
                value={form.webhookSecret}
                onChangeText={(value) => updateForm("webhookSecret", value)}
                placeholder="Webhook secret token (optional)"
                autoCapitalize="none"
                style={styles.input}
              />
              <View style={styles.rowActions}>
                <Pressable
                  disabled={saving || form.webhookUrl.trim().length === 0}
                  onPress={() => void handleSaveWebhook()}
                  style={[
                    styles.inlineButtonPrimary,
                    (saving || form.webhookUrl.trim().length === 0) && styles.disabled
                  ]}
                >
                  <Text style={styles.inlineButtonPrimaryText}>Save webhook</Text>
                </Pressable>
                <Pressable
                  disabled={saving || !selectedBot.webhookEnabled}
                  onPress={() => void handleClearWebhook()}
                  style={[styles.dangerButton, (saving || !selectedBot.webhookEnabled) && styles.disabled]}
                >
                  <Text style={styles.dangerButtonText}>Clear webhook</Text>
                </Pressable>
              </View>

              <Text style={styles.metaText}>
                Token prefix: {selectedBot.apiTokenPrefix}...
              </Text>
              <Text style={styles.metaText}>
                Webhook: {selectedBot.webhookEnabled ? selectedBot.webhookUrl ?? "configured" : "not configured"}
              </Text>
              <Text style={styles.metaText}>
                Secret: {selectedBot.hasWebhookSecret ? "configured" : "not configured"}
              </Text>
              {selectedBot.lastWebhookDeliveryAt ? (
                <Text style={styles.metaText}>
                  Last delivery: {new Date(selectedBot.lastWebhookDeliveryAt).toLocaleString()}
                </Text>
              ) : null}
              {selectedBot.lastWebhookError ? (
                <Text style={styles.errorText}>Last webhook error: {selectedBot.lastWebhookError}</Text>
              ) : null}
            </>
          ) : null}

          {issuedToken ? (
            <View style={styles.tokenCard}>
              <Text style={styles.sectionSubtitle}>Issued token</Text>
              <Text style={styles.tokenText}>{issuedToken}</Text>
              <Text style={styles.metaText}>This token is shown once. Rotate it if it is exposed.</Text>
            </View>
          ) : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#f8fafc", padding: 20 },
  header: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 16 },
  title: { flex: 1, fontSize: 24, fontWeight: "700", color: "#0f172a" },
  loader: { marginBottom: 12 },
  content: { gap: 12, paddingBottom: 24 },
  panel: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 12
  },
  sectionHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  sectionTitle: { color: "#0f172a", fontSize: 18, fontWeight: "700" },
  sectionSubtitle: { color: "#0f172a", fontWeight: "700" },
  metaText: { color: "#64748b" },
  metaStrong: { fontWeight: "700", color: "#0f172a" },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    backgroundColor: "#ffffff"
  },
  multilineInput: { minHeight: 96, textAlignVertical: "top" },
  botList: { gap: 10 },
  botCard: {
    borderWidth: 1,
    borderColor: "#e2e8f0",
    borderRadius: 16,
    padding: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 12
  },
  botCardActive: {
    borderColor: "#0f172a",
    backgroundColor: "#f8fafc"
  },
  botInfo: { flex: 1 },
  botTitle: { color: "#0f172a", fontSize: 16, fontWeight: "700" },
  toggleRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
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
  primaryButtonText: { color: "#ffffff", fontWeight: "700" },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: { color: "#0f172a", fontWeight: "600" },
  inlineButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  inlineButtonText: { color: "#0f172a", fontWeight: "600" },
  inlineButtonPrimary: {
    borderRadius: 12,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 12,
    paddingVertical: 10,
    alignItems: "center"
  },
  inlineButtonPrimaryText: { color: "#1d4ed8", fontWeight: "700" },
  warningButton: {
    borderRadius: 14,
    backgroundColor: "#fef3c7",
    paddingVertical: 14,
    alignItems: "center"
  },
  warningButtonText: { color: "#92400e", fontWeight: "700" },
  dangerButton: {
    borderRadius: 12,
    backgroundColor: "#fee2e2",
    paddingHorizontal: 12,
    paddingVertical: 10,
    alignItems: "center"
  },
  dangerButtonText: { color: "#b91c1c", fontWeight: "700" },
  rowActions: { flexDirection: "row", gap: 10 },
  tokenCard: {
    borderRadius: 16,
    backgroundColor: "#eff6ff",
    padding: 14,
    gap: 8
  },
  tokenText: { color: "#0f172a", fontFamily: "monospace" },
  errorText: { color: "#b91c1c", marginBottom: 12 },
  noticeText: { color: "#0f766e", marginBottom: 12, fontWeight: "600" },
  disabled: { opacity: 0.6 }
});
