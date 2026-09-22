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
- 提问时检索 top-3 相关片段作为上下文，由对话模型生成答案
- 支持初始化「质检标准知识库」、按预设质检助手身份问答（`/rag/qc-ask`）
- 一键清空知识库

### 3. Milvus 通用向量检索
- 直接封装 Milvus SDK，提供集合 CRUD、向量插入/删除/更新、按 ID 查询、相似度检索的通用 REST 接口

### 4. AI 聊天（基础能力）
- 与 Ollama 本地模型（默认 `deepseek-r1`）对话
- 用户/AI 消息实时写入 MongoDB（`chat_messages`）
- 通过 Kafka（`chat-topic`）异步落库 `chat_record`，并由 AI 抽取对话关键词
- 按会话/用户/角色查询历史消息

### 5. 用户认证与管理
- Spring Security + BCrypt + JWT（登录签发 token，默认 7 天有效）
- 用户登录（用户名或邮箱）、新增/更新/禁用/删除（管理操作需 `role=1`）、改密码、查重

### 6. 质检分析 Agent（L1，模型自主决策）
- 基于 LangChain4j `@Tool`：把质检数据查询封装为**工具**，由**模型自主决定**调哪些、什么顺序
- 支持 **SSE 流式**推送**工具调用轨迹**（前端实时看到 Agent 在做什么）
- 与固定管道（L0）形成对照：`GET /api/qc/report/{id}/root-cause` 顺序写死；Agent 则控制流在模型手里
- **安全边界**：只暴露只读工具，即使被提示词注入，模型也只能读不能改
- 零新增依赖，零侵入（工具代码不含任何埋点）

---

## 技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| 语言 | Java | 17 |
| 框架 | Spring Boot | 4.0.0 |
| ORM | MyBatis-Plus | 3.5.16 |
| 数据库 | MySQL | 8.0+ |
| 数据库 | MongoDB | 4.0+ |
| 缓存 | Redis | 6.0+ |
| 消息队列 | Apache Kafka | 4.1（KRaft，无 Zookeeper） |
| AI | LangChain4j | 1.14.1 |
| AI | 对话/Embedding：Ollama（本地）或 DeepSeek + 智谱（云端） | Profile 切换 |
| 向量库 | Milvus（SDK 2.6.18） | 2.x |
| OCR | Tesseract（Tess4j 5.7.0） | 需本地安装 |
| 文档解析 | PDFBox / Apache POI | - |
| 认证 | Spring Security + JWT (jjwt) | - |
| 数据库迁移 | Flyway | 11.14.1 |
| 监控 | Spring Boot Actuator + Micrometer（Prometheus） | - |
| API 文档 | SpringDoc OpenAPI | 2.8.13 |
| 其他 | Lombok、Fastjson2、Easypoi、Jsoup、IK Analyzer | - |

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
                        └───┬───────┬────────┬────┬────┘
                            │       │        │    │
              ┌─────────────┘       │        │    └───────────────┐
              ▼                     ▼        ▼                    ▼
         MySQL(业务/QC)        MongoDB      Redis           Kafka
          sys_user / chat      chat_messages                chat-topic
          qc_report/qc_defect                              qc-report-topic
          qc_production_param                               │ (AI 任务异步)
              │                                            ▼
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

| 依赖 | 说明 |
| --- | --- |
| JDK 17+ | 编译运行 |
| Maven 3.8+ | 构建 |
| MySQL 8.0+ | 业务 / QC 数据 |
| MongoDB 4.0+ | 聊天消息 |
| Redis 6.0+ | 缓存 |
| Kafka 3.0+ | 消息队列 |
| Milvus 2.x | 向量库（RAG） |
| Ollama | 本地大模型服务 |
| Tesseract | 图片 OCR（可选，仅图片质检需要） |

### 1. 启动中间件

MySQL / MongoDB / Redis / Kafka 按常规方式启动。向量库可使用 Docker 快速起（standalone 模式依赖 etcd + minio）：

```bash
# 方式一：使用 milvus 官方 standalone docker-compose 编排
# 参考 https://milvus.io/docs/install_standalone-docker.md

# 方式二：仅起 Milvus 主服务（需要已有 etcd/minio 或本机已装）
docker run -d --name milvus \
  --network host \
  -p 19530:19530 -p 9091:9091 \
  milvusdb/milvus:v2.4.x standalone
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
CREATE DATABASE IF NOT EXISTS mate10db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

启动应用后 Flyway 会自动完成：

1. 创建版本记录表 `flyway_schema_history`
2. 执行 `db/migration/V1__init_schema.sql` 建表
   （`sys_user` / `qc_report` / `qc_defect` / `qc_production_param` / `chat` / `chat_record`）

**已有数据的库**：Flyway 检测到「库非空但无版本表」时，会自动 baseline（记为已迁移），
**不会重复建表，也不会报错** ✅

#### 后续如何新增数据库变更

```sql
-- 新建文件：src/main/resources/db/migration/V2__add_report_remark.sql
ALTER TABLE qc_report ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注';
```

重启应用后自动执行 ✅

**三条铁律**：

| 规则 | 说明 |
|---|---|
| 文件名必须 `V<版本>__<描述>.sql` | **双下划线**，一个都不能少 |
| 已执行过的脚本**不可修改内容** | 校验和会变 → 启动直接失败 ❌ |
| 要改结构请**新增版本** V3、V4… | 永远向前，不回头 ✅ |

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
{ "code": 200, "msg": "成功", "data": {} }
```

> `code=200` 成功，`code=500` 业务失败。以下省略 `Result` 包装层。

### 认证 / 用户管理 `/blocker/user`

| 方法     | 路径                               | 参数                                   | 说明                     |
|--------|----------------------------------|--------------------------------------|------------------------|
| POST   | `/blocker/user/login`            | `username`,`password`                | 登录，返回 `token` + `user` |
| POST   | `/blocker/user/add`              | body `SysUser`，`role=1`              | 新增用户（管理员）              |
| PUT    | `/blocker/user/update`           | body `SysUser`，`role=1`              | 更新用户                   |
| GET    | `/blocker/user/check-username`   | `username`                           | 用户名查重                  |
| PUT    | `/blocker/user/update-username`  | `userId`,`username`                  | 改用户名                   |
| PUT    | `/blocker/user/updatePassword`   | `userId`,`oldPassword`,`newPassword` | 修改密码                   |
| PUT    | `/blocker/user/disable/{userId}` | `role=1`                             | 禁用用户                   |
| DELETE | `/blocker/user/delete/{userId}`  | `role=1`                             | 删除用户                   |
| GET    | `/blocker/user/get/{userId}`     | -                                    | 查询用户                   |

### AI 聊天

| 方法   | 路径                                   | 参数                                        | 说明                                  |
|------|--------------------------------------|-------------------------------------------|-------------------------------------|
| POST | `/ai/chat`                           | body `Chat{userId,title}`                 | 创建会话                                |
| POST | `/ai/chatRecord`                     | body `ChatRequest{message,userId,chatId}` | 发送消息并返回 AI 回答（存 MongoDB + Kafka 落库） |
| POST | `/ai/chat/stream`                    | body `ChatRequest{message,userId,chatId,mode}` | **SSE 流式**对话，逐字返回                |
| GET  | `/api/chat/messages/{chatId}`        | -                                         | 按会话查消息                              |
| GET  | `/api/chat/user/{userId}/messages`   | -                                         | 按用户查消息                              |
| GET  | `/api/chat/messages/{chatId}/{role}` | `role=User/ai`                            | 按会话+角色查消息                           |

### 质检分析 Agent `/ai/qc-agent`

| 方法 | 路径 | 参数 | 说明 |
|---|---|---|---|
| POST | `/ai/qc-agent` | body `{"question": "分析 R20260808001 的根因"}` | 非流式，一次性返回结论 |
| POST | `/ai/qc-agent/stream` | 同上 | **SSE 流式**，实时推送工具调用轨迹 + 结论 |

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

| 方法     | 路径                                     | 参数                                                                                | 说明                |
|--------|----------------------------------------|-----------------------------------------------------------------------------------|-------------------|
| POST   | `/api/qc/report/create`                | body `QcReport`                                                                   | 手工创建报告            |
| POST   | `/api/qc/report/upload`                | `file` + `reportNo/productName/productBatch/productionLine/inspector`，可选 `prompt` | 上传文件并触发异步 AI 分析   |
| GET    | `/api/qc/report/page`                  | `pageNum`,`pageSize`,`productName?`,`status?`                                     | 分页查询              |
| GET    | `/api/qc/report/{id}`                  | -                                                                                 | 报告详情              |
| PUT    | `/api/qc/report/{id}/status`           | `status`                                                                          | 更新状态              |
| DELETE | `/api/qc/report/{id}`                  | -                                                                                 | 删除报告              |
| GET    | `/api/qc/report/stats/trend`           | `days=7`                                                                          | 按天缺陷趋势            |
| GET    | `/api/qc/report/stats/production-line` | -                                                                                 | 按生产线统计            |
| GET    | `/api/qc/report/stats/product-batch`   | `limit=10`                                                                        | 按批次统计             |
| GET    | `/api/qc/report/{id}/root-cause`       | -                                                                                 | AI 根因分析（需缺陷+生产参数） |

### 缺陷 `/api/qc/defect`

| 方法     | 路径                                 | 参数              | 说明                       |
|--------|------------------------------------|-----------------|--------------------------|
| POST   | `/api/qc/defect/create`            | body `QcDefect` | 新增缺陷                     |
| PUT    | `/api/qc/defect/update`            | body `QcDefect` | 更新缺陷                     |
| DELETE | `/api/qc/defect/{id}`              | -               | 删除缺陷                     |
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
├── common/
│   ├── Result.java               # 统一响应封装 {code,msg,data}
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
│   ├── ChatContextBuilder.java       # 多轮对话上下文组装（历史裁剪 + 字符预算）
│   ├── ChatProperties.java           # app.chat.* 配置映射
│   ├── RagProperties.java            # app.rag.* 配置映射（分块参数）
│   ├── PromptLoader.java             # 提示词模板加载
│   ├── MilvusConfig.java             # MilvusClient Bean
│   ├── MilvusProperties.java         # milvus.* 配置映射
│   ├── MyBatisConfig.java            # MyBatis-Plus
│   ├── RedisConfig.java              # RedisTemplate + 缓存管理
│   └── TesseractConfig.java          # tesseract.* 配置映射（OCR）
├── controller/
│   ├── ChatController.java       # /ai/chat、/ai/chatRecord
│   ├── MongoChatController.java  # /api/chat/*
│   ├── RagController.java        # /rag/*
│   ├── SysUserController.java    # /blocker/user/*
│   ├── VectorController.java     # /api/vector/*
│   └── qc/
│       ├── QcDefectController.java
│       ├── QcProductionParamController.java
│       └── QcReportController.java
├── document/
│   └── ChatMessageDocument.java  # MongoDB chat_messages 文档
├── dto/                          # ChatRequest / ChatMessageResponse / CreateChat
├── entity/                       # SysUser / Chat / ChatRecord
│   └── qc/                       # QcReport / QcDefect / QcProductionParam
├── mapper/
│   ├── SysUserMapper.java / ChatMapper.java / ChatRecordMapper.java
│   └── qc/                       # QC 三个 Mapper（注解 SQL + 统计）
├── repository/
│   └── ChatMessageRepository.java # Spring Data MongoDB Repository
├── service/
│   ├── ChatService / ChatRecordService
│   ├── FileParseService          # PDFBox / OCR 文件解析
│   ├── MongoChatMessageService
│   ├── RagService
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
  kafka:                 # bootstrap-servers: localhost:9092

mybatis-plus:            # mapper-locations、驼峰映射、日志

ollama:                  # 本地大模型
  base-Url: http://localhost:11434
  default-model: deepseek-r1:latest    # 对话模型
  embedding-model: all-minilm:latest   # embedding 模型

milvus:                  # 向量库
  host: 127.0.0.1
  port: 19530
  dimension: 2048                      # 需与 embedding 模型维度一致
  collection-name: rag_documents

tesseract:               # 图片 OCR（需本机安装 Tesseract + 中文语言包 chi_sim）
  path: "I:\\OCR\\tesseract-main"
  data-path: "I:\\OCR\\tesseract-main\\tessdata"
  language: "chi_sim"
```

---

## 监控与可观测性

基于 **Spring Boot Actuator + Micrometer**，指标以 Prometheus 格式导出。

### Actuator 端点

| 端点 | 访问控制 | 说明 |
|---|---|---|
| `/actuator/health` | ✅ 免认证 | 健康检查（含 db / mongo / redis / diskSpace 组件明细） |
| `/actuator/prometheus` | ✅ 免认证 | Prometheus 指标抓取（裸文本格式） |
| `/actuator/info` | 🔒 需认证 | 应用信息 |
| `/actuator/metrics` | 🔒 需认证 | 指标列表与单项查询 |
| `/actuator/flyway` | 🔒 需认证 | 数据库迁移状态 |
| `/actuator/loggers` | 🔒 需认证 | 运行时动态调整日志级别 |

> ⚠️ **只放行 `health` 与 `prometheus`**，其余端点保持需要认证。
> `/actuator/heapdump`（堆内存快照）、`/actuator/env`（含密钥的配置）一旦公开会泄露敏感信息。

### 暴露的关键指标

| 指标 | 含义 |
|---|---|
| `jvm_memory_used_bytes` | JVM 各内存区使用量 |
| `jvm_gc_pause_seconds` | GC 停顿时间 |
| `http_server_requests_seconds` | HTTP 接口耗时直方图（可算 P95 / P99） |
| `hikaricp_connections_active` | 数据库连接池活跃连接数 |
| `system_cpu_usage` | 系统 CPU 使用率 |

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

| 环境 | 值 | 原因 |
|---|---|---|
| `dev` | `always` | 方便本地调试，直接看到各组件状态 |
| `prod` | `when-authorized` | 不对外暴露数据库地址、磁盘路径等细节 |

### 接入 Prometheus + Grafana

`deploy/docker-compose.yml` 已包含 `prometheus` 与 `grafana` 两个服务：

- Prometheus 通过 **Docker 内网** `backend:8080` 抓取指标 —— **不经过 Nginx，也不暴露公网** ✅
- 抓取配置见 `deploy/prometheus.yml`（`scrape_interval: 15s`）
- Grafana 暴露在 `3000` 端口用于查看仪表盘

> 若将来把 Prometheus 部署到**外部机器**，则需要在 Nginx 层限制来源 IP
> （`allow <Prometheus IP>; deny all;`），因为此时流量会经过 Nginx。

---

## 质检分析 Agent

基于 **LangChain4j `@Tool`** 实现 L1 Agent：把质检数据查询封装为**工具**，
由**模型自主决定**调哪些工具、以什么顺序调查。

### 与 L0（固定管道）的区别

| | L0：`GET /api/qc/report/{id}/root-cause` | L1：`POST /ai/qc-agent` |
|---|---|---|
| 控制流 | **开发者**（顺序写死在代码里） | **模型**（自主规划） |
| 灵活性 | 只能按预设路径 | 可按情况调整调查策略 |
| 可观测性 | 无中间过程 | **SSE 实时推送工具调用轨迹** |

### 组件

| 组件 | 职责 |
|---|---|
| `agent/QcAgentTools.java` | 工具集（**全部只读**，**不含任何埋点代码**） |
| `agent/QcAnalysisAgent.java` | Agent 接口 + SystemMessage（含"禁止臆测"约束） |
| `agent/AgentFactory.java` | Agent 工厂 —— 每次请求创建一个实例 |
| `agent/AgentTraceListener.java` | 工具执行**完成**钩子 → 推送 SSE |
| `agent/AgentTraceContext.java` | SSE 事件发送工具（无状态） |
| `config/qc/QcAgentConfig.java` | 按 profile 装配，注册两个轨迹钩子 |
| `controller/qc/QcAgentController.java` | HTTP 入口 |

### 可用工具（全部只读）

| 工具 | 底层方法 | 用途 |
|---|---|---|
| `getReportByNo(reportNo)` | `QcReportService.getByReportNo` | 按业务编号（`R2026…`）换数字 ID |
| `getReport(reportId)` | `QcReportService.getById` | 报告背景 |
| `getDefects(reportId)` | `QcDefectService.getByReportId` | 缺陷构成 |
| `searchKnowledge(question)` | `RagService.retrieve` | 检索质量标准（只给素材，不让它二次生成） |

> ⚠️ **安全边界**：`deleteReport` / `updateStatus` / `confirmDefect` **一律不暴露** ——
> 即使被提示词注入，模型也只能读不能改。

### 模型选择（Agent 需要 Function Calling）

| profile | Agent 用的模型 |
|---|---|
| `ollama` | `ollama.agent-model`（默认 `llama3.1`）—— `deepseek-r1` 是推理模型，工具调用能力弱 |
| `cloud` | `cloud-ai.chat.model`（`deepseek-chat`，原生支持 Function Calling） |

### 轨迹埋点（零侵入，不碰工具代码）

用 `AiServices` 的两个**官方钩子**：

```java
AiServices.builder(QcAnalysisAgent.class)
        .chatModel(model)
        .tools(tools)
        .beforeToolExecution(b -> { ... })                   // 工具开始执行
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

**判断标准**：`[Tool]` 出现多条且顺序不固定 = 模型在自主决策 ✅

### 三方对比实验（同一份报告）

**实验条件**：同一报告 `R20260812002`（6 缺陷 / 严重）、同一问题（"分析 R20260812002 的根因"）、各跑 3 次取平均。

| 维度 | ① RAG 问答 | ② L0 固定管道 | ③ **L1 Agent** |
|---|---|---|---|
| 数据源 | 知识库 | 数据库 | **数据库 + 知识库** |
| **⏱ 耗时（平均）** | **3,711 ms** | **6,038 ms** | **10,706 ms** |
| 模型调用轮数 | 1 | 1 | **3** |
| 工具调用次数 | 0 | 0 | **7** |
| 总 token | — | — | **9,141** |
| 输出长度 | ~615 字符 | ~6,400 字符 | ~4,750 字符 |
| 输出格式 | 纯文本 | ✅ **JSON** | 纯文本 |
| 实测参数引用 | ❌ | ✅ | ✅ |
| 知识库标准引用 | ✅ | ❌ | ✅ |
| **多参数交叉分析** | ❌ | ❌ | ✅ ⭐ |
| 处理建议 | ⚠️ 未给出 | ✅ | ✅ |
| 信息缺口说明 | ❌ | ❌ | ✅ |

**四个关键发现**：

1. **耗时**：`RAG (3.7s) < L0 (6.0s) < L1 (10.7s)` —— L1 是 RAG 的 **2.9 倍**
   （L1 调 3 轮模型，每轮都要重发历史消息 → token 成本**非线性增长**）
2. **L0 输出最长（6,400 字符）但归因单一** —— 6 条缺陷**全部**归因到 `temperature`，
   且不查知识库 → 没有标准依据
3. **RAG 输出最短（615 字符）** —— 只复述了案例原文，**没有判断和建议**
4. **L1 唯一能做交叉分析** ⭐ —— 自主对比四项工艺参数，发现
   "温度 / 压力 / 湿度 / 速度**全部超出正常区间**"
   → 得出"**系统性工艺失控**"而非"单点故障"的结论（L0 和 RAG 都给不出）

**结论：三者适用场景不同，不是替代关系**

| 场景 | 推荐 | 理由 |
|---|---|---|
| 批量、标准化分析 | **L0** | JSON 结构固定、可编程消费、成本低 |
| 知识检索问答 | **RAG** | 最快（3.7s），直接命中知识库 |
| 复杂根因调查 | **L1** | 数据 + 知识 + 交叉分析，结论质量不可替代 |
| 交互式探索 | **L1** | 自然语言输入、可追问、有工具轨迹展示 |

---

## 已知事项 / Roadmap

### 待完善

- **文件解析**：`FileParseServiceImpl` 已实现 PDF（PDFBox）与图片 OCR（Tess4j）；
  Word / Excel 解析方法为预留（返回空串），上传 Word/Excel 时 AI 无法提取内容。
- **上传文件清理**：质检报告文件保存在运行目录 `uploads/qc/`（相对路径），
  部署时需注意目录挂载与磁盘清理策略。
- **重排序（Rerank）**：混合检索（向量 + BM25 + RRF）已落地，
  但尚未接入 Rerank 重排序 —— 知识库规模继续增长时，这是下一步优化方向
  （详见 `RAG检索优化实验记录.md`）。

### 设计说明（非缺陷）

- **认证与鉴权**：`SecurityConfig` 采用 JWT **无状态认证**（`SessionCreationPolicy.STATELESS`），
  由 `JwtAuthenticationFilter` 解析 token 并写入 `SecurityContext`。
  仅放行登录、注册、Swagger，以及 Actuator 的 `health` / `prometheus`，其余接口均需认证。
  未认证返回 **401**、无权限返回 **403**（由 `authenticationEntryPoint` / `accessDeniedHandler` 分别处理）。
- **密钥外部化**：`JwtUtil` 通过 `@Value("${JWT_SECRET_KEY}")` 从环境变量注入，
  并在 `@PostConstruct` 中做启动校验（缺失或不足 32 字节直接启动失败）。
- **数据库**：所有表统一建在 `mate10db`，**不存在跨库查询**。
- **Redis 缓存**：两级缓存（`qcStats` 5min / `qcDetail` 30min），
  读用 `@Cacheable`、写用 `@EvictQcCache` 组合注解（粗粒度失效，避免漏清）。
  缓存序列化用 `GenericJacksonJsonRedisSerializer.builder().enableDefaultTyping(...)`
  —— 必须写 `@class`，否则反序列化会退化成 `LinkedHashMap`。
- **混合检索**：向量（语义）+ Lucene BM25（精确标识符）→ RRF 融合。
  关键词索引持久化到 `./data/lucene-index`（与 Milvus 各自持久化），
  可通过 `app.rag.hybrid-enabled` 关闭退回纯向量。
- **Profile 优先级**（从高到低）：
  `命令行参数` > `JVM 系统属性 (-D...)` > `操作系统环境变量` > `application.yml`

