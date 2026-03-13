import { localDatabase } from "./localDatabase";
import { clearAllSecretAttachmentCache } from "./secretChatAttachments";
import { secretChatCrypto } from "./secretChatCrypto";
import type { SecretChatSummary } from "../types";

function isRetainedSecretChat(secretChat: SecretChatSummary) {
  return secretChat.status === "PENDING" || secretChat.status === "ACTIVE";
}

async function purgeSecretChatState(currentUserId: string, secretChatId: string) {
  await Promise.allSettled([
    localDatabase.removeSecretChat(currentUserId, secretChatId),
    secretChatCrypto.clearPrivateKey(secretChatId),
    clearAllSecretAttachmentCache()
  ]);
}

export const secretChatLocalCleanup = {
  async purgeSecretChat(currentUserId: string, secretChatId: string) {
    await purgeSecretChatState(currentUserId, secretChatId);
  },

  async pruneSecretChats(currentUserId: string, secretChats: SecretChatSummary[]) {
    const retainedSecretChats = secretChats.filter(isRetainedSecretChat);
    const retainedIds = retainedSecretChats.map((secretChat) => secretChat.secretChatId);
    const removedSecretChats = secretChats.filter((secretChat) => !isRetainedSecretChat(secretChat));

    await Promise.allSettled([
      ...removedSecretChats.map((secretChat) =>
        purgeSecretChatState(currentUserId, secretChat.secretChatId)
      ),
      secretChatCrypto.prunePrivateKeys(retainedIds)
    ]);

    return retainedSecretChats;
  },

  async clearAllSecretState(currentUserId: string) {
    await Promise.allSettled([
      localDatabase.clearSecretState(currentUserId),
      secretChatCrypto.clearAllPrivateKeys(),
      clearAllSecretAttachmentCache()
    ]);
  }
};
