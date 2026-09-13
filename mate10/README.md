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
| 消息队列 | Apache Kafka | 3.0+ |
| AI | LangChain4j | 1.14.1 |
| AI | Ollama（对话 + embedding） | 本地模型 |
| 向量库 | Milvus（SDK 2.6.18） | 2.x |
| OCR | Tesseract（Tess4j 5.7.0） | 需本地安装 |
| 文档解析 | PDFBox / Apache POI | - |
| 认证 | Spring Security + JWT (jjwt) | - |
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

### 3. 初始化 MySQL

```sql
CREATE DATABASE IF NOT EXISTS mate10db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

QC 模块参考建表语句（列名与 Mapper 注解 SQL 对应）：

```sql
-- 质检报告
CREATE TABLE qc_report (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_no      VARCHAR(64)   NOT NULL COMMENT '报告编号',
    product_name   VARCHAR(128)  NOT NULL COMMENT '产品名称',
    product_batch  VARCHAR(64)   COMMENT '产品批次',
    production_line VARCHAR(64)  COMMENT '生产线',
    inspector      VARCHAR(64)   COMMENT '检验员',
    inspect_time   DATETIME      COMMENT '检验时间',
    file_url       VARCHAR(255)  COMMENT '源文件路径',
    file_type      VARCHAR(16)   COMMENT 'PDF/IMAGE/WORD/EXCEL',
    status         VARCHAR(16)   DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/COMPLETED/FAILED',
    total_defects  INT           DEFAULT 0 COMMENT '缺陷总数',
    severity_level VARCHAR(16)   COMMENT 'NORMAL/WARNING/CRITICAL',
    create_time    DATETIME,
    update_time    DATETIME
) COMMENT='质检报告';

-- 缺陷记录
CREATE TABLE qc_defect (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id      BIGINT    NOT NULL COMMENT '所属报告ID',
    defect_type    VARCHAR(64)  COMMENT '缺陷类型',
    defect_position VARCHAR(64) COMMENT '缺陷位置',
    severity       VARCHAR(16)  COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
    description    VARCHAR(512) COMMENT '缺陷描述',
    confidence     DECIMAL(5,2) COMMENT '置信度0-100',
    is_confirmed   TINYINT DEFAULT 0 COMMENT '是否人工确认',
    create_time    DATETIME,
    INDEX idx_report (report_id)
) COMMENT='质检缺陷';

-- 生产参数
CREATE TABLE qc_production_param (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id   BIGINT   NOT NULL COMMENT '所属报告ID',
    temperature DECIMAL(8,2) COMMENT '温度°C',
    pressure    DECIMAL(8,2) COMMENT '压力MPa',
    humidity    DECIMAL(8,2) COMMENT '湿度%',
    speed       DECIMAL(8,2) COMMENT '速度rpm',
    operator    VARCHAR(64)  COMMENT '操作员',
    remark      VARCHAR(255) COMMENT '备注',
    create_time DATETIME,
    INDEX idx_report (report_id)
) COMMENT='生产参数';
```

> 用户表与聊天模块沿用既有 `sys_user` / `chat` / `chat_record` 表（BCrypt 密码、status 0=禁用 1=启用）。
> 注意：聊天模块 `ChatMapper` 中存在跨库查询 `mate10sql.chat`，若默认库不叫 `mate10sql` 需同步修改。

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

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/blocker/user/login` | `username`,`password` | 登录，返回 `token` + `user` |
| POST | `/blocker/user/add` | body `SysUser`，`role=1` | 新增用户（管理员） |
| PUT | `/blocker/user/update` | body `SysUser`，`role=1` | 更新用户 |
| GET | `/blocker/user/check-username` | `username` | 用户名查重 |
| PUT | `/blocker/user/update-username` | `userId`,`username` | 改用户名 |
| PUT | `/blocker/user/updatePassword` | `userId`,`oldPassword`,`newPassword` | 修改密码 |
| PUT | `/blocker/user/disable/{userId}` | `role=1` | 禁用用户 |
| DELETE | `/blocker/user/delete/{userId}` | `role=1` | 删除用户 |
| GET | `/blocker/user/get/{userId}` | - | 查询用户 |

### AI 聊天

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/ai/chat` | body `Chat{userId,title}` | 创建会话 |
| POST | `/ai/chatRecord` | body `ChatRequest{message,userId,chatId}` | 发送消息并返回 AI 回答（存 MongoDB + Kafka 落库） |
| GET | `/api/chat/messages/{chatId}` | - | 按会话查消息 |
| GET | `/api/chat/user/{userId}/messages` | - | 按用户查消息 |
| GET | `/api/chat/messages/{chatId}/{role}` | `role=User/ai` | 按会话+角色查消息 |

### RAG 知识库 `/rag`

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/rag/add-text` | body 纯文本 | 将文本加入知识库 |
| POST | `/rag/upload` | `file`（multipart，按文本解析） | 上传文件入知识库 |
| POST | `/rag/ask` | body 问题文本 | 通用 RAG 问答 |
| POST | `/rag/qc-ask` | body 问题文本 | 质检助手预设上下文问答 |
| POST | `/rag/init-qc-knowledge` | - | 写入内置质检标准知识库 |
| DELETE | `/rag/clear` | - | 清空知识库 |

### 质检报告 `/api/qc/report`

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/qc/report/create` | body `QcReport` | 手工创建报告 |
| POST | `/api/qc/report/upload` | `file` + `reportNo/productName/productBatch/productionLine/inspector`，可选 `prompt` | 上传文件并触发异步 AI 分析 |
| GET | `/api/qc/report/page` | `pageNum`,`pageSize`,`productName?`,`status?` | 分页查询 |
| GET | `/api/qc/report/{id}` | - | 报告详情 |
| PUT | `/api/qc/report/{id}/status` | `status` | 更新状态 |
| DELETE | `/api/qc/report/{id}` | - | 删除报告 |
| GET | `/api/qc/report/stats/trend` | `days=7` | 按天缺陷趋势 |
| GET | `/api/qc/report/stats/production-line` | - | 按生产线统计 |
| GET | `/api/qc/report/stats/product-batch` | `limit=10` | 按批次统计 |
| GET | `/api/qc/report/{id}/root-cause` | - | AI 根因分析（需缺陷+生产参数） |

### 缺陷 `/api/qc/defect`

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/qc/defect/create` | body `QcDefect` | 新增缺陷 |
| PUT | `/api/qc/defect/update` | body `QcDefect` | 更新缺陷 |
| DELETE | `/api/qc/defect/{id}` | - | 删除缺陷 |
| GET | `/api/qc/defect/report/{reportId}` | - | 按报告查缺陷 |
| GET | `/api/qc/defect/stats/type` | - | 缺陷类型统计 |
| GET | `/api/qc/defect/stats/severity` | - | 严重程度统计 |
| PUT | `/api/qc/defect/{id}/confirm` | - | 人工确认缺陷 |
| GET | `/api/qc/defect/3d/{reportId}` | - | ECharts/Three.js 3D 缺陷数据 |

### 生产参数 `/api/qc/param`

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/qc/param/create` | body `QcProductionParam` | 新增生产参数 |
| PUT | `/api/qc/param/update` | body `QcProductionParam` | 更新生产参数 |
| GET | `/api/qc/param/report/{reportId}` | - | 按报告查生产参数 |

### Milvus 通用向量 `/api/vector`

| 方法 | 路径 | 参数 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/vector/collection` | `collName`,`dim` | 创建集合 + 索引 + 加载 |
| POST | `/api/vector` | `collName`，body 向量数组 | 插入向量 |
| POST | `/api/vector/search` | `collName`,`vector`,`topK=5` | 相似度检索（返回 ID） |
| POST | `/api/vector/query` | `collName`，body ID 数组 | 按 ID 查询向量 |
| PUT | `/api/vector/update` | `collName`,`id`，body 向量 | 更新（先删后插） |
| DELETE | `/api/vector/delete` | `collName`，body ID 数组 | 删除向量 |
| DELETE | `/api/vector/collection` | `collName` | 删除整个集合 |
| GET | `/api/vector/collection/exists` | `collName` | 判断集合是否存在 |

---

## Kafka 主题

| 主题 | 生产者 | 消费者 | 用途 |
| --- | --- | --- | --- |
| `chat-topic` | `/ai/chatRecord` | `ChatRecordServiceImpl` | 聊天记录异步落库 + AI 关键词 |
| `qc-report-topic` | `/api/qc/report/upload` | `QcReportServiceImpl` | 质检报告异步 AI 分析 |

---

## 项目结构

```
src/main/java/org/mate/mate10/
├── Mate10Application.java        # 启动类
├── common/
│   └── Result.java               # 统一响应封装 {code,msg,data}
├── config/
│   ├── CorsConfig.java           # 跨域（Vite 5173 白名单）
│   ├── KafkaConfig.java          # Kafka 生产者/消费者/监听容器
│   ├── LangChain4jConfig.java    # Ollama 对话/Embedding + MilvusEmbeddingStore
│   ├── MilvusConfig.java         # MilvusClient Bean
│   ├── MilvusProperties.java     # milvus.* 配置映射
│   ├── MongoConfig.java          # MongoDB 连接与 Repository 扫描
│   ├── MyBatisConfig.java        # MyBatis-Plus
│   ├── OllamaProperties.java     # ollama.* 配置映射
│   ├── RedisConfig.java          # RedisTemplate + 缓存管理
│   ├── SecurityConfig.java       # Security（当前全放行）+ BCrypt
│   └── TesseractConfig.java      # tesseract.* 配置映射（OCR）
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

## 已知事项 / Roadmap

- **文件解析**：目前 `FileParseServiceImpl` 已实现 PDF（PDFBox）与图片 OCR（Tess4j）；Word / Excel 解析方法为预留（返回空串），上传 Word/Excel 时 AI 将无法提取内容。
- **安全管理**：`SecurityConfig` 当前对接口匿名放行（`permitAll`），JWT 由应用层自行校验/签发，未配置全局 Filter 拦截，正式环境需收紧。
- **数据库名不一致**：`application.yml` 默认库为 `mate10db`，但 `ChatMapper.selectByUserId` 硬编码了 `mate10sql.chat` 跨库查询，切换库名时需同步调整。
- **JWT 密钥**：`JwtUtil` 中的 SECRET_KEY 为内置常量，生产环境应改为外部配置注入。
- 质检报告上传文件保存在运行目录 `uploads/qc/`（相对路径），部署时注意目录与磁盘清理策略。
