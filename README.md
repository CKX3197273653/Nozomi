# Mate10 · 智能质检分析平台



> 面向制造业质检场景的全栈平台：**质检数据管理 + AI 对话助手 + RAG 知识库问答**。
> 支持本地模型与云端模型一键切换，已完整容器化并在华为云验证部署。

> 本项目采用 [MIT License](LICENSE) 开源，允许学习、二次开发与商业化使用（需保留版权说明）。

---

## 功能截图

<img width="2560" height="1440" alt="3830503fe61f495d8c946f130b5e439c" src="https://github.com/user-attachments/assets/de85ff6a-5f65-4bbf-ab37-3e788ad69d26" />
<img width="2560" height="1440" alt="ddc822d29b52960fc49b5f18688e7779" src="https://github.com/user-attachments/assets/0b265388-5c64-44b5-a4bc-dc94087f350f" />
<img width="2560" height="1440" alt="b14ea0291f802e318c13cb00f529d6e4" src="https://github.com/user-attachments/assets/a750190e-c9c5-41a6-9f12-a089e9d646f3" />
<img width="2560" height="1440" alt="8c50c4cd7245ef5276c03ca9537ccc59" src="https://github.com/user-attachments/assets/e9257677-a898-4e06-9672-ab4d7f959a52" />
<img width="2560" height="1440" alt="23f01ae78866bfeb124e8543e6f2b897" src="https://github.com/user-attachments/assets/56b820ae-2071-4b1f-a50a-14d52e162bef" />
<img width="2560" height="1440" alt="184db1f9d0daeb73e2b3fbd66e3623a1" src="https://github.com/user-attachments/assets/594dc200-86a8-4fa7-9c60-7c6840ef4c29" />
<img width="2560" height="1440" alt="4fa1898ab93580eeb7de74dc3415818b" src="https://github.com/user-attachments/assets/cd3c6f24-5893-437b-a432-236cd899958a" />
<img width="1920" height="1080" alt="工具滚动" src="https://github.com/user-attachments/assets/9cd1548f-904a-47dd-908c-99d57a3b79bf" />
---

## 核心功能

| 模块 | 说明 |
|---|---|
| **质检报告管理** | 报告录入、查询、统计 |
| **缺陷管理** | 缺陷类型 / 等级 / 处置记录 |
| **生产参数管理** | 工艺参数维护与追溯 |
| **AI 对话助手** | 多轮对话记忆（MongoDB 持久化）+ SSE 流式逐字输出 |
| **AI 质检 Agent** | 模型**自主调用工具**（LangChain4j `@Tool`），SSE 实时展示调用轨迹 |
| **AI 记忆与审计** | 两层记忆（会话事件流 + 结论快照）；结论版本链留痕，可倒推「判定是怎么来的」 |
| **RAG 知识库问答** | 文档分块 → 混合检索 → Cross-Encoder 精排 → 增强回答，附评测体系 |
| **3D 模型查看** | GLB 模型加载（Three.js） |
| **用户体系** | Spring Security + JWT ；**员工工号名册自助注册**（工号白名单 + CAS 原子占用）|

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
   业务数据   聊天记录+结论   缓存      向量检索
                档案                  （etcd + minio）
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

**后端**：Spring Boot 4.0 · Java 17 · Spring Security + JWT · MyBatis-Plus（SB4 专用 starter）· LangChain4j · Maven

**前端**：Vue 3 · Vite 8 · Element Plus · Pinia · Vue Router · Axios · ECharts · Three.js

**存储**：MySQL 8.0 · MongoDB · Redis · Milvus 2.6

**中间件**：Kafka 4.1（KRaft）

**AI**：DeepSeek API · 智谱 embedding-3 · Ollama（本地）· ONNX Runtime（本地精排）

**数据迁移**：Flyway

**监控**：Spring Boot Actuator · Micrometer · Prometheus · Grafana

**运维**：Docker 多阶段构建 · docker-compose · Nginx · GitHub Actions

---

## 工程实践亮点

| 亮点 | 说明 |
|---|---|
| **RAG 两段式检索** | 分块策略 + Top-K 调优：命中率 **86.7% → 96.7%**；再加 **Cross-Encoder 精排**：**96.7% → 100%**（附完整实验记录） |
| **AI 记忆架构** | 两层：**会话事件流**（可追溯）+ **结论快照**（覆盖式写入，每份报告恒定 1 条有效）；从「对话堆积」改为「维护当前认识」，噪音不再累积 |
| **可审计** | 结论版本链留痕（`superseded` 软删除）+ 记录「谁/何时/讨论轨迹」，可倒推「这个判定是怎么来的」 |
| **流式输出** | SSE + 独立线程池；**4 条终态路径统一走 `finish()` 发 `done`**（正常完成 / 模型报错 / 端点异常 / 超时），用 **CAS 保证只发一次**；错误详情按 `app.error.detail` 脱敏、完整原因只进日志；前端用 `fetch` + `ReadableStream` 绕过 EventSource 的鉴权限制 |
| **Agent 化（L0 → L1）** | 把质检查询封装为 `@Tool`，**控制流从开发者转移到模型**；用 `beforeToolExecution` + `ToolExecutedEventListener` 做**零侵入**轨迹埋点（可扩展） |
| **安全加固** | 方法级权限（`@PreAuthorize`）替代前端传参判断；**会话接口归属校验（防 IDOR）**；SSE 错误信息按开关脱敏；JWT 密钥强度启动校验；密码字段 `WRITE_ONLY` 防泄露 |
| **数据可复现** | Flyway **7 个版本化迁移 + dev-only 种子**；提供「空库从零建库 → 两库结构签名比对」的验证流程，防止 schema 悄然分叉 
| **配置外部化** | 双维度 Profile（环境 `dev/prod` × AI `ollama/cloud`，共 4 种组合）；生产配置**刻意无默认值**，缺失即 fail-fast |
| **测试与 CI** | **28 个单元测试 + 8 个集成测试（Testcontainers，真 MySQL/Redis）**；GitHub Actions 自动验证（集成测试打 `@Tag`，CI 跳过） |
| **数据库迁移** | Flyway 版本化（`V1`~`V7`）；已有库自动 baseline；**迁移脚本一律幂等**（`IF NOT EXISTS` + `information_schema` 守卫）；开发种子只在 `dev` 加载 |
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
**所以**：「配置写了」≠「配置生效了」，要验证运行时行为。
</details>

<details>
<summary><b>Spring Boot 4.0：加了依赖却「什么都不发生」</b></summary>

Spring Boot 4.0 把自动配置**拆成了独立模块**。只加第三方库（如 `flyway-core`）
不会引入 Spring Boot 的集成层（`spring-boot-flyway`），于是**自动配置完全不生效** ——
没有日志、没有报错、表也没建，但 IDE 会提示「无法解析 `spring.flyway.*` 属性」。
**解法**：用 `spring-boot-starter-flyway` 替代裸 `flyway-core`（`flyway-mysql` 仍需单独加）。
**所以**：SB 4.0 是「库 + 集成层」两件套；**IDE 的属性解析警告通常是真的**，别急着当成缓存问题。
</details>

<details>
<summary><b>Spring Data Redis 的 cache.clear() 静默失效（框架 bug）</b></summary>

`@CacheEvict(allEntries=true)` 与手动 `cacheManager.getCache("qcDetail").clear()`
**都静默失败** —— 无异常、无报错，但 Redis 里的 key 没删；而 `evict(key)`（单条 DEL）正常。

排查四个假想敌：① 组合注解没被识别？→ 排除 ② `transactionAware()` 延迟提交？→ 排除
③ 最小化对比 `evict(key)` ✅ vs `clear()` ❌ → 锁定 ④ 切换 KEYS/SCAN 批处理策略？→ 仍失效，排除

**根因**：Spring Data Redis 3.x+ 默认的 `RedisCacheWriter` 是**批量写入**，
`clear()` 的批量操作没同步刷到 Redis；`evict(key)` 走即时路径所以正常。
**解法**：`RedisCacheWriter.create(connectionFactory, b -> b.immediateWrites())`。

**所以**：这是**只有集成测试（真 Redis）才能抓到**的 bug —— 单测 mock 掉缓存，
永远测不出「清空失效」。
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

<details>
<summary><b>SSE 流式接口数据正常，日志却报 Access Denied</b></summary>

SSE 的数据已正常返回（前端也正常显示），但日志紧跟一条
`AuthorizationDeniedException: Access Denied`。

**根因**：`SseEmitter.complete()` 会让 Tomcat 触发 **ASYNC dispatch**，
而 Spring Security 7 默认对**所有 dispatcherType** 做授权检查 ——
此时异步线程里 `SecurityContext` 已清空，被 `anyRequest().authenticated()` 拒绝。

**解法**（1 行）：
```java
.authorizeHttpRequests(auth -> auth
        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
        ...)
```

**所以**：ASYNC 派发**不是新请求**，是同一请求的延续 ——
第 1 次 REQUEST 派发时已用 JWT 检查过，放行它不降低安全性。
</details>

<details>
<summary><b>SSE 只在成功路径发 done —— 前端永久卡「思考中」</b></summary>

`event:done` 原本只在 `onCompleteResponse` 的 `finally` 里发。
但一次流式请求有 **4 种终态**：① 正常答完 ② 模型报错（`onError`）
③ 端点自身异常（外层 `catch`）④ 5 分钟超时（容器只关连接）。

**②③④ 三条路径都不发 done** → 前端永远等不到结束信号 → 界面永久卡住。

**解法**：抽一个 `finish()` 统一收尾，4 条路径都调它；
用 `AtomicBoolean` + **CAS** 保证 done 只发一次（终态可能竞态：超时与返回几乎同时）。

**验证方式**：给 `finish()` 加一行日志（`[SSE] finish 收尾 -> 正常结束 / 异常: …`），
日志里一眼看出收尾走的哪条路径 —— 把「看不见的行为」变成「可观测的现象」。

**所以**：「抽成方法」不只是少写重复代码 —— 它把「无论怎么结束都必须发 done」
变成了一个**无法被遗漏的不变式**。
</details>

<details>
<summary><b>MyBatis-Plus 在 Spring Boot 4 下静默失效（分页插件失灵）</b></summary>

现象：`selectPage` 返回的 `total` **恒为 0**，`pageSize` 也不起作用（全表返回）。

**根因（两层）**：

1. `pom.xml` 显式钉了 `mybatis-plus-extension:3.5.7`，与 starter 的 `3.5.16` **混搭** ——
   Maven「最近者优先」让 3.5.7 胜出，而 `IRepository` 在 3.5.16 的 `extension` 里 → 编译失败
2. 更隐蔽的是：`mybatis-plus-boot-starter` 面向 **SB 2/3**，
   在 SB4 下自动配置**条件不满足、静默跳过**（SB4 把 `spring-boot-autoconfigure` 拆成 70+ 模块）

**连带后果**：项目一直靠**手写** `sqlSessionFactory` 才能启动 →
`application.yml` 里 `mybatis-plus.*` 全部不生效 → 插件必须手动 `setPlugins`。

**解法**：换 `mybatis-plus-spring-boot4-starter`（SB4 专用）；
3.5.9+ 分页拦截器在 `mybatis-plus-jsqlparser`（单独加、版本一致）；
手写 factory 必须 `factory.setPlugins(...)`；实体显式写 `@TableId(type = IdType.AUTO)`。

**所以**：只钉「入口依赖」的版本，**不要钉它内部子模块** ——
混搭会以最难排查的方式爆炸。
</details>

<details>
<summary><b>Flyway 依赖被标成 test scope —— 数据库版本管理静默失效</b></summary>

现象：**Flyway 一次都没执行过**，但应用**启动完全正常、零报错、零日志** ——
表结构全靠手工建，`flyway_schema_history` 里只有一个早年的 BASELINE 行。

**根因**：`pom.xml` 里 `flyway-core` 被标成了 `<scope>test</scope>` ——
运行时 classpath 里没有它 → `FlywayAutoConfiguration` 上的
`@ConditionalOnClass(Flyway.class)` 不满足 → **自动配置被静默跳过**。

**这是「SB 4.0 模块化陷阱」的镜像版**：那条讲「只加库、缺集成层」；
这条是「**有集成层、但库被标成了 test**」—— 同样静默失效。

**排查三步（5 分钟）**：

1. 看**运行进程的 classpath** 里有没有 `flyway-core`
   —— 「依赖在 pom 里」≠「依赖在运行时 classpath 里」
2. 看 `flyway_schema_history`：**没有这张表 / 只有 BASELINE 行** = Flyway 从未工作
3. **换一个空库启动** —— 空库里零张表 = 铁证

**解法**：删掉 `<scope>test</scope>`（`compile` 也包含测试 classpath，集成测试照常可用）。

** 连带纪律**：Flyway 生效后，**迁移文件就冻结了** ——
改了已应用的迁移（哪怕只改注释/空格）→
`FlywayValidateException: Migration checksum mismatch` → **启动失败**；
而这个报错会**伪装成** `Error creating bean with name 'xxxMapper'` ——
**真凶永远在最内层的 `Caused by`**。

**所以**：「依赖加了」≠「依赖生效了」。这已经是本项目第 3 次踩同一类坑
（MongoDB 配置未生效 / MyBatis-Plus starter 不匹配 / Flyway 依赖 scope 错）——
共同特征都是 **零报错的静默失效**，只能靠**观测运行时行为**发现。
</details>

<details>
<summary><b>Redis 缓存读出来变成 LinkedHashMap</b></summary>

用 `new GenericJacksonJsonRedisSerializer(objectMapper)` 构造序列化器时
**不会开启 default typing** → 写入的 JSON **不含 `@class`** →
反序列化退化成 `LinkedHashMap` → 读取时报
`ClassCastException: LinkedHashMap cannot be cast to ...`

**解法**：用 Spring 官方 builder 显式开启类型信息：
```java
GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.mate.mate10.")
                .allowIfSubType("java.util.")
                ...
                .build())
        .build();
```

**验证**：`redis-cli GET "qcDetail::report:1"` 应看到 `"@class":"org.mate..."`。
** 修复后必须清旧缓存**（`redis-cli FLUSHDB`）—— 旧数据没有 `@class`，仍会报错。
</details>

---

## 项目结构

```
Mate10/
├── mate10/                    # 后端（Spring Boot）
│   ├── src/main/java/         # 分层：controller / service / mapper / entity / config
│   ├── src/main/resources/
│   │   ├── application*.yml   # 多环境配置（dev/prod × ollama/cloud）
│   │   └── db/
│   │       ├── migration/     # Flyway 迁移脚本（V1__init_schema.sql … V7__…）
│   │       └── seed-dev/      # 仅 dev 加载的种子数据（R__seed_…）
│   ├── src/test/java/         # 单元测试 + 集成测试（Testcontainers）
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


![CI](https://github.com/CKX3197273653/Nozomi/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)
![Vue](https://img.shields.io/badge/Vue-3-42b883)
![Docker](https://img.shields.io/badge/Docker-容器化-2496ED)
![License](https://img.shields.io/github/license/CKX3197273653/Nozomi)
