import { create } from "zustand";
import type { SharedMediaBuckets } from "../types";

type State = {
  bucketsByChatId: Record<string, SharedMediaBuckets>;
  setBuckets: (chatId: string, buckets: SharedMediaBuckets) => void;
  clearBuckets: (chatId: string) => void;
  clearAll: () => void;
};

export const useMediaStore = create<State>((set) => ({
  bucketsByChatId: {},
  setBuckets: (chatId, buckets) =>
    set((state) => ({
      bucketsByChatId: {
        ...state.bucketsByChatId,
        [chatId]: buckets
      }
    })),
  clearBuckets: (chatId) =>
    set((state) => {
      const nextBuckets = { ...state.bucketsByChatId };
      delete nextBuckets[chatId];
      return {
        bucketsByChatId: nextBuckets
      };
    }),
  clearAll: () =>
    set({
      bucketsByChatId: {}
    })
}));
