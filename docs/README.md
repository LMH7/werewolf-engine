# werewolf-engine 文档索引

**入口页。** 文档按职能分子目录；ADR 保持独立四篇。

| 类别 | 文档 | 说明 |
|------|------|------|
| **需求** | [progress/requirements-mvp-v0.1.md](progress/requirements-mvp-v0.1.md) | 规则、协议、验收（**唯一基线**） |
| **架构** | [architecture/architecture-design-spec.md](architecture/architecture-design-spec.md) · [architecture/tech-selection-feasibility.md](architecture/tech-selection-feasibility.md) | 系统架构、技术选型 |
| **ADR** | [adr/001](adr/001-night-skill-pipeline.md) · [002](adr/002-death-bus-and-hunter-flow.md) · [003](adr/003-ai-integration.md) · [004](adr/004-ai-seat-memory.md) | 设计决策（夜内/死亡/AI/Memory） |
| **参考** | [reference/code-modules.md](reference/code-modules.md) · [reference/gateway-integration.md](reference/gateway-integration.md) | 包结构、Gateway/Bot 联调 |
| **进度** | [progress/status.md](progress/status.md) | 实现状态与待办 |
| **本地环境** | [developer-local-setup.md](developer-local-setup.md) | JDK、Docker、API Key |

## 谁读什么

| 角色 | 顺序 |
|------|------|
| 新人 | 本页 → PRD §0～2 → 架构 §1～3 → [code-modules](reference/code-modules.md) |
| A（game/ai） | PRD §3～4.5 → ADR 001～004 → [status](progress/status.md) |
| B（gateway） | PRD §4.2、§4.6～4.7 → [gateway-integration](reference/gateway-integration.md) → ADR-003 §7 |
| C（bot） | [gateway-integration](reference/gateway-integration.md) → [bot/README](../bot/README.md) |

Cursor 规则：[werewolf-engine-context.mdc](../.cursor/rules/werewolf-engine-context.mdc)
