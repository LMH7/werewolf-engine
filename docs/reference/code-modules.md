# 代码模块速查（`game` + `ai`）

包依赖与 PRD §7.2 一致：`game` → `ai`；`ai` 不依赖 `gateway`。

---

## `com.werewolfengine.game`

| 功能 | 子包 | 类 | 说明 |
|------|------|-----|------|
| **门面** | `game.engine` | `GameEngineService` | HTTP/网关入口：建房、提交动作、tick、Mock 自动对局 |
| **状态机** | `game.engine` | `GameStateMachine` | 权威局况；`handleAction` / `advanceDayAnnounce` |
| **编排** | `game.orchestration` | `AiTurnCoordinator`, `TurnActorResolver` | AI 选座与 SM 桥接 |
| **编排** | `game.orchestration` | `GamePhaseScheduler` | 网关 `phase-tick` |
| **编排** | `game.orchestration` | `MockGameRunner` | dev/压测跑到 `GAME_OVER` |
| **同步** | `game.sync` | `PhaseSyncBuilder` | 按座 `PHASE_SYNC` |
| **视图** | `game.view` | `GameViews`, `GameView`, `SeatVisibility`, `SeatPerceptionProjector` | 按座可见性 + episodic 投影（ADR-004） |
| **开局** | `game.setup` | `RoleAssigner` | 发牌 |
| **夜晚** | `game.night` | `NightActions`, `NightSkillPipeline`, `NightResolver`, `WolfVoteResolver` | 夜技能与结算 |
| **胜负** | `game.win` | `WinChecker`, `GameOutcome` | 屠边 |
| **放逐** | `game.exile` | `ExileResolver` | 白天投票 |
| **猎人** | `game.hunter` | `HunterShootFlow` | 开枪 |
| **遗言** | `game.lastwords` | `LastWordsFlow` | R24 |
| **死亡** | `game.death` | `DeathBus`, subscribers | 死亡总线 |
| **模型** | `game.model` | `GameRoomState`, `GamePhase`, … | DTO |
| **HTTP** | `game.api` | `InternalGameController` | 内部 REST（Week1） |
| **可观测** | `game.observability` | `ActionLogService` | `action_log`（Memory 投影源） |

```text
game.api → game.engine | game.orchestration
game.engine → setup | sync | win | night | death | hunter | exile | lastwords | model
game.orchestration → game.engine | ai.api
game.view → sync
ai.* → game.view | game.engine（只读）| game.model
```

内部设计见 [ADR-001](../adr/001-night-skill-pipeline.md)、[ADR-002](../adr/002-death-bus-and-hunter-flow.md)。

---

## `com.werewolfengine.ai`

| 功能 | 子包 | 类 | 说明 |
|------|------|-----|------|
| **门面** | `ai.api` | `AIService` | `decide(room, seat)` → `PlayerIntent` |
| **契约** | `ai.api` | `PlayerIntent` | → `GameActionCommand` |
| **Agent** | `ai.agent` | `AiAgent` | 单座 Persona |
| **记忆** | `ai.memory` | `MemoryPromptFormatter` | Prompt「本局记忆」段；投影见 `game.view` |
| **感知** | `ai.perceive` | `GameViewContext` | 委托 `GameViews.forSeat` |
| **提示词** | `ai.prompt` | `AiPromptBuilder`, `Persona` | 记忆 + 当前局面 + 合法 action |
| **解析** | `ai.parse` | `AiIntentParser`, `AiActionJson` | LLM → intent（重试 0） |
| **策略** | `ai.policy` | `MockAIPlayer` | Mock / fallback |
| **校验** | `ai.guard` | `AiLegalActions` | 合法 action |
| **工具** | `ai.tools` | `GameTools` | Phase B，主路径未接 |
| **配置** | `ai.config` | `AiProperties`, `AiConfiguration` | `werewolf.ai.*` |

```text
game (AiTurnCoordinator) → ai.api.AIService
ActionLogService → game.view.SeatPerceptionProjector → ai.memory.MemoryPromptFormatter
ai.api → agent | memory | perceive | prompt | parse | guard | policy | config
ai 不依赖 gateway
```

### 数据流（M3）

```text
action_log → SeatPerceptionProjector → MemoryPromptFormatter → 「本局记忆」
GameViews → GameViewContext → 「当前局面」
→ AiPromptBuilder → ChatModel → AiIntentParser → PlayerIntent
```

编排见 [ADR-003](../adr/003-ai-integration.md)、[ADR-004](../adr/004-ai-seat-memory.md)。

### Game ↔ AI 调用链

```text
Gateway tick → GamePhaseScheduler → AiTurnCoordinator → AIService.decide
  → GameEngineService.submitAction → GameStateMachine
```
