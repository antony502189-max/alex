import { useMemo, useState } from "react";
import { useAppStore } from "../../store/useAppStore";

export function useProfileLocalAccounts() {
  const activeAccountId = useAppStore((state) => state.activeAccountId);
  const accountsById = useAppStore((state) => state.accountsById);
  const switchAccount = useAppStore((state) => state.switchAccount);
  const removeAccount = useAppStore((state) => state.removeAccount);
  const logout = useAppStore((state) => state.logout);

  const [switchingAccountId, setSwitchingAccountId] = useState<string | null>(null);
  const [removingAccountId, setRemovingAccountId] = useState<string | null>(null);

  const localAccounts = useMemo(
    () =>
      Object.values(accountsById).sort((left, right) =>
        right.lastActivatedAt.localeCompare(left.lastActivatedAt)
      ),
    [accountsById]
  );

  function handleSwitchAccount(
    accountId: string,
    onNotice: (value: string | null) => void,
    onError: (value: string | null) => void
  ) {
    if (accountId === activeAccountId) {
      return;
    }

    setSwitchingAccountId(accountId);
    onError(null);
    onNotice(null);
    try {
      switchAccount(accountId);
      const nextAccount = accountsById[accountId];
      onNotice(
        nextAccount
          ? `Switched to ${nextAccount.session.displayName}.`
          : "Switched account."
      );
    } finally {
      setSwitchingAccountId(null);
    }
  }

  function handleRemoveLocalAccount(
    accountId: string,
    onNotice: (value: string | null) => void,
    onError: (value: string | null) => void
  ) {
    if (!accountId) {
      return;
    }

    setRemovingAccountId(accountId);
    onError(null);
    onNotice(null);
    try {
      if (accountId === activeAccountId) {
        logout();
      } else {
        removeAccount(accountId);
      }
      onNotice("Local account removed from this device.");
    } finally {
      setRemovingAccountId(null);
    }
  }

  return {
    activeAccountId,
    localAccounts,
    removingAccountId,
    switchingAccountId,
    handleRemoveLocalAccount,
    handleSwitchAccount
  };
}
