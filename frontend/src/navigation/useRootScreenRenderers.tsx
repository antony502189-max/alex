import { createRootModalScreenRenderers } from "./createRootModalScreenRenderers";
import { createRootPrimaryScreenRenderers } from "./createRootPrimaryScreenRenderers";
import type {
  RootScreenRenderers,
  RootScreenRenderersInput
} from "./rootScreenRendererTypes";

export function useRootScreenRenderers(
  input: RootScreenRenderersInput
): RootScreenRenderers {
  return {
    ...createRootPrimaryScreenRenderers(input),
    ...createRootModalScreenRenderers(input)
  };
}
