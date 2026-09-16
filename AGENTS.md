# AGENTS.md — Mate10 项目

## 项目概览

单一 git 仓库管理两个独立项目，**分别启动、分别构建**（不是 monorepo 共享构建）：

- **`mate10/`** — Spring Boot 4.0 后端（Maven / Java 17）
- **`mate10-V/`** — Vue 3 + Vite 前端（Node.js）
- **`milvus/`** — 空目录（历史遗留），Milvus 实际用 Docker 部署

> 根目录旧 `.zip` 归档已废弃，不要依赖（且未被 git 跟踪，可自行清理）。

---

## 后端 `mate10/`

### 环境要求

- JDK 17（本机 `JAVA_HOME=I:\JAVAgen`）
- Maven 3.8+（本机无全局 mvn，使用 IDEA 内置 Maven：
  `I:\IDEA\IntelliJ IDEA 2023.3.3\plugins\maven\lib\maven3\bin\mvn.cmd`）

### 启动与构建

```bash
# 开发模式（热部署）
mvn spring-boot:run

# 打包跳过测试
mvn clean package -DskipTests
java -jar target/mate10-0.0.1-SNAPSHOT.jar
```

### AI 对话接口

| 端点 | 说明 | 响应格式 |
|---|---|---|
| `POST /ai/chat` | 创建会话，返回 chatId | `Result<Chat>` |
| `POST /ai/chatRecord` | 非流式对话（带记忆） | `Result<String>` |
| `POST /ai/chat/stream` | 流式对话（SSE） | `text/event-stream` |

**记忆机制**：`ChatContextBuilder` 组装上下文 = SystemMessage + 最近 10 条历史（2000 字符预算）+ 当前问题。
历史存 MongoDB `chat_messages`，按 `chatId` 隔离。`chatId` 为 null 时降级为单轮对话。

**RAG 融合**：请求体带 `mode=qc` 时，自动检索知识库并把资料作为 SystemMessage 注入；
`mode=general` 为纯对话。

**流式约定（重要，改流式前必读）**：

- 后端在 `onCompleteResponse` 中发送 `event:done` 结束事件（用 `try/finally` 保证一定发送）
- 前端**不能依赖"连接关闭"判断流结束**——经 Vite 代理时关闭信号会丢失，界面会永久卡在"思考中"
- 前端用 `fetch` + `ReadableStream`（`EventSource` 无法携带 `Authorization` 头）
- 解析需按 `\n\n` 切分并维护缓冲区（chunk 是任意切分的，存在"粘包"）
- 后端模型调用必须放在独立线程（`ThreadPoolTaskExecutor`）执行，否则方法不返回、事件被缓冲、流式失效

### 中间件依赖（全部需要本地启动）

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL 8.0+ | 3306 | 业务/QC/聊天记录，默认库 `mate10db` |
| MongoDB 4.0+ | 27017 | 聊天消息 |
| Redis 6.0+ | 6379 | 缓存 |
| Kafka 4.1（KRaft 模式） | 9092 | 消息队列（无 Zookeeper） |
| Milvus 2.x（Docker） | 19530 | 向量库（docker-compose：standalone + etcd + minio） |
| Ollama | 11434 | 本地模型服务（仅 ollama 模式需要） |
| Tesseract | - | OCR（图片质检需要） |

启动顺序建议：MySQL → MongoDB → Redis → Kafka → Milvus → Ollama → 后端

### AI 模型切换（ollama / cloud 双 profile）

通过 Spring Profile 一键切换整套 AI 能力，**无需改代码**：

| | `ollama`（本地） | `cloud`（云端） |
|---|---|---|
| 对话模型 | deepseek-r1:latest | DeepSeek API（deepseek-chat） |
| Embedding | all-minilm（384 维） | 智谱 embedding-3（2048 维） |
| Milvus 集合 | `rag_documents`（384 维） | `rag_documents_2048`（2048 维） |

**切换方式**：IDEA 运行配置 → Active profiles 填 `ollama` 或 `cloud` → 重启后端。

**环境变量**（IDEA 运行配置 → Environment variables）：

| 变量 | 需要场景 |
|------|---------|
| `DEEPSEEK_API_KEY` | cloud 模式对话 |
| `ZHIPU_API_KEY` | cloud 模式 Embedding |
| `TESSERACT_PATH` | OCR（默认 `I:\OCR\tesseract-main`） |
| `TESSERACT_DATA_PATH` | OCR 数据（默认 `I:\OCR\tesseract-main\tessdata`） |
| `JWT_SECRET_KEY` | JWT 签名（有内置默认值） |

> 两个模式的知识库互相独立，切换模式后需重新"初始化质检知识库"。
> 两种 profile 各提供 `ChatModel` 与 `StreamingChatModel`（类型不同，不会冲突）。

### 数据库初始化

新环境一键建库建表：

```bash
mysql -uroot -proot --default-character-set=utf8mb4 < mate10/src/main/resources/sql/init.sql
```

- `mate10db`：sys_user、qc_report、qc_defect、qc_production_param、chat_record、chat

> 数据库结构变更后，务必同步更新 `sql/init.sql`（保持脚本与实际库一致）。
> 所有表统一建在 `mate10db`，**不要再用跨库查询**（历史遗留的 `mate10sql` 已废弃）。

### 关键配置与硬编码

- `application.yml` — 所有中间件连接配置、profile 切换、双集合维度配置
- Tesseract、JWT 密钥均已外部化为环境变量（见上表）
- **JWT 有效期 7 天**：过期后接口返回 401（`SecurityConfig` 已配置 `AuthenticationEntryPoint`）

### 已知问题

- **无测试文件**：`src/test/java` 为空，无单元测试
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

> ⚠️ 改 `vite.config.js` 后**必须重启 dev server**，配置不会热更新。

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

---

## 联调须知

1. **先启中间件**，再启后端，最后启前端
2. 后端 `mvn spring-boot:run` 和前端 `npm run dev` 可并行
3. Ollama 需先拉模型：`ollama pull deepseek-r1:latest` 和 `ollama pull all-minilm:latest`
4. Milvus 用 Docker 部署（standalone + etcd + minio 三容器）；
   **Milvus 容器重启后集合回到未加载状态**，若问答报 SearchRequest failed，
   可用后端 API 或 REST 重新 load 集合
5. **Kafka**（本机路径 `D:\Kafka\kafka_2.13-4.1.0`）：
    - KRaft 模式（无 Zookeeper），配置文件 `config\server.properties`
    - 数据目录：`log.dirs=kraft-logs`、`metadata.log.dir=kraft-meta`（**两者都不能删**）
    - **格式化必须加 `--standalone`**：
      `bin\windows\kafka-storage.bat format --standalone -t <集群ID> -c config\server.properties`
    - **数据目录不能被设成只读**：`kraft-meta` 下 `.checkpoint` 文件若为 ReadOnly，
      启动会报 `No meta.properties found`，用 `attrib -R "路径\*.checkpoint*"` 清除
    - **建议把 `D:\Kafka` 加入 Windows Defender 排除目录**，
      否则日志压缩时可能因文件被占用而失败（`AccessDeniedException: 另一个程序正在使用此文件`）
    - `kafka-server-stop.bat` 仍含 wmic 检测，Windows 11 24H2 上停止可能失败，可直接杀进程
6. 前端 `npm run build` 前必须确认后端已运行，否则代理不可用
7. 数据库初始化请使用 `sql/init.sql`（勿手写建表，避免与脚本漂移）
8. **改完代码必须重启后端再测**——否则测的是上一次编译的 class，结论不可信

---

## 新环境恢复手册

新机器从零恢复项目的完整流程：

1. **装环境**：JDK 17、Node.js 20.19+、MySQL、MongoDB、Redis、Kafka、Docker、Ollama、Tesseract
2. **拉代码**：`git clone <仓库地址>`
3. **初始化数据库**：执行 `sql/init.sql`
4. **拉模型**：`ollama pull deepseek-r1:latest`、`ollama pull all-minilm:latest`
5. **启动中间件**：MySQL → MongoDB → Redis → Kafka → Milvus → Ollama
6. **配置后端**（IDEA）：
    - Active profiles：`ollama` 或 `cloud`
    - 环境变量：API 密钥（cloud）、`TESSERACT_PATH`（换机器必改）
7. **启动后端**：`mvn spring-boot:run`
8. **启动前端**：`cd mate10-V && npm install && npm run dev`
9. **首次使用**：QA 界面"初始化质检知识库"（Milvus 集合自动创建）

> 换环境必改项：`TESSERACT_PATH`、数据库密码（`application.yml` 中 root/root）、
> Kafka 路径与 wmic 修复、Windows Defender 排除目录。

---

## 版本里程碑

| 标签 | 内容 |
|---|---|
| `v0.1.0` | 多轮对话记忆：上下文注入、会话隔离、RAG 融合 |
| `v0.2.0` | 流式输出：SSE 端点 + 前端逐字渲染 |