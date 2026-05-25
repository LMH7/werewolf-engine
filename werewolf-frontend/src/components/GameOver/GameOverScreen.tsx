import React from 'react';
import { GameWinner, Role, RoleNames, RoleEmojis } from '../../types/game';

interface GameOverScreenProps {
  winner: GameWinner | null;
  roles: Record<number, Role>;
  onBackToLobby: () => void;
}

export const GameOverScreen: React.FC<GameOverScreenProps> = ({
  winner,
  roles,
  onBackToLobby,
}) => {
  const isVillagerWin = winner === GameWinner.VILLAGERS;
  const hasWinner = winner !== null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-md">
      <div className="card max-w-lg w-full mx-4 text-center animate-fade-in">
        {/* Winner announcement */}
        <div className="mb-6">
          <div className="text-6xl mb-4">{hasWinner ? (isVillagerWin ? '👑' : '🐺') : '🏁'}</div>
          <h1 className={`text-3xl font-bold mb-2 ${hasWinner ? (isVillagerWin ? 'text-green-400' : 'text-red-400') : 'text-gold'}`}>
            {hasWinner ? (isVillagerWin ? '好人阵营获胜！' : '狼人阵营获胜！') : '游戏结束'}
          </h1>
          <p className="text-gray-400">
            {hasWinner ? (isVillagerWin ? '所有狼人已被消灭' : '狼人已占领村庄') : '查看所有玩家身份'}
          </p>
        </div>

        {/* Role reveal */}
        {Object.keys(roles).length > 0 && (
          <div className="mb-6">
            <h3 className="text-sm font-semibold text-gray-400 mb-3">所有玩家身份</h3>
            <div className="grid grid-cols-4 gap-2">
              {Object.entries(roles).map(([seatId, role]) => (
                <div
                  key={seatId}
                  className="bg-gray-800/50 rounded-lg p-2 text-center"
                >
                  <div className="text-lg">{RoleEmojis[role]}</div>
                  <div className="text-xs text-gray-400">#{seatId}</div>
                  <div className="text-xs font-semibold text-gold">{RoleNames[role]}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Back button */}
        <button className="btn-primary" onClick={onBackToLobby}>
          返回大厅
        </button>
      </div>
    </div>
  );
};

