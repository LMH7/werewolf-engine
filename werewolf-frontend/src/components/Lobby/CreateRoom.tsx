import React, { useState } from 'react';
import { api } from '../../services/api';

interface CreateRoomProps {
  onRoomCreated: (roomId: string, isOwner: boolean) => void;
}

export const CreateRoom: React.FC<CreateRoomProps> = ({ onRoomCreated }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleCreate = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await api.createRoom();
      onRoomCreated(res.roomId, true);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '创建房间失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card text-center">
      <h2 className="text-xl font-bold text-gold mb-2">创建新房间</h2>
      <p className="text-gray-400 text-sm mb-4">创建一个新的狼人杀房间，你将自动成为房主</p>
      <button className="btn-primary w-full" onClick={handleCreate} disabled={loading}>
        {loading ? '创建中...' : '创建房间'}
      </button>
      {error && <p className="text-red-400 text-sm mt-2">{error}</p>}
    </div>
  );
};
