import type { QrLoginChallenge, UserSession } from "../../types";

export function getPendingQrApprovalCount(challenges: QrLoginChallenge[]) {
  return challenges.filter((challenge) => challenge.status === "PENDING_APPROVAL").length;
}

export function isCurrentSession(session: UserSession, currentSessionId: string) {
  return session.current || session.sessionId === currentSessionId;
}

export function formatSessionPlatform(session: UserSession) {
  return [session.platform, session.appVersion].filter(Boolean).join(" | ") || "Unknown platform";
}

export function formatSessionTrust(session: UserSession) {
  if (!session.trustedSession) {
    return "standard";
  }

  return session.trustedAt
    ? `trusted since ${new Date(session.trustedAt).toLocaleString()}`
    : "trusted";
}

export function buildSessionMetaLines(session: UserSession) {
  return [
    formatSessionPlatform(session),
    `Active: ${new Date(session.lastActiveAt).toLocaleString()}`,
    `Auth: ${session.authMethod ?? "UNKNOWN"}`,
    `Trust: ${formatSessionTrust(session)}`,
    `Push notifications: ${session.notificationsEnabled ? "enabled" : "disabled"}`,
    `Started: ${new Date(session.createdAt).toLocaleString()}`,
    session.userAgent,
    session.ipAddress ? `IP ${session.ipAddress}` : null
  ].filter((line): line is string => Boolean(line));
}

export function buildQrChallengeMetaLines(challenge: QrLoginChallenge) {
  return [
    `Status: ${challenge.status}`,
    `Device: ${challenge.deviceName ?? "Unknown device"}`,
    [challenge.platform, challenge.appVersion].filter(Boolean).join(" | ") || "Unknown platform",
    `Created: ${new Date(challenge.createdAt).toLocaleString()}`,
    challenge.boundAt ? `Bound: ${new Date(challenge.boundAt).toLocaleString()}` : null,
    challenge.approvedAt ? `Approved: ${new Date(challenge.approvedAt).toLocaleString()}` : null,
    challenge.ipAddress ? `IP ${challenge.ipAddress}` : null,
    challenge.userAgent,
    `Expires: ${new Date(challenge.expiresAt).toLocaleString()}`
  ].filter((line): line is string => Boolean(line));
}
