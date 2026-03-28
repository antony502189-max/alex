import { api } from "../services/api";
import { callMediaSession } from "../services/callMediaSession";
import { normalizeCallLinkToken } from "../services/linkTokens";
import { isLiveCall, pickPreferredCall } from "./rootCallUtils";
import type { Dispatch, SetStateAction } from "react";
import type {
  AuthSession,
  CallJoinLink,
  CallMediaState,
  CallSession,
  CallSignalEvent
} from "../types";

type UseRootCallActionsInput = {
  callMediaState: CallMediaState;
  currentCallRef: { current: CallSession | null };
  session: AuthSession | null;
  setCurrentCall: Dispatch<SetStateAction<CallSession | null>>;
  setCurrentCallLinks: Dispatch<SetStateAction<CallJoinLink[]>>;
  setRecentCallSignals: Dispatch<SetStateAction<CallSignalEvent[]>>;
};

export function useRootCallActions({
  callMediaState,
  currentCallRef,
  session,
  setCurrentCall,
  setCurrentCallLinks,
  setRecentCallSignals
}: UseRootCallActionsInput) {
  async function refreshActiveCalls(sessionToken: string, currentUserId: string) {
    const activeCalls = await api.getActiveCalls(sessionToken);
    const nextCall = pickPreferredCall(activeCalls, currentUserId);
    if (currentCallRef.current?.callId !== nextCall?.callId) {
      setRecentCallSignals([]);
    }
    setCurrentCall(nextCall);
    return nextCall;
  }

  async function refreshCurrentCallLinks(call: CallSession | null, sessionToken: string) {
    if (!call || call.mode !== "GROUP" || !call.viewerCanManageLinks) {
      setCurrentCallLinks([]);
      return [];
    }

    const links = await api.getCallLinks(sessionToken, call.chatId);
    setCurrentCallLinks(links);
    return links;
  }

  async function startChatCall(chatId: string, kind: "VOICE" | "VIDEO") {
    if (!session) {
      return;
    }

    try {
      const call = await api.startCall(session.token, { chatId, kind });
      if (currentCallRef.current?.callId !== call.callId) {
        setRecentCallSignals([]);
      }
      setCurrentCall(call);
      void refreshCurrentCallLinks(call, session.token).catch(() => undefined);
    } catch {
      void refreshActiveCalls(session.token, session.userId).catch(() => undefined);
    }
  }

  async function joinCallByLink(rawToken: string) {
    if (!session) {
      return;
    }

    const normalizedToken = normalizeCallLinkToken(rawToken);
    if (!normalizedToken) {
      return;
    }

    const call = await api.joinCallLink(session.token, normalizedToken);
    if (currentCallRef.current?.callId !== call.callId) {
      setRecentCallSignals([]);
    }
    setCurrentCall(call);
    void refreshCurrentCallLinks(call, session.token).catch(() => undefined);
  }

  async function acceptCurrentCall() {
    if (!session || !currentCallRef.current) {
      return;
    }

    const call = await api.acceptCall(session.token, currentCallRef.current.callId);
    setCurrentCall(call);
  }

  async function declineCurrentCall() {
    if (!session || !currentCallRef.current) {
      return;
    }

    const call = await api.declineCall(session.token, currentCallRef.current.callId);
    if (isLiveCall(call, session.userId)) {
      setCurrentCall(call);
      return;
    }

    setCurrentCall(null);
    setRecentCallSignals([]);
    void refreshActiveCalls(session.token, session.userId).catch(() => undefined);
  }

  async function leaveCurrentCall() {
    if (!session || !currentCallRef.current) {
      return;
    }

    const call = await api.leaveCall(session.token, currentCallRef.current.callId);
    if (isLiveCall(call, session.userId)) {
      setCurrentCall(call);
      return;
    }

    setCurrentCall(null);
    setCurrentCallLinks([]);
    setRecentCallSignals([]);
    void refreshActiveCalls(session.token, session.userId).catch(() => undefined);
  }

  async function createCurrentCallLink(kind: "VOICE" | "VIDEO") {
    if (!session || !currentCallRef.current) {
      return;
    }

    const created = await api.createCallLink(session.token, {
      chatId: currentCallRef.current.chatId,
      kind
    });
    setCurrentCallLinks((current) => [created, ...current.filter((item) => item.linkId !== created.linkId)]);
  }

  async function moderateCurrentCallParticipant(
    userId: string,
    payload: {
      audioPublishingAllowed?: boolean;
      videoPublishingAllowed?: boolean;
      screenShareAllowed?: boolean;
      removeParticipant?: boolean;
    }
  ) {
    if (!session || !currentCallRef.current) {
      return;
    }

    const call = await api.moderateCallParticipant(
      session.token,
      currentCallRef.current.callId,
      userId,
      payload
    );
    setCurrentCall(call);
  }

  async function sendCallSignalToUser(
    toUserId: string,
    signalType: string,
    payload: Record<string, unknown>
  ) {
    if (!session || !currentCallRef.current) {
      return;
    }

    await api.sendCallSignal(session.token, currentCallRef.current.callId, {
      toUserId,
      signalType,
      payload: JSON.stringify(payload)
    });
  }

  async function broadcastCallSignal(
    signalType: string,
    payload: Record<string, unknown>
  ) {
    if (!session || !currentCallRef.current) {
      return;
    }

    const call = currentCallRef.current;
    const recipients = call.participants.filter(
      (participant) =>
        participant.userId !== session.userId &&
        !["LEFT", "DECLINED", "MISSED"].includes(participant.state)
    );
    if (recipients.length === 0) {
      return;
    }

    const serializedPayload = JSON.stringify(payload);
    await Promise.allSettled(
      recipients.map((participant) =>
        api.sendCallSignal(session.token, call.callId, {
          toUserId: participant.userId,
          signalType,
          payload: serializedPayload
        })
      )
    );
  }

  async function toggleCurrentScreenShare() {
    if (!session || !currentCallRef.current) {
      return;
    }

    const nextSharing = !callMediaState.localScreenSharing;
    if (nextSharing) {
      await callMediaSession.startScreenShare();
    } else {
      await callMediaSession.stopScreenShare();
    }

    try {
      const call = nextSharing
        ? await api.startScreenShare(session.token, currentCallRef.current.callId)
        : await api.stopScreenShare(session.token, currentCallRef.current.callId);
      setCurrentCall(call);
      await broadcastCallSignal(nextSharing ? "SCREEN_SHARE_ON" : "SCREEN_SHARE_OFF", {
        screenSharing: nextSharing
      });
    } catch (error) {
      if (nextSharing) {
        await callMediaSession.stopScreenShare().catch(() => undefined);
      }
      throw error;
    }
  }

  return {
    acceptCurrentCall,
    broadcastCallSignal,
    createCurrentCallLink,
    declineCurrentCall,
    joinCallByLink,
    leaveCurrentCall,
    moderateCurrentCallParticipant,
    refreshActiveCalls,
    refreshCurrentCallLinks,
    sendCallSignalToUser,
    startChatCall,
    toggleCurrentScreenShare
  };
}
