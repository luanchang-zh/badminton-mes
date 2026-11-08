# 目标

在 P1 已交付的多租户骨架之上，交付 LightRAG 多实例 **HTTP 服务入口**：单个 FastAPI 进程内为每个租户
创建独立的 `/tenant/{user_id}` 路由前缀，复用官方 `create_document_routes / create_query_routes /
create_graph_routes` 三个工厂；一个统一的 lifespan 负责全部实例的启动与关闭；所有租户共享一个模型并发上限；
租户路由只允许持有内部密钥的业务服务调用。

本阶段完成后，P3 的隔离验证脚本可以直接对 `http://127.0.0.1:<port>/tenant/{user_id}/...` 发起请求。

# 范围

## 目录结构

### 新建

- `lightrag-service/tenant/server.py` — 启动入口与 CLI：解析 `--config` / `--host` / `--port` / `--print-routes` / `--docs`；在导入任何 `lightrag.api.*` 之前固定 `sys.argv` 并完成上游配置初始化，避免 argparse 抢占命令行。
- `lightrag-service/tenant/app.py` — 应用构建：装配注册表、按租户循环注册三组路由到 `/tenant/{user_id}`，附加 `/health` 与统一异常处理；只构建、不启动。
- `lightrag-service/tenant/lifespan.py` — 统一生命周期：启动逐个初始化存储、失败逆序回滚；关闭先等待后台任务再逐个释放，共享状态最后只清理一次。
- `lightrag-service/tenant/llm_limiter.py` — 全局模型并发上限：`asyncio.Semaphore` 包住 LLM 与 Embedding 调用，所有租户共用。
- `lightrag-service/tenant/auth.py` — 内部鉴权：把内部密钥传给三个路由工厂，并在启动时拦截会破坏鉴权的配置。
- `lightrag-service/tests/test_app_routes.py` — 前缀注册与路由数量单测。
- `lightrag-service/tests/test_auth.py` — 内部密钥鉴权单测。
- `lightrag-service/tests/test_lifespan.py` — 启停顺序与次数单测。
- `lightrag-service/tests/test_limiter.py` — 并发上限单测。
- `lightrag-service/tests/test_server.py` — CLI 参数、`--print-routes` 输出与配置拒绝单测。

### 修改

- `lightrag-service/tenant/settings.py` — 增加监听地址、端口、内部密钥、模型并发上限、文档开关等配置项。
- `lightrag-service/tenant/factory.py` — 让 LLM 与 Embedding 构造走并发限流包装。
- `lightrag-service/tenant/__init__.py` — 导出新增的公开符号。
- `lightrag-service/.env.example` — 补充 P2 新增的环境变量及说明。
- `lightrag-service/README.md` — 补充启动命令、`--print-routes`、内部密钥与端口约定。

### 删除

无

### 明确不建

- 不改动 vendored 的 `lightrag-service/LightRAG/` — P2 只用官方路由工厂的既有参数，不打补丁。
- 不建 Ollama 兼容接口与 WebUI 静态资源路由 — 课设不需要，且会扩大鉴权面。
- 不建 docker 应用容器与 Dockerfile — 属于部署话题，P2 只保证 `python -m tenant.server` 可运行。
- 不建进程守护、多 worker、反向代理配置 — 本阶段固定单进程。
- 不建 P3 的隔离验证脚本与 P4/P5 的 Java 模块。

# 非目标

- 不连接真实 PostgreSQL / Neo4j，不调用真实模型；P2 的验收不依赖这些外部服务。
- 不实现按租户拆分后台任务队列：v1.5.7 的后台任务集合是全应用共享的，关闭时整体等待一次即可。
- 不改动 LightRAG 的检索算法、存储实现与鉴权实现。
- 不回归测试既有 MES 的 Java、前端与小程序代码。

# 约束与不变量

- 复用 P1 的 `load_tenants()` 与 `validate_tenants()`：校验失败时服务必须拒绝启动，不进入路由注册。
- 路由只在应用构建阶段注册，运行期不得增删。
- 三个路由工厂的 `api_key` 统一传内部密钥；空密钥时服务只允许绑定回环地址。
- 禁止 `WHITELIST_PATHS` 覆盖租户前缀（如设为 `/*`）：命中即拒绝启动，否则所有租户路由免鉴权。
- 后台任务集合全应用共享，关闭时只能整体等待一次，不能按租户拆分；`finalize_share_data()` 只调用一次。
- `--print-routes` 不得连接数据库、不得调用模型、不得启动 uvicorn。
- 代码规范：单文件不超过 400 行，单函数不超过 50 行，公开函数带函数级注释。

# 关键决定

- **路由工厂签名**（已核对 v1.5.7）：`create_document_routes(rag, doc_manager, api_key)`、`create_query_routes(rag, api_key, top_k)`、`create_graph_routes(rag, api_key)`，单租户共 33 条路由（文档 18 + 查询 3 + 图谱 12）。
- **上游配置初始化时机**：`lightrag/api/config.py` 的 `global_args` 是首次访问即初始化的代理，导入路由模块会触发 argparse；因此入口必须先固定 `sys.argv` 再导入。
- **pipeline_status 已按 workspace 隔离**：`initialize_storages()` 内部按实例 workspace 初始化，入口层不再重复包隔离。

# 验证期望

- 本机补装最小运行依赖（`fastapi`、`starlette`、`aiofiles`、`aiohttp`、`networkx`、`tiktoken`、`json_repair`、`nano-vectordb`、`pypinyin`、`ascii_colors`、`python-dotenv`、`pipmaster`、`python-multipart`），不装 spacy / langchain 等文档解析大包。
- 本机执行 `pytest lightrag-service/tests` 与 `python -m tenant.server --print-routes`，两者都不连数据库、不调模型。
- 路由与鉴权用 FastAPI `TestClient` 真实验证；生命周期与限流用替身验证，不引入真实存储与网络调用。

# 未决问题

（已确认）Q1: 本机补装最小运行依赖，用 FastAPI `TestClient` 真实验证路由表与鉴权；不安装 spacy / langchain 等文档解析依赖。

# 验收示例

- 构建 2 个租户的应用后，路由表中存在 `/tenant/user_1/...` 与 `/tenant/user_2/...` 两组路由，每组各 33 条，且不存在未加租户前缀的 `/documents/upload`、`/query`、`/graphs`。
- 执行 `python -m tenant.server --print-routes`，输出 2 个租户各自带 `/tenant/<user_id>` 前缀的路由，过程中不连接数据库、不调用模型。
- 租户配置非法（重复 workspace 或设置了 `POSTGRES_WORKSPACE`）时执行 `--print-routes`，进程以非 0 退出码结束且不打印任何路由。
- 请求任一租户路由时不带 `X-API-Key`，或带错误的 `X-API-Key`，响应状态为 401 或 403。
- 请求任一租户路由时带正确的 `X-API-Key`，响应状态不是 401 也不是 403。
- `WHITELIST_PATHS` 被配置为通配全部路径时，服务拒绝启动且错误信息指出白名单会绕过租户鉴权。
- 应用启动再关闭后，每个租户实例的存储初始化恰好一次、存储释放恰好一次，共享状态清理恰好一次。
- 关闭时若仍有后台任务在运行，实例存储释放发生在等待后台任务结束之后。
- 全局并发上限为 N 时并发发起多于 N 个模型调用，任一时刻正在执行的调用数不超过 N，且全部调用最终完成。
- 未配置全局并发上限时并发发起多个模型调用，调用不被限流阻塞且全部成功完成。
