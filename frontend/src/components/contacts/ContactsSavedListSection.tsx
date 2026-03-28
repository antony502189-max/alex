import React, { useMemo } from "react";
import { AppButton } from "../ui/AppButton";
import type {
  BlockedUser,
  BotSummary,
  Contact,
  DeviceContactRecord,
  ImportContactsResult
} from "../../types";
import { ContactsDefaultSections } from "./ContactsDefaultSections";
import { ContactsUserList, type ContactUserListItem } from "./ContactsUserList";
import { buildUserMetaLines } from "./contactsPresentation";

type ContactsSavedListSectionProps = {
  actionUserKey: string | null;
  blockedUsers: BlockedUser[];
  bots: BotSummary[];
  contacts: Contact[];
  deviceContactsList: DeviceContactRecord[];
  importSummary: ImportContactsResult | null;
  isBlocked: (userId: string) => boolean;
  onBlockUser: (userId: string) => void | Promise<void>;
  onLoadDeviceContacts: () => void | Promise<void>;
  onOpenBotMiniApp?: (botUserId: string, title: string) => void;
  onOpenDirect: (userId: string) => void | Promise<void>;
  onRemoveContact: (userId: string) => void | Promise<void>;
  onUnblockUser: (userId: string) => void | Promise<void>;
  syncingDeviceContacts: boolean;
};

export function ContactsSavedListSection({
  actionUserKey,
  blockedUsers,
  bots,
  contacts,
  deviceContactsList,
  importSummary,
  isBlocked,
  onBlockUser,
  onLoadDeviceContacts,
  onOpenBotMiniApp,
  onOpenDirect,
  onRemoveContact,
  onUnblockUser,
  syncingDeviceContacts
}: ContactsSavedListSectionProps) {
  const listHeaderComponent = useMemo(
    () => (
      <ContactsDefaultSections
        actionUserKey={actionUserKey}
        blockedUsers={blockedUsers}
        bots={bots}
        deviceContactsList={deviceContactsList}
        importSummary={importSummary}
        onLoadDeviceContacts={() => void onLoadDeviceContacts()}
        onOpenBotMiniApp={(botUserId, title) => onOpenBotMiniApp?.(botUserId, title)}
        onOpenDirect={(userId) => void onOpenDirect(userId)}
        onUnblockUser={(userId) => void onUnblockUser(userId)}
        syncingDeviceContacts={syncingDeviceContacts}
      />
    ),
    [actionUserKey, blockedUsers, bots, deviceContactsList, importSummary, onLoadDeviceContacts, onOpenBotMiniApp, onOpenDirect, onUnblockUser, syncingDeviceContacts]
  );

  const items = useMemo<ContactUserListItem[]>(
    () =>
      contacts.map((item) => ({
        actions: (
          <>
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
            <AppButton
              disabled={actionUserKey === `remove:${item.userId}`}
              onPress={() => void onRemoveContact(item.userId)}
              size="sm"
              variant="danger"
            >
              {actionUserKey === `remove:${item.userId}` ? "..." : "Remove"}
            </AppButton>
          </>
        ),
        description: item.botDescription,
        key: item.userId,
        metaLines: buildUserMetaLines(
          {
            bot: item.bot,
            botSupportsInline: item.botSupportsInline,
            lastSeenAt: item.lastSeenAt,
            online: item.online,
            phoneNumber: item.phoneNumber,
            username: item.username
          },
          isBlocked(item.userId)
        ),
        photoUrl: item.photoUrl,
        title: item.contactName
      })),
    [actionUserKey, contacts, isBlocked, onBlockUser, onOpenBotMiniApp, onOpenDirect, onRemoveContact, onUnblockUser]
  );

  return (
    <ContactsUserList
      emptyStateText="No contacts yet."
      items={items}
      listHeaderComponent={listHeaderComponent}
    />
  );
}
