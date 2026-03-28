import React from "react";
import { ActivityIndicator, StyleSheet, Text } from "react-native";
import { WebView } from "react-native-webview";
import type { WebViewNavigation } from "react-native-webview/lib/WebViewTypes";
import type { RefObject } from "react";
import { BotMiniAppErrorCard } from "./BotMiniAppErrorCard";
import { BotMiniAppWebViewStage } from "./BotMiniAppWebViewStage";
import { buildBotMiniAppSubtitle } from "./botMiniAppPresentation";
import type { BotMiniAppScreenController } from "./useBotMiniAppController";
import { AppBanner } from "../ui/AppBanner";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenStack } from "../ui/ScreenStack";
import { appColors } from "../../theme/tokens";

type BotMiniAppScreenContentProps = {
  controller: BotMiniAppScreenController;
  onClose: () => void;
  title: string;
  webViewRef: RefObject<WebView | null>;
};

export function BotMiniAppScreenContent({
  controller,
  onClose,
  title,
  webViewRef
}: BotMiniAppScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        rightSlot={(
          <ScreenStack direction="row" gap="sm">
            <AppButton
              disabled={controller.loading || controller.refreshing}
              onPress={() => void controller.handleRefresh()}
              size="sm"
            >
              {controller.refreshing ? "..." : "Refresh session"}
            </AppButton>
            <AppButton
              disabled={!controller.launch || controller.loading}
              onPress={() => void controller.handleOpenExternally()}
              size="sm"
            >
              Browser
            </AppButton>
          </ScreenStack>
        )}
        subtitle={buildBotMiniAppSubtitle(controller.launch)}
        title={title}
      />

      {controller.loading ? (
        <ScreenStack alignItems="center" flex={1} gap="md" justifyContent="center">
          <ActivityIndicator color={appColors.textPrimary} />
          <Text style={styles.metaText}>Preparing signed launch session...</Text>
        </ScreenStack>
      ) : null}

      {controller.error && !controller.launch ? (
        <BotMiniAppErrorCard
          error={controller.error}
          onClose={onClose}
          onRetry={() => void controller.loadLaunch()}
        />
      ) : null}

      {controller.runtimeNotice && controller.launch ? (
        <AppBanner message={controller.runtimeNotice} tone="danger" />
      ) : null}

      {!controller.loading && !controller.error && controller.launch ? (
        <BotMiniAppWebViewStage
          canGoBack={controller.canGoBack}
          launch={controller.launch}
          onNavigationStateChange={controller.handleNavigationStateChange as (state: WebViewNavigation) => void}
          onWebViewError={controller.handleWebViewError}
          webViewKey={controller.webViewKey}
          webViewRef={webViewRef}
        />
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  metaText: {
    color: appColors.textSecondary
  }
});
