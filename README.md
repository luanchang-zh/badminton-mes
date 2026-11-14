# 羽毛球 MES 本地一键启动

在已安装 Docker Desktop 的环境执行：

```powershell
docker network create mes-face   # 只需首次执行一次，供后端与 InsightFace 人脸服务互通
docker compose up -d --build
```

打开 `http://localhost`，在登录页注册工作账号。全新数据库默认没有演示账号或知识库绑定。前端 Nginx 会将 `/api` 代理到 Spring Boot 服务。默认使用 `docker.m.daocloud.io/library` 代理 Docker Hub、`registry.npmmirror.com` 下载 npm 依赖、阿里云 Maven 公共仓库下载 Gradle 依赖，均可在 `.env` 中覆盖。宿主机端口和镜像标签也可通过 `.env.example` 中列出的变量覆盖。Web 可以独立运行，联调微信小程序时再配置 AppID 和密钥。

### 随 MES 启动 LightRAG

按 `lightrag-service/.env.example` 配置 `lightrag-service/.env` 中的生成模型和向量模型。在根目录 `.env` 中设置 `MES_RAG_INTERNAL_API_KEY`，Compose 将这一密钥同时传给 MES 和 LightRAG。创建上文的 `mes-face` 网络后执行：

```powershell
docker compose -f docker-compose.yml -f compose.lightrag.yml up -d --build --wait
Invoke-RestMethod http://127.0.0.1:9623/health
```

PostgreSQL 和 Neo4j 使用当前 Compose 项目的数据卷和内部网络。LightRAG 的输入文件和工作目录保存在 `lightrag-files` 卷中。LightRAG 租户清单须包含 MES 绑定的租户 ID（新绑定默认为 `mes_u_<用户主键>`），每个租户使用独立 workspace。默认读取 `lightrag-service/config/tenants.yaml`，可用 `MES_LIGHTRAG_TENANTS_FILE` 指定本地清单。更新清单后重启 LightRAG。参见 [MES 对接说明](docs/lightrag-rag/05-mes-chat-integration.md)。

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
