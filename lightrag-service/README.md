# LightRAG 多租户服务（P1 多租户底座 + P2 HTTP 服务入口）

固定 LightRAG v1.5.7 源码，用声明式配置描述「业务用户 → workspace」映射，
装配前完成隔离校验（P1），再把每个租户的官方路由挂到独立前缀下对外提供 HTTP 接口（P2）。
本机验收不连接数据库、不调用模型。

## 目录结构

```text
lightrag-service/
├── LightRAG/                  # vendored 上游源码，固定 v1.5.7，已删除其 .git
├── config/tenants.yaml        # 固定租户映射：user_id → workspace
├── tenant/
│   ├── settings.py            # 环境变量收口（存储、模型、目录、容量）
│   ├── config.py              # 解析 YAML 并推导各租户目录
│   ├── validator.py           # 启动前隔离校验
│   ├── factory.py             # 构造 LLM / Embedding 函数与 LightRAG 实例
│   ├── registry.py            # TenantContext / TenantRegistry
│   ├── check.py               # 自检 CLI
│   ├── server.py              # 启动入口（--print-routes）
│   ├── app.py                 # 应用构建与路由注册
│   ├── lifespan.py            # 统一生命周期
│   ├── llm_limiter.py         # 全局模型并发上限
│   ├── auth.py                # 内部鉴权与启动期安全校验
│   └── placeholder.py         # 打印路由用的占位实例
├── tests/                     # pytest 单测（不连数据库、不调模型）
├── docker-compose.yml         # 依赖组件：PostgreSQL + pgvector、Neo4j + APOC
├── requirements.txt
└── .env.example
```

## 安装

```bash
cd lightrag-service

# 建议使用虚拟环境
python -m venv .venv
.venv/Scripts/activate        # Windows
# source .venv/bin/activate   # Linux / macOS

# vendored 源码以可编辑方式安装，后续补丁直接落在这里
pip install -e ./LightRAG
pip install -r requirements.txt
```

## 启动依赖组件

只启动 RAG 依赖的两个组件，与仓库根目录的 MySQL / Redis / MES 编排互不影响：

```bash
docker compose -f lightrag-service/docker-compose.yml up -d
docker compose -f lightrag-service/docker-compose.yml ps
```

默认端口（均只绑定 `127.0.0.1`）：

| 组件 | 宿主机端口 | 说明 |
| --- | --- | --- |
| PostgreSQL | 15432 | 带 pgvector，用于 KV / 向量 / 文档状态 |
| Neo4j Bolt | 17687 | 图存储 |
| Neo4j 浏览器 | 17474 | 图谱控制台 |

镜像地址由 `LR_PG_IMAGE` / `LR_NEO4J_IMAGE` 注入，默认走国内镜像前缀；
`.env.example` 中有全部可覆盖项。**不要**设置 `POSTGRES_WORKSPACE`、
`NEO4J_WORKSPACE` 或 `WORKSPACE`：它们会覆盖实例级 workspace，导致租户串数据。

## 配置

1. 复制 `.env.example` 为 `.env` 并按需修改（`.env` 已被忽略，不会入库）。
2. 编辑 `config/tenants.yaml` 增删演示用户：

```yaml
tenants:
  - user_id: user_1
    workspace: ws_001
  - user_id: user_2
    workspace: ws_002
```

`user_id` 使用业务系统的可信内部标识，`workspace` 只允许字母、数字与下划线。
上传目录与工作目录由 workspace 推导为 `<LR_INPUT_DIR>/<workspace>` 与
`<LR_WORKING_DIR>/<workspace>`，与官方 `DocumentManager` 的规则一致。

## 本机自检

```bash
# 配置与隔离校验（不连数据库、不调模型）
python -m tenant.check

# 单测
python -m pytest
```

## 启动多租户 HTTP 服务

```bash
# 打印租户路由表：不连数据库、不调模型、不启动 uvicorn
python -m tenant.server --print-routes

# 真实启动（需要模型配置与已启动的 PostgreSQL / Neo4j）
python -m tenant.server
python -m tenant.server --host 127.0.0.1 --port 9621 --docs
```

每个租户拥有 33 条路由（文档 18、查询 3、图谱 12），统一挂在 `/tenant/{user_id}` 前缀下：

```text
POST   http://127.0.0.1:9621/tenant/user_1/documents/upload
POST   http://127.0.0.1:9621/tenant/user_1/query
GET    http://127.0.0.1:9621/tenant/user_1/graphs
```

### 内部鉴权

租户路由只允许可信业务服务访问，请求需携带 `X-API-Key`（值取 `LR_INTERNAL_API_KEY`）：

```bash
curl -H "X-API-Key: $LR_INTERNAL_API_KEY" \
  http://127.0.0.1:9621/tenant/user_1/documents/status_counts
```

以下情况服务会**拒绝启动**并说明原因：

- 监听非回环地址但没有配置内部密钥；
- `WHITELIST_PATHS` 配成 `/*` 这类全通配（会让租户路由绕过鉴权）；
- 租户配置非法（重复 workspace、设置了 `POSTGRES_WORKSPACE` 等）。

以上三项对**进程环境变量**与 **`.env` 文件**同等生效：入口在读取配置前会先加载 `.env`，
避免出现「校验看不到、运行时却生效」的漏洞。

`/health` 不参与租户隔离，也不要求密钥，供存活探针使用。

### 生命周期与并发

- 启动时逐租户初始化存储，任一失败即回滚已初始化的实例并中止启动；
- 关闭时先等待后台任务，再逐个释放实例，共享状态只清理一次；
- 所有租户共享同一个模型并发上限（`LR_LLM_CONCURRENCY`），在模型调用层限流；
  留空或 0 表示不限制。

`tenant.check` 自检通过时退出码为 0，会打印每个租户的 workspace 与推导出的两个目录；
以下情况退出码非 0 并给出具体原因：

- `user_id` 或 `workspace` 重复；
- `workspace` 为空、含路径分隔符或白名单外字符；
- 各租户目录相同或互相包含；
- 设置了 `POSTGRES_WORKSPACE` / `NEO4J_WORKSPACE` / `WORKSPACE`；
- 租户列表为空。

模型参数（`LR_LLM_MODEL`、`LR_EMBEDDING_MODEL`、`LR_EMBEDDING_DIM`）未配置时只输出
警告，自检仍然通过，只展示配置推导结果而不构建真实实例。

## 与后续阶段的关系

- **P3**：隔离验证脚本（交叉查询、同名文件、并发上传删除、重启恢复、跨库核对）。
- **P4 / P5**：Java 业务服务的租户映射与对外接口。

刻意不实现动态实例池、LRU、自动扩缩容，也不改动 LightRAG 的检索算法。
