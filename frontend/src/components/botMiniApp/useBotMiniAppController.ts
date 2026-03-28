import { Linking } from "react-native";
import { useEffect, useState } from "react";
import type { WebViewNavigation } from "react-native-webview/lib/WebViewTypes";
import { api } from "../../services/api";
import type { BotWebAppLaunch } from "../../types";

type UseBotMiniAppControllerParams = {
  botUserId: string;
  chatId?: string | null;
  startParameter?: string | null;
  token: string;
};

export function useBotMiniAppController({
  botUserId,
  chatId,
  startParameter,
  token
}: UseBotMiniAppControllerParams) {
  const [launch, setLaunch] = useState<BotWebAppLaunch | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [runtimeNotice, setRuntimeNotice] = useState<string | null>(null);
  const [canGoBack, setCanGoBack] = useState(false);
  const [webViewKey, setWebViewKey] = useState(0);

  async function loadLaunch(mode: "INITIAL" | "REFRESH" = "INITIAL") {
    if (mode === "INITIAL") {
      setLoading(true);
    } else {
      setRefreshing(true);
    }
    setError(null);
    setRuntimeNotice(null);
    try {
      const nextLaunch = await api.getBotWebAppLaunch(token, botUserId, {
        chatId,
        startParameter
      });
      setLaunch(nextLaunch);
      setCanGoBack(false);
      setWebViewKey((current) => current + 1);
    } catch (loadError) {
      const nextError =
        loadError instanceof Error ? loadError.message : "Unable to open mini app";
      if (mode === "REFRESH" && launch) {
        setRuntimeNotice("Unable to refresh signed session. Keeping the current mini app open.");
      } else {
        setError(nextError);
        setLaunch(null);
      }
    } finally {
      if (mode === "INITIAL") {
        setLoading(false);
      } else {
        setRefreshing(false);
      }
    }
  }

  useEffect(() => {
    void loadLaunch();
  }, [botUserId, chatId, startParameter, token]);

  async function handleOpenExternally() {
    if (!launch) {
      return;
    }

    setError(null);
    setRuntimeNotice(null);
    try {
      const supported = await Linking.canOpenURL(launch.launchUrl);
      if (!supported) {
        throw new Error("Mini app URL is not supported on this device");
      }
      await Linking.openURL(launch.launchUrl);
    } catch (openError) {
      setRuntimeNotice(
        openError instanceof Error ? openError.message : "Unable to open mini app"
      );
    }
  }

  async function handleRefresh() {
    await loadLaunch("REFRESH");
  }

  function handleNavigationStateChange(state: Pick<WebViewNavigation, "canGoBack">) {
    setCanGoBack(state.canGoBack);
    setRuntimeNotice(null);
  }

  function handleWebViewError(description?: string | null) {
    setRuntimeNotice(description?.trim() || "Mini app page failed to load");
  }

  return {
    canGoBack,
    error,
    handleNavigationStateChange,
    handleOpenExternally,
    handleRefresh,
    handleWebViewError,
    launch,
    loadLaunch,
    loading,
    refreshing,
    runtimeNotice,
    webViewKey
  };
}

export type BotMiniAppScreenController = ReturnType<typeof useBotMiniAppController>;
