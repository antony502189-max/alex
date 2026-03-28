import React, { useRef } from "react";
import { WebView } from "react-native-webview";
import { BotMiniAppScreenContent } from "../components/botMiniApp/BotMiniAppScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useBotMiniAppController } from "../components/botMiniApp/useBotMiniAppController";

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
  const controller = useBotMiniAppController({
    botUserId,
    chatId,
    startParameter,
    token
  });

  return (
    <AppScreen paddingBottom="md" paddingHorizontal="lg" paddingTop="md">
      <BotMiniAppScreenContent
        controller={controller}
        onClose={onClose}
        title={title}
        webViewRef={webViewRef}
      />
    </AppScreen>
  );
}
