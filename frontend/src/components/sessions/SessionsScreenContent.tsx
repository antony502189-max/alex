import React from "react";
import { SessionsDeviceList } from "./SessionsDeviceList";
import { SessionsQrSection } from "./SessionsQrSection";
import type { SessionsScreenController } from "./useSessionsScreenController";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { ScreenScrollView } from "../ui/ScreenScrollView";

type SessionsScreenContentProps = {
  controller: SessionsScreenController;
  currentSessionId: string;
  onClose: () => void;
};

export function SessionsScreenContent({
  controller,
  currentSessionId,
  onClose
}: SessionsScreenContentProps) {
  return (
    <>
      <AppHeader
        onBack={onClose}
        subtitle={`${controller.sessions.length} active device${controller.sessions.length === 1 ? "" : "s"}`}
        title="Devices"
      />
      <ScreenFeedback error={controller.error} notice={controller.notice} />

      <ScreenScrollView gap="md" paddingBottom="xl">
        <SessionsQrSection
          creatingQr={controller.creatingQr}
          loading={controller.loading}
          onApproveQr={(challengeId) => void controller.handleApproveQr(challengeId)}
          onCreateQr={() => void controller.handleCreateQr()}
          onDeclineQr={(challengeId) => void controller.handleDeclineQr(challengeId)}
          onRefresh={() => void controller.handleRefresh()}
          pendingQrApprovals={controller.pendingQrApprovals}
          processingQrChallengeId={controller.processingQrChallengeId}
          qrChallenge={controller.qrChallenge}
          qrChallenges={controller.qrChallenges}
        />
        <SessionsDeviceList
          currentSessionId={currentSessionId}
          loading={controller.loading}
          onRefresh={() => void controller.handleRefresh()}
          onRevoke={(sessionId) => void controller.handleRevoke(sessionId)}
          onRevokeOthers={() => void controller.handleRevokeOthers()}
          revokingSessionId={controller.revokingSessionId}
          sessions={controller.sessions}
        />
      </ScreenScrollView>
    </>
  );
}
