import React from "react";
import { CallsScreenContent } from "../components/calls/CallsScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useCallsScreenController } from "../components/calls/useCallsScreenController";
import type { ParsedDeepLink } from "../navigation/deepLinks";
import type { ChatSummary } from "../types";

type CallsScreenProps = {
  availableChats: ChatSummary[];
  callJoinLinksEnabled: boolean;
  currentUserId: string;
  onCallBack: (chatId: string, kind: "VOICE" | "VIDEO") => void;
  onClose: () => void;
  onJoinCallLink: (rawToken: string) => void;
  onOpenChat: (chatId: string) => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
  token: string;
};

export function CallsScreen({
  availableChats,
  callJoinLinksEnabled,
  currentUserId,
  onCallBack,
  onClose,
  onJoinCallLink,
  onOpenChat,
  onOpenParsedLink,
  token
}: CallsScreenProps) {
  const controller = useCallsScreenController({
    currentUserId,
    token
  });

  return (
    <AppScreen padding="xl">
      <CallsScreenContent
        availableChats={availableChats}
        callJoinLinksEnabled={callJoinLinksEnabled}
        controller={controller}
        onCallBack={onCallBack}
        onClose={onClose}
        onJoinCallLink={onJoinCallLink}
        onOpenChat={onOpenChat}
        onOpenParsedLink={onOpenParsedLink}
      />
    </AppScreen>
  );
}
