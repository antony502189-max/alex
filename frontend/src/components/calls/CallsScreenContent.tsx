import React from "react";
import { CallsHistoryList } from "./CallsHistoryList";
import { CallsJoinSection } from "./CallsJoinSection";
import {
  buildCallsHistoryEmptyState,
  buildCallsLinkAction,
  formatMissedCallsSummary,
  findExactCallsPublicChatMatch,
  normalizeCallLinkToken
} from "./callsPresentation";
import type { CallsScreenController } from "./useCallsScreenController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenStack } from "../ui/ScreenStack";
import { SectionCard } from "../ui/SectionCard";
import type { ParsedDeepLink } from "../../navigation/deepLinks";
import type { ChatSummary } from "../../types";

type CallsScreenContentProps = {
  availableChats: ChatSummary[];
  callJoinLinksEnabled: boolean;
  controller: CallsScreenController;
  onCallBack: (chatId: string, kind: "VOICE" | "VIDEO") => void;
  onClose: () => void;
  onJoinCallLink: (rawToken: string) => void;
  onOpenChat: (chatId: string) => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
};

export function CallsScreenContent({
  availableChats,
  callJoinLinksEnabled,
  controller,
  onCallBack,
  onClose,
  onJoinCallLink,
  onOpenChat,
  onOpenParsedLink
}: CallsScreenContentProps) {
  const exactPublicChatMatch = findExactCallsPublicChatMatch(
    availableChats,
    controller.parsedLink
  );
  const linkAction = buildCallsLinkAction(
    controller.parsedLink,
    exactPublicChatMatch
  );
  const emptyState = buildCallsHistoryEmptyState(
    controller.recentCalls.length === 0 ? controller.error : null
  );

  function handleJoinCallLink() {
    const normalized = normalizeCallLinkToken(controller.callLinkToken);
    if (!normalized) {
      return;
    }

    onJoinCallLink(normalized);
  }

  return (
    <>
      <AppHeader
        onBack={onClose}
        rightSlot={(
          <AppButton onPress={() => void controller.loadRecentCalls()} size="sm">
            {controller.refreshing ? "..." : "Refresh"}
          </AppButton>
        )}
        subtitle={formatMissedCallsSummary(controller.missedCallsCount)}
        title="Calls"
      />
      <ScreenFeedback error={controller.error} loading={controller.refreshing} />

      <ScreenStack flex={1} gap="md">
        {controller.parsedLink && linkAction ? (
          <SectionCard description={linkAction.description} title={linkAction.title}>
            <AppButton
              fullWidth
              onPress={() => {
                if (exactPublicChatMatch) {
                  onOpenChat(exactPublicChatMatch.chatId);
                  return;
                }

                onOpenParsedLink(controller.parsedLink!);
              }}
              variant="primary"
            >
              {linkAction.ctaLabel}
            </AppButton>
          </SectionCard>
        ) : null}

        {callJoinLinksEnabled ? (
          <CallsJoinSection
            callLinkToken={controller.callLinkToken}
            canJoinCallLink={controller.canJoinCallLink}
            onCallLinkTokenChange={controller.handleCallLinkTokenChange}
            onJoinCallLink={handleJoinCallLink}
          />
        ) : null}

        <CallsHistoryList
          calls={controller.recentCalls}
          emptyStateDescription={emptyState.description}
          emptyStateTitle={emptyState.title}
          onCallBack={onCallBack}
          onOpenChat={onOpenChat}
        />
      </ScreenStack>
    </>
  );
}
