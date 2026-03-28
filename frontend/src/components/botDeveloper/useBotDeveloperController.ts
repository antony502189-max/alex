import { useEffect, useMemo, useState } from "react";
import { api } from "../../services/api";
import type { DeveloperBot } from "../../types";
import {
  buildBotSavePayload,
  buildBotWebhookPayload,
  canSaveBotForm,
  EMPTY_BOT_FORM,
  isBotFormBlank,
  toBotFormState,
  type BotFormState
} from "./botDeveloperPresentation";

type UseBotDeveloperControllerParams = {
  token: string;
};

export function useBotDeveloperController({
  token
}: UseBotDeveloperControllerParams) {
  const [bots, setBots] = useState<DeveloperBot[]>([]);
  const [selectedBotId, setSelectedBotId] = useState<string | null>(null);
  const [form, setForm] = useState<BotFormState>(EMPTY_BOT_FORM);
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
          setForm((current) => (isBotFormBlank(current) ? toBotFormState(refreshed) : current));
        } else {
          setSelectedBotId(null);
          setForm(EMPTY_BOT_FORM);
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

  function handleSelectBot(bot: DeveloperBot | null) {
    setSelectedBotId(bot?.botUserId ?? null);
    setForm(bot ? toBotFormState(bot) : EMPTY_BOT_FORM);
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
        const updated = await api.updateDeveloperBot(
          token,
          selectedBot.botUserId,
          buildBotSavePayload(form)
        );
        upsertBot(updated);
        handleSelectBot(updated);
        setNotice("Bot profile updated.");
        return;
      }

      const created = await api.createDeveloperBot(token, buildBotSavePayload(form));
      upsertBot(created.bot);
      setSelectedBotId(created.bot.botUserId);
      setForm(toBotFormState(created.bot));
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
      setForm(toBotFormState(rotated.bot));
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
      const updated = await api.updateDeveloperBotWebhook(
        token,
        selectedBot.botUserId,
        buildBotWebhookPayload(form)
      );
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
      setForm((current) => ({ ...current, webhookSecret: "", webhookUrl: "" }));
      setSelectedBotId(updated.botUserId);
      setNotice("Webhook cleared.");
    } catch (clearError) {
      setError(clearError instanceof Error ? clearError.message : "Unable to clear webhook");
    } finally {
      setSaving(false);
    }
  }

  return {
    bots,
    canSave: canSaveBotForm(form),
    error,
    form,
    handleClearWebhook,
    handleRotateToken,
    handleSave,
    handleSaveWebhook,
    handleSelectBot,
    issuedToken,
    loading,
    loadBots,
    notice,
    saving,
    selectedBot,
    selectedBotId,
    updateForm
  };
}

export type BotDeveloperScreenController = ReturnType<typeof useBotDeveloperController>;
