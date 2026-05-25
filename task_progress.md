# 前后端不匹配问题修复清单

## 需要修复的问题

### 1. ACTION_ACK 载荷解析错误 (useWebSocket.ts)
后端实际发送: `payload = { ack: ActionAckPayload, phaseSyncs: [...], actorSeatId: N, actorPhaseSync: {...} }`
前端错误地将整个 payload 当作 ActionAckPayload，需要从 `payload.ack` 提取

### 2. 后端不发送 GAME_OVER 消息，前端需要从 PHASE_SYNC 检测游戏结束
后端 GameOutcome.endGame() 只设置房间状态为 GAME_OVER，不主动发 GAME_OVER 消息
但 GameTickScheduler 会广播 PHASE_SYNC，其中 currentPhase = GAME_OVER
前端需要从 PHASE_SYNC 检测 GAME_OVER 并触发游戏结束逻辑

### 3. 后端不广播 CHAT_BROADCAST，前端需要从 ACTION_ACK 获取聊天反馈
后端处理 SPEAK/WOLF_CHAT 后只返回 ACTION_ACK，不广播 CHAT_BROADCAST
前端需要调整聊天显示逻辑

### 4. 前端 useWebSocket.ts 中 PHASE_SYNC 解析需要适配后端实际格式
后端发送: `{ type: "PHASE_SYNC", payload: { seatId: N, phaseSync: PhaseSyncPayload } }`
前端需要正确提取 phaseSync

### 5. GamePage.tsx 中 onActionAck 回调需要适配新结构
