# 羽毛球 MES 本地一键启动

在已安装 Docker Desktop 的环境执行：

```powershell
docker network create mes-face   # 只需首次执行一次，供后端与 InsightFace 人脸服务互通
docker compose up -d --build
```

打开 `http://localhost`，使用管理员账号 `admin`、密码 `admin123` 登录。前端 Nginx 会将 `/api` 代理到 Spring Boot 服务；MySQL、Redis、后端和前端均由同一个 Compose 编排启动。默认使用 `docker.m.daocloud.io/library` 代理 Docker Hub、`registry.npmmirror.com` 下载 npm 依赖、阿里云 Maven 公共仓库下载 Gradle 依赖；均可在 `.env` 中覆盖。

开发环境启动时，Flyway 会创建并写入从 `2026-07-05` 到容器当前日期的演示工单、生产任务、派工明细、批次状态和报工事实数据，供生产报表、实时生产和电子看板联调使用。

只读接口冒烟测试会从前端 API 客户端自动提取路径，经 Nginx 逐条请求，不会写入业务数据：

```powershell
.\scripts\test-api-readonly.ps1
```

如需完全重建本地数据库与演示数据：

```powershell
docker compose down -v
docker compose up -d --build
```

部署到非本地环境时，请复制 `.env.example` 为 `.env` 并替换默认密码和微信小程序配置。
