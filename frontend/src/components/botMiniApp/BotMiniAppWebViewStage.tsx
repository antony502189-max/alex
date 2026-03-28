import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { WebView } from "react-native-webview";
import type { WebViewNavigation } from "react-native-webview/lib/WebViewTypes";
import type { RefObject } from "react";
import type { BotWebAppLaunch } from "../../types";
import { appColors, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { buildBotMiniAppExpiryLabel } from "./botMiniAppPresentation";

type BotMiniAppWebViewStageProps = {
  canGoBack: boolean;
  launch: BotWebAppLaunch;
  onNavigationStateChange: (state: WebViewNavigation) => void;
  onWebViewError: (description?: string | null) => void;
  webViewKey: number;
  webViewRef: RefObject<WebView | null>;
};

export function BotMiniAppWebViewStage({
  canGoBack,
  launch,
  onNavigationStateChange,
  onWebViewError,
  webViewKey,
  webViewRef
}: BotMiniAppWebViewStageProps) {
  return (
    <View style={styles.wrap}>
      <View style={styles.toolbar}>
        <Text style={styles.meta}>{buildBotMiniAppExpiryLabel(launch)}</Text>
        {canGoBack ? (
          <AppButton onPress={() => webViewRef.current?.goBack()} size="sm">
            Back page
          </AppButton>
        ) : null}
      </View>
      <WebView
        key={webViewKey}
        onError={(event) => onWebViewError(event.nativeEvent.description)}
        onHttpError={(event) =>
          onWebViewError(`Mini app request failed with status ${event.nativeEvent.statusCode}`)
        }
        onNavigationStateChange={onNavigationStateChange}
        originWhitelist={["*"]}
        ref={webViewRef}
        sharedCookiesEnabled
        source={{ uri: launch.launchUrl }}
        style={styles.webView}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1
  },
  toolbar: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    paddingBottom: appSpacing.sm
  },
  meta: {
    color: appColors.textSecondary
  },
  webView: {
    flex: 1
  }
});
