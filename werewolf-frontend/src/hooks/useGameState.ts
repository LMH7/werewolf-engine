import { useReducer, useCallback } from 'react';
import {
  GameState,
  GamePhase,
  RoomStatus,
  PlayerInfo,
  PhaseSyncPayload,
  ActionAckPayload,
  GameLogEntry,
  ChatMessage,
  GameWinner,
  Role,
  GameOverPayload,
  GameEventPayload,
  ChatMessagePayload,
} from '../types/game';

// ===== Initial State =====

const initialState: GameState = {
  roomId: '',
  phase: GamePhase.WAITING,
  round: 0,
  status: RoomStatus.WAITING,
  mySeatId: null,
  myUserId: null,
  phaseSync: null,
  players: createEmptyPlayers(),
  gameLog: [],
  chatMessages: [],
  connected: false,
  isRoomOwner: false,
  winner: null,
  finalRoles: null,
};

function createEmptyPlayers(): PlayerInfo[] {
  return Array.from({ length: 12 }, (_, i) => ({
    seatId: i + 1,
    alive: true,
    ready: false,
    role: null,
    isHuman: false,
    userId: null,
  }));
}

// ===== Actions =====

type GameAction =
  | { type: 'SET_ROOM'; roomId: string; isOwner: boolean }
  | { type: 'SET_MY_SEAT'; seatId: number; userId: number }
  | { type: 'SET_CONNECTED'; connected: boolean }
  | { type: 'UPDATE_PLAYER_READY'; seatId: number; ready: boolean }
  | { type: 'UPDATE_PLAYER_USER'; seatId: number; userId: number }
  | { type: 'PHASE_SYNC'; payload: PhaseSyncPayload }
  | { type: 'ACTION_ACK'; payload: ActionAckPayload }
  | { type: 'GAME_EVENT'; payload: GameEventPayload }
  | { type: 'GAME_OVER'; payload: GameOverPayload }
  | { type: 'CHAT_MESSAGE'; payload: ChatMessagePayload }
  | { type: 'ADD_LOG'; entry: GameLogEntry }
  | { type: 'RESET' };

// ===== Reducer =====

function gameReducer(state: GameState, action: GameAction): GameState {
  switch (action.type) {
    case 'SET_ROOM':
      return { ...state, roomId: action.roomId, isRoomOwner: action.isOwner };

    case 'SET_MY_SEAT':
      return { ...state, mySeatId: action.seatId, myUserId: action.userId };

    case 'SET_CONNECTED':
      return { ...state, connected: action.connected };

    case 'UPDATE_PLAYER_READY': {
      const players = state.players.map((p) =>
        p.seatId === action.seatId ? { ...p, ready: action.ready } : p
      );
      return { ...state, players };
    }

    case 'UPDATE_PLAYER_USER': {
      const players = state.players.map((p) =>
        p.seatId === action.seatId
          ? { ...p, userId: action.userId, isHuman: true }
          : p
      );
      return { ...state, players };
    }

    case 'PHASE_SYNC': {
      const sync = action.payload;
      const players = state.players.map((p) => ({
        ...p,
        alive: sync.alivePlayers.includes(p.seatId),
        role: sync.yourRole && p.seatId === state.mySeatId ? sync.yourRole : p.role,
      }));

      // If we got a role, update teammates
      if (sync.yourRole && state.mySeatId) {
        for (const tid of sync.yourTeammates) {
          const idx = players.findIndex((p) => p.seatId === tid);
          if (idx >= 0) {
            players[idx] = { ...players[idx], role: sync.yourRole };
          }
        }
      }

      // Detect GAME_OVER from phase sync (backend doesn't send GAME_OVER message separately)
      if (sync.currentPhase === GamePhase.GAME_OVER) {
        // Infer winner: if we know our role and alive players, we can guess
        // But the most reliable approach is to just set game over state
        // The winner will be determined by the backend and shown via the game over screen
        const winner = state.winner; // preserve any previously set winner
        return {
          ...state,
          phase: GamePhase.GAME_OVER,
          status: RoomStatus.ENDED,
          round: sync.round,
          phaseSync: sync,
          players,
          winner: winner || null,
          finalRoles: null,
          gameLog: [
            ...state.gameLog,
            {
              id: Date.now(),
              round: sync.round,
              phase: GamePhase.GAME_OVER,
              message: '游戏结束!',
              type: 'system',
            },
          ],
        };
      }

      const newState: GameState = {
        ...state,
        phase: sync.currentPhase,
        round: sync.round,
        status: RoomStatus.PLAYING,
        phaseSync: sync,
        players,
      };

      // Add phase change log
      if (state.phase !== sync.currentPhase) {
        newState.gameLog = [
          ...state.gameLog,
          {
            id: Date.now(),
            round: sync.round,
            phase: sync.currentPhase,
            message: `进入阶段: ${sync.currentPhase}`,
            type: 'system',
          },
        ];
      }

      return newState;
    }


    case 'ACTION_ACK': {
      const ack = action.payload;
      const entry: GameLogEntry = {
        id: Date.now(),
        round: state.round,
        phase: state.phase,
        message: ack.success
          ? `操作成功: ${ack.message || 'OK'}`
          : `操作失败: ${ack.message || ack.code || 'UNKNOWN'}`,
        type: ack.success ? 'action' : 'system',
      };
      return { ...state, gameLog: [...state.gameLog, entry] };
    }

    case 'GAME_EVENT': {
      const evt = action.payload;
      const entry: GameLogEntry = {
        id: Date.now(),
        round: state.round,
        phase: state.phase,
        message: `事件: ${evt.type} ${JSON.stringify(evt.data)}`,
        type: 'event',
      };
      return { ...state, gameLog: [...state.gameLog, entry] };
    }

    case 'GAME_OVER': {
      const go = action.payload;
      const players = state.players.map((p) => ({
        ...p,
        role: go.roles[p.seatId] || p.role,
      }));
      return {
        ...state,
        phase: GamePhase.GAME_OVER,
        status: RoomStatus.ENDED,
        winner: go.winner,
        finalRoles: go.roles,
        players,
        gameLog: [
          ...state.gameLog,
          {
            id: Date.now(),
            round: state.round,
            phase: GamePhase.GAME_OVER,
            message: `游戏结束! ${go.winner === GameWinner.VILLAGERS ? '好人阵营' : '狼人阵营'}获胜!`,
            type: 'system',
          },
        ],
      };
    }

    case 'CHAT_MESSAGE': {
      const chat = action.payload;
      const msg: ChatMessage = {
        seatId: chat.seatId,
        content: chat.content,
        isWolfChat: chat.isWolfChat,
        timestamp: Date.now(),
      };
      return { ...state, chatMessages: [...state.chatMessages, msg] };
    }

    case 'ADD_LOG':
      return { ...state, gameLog: [...state.gameLog, action.entry] };

    case 'RESET':
      return { ...initialState };

    default:
      return state;
  }
}

// ===== Hook =====

export function useGameState() {
  const [state, dispatch] = useReducer(gameReducer, initialState);

  const setRoom = useCallback((roomId: string, isOwner: boolean) => {
    dispatch({ type: 'SET_ROOM', roomId, isOwner });
  }, []);

  const setMySeat = useCallback((seatId: number, userId: number) => {
    dispatch({ type: 'SET_MY_SEAT', seatId, userId });
  }, []);

  const setConnected = useCallback((connected: boolean) => {
    dispatch({ type: 'SET_CONNECTED', connected });
  }, []);

  const updatePlayerReady = useCallback((seatId: number, ready: boolean) => {
    dispatch({ type: 'UPDATE_PLAYER_READY', seatId, ready });
  }, []);

  const updatePlayerUser = useCallback((seatId: number, userId: number) => {
    dispatch({ type: 'UPDATE_PLAYER_USER', seatId, userId });
  }, []);

  const handlePhaseSync = useCallback((payload: PhaseSyncPayload) => {
    dispatch({ type: 'PHASE_SYNC', payload });
  }, []);

  const handleActionAck = useCallback((payload: ActionAckPayload) => {
    dispatch({ type: 'ACTION_ACK', payload });
  }, []);

  const handleGameEvent = useCallback((payload: GameEventPayload) => {
    dispatch({ type: 'GAME_EVENT', payload });
  }, []);

  const handleGameOver = useCallback((payload: GameOverPayload) => {
    dispatch({ type: 'GAME_OVER', payload });
  }, []);

  const handleChatMessage = useCallback((payload: ChatMessagePayload) => {
    dispatch({ type: 'CHAT_MESSAGE', payload });
  }, []);

  const addLog = useCallback((entry: GameLogEntry) => {
    dispatch({ type: 'ADD_LOG', entry });
  }, []);

  const reset = useCallback(() => {
    dispatch({ type: 'RESET' });
  }, []);

  return {
    state,
    setRoom,
    setMySeat,
    setConnected,
    updatePlayerReady,
    updatePlayerUser,
    handlePhaseSync,
    handleActionAck,
    handleGameEvent,
    handleGameOver,
    handleChatMessage,
    addLog,
    reset,
  };
}
