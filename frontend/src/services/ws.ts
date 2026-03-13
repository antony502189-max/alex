import { Client, type StompSubscription } from "@stomp/stompjs";
import { WS_URL } from "../config/env";
import type { ChatMessage, ChatReadEvent, PinMessageEvent, TypingEvent } from "../types";

type ConnectionListener = (connected: boolean) => void;

class WebSocketService {
  private client?: Client;
  private userQueueSubscription?: StompSubscription;
  private subscriptions = new Map<
    string,
    {
      destination: string;
      handler: (payload: string) => void;
      live?: StompSubscription;
    }
  >();
  private connectionListeners = new Set<ConnectionListener>();
  private subscriptionCounter = 0;
  private connected = false;
  private shouldBeConnected = false;
  private foreground = true;
  private token?: string;
  private messageHandler?: (message: ChatMessage) => void;

  connect(token: string, onMessage: (message: ChatMessage) => void) {
    this.token = token;
    this.messageHandler = onMessage;
    this.shouldBeConnected = true;
    this.ensureClient();
  }

  setForegroundState(foreground: boolean) {
    if (this.foreground === foreground) {
      return;
    }
    this.foreground = foreground;

    if (foreground) {
      this.ensureClient();
      return;
    }

    this.handleDisconnected();
    void this.client?.deactivate();
  }

  subscribe(destination: string, handler: (payload: string) => void) {
    const key = `${destination}#${this.subscriptionCounter++}`;
    const entry = { destination, handler, live: undefined as StompSubscription | undefined };
    this.subscriptions.set(key, entry);

    if (this.connected && this.client) {
      entry.live = this.client.subscribe(destination, (frame) => {
        handler(frame.body);
      });
    }

    return () => {
      entry.live?.unsubscribe();
      this.subscriptions.delete(key);
    };
  }

  subscribeToChat(
    chatId: string,
    handlers: {
      onTyping?: (event: TypingEvent) => void;
      onRead?: (event: ChatReadEvent) => void;
      onPin?: (event: PinMessageEvent) => void;
    }
  ) {
    const unsubscribers: Array<() => void> = [];

    if (handlers.onTyping) {
      unsubscribers.push(
        this.subscribe(`/topic/chats/${chatId}/typing`, (payload) => {
          handlers.onTyping?.(JSON.parse(payload) as TypingEvent);
        })
      );
    }

    if (handlers.onRead) {
      unsubscribers.push(
        this.subscribe(`/topic/chats/${chatId}/reads`, (payload) => {
          handlers.onRead?.(JSON.parse(payload) as ChatReadEvent);
        })
      );
    }

    if (handlers.onPin) {
      unsubscribers.push(
        this.subscribe(`/topic/chats/${chatId}/pins`, (payload) => {
          handlers.onPin?.(JSON.parse(payload) as PinMessageEvent);
        })
      );
    }

    return () => {
      for (const unsubscribe of unsubscribers) {
        unsubscribe();
      }
    };
  }

  onConnectionChange(listener: ConnectionListener) {
    this.connectionListeners.add(listener);
    listener(this.connected);
    return () => {
      this.connectionListeners.delete(listener);
    };
  }

  isConnected() {
    return this.connected;
  }

  disconnect() {
    this.shouldBeConnected = false;
    this.token = undefined;
    this.messageHandler = undefined;
    this.connected = false;
    this.userQueueSubscription?.unsubscribe();
    this.userQueueSubscription = undefined;
    for (const entry of this.subscriptions.values()) {
      entry.live?.unsubscribe();
      entry.live = undefined;
    }
    this.subscriptions.clear();
    this.connectionListeners.forEach((listener) => listener(false));
    void this.client?.deactivate();
    this.client = undefined;
  }

  private ensureClient() {
    if (!this.shouldBeConnected || !this.foreground || !this.token || !this.messageHandler) {
      return;
    }

    if (this.client?.active) {
      return;
    }

    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: {
        Authorization: `Bearer ${this.token}`
      },
      reconnectDelay: 1500,
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 15000,
      connectionTimeout: 10000,
      discardWebsocketOnCommFailure: true,
      debug: () => undefined,
      onConnect: () => {
        this.connected = true;
        this.connectionListeners.forEach((listener) => listener(true));
        this.userQueueSubscription = client.subscribe("/user/queue/messages", (frame) => {
          this.messageHandler?.(JSON.parse(frame.body) as ChatMessage);
        });
        for (const entry of this.subscriptions.values()) {
          entry.live = client.subscribe(entry.destination, (frame) => {
            entry.handler(frame.body);
          });
        }
      },
      onDisconnect: () => {
        this.handleDisconnected();
      },
      onWebSocketClose: () => {
        this.handleDisconnected();
      },
      onStompError: (frame) => {
        console.warn("STOMP error", frame.headers["message"]);
      },
      onWebSocketError: (event) => {
        console.warn("WebSocket error", event);
      }
    });

    this.client = client;
    client.activate();
  }

  private handleDisconnected() {
    if (!this.connected && !this.userQueueSubscription) {
      return;
    }

    this.connected = false;
    this.userQueueSubscription?.unsubscribe();
    this.userQueueSubscription = undefined;
    for (const entry of this.subscriptions.values()) {
      entry.live = undefined;
    }
    this.connectionListeners.forEach((listener) => listener(false));
  }
}

export const wsService = new WebSocketService();
