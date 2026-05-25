import React, { useState } from 'react';
import { api } from '../../services/api';

interface JoinRoomProps {
  onJoined: (roomId: string, seatId: number, userId: number) => void;
}

export const JoinRoom: React.FC<JoinRoomProps> = ({ onJoined }) => {
  const [roomId, setRoomId] = useState('');
  const [seatId, setSeatId] = useState(1);
  const [userId, setUserId] = useState(Math.floor(Math.random() * 9000 + 1000));
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleJoin = async () => {
    if (!roomId.trim()) {
      setError('请输入房间ID');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await api.joinRoom(roomId.trim(), seatId, userId);
      onJoined(roomId.trim(), seatId, userId);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加入房间失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h2 className="text-xl font-bold text-gold mb-4">加入房间</h2>

      <div className="space-y-3">
        <div>
          <label className="block text-sm text-gray-400 mb-1">房间 ID</label>
          <input
            className="input-field"
            placeholder="输入房间ID..."
            value={roomId}
            onChange={(e) => setRoomId(e.target.value)}
          />
        </div>

        <div>
          <label className="block text-sm text-gray-400 mb-1">座位号 (1-12)</label>
          <div className="grid grid-cols-6 gap-2">
            {Array.from({ length: 12 }, (_, i) => i + 1).map((s) => (
              <button
                key={s}
                className={`px-3 py-2 rounded-lg text-sm font-bold transition-colors ${
                  seatId === s
                    ? 'bg-gold text-night'
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
                onClick={() => setSeatId(s)}
              >
                {s}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm text-gray-400 mb-1">用户 ID</label>
          <input
            className="input-field"
            type="number"
            value={userId}
            onChange={(e) => setUserId(Number(e.target.value))}
          />
        </div>

        <button className="btn-primary w-full" onClick={handleJoin} disabled={loading}>
          {loading ? '加入中...' : '加入房间'}
        </button>

        {error && <p className="text-red-400 text-sm">{error}</p>}
      </div>
    </div>
  );
};
