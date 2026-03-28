import React, { useMemo } from "react";
import { AppButton } from "../ui/AppButton";
import { ContactsUserList, type ContactUserListItem } from "./ContactsUserList";
import { buildUserMetaLines } from "./contactsPresentation";
import type { UserSearchResult } from "../../types";

type ContactsSearchResultsSectionProps = {
  actionUserKey: string | null;
  isBlocked: (userId: string) => boolean;
  onAddContact: (user: UserSearchResult) => void | Promise<void>;
  onBlockUser: (userId: string) => void | Promise<void>;
  onOpenBotMiniApp?: (botUserId: string, title: string) => void;
  onOpenDirect: (userId: string) => void | Promise<void>;
  onReportUser: (userId: string) => void | Promise<void>;
  onUnblockUser: (userId: string) => void | Promise<void>;
  results: UserSearchResult[];
};

export function ContactsSearchResultsSection({
  actionUserKey,
  isBlocked,
  onAddContact,
  onBlockUser,
  onOpenBotMiniApp,
  onOpenDirect,
  onReportUser,
  onUnblockUser,
  results
}: ContactsSearchResultsSectionProps) {
  const items = useMemo<ContactUserListItem[]>(
    () =>
      results.map((item) => ({
        actions: (
          <>
            {!item.bot ? (
              <AppButton onPress={() => void onAddContact(item)} size="sm" variant="secondary">
                Add
              </AppButton>
            ) : null}
            {!item.bot ? (
              <AppButton
                disabled={actionUserKey === `report:${item.userId}`}
                onPress={() => void onReportUser(item.userId)}
                size="sm"
                variant="secondary"
              >
                {actionUserKey === `report:${item.userId}` ? "..." : "Report"}
              </AppButton>
            ) : null}
            {!item.bot ? (
              isBlocked(item.userId) ? (
                <AppButton
                  disabled={actionUserKey === `unblock:${item.userId}`}
                  onPress={() => void onUnblockUser(item.userId)}
                  size="sm"
                  variant="secondary"
                >
                  {actionUserKey === `unblock:${item.userId}` ? "..." : "Unblock"}
                </AppButton>
              ) : (
                <AppButton
                  disabled={actionUserKey === `block:${item.userId}`}
                  onPress={() => void onBlockUser(item.userId)}
                  size="sm"
                  variant="danger"
                >
                  {actionUserKey === `block:${item.userId}` ? "..." : "Block"}
                </AppButton>
              )
            ) : null}
            {item.bot && item.botWebAppUrl && onOpenBotMiniApp ? (
              <AppButton
                onPress={() => onOpenBotMiniApp(item.userId, item.displayName)}
                size="sm"
                variant="secondary"
              >
                Mini App
              </AppButton>
            ) : null}
            <AppButton onPress={() => void onOpenDirect(item.userId)} size="sm" variant="primary">
              Chat
            </AppButton>
          </>
        ),
        description: item.botDescription,
        key: item.userId,
        metaLines: buildUserMetaLines(item, isBlocked(item.userId)),
        photoUrl: item.photoUrl,
        title: item.displayName
      })),
    [actionUserKey, isBlocked, onAddContact, onBlockUser, onOpenBotMiniApp, onOpenDirect, onReportUser, onUnblockUser, results]
  );

  return <ContactsUserList items={items} />;
}
