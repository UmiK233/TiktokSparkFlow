# Tiktok Spark Flow

一个用于管理抖音好友“续火花”的本地化工具。它通过 Playwright 驱动浏览器完成扫码登录、好友同步与消息发送，并提供 Vue 管理界面、任务记录和定时执行能力。

> 本项目仅供个人学习与自动化辅助使用。请遵守抖音平台规则，避免高频或骚扰性操作；使用本项目所产生的风险由使用者自行承担。

## 功能

- 扫码登录抖音并在本地持久化浏览器会话
- 同步好友列表，维护需要续火花的好友名单
- 向单个好友发送消息，或创建面向已选名单的批量发送任务
- 查看任务状态、失败重试和当日发送记录
- 配置每日定时任务、发送内容、重复发送策略与浏览器模式
- 在 Web 界面中查看并操作容器内浏览器画面（noVNC）

## 技术栈

- 后端：Java 17、Spring Boot、Playwright
- 前端：Vue 3、Vite、Element Plus
- 部署：Docker Compose、Nginx、Xvfb、noVNC

## 快速开始（Docker，推荐）

### 前置条件

- Docker Desktop（含 Docker Compose）
- Java 17 和 Maven Wrapper 可用（用于先构建后端 JAR）

### 启动

在项目根目录依次执行(仅首次构建, 后续不需要重复构建)：  
Windows：
```powershell
.\mvnw.cmd clean package -DskipTests
```

Linux / macOS：

```bash
./mvnw clean package -DskipTests
```
构建生成 Jar 包后 每次部署仅需执行如下命令:
```aiignore
docker compose up -d --build
```

启动完成后访问：

| 地址 | 用途 |
| --- | --- |
| http://localhost | Web 管理界面 |
| http://localhost:8080 | 后端 API |

首次使用时，在管理界面扫描二维码登录；随后同步好友、勾选续火花名单，并按需创建发送任务或设置每日计划。

查看服务日志：

```bash
docker compose logs -f
```

停止服务：

```bash
docker compose down
```

## 本地开发

### 后端

需要 Java 17。Windows 下执行：

```powershell
.\mvnw.cmd spring-boot:run
```

macOS / Linux 下执行：

```bash
./mvnw spring-boot:run
```
若修改本地代码，需要重新构建后端 JAR 包再进行部署，执行如下命令:
```bash
./mvnw clean package -DskipTests
```

后端默认运行在 `http://localhost:8080`。在 Linux 的有头模式下可通过VNC链接5900端口查看调试

### 前端

需要 Node.js（建议使用当前 LTS 版本）：

```bash
cd frontend
npm install
npm run dev
```

打开 Vite 输出的地址（默认 `http://localhost:5173`）。开发服务器会将 `/api` 请求转发到 `http://localhost:8080`。

## 使用流程

1. 打开 Web 界面，使用抖音 App 扫码登录。
2. 在“好友管理”中同步好友列表并保存续火花名单。
3. 需要立即执行时，创建批量发送任务，或对单个好友单独发送。
4. 在“任务中心”和“发送历史”中核对执行结果。
5. 如需自动执行，在“运行配置”中启用每日任务、设置时间和消息内容。

默认使用 **有头浏览器模式**，可在“浏览器监控”中观察扫码和操作过程。切换到无头模式会关闭当前浏览器会话，通常需要重新扫码登录。

## 数据与配置

运行期数据均保存在项目根目录的 `data/`，日志位于 `logs/`，并已被 Git 忽略：

| 路径 | 内容 |
| --- | --- |
| `data/single-user-profile/` | Chromium 浏览器资料与登录状态 |
| `data/friends.json` | 已同步的好友列表 |
| `data/friend-selection.json` | 续火花名单 |
| `data/send-tasks/` | 发送任务及状态 |
| `data/send-history/` | 每日成功发送记录 |
| `data/runtime-settings.json` | 页面保存的运行配置 |
| `logs/tiktok-sparkflow.log` | 应用日志 |

请勿提交、公开或与他人共享 `data/single-user-profile/`：其中可能包含登录 Cookie 和会话信息。退出登录会清理本地浏览器资料；如需完整重置，也可在**停止服务后**自行备份或移除 `data/` 目录。

主要静态配置位于 [application.yaml](src/main/resources/application.yaml)，包括数据目录、超时时间、好友同步超时和批量发送间隔。应用默认使用 `GMT+8` 时区；每日任务每 15 秒检查一次，在设定分钟内只会触发一次。

## 常见问题

### Docker 构建时找不到 JAR

Dockerfile 会复制 `target/*.jar`，如无构建的jar请先执行 `./mvnw clean package -DskipTests`（Windows 使用 `./mvnw.cmd`），再运行 `docker compose up --build`。

### 浏览器无法启动或提示 Profile 被占用

请确认没有其他本地进程或容器正在使用同一个 `data/single-user-profile/`。停止旧进程后重试；应用会尝试清理部分遗留的 Chromium 临时锁。