import React from "react";
import { FlatList, StyleSheet, Text } from "react-native";
import { appColors, appSpacing } from "../../theme/tokens";
import { ContactUserCard } from "./ContactUserCard";

export type ContactUserListItem = {
  actions?: React.ReactNode;
  description?: string | null;
  key: string;
  metaLines: string[];
  photoUrl: string | null;
  title: string;
};

type ContactsUserListProps = {
  emptyStateText?: string;
  items: ContactUserListItem[];
  listHeaderComponent?: React.ReactElement | null;
};

export function ContactsUserList({
  emptyStateText,
  items,
  listHeaderComponent
}: ContactsUserListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.listContent}
      data={items}
      keyExtractor={(item) => item.key}
      ListEmptyComponent={
        emptyStateText ? <Text style={styles.emptyState}>{emptyStateText}</Text> : null
      }
      ListHeaderComponent={listHeaderComponent}
      renderItem={({ item }) => (
        <ContactUserCard
          actions={item.actions}
          description={item.description}
          metaLines={item.metaLines}
          photoUrl={item.photoUrl}
          title={item.title}
        />
      )}
    />
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: appSpacing.md,
    paddingBottom: appSpacing.xl
  },
  emptyState: {
    color: appColors.textSecondary,
    paddingTop: appSpacing.xxl
  }
});
