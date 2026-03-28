import { createRootModalScreenRenderers } from "./createRootModalScreenRenderers";
import { createRootPrimaryScreenRenderers } from "./createRootPrimaryScreenRenderers";
import { useRootScreenRenderers } from "./useRootScreenRenderers";
import type { RootScreenRenderersInput } from "./rootScreenRendererTypes";

jest.mock("./createRootPrimaryScreenRenderers", () => ({
  createRootPrimaryScreenRenderers: jest.fn()
}));

jest.mock("./createRootModalScreenRenderers", () => ({
  createRootModalScreenRenderers: jest.fn()
}));

describe("useRootScreenRenderers", () => {
  it("composes primary and modal renderer sets with the same input", () => {
    const primarySet = {
      renderAuthScreen: jest.fn(),
      renderMainTabsScreen: jest.fn(),
      renderChatScreen: jest.fn(),
      renderForumTopicsScreen: jest.fn(),
      renderMembersScreen: jest.fn(),
      renderCallScreen: jest.fn()
    };
    const modalSet = {
      renderCreateChatScreen: jest.fn(),
      renderAddAccountScreen: jest.fn(),
      renderBotDeveloperScreen: jest.fn(),
      renderSessionsScreen: jest.fn(),
      renderGlobalSearchScreen: jest.fn(),
      renderCreateStoryScreen: jest.fn(),
      renderJoinByLinkScreen: jest.fn(),
      renderMediaViewerScreen: jest.fn(),
      renderSharedMediaScreen: jest.fn(),
      renderChatInfoScreen: jest.fn(),
      renderArchivedScreen: jest.fn(),
      renderFoldersScreen: jest.fn(),
      renderBotMiniAppScreen: jest.fn()
    };
    const input = {} as RootScreenRenderersInput;

    (createRootPrimaryScreenRenderers as jest.Mock).mockReturnValue(primarySet);
    (createRootModalScreenRenderers as jest.Mock).mockReturnValue(modalSet);

    expect(useRootScreenRenderers(input)).toEqual({
      ...primarySet,
      ...modalSet
    });
    expect(createRootPrimaryScreenRenderers).toHaveBeenCalledWith(input);
    expect(createRootModalScreenRenderers).toHaveBeenCalledWith(input);
  });
});
