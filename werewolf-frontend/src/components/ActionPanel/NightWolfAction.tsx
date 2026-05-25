import React from 'react';

interface NightWolfActionProps {
  selectedTarget: number | null;
  wolfChatInPhase: boolean;
  onKill: () => void;
  onWolfChat: () => void;
  onSkip: () => void;
}

export const NightWolfAction: React.FC<NightWolfActionProps> = ({
  selectedTarget,
  wolfChatInPhase,
  onKill,
  onWolfChat,
  onSkip,
}) => {
  return (
    <div className="card space-y-3">
      <h3 className="text-lg font-bold text-red-400">🐺 狼人行动</h3>
      <p className="text-sm text-gray-400">
        {selectedTarget
          ? `已选择目标: #${selectedTarget}`
          : '请选择一个目标玩家'}
      </p>
      <div className="flex gap-2">
        <button
          className="btn-danger flex-1"
          onClick={onKill}
          disabled={!selectedTarget}
        >
          🔪 击杀
        </button>
        {wolfChatInPhase && (
          <button className="btn-secondary flex-1" onClick={onWolfChat}>
            💬 狼群聊天
          </button>
        )}
        <button className="btn-secondary flex-1" onClick={onSkip}>
          ⏭️ 跳过
        </button>
      </div>
    </div>
  );
};
