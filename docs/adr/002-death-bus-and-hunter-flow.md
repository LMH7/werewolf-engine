# ADR-002: Synchronous death bus and hunter shoot flow (narrow)

| 属性 | 值 |
|------|-----|
| 状态 | 已采纳（Accepted，与 PRD v1.0.11 §4.3.8 同步） |
| 日期 | 2026-05-16 |
| 决策者 | 游戏引擎（A） |
| 关联 | [PRD §4.3.8](../progress/requirements-mvp-v0.1.md)、[ADR-001](001-night-skill-pipeline.md)、[architecture](../architecture/architecture-design-spec.md) |

## 背景

`GameStateMachine` 集中了夜末死亡、R23 判胜、猎人 `pendingHunterAfterAnnounce`、`HUNTER_SHOOT` 与放逐线 routing。猎人规则横切多个阶段，易与「先开枪后判胜」（§3.2.1）纠缠。

窄版目标：**不改** `GamePhase` 与 WS 协议，将 **真实死亡** 与 **猎人死后流程** 拆出 SM。

## 决策

### 1. DeathBus（同步、窄版）

- **单入口** `DeathBus.apply(GameRoomState room, List<DeathRecord> records)`。
- **同步**：同房间锁、同线程；**禁止** Spring ApplicationEvent 异步或消息队列（宽版/守卫预留另议）。
- **DeathRecord**：`seat` + `DeathCause`（`WOLF_KILL` | `POISON` | `VOTE_EXILE` | `HUNTER_SHOOT`）。
- **流程**：写 `is_alive=false` → 依次调用订阅者。

**订阅者（MVP 仅两个）**

| 订阅者 | 职责 |
|--------|------|
| `WinCheckSubscriber` | `WinChecker.evaluate`；胜则 `GAME_OVER`，清 `pendingHunterAfterAnnounce` / `hunterShooterSeat` / `hunterShootAfterExile` |
| `HunterPendingSubscriber` | 夜末批量死亡后：若 R7（狼刀致死猎人、非同夜毒杀该座）则 `setPendingHunterAfterAnnounce`；R8 毒杀猎人**不** pending |

**调用点**

- `NightResolver` 在狼/毒结算完成后构建 `DeathRecord` 列表并 `apply`。
- `ExileResolver`（或 SM 投票结算）对非愚者致死、`HUNTER_SHOOT` 的 `SHOOT`/`SKIP` 致死。

**不走 DeathBus**

- 愚者翻牌（R19）：仅改 `idiotRevealed` / `canVote`。
- 仅标记「待公布后猎人」时座位仍存活。

### 2. HunterShootFlow

- **独立类** `HunterShootFlow`（包 `game.hunter`），**不**注册进 `NightSkillPipeline`。
- **方法（建议）**：
  - `onNightDeathsApplied(room)` — 由 HunterPendingSubscriber 或 NightResolver 尾调用（若 pending 已在 subscriber 设置则可省略）
  - `onExileVoteResolved(room, exileSeat, role)` — 最后一神猎人 → 返回 `GameOver`；猎人且非最后一神 → `EXILE_DEATH_ANNOUNCE` + pending
  - `onAnnounceAdvanced(room, afterExile)` — R23 → `HUNTER_SHOOT` 或 `DAY_DISCUSS` / `continueAfterVote`
  - `handleShoot(room, command)` — `SHOOT` / `SKIP`
  - `onShootResolved(room)` — R23 → `DAY_DISCUSS` 或 `CHECK_WIN` 路径

- **状态字段**（仍在 `GameRoomState`）：`pendingHunterAfterAnnounce`、`hunterShooterSeat`、`hunterShootAfterExile`。

### 3. 与 ADR-001 的关系

```text
NightSkillPipeline (Wolf → Seer → Witch)
  → NightResolver.buildDeaths()
  → DeathBus.apply()
       → WinCheckSubscriber  (may GAME_OVER)
       → HunterPendingSubscriber
  → GameStateMachine.enterNightDeathAnnounce() or GAME_OVER

Day vote → ExileResolver → DeathBus (if kill) or Idiot reveal
         → HunterShootFlow.onExile...

advanceDayAnnounce → HunterShootFlow.onAnnounceAdvanced(...)
```

## 后果

### 正面

- R23 在每次真实死亡后必经 WinCheckSubscriber（与 §3.2.1 对齐）。
- 猎人逻辑单测可针对 `HunterShootFlow`，无需启动完整 SM。
- 日后 `action_log` 可记录 `DEATH_APPLIED` 事件类型。

### 负面

- 迁移期 SM 与 Bus/Flow 双路径需一次性切干净。
- `DeathCause` 须在夜末批量死亡时正确标记（狼刀 vs 毒）。

## 验收

- [ ] `DeathBusTest`：应用死亡后触发 R23；最后一神夜死不进公布。
- [ ] `HunterShootFlowTest`：R7/R9/R23 边界与 v1.0.10 行为一致。
- [ ] 集成：`GameStateMachineTest` 全绿。

## 参考

- PRD §3.2.1、§4.3.8.3、§4.3.8.5
- 当前实现：[`GameStateMachine.java`](../../src/main/java/com/werewolfengine/game/GameStateMachine.java)、[`NightResolver.java`](../../src/main/java/com/werewolfengine/game/NightResolver.java)
