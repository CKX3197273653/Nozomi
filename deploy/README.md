# Mate10 容器化部署方案

一套完整的 **11 服务编排**，已在华为云 ECS（Ubuntu 22.04 / 8 vCPU / 16 GB / x86_64）验证通过。

---

## 架构

| 服务 | 镜像 | 对外端口 | 说明 |
|---|---|---|---|
| **frontend** | mate10-frontend | **80** | Nginx 托管 Vue 静态文件 + 反向代理 |
| backend | mate10-backend | `127.0.0.1:8080` | Spring Boot 应用（前端经 Docker 内网访问） |
| mysql | mysql:8.0 | `127.0.0.1:3306` | 业务数据 |
| mongo | mongo:6 | `127.0.0.1:27017` | 聊天记录 |
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

**表结构由 Flyway 在 backend 启动时自动创建** ✅

- `MYSQL_DATABASE: mate10db` 已在 compose 中声明 → MySQL 首次启动自动建库
- backend 启动时 Flyway 接着建表（`sys_user` / `qc_report` / `qc_defect` /
  `qc_production_param` / `chat` / `chat_record`）

```bash
# 验证 Flyway 是否执行成功
docker compose logs backend | grep -i flyway
# 期望：Successfully baselined schema with version: 1
#   或：Successfully applied 1 migration to schema `mate10db`

# 查看版本记录表
docker exec mate10-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
  -e "USE mate10db; SELECT version, description, type, success FROM flyway_schema_history;"
```

> ⚠️ 迁移脚本位于 `mate10/src/main/resources/db/migration/`，会打包进后端镜像。
> **新增表结构变更请新建 `V2__xxx.sql`，不要修改已执行过的脚本**
> （校验和变化会导致启动直接失败）。

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

<!-- ✅ 含 spring-boot-flyway（FlywayAutoConfiguration） -->
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

**排查方法**：

1. IDE 报「无法解析配置属性」→ 大概率是集成模块不在类路径上
   （**这个警告通常是真的，别急着当成 IDE 缓存问题**）
2. 查官方文档的「自动配置类」附录，确认模块名
3. `mvn dependency:tree` 确认模块是否真的被引入

### 前端请求为什么不用改代码

开发环境靠 Vite 的 `server.proxy` 转发 `/blocker`、`/api`、`/rag`、`/ai`；
生产环境由 Nginx 做同样的事。
由于 axios 的 `baseURL` 是空字符串（走相对路径），**前端代码在两种环境下完全一致**。

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