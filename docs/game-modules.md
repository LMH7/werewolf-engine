# Game 模块功能分类（`com.werewolfengine.game`）

| 功能 | 子包 | 类 | 说明 |
|------|------|-----|------|
| **门面** | `game.engine` | `GameEngineService` | B 侧 HTTP/网关入口：建房、提交动作、tick、Mock 自动对局 |
| **状态机** | `game.engine` | `GameStateMachine` | 权威局况与阶段流转；`handleAction` / `advanceDayAnnounce` |
| **编排** | `game.orchestration` | `AiTurnCoordinator`, `TurnActorResolver` | AI 选座与 `handleAction` 桥接（ADR-003） |
| **编排** | `game.orchestration` | `GamePhaseScheduler` | 网关 `phase-tick`：定时阶段推进 |
| **编排** | `game.orchestration` | `MockGameRunner` | 开发/压测：自动跑到 `GAME_OVER` |
| **同步** | `game.sync` | `PhaseSyncBuilder` | 按座位构建 `PhaseSyncPayload`（网关可见性） |
| **视图** | `game.view` | `GameViews`, `GameView` | AI/观测用局况视图（与 PhaseSync 同规则） |
| **开局** | `game.setup` | `RoleAssigner` | `ROLE_ASSIGN` 发牌 |
| **夜晚** | `game.night` | `NightActions`, `NightSkillPipeline` | 狼/预/女巫技能管线 |
| **夜晚** | `game.night` | `NightResolver`, `WolfVoteResolver` | 夜杀结算、狼刀票决 |
| **胜负** | `game.win` | `WinChecker`, `GameOutcome` | 屠边判定与终局同步 |
| **放逐** | `game.exile` | `ExileResolver` | 白天投票放逐 |
| **猎人** | `game.hunter` | `HunterShootFlow` | 猎人开枪 |
| **遗言** | `game.lastwords` | `LastWordsFlow` | 出局遗言 |
| **死亡** | `game.death` | `DeathBus`, subscribers | 死亡事件总线 |
| **模型** | `game.model` | `GameRoomState`, `GamePhase`, … | 局况 DTO 与动作枚举 |
| **HTTP** | `game.api` | `InternalGameController` | 内部 REST（Week1 临时） |
| **可观测** | `game.observability` | `ActionLogService` | 动作/系统阶段日志 |

## 依赖方向

```text
game.api → game.engine | game.orchestration
game.engine → setup | sync | win | night | death | hunter | exile | lastwords | model
game.orchestration → game.engine | ai.api
game.view → sync（规则对齐，供 ai.perceive 使用）
ai.* → game.view | game.engine（只读工具）| game.model
```

## 与 AI 的边界

```text
Gateway tick → GamePhaseScheduler → AiTurnCoordinator → AIService.decide
  → GameEngineService.submitAction → GameStateMachine
```

详见 [ADR-003](adr/003-ai-integration.md)、[ai-modules.md](ai-modules.md)。
