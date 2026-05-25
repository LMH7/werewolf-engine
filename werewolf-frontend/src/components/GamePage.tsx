import React, { useState, useCallback, useEffect } from 'react';

import { useGameState } from '../hooks/useGameState';
import { useWebSocket } from '../hooks/useWebSocket';
import { api } from '../services/api';
import { PhaseDisplay } from './PhaseBar/PhaseDisplay';
import { CountdownTimer } from './PhaseBar/CountdownTimer';
import { GameTable } from './GameBoard/GameTable';
import { ActionPanel } from './ActionPanel/ActionPanel';
import { ChatPanel } from './ChatPanel/ChatPanel';
import { EventTimeline } from './GameLog/EventTimeline';
import { GameOverScreen } from './GameOver/GameOverScreen';
import { Loading } from './common/Loading';
import { Toast } from './common/Toast';
import { GamePhase, PhaseSyncPayload, GameOverPayload, ChatMessagePayload, MessageType } from '../types/game';


interface GamePageProps {
  roomId: string;
  seatId: number;
  userId: number;
  isOwner: boolean;
  onBackToLobby: () => void;
}

export const GamePage: React.FC<GamePageProps> = ({
  roomId,
  seatId,
  userId,
  isOwner,
  onBackToLobby,
}) => {
  const {
    state,
    setRoom,
    setMySeat,
    setConnected,
    updatePlayerReady,
    handlePhaseSync,
    handleActionAck,
    handleGameOver,
    handleChatMessage,
    addLog,
    reset,
  } = useGameState();

  const [selectedTarget, setSelectedTarget] = useState<number | null>(null);
  const [toast, setToast] = useState<{ message: string; type: 'info' | 'error' | 'success' } | null>(null);
  const [loading, setLoading] = useState(true);

  // Initialize state
  useEffect(() => {
    setRoom(roomId, isOwner);
    setMySeat(seatId, userId);
  }, [roomId, seatId, userId, isOwner, setRoom, setMySeat]);

  // WebSocket callbacks
  const onConnected = useCallback(() => {
    setConnected(true);
    setLoading(false);
    setToast({ message: 'WebSocket 已连接', type: 'success' });

    // Join room via WS
    ws.joinRoom(roomId, seatId);
  }, [roomId, seatId]);

  const onPhaseSync = useCallback((payload: PhaseSyncPayload) => {
    handlePhaseSync(payload);
    setSelectedTarget(null);
  }, [handlePhaseSync]);

  const onActionAck = useCallback((payload: any) => {
    handleActionAck(payload);
    if (!payload.success) {
      setToast({ message: payload.message || '操作失败', type: 'error' });
    }
  }, [handleActionAck]);

  const onGameOver = useCallback((payload: GameOverPayload) => {
    handleGameOver(payload);
  }, [handleGameOver]);

  const onChatMessage = useCallback((payload: ChatMessagePayload) => {
    handleChatMessage(payload);
  }, [handleChatMessage]);

  const onWsError = useCallback((message: string) => {
    setToast({ message, type: 'error' });
  }, []);

  const ws = useWebSocket({
    userId,
    onConnected,
    onPhaseSync,
    onActionAck,
    onGameOver,
    onChatMessage,
    onError: onWsError,
  });

  // Connect WebSocket on mount
  useEffect(() => {
    ws.connect();
    return () => {
      ws.disconnect();
    };
  }, []);

  // Handle ready
  const handleReady = async () => {
    try {
      await api.setReady(roomId, seatId, true);
      updatePlayerReady(seatId, true);
      ws.setReady(roomId, seatId, true);
      setToast({ message: '已准备', type: 'success' });
    } catch (e: unknown) {
      setToast({ message: e instanceof Error ? e.message : '准备失败', type: 'error' });
    }
  };

  // Handle start game (owner only)
  const handleStart = async () => {
    try {
      const result = await api.startGame(roomId);
      if (result.success) {
        setToast({ message: '游戏已开始！', type: 'success' });
        // The backend will push PHASE_SYNC via WebSocket shortly after start.
        // We also request an immediate phase sync for our seat to update the UI.
        setTimeout(() => {
          ws.send(MessageType.PHASE_SYNC, { roomId, seatId });
        }, 500);
      } else {
        setToast({ message: result.message || '开局失败', type: 'error' });
      }
    } catch (e: unknown) {
      setToast({ message: e instanceof Error ? e.message : '开局失败', type: 'error' });
    }
  };

  // Handle game action
  const handleAction = useCallback(
    (action: string, target?: number, content?: string) => {
      ws.sendGameAction(roomId, seatId, action, state.phase, target, content);
      setSelectedTarget(null);
    },
    [ws, roomId, seatId, state.phase]
  );

  // Handle chat message
  const handleSendMessage = useCallback(
    (content: string, isWolfChat: boolean) => {
      ws.sendGameAction(roomId, seatId, isWolfChat ? 'WOLF_CHAT' : 'SPEAK', state.phase, undefined, content);
      // The backend will broadcast CHAT_BROADCAST to all players including the sender,
      // so we don't need to add the message locally anymore.
    },
    [ws, roomId, seatId, state.phase]
  );



  // Handle back to lobby
  const handleBackToLobby = () => {
    ws.disconnect();
    reset();
    onBackToLobby();
  };

  if (loading) {
    return <Loading text="正在连接游戏服务器..." />;
  }

  const isWaiting = state.phase === GamePhase.WAITING;
  const isGameOver = state.phase === GamePhase.GAME_OVER;

  return (
    <div className="min-h-screen p-4">
      {/* Toast notifications */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onDone={() => setToast(null)}
        />
      )}

      <div className="max-w-6xl mx-auto space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-gold">🐺 AI 狼人杀</h1>
            <p className="text-xs text-gray-500">房间: {roomId} | 座位: #{seatId}</p>
          </div>
          <div className="flex items-center gap-2">
            {isWaiting && (
              <>
                <button className="btn-primary" onClick={handleReady}>
                  ✅ 准备
                </button>
                {isOwner && (
                  <button className="btn-danger" onClick={handleStart}>
                    🎮 开始游戏
                  </button>
                )}
              </>
            )}
            <button className="btn-secondary text-sm" onClick={handleBackToLobby}>
              退出
            </button>
          </div>
        </div>

        {/* Phase display */}
        <PhaseDisplay
          phase={state.phase}
          round={state.round}
          yourRole={state.phaseSync?.yourRole || null}
        />

        {/* Countdown */}
        {state.phaseSync?.countdown != null && (
          <CountdownTimer countdown={state.phaseSync.countdown} />
        )}

        {/* Main game area */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          {/* Left: Game table */}
          <div className="lg:col-span-2 space-y-4">
            <GameTable
              players={state.players}
              mySeatId={state.mySeatId}
              phase={state.phase}
              phaseSync={state.phaseSync ? { yourRole: state.phaseSync.yourRole, canAct: state.phaseSync.canAct, canVote: state.phaseSync.canVote } : null}
              selectedTarget={selectedTarget}
              onSelectTarget={setSelectedTarget}
            />

            {/* Action panel */}
            <ActionPanel
              phase={state.phase}
              myRole={state.phaseSync?.yourRole || null}
              phaseSync={state.phaseSync}
              selectedTarget={selectedTarget}
              onAction={handleAction}
            />
          </div>

          {/* Right: Chat + Log */}
          <div className="space-y-4">
            <ChatPanel
              messages={state.chatMessages}
              phase={state.phase}
              myRole={state.phaseSync?.yourRole || null}
              mySeatId={state.mySeatId}
              wolfChatInPhase={state.phaseSync?.wolfChatInPhase || false}
              onSendMessage={handleSendMessage}
            />
            <EventTimeline entries={state.gameLog} />
          </div>
        </div>
      </div>

      {/* Game Over overlay */}
      {isGameOver && (
        <GameOverScreen
          winner={state.winner}
          roles={state.finalRoles || {}}
          onBackToLobby={handleBackToLobby}
        />
      )}

    </div>
  );
};
