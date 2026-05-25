import React from 'react';

export const Loading: React.FC<{ text?: string }> = ({ text = '加载中...' }) => {
  return (
    <div className="flex flex-col items-center justify-center py-12">
      <div className="w-12 h-12 border-4 border-gold/30 border-t-gold rounded-full animate-spin mb-4" />
      <p className="text-gray-400 text-sm">{text}</p>
    </div>
  );
};
