import React, { useState, useRef, useEffect } from 'react';
import { ChatMessage, GamePhase, Role } from '../../types/game';

interface ChatPanelProps {
  messages: ChatMessage[];
  phase: GamePhase;
  myRole: Role | null;
  mySeatId: number | null;
  wolfChatInPhase: boolean;
  onSendMessage: (content: string, isWolfChat: boolean) => void;
}

export const ChatPanel: React.FC<ChatPanelProps> = ({
  messages,
  phase,
  myRole,
  mySeatId,
  wolfChatInPhase,
  onSendMessage,
}) => {
  const [input, setInput] = useState('');
  const [wolfChat, setWolfChat] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  const isWolf = myRole === Role.WEREWOLF;
  const canChat = phase === GamePhase.DAY_DISCUSS || (wolfChatInPhase && isWolf);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = () => {
    if (!input.trim()) return;
    onSendMessage(input.trim(), wolfChat);
    setInput('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const filteredMessages = wolfChat
    ? messages.filter((m) => m.isWolfChat)
    : messages.filter((m) => !m.isWolfChat);

  return (
    <div className="card flex flex-col h-full">
      {/* Chat header */}
      <div className="flex items-center justify-between mb-2 pb-2 border-b border-gray-700">
        <h3 className="text-sm font-semibold text-gray-300">
          {wolfChat ? '🐺 狼群密语' : '💬 公共聊天'}
        </h3>
        {isWolf && wolfChatInPhase && (
          <button
            className={`text-xs px-2 py-1 rounded-full transition-colors ${
              wolfChat
                ? 'bg-wolf text-white'
                : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
            }`}
            onClick={() => setWolfChat(!wolfChat)}
          >
            {wolfChat ? '狼群' : '公共'}
          </button>
        )}
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto space-y-1 mb-2 max-h-40">
        {filteredMessages.length === 0 && (
          <p className="text-gray-600 text-xs text-center py-4">暂无消息</p>
        )}
        {filteredMessages.map((msg, i) => (
          <div
            key={i}
            className={`text-sm ${
              msg.seatId === mySeatId ? 'text-gold' : 'text-gray-300'
            }`}
          >
            <span className="font-semibold">
              {msg.seatId === mySeatId ? '我' : `#${msg.seatId}`}:
            </span>{' '}
            <span>{msg.content}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      {canChat && (
        <div className="flex gap-2">
          <input
            className="input-field flex-1 text-sm"
            placeholder={wolfChat ? '输入狼群密语...' : '输入发言...'}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <button className="btn-primary text-sm px-3" onClick={handleSend}>
            发送
          </button>
        </div>
      )}
    </div>
  );
};
