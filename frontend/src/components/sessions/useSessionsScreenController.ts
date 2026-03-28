import { useEffect, useMemo, useState } from "react";
import { api } from "../../services/api";
import type {
  GeneratedQrLogin,
  QrLoginChallenge,
  UserSession
} from "../../types";
import { getPendingQrApprovalCount } from "./sessionsPresentation";

type UseSessionsScreenControllerParams = {
  token: string;
};

type LoadSessionsOptions = {
  silent?: boolean;
};

export function useSessionsScreenController({
  token
}: UseSessionsScreenControllerParams) {
  const [sessions, setSessions] = useState<UserSession[]>([]);
  const [qrChallenge, setQrChallenge] = useState<GeneratedQrLogin | null>(null);
  const [qrChallenges, setQrChallenges] = useState<QrLoginChallenge[]>([]);
  const [loading, setLoading] = useState(false);
  const [revokingSessionId, setRevokingSessionId] = useState<string | null>(null);
  const [creatingQr, setCreatingQr] = useState(false);
  const [processingQrChallengeId, setProcessingQrChallengeId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadSessions(options?: LoadSessionsOptions) {
    if (!options?.silent) {
      setLoading(true);
      setError(null);
    }

    try {
      const [nextSessions, nextQrChallenges] = await Promise.all([
        api.getSessions(token),
        api.getQrLoginChallenges(token)
      ]);
      setSessions(nextSessions);
      setQrChallenges(nextQrChallenges);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load sessions");
    } finally {
      if (!options?.silent) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    void loadSessions();
  }, [token]);

  useEffect(() => {
    if (!qrChallenge && !qrChallenges.some((challenge) => challenge.status === "PENDING_APPROVAL")) {
      return;
    }

    const timer = setInterval(() => {
      void loadSessions({ silent: true });
    }, 4000);

    return () => {
      clearInterval(timer);
    };
  }, [qrChallenge, qrChallenges, token]);

  async function handleRefresh() {
    await loadSessions();
  }

  async function handleRevoke(sessionId: string) {
    setRevokingSessionId(sessionId);
    setError(null);
    setNotice(null);
    try {
      await api.revokeSession(token, sessionId);
      await loadSessions();
    } catch (revokeError) {
      setError(revokeError instanceof Error ? revokeError.message : "Unable to revoke session");
    } finally {
      setRevokingSessionId(null);
    }
  }

  async function handleRevokeOthers() {
    setRevokingSessionId("others");
    setError(null);
    setNotice(null);
    try {
      await api.revokeOtherSessions(token);
      await loadSessions();
    } catch (revokeError) {
      setError(
        revokeError instanceof Error ? revokeError.message : "Unable to revoke other sessions"
      );
    } finally {
      setRevokingSessionId(null);
    }
  }

  async function handleCreateQr() {
    setCreatingQr(true);
    setError(null);
    setNotice(null);
    try {
      const nextChallenge = await api.generateQrLogin(token);
      setQrChallenge(nextChallenge);
      setNotice(
        "QR login token generated. Open the QR tab on the new device and paste this token there."
      );
      await loadSessions();
    } catch (createError) {
      setError(
        createError instanceof Error ? createError.message : "Unable to generate QR login token"
      );
    } finally {
      setCreatingQr(false);
    }
  }

  async function handleApproveQr(challengeId: string) {
    setProcessingQrChallengeId(challengeId);
    setError(null);
    setNotice(null);
    try {
      await api.approveQrLogin(token, challengeId);
      setNotice("QR login approved. The new device can finish sign-in now.");
      await loadSessions();
    } catch (approveError) {
      setError(approveError instanceof Error ? approveError.message : "Unable to approve QR login");
    } finally {
      setProcessingQrChallengeId(null);
    }
  }

  async function handleDeclineQr(challengeId: string) {
    setProcessingQrChallengeId(challengeId);
    setError(null);
    setNotice(null);
    try {
      await api.declineQrLogin(token, challengeId);
      setNotice("QR login request declined.");
      await loadSessions();
    } catch (declineError) {
      setError(declineError instanceof Error ? declineError.message : "Unable to decline QR login");
    } finally {
      setProcessingQrChallengeId(null);
    }
  }

  const pendingQrApprovals = useMemo(
    () => getPendingQrApprovalCount(qrChallenges),
    [qrChallenges]
  );

  return {
    creatingQr,
    error,
    handleApproveQr,
    handleCreateQr,
    handleDeclineQr,
    handleRefresh,
    handleRevoke,
    handleRevokeOthers,
    loading,
    notice,
    pendingQrApprovals,
    processingQrChallengeId,
    qrChallenge,
    qrChallenges,
    revokingSessionId,
    sessions
  };
}

export type SessionsScreenController = ReturnType<typeof useSessionsScreenController>;
