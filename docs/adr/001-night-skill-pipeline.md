# ADR-001: Night skill pipeline and role handlers (P1)

| 属性 | 值 |
|------|-----|
| 状态 | 已采纳（Accepted，窄版范围见 PRD §4.3.8.4） |
| 日期 | 2026-05-16 |
| 决策者 | 游戏引擎（A） |
| 关联 | [PRD §4.3.8.4](../requirements-mvp-v0.1.md)、[ADR-002](002-death-bus-and-hunter-flow.md)、[architecture-design-spec.md](../architecture-design-spec.md) |

## 背景

MVP 使用细粒度 `GamePhase`（`NIGHT_WOLF`、`NIGHT_SEER`、`NIGHT_WITCH` 等）驱动 `PHASE_SYNC`、倒计时与定向可见性（PRD §4.3.3、§4.3.7）。夜内推进目前集中在 `GameStateMachine` 的 switch-case 中。

未来若增加守卫、骑士等角色，若继续「每角色改一张巨型状态图」，维护成本高。产品层可简化为宏观 **NIGHT / DAY**，但**对外协议**仍需可观测的子阶段名。

## 决策

1. **保留 `GamePhase` 作为 WS / `PHASE_SYNC` 的协议真源**；不因宏观叙事合并为仅 `NIGHT`、`DAY` 两个枚举（除非同步修订 payload 并增加 `nightStep` 等子字段，属破坏性变更）。
2. **夜内逻辑**在 P1 抽为 **`NightSkillPipeline`**：有序 **`RoleSkillHandler`** 列表 + 阶段末 **`NightResolver`** 死亡结算。
3. **屠边判定（R23）**横切挂在 **死亡事件之后** 与 **离开死讯公布之前**，由 `WinChecker` / `tryEndGame` 统一调用，与具体角色 handler 无关。
4. **新角色**通过注册 handler、调整优先级表、扩展 `NightResolver` 输入实现；**不要求**修改 PRD 顶层宏观状态图。

## 结构（目标）

```text
GameStateMachine
  └── NightSkillPipeline.run(room)
        ├── handlers[]  // 按 board 配置排序，例：Wolf → Seer → Witch → (Guard…)
        │     each: supports(phase) / onEnter / onAction / onTimeout / onExit
        └── finishNight → NightResolver → DeathBus.apply (R23，见 ADR-002)
```

```text
Day 侧（MVP 不变）
  DAY_DISCUSS → DAY_VOTE → VOTE_RESULT → EXILE_DEATH_ANNOUNCE? → HUNTER_SHOOT? → CHECK_WIN
```

## 与 MVP 的边界

| 项 | 迁移前 | 窄版（v1.0.11） |
|----|--------|----------------|
| `GamePhase` 枚举 | 已冻结 v1.0.9 | **不删**阶段名 |
| 夜内推进 | `GameStateMachine` switch | `NightSkillPipeline` + 3 handlers，SM 委托 |
| 夜末死亡 / R23 | SM 内 `tryEndGame` | **ADR-002** `DeathBus` |
| 猎人 | SM 内分散逻辑 | **ADR-002** `HunterShootFlow`（**不**进 pipeline） |
| 愚者 | 投票分支 | **无** IdiotHandler |
| 守卫/骑士 | 无 | 宽版：handler + 延迟伤害（另 ADR） |

## 后果

### 正面

- 加角色时改 **配置表 + handler**，而非改整张 Mermaid / 巨型 switch。
- R23 检测点集中在一处（死亡后、`tryEndGame`），降低「先开枪后判胜」类 bug。

### 负面 / 风险

- 一次性迁移 switch-case 有回归成本；须保持现有 `GameStateMachineTest` / 夜序测试绿。
- Handler 顺序错误会导致规则 bug；须用板子配置单测锁定顺序（与 R10、夜序预→女一致）。

## 验收（P1 落地时）

- [ ] `NightSkillPipeline` 单元测试：预女猎愚板顺序与 MVP 行为一致。
- [ ] 夜末、公布前 R23 仍仅在规定检测点触发（§3.2.1）。
- [ ] `PHASE_SYNC.currentPhase` 序列与 v1.0.9 协议图一致。

## 参考实现（当前）

- [`GameStateMachine.java`](../../src/main/java/com/werewolfengine/game/GameStateMachine.java) — `finishNightAfterWitch`、`advanceNightDeathAnnounce`、`tryEndGame`
- [`NightResolver.java`](../../src/main/java/com/werewolfengine/game/NightResolver.java)
- [`WinChecker.java`](../../src/main/java/com/werewolfengine/game/WinChecker.java)
