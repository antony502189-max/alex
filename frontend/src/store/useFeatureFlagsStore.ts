import { create } from "zustand";
import {
  defaultClientFeatureFlags,
  type ClientFeatureFlags
} from "../config/featureFlags";

type FeatureFlagsState = ClientFeatureFlags & {
  hydrate: (flags?: Partial<ClientFeatureFlags> | null) => void;
};

export const useFeatureFlagsStore = create<FeatureFlagsState>((set) => ({
  ...defaultClientFeatureFlags,
  hydrate: (flags) =>
    set((state) => ({
      stories: flags?.stories ?? state.stories,
      bots: flags?.bots ?? state.bots,
      calls: flags?.calls ?? state.calls
    }))
}));
