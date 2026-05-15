# 开发者本地环境与基础设施说明

| 属性 | 值 |
|------|-----|
| 版本 | v1.0 |
| 日期 | 2026-05-16 |
| 读者 | 后端 / 全栈开发人员、小组技术成员 |
| 关联 | [PRD v1.0.4](requirements-mvp-v0.1.md)、[技术选型 v0.1.5](tech-selection-feasibility.md)、[架构说明 v1.3](architecture-design-spec.md) |

本文档整理 **MVP 工程落地阶段** 中团队曾讨论过的技术疑问与最终决策，并给出 **可复制的本地启动步骤**。若与 PRD 冲突，以 PRD 为准；本文侧重「怎么在本机跑起来」。

---

## 1. 决策摘要（曾讨论问题的结论）

### 1.1 基础设施：Docker 里跑什么？

| 组件 | 是否进 Docker | 说明 |
|------|----------------|------|
| **MySQL 8** | ✅ | 持久化：用户、房间、对局、`action_log` |
| **Redis 7** | ✅ | WS 会话映射、房间集合等（MVP **无**「无 Redis 内存降级」） |
| **Ollama** | ❌ 已移除 | 本地推理慢，不再作为开发 LLM |
| **应用 JAR** | ❌ | 本机 `mvnw spring-boot:run` 或 IDE 启动 |

配置文件：仓库根目录 [`docker-compose.yml`](../docker-compose.yml)。

### 1.2 LLM：还用千问 / 百炼吗？能用 DeepSeek 官方 API 吗？

**结论：dev 与 prod 统一走 DeepSeek 官方 OpenAI 兼容 API，不必经过千问 / 阿里云百炼。**

| 问题 | 答案 |
|------|------|
| 千问平台上架的 DeepSeek 能用吗？ | 可以，但属于**阿里云托管路由**，模型 ID、计费、延迟可能与官方不一致，且与 PRD「dev/prod 同端点」目标冲突。 |
| 能否直连 DeepSeek？ | **推荐**。LangChain4j 使用 `langchain4j-open-ai-spring-boot4-starter`，只需配置 `base-url` + `api-key`。 |
| 默认模型 | `deepseek-v4-flash`（多轮对局，延迟与成本更优） |
| 更强推理 | 可改为 `deepseek-v4-pro`（**整局仍须单模型**，见 PRD §4.5.6） |
| API Key 申请 | https://platform.deepseek.com/api_keys |
| 官方文档 | https://api-docs.deepseek.com/ |

### 1.3 密钥与 Profile：写在哪里？

**结论：写入 Windows 用户/系统环境变量，由 Spring 占位符读取；不要把 Key 提交进 Git。**

| 变量名 | 用途 | 写入方式 |
|--------|------|----------|
| `DEEPSEEK_API_KEY` | LangChain4j 调用 DeepSeek | `scripts/set-dev-env.ps1` 或系统「环境变量」面板 |
| `SPRING_PROFILES_ACTIVE` | 激活 `dev` profile | 脚本一并写入；未设置时 `application.properties` 默认 `dev` |

`.env` 已列入 [`.gitignore`](../.gitignore)，请勿将真实 Key 提交仓库。

### 1.4 LangChain4j 版本与 Spring Boot 4

PRD 早期写的是 LangChain4j `0.31.x`；当前仓库使用 **Spring Boot 4.0.6**，须使用 **`*-spring-boot4-starter`** 后缀（与 Boot 3 的 `*-spring-boot-starter` **不可混用**）。

| 依赖 | 版本（`pom.xml`） |
|------|-------------------|
| `langchain4j-spring-boot4-starter` | `1.15.0-beta25` |
| `langchain4j-open-ai-spring-boot4-starter` | 同上 |

详见架构说明 §9.1 与 [LangChain4j Spring 集成文档](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/spring-boot-integration.md)。

### 1.5 单测为何不需要 Docker？

`src/test/resources/application.properties` 排除了 DataSource / JPA / Redis / LangChain4j 自动配置，因此 `mvn test` 可在**无** MySQL、Redis、API Key 的情况下通过冒烟测试。集成联调仍需 Docker + 环境变量。

---

## 2. 环境要求

| 项 | 版本 / 说明 |
|----|-------------|
| JDK | **21**（虚拟线程：`spring.threads.virtual.enabled=true`） |
| Maven | 使用仓库自带 `./mvnw.cmd` |
| Docker Desktop | 用于 MySQL + Redis（Windows 需托盘显示 Running） |
| DeepSeek 账号 | 用于申请 `DEEPSEEK_API_KEY` |

---

## 3. Maven 依赖一览

当前 [`pom.xml`](../pom.xml) 已引入（实现业务前即可编译）：

| Starter / 依赖 | 用途 |
|----------------|------|
| `spring-boot-starter-web` | HTTP API |
| `spring-boot-starter-websocket` | 原生 WebSocket（非 STOMP） |
| `spring-boot-starter-data-jpa` | MySQL |
| `spring-boot-starter-data-redis` | Redis |
| `mysql-connector-j` | MySQL 驱动 |
| `langchain4j-spring-boot4-starter` | `@AiService` 等 |
| `langchain4j-open-ai-spring-boot4-starter` | OpenAI 兼容客户端（指向 DeepSeek） |

**未引入**：`langchain4j-ollama-spring-boot4-starter`（已按团队决策移除）。

---

## 4. Docker 启动（MySQL + Redis）

### 4.1 启动

在项目根目录执行：

```powershell
cd g:\Code\werewolf-engine
docker compose up -d
```

首次会拉取镜像，需等待数分钟。MySQL 健康检查通过前约需 30～60 秒。

### 4.2 常用命令

```powershell
docker compose ps          # 状态（期望 mysql / redis 为 healthy）
docker compose logs -f     # 日志
docker compose stop        # 停止
docker compose down        # 停止并删除容器
docker compose down -v     # 连同数据卷删除（清空库）
```

若本机已安装 `docker-compose`（带连字符），可替换为 `docker-compose up -d`。

### 4.3 连接信息（与 `application-dev.properties` 一致）

| 服务 | 地址 | 凭证 |
|------|------|------|
| MySQL | `localhost:3306`，库 `werewolf` | 用户 `werewolf` / 密码 `werewolf` |
| Redis | `localhost:6379` | 无密码 |

端口冲突时：关闭本机已有 MySQL/Redis，或修改 `docker-compose.yml` 端口映射。

---

## 5. 环境变量配置

### 5.1 推荐：PowerShell 脚本（持久化）

```powershell
cd g:\Code\werewolf-engine
.\scripts\set-dev-env.ps1 -ApiKey "sk-你的DeepSeek密钥"
```

- 默认写入 **当前用户** 环境变量（`User` 作用域）。
- 系统级（所有用户）：管理员 PowerShell 执行 `-Scope Machine`。

**写入后必须重启终端 / IDE**，新进程才能读到变量。

### 5.2 手动：Windows 图形界面

「设置 → 系统 → 关于 → 高级系统设置 → 环境变量」中新建：

- `DEEPSEEK_API_KEY` = `sk-...`
- `SPRING_PROFILES_ACTIVE` = `dev`（可选；未设置时应用默认 `dev`）

### 5.3 配置如何被应用读取

| 文件 | 关键项 |
|------|--------|
| [`application.properties`](../src/main/resources/application.properties) | `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}` |
| [`application-dev.properties`](../src/main/resources/application-dev.properties) | `langchain4j.open-ai.chat-model.api-key=${DEEPSEEK_API_KEY}` 等 |

LLM 相关片段（dev）：

```properties
langchain4j.open-ai.chat-model.api-key=${DEEPSEEK_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.deepseek.com/v1
langchain4j.open-ai.chat-model.model-name=deepseek-v4-flash
langchain4j.open-ai.chat-model.timeout=3s
```

切换为 Pro 模型：仅改 `model-name=deepseek-v4-pro`（或通过后续配置中心 / profile 拆分，须保持单局单模型）。

---

## 6. 本地完整启动顺序（Checklist）

```text
[ ] 1. 安装并启动 Docker Desktop
[ ] 2. docker compose up -d
[ ] 3. docker compose ps   → mysql、redis 为 healthy
[ ] 4. 设置 DEEPSEEK_API_KEY（脚本或系统环境变量）
[ ] 5. 重启 IDE / 终端
[ ] 6. .\mvnw.cmd spring-boot:run
[ ] 7. （可选）.\mvnw.cmd test
```

预期：应用以 `dev` profile 启动，连接本机 MySQL/Redis，LLM 请求发往 `api.deepseek.com`。

---

## 7. 与 PRD / 文档版本对应关系

| 主题 | 本文 | PRD / 技术选型 |
|------|------|----------------|
| LLM 厂商 | DeepSeek 官方 | PRD v1.0.4 §0.5、§4.5.6 |
| 移除 Ollama / 百炼 dev 分叉 | §1.1、§1.2 | 技术选型 v0.1.5 §4.6 |
| Docker 仅 MySQL + Redis | §4 | 架构 v1.3 §10.2 |
| 单局单厂商单模型 | §1.2 | PRD §4.5.6（禁止同局混用多厂商） |
| LLM 3s 超时 + fallback | `timeout=3s` | PRD 非功能 / §4.5 |

---

## 8. 常见问题（FAQ）

**Q: `mvn test` 通过，但 `spring-boot:run` 启动失败报数据源错误？**  
A: 未启动 Docker，或 MySQL 尚未 healthy；确认 `docker compose ps`。

**Q: 启动报 `DEEPSEEK_API_KEY` 相关错误？**  
A: 环境变量未设置或未重启 IDE；执行 `set-dev-env.ps1` 后重开终端。可在 PowerShell 验证：`[Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY','User')`

**Q: 能否继续用 Ollama 省 API 费用？**  
A: 当前仓库**已移除** Ollama 依赖与 compose 服务；若需恢复须经 PRD 版本评审。Week1 仍可用 **Mock AI**（不调用 LLM）跑通状态机。

**Q: macOS / Linux 开发者怎么办？**  
A: `docker compose` 命令相同；环境变量改为 `export DEEPSEEK_API_KEY=...`、`export SPRING_PROFILES_ACTIVE=dev`，或写入 `~/.bashrc` / `~/.zshrc`。暂无 bash 版 `set-dev-env` 脚本，可自行对照 `scripts/set-dev-env.ps1` 逻辑。

**Q: 生产环境 Key 怎么管？**  
A: 使用部署平台密钥注入（K8s Secret、云厂商环境变量等），**不要**把 prod Key 写入 `application-*.properties` 提交 Git；prod profile 可后续单独新增 `application-prod.properties` 模板（仅占位符）。

---

## 9. 变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-05-16 | 初稿：整合 Docker、DeepSeek API、环境变量、Ollama/千问决策与本地启动清单 |

---

*文档结束*
