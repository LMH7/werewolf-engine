// ===== Enums (matching backend) =====

export enum GamePhase {
  WAITING = 'WAITING',
  ROLE_ASSIGN = 'ROLE_ASSIGN',
  NIGHT_START = 'NIGHT_START',
  NIGHT_WOLF = 'NIGHT_WOLF',
  NIGHT_SEER = 'NIGHT_SEER',
  NIGHT_WITCH = 'NIGHT_WITCH',
  HUNTER_SHOOT = 'HUNTER_SHOOT',
  NIGHT_DEATH_ANNOUNCE = 'NIGHT_DEATH_ANNOUNCE',
  LAST_WORDS = 'LAST_WORDS',
  EXILE_DEATH_ANNOUNCE = 'EXILE_DEATH_ANNOUNCE',
  DAY_DISCUSS = 'DAY_DISCUSS',
  DAY_VOTE = 'DAY_VOTE',
  VOTE_RESULT = 'VOTE_RESULT',
  CHECK_WIN = 'CHECK_WIN',
  GAME_OVER = 'GAME_OVER',
}

export enum GameActionType {
  KILL = 'KILL',
  WOLF_CHAT = 'WOLF_CHAT',
  SAVE = 'SAVE',
  POISON = 'POISON',
  CHECK = 'CHECK',
  SPEAK = 'SPEAK',
  VOTE = 'VOTE',
  SHOOT = 'SHOOT',
  SKIP = 'SKIP',
  SKIP_SPEAK = 'SKIP_SPEAK',
  SKIP_VOTE = 'SKIP_VOTE',
}

export enum Role {
  WEREWOLF = 'WEREWOLF',
  VILLAGER = 'VILLAGER',
  IDIOT = 'IDIOT',
  SEER = 'SEER',
  WITCH = 'WITCH',
  HUNTER = 'HUNTER',
}

export enum SpeakDirection {
  CLOCKWISE = 'CLOCKWISE',
  COUNTER_CLOCKWISE = 'COUNTER_CLOCKWISE',
}

export enum RoomStatus {
  WAITING = 'WAITING',
  PLAYING = 'PLAYING',
  ENDED = 'ENDED',
}

export enum GameWinner {
  VILLAGERS = 'VILLAGERS',
  WEREWOLVES = 'WEREWOLVES',
}

export enum MessageType {
  CONNECTED = 'CONNECTED',
  PHASE_SYNC = 'PHASE_SYNC',
  ACTION_ACK = 'ACTION_ACK',
  GAME_EVENT = 'GAME_EVENT',
  CHAT_BROADCAST = 'CHAT_BROADCAST',
  GAME_OVER = 'GAME_OVER',
  ERROR = 'ERROR',
  GAME_ACTION = 'GAME_ACTION',
  JOIN_ROOM = 'JOIN_ROOM',
  READY = 'READY',
}

// ===== Payloads =====

export interface PhaseSyncPayload {
  currentPhase: GamePhase;
  round: number;
  countdown: number | null;
  alivePlayers: number[];
  yourRole: Role | null;
  yourTeammates: number[];
  canAct: boolean;
  canVote: boolean;
  idiotRevealed: boolean;
  wolfChatInPhase: boolean;
  witchAntidoteLeft: number;
  witchPoisonLeft: number;
  wolfKillTarget: number | null;
  speakDirection: SpeakDirection | null;
  speakAnchorSeat: number | null;
  currentSpeakerId: number | null;
  seerCheckAlignment: string | null;
  seerCheckTarget: number | null;
}

export interface ActionAckPayload {
  success: boolean;
  message: string;
  code: string | null;
  serverPhase: GamePhase;
  playerSubState: string | null;
}

export interface GameEventPayload {
  type: string;
  data: Record<string, unknown>;
}

export interface GameOverPayload {
  winner: GameWinner;
  roles: Record<number, Role>;
}

export interface ChatMessagePayload {
  seatId: number;
  content: string;
  isWolfChat: boolean;
}

// ===== WebSocket Envelope =====

export interface WsEnvelope {
  type: string;
  payload: Record<string, unknown>;
  timestamp?: number;
  requestId?: string;
}

// ===== Game State (frontend) =====

export interface PlayerInfo {
  seatId: number;
  alive: boolean;
  ready: boolean;
  role: Role | null;
  isHuman: boolean;
  userId: number | null;
}

export interface GameState {
  roomId: string;
  phase: GamePhase;
  round: number;
  status: RoomStatus;
  mySeatId: number | null;
  myUserId: number | null;
  phaseSync: PhaseSyncPayload | null;
  players: PlayerInfo[];
  gameLog: GameLogEntry[];
  chatMessages: ChatMessage[];
  connected: boolean;
  isRoomOwner: boolean;
  winner: GameWinner | null;
  finalRoles: Record<number, Role> | null;
}

export interface GameLogEntry {
  id: number;
  round: number;
  phase: GamePhase;
  message: string;
  type: 'system' | 'action' | 'death' | 'vote' | 'event';
}

export interface ChatMessage {
  seatId: number;
  content: string;
  isWolfChat: boolean;
  timestamp: number;
}

// ===== HTTP API types =====

export interface CreateRoomResponse {
  roomId: string;
  status: string;
  phase: string;
  round: number;
}

export interface JoinRoomResponse {
  roomId: string;
  seatId: number;
  userId: number;
  ready: boolean;
  phase: string;
}

export interface RoomSnapshotResponse {
  roomId: string;
  status: string;
  phase: string;
  round: number;
}

// ===== Role display helpers =====

export const RoleNames: Record<Role, string> = {
  [Role.WEREWOLF]: '狼人',
  [Role.VILLAGER]: '村民',
  [Role.IDIOT]: '愚者',
  [Role.SEER]: '预言家',
  [Role.WITCH]: '女巫',
  [Role.HUNTER]: '猎人',
};

export const RoleEmojis: Record<Role, string> = {
  [Role.WEREWOLF]: '🐺',
  [Role.VILLAGER]: '👤',
  [Role.IDIOT]: '🤡',
  [Role.SEER]: '🔮',
  [Role.WITCH]: '🧪',
  [Role.HUNTER]: '🏹',
};

export const PhaseNames: Record<GamePhase, string> = {
  [GamePhase.WAITING]: '等待中',
  [GamePhase.ROLE_ASSIGN]: '分配身份',
  [GamePhase.NIGHT_START]: '夜幕降临',
  [GamePhase.NIGHT_WOLF]: '狼人夜',
  [GamePhase.NIGHT_SEER]: '预言家夜',
  [GamePhase.NIGHT_WITCH]: '女巫夜',
  [GamePhase.HUNTER_SHOOT]: '猎人开枪',
  [GamePhase.NIGHT_DEATH_ANNOUNCE]: '昨夜死讯',
  [GamePhase.LAST_WORDS]: '遗言',
  [GamePhase.EXILE_DEATH_ANNOUNCE]: '放逐死讯',
  [GamePhase.DAY_DISCUSS]: '白天讨论',
  [GamePhase.DAY_VOTE]: '白天投票',
  [GamePhase.VOTE_RESULT]: '投票结果',
  [GamePhase.CHECK_WIN]: '胜负判定',
  [GamePhase.GAME_OVER]: '游戏结束',
};
