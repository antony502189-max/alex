import React from "react";
import { StyleSheet, Text } from "react-native";
import { GlobalSearchResults } from "./GlobalSearchResults";
import {
  buildGlobalSearchLinkAction,
  buildGlobalSearchInfoText
} from "./globalSearchPresentation";
import type { GlobalSearchScreenController } from "./useGlobalSearchController";
import { AppBanner } from "../ui/AppBanner";
import { AppButton } from "../ui/AppButton";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";
import { AppTextField } from "../ui/AppTextField";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appSpacing } from "../../theme/tokens";
import type {
  ChatSummary,
  GlobalMessageSearchResult
} from "../../types";
import type { ParsedDeepLink } from "../../navigation/deepLinks";

type GlobalSearchScreenContentProps = {
  controller: GlobalSearchScreenController;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenMessageResult: (chat: ChatSummary, message: GlobalMessageSearchResult["message"]) => void;
  onOpenParsedLink: (parsedLink: ParsedDeepLink) => void;
};

export function GlobalSearchScreenContent({
  controller,
  onClose,
  onOpenChat,
  onOpenMessageResult,
  onOpenParsedLink
}: GlobalSearchScreenContentProps) {
  const linkAction = buildGlobalSearchLinkAction(
    controller.parsedLink,
    controller.exactPublicChatMatch
  );
  const infoText = buildGlobalSearchInfoText(
    controller.query,
    controller.loading,
    controller.resultSummary,
    linkAction
  );

  return (
    <>
      <AppHeader onBack={onClose} title="Global search" />

      <AppTextField
        autoCapitalize="none"
        onChangeText={controller.setQuery}
        placeholder="Search chats, people, messages"
        style={styles.searchField}
        value={controller.query}
      />

      <AppBanner message={infoText} style={styles.infoBanner} />
      <ScreenFeedback
        error={controller.error}
        loading={controller.loading}
        loadingFirst={false}
        loadingStyle={styles.loader}
      />

      <ScreenScrollView gap="md" paddingBottom="xl">
        {controller.parsedLink && linkAction ? (
          <SectionCard description={linkAction.description} title={linkAction.title}>
            <AppButton
              fullWidth
              onPress={() => {
                if (controller.exactPublicChatMatch) {
                  onOpenChat(controller.exactPublicChatMatch);
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

        <GlobalSearchResults
          onOpenChat={onOpenChat}
          onOpenMessageResult={onOpenMessageResult}
          onOpenUser={(user) => void controller.handleOpenUser(user)}
          openingUserId={controller.openingUserId}
          results={controller.results}
        />

        {controller.normalizedQuery.length >= 2 &&
        !controller.loading &&
        !controller.hasResults &&
        !controller.parsedLink ? (
          <Text style={styles.emptyState}>No matches found.</Text>
        ) : null}
      </ScreenScrollView>
    </>
  );
}

const styles = StyleSheet.create({
  searchField: {
    marginBottom: appSpacing.sm
  },
  infoBanner: {
    marginBottom: appSpacing.sm
  },
  loader: {
    marginBottom: appSpacing.sm
  },
  emptyState: {
    color: appColors.textSecondary,
    paddingTop: appSpacing.xs
  }
});
