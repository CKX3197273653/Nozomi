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
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    '/rag': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

> 后端另有 CORS 白名单，默认已放行 `http://localhost:5173`。
> 注意：登录接口实际地址为 `/api/blocker/user/login`，若 `request.js` 的 `baseURL` 留空则请求会打到当前 Vite 源，再由代理转发到 8080。

## 目录结构

```
mate10-v/
├── index.html
├── vite.config.js        # 别名 @、依赖预构建、/api /rag 代理
├── package.json
└── src/
    ├── main.js           # 入口：Pinia + Router + Element Plus + 全量图标注册
    ├── App.vue
    ├── style.css
    ├── api/
    │   ├── auth.js       # 登录
    │   ├── qc.js         # 报告/缺陷/参数/统计/3D 接口
    │   └── rag.js        # 知识库问答接口
    ├── layout/
    │   └── index.vue     # 主布局（顶栏 + 侧边菜单 + 内容区）
    ├── router/
    │   └── index.js      # 路由 + 未登录跳转守卫
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
            └── QA.vue
```

## 说明

- 路由使用 `createWebHistory`，生产环境部署需服务器配置 history 回退（如 Nginx `try_files`）。
- 请求拦截器自动从 `localStorage` 读取 `token` 并写入 `Authorization: Bearer <token>`；响应 `401` 时清理登录态并跳转登录页。
- 已知联调问题：前端登录请求 `/api/blocker/user/login` 带 `/api` 前缀，而后端实际映射为 `/blocker/user/login`（无前缀），`request.js` 的 `baseURL` 也为空；如登录 404，需统一两端的路径前缀。
- 对应后端接口说明请见后端项目 [`../mate10/README.md`](../mate10/README.md)。
