import { WsEnvelope } from '../types/game';

export type WsMessageHandler = (envelope: WsEnvelope) => void;

export class WsClient {
  private ws: WebSocket | null = null;
  private url: string;
  private handlers: Set<WsMessageHandler> = new Set();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private shouldReconnect = true;
  private messageQueue: string[] = [];

  constructor(baseUrl: string, token: string) {
    // Convert http:// to ws:// and use the backend WebSocket endpoint
    const wsBase = baseUrl.replace(/^http/, 'ws');
    this.url = `${wsBase}/ws/game?token=${token}`;
  }

  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) return;

    this.ws = new WebSocket(this.url);

    this.ws.onopen = () => {
      console.log('[WS] Connected');
      // Flush queued messages
      for (const msg of this.messageQueue) {
        this.ws?.send(msg);
      }
      this.messageQueue = [];
    };

    this.ws.onmessage = (event) => {
      try {
        const envelope: WsEnvelope = JSON.parse(event.data);
        this.handlers.forEach((h) => h(envelope));
      } catch (e) {
        console.error('[WS] Failed to parse message:', e);
      }
    };

    this.ws.onclose = () => {
      console.log('[WS] Disconnected');
      if (this.shouldReconnect) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (err) => {
      console.error('[WS] Error:', err);
    };
  }

  disconnect(): void {
    this.shouldReconnect = false;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
  }

  send(type: string, payload: Record<string, unknown>): void {
    const msg = JSON.stringify({
      type,
      payload,
      timestamp: Date.now(),
    });

    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(msg);
    } else {
      this.messageQueue.push(msg);
    }
  }

  onMessage(handler: WsMessageHandler): () => void {
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  get connected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      console.log('[WS] Reconnecting...');
      this.connect();
    }, 3000);
  }
}
