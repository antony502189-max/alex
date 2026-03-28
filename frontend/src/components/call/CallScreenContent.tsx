import React from "react";
import { CallActivitySection } from "./CallActivitySection";
import { CallControlsPanel } from "./CallControlsPanel";
import { CallFooterActions } from "./CallFooterActions";
import { CallHeroCard } from "./CallHeroCard";
import { CallLinksSection } from "./CallLinksSection";
import { CallMediaStage } from "./CallMediaStage";
import { CallParticipantsSection } from "./CallParticipantsSection";
import { CallTransportPanel } from "./CallTransportPanel";
import type { CallScreenController } from "./useCallScreenController";
import { ScreenScrollView } from "../ui/ScreenScrollView";
import type {
  CallAdaptationProfile,
  CallJoinLink,
  CallMediaState,
  CallSession
} from "../../types";

type CallScreenContentProps = {
  call: CallSession;
  callLinks: CallJoinLink[];
  callJoinLinksEnabled: boolean;
  callModerationEnabled: boolean;
  callScreenSharingEnabled: boolean;
  chatPhotoUrl?: string | null;
  chatTitle: string;
  controller: CallScreenController;
  currentUserId: string;
  mediaState: CallMediaState;
  onAccept: () => void;
  onCreateCallLink: (kind: "VOICE" | "VIDEO") => void;
  onDecline: () => void;
  onLeave: () => void;
  onModerateParticipant: (
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      removeParticipant?: boolean;
      screenShareAllowed?: boolean;
      videoPublishingAllowed?: boolean;
    }
  ) => void;
  onSetAdaptationProfile: (profile: CallAdaptationProfile) => void;
  onToggleMute: () => void;
  onToggleScreenShare: () => void;
  onToggleSpeaker: () => void;
  onToggleVideo: () => void;
};

export function CallScreenContent({
  call,
  callLinks,
  callJoinLinksEnabled,
  callModerationEnabled,
  callScreenSharingEnabled,
  chatPhotoUrl,
  chatTitle,
  controller,
  currentUserId,
  mediaState,
  onAccept,
  onCreateCallLink,
  onDecline,
  onLeave,
  onModerateParticipant,
  onSetAdaptationProfile,
  onToggleMute,
  onToggleScreenShare,
  onToggleSpeaker,
  onToggleVideo
}: CallScreenContentProps) {
  return (
    <>
      <ScreenScrollView gap="lg" padding="xl" paddingBottom="xl">
        <CallMediaStage
          localStreamUrl={mediaState.localStreamUrl}
          localVisible={mediaState.localVideoEnabled || mediaState.localScreenSharing}
          remoteStreamUrl={call.kind === "VIDEO" ? controller.primaryRemotePeer?.remoteStreamUrl ?? null : null}
        />

        <CallHeroCard
          call={call}
          chatPhotoUrl={chatPhotoUrl}
          chatTitle={chatTitle}
          headlineParticipant={controller.headlineParticipant}
          statusText={controller.statusText}
        />

        <CallControlsPanel
          call={call}
          canToggleCamera={controller.canToggleCamera}
          canToggleMicrophone={controller.canToggleMicrophone}
          canToggleScreenShare={controller.canToggleScreenShare}
          controlIssues={controller.controlIssues}
          localAudioEnabled={mediaState.localAudioEnabled}
          localScreenSharing={mediaState.localScreenSharing}
          localVideoEnabled={mediaState.localVideoEnabled}
          onToggleMute={onToggleMute}
          onToggleScreenShare={onToggleScreenShare}
          onToggleSpeaker={onToggleSpeaker}
          onToggleVideo={onToggleVideo}
          screenShareEnabled={callScreenSharingEnabled}
          screenShareSupported={mediaState.screenShareSupported}
          speakerOn={mediaState.speakerOn}
        />

        <CallTransportPanel
          mediaState={mediaState}
          onSetAdaptationProfile={onSetAdaptationProfile}
        />

        <CallLinksSection
          call={call}
          callJoinLinksEnabled={callJoinLinksEnabled}
          callLinks={callLinks}
          onCreateCallLink={onCreateCallLink}
        />

        <CallParticipantsSection
          call={call}
          callModerationEnabled={callModerationEnabled}
          currentUserId={currentUserId}
          mediaState={mediaState}
          onModerateParticipant={onModerateParticipant}
        />

        <CallActivitySection lines={controller.recentSignalLines} />
      </ScreenScrollView>

      <CallFooterActions
        incomingRinging={controller.incomingRinging}
        leaveLabel={controller.leaveLabel}
        onAccept={onAccept}
        onDecline={onDecline}
        onLeave={onLeave}
      />
    </>
  );
}
