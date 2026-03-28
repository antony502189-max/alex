import { useCallback, useEffect, useRef, useState } from "react";
import { useFocusEffect } from "@react-navigation/native";
import { AppState } from "react-native";
import { api } from "../../services/api";
import { deviceContacts } from "../../services/deviceContacts";
import {
  getUniquePresenceUserIds,
  mergePresenceIntoContacts,
  mergePresenceIntoUserResults
} from "../../services/presenceSync";
import type {
  BlockedUser,
  BotSummary,
  ChatSummary,
  Contact,
  ImportContactsResult,
  ImportedPhoneContact,
  DeviceContactRecord,
  UserSearchResult
} from "../../types";

type UseContactsControllerParams = {
  onOpenChat: (chat: ChatSummary) => void;
  token: string;
};

export function useContactsController({
  onOpenChat,
  token
}: UseContactsControllerParams) {
  const [bots, setBots] = useState<BotSummary[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [blockedUsers, setBlockedUsers] = useState<BlockedUser[]>([]);
  const [deviceContactsList, setDeviceContactsList] = useState<DeviceContactRecord[]>([]);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searching, setSearching] = useState(false);
  const [syncingDeviceContacts, setSyncingDeviceContacts] = useState(false);
  const [importSummary, setImportSummary] = useState<ImportContactsResult | null>(null);
  const [actionUserKey, setActionUserKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const contactsRef = useRef<Contact[]>([]);
  const resultsRef = useRef<UserSearchResult[]>([]);

  useEffect(() => {
    contactsRef.current = contacts;
  }, [contacts]);

  useEffect(() => {
    resultsRef.current = results;
  }, [results]);

  const refreshContactsPresence = useCallback(async (seedContacts?: Contact[]) => {
    const source = seedContacts ?? contactsRef.current;
    const userIds = getUniquePresenceUserIds(source.map((contact) => contact.userId));
    if (userIds.length === 0) {
      return;
    }

    try {
      const statuses = await api.getUsersPresence(token, userIds);
      setContacts((current) => mergePresenceIntoContacts(seedContacts ?? current, statuses));
    } catch {
    }
  }, [token]);

  const refreshResultsPresence = useCallback(async (seedResults?: UserSearchResult[]) => {
    const source = seedResults ?? resultsRef.current;
    const userIds = getUniquePresenceUserIds(source.map((result) => result.userId));
    if (userIds.length === 0) {
      return;
    }

    try {
      const statuses = await api.getUsersPresence(token, userIds);
      setResults((current) => mergePresenceIntoUserResults(seedResults ?? current, statuses));
    } catch {
    }
  }, [token]);

  const loadContacts = useCallback(async () => {
    setLoading(true);
    setError(null);
    setNotice(null);
    try {
      const [nextContacts, nextBots, nextBlockedUsers] = await Promise.all([
        api.getContacts(token),
        api.getBots(token),
        api.getBlockedUsers(token)
      ]);
      setContacts(nextContacts);
      setBots(nextBots);
      setBlockedUsers(nextBlockedUsers);
      void refreshContactsPresence(nextContacts);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load contacts");
    } finally {
      setLoading(false);
    }
  }, [refreshContactsPresence, token]);

  useEffect(() => {
    void loadContacts();
  }, [loadContacts]);

  useEffect(() => {
    let cancelled = false;
    const normalized = query.trim();
    if (normalized.length < 2) {
      setResults([]);
      setSearching(false);
      return;
    }

    const timeoutId = setTimeout(() => {
      setSearching(true);
      setError(null);
      setNotice(null);
      api.searchUsers(token, normalized)
        .then((nextResults) => {
          if (!cancelled) {
            setResults(nextResults);
            void refreshResultsPresence(nextResults);
          }
        })
        .catch((searchError) => {
          if (!cancelled) {
            setError(searchError instanceof Error ? searchError.message : "Unable to search users");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setSearching(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timeoutId);
    };
  }, [query, refreshResultsPresence, token]);

  useEffect(() => {
    const appStateSubscription = AppState.addEventListener("change", (nextState) => {
      if (nextState !== "active") {
        return;
      }

      void refreshContactsPresence().catch(() => undefined);
      void refreshResultsPresence().catch(() => undefined);
    });
    const intervalId = setInterval(() => {
      void refreshContactsPresence().catch(() => undefined);
      void refreshResultsPresence().catch(() => undefined);
    }, 45_000);

    return () => {
      appStateSubscription.remove();
      clearInterval(intervalId);
    };
  }, [refreshContactsPresence, refreshResultsPresence]);

  useFocusEffect(
    useCallback(() => {
      void refreshContactsPresence().catch(() => undefined);
      void refreshResultsPresence().catch(() => undefined);
    }, [refreshContactsPresence, refreshResultsPresence])
  );

  const isBlocked = useCallback(
    (userId: string) => blockedUsers.some((user) => user.userId === userId),
    [blockedUsers]
  );

  const handleOpenDirect = useCallback(async (userId: string) => {
    setError(null);
    setNotice(null);
    try {
      const chat = await api.createDirectChat(token, userId);
      onOpenChat(chat);
    } catch (openError) {
      setError(openError instanceof Error ? openError.message : "Unable to open chat");
    }
  }, [onOpenChat, token]);

  const handleAddContact = useCallback(async (user: UserSearchResult) => {
    setError(null);
    setNotice(null);
    try {
      const nextContacts = await api.addContact(token, {
        contactUserId: user.userId,
        contactName: user.displayName
      });
      setContacts(nextContacts);
      void refreshContactsPresence(nextContacts).catch(() => undefined);
      setNotice("Contact added.");
    } catch (addError) {
      setError(addError instanceof Error ? addError.message : "Unable to add contact");
    }
  }, [token]);

  const handleRemoveContact = useCallback(async (userId: string) => {
    setActionUserKey(`remove:${userId}`);
    setError(null);
    setNotice(null);
    try {
      const nextContacts = await api.removeContact(token, userId);
      setContacts(nextContacts);
      void refreshContactsPresence(nextContacts).catch(() => undefined);
      setNotice("Contact removed.");
    } catch (removeError) {
      setError(removeError instanceof Error ? removeError.message : "Unable to remove contact");
    } finally {
      setActionUserKey(null);
    }
  }, [token]);

  const handleBlockUser = useCallback(async (userId: string) => {
    setActionUserKey(`block:${userId}`);
    setError(null);
    setNotice(null);
    try {
      setBlockedUsers(await api.blockUser(token, userId));
      setNotice("User blocked.");
    } catch (blockError) {
      setError(blockError instanceof Error ? blockError.message : "Unable to block user");
    } finally {
      setActionUserKey(null);
    }
  }, [token]);

  const handleUnblockUser = useCallback(async (userId: string) => {
    setActionUserKey(`unblock:${userId}`);
    setError(null);
    setNotice(null);
    try {
      setBlockedUsers(await api.unblockUser(token, userId));
      setNotice("User unblocked.");
    } catch (unblockError) {
      setError(unblockError instanceof Error ? unblockError.message : "Unable to unblock user");
    } finally {
      setActionUserKey(null);
    }
  }, [token]);

  const handleReportUser = useCallback(async (userId: string) => {
    setActionUserKey(`report:${userId}`);
    setError(null);
    setNotice(null);
    try {
      await api.reportUser(token, {
        reportedUserId: userId,
        category: "ABUSE"
      });
      setNotice("Report submitted.");
    } catch (reportError) {
      setError(reportError instanceof Error ? reportError.message : "Unable to report user");
    } finally {
      setActionUserKey(null);
    }
  }, [token]);

  const handleLoadDeviceContacts = useCallback(async () => {
    setSyncingDeviceContacts(true);
    setError(null);
    setNotice(null);
    try {
      const nextDeviceContacts = await deviceContacts.requestAndList(1000);
      setDeviceContactsList(nextDeviceContacts);

      const deduplicatedPhonebook = nextDeviceContacts.reduce<ImportedPhoneContact[]>(
        (result, contact) => {
          for (const phoneNumber of contact.phoneNumbers) {
            if (result.some((entry) => entry.phoneNumber === phoneNumber)) {
              continue;
            }
            result.push({
              phoneNumber,
              contactName: contact.displayName || undefined
            });
          }
          return result;
        },
        []
      );

      if (deduplicatedPhonebook.length === 0) {
        setImportSummary({
          importedCount: 0,
          matchedCount: 0,
          persistedMatches: true,
          unmatchedPhoneNumbers: [],
          matchedUsers: []
        });
        setNotice("No device contacts with phone numbers were found.");
        return;
      }

      let importedCount = 0;
      let matchedCount = 0;
      let persistedMatches = true;
      const unmatchedPhoneNumbers = new Set<string>();
      const matchedUsersById = new Map<Contact["userId"], Contact>();

      for (let index = 0; index < deduplicatedPhonebook.length; index += 1000) {
        const batch = deduplicatedPhonebook.slice(index, index + 1000);
        const result = await api.importContacts(token, {
          contacts: batch,
          persistMatches: true
        });
        importedCount += result.importedCount;
        matchedCount += result.matchedCount;
        persistedMatches = persistedMatches && result.persistedMatches;
        for (const phoneNumber of result.unmatchedPhoneNumbers) {
          unmatchedPhoneNumbers.add(phoneNumber);
        }
        for (const user of result.matchedUsers) {
          matchedUsersById.set(user.userId, user);
        }
      }

      const nextContacts = await api.getContacts(token);
      setContacts(nextContacts);
      void refreshContactsPresence(nextContacts).catch(() => undefined);
      setImportSummary({
        importedCount,
        matchedCount,
        persistedMatches,
        unmatchedPhoneNumbers: [...unmatchedPhoneNumbers],
        matchedUsers: [...matchedUsersById.values()]
      });
      setNotice(
        matchedCount > 0
          ? `Imported ${importedCount} phone numbers and matched ${matchedCount} users.`
          : `Imported ${importedCount} phone numbers but found no registered users yet.`
      );
    } catch (syncError) {
      setError(syncError instanceof Error ? syncError.message : "Unable to read device contacts");
    } finally {
      setSyncingDeviceContacts(false);
    }
  }, []);

  return {
    actionUserKey,
    blockedUsers,
    bots,
    contacts,
    deviceContactsList,
    error,
    handleAddContact,
    handleBlockUser,
    handleLoadDeviceContacts,
    handleOpenDirect,
    handleRemoveContact,
    handleReportUser,
    handleUnblockUser,
    importSummary,
    isBlocked,
    loading,
    notice,
    query,
    results,
    searching,
    setQuery,
    syncingDeviceContacts
  };
}

export type ContactsScreenController = ReturnType<typeof useContactsController>;
