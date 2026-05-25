import React from 'react';
import { GamePhase, PhaseNames, Role, RoleEmojis, RoleNames } from '../../types/game';

interface PhaseDisplayProps {
  phase: GamePhase;
  round: number;
  yourRole: Role | null;
}

const phaseColors: Record<GamePhase, string> = {
  [GamePhase.WAITING]: 'bg-gray-700 text-gray-300',
  [GamePhase.ROLE_ASSIGN]: 'bg-purple-700 text-purple-200',
  [GamePhase.NIGHT_START]: 'bg-indigo-900 text-indigo-200',
  [GamePhase.NIGHT_WOLF]: 'bg-wolf text-red-200',
  [GamePhase.NIGHT_SEER]: 'bg-seer text-blue-200',
  [GamePhase.NIGHT_WITCH]: 'bg-witch text-purple-200',
  [GamePhase.HUNTER_SHOOT]: 'bg-hunter text-orange-200',
  [GamePhase.NIGHT_DEATH_ANNOUNCE]: 'bg-gray-800 text-gray-200',
  [GamePhase.LAST_WORDS]: 'bg-gray-800 text-gray-200',
  [GamePhase.EXILE_DEATH_ANNOUNCE]: 'bg-gray-800 text-gray-200',
  [GamePhase.DAY_DISCUSS]: 'bg-yellow-800 text-yellow-200',
  [GamePhase.DAY_VOTE]: 'bg-orange-800 text-orange-200',
  [GamePhase.VOTE_RESULT]: 'bg-orange-800 text-orange-200',
  [GamePhase.CHECK_WIN]: 'bg-green-800 text-green-200',
  [GamePhase.GAME_OVER]: 'bg-gold text-night',
};

export const PhaseDisplay: React.FC<PhaseDisplayProps> = ({ phase, round, yourRole }) => {
  const isNight = phase.toString().startsWith('NIGHT');
  const isDay = phase.toString().startsWith('DAY');

  return (
    <div className="card flex items-center justify-between">
      <div className="flex items-center gap-3">
        {/* Phase badge */}
        <span className={`phase-badge ${phaseColors[phase] || 'bg-gray-700'}`}>
          {PhaseNames[phase] || phase}
        </span>

        {/* Round */}
        {round > 0 && (
          <span className="text-sm text-gray-400">
            第 {round} 晚/天
          </span>
        )}

        {/* Night / Day indicator */}
        {isNight && <span className="text-sm text-indigo-300">🌙 夜晚</span>}
        {isDay && <span className="text-sm text-yellow-300">☀️ 白天</span>}
      </div>

      {/* Role display */}
      {yourRole && (
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-400">你的身份:</span>
          <span className="text-lg">{RoleEmojis[yourRole]}</span>
          <span className="text-sm font-semibold text-gold">{RoleNames[yourRole]}</span>
        </div>
      )}
    </div>
  );
};
