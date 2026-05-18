# 狼人杀测试Bot - C角色提交包

## 📋 项目说明

本目录包含用于测试 `werewolf-engine` 后端的Python Bot客户端，完全符合PRD v1.0.11中C角色的要求。

**负责人**：角色C（测试Bot + 压测）  
**PRD版本**：v1.0.11  
**提交日期**：2026-05-18

## ✅ 验收清单（PRD §8.2 Day4联调）

所有验收点已完成：

- ✅ Bot能WS连接并收到 `CONNECTED`
- ✅ `JOIN_ROOM` 后收到 `PHASE_SYNC`，且 `playerId` 正确
- ✅ 房主 `start` 后阶段进入 `NIGHT_WOLF`（或经 `ROLE_ASSIGN`）
- ✅ Bot发送 `GAME_ACTION` 收到 `ACTION_ACK`（success或明确ERROR）
- ✅ 12 Bot同房间消息不串房

## 🏗️ 技术栈

- **Python**: 3.11+
- **WebSocket**: `websocket-client` (原生WebSocket协议，符合PRD §4.2.4)
- **HTTP**: `requests`
- **Token策略**: 直接使用 `userId` 作为 token（PRD §4.2.4）

## 📦 核心文件

```
werewolf-engine/bot/
├── config.py                 # 配置管理（服务器地址、Token生成）
├── http_api.py              # HTTP API封装（建房、加入、准备、开始）
├── ws_client.py             # WebSocket客户端（原生协议）
├── message_handler.py       # 消息处理器（解析、打印、强制显示requestId）
├── bot_player.py            # Bot玩家逻辑（整合HTTP+WS）
├── requirements.txt         # Python依赖
└── README.md               # 本文档
```

## 🚀 快速开始

### 1. 安装依赖

```bash
cd werewolf-engine/bot
pip install -r requirements.txt
```

### 2. 配置服务器地址（可选）

默认连接 `localhost:8090`，可通过环境变量覆盖：

```bash
# Windows PowerShell
$env:WEREWOLF_BASE_URL="http://192.168.1.100:8090"
$env:WEREWOLF_WS_URL="ws://192.168.1.100:8090"

# Linux/Mac
export WEREWOLF_BASE_URL="http://192.168.1.100:8090"
export WEREWOLF_WS_URL="ws://192.168.1.100:8090"
```

### 3. 使用示例

```python
from bot_player import BotPlayer

# 创建Bot
bot = BotPlayer(user_id=1001)

# 创建房间
room_id = bot.create_room(ai_count=11)

# 连接WebSocket
bot.connect_websocket()

# 加入房间
bot.join_room(room_id)

# 准备
bot.ready(True)

# 开始游戏（房主）
bot.start_game()

# 发送游戏动作
bot.send_game_action(
    action="KILL",
    phase="NIGHT_WOLF",
    target=2,
    reason="测试"
)

# 等待观察消息
bot.wait(5)

# 关闭连接
bot.close()
```

## 📝 协议规范遵循

### 消息信封格式（PRD §4.6.2）

```json
{
    "type": "MESSAGE_TYPE",
    "payload": {},
    "timestamp": 1715760000000,
    "requestId": "optional-uuid"
}
```

### HTTP API（PRD §6.1）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/room` | 创建房间 |
| POST | `/api/room/{roomId}/join` | 加入房间 |
| POST | `/api/room/{roomId}/ready` | 准备 |
| POST | `/api/room/{roomId}/start` | 开始游戏 |

### WebSocket（PRD §4.6.1）

- URL格式：`ws://host:port/ws/game?token={token}`
- 原生WebSocket协议（不使用STOMP）
- Token：直接使用 `userId`

### 消息类型（PRD §4.6.4）

**服务端 → 客户端**：
- `CONNECTED` - 连接确认
- `PHASE_SYNC` - 阶段同步
- `ACTION_ACK` - 操作确认
- `GAME_EVENT` - 游戏事件
- `ERROR` - 错误消息

**客户端 → 服务端**：
- `JOIN_ROOM` - 加入房间
- `READY` - 准备
- `GAME_ACTION` - 游戏动作

## 🎯 技术亮点

1. **协议严格遵循**：100%符合PRD v1.0.11规范
2. **线程安全设计**：使用锁保证多线程打印不乱码
3. **事件驱动**：WebSocket使用`threading.Event`等待CONNECTED确认
4. **错误处理完善**：完善的异常捕获和超时处理
5. **RequestID强制显示**：所有消息的requestId都显眼打印（用户要求）

## 📊 与后端接口对齐

### MessageType枚举对齐

Bot支持所有后端定义的消息类型：
```java
// 后端: MessageType.java
CONNECTED, PHASE_SYNC, ACTION_ACK, GAME_EVENT, 
CHAT_BROADCAST, GAME_OVER, ERROR, GAME_ACTION
```

### GameActionType枚举对齐

Bot的`send_game_action`支持所有动作类型：
```java
// 后端: GameActionType.java
KILL, WOLF_CHAT, SAVE, POISON, CHECK, SPEAK, 
VOTE, SHOOT, SKIP, SKIP_SPEAK, SKIP_VOTE
```

### PhaseSyncPayload字段对齐

Bot的消息处理器能正确解析所有字段：
```java
// 后端: PhaseSyncPayload.java
currentPhase, round, countdown, alivePlayers, yourRole,
yourTeammates, canAct, canVote, idiotRevealed, 
wolfChatInPhase, witchAntidoteLeft, witchPoisonLeft,
wolfKillTarget, speakDirection, speakAnchorSeat, 
currentSpeakerId, seerCheckAlignment, seerCheckTarget
```

## ⚠️ 注意事项

### Token机制
- **当前阶段**：直接使用 `userId` 作为 token
- 示例：`userId=1001` → `token="1001"`
- 后续可能改为JWT，但接口保持兼容

### RequestID显示
- **强制要求**：所有消息的 `requestId` 必须显眼打印
- 格式：`🔑 RequestID: >>>>>> {id} <<<<<<`
- 如果为空：`🔑 RequestID: >>>>>> ⚠️  NONE  ⚠️  <<<<<<`

### 并发限制
- 单个Bot使用单线程（WebSocket在后台线程）
- 12个Bot并发时建议间隔0.3秒启动

## 📚 相关文档

- **PRD**: `../docs/requirements-mvp-v0.1.md` (v1.0.11)
- **技术选型**: `../docs/tech-selection-feasibility.md`
- **架构设计**: `../docs/architecture-design-spec.md`

## 🤝 协作说明

### 与A（状态机负责人）对齐
- ✅ `GamePhase` 枚举值
- ✅ `GameActionType` 枚举值
- ✅ 各阶段的超时时间

### 与B（WebSocket负责人）对齐
- ✅ Token格式：直接使用 `userId`
- ✅ 消息信封：严格遵循PRD §4.6.2
- ✅ 错误码：遵循PRD §4.6.6

## 📈 后续工作（Week2）

- [ ] Mock AI逻辑（根据阶段自动响应）
- [ ] 压测脚本（20局、100局）
- [ ] 统计胜率、时长、异常率
- [ ] 非法操作测试用例（5个）

## 📞 联系方式

**负责人**：角色C（测试Bot负责人）  
**问题反馈**：请在联调时直接沟通

---

*最后更新：2026-05-18*  
*PRD版本：v1.0.11*
