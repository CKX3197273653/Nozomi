# mate10-v 前端（智能质检分析平台）

> Vue 3 + Vite 构建的深色科技风单页应用，配套后端 `mate10` 服务（Spring Boot，:8080）。
> 围绕质检业务提供 **报告管理、AI 缺陷分析结果可视化、知识问答（RAG）** 等能力。

## 功能页面

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | 登录 | 用户名密码登录，token 存 `localStorage` |
| `/dashboard` | 质检仪表盘 | 报告/缺陷/产线概览统计 |
| `/report` | 报告管理 | 上传质检报告、状态流转、查看 AI 分析缺陷、根因分析 |
| `/defect` | 缺陷分析 | 缺陷列表、类型/严重程度统计、人工确认 |
| `/trend` | 趋势分析 | 按天/产线/批次缺陷趋势图表 |
| `/visual3d` | 3D 可视化 | Three.js 工件三维模型 + 缺陷点渲染与交互 |
| `/qa` | 知识问答 | RAG 质检助手对话、初始化知识库、上传知识文档 |
| `/agent` | **质检 Agent** | 模型自主调用工具做根因分析，**实时展示工具调用轨迹** |

整体为**智能质检分析平台**（深色背景 + 侧边菜单 + 顶栏），所有业务页共用主布局 `layout/index.vue`。

## 技术栈

- Vue 3（`<script setup>` SFC）
- Vite
- Vue Router（含登录守卫）+ Pinia
- Element Plus + `@element-plus/icons-vue`
- ECharts（统计图表）
- Three.js（3D 可视化）
- Axios（统一请求封装 + token 拦截）

## 快速开始

### 环境要求

- Node.js **20.19+ / 22.12+**（Vite 8 要求）
- npm / pnpm
- 后端服务已启动在 `http://localhost:8080`（含 MySQL / Redis / Kafka / Milvus / Ollama 依赖，见后端 README）

### 安装与运行

```bash
npm install
npm run dev      # 启动开发服务器 http://localhost:5173
```

生产构建与预览：

```bash
npm run build    # 产物输出 dist/
npm run preview
```

### 联调配置（Vite 代理）

`vite.config.js` 已配置代理，开发环境无需额外处理跨域：

```js
server: {
  port: 5173,
  proxy: {
    '/blocker': { target: 'http://localhost:8080', changeOrigin: true },  // 登录/注册
    '/api':     { target: 'http://localhost:8080', changeOrigin: true },  // QC 业务
    '/rag':     { target: 'http://localhost:8080', changeOrigin: true },  // 知识库
    '/ai':      { target: 'http://localhost:8080', changeOrigin: true }   // 对话 / 流式 / Agent
  }
}
```

> 后端另有 CORS 白名单，默认已放行 `http://localhost:5173`。
> `request.js` 的 `baseURL` 为空 → 请求走相对路径 → 由 Vite 代理转发到 8080。

## 目录结构

```
mate10-v/
├── index.html
├── vite.config.js        # 别名 @、assetsInclude glb、/blocker /api /rag /ai 代理
├── package.json
└── src/
    ├── main.js           # 入口：Pinia + Router + Element Plus + 全量图标注册
    ├── App.vue
    ├── style.css
    ├── api/
    │   ├── auth.js       # 登录
    │   ├── chat.js       # 对话（含 chatStream：原生 fetch 流式）
    │   ├── agent.js      # 质检 Agent 流式（agentStream：原生 fetch + SSE 解析）
    │   ├── qc.js         # 报告/缺陷/参数/统计/3D 接口
    │   └── rag.js        # 知识库问答接口
    ├── components/
    │   └── 3d/
    │       └── ModelViewer.vue   # Three.js GLB 模型查看器
    ├── layout/
    │   └── index.vue     # 主布局（顶栏 + 侧边菜单 + 内容区）
    ├── router/
    │   └── index.js      # 路由 + 未登录跳转守卫
    ├── stores/
    │   └── user.js       # Pinia 用户状态
    ├── utils/
    │   └── request.js    # Axios 封装（token 注入 / 401 处理）
    └── views/
        ├── Login.vue
        └── qc/
            ├── Dashboard.vue
            ├── Report.vue
            ├── Defect.vue
            ├── Trend.vue
            ├── Visual3D.vue
            ├── QA.vue
            └── Agent.vue      # 质检 Agent（工具调用轨迹实时展示）
```

## 说明

- 路由使用 `createWebHistory`，生产环境部署需服务器配置 history 回退（如 Nginx `try_files`）。
- 请求拦截器自动从 `localStorage` 读取 `token` 并写入 `Authorization: Bearer <token>`；响应 `401` 时清理登录态并跳转登录页。
- **流式接口不走 axios**：`api/chat.js` 的 `chatStream()` 与 `api/agent.js` 的 `agentStream()`
  用原生 `fetch` + `ReadableStream` —— 因为 SSE 响应是裸文本流（没有 `{code,msg,data}` 结构），
  且 `EventSource` 无法携带 `Authorization` 头。

> ⚠️ **SSE 多行 data 必须用 `\n` 拼回**：后端 `message` 事件含换行的 Markdown，
> 会被 SSE 规范拆成多个 `data:` 行。若直接拼接（`data += line`）会**丢掉换行**，
> 结论会显示成一整行。正确做法见 `api/agent.js` 的 `dataLines.join('\n')`。

- 对应后端接口说明请见后端项目 [`../mate10/README.md`](../mate10/README.md)。
