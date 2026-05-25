import React, { useRef, useEffect } from 'react';
import { GameLogEntry, GamePhase, PhaseNames } from '../../types/game';

interface EventTimelineProps {
  entries: GameLogEntry[];
}

const typeColors: Record<string, string> = {
  system: 'text-gray-400',
  action: 'text-green-400',
  death: 'text-red-400',
  vote: 'text-yellow-400',
  event: 'text-blue-400',
};

const typeIcons: Record<string, string> = {
  system: '⚙️',
  action: '🎯',
  death: '💀',
  vote: '🗳️',
  event: '📌',
};

export const EventTimeline: React.FC<EventTimelineProps> = ({ entries }) => {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [entries]);

  return (
    <div className="card">
      <h3 className="text-sm font-semibold text-gray-400 mb-2">📜 游戏日志</h3>
      <div className="max-h-48 overflow-y-auto space-y-1">
        {entries.length === 0 && (
          <p className="text-gray-600 text-xs text-center py-4">暂无日志</p>
        )}
        {entries.map((entry) => (
          <div key={entry.id} className="flex items-start gap-2 text-xs">
            <span className="flex-shrink-0">{typeIcons[entry.type] || '📄'}</span>
            <span className={typeColors[entry.type] || 'text-gray-400'}>
              {entry.message}
            </span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  );
};
