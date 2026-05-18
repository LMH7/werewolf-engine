# 项目状态（实现进度）

| 属性 | 值 |
|------|-----|
| 更新 | 2026-05-18 |
| 需求基线 | [PRD v1.0.14](requirements-mvp-v0.1.md) |
| ADR | [adr/](../adr/) · [code-modules](../reference/code-modules.md) |

细节规则见 PRD；包结构见 [code-modules](../reference/code-modules.md)。

---

## 结论（一句话）

**`game` 内存态主路径可测，Mock/LLM 整局与 Seat Memory 已闭环；上线阻塞在 Gateway/Room/WS 与持久化。**

| 维度 | 状态 |
|------|------|
| 状态机（夜/昼/死/猎/愚/遗言/屠边） | 基本完成 |
| `action_log`、AI LLM 骨架 | 部分 |
| `gateway` / `room` / MySQL 热路径 | 未开始 |
| Seat Memory（M3） | **已实现**（[ADR-004](../adr/004-ai-seat-memory.md)） |

测试（2026-05-17）：`mvnw.cmd test` → 34 方法 / 38 执行，0 失败。

---

## 已完成要点

- PRD v1.0.13、架构/选型/设计文档、Bot 脚手架
- `GameStateMachine` 全阶段 + DeathBus + 遗言 R24
- `AiTurnCoordinator`、`GameViews`、`ActionLogService`
- `MockGameRunner` + `MockAIFullGameTest`（A-01）
- LangChain4j + DeepSeek（`wolves-only` 可仅狼夜）

阶段流参见 [code-modules](../reference/code-modules.md) 与 PRD §4.3.2。

---

## 待办（按优先级）

### 引擎（A）

| ID | 项 | 状态 |
|----|-----|------|
| G-02 | 阶段超时 + WS 定时 | 骨架在，无 WS |
| G-03 | `GAME_EVENT` 广播 | 未发 |
| G-04 | `action_log` 持久化 | 仅内存 |
| G-06 | 狼夜超时随机刀 | 未做 |
| A-02-M3 | Seat Memory + SM 补 log P0 | **已完成** |
| A-02 P1 | 全角色 LLM + JSON 95% | 待做 |

### 网关 / Bot（B / C）

| ID | 项 | 状态 |
|----|-----|------|
| P-01～P-03 | WS、room、Redis | 未开始 |
| P-04 | 12 Bot 联调 | 脚手架 |
| P-05 | MySQL 对局 | 未接热路径 |

### PRD P0 验收

| 项 | 当前 |
|----|------|
| Mock 无人干预整局 | 内存 **是** / WS **否** |
| `PHASE_SYNC` 倒计时 | **否** |
| 非法操作不污染状态 | **是**（部分单测） |

---

## Post-MVP / 后续功能（不挡上线）

| ID | 项 | 说明 |
|----|-----|------|
| **A-03** | **狼队两阶段夜**：先商议再 `KILL` | 期望：四狼在 `NIGHT_WOLF` 内先轮流/多轮 `WOLF_CHAT`（定刀口、起跳、跟票、倒钩等），全员确认后再进入刀票；与现实现（聊与刀同一阶段、各狼独立 `KILL`、R10 聚合）不同。**MVP 保持 PRD R10/R11/R17a 现状**。 |

---

## 建议顺序

1. P-01 + P-02 + G-02（WS + 定时 tick）
2. P-04 Bot 压测
3. G-03 + G-04 持久化
4. A-02 P1 全角色 LLM
5. （后续）A-03 狼队商议阶段

---

## 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05-18 | 精简进度页；包结构迁至 `reference/code-modules` |
| v1.1 | 2026-05-18 | ADR 保持四篇独立；删除合并稿与重定向 stub |
| v1.2 | 2026-05-18 | M3 标完成；Post-MVP 登记 A-03 狼队两阶段商议 |
