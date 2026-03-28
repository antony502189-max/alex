import React from "react";
import { CallScreenContent } from "../components/call/CallScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useCallScreenController } from "../components/call/useCallScreenController";
import type {
  CallAdaptationProfile,
  CallJoinLink,
  CallMediaState,
  CallSession,
  CallSignalEvent
} from "../types";

type CallScreenProps = {
  call: CallSession;
  callLinks: CallJoinLink[];
  callJoinLinksEnabled: boolean;
  callModerationEnabled: boolean;
  callScreenSharingEnabled: boolean;
  chatTitle: string;
  chatPhotoUrl?: string | null;
  currentUserId: string;
  mediaState: CallMediaState;
  recentSignals: CallSignalEvent[];
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

export function CallScreen({
  call,
  callLinks,
  callJoinLinksEnabled,
  callModerationEnabled,
  callScreenSharingEnabled,
  chatTitle,
  chatPhotoUrl,
  currentUserId,
  mediaState,
  recentSignals,
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
}: CallScreenProps) {
  const controller = useCallScreenController({
    call,
    callScreenSharingEnabled,
    currentUserId,
    mediaState,
    recentSignals
  });

  return (
    <AppScreen>
      <CallScreenContent
        call={call}
        callLinks={callLinks}
        callJoinLinksEnabled={callJoinLinksEnabled}
        callModerationEnabled={callModerationEnabled}
        callScreenSharingEnabled={callScreenSharingEnabled}
        chatPhotoUrl={chatPhotoUrl}
        chatTitle={chatTitle}
        controller={controller}
        currentUserId={currentUserId}
        mediaState={mediaState}
        onAccept={onAccept}
        onCreateCallLink={onCreateCallLink}
        onDecline={onDecline}
        onLeave={onLeave}
        onModerateParticipant={onModerateParticipant}
        onSetAdaptationProfile={onSetAdaptationProfile}
        onToggleMute={onToggleMute}
        onToggleScreenShare={onToggleScreenShare}
        onToggleSpeaker={onToggleSpeaker}
        onToggleVideo={onToggleVideo}
      />
    </AppScreen>
  );
}
