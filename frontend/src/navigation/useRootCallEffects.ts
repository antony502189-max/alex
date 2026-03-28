import { useEffect, useEffectEvent } from "react";
import { callMediaSession } from "../services/callMediaSession";
import { isLiveCall } from "./rootCallUtils";
import type { Dispatch, SetStateAction } from "react";
import type {
  AuthSession,
  CallJoinLink,
  CallMediaState,
  CallSession
} from "../types";

type UseRootCallEffectsInput = {
  currentCall: CallSession | null;
  currentCallRef: { current: CallSession | null };
  refreshCurrentCallLinks: (
    call: CallSession | null,
    sessionToken: string
  ) => Promise<unknown>;
  sendCallSignalToUser: (
    toUserId: string,
    signalType: string,
    payload: Record<string, unknown>
  ) => Promise<void>;
  session: AuthSession | null;
  setCallMediaState: Dispatch<SetStateAction<CallMediaState>>;
  setCurrentCallLinks: Dispatch<SetStateAction<CallJoinLink[]>>;
};

export function useRootCallEffects({
  currentCall,
  currentCallRef,
  refreshCurrentCallLinks,
  sendCallSignalToUser,
  session,
  setCallMediaState,
  setCurrentCallLinks
}: UseRootCallEffectsInput) {
  const handleSendCallSignalToUser = useEffectEvent(sendCallSignalToUser);
  const handleRefreshCurrentCallLinks = useEffectEvent(refreshCurrentCallLinks);

  useEffect(() => {
    currentCallRef.current = currentCall;
  }, [currentCall, currentCallRef]);

  useEffect(() => callMediaSession.subscribe(setCallMediaState), [setCallMediaState]);

  useEffect(() => {
    if (!session || !currentCall || !isLiveCall(currentCall, session.userId)) {
      void callMediaSession.stop().catch(() => undefined);
      return;
    }

    const shouldStartMedia =
      currentCall.createdByUserId === session.userId || currentCall.status === "ACTIVE";
    if (!shouldStartMedia) {
      void callMediaSession.stop().catch(() => undefined);
      return;
    }

    void callMediaSession.start(
      currentCall,
      session.userId,
      session.token,
      (toUserId, signalType, payload) =>
        handleSendCallSignalToUser(toUserId, signalType, payload)
    ).catch(() => undefined);
  }, [currentCall, session]);

  useEffect(() => {
    if (!session || !currentCall) {
      setCurrentCallLinks([]);
      return;
    }

    void handleRefreshCurrentCallLinks(currentCall, session.token).catch(() => undefined);
  }, [currentCall, session, setCurrentCallLinks]);
}
