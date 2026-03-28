import React from "react";
import {
  Pressable,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { AppChip } from "../ui/AppChip";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type {
  ChatSummary,
  GlobalMessageSearchResult,
  GlobalSearchResponse,
  UserSearchResult
} from "../../types";
import {
  buildGlobalSearchUserMeta,
  describeGlobalSearchChat,
  describeGlobalSearchMessage
} from "./globalSearchPresentation";

type GlobalSearchResultsProps = {
  onOpenChat: (chat: ChatSummary) => void;
  onOpenMessageResult: (chat: ChatSummary, message: GlobalMessageSearchResult["message"]) => void;
  onOpenUser: (user: UserSearchResult) => void;
  openingUserId: string | null;
  results: GlobalSearchResponse | null;
};

export function GlobalSearchResults({
  onOpenChat,
  onOpenMessageResult,
  onOpenUser,
  openingUserId,
  results
}: GlobalSearchResultsProps) {
  return (
    <>
      {results?.users.length ? (
        <SectionCard description="People matching your current query." title="People">
          <View style={styles.sectionList}>
            {results.users.map((user) => {
              const metaLines = buildGlobalSearchUserMeta(user);

              return (
                <View key={user.userId} style={styles.card}>
                  <Avatar uri={user.photoUrl} size={52} title={user.displayName} />
                  <View style={styles.cardBody}>
                    <Text style={styles.cardTitle}>{user.displayName}</Text>
                    {metaLines.map((line) => (
                      <Text key={`${user.userId}:${line}`} style={styles.cardMeta}>
                        {line}
                      </Text>
                    ))}
                  </View>
                  <AppButton
                    disabled={openingUserId === user.userId}
                    onPress={() => onOpenUser(user)}
                    size="sm"
                    variant="primary"
                  >
                    {openingUserId === user.userId ? "..." : "Chat"}
                  </AppButton>
                </View>
              );
            })}
          </View>
        </SectionCard>
      ) : null}

      {results?.chats.length ? (
        <SectionCard description="Chats and channels related to the query." title="Chats">
          <View style={styles.sectionList}>
            {results.chats.map((chat) => (
              <Pressable
                key={chat.chatId}
                onPress={() => onOpenChat(chat)}
                style={({ pressed }) => [styles.card, pressed && styles.pressed]}
              >
                <Avatar uri={chat.photoUrl} size={52} title={chat.title} />
                <View style={styles.cardBody}>
                  <Text style={styles.cardTitle}>{chat.title}</Text>
                  <Text style={styles.cardMeta}>{describeGlobalSearchChat(chat)}</Text>
                  {chat.about ? (
                    <Text numberOfLines={1} style={styles.cardMeta}>
                      {chat.about}
                    </Text>
                  ) : null}
                </View>
              </Pressable>
            ))}
          </View>
        </SectionCard>
      ) : null}

      {results?.messages.length ? (
        <SectionCard description="Jump directly to matching message history." title="Messages">
          <View style={styles.sectionList}>
            {results.messages.map((result) => (
              <Pressable
                key={`${result.chat.chatId}:${result.message.messageId}`}
                onPress={() => onOpenMessageResult(result.chat, result.message)}
                style={({ pressed }) => [styles.card, pressed && styles.pressed]}
              >
                <Avatar uri={result.chat.photoUrl} size={52} title={result.chat.title} />
                <View style={styles.cardBody}>
                  <Text style={styles.cardTitle}>{result.chat.title}</Text>
                  <Text numberOfLines={2} style={styles.messageSnippet}>
                    {describeGlobalSearchMessage(result)}
                  </Text>
                  <Text style={styles.cardMeta}>
                    {new Date(result.message.createdAt).toLocaleString()}
                  </Text>
                </View>
                <AppChip tone="brand">Jump</AppChip>
              </Pressable>
            ))}
          </View>
        </SectionCard>
      ) : null}
    </>
  );
}

const styles = StyleSheet.create({
  sectionList: {
    gap: appSpacing.sm + 2
  },
  card: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.lg
  },
  cardBody: {
    flex: 1,
    gap: appSpacing.xs
  },
  cardTitle: {
    color: appColors.textPrimary,
    fontSize: 17,
    fontWeight: "600"
  },
  cardMeta: {
    color: appColors.textSecondary
  },
  messageSnippet: {
    color: appColors.textPrimary
  },
  pressed: {
    opacity: 0.9
  }
});
