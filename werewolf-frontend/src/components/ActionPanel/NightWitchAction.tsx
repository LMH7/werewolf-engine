import React from 'react';

interface NightWitchActionProps {
  selectedTarget: number | null;
  wolfKillTarget: number | null;
  antidoteLeft: number;
  poisonLeft: number;
  onSave: () => void;
  onPoison: () => void;
  onSkip: () => void;
}

export const NightWitchAction: React.FC<NightWitchActionProps> = ({
  selectedTarget,
  wolfKillTarget,
  antidoteLeft,
  poisonLeft,
  onSave,
  onPoison,
  onSkip,
}) => {
  return (
    <div className="card space-y-3">
      <h3 className="text-lg font-bold text-purple-400">🧪 女巫行动</h3>

      {wolfKillTarget && antidoteLeft > 0 && (
        <div className="bg-purple-900/30 border border-purple-700 rounded-lg p-3">
          <p className="text-sm text-purple-200">
            今晚被狼人击杀的是: <span className="font-bold">#{wolfKillTarget}</span>
          </p>
        </div>
      )}

      <p className="text-sm text-gray-400">
        {antidoteLeft > 0 ? `💊 解药剩余: ${antidoteLeft}` : '💊 解药已用'}
        {' | '}
        {poisonLeft > 0 ? `☠️ 毒药剩余: ${poisonLeft}` : '☠️ 毒药已用'}
      </p>

      <p className="text-sm text-gray-400">
        {selectedTarget
          ? `已选择目标: #${selectedTarget}`
          : '选择目标后使用毒药，或直接救/跳过'}
      </p>

      <div className="flex gap-2">
        <button
          className="btn-primary flex-1"
          onClick={onSave}
          disabled={!wolfKillTarget || antidoteLeft <= 0}
        >
          💊 救
        </button>
        <button
          className="btn-danger flex-1"
          onClick={onPoison}
          disabled={!selectedTarget || poisonLeft <= 0}
        >
          ☠️ 毒
        </button>
        <button className="btn-secondary flex-1" onClick={onSkip}>
          ⏭️ 跳过
        </button>
      </div>
    </div>
  );
};
