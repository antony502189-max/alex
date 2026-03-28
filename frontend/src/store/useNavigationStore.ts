import { create } from "zustand";
import type { ActiveRouteState, AppModalRoute, ChatRoute, RootTab } from "../navigation/types";

type NavigationState = ActiveRouteState & {
  setActiveRootTab: (tab: RootTab) => void;
  setModalRoute: (route: AppModalRoute | null) => void;
  setChatRoute: (route: ChatRoute | null) => void;
  reset: () => void;
};

export const useNavigationStore = create<NavigationState>((set) => ({
  activeRootTab: "CHATS",
  modalRoute: null,
  chatRoute: null,
  setActiveRootTab: (tab) => set({ activeRootTab: tab }),
  setModalRoute: (route) => set({ modalRoute: route }),
  setChatRoute: (route) => set({ chatRoute: route }),
  reset: () =>
    set({
      activeRootTab: "CHATS",
      modalRoute: null,
      chatRoute: null
    })
}));
