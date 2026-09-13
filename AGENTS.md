# AGENTS.md — Mate10 项目

## 项目概览

这不是单体仓库，而是两个独立项目共存：
- **`mate10/`** — Spring Boot 4.0 后端（Maven / Java 17）
- **`mate10-V/`** — Vue 3 + Vite 前端（Node.js）
- **`milvus/`** — 空目录，Milvus 部署用
- 根目录的 `.zip` 文件是历史归档，不要依赖它们

两个项目需分别启动，不是 monorepo 共享构建。

---

## 后端 `mate10/`

### 启动与构建

```bash
# 开发模式（热部署）
mvn spring-boot:run

# 打包跳过测试
mvn clean package -DskipTests
java -jar target/mate10-0.0.1-SNAPSHOT.jar

# 使用 Maven Wrapper（无需本地安装 Maven）
./mvnw spring-boot:run   # Windows: mvnw.cmd spring-boot:run
```

### 中间件依赖（全部需要本地启动）

| 服务 | 端口 | 说明 |
|------|------|------|
| MySQL 8.0+ | 3306 | 业务/QC 数据，默认库 `mate10db` |
| MongoDB 4.0+ | 27017 | 聊天消息 |
| Redis 6.0+ | 6379 | 缓存 |
| Kafka 3.0+ | 9092 | 消息队列 |
| Milvus 2.x | 19530 | 向量库（需 etcd + minio） |
| Ollama | 11434 | 本地大模型服务 |
| Tesseract | - | OCR（图片质检需要） |

启动顺序建议：MySQL → MongoDB → Redis → Kafka → Milvus → Ollama → 后端

### 关键配置

- `src/main/resources/application.yml` — 所有中间件连接配置
- Tesseract OCR 路径硬编码为 `I:\OCR\tesseract-main`（Windows），换环境必须修改
- `ChatMapper` 中硬编码了 `mate10sql.chat` 跨库查询，若 MySQL 默认库不是 `mate10sql` 需同步修改
- `JwtUtil.SECRET_KEY` 是内置常量，生产环境必须外部配置
- `SecurityConfig` 当前 `permitAll`，未启用 JWT 全局拦截，正式环境需收紧

### 重要提醒

- **Word/Excel 解析未实现**：`FileParseServiceImpl` 中 Word/Excel 方法返回空字符串，上传这些格式 AI 无法提取内容
- **无测试文件**：`src/test/java` 为空，无法运行单元测试
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
- `/api` → `http://localhost:8080`
- `/rag` → `http://localhost:8080`

### 已知路径问题

- 前端登录请求 `/api/blocker/user/login`（带 `/api` 前缀），后端实际映射为 `/blocker/user/login`（无前缀）
- `request.js` 的 `baseURL` 为空，请求会打到 Vite 源再由代理转发
- 如登录 404，需统一两端路径前缀

### 架构要点

- Vue Router `createWebHistory`，生产部署需 Nginx `try_files` 回退
- Pinia 状态管理，登录守卫在 `router/index.js`
- Axios 拦截器自动从 `localStorage` 读取 token 并注入 `Authorization: Bearer <token>`
- 所有业务页复用 `layout/index.vue` 主布局

---

## 联调须知

1. **先启中间件**，再启后端，最后启前端
2. 后端 `mvn spring-boot:run` 和前端 `npm run dev` 可并行
3. Ollama 需先拉模型：`ollama pull deepseek-r1:latest` 和 `ollama pull all-minilm:latest`
4. Milvus standalone 需 Docker + etcd + minio，或使用官方 docker-compose
5. 前端 `npm run build` 前必须确认后端已运行，否则代理不可用
6. MySQL 初始化：`CREATE DATABASE IF NOT EXISTS mate10db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
