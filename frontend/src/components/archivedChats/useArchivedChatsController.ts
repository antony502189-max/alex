import { useEffect, useState } from "react";
import { api } from "../../services/api";
import type { ChatSummary } from "../../types";

type UseArchivedChatsControllerParams = {
  token: string;
};

export function useArchivedChatsController({
  token
}: UseArchivedChatsControllerParams) {
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadArchivedChats() {
    setLoading(true);
    setError(null);
    try {
      setChats(await api.getArchivedChats(token));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load archived chats");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadArchivedChats();
  }, [token]);

  return {
    chats,
    error,
    loadArchivedChats,
    loading
  };
}

export type ArchivedChatsScreenController = ReturnType<typeof useArchivedChatsController>;
