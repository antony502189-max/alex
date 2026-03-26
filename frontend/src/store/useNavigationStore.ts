import { create } from "zustand";
import type { RootTab } from "../navigation/types";

type NavigationState = {
  activeRootTab: RootTab;
  setActiveRootTab: (tab: RootTab) => void;
};

export const useNavigationStore = create<NavigationState>((set) => ({
  activeRootTab: "CHATS",
  setActiveRootTab: (tab) => set({ activeRootTab: tab })
}));
