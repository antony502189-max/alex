import React from "react";
import {
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../Avatar";
import { AppChip } from "../ui/AppChip";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { UserSearchResult } from "../../types";
import type { CreateChatMode } from "./createChatPresentation";
import {
  buildCreateChatEmptyState,
  buildCreateChatUserMeta
} from "./createChatPresentation";

type CreateChatResultsListProps = {
  loading: boolean;
  mode: CreateChatMode;
  onSelectDirect: (userId: string) => void;
  onToggleUser: (userId: string) => void;
  query: string;
  results: UserSearchResult[];
  selectedUserIds: string[];
  submitting: boolean;
};

export function CreateChatResultsList({
  loading,
  mode,
  onSelectDirect,
  onToggleUser,
  query,
  results,
  selectedUserIds,
  submitting
}: CreateChatResultsListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.listContent}
      data={results}
      keyExtractor={(item) => item.userId}
      ListEmptyComponent={
        !loading ? <Text style={styles.emptyState}>{buildCreateChatEmptyState(query)}</Text> : null
      }
      renderItem={({ item }) => {
        const selected = selectedUserIds.includes(item.userId);
        const metaLines = buildCreateChatUserMeta(item);

        return (
          <Pressable
            disabled={submitting}
            onPress={() =>
              mode === "direct" ? onSelectDirect(item.userId) : onToggleUser(item.userId)
            }
            style={({ pressed }) => [
              styles.card,
              selected && styles.cardSelected,
              pressed && !submitting && styles.pressed,
              submitting && styles.disabled
            ]}
          >
            <Avatar uri={item.photoUrl} title={item.displayName} size={48} />
            <View style={styles.body}>
              <Text style={styles.name}>{item.displayName}</Text>
              {metaLines.map((line) => (
                <Text key={`${item.userId}:${line}`} style={styles.meta}>
                  {line}
                </Text>
              ))}
            </View>
            {mode !== "direct" ? (
              <AppChip active={selected} tone={selected ? "brand" : "muted"}>
                {selected ? "Selected" : "Select"}
              </AppChip>
            ) : null}
          </Pressable>
        );
      }}
    />
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: appSpacing.md,
    paddingBottom: appSpacing.xl
  },
  card: {
    alignItems: "center",
    backgroundColor: appColors.surface,
    borderRadius: appRadii.lg,
    flexDirection: "row",
    gap: appSpacing.md,
    padding: appSpacing.lg
  },
  cardSelected: {
    borderColor: appColors.textPrimary,
    borderWidth: 2
  },
  body: {
    flex: 1,
    gap: appSpacing.xs
  },
  name: {
    color: appColors.textPrimary,
    fontSize: 18,
    fontWeight: "600"
  },
  meta: {
    color: appColors.textSecondary,
    fontSize: 13
  },
  emptyState: {
    color: appColors.textSecondary,
    lineHeight: 22,
    paddingTop: appSpacing.xl
  },
  pressed: {
    opacity: 0.9
  },
  disabled: {
    opacity: 0.6
  }
});
