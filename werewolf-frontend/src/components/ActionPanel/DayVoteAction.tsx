import React from 'react';

interface DayVoteActionProps {
  selectedTarget: number | null;
  onVote: () => void;
  onSkipVote: () => void;
}

export const DayVoteAction: React.FC<DayVoteActionProps> = ({
  selectedTarget,
  onVote,
  onSkipVote,
}) => {
  return (
    <div className="card space-y-3">
      <h3 className="text-lg font-bold text-yellow-400">🗳️ 投票</h3>
      <p className="text-sm text-gray-400">
        {selectedTarget
          ? `已选择投票目标: #${selectedTarget}`
          : '请选择要放逐的玩家'}
      </p>
      <div className="flex gap-2">
        <button
          className="btn-primary flex-1"
          onClick={onVote}
          disabled={!selectedTarget}
        >
          ✅ 投票
        </button>
        <button className="btn-secondary flex-1" onClick={onSkipVote}>
          ⏭️ 弃票
        </button>
      </div>
    </div>
  );
};
