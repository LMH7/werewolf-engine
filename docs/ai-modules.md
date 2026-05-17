# AI 模块功能分类（`com.werewolfengine.ai`）

| 功能 | 子包 | 类 | 说明 |
|------|------|-----|------|
| **门面** | `ai.api` | `AIService` | 唯一对外入口：`decide(room, seat)` → `PlayerIntent` |
| **契约** | `ai.api` | `PlayerIntent` | 意图 DTO，经 `game` 转为 `GameActionCommand` |
| **Agent** | `ai.agent` | `AiAgent` | 单座逻辑实例（Persona；Memory 预留 Week2+） |
| **感知** | `ai.perceive` | `GameViewContext` | Prompt 用局况快照，委托 `game.view.GameViews` |
| **提示词** | `ai.prompt` | `AiPromptBuilder`, `Persona` | System/User 拼装、性格枚举 |
| **解析** | `ai.parse` | `AiIntentParser` | LLM 文本 → `PlayerIntent`（重试 0） |
| **解析模型** | `ai.parse.model` | `AiActionJson` | PRD §4.5.3 冻结 JSON DTO |
| **策略** | `ai.policy` | `MockAIPlayer` | 无 LLM 规则/随机；LLM 失败兜底 |
| **校验** | `ai.guard` | `AiLegalActions` | 合法 action 集合与 LLM 意图预检 |
| **工具** | `ai.tools` | `GameTools` | LangChain4j `@Tool`（Phase B，主路径未接） |
| **配置** | `ai.config` | `AiProperties`, `AiConfiguration` | `werewolf.ai.*` |

## 依赖方向

```text
game (AiTurnCoordinator) → ai.api.AIService
ai.api → agent | perceive | prompt | parse | guard | policy | config
ai.tools → guard | perceive | game (只读)
ai 不依赖 gateway
```

## 数据流

```text
GameViews.forSeat (game.view)
  → GameViewContext (perceive)
  → AiPromptBuilder (prompt) + AiLegalActions.allowed (guard)
  → ChatModel → AiIntentParser (parse)
  → AiLegalActions.isLegal (guard)
  → PlayerIntent (api)
```

编排（选座、tick、`handleAction`）在 **`game`** 包，见 [ADR-003](adr/003-ai-integration.md)。
