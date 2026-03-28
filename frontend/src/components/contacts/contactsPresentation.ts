import { formatPresenceStatus } from "../../services/presence";

type UserMetaInput = {
  bot: boolean;
  botSupportsInline?: boolean;
  lastSeenAt: string | null;
  online: boolean;
  phoneNumber: string | null;
  username: string | null;
};

export function buildUserMetaLines(user: UserMetaInput, blocked: boolean) {
  const presence = user.bot
    ? "bot account"
    : formatPresenceStatus(
        { online: user.online, lastSeenAt: user.lastSeenAt },
        "status hidden"
      );

  return [
    [
      user.username ? `@${user.username}` : "no username",
      user.bot ? "bot" : null,
      user.botSupportsInline ? "inline" : null,
      blocked ? "blocked" : null
    ]
      .filter(Boolean)
      .join(" - "),
    [presence, user.phoneNumber ?? "phone hidden"].filter(Boolean).join(" - ")
  ];
}

export function buildBotMetaLine(username: string, supportsInline: boolean) {
  return [`@${username} - bot${supportsInline ? " - inline" : ""}`];
}
