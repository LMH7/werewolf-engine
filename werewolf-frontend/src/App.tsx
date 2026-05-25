import React, { useState } from 'react';
import { Lobby } from './components/Lobby/Lobby';
import { GamePage } from './components/GamePage';

type Screen = 'lobby' | 'game';

interface GameConfig {
  roomId: string;
  seatId: number;
  userId: number;
  isOwner: boolean;
}

const App: React.FC = () => {
  const [screen, setScreen] = useState<Screen>('lobby');
  const [gameConfig, setGameConfig] = useState<GameConfig | null>(null);

  const handleEnterGame = (roomId: string, seatId: number, userId: number, isOwner: boolean) => {
    setGameConfig({ roomId, seatId, userId, isOwner });
    setScreen('game');
  };

  const handleBackToLobby = () => {
    setGameConfig(null);
    setScreen('lobby');
  };

  if (screen === 'game' && gameConfig) {
    return (
      <GamePage
        roomId={gameConfig.roomId}
        seatId={gameConfig.seatId}
        userId={gameConfig.userId}
        isOwner={gameConfig.isOwner}
        onBackToLobby={handleBackToLobby}
      />
    );
  }

  return <Lobby onEnterGame={handleEnterGame} />;
};

export default App;
