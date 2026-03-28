import React from "react";
import { ActivityIndicator, StyleSheet, Text } from "react-native";
import { JoinChatDiscoveriesList } from "./JoinChatDiscoveriesList";
import {
  buildJoinChatLinkAction,
  findExactPublicChatDiscovery,
  getJoinFieldActionLabel
} from "./joinChatPresentation";
import type { JoinChatByLinkScreenController } from "./useJoinChatByLinkController";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type { ParsedDeepLink } from "../../navigation/deepLinks";

type JoinChatByLinkScreenContentProps = {
  controller: JoinChatByLinkScreenController;
  onClose: () => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
};

export function JoinChatByLinkScreenContent({
  controller,
  onClose,
  onOpenParsedLink
}: JoinChatByLinkScreenContentProps) {
  const linkAction = buildJoinChatLinkAction(controller.parsedLink);
  const exactDiscovery = findExactPublicChatDiscovery(
    controller.discoveries,
    controller.normalizedInviteToken
  );
  const joinActionLabel = controller.exactPublicChatMatch
    ? "Open chat"
    : getJoinFieldActionLabel(exactDiscovery);

  function handlePrimaryJoinAction() {
    if (controller.exactPublicChatMatch) {
      controller.handleOpenExactPublicChat();
      return;
    }

    if (exactDiscovery) {
      void controller.handleJoinDiscoveredChat(exactDiscovery);
      return;
    }

    void controller.handleJoin();
  }

  return (
    <>
      <AppHeader onBack={onClose} title="Join by link" />

      {controller.parsedLink && linkAction ? (
        <SectionCard description={linkAction.description} title={linkAction.title}>
          <AppButton
            fullWidth
            onPress={() => onOpenParsedLink(controller.parsedLink!)}
            variant="primary"
          >
            {linkAction.ctaLabel}
          </AppButton>
        </SectionCard>
      ) : null}

      <SectionCard>
        <Text style={styles.body}>
          Paste an invite link, a public `@username`, a call link, or an app chat link.
        </Text>
        <AppTextField
          autoCapitalize="none"
          autoCorrect={false}
          onChangeText={controller.handleInviteTokenChange}
          placeholder="t.me/... , tg://... , @channel, or alex://chat/..."
          value={controller.inviteToken}
        />
        <ScreenFeedback error={controller.error} notice={controller.statusMessage} />
        <AppButton
          disabled={controller.joining || !controller.canJoin}
          fullWidth
          onPress={handlePrimaryJoinAction}
          variant="primary"
        >
          {controller.joining ? "Joining..." : joinActionLabel}
        </AppButton>
        {controller.discovering ? (
          <ActivityIndicator color={appColors.textPrimary} style={styles.loader} />
        ) : null}
      </SectionCard>

      <JoinChatDiscoveriesList
        discoveries={controller.discoveries}
        joining={controller.joining}
        onJoinDiscovery={(chat) => void controller.handleJoinDiscoveredChat(chat)}
      />
    </>
  );
}

const styles = StyleSheet.create({
  body: {
    color: appColors.textSecondary,
    lineHeight: 20
  },
  loader: {
    marginTop: appSpacing.sm
  }
});
