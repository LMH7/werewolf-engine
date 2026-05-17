# werewolf-engine 项目进度报告

| 属性 | 值 |
|------|-----|
| 版本 | v0.3 |
| 更新日期 | 2026-05-17 |
| 需求基线 | [requirements-mvp-v0.1.md](requirements-mvp-v0.1.md) **v1.0.12**（含 R24 遗言） |
| 架构对照 | [architecture-design-spec.md](architecture-design-spec.md) v1.4 |
| **AI 设计** | [adr/003-ai-integration.md](adr/003-ai-integration.md) **已冻结（Accepted）** |

---

## 0. AI 设计状态

| 项 | 状态 |
|----|------|
| 需求与架构 ADR | **已冻结** — [ADR-003](adr/003-ai-integration.md)（§10 七项决议） |
| 编排 | `AiTurnCoordinator` + `TurnActorResolver`（`MockGameRunner` 为 dev 压测别名） |
| 视图 | `GameViews` / `GameView` 与 `PhaseSyncBuilder` 共用 `canAct` |
| Week2 LLM | P0.5 狼夜 → P1 全角色；见 ADR §7 |

---

## 1. 总体结论

**`game` 模块：状态机主路径与 12 人板核心机制已在内存态实现并可测。A 侧「内存 Mock 无人干预整局」已通过 `MockGameRunner` + 单测闭环；PRD P0 完整验收仍依赖 B/C 的 WS、房间与 12 Bot 联调。**

| 维度 | 状态 |
|------|------|
| 游戏状态机主干（夜/昼/死/猎/愚/遗言/屠边） | **基本完成** |
| 规则边角、超时调度、事件与日志 | **部分**（`action_log` 内存版已接；无网关定时） |
| `gateway` / `room` / 持久化热路径 | **未开始** |
| `bot/` 联调脚手架 | **已提供**（Python 客户端 + internal HTTP） |
| 全自动 Mock 整局（内存） | **已闭环** |
| LangChain4j + DeepSeek | **骨架已接**（Mock fallback；可配置全角色或仅狼夜） |

本地验证（2026-05-17）：`mvnw.cmd test` → **34** 个测试方法、**38** 次执行（含 `MockAIFullGameTest` 的 `@RepeatedTest(5)`），**0** 失败。

---

## 2. 已完成（`game` + `ai` + 文档）

### 2.1 文档与规范

- MVP PRD v1.0.12（R1～R24、§4.3.8 引擎窄版）
- 架构设计说明书 v1.4、技术选型、ADR-001/002、本地环境说明
- [gateway-bot-integration.md](gateway-bot-integration.md) — B/C 与 `GamePhaseScheduler`、internal API 契约
- Cursor 项目上下文规则（`.cursor/rules/werewolf-engine-context.mdc`）

### 2.2 工程骨架

- Java 21 + Spring Boot 4.0.6
- 依赖：Web、WebSocket、JPA、Redis、LangChain4j（**AI 已接 DeepSeek**）
- `docker-compose`：MySQL 8 + Redis 7
- 包结构：`game`（见 [game-modules.md](game-modules.md)）、`ai`（见 [ai-modules.md](ai-modules.md)）、`message`、`bot/`（Python 联调）；**无** `gateway`、`room` Java 包

### 2.3 状态机与 `GamePhase`

已实现并可测试的阶段流转（内存 `game.engine.GameStateMachine`）：

```text
WAITING → ROLE_ASSIGN → NIGHT_START → NIGHT_WOLF → NIGHT_SEER → NIGHT_WITCH
  → [夜末结算 / R23] → NIGHT_DEATH_ANNOUNCE → [LAST_WORDS 首夜有条件]
  → [HUNTER_SHOOT] → DAY_DISCUSS → DAY_VOTE → VOTE_RESULT
  → [EXILE_DEATH_ANNOUNCE] → [LAST_WORDS 放逐] → [HUNTER_SHOOT] → CHECK_WIN
  → 下一夜 / GAME_OVER
```

### 2.4 核心机制（对照 PRD）

| 能力 | 实现落点 | 备注 |
|------|----------|------|
| 12 人发牌（4 狼 4 民 1 愚 1 预 1 女 1 猎） | `RoleAssigner` | 随机洗牌 |
| 狼刀跟票 R10、刀狼商议 R17a | `NightActions`、`WolfVoteResolver` | |
| 预言家查验 R12 | `NightActions` | GOOD/WOLF |
| 女巫救/毒/跳过 R3/R4/R5 | `NightActions`、`NightResolver` | 同夜不可救+毒 |
| 夜末死亡结算 | `NightResolver` + `DeathBus` | |
| 天亮/放逐死讯公布 | `NIGHT_DEATH_ANNOUNCE`、`EXILE_DEATH_ANNOUNCE`、`advanceDayAnnounce` | |
| 遗言 R24 | `LastWordsFlow`、`GamePhase.LAST_WORDS` | 首夜死者 / 放逐者 |
| 猎人开枪 R7/R8/R9 | `HunterShootFlow`、`HunterPendingSubscriber` | 毒杀不开枪 |
| 白天讨论 R13 | `enterDayDiscuss`、发言顺序 | 锚点 + 顺/逆时针 |
| 投票、平票 R14 | `resolveDayVote` | 最高票唯一才放逐 |
| 愚者翻牌 R19～R22 | `ExileResolver` | 翻牌后进入下一夜 |
| 屠边胜负 R1/R21、即时判胜 R23 | `WinChecker`、`GameOutcome.tryEndGame` | 含最后一神猎人被票 |
| 定向 `PHASE_SYNC` 骨架 | `PhaseSyncBuilder` | 子集字段 |
| 临时 HTTP 联调 | `InternalGameController`、`GameEngineService` | 无鉴权，非对外协议 |
| 阶段推进（网关/Bot 用） | `GamePhaseScheduler` | 死讯 `advanceDayAnnounce` + 可选 AI 单步 |
| 结构化 `action_log`（内存） | `ActionLogService` | 含 AI `thinking`（调试） |

### 2.5 AI 模块（按功能分子包，见 [ai-modules.md](ai-modules.md)）

| 子包 | 类 |
|------|-----|
| `ai.api` | `AIService`, `PlayerIntent` |
| `ai.agent` | `AiAgent` |
| `ai.perceive` | `GameViewContext` |
| `ai.prompt` | `AiPromptBuilder`, `Persona` |
| `ai.parse` / `ai.parse.model` | `AiIntentParser`, `AiActionJson` |
| `ai.policy` | `MockAIPlayer` |
| `ai.guard` | `AiLegalActions` |
| `ai.tools` | `GameTools`（Phase B） |
| `ai.config` | `AiProperties`, `AiConfiguration` |
| `game.orchestration` | `AiTurnCoordinator`, `TurnActorResolver`, `GamePhaseScheduler`, `MockGameRunner` |
| `game.view` | `GameViews`, `GameView` |

### 2.5.1 Game 模块（按功能分子包，见 [game-modules.md](game-modules.md)）

| 子包 | 类 |
|------|-----|
| `game.engine` | `GameEngineService`, `GameStateMachine` |
| `game.sync` | `PhaseSyncBuilder` |
| `game.setup` | `RoleAssigner` |
| `game.night` | `NightActions`, `NightSkillPipeline`, `NightResolver`, `WolfVoteResolver` |
| `game.win` | `WinChecker`, `GameOutcome` |
| `game.exile` / `game.hunter` / `game.lastwords` / `game.death` | 放逐、猎人、遗言、死亡总线 |

配置见 `application-dev.properties`：`langchain4j.open-ai.*` → DeepSeek；`werewolf.ai.*`。

### 2.6 测试与剧本

| 测试 | 说明 |
|------|------|
| `GameStateMachineTest`（13） | 狼夜、死讯、猎人、胜负、遗言、平票等 |
| `GameScenarioDemoTest`（3） | 多夜狼胜/好人胜剧本 |
| `GameFlowDemoTest`（1） | 第一夜控制台演示 |
| `MockAIFullGameTest`（5+1） | **A-01**：Mock 整局至 `GAME_OVER` |
| `AIServiceTest`（5） | LLM Mock、fallback、disabled |
| `AiIntentParserTest`（4） | JSON 解析 |
| `AIServiceLlmIntegrationTest`（1） | 需 `DEEPSEEK_API_KEY` |
| `WerewolfEngineApplicationTests`、`DevProfileStartupWithoutApiKeyTest` | Spring 上下文 |

运行示例：

```powershell
mvnw.cmd test
mvnw.cmd test -Dtest=MockAIFullGameTest
```

---

## 3. 未完成 / 待办（按优先级）

### 3.1 规则与引擎边角（A / `game`）

| ID | 项 | PRD/说明 | 状态 |
|----|-----|---------|------|
| G-01 | **R2** 最后一狼与最后一民同夜同死 → 狼赢 | §3.2 R2 | 未实现 / 无单测 |
| G-02 | **阶段超时与兜底** | §4.3.3 各阶段 countdown、超时默认行为 | `GamePhaseScheduler` 骨架在；**无** WS 定时推送 |
| G-03 | **`GAME_EVENT` 广播** | 如 `IDIOT_REVEALED` | `MessageType` 有定义，局内未发送 |
| G-04 | **`action_log` 持久化** | §4.7.3 | 内存 `ActionLogService` 已接；**未**写 MySQL |
| G-05 | **`SPEAK` / `WOLF_CHAT` 正文** | §4.5 | `GameActionCommand.content` **已加**；落库待 B |
| G-06 | **狼夜无票型时随机刀非狼** | §4.3.3 超时兜底 | SM 内未自动执行 |
| G-07 | **`DayResolver` 独立模块** | §4.4.3 | 逻辑在 SM + `ExileResolver`（能力等价） |
| G-08 | **`ROLE_ASSIGN` / `NIGHT_START` 节奏** | 对外推送与定时 | 开局快速掠过 |
| G-09 | **`CHECK_WIN` 对外语义** | 协议图 | 多数 R23 直接 `GAME_OVER` |
| G-10 | **座位与账号** | `userId`↔座位 | 仅 `PlayerState`，无 `room` 绑定 |

### 3.2 网关、房间、Bot（B / C — PRD P0 联调）

| ID | 项 | 负责人 | 状态 |
|----|-----|--------|------|
| P-01 | **`gateway`** 原生 WebSocket、`GAME_ACTION` / `PHASE_SYNC` 推送 | B | 未开始 |
| P-02 | **`room`** HTTP 建房、ready、12 人 | B | 未开始 |
| P-03 | **Redis** 连接映射 | B | 未开始 |
| P-04 | **`bot/`** 12 路并发、同房不串房 | C | **脚手架**（见 `bot/README.md`）；待 WS 联调 |
| P-05 | **MySQL** 对局持久化 | B | JPA 在，热路径未接 |

### 3.3 AI 与自动化（A — Week1/2）

| ID | 项 | 状态 |
|----|-----|------|
| A-01 | **12 座 Mock 自动打满一整局**（内存） | **已完成**（`MockGameRunner` + `MockAIFullGameTest`） |
| A-02 | **LangChain4j + DeepSeek** 全角色 AI | **骨架完成**；`wolves-only` 可仅狼夜；缺 Memory / 压测指标 |
| A-03 | **`GameView` / `legalActions`（§4.3.8 M4）** | **部分**（`GameViewContext` + `GameTools`；未与 SM handler 共用） |

### 3.4 PRD P0 验收项对照

| 验收项（§1.2 / §8） | 当前 |
|---------------------|------|
| 从发牌到胜负，**Mock 无人干预**可跑通一整局 | **内存：是**；**WS/Bot：否** |
| 存活玩家均能收到 `PHASE_SYNC`，倒计时误差 ≤ 1s | **否**（无 WS、无定时） |
| 非法操作返回 `ERROR`，不污染状态 | **是**（单测覆盖部分路径） |
| 场景 S2 纯 AI / 压测多局 | **部分**（`mock-auto-play` + Bot 脚本；缺报表） |

---

## 4. 建议实施顺序

1. **P-01 + P-02 + G-02**：网关 WS + 房间；定时器调 `GamePhaseScheduler` / `advanceDayAnnounce`。
2. **P-04**：Bot 从 internal HTTP 迁到 WS，12 路压测。
3. **G-03 + G-04 持久化**：`GAME_EVENT` + `game_record.action_log`。
4. **G-01 + G-06**：R2 单测、狼夜超时 Fallback。
5. **A-02 深化**：`ChatMemory`、50 次 JSON 成功率、全角色 LLM 稳定。

---

## 5. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.1 | 2026-05-17 | 初版：汇总 `game` 已完成项与 PRD P0 未完成项 |
| v0.2 | 2026-05-17 | A-01 闭环；AI 模块清单；测试 34/38；`action_log`、`GamePhaseScheduler`、`bot/`、`content` 字段 |
