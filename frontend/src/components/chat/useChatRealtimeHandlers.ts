import { useCallback } from "react";
import { localDatabase } from "../../services/localDatabase";
import { useAppStore } from "../../store/useAppStore";
import type { ChatMember, ChatReadEvent, PinMessageEvent, TypingEvent } from "../../types";

type UseChatRealtimeHandlersParams = {
  applyPinnedMessageId: (messageId: string | null) => void;
  applyReadEvent: (event: ChatReadEvent) => void;
  currentUserId: string;
  refreshPinnedHistory: () => Promise<unknown>;
  setMembers: React.Dispatch<React.SetStateAction<ChatMember[]>>;
  setTypingUserIds: React.Dispatch<React.SetStateAction<string[]>>;
  typingTimeoutsRef: React.MutableRefObject<Record<string, ReturnType<typeof setTimeout>>>;
};

export function useChatRealtimeHandlers({
  applyPinnedMessageId,
  applyReadEvent,
  currentUserId,
  refreshPinnedHistory,
  setMembers,
  setTypingUserIds,
  typingTimeoutsRef
}: UseChatRealtimeHandlersParams) {
  const clearTypingTimeout = useCallback((userId: string) => {
    const timeoutId = typingTimeoutsRef.current[userId];
    if (!timeoutId) {
      return;
    }
    clearTimeout(timeoutId);
    delete typingTimeoutsRef.current[userId];
  }, [typingTimeoutsRef]);

  const handleTypingEvent = useCallback((event: TypingEvent) => {
    if (event.userId === currentUserId) {
      return;
    }
    clearTypingTimeout(event.userId);
    setTypingUserIds((current) => {
      if (event.typing) {
        return current.includes(event.userId) ? current : [...current, event.userId];
      }
      return current.filter((userId) => userId !== event.userId);
    });

    if (!event.typing) {
      return;
    }

    typingTimeoutsRef.current[event.userId] = setTimeout(() => {
      delete typingTimeoutsRef.current[event.userId];
      setTypingUserIds((current) => current.filter((userId) => userId !== event.userId));
    }, 3000);
  }, [clearTypingTimeout, currentUserId, setTypingUserIds, typingTimeoutsRef]);

  const handleReadEvent = useCallback((event: ChatReadEvent) => {
    applyReadEvent(event);
    setMembers((current) =>
      current.map((member) =>
        member.userId === event.userId ? { ...member, lastReadAt: event.readAt } : member
      )
    );
    void localDatabase
      .upsertMessages(currentUserId, useAppStore.getState().messagesByChat[event.chatId] ?? [])
      .catch(() => undefined);
    if (event.userId === currentUserId) {
      const updatedChat = useAppStore.getState().chats.find((item) => item.chatId === event.chatId);
      if (updatedChat) {
        void localDatabase.upsertChats(currentUserId, [updatedChat]).catch(() => undefined);
      }
    }
  }, [applyReadEvent, currentUserId, setMembers]);

  const handlePinEvent = useCallback((event: PinMessageEvent) => {
    applyPinnedMessageId(event.messageId);
    void refreshPinnedHistory();
  }, [applyPinnedMessageId, refreshPinnedHistory]);

  return {
    handlePinEvent,
    handleReadEvent,
    handleTypingEvent
  };
}
