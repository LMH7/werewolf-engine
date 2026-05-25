import React from 'react';
import { GamePhase, Role, PhaseSyncPayload } from '../../types/game';
import { NightWolfAction } from './NightWolfAction';
import { NightSeerAction } from './NightSeerAction';
import { NightWitchAction } from './NightWitchAction';
import { HunterAction } from './HunterAction';
import { DayVoteAction } from './DayVoteAction';

interface ActionPanelProps {
  phase: GamePhase;
  myRole: Role | null;
  phaseSync: PhaseSyncPayload | null;
  selectedTarget: number | null;
  onAction: (action: string, target?: number, content?: string) => void;
}

export const ActionPanel: React.FC<ActionPanelProps> = ({
  phase,
  myRole,
  phaseSync,
  selectedTarget,
  onAction,
}) => {
  // Don't show action panel if we can't act
  if (!phaseSync?.canAct && phase !== GamePhase.DAY_VOTE) {
    return null;
  }

  const renderAction = () => {
    switch (phase) {
      case GamePhase.NIGHT_WOLF:
        if (myRole === Role.WEREWOLF) {
          return (
            <NightWolfAction
              selectedTarget={selectedTarget}
              wolfChatInPhase={phaseSync?.wolfChatInPhase || false}
              onKill={() => onAction('KILL', selectedTarget || undefined)}
              onWolfChat={() => onAction('WOLF_CHAT')}
              onSkip={() => onAction('SKIP')}
            />
          );
        }
        break;

      case GamePhase.NIGHT_SEER:
        if (myRole === Role.SEER) {
          return (
            <NightSeerAction
              selectedTarget={selectedTarget}
              onCheck={() => onAction('CHECK', selectedTarget || undefined)}
              onSkip={() => onAction('SKIP')}
            />
          );
        }
        break;

      case GamePhase.NIGHT_WITCH:
        if (myRole === Role.WITCH) {
          return (
            <NightWitchAction
              selectedTarget={selectedTarget}
              wolfKillTarget={phaseSync?.wolfKillTarget || null}
              antidoteLeft={phaseSync?.witchAntidoteLeft || 0}
              poisonLeft={phaseSync?.witchPoisonLeft || 0}
              onSave={() => onAction('SAVE')}
              onPoison={() => onAction('POISON', selectedTarget || undefined)}
              onSkip={() => onAction('SKIP')}
            />
          );
        }
        break;

      case GamePhase.HUNTER_SHOOT:
        if (myRole === Role.HUNTER) {
          return (
            <HunterAction
              selectedTarget={selectedTarget}
              onShoot={() => onAction('SHOOT', selectedTarget || undefined)}
              onSkip={() => onAction('SKIP')}
            />
          );
        }
        break;

      case GamePhase.DAY_VOTE:
        if (phaseSync?.canVote) {
          return (
            <DayVoteAction
              selectedTarget={selectedTarget}
              onVote={() => onAction('VOTE', selectedTarget || undefined)}
              onSkipVote={() => onAction('SKIP_VOTE')}
            />
          );
        }
        break;
    }

    // Default: show waiting message
    return (
      <div className="card text-center">
        <p className="text-gray-400 text-sm">
          {phase === GamePhase.DAY_DISCUSS
            ? '💬 白天讨论阶段，请等待轮到你发言'
            : phase === GamePhase.NIGHT_DEATH_ANNOUNCE || phase === GamePhase.EXILE_DEATH_ANNOUNCE
            ? '📢 公布死讯中...'
            : phase === GamePhase.LAST_WORDS
            ? '💀 遗言阶段'
            : phase === GamePhase.VOTE_RESULT
            ? '📊 投票结果统计中...'
            : phase === GamePhase.CHECK_WIN
            ? '⚖️ 胜负判定中...'
            : phase === GamePhase.GAME_OVER
            ? '🏁 游戏已结束'
            : '⏳ 等待你的行动...'}
        </p>
      </div>
    );
  };

  return (
    <div className="animate-fade-in">
      {renderAction()}
    </div>
  );
};
