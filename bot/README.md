# Werewolf Bot（C — 联调客户端）

Python 3.11+ 脚本，通过 **internal HTTP** 驱动 12 座 AI 对局（PRD §6.3）。不实现游戏规则。

## 安装

```powershell
cd bot
pip install -r requirements.txt
```

## 一键整局（后端 Mock + 可选 LLM fallback）

```powershell
$env:WERWOLF_BASE_URL = "http://localhost:8080"
python auto_play_client.py
```

等价于调用 `POST /internal/game/rooms` → `start` → `mock-auto-play`。

## 12 路逐步推进（模拟网关定时 tick）

```powershell
python tick_play_client.py --room-id r_demo
```

每轮调用 `POST .../phase-tick`，直到 `GAME_OVER` 或步数上限。

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `WERWOLF_BASE_URL` | `http://localhost:8080` | 引擎 base URL |
| `WERWOLF_MAX_TICKS` | `10000` | `tick_play_client` 安全上限 |

正式联调：改为连接 B 的 `ws://host/ws/game?token=...`，收到 `PHASE_SYNC` 后发送 `GAME_ACTION`（见 [gateway-integration.md](../docs/reference/gateway-integration.md)）。
