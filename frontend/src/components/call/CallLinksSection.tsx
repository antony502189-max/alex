import React from "react";
import { Share, StyleSheet, Text, View } from "react-native";
import type { CallJoinLink, CallSession } from "../../types";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import { AppButton } from "../ui/AppButton";
import { SectionCard } from "../ui/SectionCard";
import {
  buildCallLinkMeta,
  isCallLinkExpired
} from "./callPresentation";

type CallLinksSectionProps = {
  call: CallSession;
  callJoinLinksEnabled: boolean;
  callLinks: CallJoinLink[];
  onCreateCallLink: (kind: "VOICE" | "VIDEO") => void;
};

export function CallLinksSection({
  call,
  callJoinLinksEnabled,
  callLinks,
  onCreateCallLink
}: CallLinksSectionProps) {
  if (call.mode !== "GROUP" || !call.viewerCanManageLinks || !callJoinLinksEnabled) {
    return null;
  }

  function handleShareUrl(url: string) {
    void Share.share({
      message: url,
      url
    }).catch(() => undefined);
  }

  return (
    <SectionCard
      description="Group call links let you reopen the same voice or video room without starting from scratch."
      title="Call links"
    >
      <View style={styles.actionsRow}>
        <AppButton onPress={() => onCreateCallLink(call.kind)}>
          New {call.kind === "VIDEO" ? "video" : "voice"}
        </AppButton>
      </View>

      {callLinks.length === 0 ? (
        <Text style={styles.meta}>No call links yet.</Text>
      ) : (
        callLinks.slice(0, 3).map((link) => {
          const expired = isCallLinkExpired(link);
          const inactive = link.revoked || expired;
          const statusLabel = link.revoked ? "Revoked" : expired ? "Expired" : null;

          return (
            <View key={link.linkId} style={styles.linkRow}>
              <Text style={styles.linkTitle}>{link.label ?? link.shareUrl}</Text>
              <Text style={styles.meta}>{buildCallLinkMeta(link)}</Text>
              {statusLabel ? (
                <Text style={[styles.statusLabel, link.revoked ? styles.revokedLabel : styles.expiredLabel]}>
                  {statusLabel}
                </Text>
              ) : null}
              {!inactive ? (
                <View style={styles.linkActions}>
                  <AppButton
                    onPress={() => handleShareUrl(link.shareUrl)}
                    size="sm"
                    variant="secondary"
                  >
                    Share link
                  </AppButton>
                </View>
              ) : null}
            </View>
          );
        })
      )}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  actionsRow: {
    flexDirection: "row",
    gap: appSpacing.sm
  },
  linkRow: {
    backgroundColor: "#f8fbff",
    borderColor: appColors.border,
    borderRadius: appRadii.md,
    borderWidth: 1,
    gap: appSpacing.xs,
    padding: appSpacing.md
  },
  linkActions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  linkTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  meta: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  statusLabel: {
    fontSize: 12,
    fontWeight: "700"
  },
  revokedLabel: {
    color: appColors.danger,
  },
  expiredLabel: {
    color: "#9a6700"
  }
});
