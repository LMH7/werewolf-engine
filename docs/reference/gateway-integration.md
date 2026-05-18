# Gateway / Bot 联调契约（A 提供，B/C 消费）

本文描述 **Week1 Day4～Day6** 联调时，网关（B）与 Bot（C）如何驱动已实现的 `game` + `ai` 能力。对外 WS 协议仍以 PRD §4.6 为准；在 `gateway` 未就绪前，可用 **internal HTTP** 验证端到端。

## 1. Internal HTTP（临时）

Base: `http://localhost:8080/internal/game`（无鉴权，仅 dev）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/rooms` | 建房并 12 人 ready |
| POST | `/rooms/{roomId}/start` | 开局 → `NIGHT_WOLF` |
| POST | `/rooms/{roomId}/actions` | 提交 `GAME_ACTION`（body 含 `content` 可选） |
| POST | `/rooms/{roomId}/advance-announce` | 离开死讯公布阶段 |
| POST | `/rooms/{roomId}/phase-tick` | **网关定时器应调用的单步推进**（见 §2） |
| POST | `/rooms/{roomId}/mock-auto-play` | 一次性跑满整局（压测/演示） |
| GET | `/rooms/{roomId}/action-log` | 本局内存 `action_log`（含 `thinking` 调试行） |
| GET | `/rooms/{roomId}` | 房间快照 |

`actions` 请求体示例：

```json
{
  "playerId": 3,
  "action": "KILL",
  "target": 8,
  "phase": "NIGHT_WOLF",
  "content": null
}
```

## 2. `GamePhaseScheduler.tick`（B 侧定时器）

每个房间、每个阶段超时或 Bot 轮询时调用 **`POST .../phase-tick`** 一次（勿 busy-wait）。

| 当前 `GamePhase` | `tick` 行为 |
|------------------|-------------|
| `NIGHT_DEATH_ANNOUNCE` / `EXILE_DEATH_ANNOUNCE` | 调用 `advanceDayAnnounce` |
| `NIGHT_WOLF` / `NIGHT_SEER` / `NIGHT_WITCH` / `DAY_DISCUSS` / `DAY_VOTE` / `HUNTER_SHOOT` / `LAST_WORDS` | `AiTurnCoordinator` 选座 → `AIService` → **一步** `handleAction`（见 [ADR-003](../adr/003-ai-integration.md)） |
| `GAME_OVER` | 返回 `GAME_OVER` |
| 其他 | `NO_OP`（等待 `start` 或系统阶段） |

响应 `status`：`ADVANCED` | `AI_STEP` | `STUCK` | `NO_OP` | `GAME_OVER`。

**正式 WS 上线后**：Gateway 在阶段超时回调中应等价调用 `GameEngineService.tickPhase(roomId)`（或注入 `GamePhaseScheduler`），并向客户端广播返回的 `phaseSyncs`（当前 internal API 仅返回 tick 元数据；B 需在 WS 层补 `PHASE_SYNC` 推送）。

## 3. Bot（C）— `bot/` 目录

见 [bot/README.md](../../bot/README.md)。Bot **不实现规则**，只：

1. 建房 / 开局（或加入 B 的 room API）
2. 循环 `phase-tick` 或订阅 WS `PHASE_SYNC` 后 `actions`
3. 断言 `action-log` / 终局 `GAME_OVER`

## 4. 与 PRD 的差距（联调时注意）

- 无 `countdown` 推送、无 Redis session 映射
- `action_log` 仅在内存，未写 MySQL
- `GAME_EVENT`（愚者翻牌等）尚未从 SM 发出
