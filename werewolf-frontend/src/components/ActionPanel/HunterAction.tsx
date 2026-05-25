import React from 'react';

interface HunterActionProps {
  selectedTarget: number | null;
  onShoot: () => void;
  onSkip: () => void;
}

export const HunterAction: React.FC<HunterActionProps> = ({
  selectedTarget,
  onShoot,
  onSkip,
}) => {
  return (
    <div className="card space-y-3">
      <h3 className="text-lg font-bold text-orange-400">🏹 猎人行动</h3>
      <p className="text-sm text-gray-400">
        你被击杀！可以开枪带走一名玩家
      </p>
      <p className="text-sm text-gray-400">
        {selectedTarget
          ? `已选择目标: #${selectedTarget}`
          : '请选择开枪目标'}
      </p>
      <div className="flex gap-2">
        <button
          className="btn-danger flex-1"
          onClick={onShoot}
          disabled={!selectedTarget}
        >
          🏹 开枪
        </button>
        <button className="btn-secondary flex-1" onClick={onSkip}>
          ⏭️ 不开枪
        </button>
      </div>
    </div>
  );
};
