import React from 'react';
import { PlayerInfo, Role, RoleEmojis, RoleNames } from '../../types/game';
import { RoleBadge } from './RoleBadge';

interface SeatCardProps {
  player: PlayerInfo;
  isMySeat: boolean;
  isSelected: boolean;
  selectable: boolean;
  showRole: boolean;
  onClick?: () => void;
}

export const SeatCard: React.FC<SeatCardProps> = ({
  player,
  isMySeat,
  isSelected,
  selectable,
  showRole,
  onClick,
}) => {
  const { seatId, alive, ready, role, isHuman } = player;

  return (
    <button
      className={`
        relative card flex flex-col items-center justify-center p-3 transition-all duration-200
        ${!alive ? 'opacity-40 grayscale' : ''}
        ${isMySeat ? 'ring-2 ring-gold ring-offset-2 ring-offset-night' : ''}
        ${isSelected ? 'ring-2 ring-blood ring-offset-2 ring-offset-night bg-blood/20' : ''}
        ${selectable && alive ? 'cursor-pointer hover:bg-gray-800/80 hover:scale-105' : 'cursor-default'}
        ${!alive ? 'bg-gray-900/40' : ''}
      `}
      onClick={selectable && alive ? onClick : undefined}
      disabled={!selectable || !alive}
    >
      {/* Seat number */}
      <div className="absolute top-2 left-2 text-xs font-bold text-gray-500">
        #{seatId}
      </div>

      {/* Ready indicator */}
      {ready && alive && (
        <div className="absolute top-2 right-2 w-2 h-2 rounded-full bg-green-400 animate-pulse" />
      )}

      {/* Avatar */}
      <div className={`text-3xl mb-1 ${!alive ? 'grayscale' : ''}`}>
        {role && showRole ? RoleEmojis[role] : '👤'}
      </div>

      {/* Player name */}
      <div className="text-xs font-medium text-gray-300 mb-1">
        {isHuman ? `玩家${seatId}` : `AI-${seatId}`}
      </div>

      {/* Role (if revealed) */}
      {showRole && role && (
        <RoleBadge role={role} size="sm" />
      )}

      {/* Death marker */}
      {!alive && (
        <div className="absolute inset-0 flex items-center justify-center">
          <span className="text-4xl font-bold text-blood/60">💀</span>
        </div>
      )}

      {/* My seat indicator */}
      {isMySeat && (
        <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-gold text-night text-xs font-bold px-2 py-0.5 rounded-full">
          我
        </div>
      )}
    </button>
  );
};
