import { CreateRoomResponse, JoinRoomResponse, RoomSnapshotResponse } from '../types/game';

const BASE_URL = '/api/room';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`HTTP ${res.status}: ${text}`);
  }
  return res.json();
}

export const api = {
  /** Create a new room */
  createRoom(roomId?: string): Promise<CreateRoomResponse> {
    return request<CreateRoomResponse>(BASE_URL, {
      method: 'POST',
      body: roomId ? JSON.stringify({ roomId }) : undefined,
    });
  },

  /** Join a room */
  joinRoom(roomId: string, seatId: number, userId: number): Promise<JoinRoomResponse> {
    return request<JoinRoomResponse>(`${BASE_URL}/${roomId}/join`, {
      method: 'POST',
      body: JSON.stringify({ seatId, userId }),
    });
  },

  /** Set ready status */
  setReady(roomId: string, seatId: number, ready: boolean): Promise<JoinRoomResponse> {
    return request<JoinRoomResponse>(`${BASE_URL}/${roomId}/ready`, {
      method: 'POST',
      body: JSON.stringify({ seatId, ready }),
    });
  },

  /** Start game (room owner) */
  startGame(roomId: string): Promise<{
    success: boolean;
    phase?: string;
    message?: string;
    phaseSyncs?: Array<Record<string, unknown>>;
  }> {
    return request(`${BASE_URL}/${roomId}/start`, { method: 'POST' });
  },

  /** Get room snapshot */
  getRoom(roomId: string): Promise<RoomSnapshotResponse> {
    return request<RoomSnapshotResponse>(`${BASE_URL}/${roomId}`);
  },
};
