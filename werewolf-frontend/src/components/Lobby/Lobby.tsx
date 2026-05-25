import React from 'react';
import { CreateRoom } from './CreateRoom';
import { JoinRoom } from './JoinRoom';

interface LobbyProps {
  onEnterGame: (roomId: string, seatId: number, userId: number, isOwner: boolean) => void;
}

export const Lobby: React.FC<LobbyProps> = ({ onEnterGame }) => {
  const handleRoomCreated = (roomId: string, isOwner: boolean) => {
    // After creating room, auto-assign seat 1 as owner
    const userId = Math.floor(Math.random() * 9000 + 1000);
    onEnterGame(roomId, 1, userId, isOwner);
  };

  const handleJoined = (roomId: string, seatId: number, userId: number) => {
    onEnterGame(roomId, seatId, userId, false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-2xl">
        {/* Header */}
        <div className="text-center mb-10">
          <h1 className="text-5xl font-bold mb-2">
            <span className="text-gold">🐺 AI 狼人杀</span>
          </h1>
          <p className="text-gray-400 text-lg">基于 AI Agent 的在线狼人杀</p>
        </div>

        {/* Two columns */}
        <div className="grid md:grid-cols-2 gap-6">
          <CreateRoom onRoomCreated={handleRoomCreated} />
          <JoinRoom onJoined={handleJoined} />
        </div>

        {/* Footer info */}
        <div className="mt-8 text-center text-gray-500 text-xs space-y-1">
          <p>后端: werewolf-engine (Java 21 + Spring Boot 4)</p>
          <p>前端: React 18 + TypeScript + Tailwind CSS</p>
        </div>
      </div>
    </div>
  );
};
