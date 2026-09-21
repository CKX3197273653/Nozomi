# Mate10 · 智能质检分析平台

![CI](https://github.com/CKX3197273653/Nozomi/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3-42b883)
![Docker](https://img.shields.io/badge/Docker-容器化-2496ED)

> 面向制造业质检场景的全栈平台：**质检数据管理 + AI 对话助手 + RAG 知识库问答**。
> 支持本地模型与云端模型一键切换，已完整容器化并在华为云验证部署。

---

## 功能截图

<img width="2560" height="1440" alt="3830503fe61f495d8c946f130b5e439c" src="https://github.com/user-attachments/assets/de85ff6a-5f65-4bbf-ab37-3e788ad69d26" />
<img width="2560" height="1440" alt="ddc822d29b52960fc49b5f18688e7779" src="https://github.com/user-attachments/assets/0b265388-5c64-44b5-a4bc-dc94087f350f" />
<img width="2560" height="1440" alt="b14ea0291f802e318c13cb00f529d6e4" src="https://github.com/user-attachments/assets/a750190e-c9c5-41a6-9f12-a089e9d646f3" />
<img width="2560" height="1440" alt="8c50c4cd7245ef5276c03ca9537ccc59" src="https://github.com/user-attachments/assets/e9257677-a898-4e06-9672-ab4d7f959a52" />
<img width="2560" height="1440" alt="23f01ae78866bfeb124e8543e6f2b897" src="https://github.com/user-attachments/assets/56b820ae-2071-4b1f-a50a-14d52e162bef" />
<img width="2560" height="1440" alt="184db1f9d0daeb73e2b3fbd66e3623a1" src="https://github.com/user-attachments/assets/594dc200-86a8-4fa7-9c60-7c6840ef4c29" />
<img width="2560" height="1440" alt="4fa1898ab93580eeb7de74dc3415818b" src="https://github.com/user-attachments/assets/cd3c6f24-5893-437b-a432-236cd899958a" />

---

## 核心功能

| 模块 | 说明 |
|---|---|
| **质检报告管理** | 报告录入、查询、统计 |
| **缺陷管理** | 缺陷类型 / 等级 / 处置记录 |
| **生产参数管理** | 工艺参数维护与追溯 |
| **AI 对话助手** | 多轮对话记忆（MongoDB 持久化）+ SSE 流式逐字输出 |
| **RAG 知识库问答** | 文档分块 → 向量检索 → 增强回答，附评测体系 |
| **3D 模型查看** | GLB 模型加载（Three.js） |

---

## 🏗 系统架构

```
┌──────────────────────────────────────────────────────┐
│  前端  Vue 3 + Vite + Element Plus + ECharts         │
│        生产：Nginx 托管静态文件 + 反向代理            │
└──────────────────────────────────────────────────────┘
                        ↓  /blocker  /api  /rag  /ai
┌──────────────────────────────────────────────────────┐
│  后端  Spring Boot 4.0 + Spring Security(JWT)        │
│        MyBatis-Plus · LangChain4j                    │
└──────────────────────────────────────────────────────┘
      ↓            ↓           ↓          ↓
   MySQL       MongoDB      Redis      Milvus
   业务数据     聊天记录      缓存      向量检索
                                    （etcd + minio）
                        +
                     Kafka（异步消息）
```

**双 AI 模式**（通过 Spring Profile 一键切换，无需改代码）：

| | `ollama`（本地） | `cloud`（云端） |
|---|---|---|
| 对话模型 | deepseek-r1 | DeepSeek API |
| Embedding | all-minilm（384维） | 智谱 embedding-3（2048维） |
| 向量集合 | `rag_documents` | `rag_documents_2048` |

---

## 技术栈

**后端**：Spring Boot 4.0 · Java 17 · Spring Security + JWT · MyBatis-Plus · LangChain4j · Maven
**前端**：Vue 3 · Vite 8 · Element Plus · Pinia · Vue Router · Axios · ECharts · Three.js
**存储**：MySQL 8.0 · MongoDB · Redis · Milvus 2.6
**中间件**：Kafka 4.1（KRaft）
**AI**：DeepSeek API · 智谱 embedding-3 · Ollama（本地）
**数据迁移**：Flyway
**监控**：Spring Boot Actuator · Micrometer · Prometheus · Grafana
**运维**：Docker 多阶段构建 · docker-compose · Nginx · GitHub Actions

---

## 工程实践亮点

| 亮点 | 说明 |
|---|---|
| **RAG 检索优化** | 通过分块策略调优，检索命中率 **86.7% → 96.7%**（附完整实验记录） |
| **流式输出** | SSE + 独立线程池 + `try/finally` 保证 `done` 事件；前端用 `fetch` + `ReadableStream` 绕过 EventSource 的鉴权限制 |
| **安全加固** | 方法级权限（`@PreAuthorize`）替代前端传参判断；JWT 密钥强度启动校验；密码字段 `WRITE_ONLY` 防泄露 |
| **配置外部化** | 双维度 Profile（环境 `dev/prod` × AI `ollama/cloud`，共 4 种组合）；生产配置**刻意无默认值**，缺失即 fail-fast |
| **测试与 CI** | 13 个单元测试（覆盖上下文组装、JWT 安全不变量、登录分支）；GitHub Actions 自动验证 |
| **数据库迁移** | Flyway 版本化管理表结构；已有库自动 baseline，避免重复建表与手工 SQL 漂移 |
| **可观测性** | Actuator + Micrometer 导出 Prometheus 指标；只放行 `health`/`prometheus`，其余端点保持认证 |
| **容器化** | 多阶段构建 + 层缓存优化（先 COPY pom.xml）；前后端镜像均 < 300MB |

---

## 快速开始

### 前置依赖

```
JDK 17 · Node.js 20.19+ · MySQL 8 · MongoDB · Redis · Kafka · Milvus(Docker) · Ollama(可选)
```

### 方式一：本地开发

```bash
# 后端
cd mate10
mvn spring-boot:run          # IDEA 中设置 Active profiles: ollama 或 cloud

# 前端
cd mate10-V
npm install && npm run dev   # http://localhost:5173
```

### 方式二：容器化部署

见 **[deploy/README.md](deploy/README.md)** —— 包含完整的 **11 服务**编排方案
（含 Prometheus + Grafana 监控）、环境变量清单与踩坑记录。

```bash
cd deploy
cp .env.example .env         # 填入真实密钥
docker compose up -d
```

---

## 踩坑记录

<details>
<summary><b>Kafka 在容器内连不上 broker</b></summary>

`advertised.listeners` 默认是 `localhost:9092`，容器里的 `localhost` 指向容器自己。
**解法**：用 Docker 服务名作为 advertised 地址（`kafka:9092`），或在宿主机上配置独立 listener。
</details>

<details>
<summary><b>Nginx 反代后 SSE 流式输出失效</b></summary>

Nginx 默认缓冲响应，流式 token 会被攒到最后一次性返回。
**解法**：`/ai` 路径加 `proxy_buffering off` + `proxy_http_version 1.1` + 关闭 `Connection` 头。
</details>

<details>
<summary><b>MongoDB 配置写了却从未生效</b></summary>

一个 `@Configuration` 类继承了 `AbstractMongoClientConfiguration`，只重写了 `getDatabaseName()`，
没有重写 `mongoClient()` → 覆盖了 Spring Boot 自动配置，MongoClient 默认连 `localhost:27017`。
本地恰好能用（Mongo 就在 localhost），**容器内必然失败**。
**教训**：「配置写了」≠「配置生效了」，要验证运行时行为。
</details>

<details>
<summary><b>Spring Boot 4.0：加了依赖却「什么都不发生」</b></summary>

Spring Boot 4.0 把自动配置**拆成了独立模块**。只加第三方库（如 `flyway-core`）
不会引入 Spring Boot 的集成层（`spring-boot-flyway`），于是**自动配置完全不生效** ——
没有日志、没有报错、表也没建，但 IDE 会提示「无法解析 `spring.flyway.*` 属性」。
**解法**：用 `spring-boot-starter-flyway` 替代裸 `flyway-core`（`flyway-mysql` 仍需单独加）。
**教训**：SB 4.0 是「库 + 集成层」两件套；**IDE 的属性解析警告通常是真的**，别急着当成缓存问题。
</details>

<details>
<summary><b>Docker 镜像推送 SWR 报 manifest 解析失败</b></summary>

Docker 23+ 的 BuildKit 默认给镜像附加 provenance/SBOM 证明，镜像变成多清单索引，部分 registry 无法解析。
**解法**：`docker push --platform linux/amd64 <image>` 只推单平台清单。
</details>

<details>
<summary><b>Windows 端口被系统占用（WSAEACCES）</b></summary>

Hyper-V/WinNAT 开机时动态圈占端口范围，导致 Docker 无法绑定（报 `socket ... forbidden`）。
**解法**：`net stop winnat` → `netsh int ipv4 add excludedportrange ...` 永久排除 → `net start winnat`。
</details>

---

## 项目结构

```
Mate10/
├── mate10/                    # 后端（Spring Boot）
│   ├── src/main/java/         # 分层：controller / service / mapper / entity / config
│   ├── src/main/resources/
│   │   ├── application*.yml   # 多环境配置（dev/prod × ollama/cloud）
│   │   └── db/migration/      # Flyway 迁移脚本（V1__init_schema.sql …）
│   ├── src/test/java/         # 单元测试
│   └── Dockerfile             # 多阶段构建
├── mate10-V/                  # 前端（Vue 3 + Vite）
│   ├── src/
│   ├── nginx.conf             # 生产环境反代配置
│   └── Dockerfile             # 多阶段：node 构建 → nginx 托管
├── deploy/                    # 容器编排方案
│   ├── docker-compose.yml     # 11 服务编排
│   ├── prometheus.yml         # Prometheus 抓取配置
│   ├── .env.example
│   └── README.md              # 部署手册 + 踩坑记录
├── docs/experiments/          # 实验记录（RAG 分块策略调优）
├── AGENTS.md                  # 项目约定与踩坑手册
└── .github/workflows/ci.yml   # CI：后端测试 + 前端构建
```
