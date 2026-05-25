import React from 'react';
import { Role, RoleNames, RoleEmojis } from '../../types/game';

interface RoleBadgeProps {
  role: Role | null;
  showName?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

const sizeClasses = {
  sm: 'text-xs px-2 py-0.5',
  md: 'text-sm px-3 py-1',
  lg: 'text-base px-4 py-1.5',
};

const roleColors: Record<Role, string> = {
  [Role.WEREWOLF]: 'bg-wolf/30 text-red-300 border-red-700',
  [Role.VILLAGER]: 'bg-villager/30 text-green-300 border-green-700',
  [Role.SEER]: 'bg-seer/30 text-blue-300 border-blue-700',
  [Role.WITCH]: 'bg-witch/30 text-purple-300 border-purple-700',
  [Role.HUNTER]: 'bg-hunter/30 text-orange-300 border-orange-700',
  [Role.IDIOT]: 'bg-idiot/30 text-yellow-300 border-yellow-700',
};

export const RoleBadge: React.FC<RoleBadgeProps> = ({ role, showName = true, size = 'sm' }) => {
  if (!role) return null;

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border ${roleColors[role]} ${sizeClasses[size]} font-semibold`}
    >
      <span>{RoleEmojis[role]}</span>
      {showName && <span>{RoleNames[role]}</span>}
    </span>
  );
};
