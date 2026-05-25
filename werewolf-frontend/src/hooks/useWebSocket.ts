import { useEffect, useRef, useCallback } from 'react';
import { WsClient } from '../services/ws';
import { WsEnvelope, MessageType, PhaseSyncPayload, ActionAckPayload, GameOverPayload, ChatMessagePayload } from '../types/game';


interface UseWebSocketOptions {
  userId: number;
  onConnected?: () => void;
  onPhaseSync?: (payload: PhaseSyncPayload) => void;
  onActionAck?: (payload: ActionAckPayload) => void;
  onGameOver?: (payload: GameOverPayload) => void;
  onChatMessage?: (payload: ChatMessagePayload) => void;
  onError?: (message: string) => void;
}

export function useWebSocket(options: UseWebSocketOptions) {
  const clientRef = useRef<WsClient | null>(null);
  const optionsRef = useRef(options);
  optionsRef.current = options;

  const connect = useCallback(() => {
    if (clientRef.current) {
      clientRef.current.disconnect();
    }

    const wsUrl = window.location.origin;
    const client = new WsClient(wsUrl, String(options.userId));
    clientRef.current = client;

    client.onMessage((envelope: WsEnvelope) => {
      const { type, payload } = envelope;
      const opts = optionsRef.current;

      switch (type) {
        case MessageType.CONNECTED:
          opts.onConnected?.();
          break;

        case MessageType.PHASE_SYNC: {
          // Backend sends: { type: "PHASE_SYNC", payload: { seatId: N, phaseSync: PhaseSyncPayload } }
          const phaseSync = (payload as any)?.phaseSync as PhaseSyncPayload | undefined;
          if (phaseSync) {
            // GAME_OVER is detected in useGameState.ts PHASE_SYNC reducer
            // Backend doesn't send GAME_OVER message separately
            opts.onPhaseSync?.(phaseSync);
          }
          break;
        }


        case MessageType.ACTION_ACK: {
          // Backend sends: { type: "ACTION_ACK", payload: { ack: ActionAckPayload, phaseSyncs: [...], actorSeatId: N, actorPhaseSync: {...} } }
          const ackPayload = (payload as any)?.ack as ActionAckPayload | undefined;
          if (ackPayload) {
            opts.onActionAck?.(ackPayload);
          }
          break;
        }

        case MessageType.GAME_OVER:
          // Backend doesn't currently send GAME_OVER, but handle it just in case
          opts.onGameOver?.(payload as unknown as GameOverPayload);
          break;

        case MessageType.CHAT_BROADCAST:
          // Backend broadcasts CHAT_BROADCAST for SPEAK / WOLF_CHAT actions (both AI and manual)
          opts.onChatMessage?.(payload as unknown as ChatMessagePayload);
          break;


        case MessageType.ERROR:
          opts.onError?.(String((payload as any)?.message || 'Unknown error'));
          break;
      }
    });

    client.connect();
  }, [options.userId]);


  const disconnect = useCallback(() => {
    clientRef.current?.disconnect();
    clientRef.current = null;
  }, []);

  const send = useCallback((type: string, payload: Record<string, unknown>) => {
    clientRef.current?.send(type, payload);
  }, []);

  const joinRoom = useCallback((roomId: string, seatId: number) => {
    send(MessageType.JOIN_ROOM, { roomId, seatId, userId: optionsRef.current.userId });
  }, [send]);

  const setReady = useCallback((roomId: string, seatId: number, ready: boolean) => {
    send(MessageType.READY, { roomId, seatId, ready });
  }, [send]);

  const sendGameAction = useCallback(
    (roomId: string, playerId: number, action: string, phase: string, target?: number, content?: string) => {
      send(MessageType.GAME_ACTION, {
        roomId,
        playerId,
        action,
        phase,
        target,
        content,
      });
    },
    [send]
  );

  useEffect(() => {
    return () => {
      clientRef.current?.disconnect();
    };
  }, []);

  return { connect, disconnect, send, joinRoom, setReady, sendGameAction, client: clientRef };
}
