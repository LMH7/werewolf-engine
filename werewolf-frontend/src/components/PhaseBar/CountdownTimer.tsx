import React, { useEffect, useState } from 'react';

interface CountdownTimerProps {
  countdown: number | null;
}

export const CountdownTimer: React.FC<CountdownTimerProps> = ({ countdown }) => {
  const [display, setDisplay] = useState<number | null>(countdown);

  useEffect(() => {
    setDisplay(countdown);
    if (countdown === null || countdown <= 0) return;

    const interval = setInterval(() => {
      setDisplay((prev) => {
        if (prev === null || prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [countdown]);

  if (display === null || display < 0) return null;

  const isUrgent = display <= 10;

  return (
    <div className={`flex items-center gap-2 ${isUrgent ? 'text-blood' : 'text-gray-300'}`}>
      <span className="text-lg">⏱️</span>
      <span className={`font-mono text-xl font-bold ${isUrgent ? 'animate-pulse' : ''}`}>
        {display}s
      </span>
    </div>
  );
};
