import React from 'react';

interface NightSeerActionProps {
  selectedTarget: number | null;
  onCheck: () => void;
  onSkip: () => void;
}

export const NightSeerAction: React.FC<NightSeerActionProps> = ({
  selectedTarget,
  onCheck,
  onSkip,
}) => {
  return (
    <div className="card space-y-3">
      <h3 className="text-lg font-bold text-blue-400">🔮 预言家行动</h3>
      <p className="text-sm text-gray-400">
        {selectedTarget
          ? `已选择查验目标: #${selectedTarget}`
          : '请选择一个玩家进行查验'}
      </p>
      <div className="flex gap-2">
        <button
          className="btn-primary flex-1"
          onClick={onCheck}
          disabled={!selectedTarget}
        >
          🔍 查验
        </button>
        <button className="btn-secondary flex-1" onClick={onSkip}>
          ⏭️ 跳过
        </button>
      </div>
    </div>
  );
};
