import type { AuthFlowResult, AuthSession } from "../../types";

export function toAuthSession(result: AuthFlowResult): AuthSession | null {
  if (
    !result.authenticated ||
    !result.token ||
    !result.sessionId ||
    !result.userId ||
    !result.phoneNumber ||
    !result.displayName
  ) {
    return null;
  }

  return {
    token: result.token,
    refreshToken: result.refreshToken,
    sessionId: result.sessionId,
    userId: result.userId,
    phoneNumber: result.phoneNumber,
    displayName: result.displayName,
    username: result.username,
    accessTokenExpiresAt: result.accessTokenExpiresAt,
    refreshTokenExpiresAt: result.refreshTokenExpiresAt,
    authMethod: result.authMethod,
    trustedSession: Boolean(result.trustedSession)
  };
}
