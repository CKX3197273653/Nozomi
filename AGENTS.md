# AGENTS.md — Mate10 项目

## 项目概览

单一 git 仓库管理两个独立项目，**分别启动、分别构建**（不是 monorepo 共享构建）：

- **`mate10/`** — Spring Boot 4.0 后端（Maven / Java 17）
- **`mate10-V/`** — Vue 3 + Vite 前端（Node.js）
- **`milvus/`** — 空目录（历史遗留），Milvus 实际用 Docker 部署

---

## 后端 `mate10/`

### 环境要求

- JDK 17 = **Microsoft Build of OpenJDK 17**（本机 `JAVA_HOME=I:\JAVAgen`）
  >   **不能用 GraalVM**：Rerank 依赖的 `onnxruntime` 原生库在 GraalVM 的 `jvmci` 编译器下会报
  > `DLL初始化例程失败` / 崩溃，必须用标准 OpenJDK（HotSpot/C2）。详见「已知问题」。
- Maven 3.8+（使用 IDEA 内置 Maven：
  `I:\IDEA\IntelliJ IDEA 2023.3.3\plugins\maven\lib\maven3\bin\mvn.cmd`）

## 生产部署环境变量清单

### 数据库与中间件

MYSQL_URL=jdbc:mysql://<地址>:3306/mate10db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
MYSQL_USERNAME=<用户名>
MYSQL_PASSWORD=<强密码>
MONGODB_URI=mongodb://<用户>:<密码>@<地址>:27017/mate10db?authSource=admin
REDIS_HOST=<地址>
REDIS_PORT=6379
REDIS_PASSWORD=<密码>
KAFKA_SERVERS=<地址>:9092
MILVUS_HOST=<地址>
MILVUS_PORT=19530
MILVUS_USERNAME=<用户名>
MILVUS_PASSWORD=<密码>

>  **`application-prod.yml` 里这些变量【刻意不写默认值】** —— 缺失时**启动即失败**（fail-fast）。
> 这是有意设计：**宁可起不来，也不要静默连到错误的库**（见「环境维度差异 dev or prod」）。

### 文件处理

TESSERACT_PATH=<生产环境的 Tesseract 路径>
TESSERACT_DATA_PATH=<生产环境的 tessdata 路径>

### 安全（生产必须重新生成，不能复用开发的）

JWT_SECRET_KEY=<生产新生成的 64 字节密钥>
DEEPSEEK_API_KEY=<生产的新 key>
ZHIPU_API_KEY=<生产的新 key>

> ️ `JWT_SECRET_KEY` **必填且 ≥ 32 字节** —— 缺失或过短**启动即失败**
> （`Could not resolve placeholder 'JWT_SECRET_KEY'`；`JwtUtil` 的 `@PostConstruct` 会校验长度）。
>
> ️ cloud 模式下 `DEEPSEEK_API_KEY` / `ZHIPU_API_KEY` **必填** ——
> 缺失会导致 `ChatModel` / `EmbeddingModel` Bean 创建失败 → 启动失败。

### Rerank（可选，容器部署建议显式关掉）

APP_RAG_RERANK_ENABLED=false

> 不设则默认 `true`，而容器里没有模型文件 → 构造函数抛异常 → 容器反复重启。
> 关掉后 `HybridRetriever` 自动回退 RRF 融合，命中率从 **100% 降到 96.7%**。
>
> 若服务器性能足够、确实要开精排，改为**挂载模型 + 指定路径**：
>
> RERANK_MODEL_PATH=/app/models/bge-reranker-base/onnx/model_quantized.onnx
> RERANK_TOKENIZER_PATH=/app/models/bge-reranker-base/tokenizer.json

### Spring

SPRING_PROFILES_ACTIVE=prod,cloud

### 启动与构建

```bash
# 开发模式（热部署）
mvn spring-boot:run

# 打包跳过测试
mvn clean package -DskipTests
java -jar target/mate10-0.0.1-SNAPSHOT.jar
```

### AI 对话接口

| 端点                     | 说明             | 响应格式                |
|------------------------|----------------|---------------------|
| `POST /ai/chat`        | 创建会话，返回 chatId | `Result<Chat>`      |
| `POST /ai/chatRecord`  | 非流式对话（带记忆）     | `Result<String>`    |
| `POST /ai/chat/stream` | 流式对话（SSE）      | `text/event-stream` |

**记忆机制**：`ChatContextBuilder` 组装上下文 = SystemMessage + 最近 10 条历史（2000 字符预算）+ 当前问题。
历史存 MongoDB `chat_messages`，按 `chatId` 隔离。`chatId` 为 null 时降级为单轮对话。

**RAG 融合**：请求体带 `mode=qc` 时，自动检索知识库并把资料作为 SystemMessage 注入；
`mode=general` 为纯对话。

**流式约定（重要，改流式前必读）**：

- 后端在 **`finish()`** 中**统一**发送 `event:done` —— **所有终态都必须走它**：

  | 终态 | 触发点 |
  |---|---|
  | ① 正常答完 | `onCompleteResponse` |
  | ② 模型报错 | `onError` |
  | ③ 端点自身异常 | 外层 `catch` |
  | ④ 5 分钟超时 | `emitter.onTimeout` |

    - **只在 `onCompleteResponse` 的 finally 里发是【不够的】** ——
      ②③④ 三条路径都会让前端**永久卡在「思考中」**（曾经的真实 bug）
    - 用 `AtomicBoolean` + **CAS** 保证 done **只发一次**（终态可能竞态：超时与返回几乎同时）
    - `onError` 发给前端的消息按 **`app.error.detail`** 脱敏（dev 详情 / prod 只说"请稍后重试"），
      **完整原因只进日志** —— 与 `GlobalExceptionHandler` 同一套开关
    - `finish()` 会打一行 `[SSE] finish 收尾 -> 正常结束 / 异常: …` 日志 —— **验证时看它**
- 前端**不能依赖"连接关闭"判断流结束**——经 Vite 代理时关闭信号会丢失，界面会永久卡在"思考中"
- 前端用 `fetch` + `ReadableStream`（`EventSource` 无法携带 `Authorization` 头）
- 解析需按 `\n\n` 切分并维护缓冲区（chunk 是任意切分的，存在"粘包"）
- 后端模型调用必须放在独立线程（`ThreadPoolTaskExecutor`）执行，否则方法不返回、事件被缓冲、流式失效

### 中间件依赖（全部需要本地启动）

| 服务                      | 端口                  | 说明                                                   |
|-------------------------|---------------------|------------------------------------------------------|
| MySQL 8.0（Docker）       | 3306                | 业务/QC/聊天记录，默认库 `mate10db`                            |
| MongoDB 6（Docker）       | 27017               | 聊天消息                                                 |
| Redis 7（Docker）         | 6379                | 缓存（`requirepass` 见 `deploy/.env` 的 `REDIS_PASSWORD`） |
| Kafka 4.1（Docker，KRaft） | 宿主机 9094 / 容器内 9092 | 消息队列（无 Zookeeper）                                    |
| Milvus 2.x（Docker）      | 19530               | 向量库（standalone + etcd + minio，独立容器组 `milvus-*`）      |
| Ollama                  | 11434               | 本地模型服务（仅 ollama 模式需要）                                |
| Tesseract               | -                   | OCR（图片质检需要）                                          |

**中间件全部 Docker 化（2026-10-10 起）**，本机 Windows 服务 MySQL80 / MongoDB
已设为「手动」、本机 Redis 进程已停，**不要再启动**（会抢端口）。

```bash
# 启动中间件（在 deploy 目录）
docker compose up -d mysql mongo redis kafka
```

> Milvus 用另一套独立容器组（`milvus-standalone` / `milvus-etcd` / `milvus-minio`），
> 不在 `deploy/docker-compose.yml` 内（compose 里的 milvus 定义是生产部署用的）。
> 中间件密码等配置在 `deploy/.env`（已 gitignore，不提交）。
> 拉镜像若失败，用 daocloud 前缀拉取后 tag：`docker pull docker.m.daocloud.io/library/mysql:8.0 && docker tag ...`。

### AI 模型切换（ollama / cloud 双 profile）

通过 Spring Profile 一键切换整套 AI 能力，**无需改代码**：

|           | `ollama`（本地）           | `cloud`（云端）                  |
|-----------|------------------------|------------------------------|
| 对话模型      | deepseek-r1:latest     | DeepSeek API（deepseek-chat）  |
| Embedding | all-minilm（384 维）      | 智谱 embedding-3（2048 维）       |
| Milvus 集合 | `rag_documents`（384 维） | `rag_documents_2048`（2048 维） |

**切换方式**：IDEA 运行配置 → Active profiles 填 `ollama` 或 `cloud` → 重启后端。

**环境变量**（IDEA 运行配置 → Environment variables）：

| 变量                       | 需要场景                                                                                                                                      |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `DEEPSEEK_API_KEY`       | cloud 模式**必填** —— 缺失则 `ChatModel` Bean 创建失败 → **启动失败**                                                                                    |
| `ZHIPU_API_KEY`          | cloud 模式**必填** —— 同上（`EmbeddingModel`）                                                                                                    |
| `TESSERACT_PATH`         | OCR（默认 `I:\OCR\tesseract-main`）                                                                                                           |
| `TESSERACT_DATA_PATH`    | OCR 数据（默认 `I:\OCR\tesseract-main\tessdata`）                                                                                               |
| `JWT_SECRET_KEY`         | JWT 签名（**必填，没有内置默认值** ）。缺失 → 启动失败：`Could not resolve placeholder 'JWT_SECRET_KEY'`；`JwtUtil` 在 `@PostConstruct` 还会校验长度，**不足 32 字节同样启动失败** |
| `APP_RAG_RERANK_ENABLED` | Rerank 开关（**容器部署建议显式设 `false`** —— 不设则默认 `true`，而容器里没有模型文件 → **启动失败**）                                                                    |
| `RERANK_MODEL_PATH`      | 自定义 Rerank 模型路径                                                                                                                              |
| `RERANK_TOKENIZER_PATH`  | 自定义 Rerank tokenizer 路径                                                                                                                                         |

> 两个模式的知识库互相独立，切换模式后需重新"初始化质检知识库"。
> 两种 profile 各提供 `ChatModel` 与 `StreamingChatModel`（类型不同，不会冲突）。

### 数据库初始化（Flyway 管理）

**表结构由 Flyway 在应用启动时自动创建**，无需手工执行 SQL。

```bash
# 只需先建空库（Flyway 不负责创建 database 本身）
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS mate10db \
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

- 迁移脚本目录：`src/main/resources/db/migration/`

  | 版本 | 内容 |
  |---|---|
  | `V1__init_schema.sql` | 建表：`sys_user` / `qc_report` / `qc_defect` / `qc_production_param` / `chat` / `chat_record` |
  | `V2__add_employee_roster.sql` | 员工工号名册 `sys_employee`（`emp_no` 唯一 + `registered` 占用标记 + `register_time`）|
  | `V3__add_user_emp_no.sql` | `sys_user.emp_no`（可空 + 唯一），把注册用户与工号绑定 |
  | `V4__fix_user_phone_type.sql` | `sys_user.phone` → `VARCHAR(20)`（手机号是标识符，不是数值）|
  | `V5__add_user_email_unique.sql` | 补齐 `sys_user.uk_email` 唯一索引 |
  | `V6__add_chat_indexes.sql` | 补齐 `chat.idx_user_id` / `chat.idx_update_time` |
  | `V7__rename_username_index.sql` | 统一索引命名 `username` → `uk_username` |

- **开发环境种子数据**：`src/main/resources/db/seed-dev/R__seed_employee_roster.sql`

  ```yaml
  # application-dev.yml（只在 dev 加载第二个目录）
  spring:
    flyway:
      locations: classpath:db/migration,classpath:db/seed-dev
  ```

    - **绝对不要把它放进 `migration/`** —— 那会让**生产环境也插入这些假工号**；
      而这些工号写在 public 仓库里 = **公开的有效工号**  
    - 用 `R__`（可重复迁移）而不是 `V900__` —— V 版本号一次性且必须递增，
      用大号会挡住将来新增的 `V8`/`V9`（`out-of-order` 默认 `false` → 报"乱序"拒绝执行 ）

- **`baseline-on-migrate` 的边界**：它会**跳过 baseline 版本及以下的所有迁移**。
  本项目 `mate10db` 就是这种情况 —— 2026-09-20 baseline，**`V1` 从未执行过** ，
  表结构是**手工建的**，与 `V1` 定义**存在差异**（`phone` 类型 / 缺 `uk_email` /
  缺 `chat` 两个索引）→ 靠 `V5`/`V6`/`V7` 补齐后才收敛

**新增结构变更**：新建 `V8__xxx.sql`（**双下划线**），重启后端自动执行

- **迁移脚本一律写成幂等**：`CREATE TABLE IF NOT EXISTS` + `information_schema` 守卫
  （MySQL 8 **不支持** `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`，那是 MariaDB 语法）

> **已执行过的脚本不可修改内容** —— 校验和变化会导致
> `FlywayValidateException: Migration checksum mismatch` → **启动失败**。
> - 这个报错会**伪装成** `Error creating bean with name 'xxxMapper'`
    >   （`mapper ← sqlSessionTemplate ← flywayInitializer ← checksum mismatch`）
    >   —— **真凶永远在最内层的 `Caused by`**
> - 修复：新建空库重跑（干净），或 `DELETE FROM flyway_schema_history WHERE version='N'`
    >   让它**真正重新应用**  **不要用 `flyway repair`** —— 它只改校验和、**不重跑迁移**，
    >   会留下"记录说做了、实际没做"的假象
> - 同理 `R__` 脚本改内容也会触发校验和不匹配
> - **写迁移脚本时不要让 devtools 自动重启**：`db/migration/*.sql` 不在它的默认排除
    >   清单里 → 新建一个还没写完（甚至空的）迁移文件并保存时，应用立刻重启并把它
    >   **当成正式迁移应用掉** → 留下 `checksum = 0` 的假迁移 → 之后写满内容就
    >   `checksum mismatch` 启动失败。预防：写脚本时先停后端，或
    >   `spring.devtools.restart.additional-exclude: db/**`（ 是 **additional-exclude**）

**验证「脚本能不能从零建库」**：

```
① 建空库：CREATE DATABASE mate10db_fresh ...
② 用带 -Dspring.datasource.url=...mate10db_fresh 的 Run Configuration 启动一次
③ 两库结构对比应【完全一致】（列 + 索引）
   → 建议建两个 Run Configuration：日常用 mate10db / 验证用 mate10db_fresh 
```

> 所有表统一建在 `mate10db`，**不要再用跨库查询**（历史遗留的 `mate10sql` 已废弃）。

### 关键配置与硬编码

- `application.yml` — 所有中间件连接配置、profile 切换、双集合维度配置
- Tesseract、JWT 密钥均已外部化为环境变量（见上表）
- **JWT 有效期 7 天**：过期后接口返回 401（`SecurityConfig` 已配置 `AuthenticationEntryPoint`）
- **Rerank 模型路径**：`app.rag.rerank.*`（默认 `model-path=D:/AIGC/bge-reranker-base/onnx/model.onnx`，
  可用 `RERANK_MODEL_PATH` / `RERANK_TOKENIZER_PATH` 环境变量覆盖；模型文件不在 git，需从 ModelScope 下载）

### 监控端点（Actuator + Prometheus）

| 端点                     | 访问      | 说明                                            |
|------------------------|---------|-----------------------------------------------|
| `/actuator/health`     | 免认证     | 健康检查（含 db / mongo / redis 明细）                 |
| `/actuator/prometheus` | 免认证     | Prometheus 指标抓取                               |
| 其余 `/actuator/**`      | **需认证** | metrics / flyway / loggers / env / heapdump … |

> ️ **不要图省事放行整个 `/actuator/**`**：
> `/actuator/heapdump` 能下载堆内存快照（含密钥、token），`/actuator/env` 能看到全部配置。
> 配置见 `application.yml` 的 `management.*`，安全规则见 `SecurityConfig`。

**SB 4.0 属性名变化（写了会被静默忽略）**：

- `management.endpoint.<id>.enabled`  已移除 → 改用 `management.endpoint.<id>.access`
  （取值 `NONE` / `READ_ONLY` / `UNRESTRICTED`）
- Prometheus 新前缀：`management.prometheus.metrics.export.*`
  （旧的 `management.metrics.export.prometheus.*` 仍兼容但不推荐）

### 质检分析 Agent（LangChain4j `@Tool`，L1）

**端点**：

| 端点                         | 说明                         | 响应格式                |
|----------------------------|----------------------------|---------------------|
| `POST /ai/qc-agent`        | 非流式，一次性返回结论                | `Result<String>`    |
| `POST /ai/qc-agent/stream` | **SSE 流式**，实时推送工具调用轨迹 + 结论 | `text/event-stream` |

请求体统一为 `{"question": "分析 R20260808001 的根因"}`

**与 L0 的区别**：`GET /api/qc/report/{id}/root-cause` 是**固定管道**（顺序写死在代码里）；
Agent 则由**模型自主决定**调哪些工具、什么顺序 → 控制流从开发者转移到模型。

| 组件                                     | 职责                                  |
|----------------------------------------|-------------------------------------|
| `agent/QcAgentTools.java`              | 工具集（**全部只读**，**不含任何埋点代码**）          |
| `agent/QcAnalysisAgent.java`           | Agent 接口 + SystemMessage（含"禁止臆测"约束） |
| `agent/AgentFactory.java`              | **Agent 工厂** —— 每次请求创建一个 Agent 实例   |
| `agent/AgentTraceListener.java`        | 工具执行**完成**钩子 → 推送 SSE               |
| `agent/AgentTraceContext.java`         | SSE 事件发送工具（无状态）                     |
| `config/qc/QcAgentConfig.java`         | 按 profile 装配，注册两个轨迹钩子               |
| `controller/qc/QcAgentController.java` | HTTP 入口                             |

**可用工具**：

| 工具                          | 底层方法                            | 用途                                        |
|-----------------------------|---------------------------------|-------------------------------------------|
| `getReportByNo(reportNo)`   | `QcReportService.getByReportNo` | 按业务编号（`R2026…`）换数字 ID                     |
| `getReport(reportId)`       | `QcReportService.getById`       | 报告背景                                      |
| `getDefects(reportId)`      | `QcDefectService.getByReportId` | 缺陷构成                                      |
| `searchKnowledge(question)` | `RagService.retrieve`           | 检索质量标准（**用 retrieve 不用 ask**，只给素材不让它二次生成） |

**️ 安全边界**：只暴露读操作 —— `deleteReport` / `updateStatus` / `confirmDefect` **一律不暴露**，
即使被提示词注入，模型也只能读不能改。

**模型选择**（Agent 需要 Function Calling）：

| profile  | Agent 用的模型                             | 说明                                         |
|----------|----------------------------------------|--------------------------------------------|
| `ollama` | `ollama.agent-model`（默认 `llama3.1`）    | ️ `deepseek-r1` 是推理模型，工具调用能力弱，故**单独配一个字段** |
| `cloud`  | `cloud-ai.chat.model`（`deepseek-chat`） |  原生支持 Function Calling                     |

**轨迹埋点机制（重要 —— 零侵入，不碰工具代码）**：

用 `AiServices` 的两个**官方钩子**，`QcAgentTools` 一行都不用改：

```java
AiServices.builder(QcAnalysisAgent.class)
        .chatModel(model)
        .tools(tools)
        .beforeToolExecution(b -> { ... })              // ← 工具开始执行
        .registerListener(new AgentTraceListener(emitter))  // ← 工具执行完成（含结果）
        .build();
```

**emitter 怎么传给钩子** —— **AgentFactory + 闭包捕获**（每次请求建一个 Agent 实例）：

```java

@Bean
public AgentFactory cloudAgentFactory(QcAgentTools tools) {
    ChatModel model = ...;                       // 模型只建一次 
    return emitter -> buildAgent(model, tools, emitter);   // 闭包捕获 emitter 
}
```

为什么不用 ThreadLocal ️：

- `executeToolsConcurrently()` → 工具在**线程池**执行 → ThreadLocal **取不到**
- 换成 `StreamingChatModel` → 事件回调可能在别的线程 → 同上
- **而且是【静默失效】** —— 没有报错，最难排查

为什么不用 `InvocationContext.methodArguments()` ️：

- 那是 LangChain4j **内部**用来找 `ChatMemory` 等托管类型的机制
- 放自定义参数进去属于**未文档化行为**，升级版本可能失效

**闭包方案的优势**：不依赖线程、不依赖框架内部机制、构建开销 ~1ms（Agent 本身跑数秒，可忽略）。

**验证方式**：看日志里的 `[Tool]` 调用序列 —— 出现多条且顺序不固定 = 模型在自主决策 

```
[Tool] getReportByNo(reportNo=R20260811001)   ← 工具开始（beforeToolExecution）
[Tool] getReportByNo 完成                      ← 工具完成（AgentTraceListener）
[Tool] getReport(reportId=10)                 ← 模型自己把编号换成了 ID 
[Tool] getDefects(reportId=10)
[Tool] getReport 完成
[Tool] getDefects 完成
Agent 流式分析完成，耗时 5215 ms
```

> 💡 依赖：**零新增**。`langchain4j-core` 已含 `@Tool` / `@P` / `ToolExecutedEventListener`，
> `langchain4j` 已含 `AiServices`（含 `beforeToolExecution` / `registerListener`），无需引入 LangGraph。
> 只有做**多 Agent 协作 / 人工审批断点**时才需要考虑图编排。

### 两层记忆：会话事件流 + 结论档案

Agent 的记忆分两层，**存的东西不同、用途不同、增长方式也不同**：

|     | 第一层：会话事件流                    | 第二层：结论快照                       |
|-----|------------------------------|--------------------------------|
| 集合  | `chat_messages`              | `report_memory`                |
| 键   | `chat_id`（+ `report_no` 供审计） | `report_no`                    |
| 存什么 | 原始问答**全文**                   | 抽取的**结论**（conclusion + advice） |
| 用途  | 会话上下文 · **审计可追溯**            | 团队共享的「**当前认识**」                |
| 增长  | 只增不减                         | **每份报告恒定 1 条有效**               |
| 组件  | `MongoChatMessageService`    | `ReportMemoryService`          |

**为什么要分两层**：把「对话」当知识存，噪音会随提问次数**线性增长**
（问 100 次 = 100 组问答）。改成「结论快照」（覆盖式）后，注入长度恒定、噪音有界。

#### 路由规则（`ChatContextBuilder.buildAgentHistory`）

```
问句含报告编号（\bR\d{11}\b）
    → 读【结论档案】report_memory（团队共享、跨会话）
问句不含编号
    → 读【本次会话上文】chat_messages（按 chatId）
```

#### 覆盖语义（核心不变式）

```
同一 reportNo 下，superseded=false 的记录【最多 1 条】

写入新结论时：
  ① 旧的有效结论 → 全部标记 superseded=true（留痕，不物理删除 → 审计可追溯）
  ② 新结论 superseded=false → 成为唯一有效
读取时：
  只取 superseded=false 的最新一条
```

推荐建**部分唯一索引**，从数据库层面兜底（防并发写坏）：

```javascript
db.report_memory.createIndex(
    {report_no: 1},
    {unique: true, partialFilterExpression: {superseded: false}}
)
```

#### 天然质量门（零成本）

`ReportMemoryServiceImpl.extractSection()` 从回答里切出 `## 根因判断` 段，
**切不到就不入档**：

| 回答类型                  | 是否入档             |
|-----------------------|------------------|
| 完整根因分析（含 `## 根因判断`）   |  入档              |
| 「之前分析出什么了？」这类**元提问**  |  不入档（标题是「既往分析结论」） |
| 标准问答 / 闲聊（如「划痕的判定标准」） |  不入档             |

> ️ 这依赖 `QcAnalysisAgent` 的 SystemMessage 强制四段结构
> （`## 根因判断 / ## 改进建议 / ## 判断依据 / ## 信息缺口`）。
> **改 SystemMessage 的输出格式前，先确认这里还能切到段**，否则质量门会失效。

#### 相关类

| 类                                             | 职责                                      |
|-----------------------------------------------|-----------------------------------------|
| `document/ReportMemoryDocument`               | 结论档案文档（`report_memory` 集合）              |
| `repository/ReportMemoryRepository`           | 查询 `findByReportNoAndSupersededFalse` 等 |
| `service/ReportMemoryService`                 | 抽段 + 覆盖写入 + 读最新                         |
| `config/ChatContextBuilder.buildAgentHistory` | 路由 + 格式化注入                              |
| `controller/qc/QcAgentController.saveMemory`  | 两层分发（流式/非流式**共用**）                      |

#### 配置（`app.chat.*`）

| 配置                    | 默认   | 说明      |
|-----------------------|------|---------|
| `agent-session-limit` | 4    | 会话分支取几条 |
| `agent-history-chars` | 3000 | 参考块字符预算 |

#### 验证方式

```
① 问「分析 R20260808001 的根因」                → 首次入档
② 换新会话问「R20260808001 之前分析出什么了？」   → 读到档案（跨会话）★
③ 再问「再分析一次 R20260808001 的根因」          → 覆盖旧结论

查库验收：
  db.report_memory.find({ report_no: "R20260808001" })
  预期：superseded=false 的记录【恰好 1 条】
```

#### 本层待办


### 报告审计（可追溯）

**端点**：`GET /api/qc/report/{reportNo}/audit`  **需 ADMIN 角色**（`role=1`）

回答一个问题：**这份报告的判定是怎么来的？**

| 返回字段                 | 含义             | 数据来源                      |
|----------------------|----------------|---------------------------|
| `versionCount`       | 结论改过几次         | `report_memory`           |
| `conclusionTimeline` | 结论版本链（含被覆盖的历史） | `report_memory`           |
| `current`            | 当前有效结论         | `report_memory`           |
| `discussions`        | 谁在什么时候讨论过      | `chat_messages.report_no` |

**数据基础**（v0.11.0 已铺好）：

- 旧结论标 `superseded=true` 留痕（不物理删除）→ 天然 before/after
- `analyzed_by` / `analyzed_at` 记录「谁、何时」
- `chat_messages.report_no` 记录讨论轨迹

**组件**：`ReportAuditService` + `ReportAuditServiceImpl` + `dto/ReportAuditDTO`

**权限**：`@PreAuthorize("hasRole('ADMIN')")` —— 需 `role=1` 的账号
（`JwtAuthenticationFilter` 对 `role=1` 授予 `ROLE_ADMIN`；普通账号返回 403）

**边界行为**：该报告无任何记录时返回空轨迹（`versionCount=0`、`current=null`），**不抛异常**

**验证**：用 **ADMIN 账号**请求，日志应出现
`[Audit] reportNo=… 结论版本 N 次，讨论记录 M 条`

>  审计的设计原则（本实现已符合）：
> 只追加不改历史（`superseded` 软删除）、记录变更前后（版本链）、
> 记录 who/when（`analyzed_by`/`analyzed_at`）、用服务端时间。

### RAG 重排（Cross-Encoder Rerank）

混合检索（向量 + BM25 → 融合）之后，再加一层 **cross-encoder 精排**，把召回结果重排到 Top-K。

**链路**：召回 topK × `recall-factor` → 按文本去重 → `CrossEncoderReranker`（bge-reranker-base）精排 → Top-K

| 组件                                       | 职责                                                                   |
|------------------------------------------|----------------------------------------------------------------------|
| `service/Reranker.java`                  | 重排接口（`rerank(query, candidates, topK)`）                              |
| `service/Impl/CrossEncoderReranker.java` | 包装 LangChain4j `OnnxScoringModel`（bge-reranker-base，`scoreAll` 批量打分） |
| `HybridRetriever`                        | 多召回 + 去重 + 调 reranker（`@Autowired(required=false)`，Bean 不存在时回退 RRF）  |

**依赖**：`langchain4j-onnx-scoring:1.14.1-beta24`（传递引入 `onnxruntime:1.22.0` + DJL `tokenizers`）

**模型**：`bge-reranker-base`（int8 量化 266MB + `tokenizer.json` 16MB），放 `D:/AIGC/bge-reranker-base/`。
下载用 **ModelScope**（hf-mirror 本机连不上）：
`https://modelscope.cn/api/v1/models/Xenova/bge-reranker-base/repo?Revision=master&FilePath=onnx/model_quantized.onnx`

**配置**（`application.yml` → `app.rag.rerank`）：
`enabled` / `model-path` / `tokenizer-path` / `recall-factor`（默认 3）/ `max-length`（默认 510）

**开关**：`enabled=false` 时 `CrossEncoderReranker` Bean 不创建（`@ConditionalOnProperty`），
`HybridRetriever` 自动回退到 RRF 融合。

**验证**：启动日志出现 `[Rerank] Cross-Encoder 加载完成`；检索时出现 `[Hybrid] 召回 xx 条 → 去重 xx 条 → 重排取 xx 条`。

### 已知问题

- **Spring Data Redis 4.0 的 `RedisCache.clear()`（清空）失效（重要，集成测试抓到的框架 bug）** ️

  现象：`@CacheEvict(allEntries=true)` 和手动 `cacheManager.getCache("qcDetail").clear()`
  都**静默失败** —— 无异常、无报错，但 Redis 里的 key 没删；
  `evict(key)`（DEL）和 `KEYS + DELETE` 则正常。

  排查过程（三个假想敌，一次锁定）：
- ① 怀疑组合注解 `@EvictQcCache`（元注解 @CacheEvict）没被识别 → 换成直接 `@CacheEvict` 仍失效，排除

- ② 怀疑 `transactionAware()` 把 clear 延迟到事务提交 → 注释掉仍失效，排除（现已恢复）

- ③ 最小化对比：手动 `evict(key)`  vs 手动 `clear()`  → 锁定 `clear()`（清空）路径

- ④ 试过显式切换 `BatchStrategies.scan(1000)` SCAN 批处理策略 → 仍失效，排除批处理策略层

- （官方文档默认就是 KEYS+DEL 批处理策略，KEYS/SCAN 两种都试过，`clear()` 都静默失败）

  **根因与修复**：Spring Data Redis 3.x+ 默认的 `RedisCacheWriter` 是**批量写入**，
  `clear()` 的批量操作没同步刷到 Redis → 静默失败；`evict(key)` 单条 DEL 走即时路径所以正常。
  **修复**：`RedisConfig` 改用
  `RedisCacheWriter.create(connectionFactory, b -> b.immediateWrites())`
  → 所有写入（含 clear）立即同步执行 → 恢复正常（KEYS+DEL 工作区已移除）。
   其余 Service（`QcDefectServiceImpl` / `QcProductionParamServiceImpl`）的
  `@EvictQcCache` 之前同样失效，immediateWrites 修复后已恢复 ——
  已由 `QcReportCacheIntegrationTest` 的 `defectWriteEvictsCache` /
  `paramWriteEvictsCache` 实测覆盖。

  > 方法论：这是「集成测试（真 Redis）才能抓到的 bug」—— 单测 mock 掉缓存，
  > 永远测不出「清空失效」。`QcReportCacheIntegrationTest` 就是为它而生的。

- **`onnxruntime` 原生库 + GraalVM 不兼容（重要，排查了四个假想敌才定位）** 

  Rerank 的 ONNX 原生库在 GraalVM 下报错：
    - onnxruntime 1.22.0 → `UnsatisfiedLinkError: DLL初始化例程失败`
    - onnxruntime 1.31.0 → `EXCEPTION_ACCESS_VIOLATION`（崩在 `msvcp140.dll`）

  **真凶是 GraalVM 的 `jvmci` 编译器**，不是系统 DLL、不是 VC++ 运行库、不是版本。
  **解法**：换成标准 OpenJDK（Microsoft Build of OpenJDK 17，HotSpot/C2）→ 正常。

  排查过程（四个假想敌，记录方法论）：
  ① 怀疑缺 VC++ 运行库 → 查 `msvcp140.dll` 齐全，排除
  ② 怀疑撞 Win11 24H2 系统 `onnxruntime.dll` → 重命名后仍失败，排除（现已恢复）
  ③ 怀疑版本太新崩溃 → 换 1.31.0 表现不同但都不行
  ④ **最小化实验对比 JVM** → 写 `OrtEnvironment.getEnvironment()` 最小 main，
  分别用 GraalVM / OpenJDK 跑 → OpenJDK 成功、GraalVM 失败 → 锁定 

  > 方法论：遇到「玄学」原生库问题，写个最小 main 分别用不同 JVM 跑，一分钟二分定位，别瞎猜。

**Spring Boot 4.0 模块化陷阱（重要）**：SB 4.0 把自动配置拆成了独立模块，
加中间件需要「**库 + 集成层**」两件套。只加库会**静默失效** ——
无报错、无日志、配置属性无法解析，最难排查：

| 中间件          | 库                   | 集成层（必需）                                            |
|--------------|---------------------|----------------------------------------------------|
| Flyway       | flyway-core         | spring-boot-flyway（或 `spring-boot-starter-flyway`） |
| MongoDB      | mongodb-driver-sync | spring-boot-data-mongodb                           |
| Kafka        | kafka-clients       | spring-boot-kafka                                  |
| MyBatis-Plus | mybatis-plus-core 等 | **mybatis-plus-spring-boot4-starter**（SB4 专用）      |

**排查三步**：① IDE 报「无法解析配置属性」通常是真的，别急着当缓存问题
② 查官方文档「自动配置类」附录确认模块名 ③ `mvn dependency:tree` 确认是否引入

- **MyBatis-Plus 这条有三个变体坑**

    1. 用 SB2/3 版的 `mybatis-plus-boot-starter` → 自动配置静默跳过 →
       容器里没有 `SqlSessionFactory`（删掉自定义 factory 会直接报
       `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`）
    2. **绝不能钉它的内部子模块** —— 曾钉 `mybatis-plus-extension:3.5.7`
       与 starter 的 `3.5.16` 混搭 → `IRepository` 找不到（Maven「最近者优先」）；
       另外 **3.5.9+ 的分页拦截器在 `mybatis-plus-jsqlparser`**，要单独加、版本与主包一致
    3. 实体一律显式写 `@TableId(type = IdType.AUTO)` —— 全局 `id-type` 在手写
       `sqlSessionFactory` 场景下不生效 → id 变 19 位雪花 →
       **超出 JS 安全整数**（`2^53`）→ 前端 `JSON.parse` 静默取整

  **连带后果**：`application.yml` 里 `mybatis-plus.*` 的配置**全部不生效**
  （`log-impl` / `global-config.id-type` / `type-aliases-package`），
  插件必须手动 `setPlugins` —— 漏了就是**分页插件失效**（`total` 恒为 0）

- **Redis 缓存序列化必须写入类型信息（重要，隐蔽）** 

  `new GenericJacksonJsonRedisSerializer(objectMapper)` 构造时
  **不会开启 default typing** → 序列化出的 JSON **不含 `@class`**
  → 反序列化退化成 `LinkedHashMap` → 读取时报：

  ```
  ClassCastException: class java.util.LinkedHashMap
      cannot be cast to class org.mate.mate10.entity.qc.QcReport
  ```

  **正确写法**（用 Spring 官方 builder）：

  ```java
  GenericJacksonJsonRedisSerializer.builder()
          .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                  .allowIfSubType("org.mate.mate10.")
                  .allowIfSubType("java.util.")
                  .allowIfSubType("java.lang.")
                  .allowIfSubType("java.math.")
                  .allowIfSubType("java.time.")
                  .build())
          .build();
  ```

    - **验证**：`redis-cli GET "qcDetail::report:1"` 应能看到 `"@class":"org.mate..."`
    - **修复后必须清旧缓存**：`redis-cli FLUSHDB`
      （旧数据没有 `@class`，即使代码修好仍会报错）

- **`@Cacheable` 遇到 null 返回值会抛异常**：
  `RedisConfig` 配了 `disableCachingNullValues()`，方法返回 null 时 Spring 会抛
  `Cache 'xxx' does not allow 'null' values`。
  **解法**：给可能返回 null 的方法加 `unless = "#result == null"` ️ `#` 不能少。

- **`@Cacheable` / `@CacheEvict` 的 SpEL 是字符串，编译期不校验** ️：
  写成 `unless = "result == null"`（漏了 `#`）**能编译通过**，
  但运行时抛 `SpelEvaluationException: EL1008E: Property or field 'result' cannot be found`。
  **IDE 不会有任何提示**，改缓存注解后务必实测。

- **`@Cacheable` 只在外部调用时生效**：
  同类内部 `this.method()` 自调用不走 AOP 代理。
  如 `createFullReport` 内部调 `this.createReport()`，必须给**入口方法**也加注解。

- **SSE 流式 + Spring Security 的异步派发坑（隐蔽）** ️

  **现象**：SSE 接口的数据**已正常返回**（前端也正常显示），
  但日志紧跟一条 `AuthorizationDeniedException: Access Denied`

  **根因**：

    1. 请求进来 → Security 过滤（`dispatcherType = REQUEST`）→ 带 JWT 放行 
    2. 异步执行完 → `emitter.complete()` → Tomcat 触发 **ASYNC dispatch**
    3. Spring Security 7 默认对**所有 dispatcherType** 做授权检查
       → 异步线程里 `SecurityContext` 已清空
       → `.anyRequest().authenticated()` → 拒绝 

  调用栈特征（用来辨认）：

  ```
  AsyncContextImpl$AsyncRunnable.run
  ApplicationDispatcher.dispatch            ← 异步派发
  AuthorizationFilter.doFilter              ← 授权拦截
  ```

  **解法**（1 行）：

  ```java
  .authorizeHttpRequests(auth -> auth
          // 异步/错误派发不再重复授权（第 1 次 REQUEST 派发已用 JWT 检查过）
          .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
          .requestMatchers("/blocker/user/login", "/blocker/user/register").permitAll()
          ...
          .anyRequest().authenticated())
  ```

  需要 import `jakarta.servlet.DispatcherType`。

  > ASYNC 派发**不是新请求** —— 它是同一个请求的延续，
  > 第 1 次 REQUEST 派发时已经检查过了，放行它**不降低安全性**。
  > `/ai/chat/stream` 也有同样问题，一并解决。

- **`flyway-core` 必须是 compile scope（重要 —— 曾经静默失效两周）**   

  **现象**：**Flyway 一次都没执行过**，但应用**启动完全正常、零报错、零日志** ——
  表结构全靠手工建，`flyway_schema_history` 里只有一个早年的 BASELINE 行。

  **根因**：`pom.xml` 里 `flyway-core` 被标成了 `<scope>test</scope>` ——
  运行时 classpath 里没有它 → `FlywayAutoConfiguration` 上的
  `@ConditionalOnClass(Flyway.class)` 不满足 → **自动配置被静默跳过**。

  **这是上面「SB 4.0 模块化陷阱」的镜像版**：
  那条讲「只加库、缺集成层」；这条是「**有集成层、但库被标成了 test**」——
  **同样静默失效**   

  **排查三步（5 分钟）**：

    1. 看**运行进程的 classpath** 里有没有 `flyway-core`
       —— 「依赖在 pom 里」≠「依赖在运行时 classpath 里」
    2. 看 `flyway_schema_history`：**没有这张表 / 只有 BASELINE 行** = Flyway 从未工作
    3. **换一个空库启动** —— 空库里零张表 = 铁证

  **解法**：删掉 `<scope>test</scope>`（`compile` 也包含测试 classpath，集成测试照常可用）：

  ```xml
  <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
      <version>11.14.1</version>
      <!--    不要写 <scope>test</scope> -->
  </dependency>
  ```

  > **教训**：「依赖加了」≠「依赖生效了」。本项目已经踩了 **3 次**同一类坑
  > （MongoDB 配置未生效 / MyBatis-Plus starter 不匹配 / Flyway 依赖 scope 错），
  > 共同特征都是 **零报错的静默失效** —— 只能靠**观测运行时行为**发现   

- **`ChatController` 的 `chatId` / `userId` 来自请求体、未校验归属（越权）**

  与已修复的 `/api/chat/*` 三个查询接口不同 —— 那三个已加归属校验，
  但 `/ai/chatRecord` 与 `/ai/chat/stream` 的 `chatId` / `userId` **由客户端传入且不校验**：
  任何登录用户可以往**别人的会话里写消息**，也能**读别人会话的上文**
  （`ChatContextBuilder.build(chatId, …)`）。
  **修法**：从 `SecurityContext` 取当前用户，校验 `chatId` 归属（或忽略客户端传的 `userId`）。

- **Kafka 消费者不幂等**  

  处理前**不删旧缺陷**，且 `total_defects` 用**本批 size** 而非累计 →
  消息重复投递时**缺陷数翻倍**、统计被污染。
  **修法**：先按 `report_id` 删旧缺陷；`total_defects` 用真实 count。

- **Kafka 消费异常被吞 → 失败消息永久丢失**  

  异常被 catch 后仅打日志，而 offset 仍被提交 → 消息**再也不会重试**。
  **修法**：区分可重试/不可重试；不可重试的投递死信 topic，可重试的抛出触发重试。

- **Kafka `auto.commit` 与 `AckMode.BATCH` 并存 + `max.poll.records=100`**  

  ① 双重提交语义冲突；② 单条要做 OCR / AI（秒级），100 条远超
  `max.poll.interval.ms` → 消费者被踢出组 → rebalance → **同一批消息重放**。
  **修法**：关掉 `enable.auto.commit`；`max.poll.records` 降到个位数。

- **Milvus 客户端没传凭据**  

  `MilvusConfig` 的 `ConnectParam` 没有 username/password，
  而配置里**有** `milvus.username` / `milvus.password` → 本地能连（未开鉴权），
  **生产一旦开鉴权即全挂**。
  **修法**：把凭据接进 `ConnectParam`。

- **`updateUser` 会把密码原样写库**  

  更新用户时未判断「密码是否为空 / 是否已加密」→ 前端不传密码时把**空串或明文**
  写进 `password` → 该用户**再也登不进**（且库里出现明文）。
  **修法**：密码为空则不动该字段；非空则 `passwordEncoder.encode()`。

- **`deleteReport` 不清理子表** ️

  删报告时未删 `qc_defect` / `qc_production_param` → **孤儿数据污染全局统计**
  （缺陷类型 / 严重程度统计会把已删报告的缺陷算进去）。
  **修法**：`@Transactional` 内先删子表。

- **缺索引** 

    - `chat_messages`（Mongo）：按 `chat_id` / `report_no` 查询没有索引
    - `qc_report.report_no`（MySQL）：按编号查询**全表扫描**
    - **修法**：Mongo 建索引脚本 + 新增 `V8__add_report_no_index.sql`

- **403 返回 HTTP 200** ️

  `Result.error(403, …)` 直接 `return` → **HTTP 状态是 200**，而
  `SecurityConfig` 的 `accessDeniedHandler` 返回**真 403** →
  同一个「无权限」出现两种状态码，客户端 / 网关 / 日志都难以统一判断。
  **修法**：统一抛 `AccessDeniedException` 交给 handler，或改用 `ResponseEntity`。

- **Swagger UI**：`http://localhost:8080/swagger-ui.html`（已放行匿名访问）

---

## 前端 `mate10-V/`

### 环境要求

Node.js **20.19+ 或 22.12+**（Vite 8 要求），npm 或 pnpm。

### 启动与构建

```bash
npm install
npm run dev       # 启动开发服务器 http://localhost:5173
npm run build     # 生产构建 → dist/
npm run preview   # 预览构建结果
```

### Vite 代理

`vite.config.js` 已配置代理，开发环境无需处理跨域：

- `/blocker` → `http://localhost:8080`（登录/注册）
- `/api` → `http://localhost:8080`
- `/rag` → `http://localhost:8080`（知识库管理）
- `/ai` → `http://localhost:8080`（对话/记忆/流式）

>  改 `vite.config.js` 后**必须重启 dev server**，配置不会热更新。

### 架构要点

- Vue Router `createWebHistory`，生产部署需 Nginx `try_files` 回退
- 路由守卫在 `router/index.js`（无 token 自动跳登录页，return 风格）
- Pinia 状态管理（用户状态在 `stores/user.js`）
- Axios 拦截器自动从 `localStorage` 读取 token 并注入 `Authorization: Bearer <token>`
- 所有业务页复用 `layout/index.vue` 主布局
- **3D 模型**：`components/3d/ModelViewer.vue` 加载 GLB 模型（`Aston_Martin.glb`），
  `vite.config.js` 中 `assetsInclude: ['**/*.glb']` 为必需配置
- 登录/注册：`/blocker/user/login`、`/blocker/user/register`（前后端路径已统一）
- **对话页面**：`views/qc/QA.vue`，`chatId` 首次发送时创建，会话存于组件状态（刷新即新会话）
- **流式对话**：`api/chat.js` 的 `chatStream()` 用原生 `fetch` 绕过 axios 拦截器
  （SSE 响应是裸文本流，没有 `{code,msg,data}` 结构）
- **质检 Agent 页面**：`views/qc/Agent.vue`（路由 `/agent`，菜单图标 `MagicStick`）
    - 实时展示**工具调用轨迹**（`running` / `done` 卡片）+ 最终结论
    - `api/agent.js` 的 `agentStream()` 同样用原生 `fetch`（SSE + 需要 `Authorization` 头）
    - 事件类型：`start` / `tool` / `message` / `error` / `done`

> ️ **SSE 多行 data 必须用 `\n` 拼回**：后端 `message` 事件含换行的 Markdown，
> 会被 SSE 规范拆成多个 `data:` 行。前端若直接拼接（`data += line`）会**丢掉换行**，
> 结论会显示成一整行。
>
> **两个流式接口都要遵守**：
> - `api/agent.js` ——  （`dataLines.join('\n')`）
> - `api/chat.js`  ——  （`data += line.slice(5)`），

---

## 联调须知

1. **先启中间件**，再启后端，最后启前端
2. 后端 `mvn spring-boot:run` 和前端 `npm run dev` 可并行
3. Ollama 需先拉模型：`ollama pull deepseek-r1:latest` 和 `ollama pull all-minilm:latest`
4. Milvus 用 Docker 部署（standalone + etcd + minio 三容器）；
   **Milvus 容器重启后集合回到未加载状态**，若问答报 SearchRequest failed，
   可用后端 API 或 REST 重新 load 集合
5. **Kafka**：
    - ** 现状（2026-10-09 起）**：开发环境 Kafka 已迁移到 **Docker**
      （`deploy/docker-compose.yml` 的 kafka 服务，Linux 容器），
      本机 `D:\Kafka\kafka_2.13-4.1.0` 已退役、不要再启动。
      本机后端连 `localhost:9094`（宿主机专用 listener），容器内连 `kafka:9092`。
      迁移原因与配置见下方「根因升级与根治方案」。
    - （历史）本机路径 `D:\Kafka\kafka_2.13-4.1.0`：
      KRaft 模式（无 Zookeeper），配置文件 `config\server.properties`
    - 数据目录：`log.dirs=kraft-logs`、`metadata.log.dir=kraft-meta`（**两者都不能删**）
    - **格式化必须加 `--standalone`**：
      `bin\windows\kafka-storage.bat format --standalone -t <集群ID> -c config\server.properties`
    - `kafka-server-stop.bat` 仍含 wmic 检测，Windows 11 24H2 上停止可能失败，可直接杀进程

    - **️ 启动失败的三种报错，同一个根因（文件只读 / 被占用）**：

      | 报错信息 | 触发点 |
      |---|---|
      | `No meta.properties found` | `.checkpoint` 文件为**只读** |
      | `AccessDeniedException: 另一个程序正在使用此文件` | 日志压缩时文件被占用 |
      | `FileSystemException: atomic move failed` → `Shutdown broker because all log dirs have failed` | **Log Cleaner 原子重命名失败** |

      **排查方法**：去 `logs\server.log` 往上翻 ——
      **最后一行只是「结果」，要往上找第一个真正的异常** 
      （例：`all log dirs have failed` 只是结果，真正原因在它上面几十行的
      `Cleaner.doClean → atomicMoveWithFallback` 栈里）

      **三级解法（按顺序试）**：

        1. 清只读属性：
           `attrib -R "D:\Kafka\kafka_2.13-4.1.0\kraft-meta\*.*" /S`
           （`kraft-logs` 同样处理）
        2. 加 Defender 排除（**需管理员**）：
           `powershell -NoProfile -Command "Add-MpPreference -ExclusionPath 'D:\Kafka'"`
           图形界面：Windows 安全中心 → 病毒和威胁防护 → 管理设置 → 排除项
        3. **兜底（无需管理员，开发环境够用）**：在 `config\server.properties`
           **末尾**加一行 `log.cleaner.enable=false`，禁用日志压缩器，
           彻底绕开 `__consumer_offsets` 的清理操作。
           代价：该 topic 不再压缩（多占少量磁盘），**生产环境不建议**。

      ---

      ** 2026-10-09 根因升级（上述三级解法未触及真因，已用 Docker 根治 ）**：

      现象：启动即崩，`kafka-log-retention` 任务报
      `FileSystemException: 另一个程序正在使用此文件` →
      `Shutdown broker because all log dirs have failed`。

      真正根因 —— **Windows 上 mmap 的惰性释放竞态**：

        1. `log.cleaner.enable=false` 只禁用**日志压缩器**（管 compact 策略），
           管不了 **Log Retention**（按 `retention.ms` 删段）——**两套独立机制**  
        2. Retention 判断段是否过期必须读**段内最大时间戳**（存于 `.timeindex`），
           这一读触发 mmap 打开该文件
        3. 读完立即删段 rename —— Windows 不允许 rename 被 mmap 的文件，
           而 Java `MappedByteBuffer` 的释放是异步的（等 GC）→ 间歇性撞锁
        4. Kafka 将文件操作失败视为 log dir 硬件故障 → broker 整体停机
           （Linux 无此问题：POSIX 允许 unmap 后立即重命名，
           这也是 Kafka 官方不把 Windows 当生产平台的原因）

      **特征识别**（再遇到可秒认）：
        - 删除批次里 `.log` / `.index` 都成功，**总是 `.timeindex` 失败**
        - 间歇性发作（取决于 GC 时机）；停机后手动改名立即成功
          （锁只在 Kafka 运行期间存在）

      **根治方案（已落地 ）**：Kafka 迁到 Docker（Linux 容器），
      配置见 `deploy/docker-compose.yml`：
        - 双 listener：容器内 `kafka:9092`、宿主机 `host.docker.internal:9094`
          （`KAFKA_LISTENERS` / `KAFKA_ADVERTISED_LISTENERS` /
          `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` 三处同步 + 端口映射 `127.0.0.1:9094:9094`）
        - 宿主机 hosts 需加 `127.0.0.1 host.docker.internal`（需管理员），
          否则后端报无法解析该主机名
        - `application-dev.yml`：`bootstrap-servers: localhost:9094`
        - **验证结果**：后端启动零 Kafka 报错；`chat-topic` / `qc-report-topic`
          自动创建；`kafka-console-consumer` 消费到真实聊天消息 
        - 本机旧配置（`log.cleaner.enable=false` 等）随本机 Kafka 退役，不再使用

6. 前端 `npm run build` 前必须确认后端已运行，否则代理不可用
7. 数据库表结构由 **Flyway 在启动时自动创建**（`db/migration/`），不要手工建表
8. **改完代码必须重启后端再测**——否则测的是上一次编译的 class，结论不可信
9. **写迁移脚本（`db/migration/*.sql`）时不要让 devtools 自动重启** ——
   `db/migration/*.sql` 不在 devtools 默认排除清单里，新建一个**还没写完甚至空的**
   迁移文件并保存时应用会立刻重启，把它**当成正式迁移应用掉** →
   留下 `checksum = 0` 的假迁移 → 之后写满内容就 `checksum mismatch` **启动失败**。
   预防：写脚本时**先停后端**，或 `application-dev.yml` 加
   `spring.devtools.restart.additional-exclude: db/**`（️ 是 **additional-exclude**）

---

## 新环境恢复手册

新机器从零恢复项目的完整流程：

1. **装环境**：JDK 17（**Microsoft Build of OpenJDK，不能用 GraalVM**，见「已知问题」）、Node.js 20.19+、**Docker Desktop**
   （中间件全容器化，无需本机装数据库）、Ollama、Tesseract
2. **拉代码**：`git clone <仓库地址>`
3. **初始化数据库**：无需手工建库——`deploy/.env` 配好密码后
   `docker compose up -d mysql` 会自动创建 `mate10db` 空库，
   表结构由 Flyway 启动时按 `V1`~`V7` 顺序自动创建。

    - ️ **生产环境不会加载开发种子** —— 工号名册 `sys_employee` 是**空的**，
      必须由管理员导入真实员工工号后才能注册
      （开发种子在 `db/seed-dev/`，**只在 `dev` profile 加载**）
    - **验证迁移真的跑了**（别只看日志）：

      ```bash
      docker compose logs backend | grep -i FlywayExecutor
      # 或直接查库（最可靠）：
      docker exec mate10-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
        -e "USE mate10db; SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
      ```

4. **拉模型**：`ollama pull deepseek-r1:latest`、`ollama pull all-minilm:latest`
   （Rerank 模型另下：从 ModelScope 下 `bge-reranker-base` 的 `onnx/model_quantized.onnx` + `tokenizer.json`
   到 `D:/AIGC/bge-reranker-base/`，见「RAG 重排」章节）
5. **启动中间件**：`cd deploy && docker compose up -d mysql mongo redis kafka`
   （Milvus 独立容器组另启；拉镜像失败用 daocloud 前缀 + tag）
6. **配置后端**（IDEA）：
   - Active profiles：`ollama` 或 `cloud`
   - **`JWT_SECRET_KEY`（必填，≥ 32 字节）** —— 缺失或过短，**启动即失败**
     （`Could not resolve placeholder 'JWT_SECRET_KEY'`）
   - cloud 模式还需 **`DEEPSEEK_API_KEY`** + **`ZHIPU_API_KEY`**
     （缺失 → `ChatModel` / `EmbeddingModel` Bean 创建失败 → 启动失败）
   - `TESSERACT_PATH`（换机器必改）
   - 容器部署建议显式设 `APP_RAG_RERANK_ENABLED=false`（容器里没有 Rerank 模型文件）
7. **启动后端**：`mvn spring-boot:run`
8. **启动前端**：`cd mate10-V && npm install && npm run dev`
9. **首次使用**：QA 界面"初始化质检知识库"（Milvus 集合自动创建）

> 换环境必改项：`TESSERACT_PATH`、`RERANK_MODEL_PATH`、`deploy/.env`（中间件密码，本机为 root/root）。

### Docker 部署注意

- **Kafka 需要第二个 listener**：容器内访问必须走独立端口
  （`listeners` 加 `PLAINTEXT_DOCKER://:9094`，
  `advertised.listeners` 加 `PLAINTEXT_DOCKER://host.docker.internal:9094`，
  `listener.security.protocol.map` 同步加映射），容器侧用 `host.docker.internal:9094`
- **Docker Desktop 重启后 Hyper-V 会动态圈占端口**：若报
  `An attempt was made to access a socket in a way forbidden`，
  用管理员执行 `net stop winnat` → `netsh int ipv4 add excludedportrange protocol=tcp startport=19530 numberofports=1` →
  `net start winnat`
- **镜像内 Tesseract 路径**：`/usr/bin/tesseract` + `/usr/share/tesseract-ocr/5/tessdata`
- **prod 配置刻意无默认值**：`MILVUS_USERNAME` 等缺失时启动即失败（fail-fast）
- **Nginx 配置改完必须先校验**：`nginx.conf` 里 `allow` / `proxy_set_header` 少个分号、
  多个冒号，Nginx 会**拒绝启动**（前端容器起不来，网站直接打不开）。校验命令：

  ```bash
  docker run --rm -v "<绝对路径>/mate10-V/nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine nginx -t
  ```

  报 `host not found in upstream "backend"` 是**正常的** —— `backend` 只在 compose 网络内可解析。
  只要不是 `unknown directive` / `invalid number of arguments` 就说明语法没问题 

- **compose 改完先校验**：`docker compose -f deploy/docker-compose.yml config`。
  YAML 靠缩进，服务名必须与同级服务严格对齐；报
  `additional properties 'xxx' not allowed` 就是缩进错层了
- **监控服务**：`prometheus` 走 Docker 内网 `backend:8080` 抓取（不经过 Nginx、不暴露端口）；
  `grafana` 暴露 `3000`，管理员密码取自 `.env` 的 `GRAFANA_PASSWORD`

### 文档地图

| 文件                  | 职责                              |
|---------------------|---------------------------------|
| `README.md`（根）      | 门户：介绍 + 截图 + 亮点 + 快速开始，细节下沉到子文档 |
| `mate10/README.md`  | 后端：功能、API 表、包结构、配置、监控           |
| `mate10-V/README.md` | 前端：页面、路由、组件、构建                  |
| `deploy/README.md`  | 部署运维：Docker、Nginx、监控、备份、踩坑      |
| `AGENTS.md`（本文）     | 给 AI/新人的环境说明、约定、踩坑手册            |
| `docs/experiments/rag-chunking-experiment.md`                  | RAG 分块策略调优实验记录（30 题评测集：86.7% → 96.7%）                    |

> ️ **`mate10/src/main/resources/knowledge/*.md` 不是文档，是 RAG 知识库数据源**！
> 它们会被读入 Milvus 做向量检索，**整理文档时绝对不要动**。
> `.opencode/agents/*.md` 同理（是工具配置，不是项目文档）。

### 无害告警（不用处理）

- `mapperLocations ... not found`：项目用 MyBatis-Plus 注解方式，无 XML 映射文件
- `UserDetailsServiceAutoConfiguration`：Spring Security 默认用户提示
- `TESSERACT_PATH` 配了但代码只用 `dataPath`（见 `FileParseServiceImpl`）

### 接口参数风格不统一（历史遗留）

- `/blocker/user/login` 用 `@RequestParam`（表单/查询参数）
- `/blocker/user/register` 用 `@RequestBody`（JSON），且**必须带 `empNo`** ——
  服务端会校验「该工号在 `sys_employee` 名册中且未被占用」（CAS 原子占用）

---

## 版本里程碑

> ️ **以 GitHub Release 实际内容为准**（下表已核对过）。
> 早期版本号曾按"计划"记录，与最终落地的内容不一致，已修正。

| 标签        | 日期    | 内容                                                                                            |
|-----------|-------|-----------------------------------------------------------------------------------------------|
| `v0.1.0`  | 09-15 | 多轮对话记忆：上下文注入、会话隔离、RAG 融合                                                                      |
| `v0.2.0`  | 09-16 | 流式输出：SSE 端点 + 前端逐字渲染                                                                          |
| `v0.3.0`  | 09-17 | RAG 评测体系与检索优化：30 题评测集 + 知识库文档化 + 分块策略实验                                                       |
| `v0.4.0`  | 09-17 | 工程化加固：安全（`@PreAuthorize` / JWT 外部化）、配置外部化、13 个单测、CI                                           |
| `v0.9.9`  | 09-22 | **工程化跃迁**：容器化部署（11 服务）· Flyway · Actuator/Prometheus · Redis 缓存 · 质检分析 Agent（L1）· RAG 混合检索    |
| `v0.10.0` | 10-11 | **RAG 重排（Cross-Encoder Rerank）**：bge-reranker-base ONNX 精排，命中率 96.7%→100%，补 11 个单测（28 全绿）     |
| `v0.11.0` | 10-12 | **两层记忆**：报告结论档案（`report_memory`）—— 覆盖式写入 + 旧版留痕 + 天然质量门；集成测试补 `@EvictQcCache` 实证与 Lucene 目录隔离 |

**v0.9.9 为什么从 v0.4.0 直接跳上来**：容器化、监控、缓存、Agent、混合检索
是**同一条主线**（可观测、可部署、有 AI 决策能力的系统"），
拆成多个小版本反而割裂叙事，故合并为一次里程碑发布。

**v0.10.0 的定位**：在混合检索（召回）之上补齐「精排」，完成 RAG 两段式闭环。
JDK 从 GraalVM 换成 Microsoft OpenJDK（解决 onnxruntime 原生库不兼容，见「已知问题」）。
距生产级 1.0 还差：多环境验证、Rerank 性能优化（非 GPU 路线）。
