export function buildPublicChatShareUrl(publicUsername: string | null | undefined) {
  const normalized = publicUsername?.trim().replace(/^@+/, "") ?? "";
  return normalized ? `https://alex.example/join/${normalized}` : null;
}
