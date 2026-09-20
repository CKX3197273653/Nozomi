# ========== 阶段 1：构建 ==========
FROM node:20-alpine AS builder

WORKDIR /build

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ========== 阶段 2：Nginx 托管 ==========
FROM nginx:alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80