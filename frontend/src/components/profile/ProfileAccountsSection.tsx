import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { AccountState } from "../../types";
import {
  formatAccountIdentity,
  formatAccountTitle
} from "./profilePresentation";

type ProfileAccountsSectionProps = {
  activeAccountId: string | null;
  localAccounts: AccountState[];
  onAddAccount?: () => void;
  onRemoveCurrentAccount: () => void;
  onRemoveLocalAccount: (accountId: string) => void;
  onSwitchAccount: (accountId: string) => void;
  removingAccountId: string | null;
  switchingAccountId: string | null;
};

export function ProfileAccountsSection({
  activeAccountId,
  localAccounts,
  onAddAccount,
  onRemoveCurrentAccount,
  onRemoveLocalAccount,
  onSwitchAccount,
  removingAccountId,
  switchingAccountId
}: ProfileAccountsSectionProps) {
  return (
    <SectionCard
      description="Switch local accounts instantly. Removing one clears cached chats, drafts, and queued messages for that account on this device."
      title="Accounts on this device"
    >
      <View style={styles.inlineStack}>
        <AppButton onPress={onAddAccount} variant="secondary">
          Add account
        </AppButton>
        <AppButton
          disabled={!activeAccountId}
          onPress={onRemoveCurrentAccount}
          variant="danger"
        >
          Log out current
        </AppButton>
      </View>
      <View style={styles.accountList}>
        {localAccounts.map((account) => {
          const accountId = account.session.userId;
          const active = accountId === activeAccountId;
          const busy = switchingAccountId === accountId || removingAccountId === accountId;

          return (
            <View
              key={account.session.sessionId}
              style={[styles.accountCard, active && styles.accountCardActive]}
            >
              <View style={styles.accountCardBody}>
                <Text style={styles.accountTitle}>
                  {formatAccountTitle(account.session.displayName, active)}
                </Text>
                <Text style={styles.accountMeta}>
                  {formatAccountIdentity(account.session.phoneNumber, account.session.username)}
                </Text>
                <Text style={styles.accountMeta}>
                  Last used {new Date(account.lastActivatedAt).toLocaleString()}
                </Text>
              </View>
              <View style={styles.accountActions}>
                {!active ? (
                  <AppButton
                    disabled={busy}
                    onPress={() => onSwitchAccount(accountId)}
                    size="sm"
                    variant="secondary"
                  >
                    {switchingAccountId === accountId ? "Switching..." : "Switch"}
                  </AppButton>
                ) : null}
                <AppButton
                  disabled={busy}
                  onPress={() => onRemoveLocalAccount(accountId)}
                  size="sm"
                  variant="danger"
                >
                  {removingAccountId === accountId
                    ? "Removing..."
                    : active
                      ? "Log out"
                      : "Remove"}
                </AppButton>
              </View>
            </View>
          );
        })}
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  inlineStack: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm + 2,
    marginTop: appSpacing.sm + 2
  },
  accountList: {
    gap: appSpacing.sm + 2,
    marginTop: appSpacing.sm + 2
  },
  accountCard: {
    backgroundColor: "#f8fbff",
    borderColor: "#dbe4f3",
    borderRadius: appRadii.lg,
    borderWidth: 1,
    gap: appSpacing.md,
    padding: appSpacing.md + 2
  },
  accountCardActive: {
    backgroundColor: "#eff6ff",
    borderColor: "#3b82f6"
  },
  accountCardBody: {
    gap: 4
  },
  accountTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  accountMeta: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  accountActions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  }
});
