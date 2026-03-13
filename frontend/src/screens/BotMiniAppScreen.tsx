import React, { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Linking,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { WebView } from "react-native-webview";
import type { WebViewNavigation } from "react-native-webview/lib/WebViewTypes";
import { api } from "../services/api";
import type { BotWebAppLaunch } from "../types";

type BotMiniAppScreenProps = {
  botUserId: string;
  chatId?: string | null;
  onClose: () => void;
  startParameter?: string | null;
  title: string;
  token: string;
};

export function BotMiniAppScreen({
  botUserId,
  chatId,
  onClose,
  startParameter,
  title,
  token
}: BotMiniAppScreenProps) {
  const webViewRef = useRef<WebView>(null);
  const [launch, setLaunch] = useState<BotWebAppLaunch | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [canGoBack, setCanGoBack] = useState(false);
  const [webViewKey, setWebViewKey] = useState(0);

  async function loadLaunch() {
    setLoading(true);
    setError(null);
    try {
      const nextLaunch = await api.getBotWebAppLaunch(token, botUserId, {
        chatId,
        startParameter
      });
      setLaunch(nextLaunch);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to open mini app");
      setLaunch(null);
    } finally {
      setLoading(false);
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
    try {
      const supported = await Linking.canOpenURL(launch.launchUrl);
      if (!supported) {
        throw new Error("Mini app URL is not supported on this device");
      }
      await Linking.openURL(launch.launchUrl);
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open mini app");
    }
  }

  function handleRefresh() {
    setRefreshing(true);
    setWebViewKey((current) => current + 1);
    setTimeout(() => setRefreshing(false), 250);
  }

  function handleNavigationStateChange(state: WebViewNavigation) {
    setCanGoBack(state.canGoBack);
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <View style={styles.headerText}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.subtitle}>
            {launch ? `@${launch.botUsername}` : "Loading mini app"}
          </Text>
        </View>
        <Pressable
          disabled={!launch || refreshing}
          onPress={handleRefresh}
          style={[styles.secondaryButton, (!launch || refreshing) && styles.disabled]}
        >
          <Text style={styles.secondaryButtonText}>{refreshing ? "..." : "Reload"}</Text>
        </Pressable>
        <Pressable
          disabled={!launch}
          onPress={() => void handleOpenExternally()}
          style={[styles.secondaryButton, !launch && styles.disabled]}
        >
          <Text style={styles.secondaryButtonText}>Browser</Text>
        </Pressable>
      </View>

      {loading ? (
        <View style={styles.centerState}>
          <ActivityIndicator color="#0f172a" />
          <Text style={styles.metaText}>Preparing signed launch session...</Text>
        </View>
      ) : null}

      {error ? (
        <View style={styles.infoCard}>
          <Text style={styles.errorTitle}>Mini app unavailable</Text>
          <Text style={styles.errorBody}>{error}</Text>
          <View style={styles.row}>
            <Pressable onPress={() => void loadLaunch()} style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>Retry</Text>
            </Pressable>
            <Pressable onPress={onClose} style={styles.secondaryButton}>
              <Text style={styles.secondaryButtonText}>Close</Text>
            </Pressable>
          </View>
        </View>
      ) : null}

      {!loading && !error && launch ? (
        <View style={styles.webViewWrap}>
          <View style={styles.webViewToolbar}>
            <Text style={styles.metaText}>
              Session expires {new Date(launch.expiresAt).toLocaleTimeString()}
            </Text>
            {canGoBack ? (
              <Pressable
                onPress={() => webViewRef.current?.goBack()}
                style={styles.inlineButton}
              >
                <Text style={styles.inlineButtonText}>Back page</Text>
              </Pressable>
            ) : null}
          </View>
          <WebView
            key={webViewKey}
            ref={webViewRef}
            onNavigationStateChange={handleNavigationStateChange}
            originWhitelist={["*"]}
            sharedCookiesEnabled
            source={{ uri: launch.launchUrl }}
            style={styles.webView}
          />
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f8fafc"
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 10
  },
  headerText: {
    flex: 1,
    minWidth: 120
  },
  title: {
    color: "#0f172a",
    fontSize: 20,
    fontWeight: "700"
  },
  subtitle: {
    color: "#64748b",
    marginTop: 2
  },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  primaryButton: {
    borderRadius: 12,
    backgroundColor: "#0f172a",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  primaryButtonText: {
    color: "#ffffff",
    fontWeight: "600"
  },
  disabled: {
    opacity: 0.6
  },
  centerState: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 12
  },
  infoCard: {
    margin: 16,
    borderRadius: 16,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 10
  },
  errorTitle: {
    color: "#991b1b",
    fontWeight: "700"
  },
  errorBody: {
    color: "#7f1d1d"
  },
  row: {
    flexDirection: "row",
    gap: 10
  },
  webViewWrap: {
    flex: 1
  },
  webViewToolbar: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingBottom: 8
  },
  metaText: {
    color: "#475569"
  },
  inlineButton: {
    borderRadius: 10,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  inlineButtonText: {
    color: "#1d4ed8",
    fontWeight: "600"
  },
  webView: {
    flex: 1
  }
});
