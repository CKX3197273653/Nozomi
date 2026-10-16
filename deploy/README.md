# Mate10 容器化部署方案

一套完整的 **11 服务编排**，已在华为云 ECS（Ubuntu 22.04 / 8 vCPU / 16 GB / x86_64）验证通过。

---

## 架构

| 服务 | 镜像 | 对外端口 | 说明 |
|---|---|---|---|
| **frontend** | mate10-frontend | **80** | Nginx 托管 Vue 静态文件 + 反向代理 |
| backend | mate10-backend | `127.0.0.1:8080` | Spring Boot 应用（前端经 Docker 内网访问） |
| mysql | mysql:8.0 | `127.0.0.1:3306` | 业务数据 |
| mongo | mongo:6 | `127.0.0.1:27017` | 聊天记录 + 结论档案（`report_memory`） |
| redis | redis:7-alpine | `127.0.0.1:6379` | 缓存 |
| kafka | apache/kafka:4.1.0 | `127.0.0.1:9092` | 异步消息（KRaft 单节点） |
| milvus | milvusdb/milvus:v2.6.16 | `127.0.0.1:19530` | 向量库 |
| etcd | quay.io/coreos/etcd:v3.5.5 | — | Milvus 元数据 |
| minio | minio/minio | — | Milvus 对象存储 |
| prometheus | prom/prometheus | — | 指标抓取与存储（仅 Docker 内网） |
| **grafana** | grafana/grafana | **3000** | 监控仪表盘 |

> **安全设计**：**只有 `80`（网站）与 `3000`（Grafana）对公网开放**，
> 其余所有端口（含 backend 的 8080）**只绑定 `127.0.0.1`**。
> 数据库端口一旦暴露到公网，会在数小时内被扫描并入侵（挖矿 / 勒索），这是最常见的云安全事故。

服务间通过 **Docker 内部网络用服务名互访**（如 `mysql:3306`、`kafka:9092`），
无需 `host.docker.internal`，也无需关心宿主机 IP。

> 📌 **镜像 tag 建议与项目 release 对齐**：当前 compose 里是 `:0.5.0`，
> 而项目已到 `v0.11.0`，两者脱节会让「线上跑的是哪版」变得难以判断。
> 下次构建建议打成 `:0.11.0` 这类与 tag 同名的版本。

---

## 前置要求

| 项目 | 要求 |
|---|---|
| 架构 | **x86_64** ⚠️ 不要选鲲鹏/ARM64（镜像不兼容，且 tess4j 的 native 库在 ARM 上不可用） |
| 系统 | Ubuntu 22.04 LTS 或同等级 Linux |
| Docker | 20+ 且带 `docker compose` 插件 |
| 配置 | 建议 **4 vCPU / 8 GB** 起（Milvus standalone 本身就要 2~4 GB） |
| 磁盘 | 60 GB 起 |

---

## 部署步骤

### 1. 安装 Docker

```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker --version && docker compose version
```

### 2. 配置镜像加速器（国内环境必需）

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://<你的加速器地址>.mirror.swr.myhuaweicloud.com"
  ]
}
EOF
systemctl restart docker
```

> 不配加速器的话，`docker pull` 会因无法访问 Docker Hub 而超时。

### 3. 准备环境变量

```bash
cp .env.example .env
chmod 600 .env
vi .env
```

### 4. 启动全部服务

```bash
docker compose up -d
docker compose ps
```

### 5. 数据库初始化（无需手工操作）

**表结构由 Flyway 在 backend 启动时自动创建** 

- `MYSQL_DATABASE: mate10db` 已在 compose 中声明 → MySQL 首次启动自动建库
- backend 启动时 Flyway 按版本顺序执行 `db/migration/` 下的迁移：

  | 版本 | 内容 |
    |---|---|
  | `V1__init_schema.sql` | 建表：`sys_user` / `qc_report` / `qc_defect` / `qc_production_param` / `chat` / `chat_record` |
  | `V2__add_employee_roster.sql` | 员工工号名册 `sys_employee`（注册白名单 + 占用标记）|
  | `V3__add_user_emp_no.sql` | `sys_user.emp_no`（唯一），绑定注册用户与工号 |
  | `V4__fix_user_phone_type.sql` | `sys_user.phone` → `VARCHAR(20)` |
  | `V5__add_user_email_unique.sql` | `sys_user.uk_email` 唯一索引 |
  | `V6__add_chat_indexes.sql` | `chat.idx_user_id` / `chat.idx_update_time` |
  | `V7__rename_username_index.sql` | 统一索引命名 `username` → `uk_username` |

-  **开发环境的工号名册种子不会被加载** —— 它在
  `db/seed-dev/R__seed_employee_roster.sql`，**只在 `dev` profile 加载**
  （见 `application-dev.yml` 的 `spring.flyway.locations`）
  **生产环境名册是空的，必须由管理员导入真实员工工号** 
  （种子工号写在 public 仓库里 = 公开的有效工号，不能进生产）

```bash
# 验证 Flyway 是否执行成功
docker compose logs backend | grep -i flyway
# 全新库期望：Successfully applied N migrations to schema `mate10db`
#   注意：这里【不会】出现 "Successfully baselined schema" ——
#   只有"库非空但没有版本表"时才会 baseline

#  Spring Boot 集成后，Flyway 的 banner（"Flyway Community Edition ... by Redgate"）
#    不会出现，别拿它当判据；看 org.flywaydb.core.FlywayExecutor 那一行更可靠

# 最可靠的验收方式：查版本记录表
docker exec mate10-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "USE mate10db; SELECT installed_rank, version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;"
# 期望：V1~V7 全部 success=1，外加一行 R__（可重复迁移，version 为 NULL）
```

>  迁移脚本位于 `mate10/src/main/resources/db/migration/`，会打包进后端镜像。
> **新增表结构变更请新建 `V8__xxx.sql`，绝不要修改已执行过的脚本**
> （校验和变化会导致启动直接失败：`Migration checksum mismatch`）。

-  **已应用的迁移就冻结了** —— 改内容（哪怕只改注释/空格）→ **启动失败** 
  - 这个报错会**伪装成** `Error creating bean with name 'xxxMapper'`
    —— **真凶永远在最内层的 `Caused by`** 
  - 修复：新建空库重跑（干净），或
    `DELETE FROM flyway_schema_history WHERE version='N'` 让它**真正重新应用**
     **不要用 `flyway repair`** —— 它只改校验和、**不重跑迁移**，
    会留下"记录说做了、实际没做"的假象
- ️ **写迁移脚本时不要开着 devtools 自动重启**：
  `db/migration/*.sql` 不在 devtools 默认排除清单里 →
  **新建一个还没写完（甚至空的）迁移文件并保存**时应用会立刻重启，
  把它当正式迁移**应用掉** → 留下 `checksum = 0` 的假迁移 →
  之后写满内容就 `checksum mismatch` **启动失败**。
  预防（二选一）：写脚本时先停后端 / 或 `application-dev.yml` 加
  `spring.devtools.restart.additional-exclude: db/**`（ 是 **additional-exclude**）

### 6. 验证部署

```bash
# 应用是否响应（期望返回业务错误，说明数据库已连通）
curl -X POST "http://localhost:8080/blocker/user/login?username=probe&password=x"
# {"code":500,"data":null,"msg":"用户名/邮箱,密码错误或用户被禁用"}

# 前端
curl -o /dev/null -w "%{http_code}\n" http://localhost/
# 200

# 健康检查（免认证，应返回 UP 及 db/mongo/redis/diskSpace 明细）
curl http://localhost:8080/actuator/health

# Prometheus 指标（免认证，返回裸文本）
curl http://localhost:8080/actuator/prometheus | head -20

# 确认其他端点【仍需认证】（应返回 401）
curl -i http://localhost:8080/actuator/env

#  数据库迁移是否真的跑了（最可靠 —— 直接查库，不需要认证）
docker compose exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "USE mate10db; SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
#  全部 success=1，外加一行 R__（可重复迁移，version 为 NULL）
# ️ 如果这张表不存在，或者只有一行 BASELINE → Flyway 从未真正工作过
#    （排查见「踩坑记录 · flyway-core 被标成 test scope」）

# 用 Actuator 看迁移状态 —— ️ 该端点需认证，所以要带 token
curl -H "Authorization: Bearer <你的token>" http://localhost:8080/actuator/flyway
```

---

## 环境变量清单

| 变量 | 必需 | 说明 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | ✅ | MySQL root 密码 |
| `MONGO_ROOT_PASSWORD` | ✅ | MongoDB root 密码 |
| `REDIS_PASSWORD` | ✅ | Redis 密码 |
| `JWT_SECRET_KEY` | ✅ | **至少 32 字节**，否则应用启动即失败 |
| `DEEPSEEK_API_KEY` | ✅ | cloud 模式对话模型 |
| `ZHIPU_API_KEY` | ✅ | cloud 模式 Embedding |
| `GRAFANA_PASSWORD` | ✅ | Grafana 管理员密码（用户名固定 `admin`） |
| `APP_RAG_RERANK_ENABLED` |  **建议显式设 `false`** | 不设则默认 `true`，而容器里没有模型文件 → **启动失败**（见「踩坑记录 · Rerank」）|

**⚠️ 密码建议用纯字母数字** —— MongoDB 的 URI 里密码需要 URL 编码，
用特殊字符容易踩坑。

**⚠️ `.env` 绝不能提交到 git**（已在 `.gitignore` 中排除）。

---

## 常用运维命令

```bash
# 查看状态
docker compose ps

# 查看某个服务日志
docker compose logs -f backend

# 重启单个服务
docker compose restart backend

# 更新镜像（改代码后）
docker compose pull backend && docker compose up -d backend

# 停止全部（保留数据）
docker compose down

# 停止并删除数据卷（⚠️ 数据全丢）
docker compose down -v
```

**关于重启**：所有服务都配了 `restart: unless-stopped`，
**服务器重启后整个系统会自动恢复**，无需人工干预。

---

## 踩坑记录
### Kafka：容器内连不上 broker

`advertised.listeners` 默认是 `localhost:9092`，而容器里的 `localhost` 指向**容器自己**。

**解法**：在 Docker 网络里，直接用**服务名**作为 advertised 地址：

```yaml
KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
```

> 如果 Kafka 跑在宿主机上（不在 Docker 里），则需要额外加一个 listener，
> 并把 advertised 地址设为 `host.docker.internal`。

### Nginx：SSE 流式输出被缓冲

Nginx 默认会缓冲上游响应，导致流式 token 攒到最后一次性返回。

**解法**：

```nginx
location /ai {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Connection '';
    proxy_buffering off;        # 关键
    proxy_cache off;
    proxy_read_timeout 3600s;
}
```

### Nginx：Vue Router history 模式刷新 404

**解法**：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

### 镜像推送：manifest 解析失败

Docker 23+ 的 BuildKit 默认附加 provenance/SBOM 证明清单，镜像变成**多清单索引**，
部分 registry（如华为云 SWR）无法解析，报 `Invalid image, fail to parse 'manifest.json'`。

**解法**：

```bash
docker push --platform linux/amd64 <image>
```

### Spring Boot 4.0：加了依赖却「什么都不发生」

**现象**：`spring.flyway.*` 配置在 IDE 里报「无法解析配置属性」；
应用启动后**没有任何 Flyway 日志**，`flyway_schema_history` 表也没建 —— 但**也不报错**。

**原因**：Spring Boot 4.0 把自动配置**拆分成了独立模块**。
只加第三方库（`flyway-core`）不会引入 Spring Boot 的集成层（`spring-boot-flyway`），
自动配置不生效 —— **静默失效，最难排查**。

**解法**：用 starter，而不是裸库：

```xml
<!-- ❌ 只有库，没有集成层 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<!--  含 spring-boot-flyway（FlywayAutoConfiguration） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<!-- MySQL 方言需单独加，starter 不含 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

**通用规律**（SB 4.0 模块化陷阱 —— 「库 + 集成层」两件套）：

| 中间件 | 库 | 集成层（必需） |
|---|---|---|
| Flyway | flyway-core | spring-boot-flyway |
| MongoDB | mongodb-driver-sync | spring-boot-data-mongodb |
| Kafka | kafka-clients | spring-boot-kafka |
| MyBatis-Plus | mybatis-plus-core 等 | **mybatis-plus-spring-boot4-starter**（SB4 专用）|

> ️ **MyBatis-Plus 还有两个部署侧注意点**（详见 `AGENTS.md`「已知问题」）：
> 1. **绝不能钉它的内部子模块** —— 曾钉 `mybatis-plus-extension:3.5.7`
>    与 starter 的 `3.5.16` 混搭（Maven「最近者优先」）→ `IRepository` 找不到；
> 2.  **3.5.9+ 的分页拦截器在 `mybatis-plus-jsqlparser`**，要单独加、版本与主包一致

**排查方法**：

1. IDE 报「无法解析配置属性」→ 大概率是集成模块不在类路径上
   （**这个警告通常是真的，别急着当成 IDE 缓存问题**）
2. 查官方文档的「自动配置类」附录，确认模块名
3. `mvn dependency:tree` 确认模块是否真的被引入
4. **事后订正**：本项目当年那次"Flyway 不生效"，真正原因是
> **`flyway-core` 被标成了 `<scope>test</scope>`**（见下一条），
> 而不是"只加了裸库"。两者都会导致**同样的静默失效**，
> 所以这条「库 + 集成层」规律依然成立  —— 但排查时**两个都要查** 

###  `flyway-core` 被标成 `test` scope —— Flyway 静默失效
**现象**：**Flyway 一次都没执行过**，但 backend **启动完全正常、零报错、零日志** ——
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
    <!-- 不要写 <scope>test</scope> -->
</dependency>
```

> 「依赖加了」≠「依赖生效了」。本项目已踩 **3 次**同一类坑
> （MongoDB 配置未生效 / MyBatis-Plus starter 不匹配 / Flyway 依赖 scope 错），
> 共同特征都是 **零报错的静默失效** —— 只能靠**观测运行时行为**发现。

### 前端请求为什么不用改代码

开发环境靠 Vite 的 `server.proxy` 转发 `/blocker`、`/api`、`/rag`、`/ai`；
生产环境由 Nginx 做同样的事。
由于 axios 的 `baseURL` 是空字符串（走相对路径），**前端代码在两种环境下完全一致**。

---

### Redis：清缓存用的是 `UNLINK` 而不是 `DEL`

用 `MONITOR` 观察 Spring 清缓存，看到的是：

```
"UNLINK" "qcStats::batch:10" "qcStats::severity" "qcStats::defectType" ...
```

**为什么不是 `DEL`**：

| 命令 | 行为 | 影响 |
|---|---|---|
| `DEL` | **同步**删除，立即释放内存 | key 很大时（如几百 MB 的 List）会**阻塞 Redis 主线程** ⚠️ |
| `UNLINK` | 只把 key 从键空间摘除，**内存回收交给后台线程** | 不阻塞主线程 ✅ |

Redis 是单线程处理命令的，一次慢 `DEL` 会让所有其他请求排队。

> Spring Data Redis 默认用 `UNLINK`，前提是 Redis 4.0+（本项目开发环境 5.0.14、生产 7.x 都满足）。

### Redis：清缓存默认用 `KEYS` 扫描，数据量大时会阻塞

`RedisCache.clear()` 需要先找出该缓存名下的所有 key，Spring 默认用 `KEYS`：

```
"KEYS" "qcDetail::*"          ← O(N) 全库扫描！
"UNLINK" "qcDetail::defects:15" "qcDetail::report:15"
```

**`KEYS` 是 O(N) 遍历，且会阻塞 Redis** ⚠️ —— key 上万时会明显卡顿。

**优化思路：改用 `SCAN`（游标分批遍历，不阻塞）**

```java
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheWriter;

RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
        connectionFactory,
        BatchStrategies.scan(1000)      // 每批 1000 个 key
);

return RedisCacheManager.builder(cacheWriter)   // 用带 writer 的重载
        .cacheDefaults(config)
        .withInitialCacheConfigurations(initialCaches)
        .transactionAware()
        .build();
```

> ⚠️⚠️ **上面这段代码不能照抄直接替换现有写入器！**
>
> `nonLockingRedisCacheWriter` **不带 `immediateWrites()`**，而后者是修复
> 「`RedisCache.clear()` 静默失效」这个框架 bug 的关键（详见 `AGENTS.md` 的「已知问题」）。
> **照抄会让缓存失效重新变成静默失败** —— 写操作看起来成功了，但 Redis 里的 key 根本没删。
>
> **另外**：`SCAN` 与那个 bug 是**两件不相干的事** ——
> 项目里**已经试过** `BatchStrategies.scan(1000)`，`clear()` 仍然静默失败（已排除批处理策略层）。
> 真正的修复是 `immediateWrites()`。
>
> **所以要上 `SCAN`，必须**：
> ① 在**保留 `immediateWrites()`** 的前提下叠加批处理策略；
> ② 改完**实测 `clear()` 是否仍然生效**
>    （写一条缓存 → 触发 `@EvictQcCache` → `redis-cli` 确认 key 真被删）。
>
> 本项目当前 key 只有个位数，**暂时不需要改** ✅ —— 数据量上来（key 上万）再动，且务必按上面两步验证。

### ️ Rerank（精排）：模型缺失会导致容器启动失败

**现象**：重新构建镜像部署后，backend 容器**反复重启**，日志里能看到：

```
IllegalStateException: [Rerank] 模型文件不存在:D:/AIGC/bge-reranker-base/onnx/model.onnx
```

**证据链**：

```
application.yml       app.rag.rerank.enabled = true（默认开启）
application-prod.yml  未覆盖 app.rag → 生产也继承 true
model-path 默认值     D:/AIGC/...（Windows 路径，Linux 容器里不存在）
CrossEncoderReranker  构造函数校验模型文件，不存在就 throw → 上下文启动失败
docker-compose.yml    既没挂模型卷，也没设 RERANK_MODEL_PATH
```

**关键点**：这是一个**可选增强功能，却拥有让整个应用起不来的权力**。
本地能跑是因为 `D:/AIGC/` 真有那个模型文件 —— **典型的「本地正常、容器必炸」**。

**两种解法（部署时二选一）**：

| 方案 | 做法 | 适用 |
|---|---|---|
| **A. 关掉精排**（推荐） | compose 的 backend 环境变量加 `APP_RAG_RERANK_ENABLED: "false"` | CPU 推理慢（30 题约 145s），云服务器体验差 |
| **B. 挂载模型** | 设 `RERANK_MODEL_PATH` / `RERANK_TOKENIZER_PATH`，并挂卷 `./models/bge-reranker-base:/app/models/bge-reranker-base:ro` | 服务器性能足够、确实需要精排 |

**方案 A 示例**：

```yaml
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: prod,cloud
      APP_RAG_RERANK_ENABLED: "false"      # Spring 宽松绑定：app.rag.rerank.enabled
```

**方案 B 示例**：

```yaml
  backend:
    environment:
      RERANK_MODEL_PATH: /app/models/bge-reranker-base/onnx/model_quantized.onnx
      RERANK_TOKENIZER_PATH: /app/models/bge-reranker-base/tokenizer.json
    volumes:
      - ./models/bge-reranker-base:/app/models/bge-reranker-base:ro
```

> 关掉后 `HybridRetriever` 自动回退 RRF 融合，命中率 100% → 96.7%（仍可用）。

---



## 监控（Prometheus + Grafana）

### 指标链路

```
backend ──/actuator/prometheus──► prometheus ──► grafana
（Micrometer 采集）              （抓取 + 存储）  （可视化 :3000）
        ▲
        └── 同 Docker 网络，用 backend:8080 内网抓取
            不经过 Nginx，也不暴露公网 ✅
```

### 启动

```bash
docker compose up -d prometheus grafana
docker compose ps          # 确认两个容器都是 Up
```

### 访问

- **Grafana**：`http://<服务器IP>:3000`，账号 `admin`，密码为 `.env` 中的 `GRAFANA_PASSWORD`
- **Prometheus**：未映射端口，仅内网可用；需要时用
  `docker compose exec prometheus wget -qO- localhost:9090`

### 验证抓取是否正常

```bash
docker compose exec prometheus \
  wget -qO- 'http://localhost:9090/api/v1/targets' | head -c 500
# 期望看到 "health":"up"
```

### Grafana 导入仪表盘

登录后 `Dashboards` → `New` → `Import`，直接填官方模板 ID：

| 模板 ID | 内容 |
|---|---|
| `11378` | JVM (Micrometer) —— 内存 / GC / 线程 |
| `12900` | Spring Boot 综合面板（HTTP / 连接池 / JVM） |

数据源选 `Prometheus`，URL 填 `http://prometheus:9090` ✅

### 常见问题

| 现象 | 原因 |
|---|---|
| Grafana 无数据 | backend 未重启（配置未生效），或 Prometheus 抓取返回 401 |
| 抓取 401 | `SecurityConfig` 未放行 `/actuator/prometheus` |
| 抓取 404 | `management.endpoints.web.exposure.include` 未包含 `prometheus` |
| 抓取连接失败 | 目标写成了 `localhost:8080` —— **容器里的 localhost 是容器自己**，必须用 `backend:8080` |

---

## Redis 缓存

### 缓存设计

| 缓存名 | 内容 | TTL |
|---|---|---|
| `qcStats` | 统计聚合（趋势 / 生产线 / 产品批次 / 缺陷类型 / 严重程度） | **5 分钟** |
| `qcDetail` | 明细（报告详情 / 缺陷列表 / 3D 数据 / 生产参数） | **30 分钟** |

- 读方法用 `@Cacheable`，写方法统一用组合注解 **`@EvictQcCache`**
  （等价于 `@CacheEvict(cacheNames = {"qcStats", "qcDetail"}, allEntries = true)`）
- 失效采用**粗粒度**：QC 数据写入低频、统计查询高频，「写一次清一次」简单且不会漏清
- 缓存操作由 `RedisConfig` 的 `transactionAware()` 保证在**事务提交后**执行，避免脏数据回写
- **写入器必须用 `immediateWrites()`**：

  ```java
  RedisCacheWriter.create(connectionFactory, b -> b.immediateWrites());
  ```

  Spring Data Redis 3.x+ 默认的 `RedisCacheWriter` 是**批量写入**，
  会让 `clear()`（即 `@EvictQcCache` 的 `allEntries=true`）**静默失效** ——
  无异常、无报错，但 Redis 里的 key 根本没删。
  `immediateWrites()` 强制所有写入立即同步执行，恢复正常。

  > 详细排查过程见 `AGENTS.md` 的「已知问题」；
  > `QcReportCacheIntegrationTest` 就是为「只有真 Redis 才能抓到」这类 bug 而生的。

### ⚠️ 生产必配：`maxmemory`

**默认配置下 Redis 无内存上限，且内存满时直接报错**：

```
maxmemory_human:0B             ← 0 = 无上限，会一直吃内存直到拖垮宿主机 ☠️
maxmemory_policy:noeviction    ← 内存满时【写操作直接失败】，不淘汰旧 key ❌
```

**正确配置**（`docker-compose.yml` 的 redis `command` 里加参数）：

```yaml
redis:
  command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes \
           --maxmemory 512mb --maxmemory-policy allkeys-lru
```

**为什么缓存场景用 `allkeys-lru`**：

```
缓存数据【都可以重建】（重新查数据库即可）
        ↓
内存满时淘汰"最久未使用"的 key，比直接报错合理得多 ✅
        ↓
LRU = Least Recently Used
```

### 验证方法

**① 看缓存内容与 TTL**

```bash
# 本机 redis-cli 位置（Windows）：I:\Redis-x64-5\Redis-x64-5.0.14.1\redis-cli.exe
redis-cli KEYS "qcStats*"
redis-cli TTL "qcStats::trend:7"        # 期望 ≤ 300（5 分钟）
redis-cli GET "qcStats::severity"       # JSON 明文，可直接阅读
```

> 选 `GenericJacksonJsonRedisSerializer` 而非 JDK 序列化，正是为了让缓存内容**可读可排查** ✅

**② 实时观察缓存行为（最直观）**

```bash
redis-cli MONITOR
```

然后操作页面，观察输出：

| 看到什么 | 含义 |
|---|---|
| 只有 `GET` | ✅ 命中缓存，**没有查数据库** |
| `GET` 后跟 `SET` | ⚠️ 未命中，查完库写入缓存 |
| `UNLINK` / `DEL` | 🎯 写操作触发了缓存失效 |

```
# 命中示例
"GET" "qcStats::line"

# 失效示例
"KEYS"   "qcStats::*"
"UNLINK" "qcStats::batch:10" "qcStats::severity" ...
```

> ⚠️ `MONITOR` 会输出**所有**命令，有性能开销，**仅用于开发/排查**，不要在生产长期开启。

---

## 安全注意事项

1. **中间件端口不要对公网开放** —— 只在 `127.0.0.1` 上绑定
2. **`.env` 不进仓库** —— 已通过 `.gitignore` 排除
3. **JWT 密钥长度 ≥ 32 字节** —— 应用启动时会校验，不合格直接拒绝启动
4. **镜像内不含密钥** —— 应用只通过运行时环境变量接收密钥
5. **SSH 端口建议限制来源 IP** —— 至少使用密钥对登录，禁用密码登录
6. **公网服务器建议配置余额提醒** —— 按需实例欠费可能被自动释放

---

## 数据备份

```bash
cd deploy && set -a && . ./.env && set +a

# MySQL
docker exec mate10-mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --all-databases --single-transaction > mysql-all.sql

# MongoDB
docker exec mate10-mongo mongodump \
  --uri="mongodb://root:${MONGO_ROOT_PASSWORD}@localhost:27017/?authSource=admin" \
  --archive=/tmp/mongo.archive
docker cp mate10-mongo:/tmp/mongo.archive ./mongo.archive
```

> Milvus 的向量数据可通过重新"初始化质检知识库"重建，无需单独备份。