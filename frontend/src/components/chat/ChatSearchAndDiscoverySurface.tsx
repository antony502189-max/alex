import React from "react";
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View
} from "react-native";
import { AppBanner } from "../ui/AppBanner";
import { AppButton } from "../ui/AppButton";
import { AppPanel } from "../ui/AppPanel";
import { AppTextField } from "../ui/AppTextField";
import type { BotCommand, InlineBotResult } from "../../types";

type ChatSearchAndDiscoverySurfaceProps = {
  activeInlineBotUsername: string | null;
  botCommands: BotCommand[];
  botCommandsError: string | null;
  error: string | null;
  hasMoreHistory: boolean;
  inlineBotResults: InlineBotResult[];
  inlineBotResultsError: string | null;
  jumpingToMessage: boolean;
  loadingBotCommands: boolean;
  loadingHistory: boolean;
  loadingInlineBotResults: boolean;
  loadingOlder: boolean;
  onChangeSearchQuery: (value: string) => void;
  onClearSearch: () => void;
  onInsertBotCommand: (command: string) => void;
  onLoadOlder: () => void | Promise<void>;
  onOpenMiniApp?: () => void | Promise<void>;
  onRetryBotCommands: () => void;
  onRetryInlineBotResults: () => void;
  onSendInlineResult: (result: InlineBotResult) => void | Promise<void>;
  restrictionLabel: string | null;
  restrictionReason: string | null;
  searchQuery: string;
  searchResultsCount: number;
  searching: boolean;
  showBotCommandsPanel: boolean;
  showLoadOlderButton: boolean;
  showMiniAppAction: boolean;
};

export function ChatSearchAndDiscoverySurface({
  activeInlineBotUsername,
  botCommands,
  botCommandsError,
  error,
  hasMoreHistory,
  inlineBotResults,
  inlineBotResultsError,
  jumpingToMessage,
  loadingBotCommands,
  loadingHistory,
  loadingInlineBotResults,
  loadingOlder,
  onChangeSearchQuery,
  onClearSearch,
  onInsertBotCommand,
  onLoadOlder,
  onOpenMiniApp,
  onRetryBotCommands,
  onRetryInlineBotResults,
  onSendInlineResult,
  restrictionLabel,
  restrictionReason,
  searchQuery,
  searchResultsCount,
  searching,
  showBotCommandsPanel,
  showLoadOlderButton,
  showMiniAppAction
}: ChatSearchAndDiscoverySurfaceProps) {
  const trimmedQuery = searchQuery.trim();
  const showSearchBanner = trimmedQuery.length >= 2;

  return (
    <>
      <View style={styles.searchRow}>
        <AppTextField
          autoCapitalize="none"
          onChangeText={onChangeSearchQuery}
          placeholder="Search messages"
          style={styles.searchInputField}
          value={searchQuery}
        />
        {showSearchBanner ? (
          <AppButton onPress={onClearSearch} size="sm" variant="secondary">
            Clear
          </AppButton>
        ) : null}
      </View>

      {showSearchBanner ? (
        <AppBanner
          message={
            searching
              ? "Searching..."
              : `${searchResultsCount} result${searchResultsCount === 1 ? "" : "s"} for "${trimmedQuery}"`
          }
          style={styles.banner}
          tone="info"
        />
      ) : null}

      {showBotCommandsPanel ? (
        <AppPanel
          style={styles.botCommandBar}
          title="Bot commands"
          titleStyle={styles.selectionTitle}
          tone="brand"
        >
          {showMiniAppAction && onOpenMiniApp ? (
            <View style={styles.rowWrap}>
              <AppButton onPress={() => void onOpenMiniApp()} size="sm" variant="secondary">
                Open mini app
              </AppButton>
            </View>
          ) : null}
          {loadingBotCommands ? (
            <Text style={styles.botMeta}>Loading bot commands...</Text>
          ) : botCommandsError ? (
            <View style={styles.metaActionBlock}>
              <Text style={styles.botMeta}>{botCommandsError}</Text>
              <AppButton onPress={onRetryBotCommands} size="sm" variant="secondary">
                Retry commands
              </AppButton>
            </View>
          ) : botCommands.length > 0 ? (
            <View style={styles.rowWrap}>
              {botCommands.map((command) => (
                <AppButton
                  key={command.command}
                  onPress={() => onInsertBotCommand(command.command)}
                  size="sm"
                  variant="secondary"
                >
                  {command.command}
                </AppButton>
              ))}
            </View>
          ) : (
            <Text style={styles.botMeta}>This bot has no command shortcuts right now.</Text>
          )}
        </AppPanel>
      ) : null}

      {activeInlineBotUsername ? (
        <AppPanel
          style={styles.inlineResultsBar}
          title={`Inline results for @${activeInlineBotUsername}`}
          titleStyle={styles.inlineResultsTitle}
          tone="success"
        >
          {loadingInlineBotResults ? (
            <Text style={styles.inlineResultsMeta}>Loading inline results...</Text>
          ) : inlineBotResultsError ? (
            <View style={styles.metaActionBlock}>
              <Text style={styles.inlineResultsMeta}>{inlineBotResultsError}</Text>
              <AppButton onPress={onRetryInlineBotResults} size="sm" variant="secondary">
                Retry inline
              </AppButton>
            </View>
          ) : inlineBotResults.length === 0 ? (
            <Text style={styles.inlineResultsMeta}>No inline results.</Text>
          ) : (
            <View style={styles.inlineResultsList}>
              {inlineBotResults.map((result) => (
                <Pressable
                  key={`${result.botUserId}:${result.resultId}`}
                  onPress={() => void onSendInlineResult(result)}
                  style={styles.inlineResultCard}
                >
                  <Text style={styles.inlineResultTitle}>{result.title}</Text>
                  <Text style={styles.inlineResultDescription}>{result.description}</Text>
                </Pressable>
              ))}
            </View>
          )}
        </AppPanel>
      ) : null}

      {!loadingHistory && showLoadOlderButton && hasMoreHistory ? (
        <AppButton
          disabled={loadingOlder}
          fullWidth
          onPress={() => void onLoadOlder()}
          style={styles.loadOlderButton}
          variant="secondary"
        >
          {loadingOlder ? "Loading..." : "Load earlier messages"}
        </AppButton>
      ) : null}

      {loadingHistory ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <AppBanner message={error} style={styles.banner} tone="danger" /> : null}
      {restrictionLabel ? (
        <AppPanel
          style={styles.restrictionBar}
          title="Read-only mode"
          titleStyle={styles.restrictionBarTitle}
          tone="danger"
        >
          <Text style={styles.restrictionBarText}>{restrictionLabel}</Text>
          {restrictionReason ? (
            <Text style={styles.restrictionBarText}>{restrictionReason}</Text>
          ) : null}
        </AppPanel>
      ) : null}
      {jumpingToMessage ? (
        <AppBanner
          message="Locating message in the chat history..."
          style={styles.banner}
          tone="info"
        />
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  banner: {
    marginHorizontal: 16
  },
  botMeta: {
    color: "#92400e",
    marginTop: 8
  },
  botCommandBar: {
    backgroundColor: "#eef2ff",
    borderRadius: 14,
    marginBottom: 8,
    marginHorizontal: 16,
    padding: 12
  },
  inlineResultCard: {
    backgroundColor: "#ffffff",
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  inlineResultDescription: {
    color: "#475569",
    fontSize: 12,
    marginTop: 4
  },
  inlineResultTitle: {
    color: "#0f172a",
    fontWeight: "700"
  },
  inlineResultsBar: {
    backgroundColor: "#ecfccb",
    borderRadius: 14,
    marginBottom: 8,
    marginHorizontal: 16,
    padding: 12
  },
  inlineResultsList: {
    gap: 8,
    marginTop: 10
  },
  inlineResultsMeta: {
    color: "#4d7c0f",
    marginTop: 6
  },
  inlineResultsTitle: {
    color: "#365314",
    fontWeight: "700"
  },
  loadOlderButton: {
    alignItems: "center",
    backgroundColor: "#e2e8f0",
    borderRadius: 14,
    marginBottom: 8,
    marginHorizontal: 16,
    paddingVertical: 12
  },
  loader: {
    marginTop: 12
  },
  metaActionBlock: {
    gap: 8,
    marginTop: 8
  },
  restrictionBar: {
    backgroundColor: "#fff7ed",
    borderRadius: 14,
    marginBottom: 8,
    marginHorizontal: 16,
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  restrictionBarText: {
    color: "#9a3412",
    marginTop: 4
  },
  restrictionBarTitle: {
    color: "#9a3412",
    fontWeight: "700"
  },
  rowWrap: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 8
  },
  searchInputField: {
    flex: 1
  },
  searchRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: 8,
    marginBottom: 8,
    paddingHorizontal: 16
  },
  selectionTitle: {
    color: "#92400e",
    fontWeight: "700"
  }
});
