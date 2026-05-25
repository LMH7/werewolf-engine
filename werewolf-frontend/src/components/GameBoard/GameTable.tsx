import React from 'react';
import { PlayerInfo, GamePhase, Role } from '../../types/game';
import { SeatCard } from './SeatCard';

interface GameTableProps {
  players: PlayerInfo[];
  mySeatId: number | null;
  phase: GamePhase;
  phaseSync: {
    yourRole: Role | null;
    canAct: boolean;
    canVote: boolean;
  } | null;
  selectedTarget: number | null;
  onSelectTarget: (seatId: number) => void;
}

export const GameTable: React.FC<GameTableProps> = ({
  players,
  mySeatId,
  phase,
  phaseSync,
  selectedTarget,
  onSelectTarget,
}) => {
  // Determine if seats should be selectable based on phase
  const isNightPhase = [
    GamePhase.NIGHT_WOLF,
    GamePhase.NIGHT_SEER,
    GamePhase.NIGHT_WITCH,
    GamePhase.HUNTER_SHOOT,
  ].includes(phase);

  const isDayVote = phase === GamePhase.DAY_VOTE;

  const canSelect = (player: PlayerInfo): boolean => {
    if (!player.alive) return false;
    if (player.seatId === mySeatId) return false;
    if (isNightPhase && phaseSync?.canAct) return true;
    if (isDayVote && phaseSync?.canVote) return true;
    return false;
  };

  const showRole = phase !== GamePhase.WAITING;

  return (
    <div className="card">
      <h3 className="text-sm font-semibold text-gray-400 mb-3 text-center uppercase tracking-wider">
        座位表
      </h3>

      {/* 12 seats in a 4x3 grid */}
      <div className="seat-grid">
        {players.map((player) => (
          <SeatCard
            key={player.seatId}
            player={player}
            isMySeat={player.seatId === mySeatId}
            isSelected={selectedTarget === player.seatId}
            selectable={canSelect(player)}
            showRole={showRole}
            onClick={() => canSelect(player) && onSelectTarget(player.seatId)}
          />
        ))}
      </div>
    </div>
  );
};
