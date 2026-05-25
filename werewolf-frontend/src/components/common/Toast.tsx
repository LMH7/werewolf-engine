import React, { useEffect, useState } from 'react';

interface ToastProps {
  message: string;
  type?: 'info' | 'error' | 'success';
  duration?: number;
  onDone: () => void;
}

export const Toast: React.FC<ToastProps> = ({ message, type = 'info', duration = 3000, onDone }) => {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setVisible(false);
      setTimeout(onDone, 300);
    }, duration);
    return () => clearTimeout(timer);
  }, [duration, onDone]);

  const colors = {
    info: 'border-blue-500 bg-blue-900/80',
    error: 'border-red-500 bg-red-900/80',
    success: 'border-green-500 bg-green-900/80',
  };

  return (
    <div
      className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-lg border ${colors[type]} backdrop-blur-sm transition-all duration-300 ${
        visible ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-4'
      }`}
    >
      <p className="text-sm text-white">{message}</p>
    </div>
  );
};
