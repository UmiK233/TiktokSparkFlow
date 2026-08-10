# 构建阶段
FROM node:23 AS build

WORKDIR /app

COPY package.json package-lock.json ./
# 使用 lock 文件精确安装
RUN npm ci

COPY . .

RUN npm run build

# 运行阶段
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginxconf/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80