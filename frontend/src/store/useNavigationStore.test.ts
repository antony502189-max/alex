import { useNavigationStore } from "./useNavigationStore";

describe("useNavigationStore", () => {
  beforeEach(() => {
    useNavigationStore.getState().reset();
  });

  it("tracks root tab, modal route, and chat route and can reset them", () => {
    useNavigationStore.getState().setActiveRootTab("SETTINGS");
    useNavigationStore.getState().setModalRoute({
      type: "SESSIONS"
    });
    useNavigationStore.getState().setChatRoute({
      type: "CHAT",
      chatId: "chat-1"
    });

    expect(useNavigationStore.getState()).toMatchObject({
      activeRootTab: "SETTINGS",
      modalRoute: {
        type: "SESSIONS"
      },
      chatRoute: {
        type: "CHAT",
        chatId: "chat-1"
      }
    });

    useNavigationStore.getState().reset();

    expect(useNavigationStore.getState()).toMatchObject({
      activeRootTab: "CHATS",
      modalRoute: null,
      chatRoute: null
    });
  });
});
