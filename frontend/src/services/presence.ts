export function formatPresenceStatus(
  presence: { online: boolean; lastSeenAt: string | null | undefined },
  hiddenLabel = "status hidden"
) {
  if (presence.online) {
    return "online";
  }
  if (presence.lastSeenAt) {
    return `last seen ${new Date(presence.lastSeenAt).toLocaleString()}`;
  }
  return hiddenLabel;
}
