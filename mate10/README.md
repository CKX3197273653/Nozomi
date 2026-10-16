# Mate10 后端服务

> 智能质检分析 + AI 应用的后端平台，基于 Spring Boot 4.0。
> 面向制造/质检场景：**上传质检报告文件 → AI 自动识别缺陷 → 统计分析与知识问答**，同时内置通用 AI 聊天与 RAG 知识库能力。

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [快速开始](#快速开始)
- [API 接口](#api-接口)
- [Kafka 主题](#kafka-主题)
- [项目结构](#项目结构)
- [配置说明](#配置说明)
- [监控与可观测性](#监控与可观测性)
- [质检分析 Agent](#质检分析-agent)
- [AI 记忆与审计](#ai-记忆与审计)
- [RAG 重排（Cross-Encoder Rerank）](#rag-重排cross-encoder-rerank)
- [性能压测实验](#性能压测实验)
- [已知事项 / Roadmap](#已知事项--roadmap)

---

## 功能特性

### 1. 智能质检（QC）— 核心模块

以「质检报告」为数据载体，形成完整的 AI 质检流水线：

- 支持上传 **PDF / 图片 / Word / Excel** 质检报告文件（本地存储 `uploads/qc/`）
- 提交后立即写入 MySQL，并通过 **Kafka 异步** 派发 AI 分析任务（`qc-report-topic`）
- 消费者解析文件内容 → 调用本地 Ollama 大模型识别缺陷 → 解析 JSON 批量入库
- 报告状态机：`PENDING → PROCESSING → COMPLETED / FAILED`
- 缺陷数据（类型/位置/严重程度/置信度）+ 生产参数（温/压/湿/速度）管理，支持人工确认
- 统计能力：按天趋势、生产线、产品批次、缺陷类型、严重程度（供 ECharts 图表）
- **AI 根因分析**：结合报告 + 缺陷 + 生产参数，输出根因与参数调整建议（JSON）
- 3D 缺陷可视化数据接口（缺陷在工件坐标上展示）

### 2. RAG 知识问答

- 基于 LangChain4j：文本/文件 → 切分（512/128 递归切分）→ Ollama embedding → 存入 Milvus
- 提问时检索 **top-10** 相关片段（`app.rag.top-k`）作为上下文，由对话模型生成答案
- 混合检索（向量 + Lucene BM25）→ 去重 → **Cross-Encoder 精排**（见「RAG 重排」章节）
- 支持初始化「质检标准知识库」、按预设质检助手身份问答（`/rag/qc-ask`）
- 一键清空知识库

### 3. Milvus 通用向量检索

- 直接封装 Milvus SDK，提供集合 CRUD、向量插入/删除/更新、按 ID 查询、相似度检索的通用 REST 接口

### 4. AI 聊天（基础能力）

- 与 Ollama 本地模型（默认 `deepseek-r1`）对话
- 用户/AI 消息实时写入 MongoDB（`chat_messages`）
- 通过 Kafka（`chat-topic`）异步落库 `chat_record`，并由 AI 抽取对话关键词
- 按会话/用户/角色查询历史消息（ 均校验归属，非本人返回 403）

### 5. 用户认证与管理

- Spring Security + BCrypt + JWT（登录签发 token，默认 7 天有效）
- 用户登录（用户名或邮箱）、新增/更新/禁用/删除（管理操作需 `role=1`）、改密码、查重

### 6. 质检分析 Agent（L1，模型自主决策）

- 基于 LangChain4j `@Tool`：把质检数据查询封装为**工具**，由**模型自主决定**调哪些、什么顺序
- 支持 **SSE 流式**推送**工具调用轨迹**（前端实时看到 Agent 在做什么）
- 与固定管道（L0）形成对照：`GET /api/qc/report/{id}/root-cause` 顺序写死；Agent 则控制流在模型手里
- **安全边界**：只暴露只读工具，即使被提示词注入，模型也只能读不能改
- 零新增依赖，零侵入（工具代码不含任何埋点）

### 7. AI 记忆与审计

- **两层记忆**：会话事件流（`chat_messages`，可追溯）+ 结论快照（`report_memory`，团队共享）
- **结论档案**：从 Agent 回答中切出 `## 根因判断` 段蒸馏入档，覆盖式写入（旧版留痕）
- **天然质量门**：切不到「## 根因判断」的回答（元提问 / 闲聊）自动不入档
- **报告审计**：一次拿到「结论怎么变的 + 谁在什么时候讨论过」，可倒推判定依据

---

## 技术栈

| 分类     | 技术                                            | 版本                                                        |
|--------|-----------------------------------------------|-----------------------------------------------------------|
| 语言     | Java                                          | 17                                                        |
| 框架     | Spring Boot                                   | 4.0.0                                                     |
| ORM    | MyBatis-Plus                                  | 3.5.16（**`mybatis-plus-spring-boot4-starter`** —— SB4 专用） |
| 数据库    | MySQL                                         | 8.0+                                                      |
| 数据库    | MongoDB                                       | 6（Docker）                                                 |
| 缓存     | Redis                                         | 7（Docker）                                                 |
| 消息队列   | Apache Kafka                                  | 4.1（KRaft，无 Zookeeper）                                    |
| AI     | LangChain4j                                   | 1.14.1                                                    |
| AI     | 对话/Embedding：Ollama（本地）或 DeepSeek + 智谱（云端）    | Profile 切换                                                |
| AI     | 本地精排：ONNX Runtime（bge-reranker-base）          | 1.22                                                      |
| 向量库    | Milvus（SDK 2.6.18）                            | 2.x                                                       |
| OCR    | Tesseract（Tess4j 5.7.0）                       | 需本地安装                                                     |
| 文档解析   | PDFBox / Apache POI                           | -                                                         |
| 认证     | Spring Security + JWT (jjwt)                  | -                                                         |
| 数据库迁移  | Flyway                                        | 11.14.1                                                   |
| 监控     | Spring Boot Actuator + Micrometer（Prometheus） | -                                                         |
| API 文档 | SpringDoc OpenAPI                             | 2.8.13                                                    |
| 其他     | Lombok、Fastjson2、Easypoi、Jsoup、IK Analyzer    | -                                                         |

---

## 系统架构

```
                        ┌──────────────────────────────┐
                        │  前端 mate10-V  (Vue 3 :5173) │
                        │  /api   /rag  (Vite 代理)     │
                        └──────────────┬───────────────┘
                                       │
                                       ▼
                        ┌──────────────────────────────┐
                        │  Mate10 后端  Spring Boot:8080│
                        │  Auth / QC / RAG / Vector /Chat│
                        └───┬───────┬─────────┬────┬────┘
                            │       │         │    │
              ┌─────────────┘       │         │    └─────────┐
              ▼                     ▼         ▼              ▼
         MySQL(业务/QC)        MongoDB        Redis          Kafka
          sys_user / chat      chat_messages             chat-topic
          sys_employee         report_memory             qc-report-topic
          (工号名册)              (结论档案)                (AI 任务异步)
          qc_report/qc_defect                                │ 
          qc_production_param                                │ 
              │                                              ▼
              │                              QC 消费者：解析文件 → AI 识别缺陷
              ▼
         Ollama (localhost:11434) ◄──── embeddingModel ──► Milvus :19530
           deepseek-r1 / all-minilm                            rag_documents
```

**正交维度**

```
╔═══════════════════════════════════════════════════════════════════════╗
║                     维度划分：互不干扰，各自独立                       ║
╚═══════════════════════════════════════════════════════════════════════╝

         维度 A · 环境                            维度 B · AI 能力
      「基础设施在哪」                          「模型跑在哪」

      ┌──────────────┐                          ┌──────────────┐
      │     dev      │  开发环境                 │    ollama    │  本地模型
      │     prod     │  生产环境（正式）          │    cloud     │  云端 API
      └──────────────┘                          └──────────────┘
            │                                         │
            │ 决定                                    │ 决定
            ▼                                         ▼
   ┌──────────────────────┐                 ┌──────────────────────┐
   │  MySQL / MongoDB     │                 │  ChatModel           │
   │  Redis / Kafka       │                 │  StreamingChatModel  │
   │  Milvus 地址          │                │  EmbeddingModel      │
   │  日志级别             │                 │  Milvus 集合名+维度  │
   │  错误详情开关         │                 │                      │
   └──────────────────────┘                 └──────────────────────┘
```

**运行组合**

```
╔═══════════════════════════════════════════════════════════════════════╗
║                          4 种运行组合                                  ║
╚═══════════════════════════════════════════════════════════════════════╝

  ┌────────────────┬──────────────┬─────────────────┬────────────────┐
  │    组合        │  基础设施    │     模型        │     场景       │
  ├────────────────┼──────────────┼─────────────────┼────────────────┤
  │  dev,ollama    │  本机        │  本机 Ollama    │  纯离线开发    │
  ├────────────────┼──────────────┼─────────────────┼────────────────┤
  │  dev,cloud  ◀──┼──────────────┼─────────────────┼────────────────┤
  │                │  本机        │  云端 API       │  ★ 你现在在这  │
  ├────────────────┼──────────────┼─────────────────┼────────────────┤
  │  prod,ollama   │  生产服务器  │  服务器 Ollama  │  私有化部署    │
  ├────────────────┼──────────────┼─────────────────┼────────────────┤
  │  prod,cloud    │  生产服务器  │  云端 API       │  正式上线      │
  └────────────────┴──────────────┴─────────────────┴────────────────┘
```

**配置加载 ( dev,cloud为例 )**

```
╔═══════════════════════════════════════════════════════════════════════╗
║                   配置加载：后面的覆盖前面的                           ║
╚═══════════════════════════════════════════════════════════════════════╝

  ┌─────────────────────────────┐
  │  application.yml            │  永远加载
  │  ─────────────────────────  │
  │  · spring.application.name  │
  │  · spring.profiles.active   │
  │  · Kafka 序列化配置         │
  │  · MyBatis-Plus             │
  │  · app.chat / app.rag       │
  │  · spring.flyway            │
  └──────────────┬──────────────┘
                 │
                 ▼
  ┌─────────────────────────────┐
  │  application-dev.yml        │  active 含 "dev"
  │  ─────────────────────────  │
  │  · MySQL localhost:3306     │
  │  · root / root              │
  │  · MongoDB / Redis / Kafka  │
  │  · Milvus 127.0.0.1:19530   │
  │  · DEBUG 日志               │
  └──────────────┬──────────────┘
                 │
                 ▼
  ┌─────────────────────────────┐
  │  application-cloud.yml      │  active 含 "cloud"
  │  ─────────────────────────  │
  │  · DeepSeek api.deepseek.com│
  │  · 智谱 open.bigmodel.cn    │
  │  · model: deepseek-chat     │
  │  · embedding-3 (2048 维)    │
  │  · Milvus 集合              │
  │    rag_documents_2048       │
  └──────────────┬──────────────┘
                 │
                 ▼
        ┌────────────────────┐
        │   最终生效的配置    │
        └────────────────────┘
```

**关于 @Profile决定Bean的装配**

```
╔═══════════════════════════════════════════════════════════════════════╗
║              LangChain4jConfig.java —— 模型 Bean 的开关                ║
╚═══════════════════════════════════════════════════════════════════════╝

   ┌─────────────────────────┐        ┌─────────────────────────┐
   │   @Profile("ollama")    │        │   @Profile("cloud")     │
   │   ───────────────────   │        │   ───────────────────   │
   │   ChatModel             │        │   ChatModel             │
   │   StreamingChatModel    │  互斥  │   StreamingChatModel    │
   │   EmbeddingModel        │  ◀──▶  │   EmbeddingModel        │
   │                         │        │                         │
   │   实现：Ollama 本地调用  │        │   实现：OpenAI 兼容 HTTP │
   └─────────────────────────┘        └─────────────────────────┘
                    │                            │
                    └──────────┬─────────────────┘
                               ▼
              同一时刻【只有一套】Bean 存在
                               ▼
                  所以不会出现 Bean 冲突 

        active: dev,cloud   →  装配 cloud 那一套 
        active: dev,ollama  →  装配 ollama 那一套 
```

**两个维度唯一耦合点**

```
╔═══════════════════════════════════════════════════════════════════════╗
║         AI profile  ↔  Milvus 集合（这是唯一必须成对变化的地方）       ║
╚═══════════════════════════════════════════════════════════════════════╝

   AI profile      embedding 模型           Milvus 集合
   ───────────     ──────────────────       ───────────────────────
   ollama     →    all-minilm          →    rag_documents
                   (384 维)                 (384 维)

   cloud      →    智谱 embedding-3    →    rag_documents_2048
                   (2048 维)                (2048 维)

                        ↓
        两个集合【完全独立】，互相看不到对方的数据
                        ↓
        切换 AI profile 后，旧集合维度对不上
                        ↓
         必须在 QA 界面重新「初始化质检知识库」
```

**QC 智能分析流水线**

```
上传文件 ─► 保存到 uploads/qc + 创建报告(PENDING) ─► Kafka(qc-report-topic)
                                                          │ 异步消费
                                                          ▼
                提取文本(PDFBox/OCR) ─► Ollama 识别缺陷(JSON) ─► 批量写入 qc_defect
                                                          │
                                                          ▼
                      更新报告 total_defects/severity + 状态 COMPLETED/FAILED
```

**环境维度的实际差异 dev or prod**

```
╔═══════════════════════════════════════════════════════════════════════╗
║                          dev  or  prod                                 ║
╚═══════════════════════════════════════════════════════════════════════╝

   项目              dev                      prod
   ──────────────    ───────────────────      ──────────────────────────
   数据库            localhost:3306           ${MYSQL_URL}
                     写死 root/root           环境变量注入
   
   Redis 连接池      max-active 50            max-active 200
   
   日志              org.mate.mate10: DEBUG   root: warn
   
   错误详情          app.error.detail: true   app.error.detail: false
   
   Tesseract         有默认值                 无默认值 → 缺失即启动失败
   密钥              可写死                   全部环境变量
   
   ⚠️ prod 刻意不给默认值：
      url: ${MYSQL_URL}
        ↓
      配置缺失 → 启动直接失败（好过连到错误的库）✅
```

---

## 快速开始

### 环境要求

| 依赖         | 说明                                                       |
|------------|----------------------------------------------------------|
| JDK 17+    | 编译运行（建议标准 OpenJDK / Temurin；⚠️ 参见「已知事项」的 onnxruntime 说明） |
| Maven 3.8+ | 构建                                                       |
| MySQL 8.0+ | 业务 / QC 数据                                               |
| MongoDB 6  | 聊天消息 + 结论档案                                              |
| Redis 7    | 缓存                                                       |
| Kafka 4.1  | 消息队列                                                     |
| Milvus 2.x | 向量库（RAG）                                                 |
| Ollama     | 本地大模型服务                                                  |
| Tesseract  | 图片 OCR（可选，仅图片质检需要）                                       |

### 1. 启动中间件

中间件已全部 Docker 化（`deploy/docker-compose.yml`）：

```bash
cd deploy
docker compose up -d mysql mongo redis kafka
```

Milvus 使用独立容器组（standalone + etcd + minio）：

```bash
docker run -d --name milvus \
  --network host \
  -p 19530:19530 -p 9091:9091 \
  milvusdb/milvus:v2.6.16 standalone
```

### 2. 启动 Ollama 并准备模型

```bash
ollama serve
ollama pull deepseek-r1:latest   # 对话模型（默认）
ollama pull all-minilm:latest    # embedding 模型（384 维，与 Milvus dimension 对应）
```

### 3. 初始化数据库

**表结构由 Flyway 在应用启动时自动创建**，无需手工建表。

只需先创建一个空库（Flyway 不会创建 database 本身）：

```sql
CREATE
DATABASE IF NOT EXISTS mate10db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

启动应用后 Flyway 会自动完成：

1. 创建版本记录表 `flyway_schema_history`
2. 按版本顺序执行 `db/migration/`

   | 脚本 | 内容 |
   |---|---|
   | `V1__init_schema.sql` | 建表（`sys_user` / `qc_report` / `qc_defect` / `qc_production_param` / `chat` / `chat_record`）|
   | `V2__add_employee_roster.sql` | 员工工号名册 `sys_employee`（`emp_no` 唯一 + `registered` 占用标记）|
   | `V3__add_user_emp_no.sql` | `sys_user.emp_no`（可空 + 唯一）|
   | `V4__fix_user_phone_type.sql` | `sys_user.phone` → `VARCHAR(20)` |
   | `V5__add_user_email_unique.sql` | 补齐 `sys_user.uk_email` |
   | `V6__add_chat_indexes.sql` | 补齐 `chat` 两个索引 |
   | `V7__rename_username_index.sql` | 统一索引命名 `username` → `uk_username` |


**已有数据的库**：Flyway 检测到「库非空但无版本表」时，会自动 baseline（记为已迁移），
**不会重复建表，也不会报错** 

#### 后续如何新增数据库变更

```sql
-- 新建文件：src/main/resources/db/migration/V8__add_report_remark.sql
-- V2 已经被员工工号名册占用,新变更从 V8 开始编号(版本号必须递增且不重复)
ALTER TABLE qc_report
    ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注';
```

重启应用后自动执行 

**三条铁律**：

| 规则                      | 说明              |
|-------------------------|-----------------|
| 文件名必须 `V<版本>__<描述>.sql` | **双下划线**，一个都不能少 |
| 已执行过的脚本**不可修改内容**       | 校验和会变 → 启动直接失败  |
| 要改结构请**新增版本** V3、V4…    | 永远向前，不回头        |

> 所有表统一建在 `mate10db`，不存在跨库查询。

### 4. 配置并运行

关键配置见 `src/main/resources/application.yml`（MySQL/MongoDB 均默认使用 `mate10db`），按本机环境修改后：

```bash
mvn spring-boot:run
# 或构建后运行
mvn clean package -DskipTests
java -jar target/mate10-0.0.1-SNAPSHOT.jar
```

接口文档（已放行匿名访问）：

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api-docs

---

## API 接口

统一响应封装 `Result<T>`：

```json
{
  "code": 200,
  "msg": "成功",
  "data": {}
}
```

> `code=200` 成功，`code=500` 业务失败。以下省略 `Result` 包装层。

### 认证 / 用户管理 `/blocker/user`

| 方法     | 路径                               | 参数                                                 | 说明                                  |
|--------|----------------------------------|----------------------------------------------------|-------------------------------------|
| POST   | `/blocker/user/login`            | `username`,`password`                              | 登录，返回 `token` + `user`              |
| POST   | `/blocker/user/register`         | `username`,`password`,`empNo`,`nickname?`,`email?` | 自动注册 : `empNo`必须在员工工号名册中且没有被CAS原子占用 |
| POST   | `/blocker/user/add`              | body `SysUser`，`role=1`                            | 新增用户（管理员）                           |
| PUT    | `/blocker/user/update`           | body `SysUser`，`role=1`                            | 更新用户                                |
| GET    | `/blocker/user/check-username`   | `username`                                         | 用户名查重                               |
| PUT    | `/blocker/user/update-username`  | `userId`,`username`                                | 改用户名                                |
| PUT    | `/blocker/user/updatePassword`   | `userId`,`oldPassword`,`newPassword`               | 修改密码                                |
| PUT    | `/blocker/user/disable/{userId}` | `role=1`                                           | 禁用用户                                |
| DELETE | `/blocker/user/delete/{userId}`  | `role=1`                                           | 删除用户                                |
| GET    | `/blocker/user/get/{userId}`     | -                                                  | 查询用户                                |

### AI 聊天

| 方法   | 路径                                   | 参数                                             | 说明                                  |
|------|--------------------------------------|------------------------------------------------|-------------------------------------|
| POST | `/ai/chat`                           | body `Chat{userId,title}`                      | 创建会话                                |
| POST | `/ai/chatRecord`                     | body `ChatRequest{message,userId,chatId}`      | 发送消息并返回 AI 回答（存 MongoDB + Kafka 落库） |
| POST | `/ai/chat/stream`                    | body `ChatRequest{message,userId,chatId,mode}` | **SSE 流式**对话，逐字返回                   |
| GET  | `/api/chat/messages/{chatId}`        | -                                              | 按会话查消息                              |
| GET  | `/api/chat/user/{userId}/messages`   | -                                              | 按用户查消息                              |
| GET  | `/api/chat/messages/{chatId}/{role}` | `role=User/ai`                                 | 按会话+角色查消息                           |

>  **`/api/chat/*` 三个查询接口均校验归属**（管理员可查任意）：非本人返回 `code=403` ——
> 避免越权遍历 `chatId` / `userId` 读取他人对话（IDOR）。实现见 `common/CurrentUserProvider`。

### 质检分析 Agent `/ai/qc-agent`

| 方法   | 路径                    | 参数                                         | 说明                         |
|------|-----------------------|--------------------------------------------|----------------------------|
| POST | `/ai/qc-agent`        | body `{"question": "分析 R20260808001 的根因"}` | 非流式，一次性返回结论                |
| POST | `/ai/qc-agent/stream` | 同上（可加 `chatId` / `userId`）                 | **SSE 流式**，实时推送工具调用轨迹 + 结论 |

**流式事件序列**：

```
event:start   data: 问题文本
event:tool    data: {"name":"getReport","args":"{...}","status":"running","result":""}
event:tool    data: {"name":"getReport","status":"done","result":"报告ID: 1 ..."}
event:message data: "## 根因判断 ..."
event:done    data: [DONE]
```

> ⚠️ `message` 事件含换行的 Markdown，会被 SSE 规范拆成多个 `data:` 行 ——
> 前端必须用 `\n` 拼回（见 `mate10-V/src/api/agent.js`）。

### RAG 知识库 `/rag`

| 方法     | 路径                       | 参数                      | 说明          |
|--------|--------------------------|-------------------------|-------------|
| POST   | `/rag/add-text`          | body 纯文本                | 将文本加入知识库    |
| POST   | `/rag/upload`            | `file`（multipart，按文本解析） | 上传文件入知识库    |
| POST   | `/rag/ask`               | body 问题文本               | 通用 RAG 问答   |
| POST   | `/rag/qc-ask`            | body 问题文本               | 质检助手预设上下文问答 |
| POST   | `/rag/init-qc-knowledge` | -                       | 写入内置质检标准知识库 |
| DELETE | `/rag/clear`             | -                       | 清空知识库       |

### 质检报告 `/api/qc/report`

| 方法     | 路径                                     | 参数                                                                                | 说明                                   |
|--------|----------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------|
| POST   | `/api/qc/report/create`                | body `QcReport`                                                                   | 手工创建报告                               |
| POST   | `/api/qc/report/upload`                | `file` + `reportNo/productName/productBatch/productionLine/inspector`，可选 `prompt` | 上传文件并触发异步 AI 分析                      |
| GET    | `/api/qc/report/page`                  | `pageNum`,`pageSize`,`productName?`,`status?`                                     | 分页查询                                 |
| GET    | `/api/qc/report/{id}`                  | -                                                                                 | 报告详情                                 |
| PUT    | `/api/qc/report/{id}/status`           | `status`                                                                          | 更新状态 (需`role=1`)                     |
| DELETE | `/api/qc/report/{id}`                  | -                                                                                 | 删除报告 (需`role=1`)                     |
| GET    | `/api/qc/report/stats/trend`           | `days=7`                                                                          | 按天缺陷趋势                               |
| GET    | `/api/qc/report/stats/production-line` | -                                                                                 | 按生产线统计                               |
| GET    | `/api/qc/report/stats/product-batch`   | `limit=10`                                                                        | 按批次统计                                |
| GET    | `/api/qc/report/{id}/root-cause`       | -                                                                                 | AI 根因分析（需缺陷+生产参数）                    |
| GET    | `/api/qc/report/{reportNo}/audit`      | -                                                                                 | 🔒 **报告审计**（结论版本链 + 讨论记录，需 `role=1`） |

### 缺陷 `/api/qc/defect`

| 方法     | 路径                                 | 参数              | 说明                       |
|--------|------------------------------------|-----------------|--------------------------|
| POST   | `/api/qc/defect/create`            | body `QcDefect` | 新增缺陷                     |
| PUT    | `/api/qc/defect/update`            | body `QcDefect` | 更新缺陷                     |
| DELETE | `/api/qc/defect/{id}`              | -               | 删除缺陷(需`role=1`)          |
| GET    | `/api/qc/defect/report/{reportId}` | -               | 按报告查缺陷                   |
| GET    | `/api/qc/defect/stats/type`        | -               | 缺陷类型统计                   |
| GET    | `/api/qc/defect/stats/severity`    | -               | 严重程度统计                   |
| PUT    | `/api/qc/defect/{id}/confirm`      | -               | 人工确认缺陷                   |
| GET    | `/api/qc/defect/3d/{reportId}`     | -               | ECharts/Three.js 3D 缺陷数据 |

### 生产参数 `/api/qc/param`

| 方法   | 路径                                | 参数                       | 说明       |
|------|-----------------------------------|--------------------------|----------|
| POST | `/api/qc/param/create`            | body `QcProductionParam` | 新增生产参数   |
| PUT  | `/api/qc/param/update`            | body `QcProductionParam` | 更新生产参数   |
| GET  | `/api/qc/param/report/{reportId}` | -                        | 按报告查生产参数 |

### Milvus 通用向量 `/api/vector`

| 方法     | 路径                              | 参数                           | 说明             |
|--------|---------------------------------|------------------------------|----------------|
| POST   | `/api/vector/collection`        | `collName`,`dim`             | 创建集合 + 索引 + 加载 |
| POST   | `/api/vector`                   | `collName`，body 向量数组         | 插入向量           |
| POST   | `/api/vector/search`            | `collName`,`vector`,`topK=5` | 相似度检索（返回 ID）   |
| POST   | `/api/vector/query`             | `collName`，body ID 数组        | 按 ID 查询向量      |
| PUT    | `/api/vector/update`            | `collName`,`id`，body 向量      | 更新（先删后插）       |
| DELETE | `/api/vector/delete`            | `collName`，body ID 数组        | 删除向量           |
| DELETE | `/api/vector/collection`        | `collName`                   | 删除整个集合         |
| GET    | `/api/vector/collection/exists` | `collName`                   | 判断集合是否存在       |

---

## Kafka 主题

| 主题                | 生产者                     | 消费者                     | 用途                |
|-------------------|-------------------------|-------------------------|-------------------|
| `chat-topic`      | `/ai/chatRecord`        | `ChatRecordServiceImpl` | 聊天记录异步落库 + AI 关键词 |
| `qc-report-topic` | `/api/qc/report/upload` | `QcReportServiceImpl`   | 质检报告异步 AI 分析      |

---

## 项目结构

```
src/main/java/org/mate/mate10/
├── Mate10Application.java        # 启动类
├── agent/                        # 质检分析 Agent（L1）
│   ├── QcAgentTools.java         # 工具集（全部只读，无埋点）
│   ├── QcAnalysisAgent.java      # Agent 接口 + SystemMessage
│   ├── AgentFactory.java         # Agent 工厂（每次请求一个实例）
│   ├── AgentTraceListener.java   # 工具执行完成钩子 → SSE
│   └── AgentTraceContext.java    # SSE 事件发送工具
├── common/
│   ├── Result.java               # 统一响应封装 {code,msg,data}
│   ├── CurrentUserProvider.java  # 取当前登录用户（username → userId，供归属校验）
│   ├── MemoryChangeReason.java   # 结论覆盖原因常量
│   ├── RegisterRejectedException.java  # 注册被拒（工号不存在 / 已被占用）
│   ├── EvictQcCache.java         # QC 缓存失效组合注解（@CacheEvict allEntries）
│   └── GlobalExceptionHandler.java  # 全局异常处理（统一错误响应）
├── config/
│   ├── SecurityConfig.java           # Security（JWT 无状态认证）+ BCrypt
│   ├── JwtAuthenticationFilter.java  # JWT 解析过滤器（写入 SecurityContext）
│   ├── CorsConfig.java               # 跨域（Vite 5173 白名单）
│   ├── KafkaConfig.java              # Kafka 生产者/消费者/监听容器
│   ├── LangChain4jConfig.java        # 按 @Profile 装配 Chat/Embedding + Milvus 存储
│   ├── ChatModelFactory.java         # 对话模型工厂（ollama / cloud 双实现）
│   ├── CloudAiProperties.java        # cloud-ai.* 配置映射
│   ├── OllamaProperties.java         # ollama.* 配置映射
│   ├── ChatContextBuilder.java       # 上下文组装（对话上下文 + Agent 历史，含路由）
│   ├── ChatProperties.java           # app.chat.* 配置映射
│   ├── RagProperties.java            # app.rag.* 配置映射（分块 / 混合检索 / 重排）
│   ├── PromptLoader.java             # 提示词模板加载
│   ├── MilvusConfig.java             # MilvusClient Bean
│   ├── MilvusProperties.java         # milvus.* 配置映射
│   ├── MyBatisConfig.java            # MyBatis-Plus
│   ├── RedisConfig.java              # RedisTemplate + 缓存管理（immediateWrites）
│   ├── TesseractConfig.java          # tesseract.* 配置映射（OCR）
│   └── qc/
│       └── QcAgentConfig.java        # 按 profile 装配 Agent，注册轨迹钩子
├── controller/
│   ├── ChatController.java       # /ai/chat、/ai/chatRecord
│   ├── MongoChatController.java  # /api/chat/*
│   ├── RagController.java        # /rag/*
│   ├── SysUserController.java    # /blocker/user/*
│   ├── VectorController.java     # /api/vector/*
│   └── qc/
│       ├── QcDefectController.java
│       ├── QcProductionParamController.java
│       ├── QcReportController.java      # 含 /{reportNo}/audit（报告审计）
│       └── QcAgentController.java       # /ai/qc-agent
├── document/
│   ├── ChatMessageDocument.java   # MongoDB chat_messages（会话事件流）
│   └── ReportMemoryDocument.java  # MongoDB report_memory（结论快照）
├── dto/                          # ChatRequest / ChatMessageResponse / CreateChat / ReportAuditDTO / RegisterRequest
├── entity/                       # SysUser / SysEmployee /Chat / ChatRecord
│   └── qc/                       # QcReport / QcDefect / QcProductionParam
├── mapper/
│   ├── SysUserMapper.java / SysEmployeeMapper.java（含 CAS 原子占用工号）
│   ├── ChatMapper.java / ChatRecordMapper.java
│   └── qc/                       # QC 三个 Mapper（注解 SQL + 统计）
├── repository/
│   ├── ChatMessageRepository.java    # Spring Data MongoDB Repository
│   └── ReportMemoryRepository.java   # 结论档案查询（含审计用的全版本查询）
├── service/
│   ├── ChatService / ChatRecordService
│   ├── FileParseService          # PDFBox / OCR 文件解析
│   ├── MongoChatMessageService
│   ├── RagService
│   ├── ReportMemoryService       # 结论档案：抽段 + 覆盖写入 + 读最新
│   ├── ReportAuditService        # 报告审计：结论版本链 + 讨论记录
│   ├── ReportDataFingerprintService  # 数据指纹：dataVersion 计算（二期）
│   ├── Reranker                  # 重排接口（CrossEncoderReranker 实现）
│   ├── SysUserService
│   ├── VectorService
│   └── qc/                       # QC 三个 Service 接口
│       └── Impl/                 # 各实现（Kafka 消费 / AI 分析在此）
└── util/
    └── JwtUtil.java              # JWT 生成/解析
```

---

## 配置说明

`src/main/resources/application.yml` 关键项：

```yaml
spring:
  datasource:            # MySQL，默认库 mate10db
  mongodb:               # MongoDB uri，默认 mate10db
  data.redis:            # Redis 连接池
  kafka:                 # bootstrap-servers: localhost:9094（Docker 化，宿主机专用 listener）

mybatis-plus:            # mapper-locations、驼峰映射、日志
# 因使用手写 sqlSessionFactory（绕 SB4 兼容问题），
# 此块配置【不生效】；插件需在 MyBatisConfig 手动 setPlugins
ollama: # 本地大模型
  base-url: http://localhost:11434
  default-model: deepseek-r1:latest    # 对话模型
  agent-model: llama3.1:latest         # Agent 专用（deepseek-r1 工具调用能力弱）
  embedding-model: all-minilm:latest   # embedding 模型

milvus: # 向量库（集合名与维度由 AI profile 决定，两者必须成对）
  host: 127.0.0.1
  port: 19530
  dimension: 2048                      # cloud: 2048 / ollama: 384
  collection-name: rag_documents_2048  # cloud: rag_documents_2048 / ollama: rag_documents

app:
  error:
    detail: true         # 错误详情开关：dev=true（方便定位）/ prod=false（不暴露内部）
    # ↑ SSE 的 error 事件也走这个开关（和 GlobalExceptionHandler 共用）
  chat: # 对话与 Agent 历史预算
    max-history-messages: 10           # 对话上下文：最多取几条
    max-history-chars: 2000            # 对话上下文：字符预算
    agent-session-limit: 4             # Agent 会话分支：取几条
    agent-history-chars: 3000          # Agent 历史参考块：字符预算
  rag:
    top-k: 10                          # 检索返回片段数
    hybrid-enabled: true               # 混合检索（向量 + BM25）
    rerank:
      enabled: true                    # ⚠️ 开启时【必须】模型文件存在，否则启动失败
      model-path: "D:/AIGC/bge-reranker-base/onnx/model_quantized.onnx"
      tokenizer-path: "D:/AIGC/bge-reranker-base/tokenizer.json"
      recall-factor: 3                 # 先召回 topK×3，再精排取 topK
      max-length: 510

tesseract: # 图片 OCR（需本机安装 Tesseract + 中文语言包 chi_sim）
  path: "I:\\OCR\\tesseract-main"
  data-path: "I:\\OCR\\tesseract-main\\tessdata"
  language: "chi_sim"
```

> ⚠️ **`app.rag.rerank.enabled: true` 时，模型文件必须存在** ——
> `CrossEncoderReranker` 在构造函数里校验，文件缺失会**直接让应用启动失败**。
> 容器部署时尤其注意（容器里默认没有 `D:/AIGC/...`），详见 `deploy/README.md`。

---

## 监控与可观测性

基于 **Spring Boot Actuator + Micrometer**，指标以 Prometheus 格式导出。

### Actuator 端点

| 端点                     | 访问控制 | 说明                                          |
|------------------------|------|---------------------------------------------|
| `/actuator/health`     | 免认证  | 健康检查（含 db / mongo / redis / diskSpace 组件明细） |
| `/actuator/prometheus` | 免认证  | Prometheus 指标抓取（裸文本格式）                      |
| `/actuator/info`       | 需认证  | 应用信息                                        |
| `/actuator/metrics`    | 需认证  | 指标列表与单项查询                                   |
| `/actuator/flyway`     | 需认证  | 数据库迁移状态                                     |
| `/actuator/loggers`    | 需认证  | 运行时动态调整日志级别                                 |

> ️ **只放行 `health` 与 `prometheus`**，其余端点保持需要认证。
> `/actuator/heapdump`（堆内存快照）、`/actuator/env`（含密钥的配置）一旦公开会泄露敏感信息。

### 暴露的关键指标

| 指标                             | 含义                         |
|--------------------------------|----------------------------|
| `jvm_memory_used_bytes`        | JVM 各内存区使用量                |
| `jvm_gc_pause_seconds`         | GC 停顿时间                    |
| `http_server_requests_seconds` | HTTP 接口耗时直方图（可算 P95 / P99） |
| `hikaricp_connections_active`  | 数据库连接池活跃连接数                |
| `system_cpu_usage`             | 系统 CPU 使用率                 |

### 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,flyway,loggers
  prometheus:
    metrics:
      export:
        enabled: true
```

`health.show-details` 按环境区分（见 `application-dev.yml` / `application-prod.yml`）：

| 环境     | 值                 | 原因                 |
|--------|-------------------|--------------------|
| `dev`  | `always`          | 方便本地调试，直接看到各组件状态   |
| `prod` | `when-authorized` | 不对外暴露数据库地址、磁盘路径等细节 |

### 接入 Prometheus + Grafana

`deploy/docker-compose.yml` 已包含 `prometheus` 与 `grafana` 两个服务：

- Prometheus 通过 **Docker 内网** `backend:8080` 抓取指标 —— **不经过 Nginx，也不暴露公网**
- 抓取配置见 `deploy/prometheus.yml`（`scrape_interval: 15s`）
- Grafana 暴露在 `3000` 端口用于查看仪表盘

> 若将来把 Prometheus 部署到**外部机器**，则需要在 Nginx 层限制来源 IP
> （`allow <Prometheus IP>; deny all;`），因为此时流量会经过 Nginx。

---

## 质检分析 Agent

基于 **LangChain4j `@Tool`** 实现 L1 Agent：把质检数据查询封装为**工具**，
由**模型自主决定**调哪些工具、以什么顺序调查。

### 与 L0（固定管道）的区别

|      | L0：`GET /api/qc/report/{id}/root-cause` | L1：`POST /ai/qc-agent` |
|------|-----------------------------------------|------------------------|
| 控制流  | **开发者**（顺序写死在代码里）                       | **模型**（自主规划）           |
| 灵活性  | 只能按预设路径                                 | 可按情况调整调查策略             |
| 可观测性 | 无中间过程                                   | **SSE 实时推送工具调用轨迹**     |

### 组件

| 组件                                     | 职责                                  |
|----------------------------------------|-------------------------------------|
| `agent/QcAgentTools.java`              | 工具集（**全部只读**，**不含任何埋点代码**）          |
| `agent/QcAnalysisAgent.java`           | Agent 接口 + SystemMessage（含"禁止臆测"约束） |
| `agent/AgentFactory.java`              | Agent 工厂 —— 每次请求创建一个实例              |
| `agent/AgentTraceListener.java`        | 工具执行**完成**钩子 → 推送 SSE               |
| `agent/AgentTraceContext.java`         | SSE 事件发送工具（无状态）                     |
| `config/qc/QcAgentConfig.java`         | 按 profile 装配，注册两个轨迹钩子               |
| `controller/qc/QcAgentController.java` | HTTP 入口                             |

### 可用工具（全部只读）

| 工具                          | 底层方法                            | 用途                    |
|-----------------------------|---------------------------------|-----------------------|
| `getReportByNo(reportNo)`   | `QcReportService.getByReportNo` | 按业务编号（`R2026…`）换数字 ID |
| `getReport(reportId)`       | `QcReportService.getById`       | 报告背景                  |
| `getDefects(reportId)`      | `QcDefectService.getByReportId` | 缺陷构成                  |
| `searchKnowledge(question)` | `RagService.retrieve`           | 检索质量标准（只给素材，不让它二次生成）  |

> ️ **安全边界**：`deleteReport` / `updateStatus` / `confirmDefect` **一律不暴露** ——
> 即使被提示词注入，模型也只能读不能改。

### 模型选择（Agent 需要 Function Calling）

| profile  | Agent 用的模型                                                        |
|----------|-------------------------------------------------------------------|
| `ollama` | `ollama.agent-model`（默认 `llama3.1`）—— `deepseek-r1` 是推理模型，工具调用能力弱 |
| `cloud`  | `cloud-ai.chat.model`（`deepseek-chat`，原生支持 Function Calling）      |

### 轨迹埋点（零侵入，不碰工具代码）

用 `AiServices` 的两个**官方钩子**：

```java
AiServices.builder(QcAnalysisAgent .class)
.chatModel(model)
.tools(tools)
.beforeToolExecution(b ->{...})                   // 工具开始执行
 .registerListener(new AgentTraceListener(emitter))   // 工具执行完成（含结果）
 .build();
```

**emitter 怎么传** —— `AgentFactory` + **闭包捕获**（每次请求建一个 Agent 实例）：

```java

@Bean
public AgentFactory cloudAgentFactory(QcAgentTools tools) {
    ChatModel model = ...;                                // 模型只建一次
    return emitter -> buildAgent(model, tools, emitter);  // 闭包捕获 emitter
}
```

**为什么不用 ThreadLocal**：`executeToolsConcurrently()` 或流式模型下工具可能在别的线程执行，
ThreadLocal 会**静默失效**（无报错，最难排查）。

### 实测日志

```
[Tool] getReportByNo(reportNo=R20260811001)   ← 模型决定先换 ID
[Tool] getReportByNo 完成
[Tool] getReport(reportId=10)                 ← 拿到 ID=10，继续
[Tool] getDefects(reportId=10)
[Tool] getReport 完成
[Tool] getDefects 完成
Agent 流式分析完成，耗时 5215 ms
```

**判断标准**：`[Tool]` 出现多条且顺序不固定 = 模型在自主决策

### 三方对比实验（同一份报告）

**实验条件**：同一报告 `R20260812002`（6 缺陷 / 严重）、同一问题（"分析 R20260812002 的根因"）、各跑 3 次取平均。

| 维度           | ① RAG 问答     | ② L0 固定管道    | ③ **L1 Agent** |
|--------------|--------------|--------------|---------------|
| 数据源          | 知识库          | 数据库          | **数据库 + 知识库** |
| **⏱ 耗时（平均）** | **3,711 ms** | **6,038 ms** | **10,706 ms** |
| 模型调用轮数       | 1            | 1            | **3**         |
| 工具调用次数       | 0            | 0            | **7**         |
| 总 token      | —            | —            | **9,141**     |
| 输出长度         | ~615 字符      | ~6,400 字符    | ~4,750 字符     |
| 输出格式         | 纯文本          | **JSON**     | 纯文本           |
| 实测参数引用       | ❌            | ✅            | ✅             |
| 知识库标准引用      | ✅            | ❌            | ✅             |
| **多参数交叉分析**  | ❌            | ❌            | ✅             |
| 处理建议         | ⚠️ 未给出       | ✅            | ✅             |
| 信息缺口说明       | ❌            | ❌            | ✅             |

**四个关键发现**：

1. **耗时**：`RAG (3.7s) < L0 (6.0s) < L1 (10.7s)` —— L1 是 RAG 的 **2.9 倍**
   （L1 调 3 轮模型，每轮都要重发历史消息 → token 成本**非线性增长**）
2. **L0 输出最长（6,400 字符）但归因单一** —— 6 条缺陷**全部**归因到 `temperature`，
   且不查知识库 → 没有标准依据
3. **RAG 输出最短（615 字符）** —— 只复述了案例原文，**没有判断和建议**
4. **L1 唯一能做交叉分析**  —— 自主对比四项工艺参数，发现
   "温度 / 压力 / 湿度 / 速度**全部超出正常区间**"
   → 得出"**系统性工艺失控**"而非"单点故障"的结论（L0 和 RAG 都给不出）

**结论：三者适用场景不同，不是替代关系**

| 场景       | 推荐      | 理由                      |
|----------|---------|-------------------------|
| 批量、标准化分析 | **L0**  | JSON 结构固定、可编程消费、成本低     |
| 知识检索问答   | **RAG** | 最快（3.7s），直接命中知识库        |
| 复杂根因调查   | **L1**  | 数据 + 知识 + 交叉分析，结论质量不可替代 |
| 交互式探索    | **L1**  | 自然语言输入、可追问、有工具轨迹展示      |

---

## AI 记忆与审计

### 两层记忆：会话事件流 + 结论快照

|     | 第一层：会话事件流       | 第二层：结论快照                       |
|-----|-----------------|--------------------------------|
| 集合  | `chat_messages` | `report_memory`                |
| 键   | `chatId`        | `reportNo`（业务编号）               |
| 存什么 | 原始问答**全文**      | 抽取的**结论**（conclusion + advice） |
| 用途  | 会话上下文 · 审计可追溯   | 团队共享的「**当前认识**」                |
| 增长  | 只增不减            | **每份报告恒定 1 条有效**               |

**问题**：早期把「对话」当知识存 → 问 100 次就堆 100 组问答，噪音**线性增长**，
注入模型的上下文越来越长。

**解法**：改成「结论快照」——

- 从 Agent 回答中切出 **`## 根因判断`** 段蒸馏入档（**零额外模型调用**，复用已强制的四段输出）
- **覆盖式写入**：新结论把旧结论标 `superseded=true`（留痕不删 → 审计可追溯），读取只认最新
- 结果：**注入长度恒定**，噪音有界

**天然质量门**：切不到 `## 根因判断` 的回答（「之前分析出什么了」这类元提问、闲聊、标准问答）
**自动不入档**。

**路由规则**（`ChatContextBuilder.buildAgentHistory`）：

```
问句含报告编号（\bR\d{11}\b）  →  读【结论档案】report_memory（团队共享、跨会话）
问句不含编号                  →  读【本次会话上文】chat_messages（按 chatId）
```

### 报告审计（可追溯）

回答一个问题：**这份报告的判定是怎么来的？**

`GET /api/qc/report/{reportNo}/audit`  需 `role=1`

| 返回字段                 | 含义             | 数据来源                      |
|----------------------|----------------|---------------------------|
| `versionCount`       | 结论改过几次         | `report_memory`           |
| `conclusionTimeline` | 结论版本链（含被覆盖的历史） | `report_memory`           |
| `current`            | 当前有效结论         | `report_memory`           |
| `discussions`        | 谁在什么时候讨论过这份报告  | `chat_messages.report_no` |

**设计原则**（本实现已符合）：

| 原则            | 实现                            |
|---------------|-------------------------------|
| 只追加、不改历史      | 旧结论标 `superseded`（软删除）        |
| 记录变更前后        | 版本链天然就是 before/after          |
| 记录 who / when | `analyzed_by` / `analyzed_at` |
| 用服务端时间        | `LocalDateTime.now()`         |
| 可查询           | 审计接口 + 版本链查询                  |

**组件**：`ReportMemoryService` / `ReportAuditService` / `ReportMemoryDocument` / `ReportAuditDTO`

**边界行为**：该报告无任何记录时返回空轨迹（`versionCount=0`、`current=null`），**不抛异常**。

>  数据库层面建议加**部分唯一索引**兜底并发写坏：
> ```javascript
> db.report_memory.createIndex(
>   { report_no: 1 },
>   { unique: true, partialFilterExpression: { superseded: false } }
> )
> ```

---

## RAG 重排（Cross-Encoder Rerank）

混合检索（向量 + BM25 → 融合）之后，再加一层 **cross-encoder 精排**，
用「更慢但更准」的模型把召回结果重排到 Top-K。

### 链路

```
召回 topK × 3 → 按文本去重 → CrossEncoderReranker（bge-reranker-base）精排 → Top-K
```

### 组件

| 组件                                       | 职责                                                   |
|------------------------------------------|------------------------------------------------------|
| `service/Reranker.java`                  | 重排接口（`rerank(query, candidates, topK)`）              |
| `service/Impl/CrossEncoderReranker.java` | 包装 LangChain4j `OnnxScoringModel`（bge-reranker-base） |
| `HybridRetriever`                        | 多召回 + 去重 + 调 reranker（Bean 不存在时回退 RRF）               |

### 依赖与模型

- 依赖：`langchain4j-onnx-scoring`（传递引入 `onnxruntime` + DJL `tokenizers`）
- 模型：`bge-reranker-base`（int8 量化 266MB + `tokenizer.json`），放 `D:/AIGC/bge-reranker-base/`，
  从 ModelScope 下载（hf-mirror 本机连不上）
- 配置：`app.rag.rerank.enabled`（开关）/ `model-path` / `tokenizer-path` / `recall-factor`（默认 3）/ `max-length`

> ️ **模型文件缺失会让应用启动失败**（构造函数里校验并 throw）。
> 容器部署时请显式关闭（`APP_RAG_RERANK_ENABLED=false`）或挂载模型，详见 `deploy/README.md`。

### 效果（A/B 对比，30 题评测集）

|     | Rerank 关（RRF） | Rerank 开（Cross-Encoder） |
|-----|---------------|-------------------------|
| 命中率 | 96.7%（29/30）  | **100.0%（30/30）**       |
| 耗时  | 8.1s          | 145.3s（CPU 推理）          |

> **经典权衡**：cross-encoder 用「延迟换精度」。它只对召回后的 Top-N 精排，不全量重排，
> 所以是「最后一道精排」。未来优化方向：GPU 推理 / 减小精排候选数 / 批处理缓存。

---

## 性能压测实验

> 2026-10 使用 JMeter 5.6.3 对核心接口进行的系统性压测。
> 环境：i7-13650HX（14 物理核 6P+8E / 20 逻辑核），后端本机运行（cloud profile），
> 中间件混合部署（MySQL / MongoDB / Redis 本机，Kafka / Milvus Docker）。
> 方法：GUI 搭脚本 → 命令行执行（`jmeter -n -t <脚本> -l <结果> -e -o <报告>`）→ HTML 报告分析。

### 实验数据

| 接口                         | 场景               | 数据来源             | 平均响应                       |
|----------------------------|------------------|------------------|----------------------------|
| `GET /api/qc/report/{id}`  | 热缓存              | Redis            | **3ms**                    |
| `GET /api/qc/report/{id}`  | 冷缓存              | MySQL 单条         | **7ms**                    |
| `GET /api/qc/report/page`  | 分页（数据量小）         | MySQL count + 列表 | **3ms**                    |
| `POST /blocker/user/login` | BCrypt 校验（10 并发） | 本地 CPU           | **116ms**                  |
| `POST /blocker/user/login` | BCrypt 校验（50 并发） | 本地 CPU           | **231ms**（CPU 100%，QPS 81） |
| `POST /ai/chatRecord`      | 短回答（cloud）       | DeepSeek API     | **559ms**                  |
| `POST /ai/chatRecord`      | 长回答（cloud）       | DeepSeek API     | **9030ms**                 |

### 关键结论

1. **缓存价值**：冷/热对照差值 4ms（响应降低 57%）。
   收益公式 `(MySQL 耗时 − Redis 往返) × 命中率` —— 数据量小时收益有限，
   缓存的真正威力在复杂查询 / 大数据量 / 远程数据库场景。
2. **CPU 密集接口的 QPS 天花板**：50 并发压 BCrypt，CPU 打满 100%，
   实测 QPS 81 ≈ `CPU 等效核数 ÷ 单次计算耗时`。CPU 密集加线程只会排队，
   扩容靠加机器，不靠加线程。
3. **三种性能型态**：IO 密集（3ms，等资源）、CPU 密集（116ms，算）、
   外部依赖（9s，远程等）。「等」的接口加线程能提吞吐，「算」的接口不能。
4. **AI 接口性能公式**：`响应时间 ≈ 回答 token 数 ÷ 生成速度`
   （实测 DeepSeek ≈ 33 token/s）。系统优化无法突破模型侧瓶颈，
   流式输出（边生成边返回）是体验的关键。
5. **GUI vs 命令行**：GUI 模式自身开销污染压测数据（同场景 598ms → 231ms），
   正式压测必须用命令行模式。
6. **利特尔法则实证**：`并发在途 = QPS × 响应时间`
   （81 × 0.598 ≈ 48 个在途请求），与实测吻合。

### 踩坑记录

| 现象                  | 根因                         | 教训                 |
|---------------------|----------------------------|--------------------|
| 400 Bad Request     | Header 名称带尾随空格，违反 RFC 9112 | Header 名称字段必须干净无空格 |
| 压测数字快得不合理（4ms）      | GET 打 POST 接口，方法匹配层被拒      | 数字异常时先查响应内容再下结论    |
| 首个请求 34ms vs 后续 2ms | 连接池 / JIT 预热效应             | 压测前先热身，丢弃前几次样本     |

---

## 已知事项 / Roadmap

### 待完善

- **文件解析**：`FileParseServiceImpl` 已实现 PDF（PDFBox）与图片 OCR（Tess4j）；
  Word / Excel 解析方法为预留（返回空串），上传 Word/Excel 时 AI 无法提取内容。
- **上传文件清理**：质检报告文件保存在运行目录 `uploads/qc/`（相对路径），
  部署时需注意目录挂载与磁盘清理策略。
- ️ **`ChatController` 的 `chatId` / `userId` 来自请求体、未校验归属**
  （与已修复的 `/api/chat/*` 三个查询接口不同 —— 那三个已加归属校验）。
- ️ **Kafka 消费者缺少幂等与失败重试**：不先删旧缺陷、异常被吞 →
  重复投递会让缺陷数翻倍、失败消息永久丢失。
- ️ **索引缺失**：`chat_messages`（Mongo）、`qc_report.report_no`（MySQL）。

### 设计说明（非缺陷）

- **认证与鉴权**：`SecurityConfig` 采用 JWT **无状态认证**（`SessionCreationPolicy.STATELESS`），
  由 `JwtAuthenticationFilter` 解析 token 并写入 `SecurityContext`。
  仅放行登录、注册、Swagger，以及 Actuator 的 `health` / `prometheus`，其余接口均需认证。
  未认证返回 **401**、无权限返回 **403**（由 `authenticationEntryPoint` / `accessDeniedHandler` 分别处理）。
- **方法级权限**：`@EnableMethodSecurity` + `@PreAuthorize`。例如报告审计接口要求
  `hasRole('ADMIN')`（`role=1`），普通账号返回 403。
- **密钥外部化**：`JwtUtil` 通过 `@Value("${JWT_SECRET_KEY}")` 从环境变量注入，
  并在 `@PostConstruct` 中做启动校验（缺失或不足 32 字节直接启动失败）。
- **数据库**：所有表统一建在 `mate10db`，**不存在跨库查询**。
- **Redis 缓存**：两级缓存（`qcStats` 5min / `qcDetail` 30min），
  读用 `@Cacheable`、写用 `@EvictQcCache` 组合注解（粗粒度失效，避免漏清）。
  缓存序列化用 `GenericJacksonJsonRedisSerializer.builder().enableDefaultTyping(...)`
  —— 必须写 `@class`，否则反序列化会退化成 `LinkedHashMap`。
  写入器用 `RedisCacheWriter.create(connectionFactory, b -> b.immediateWrites())`
  —— **必须**：Spring Data Redis 3.x+ 默认的批量写入会让 `clear()`（`allEntries=true`）**静默失效**。
- **两层记忆**：`chat_messages`（会话事件流）+ `report_memory`（结论快照，覆盖式写入，旧版留痕），
  路由由问句是否含报告编号决定。详见「AI 记忆与审计」章节。
- **混合检索 + 重排**：向量（语义）+ Lucene BM25（精确标识符）→ 按文本去重 → cross-encoder 精排。
  关键词索引持久化到 `./data/lucene-index`（与 Milvus 各自持久化），
  可通过 `app.rag.hybrid-enabled` 关闭退回纯向量；`app.rag.rerank.enabled` 关闭退回 RRF 融合。
- **JDK 选择**：本地开发用 **Microsoft Build of OpenJDK 17**（标准 HotSpot），
  **不要用 GraalVM** —— Rerank 的 `onnxruntime` 原生库在 GraalVM 的 `jvmci` 编译器下会报
  `DLL初始化例程失败` / `ACCESS_VIOLATION`。容器镜像用的是 `eclipse-temurin:17-jre`（同为 HotSpot）
- **Profile 优先级**（从高到低）：
  `命令行参数` > `JVM 系统属性 (-D...)` > `操作系统环境变量` > `application.yml`