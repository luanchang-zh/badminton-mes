# LightRAG 多租户知识库开发规划

> 依据：`badminton-mes.wiki/LightRAG_课设技术方案.md`（第七节第 2～4 步 + 附录五项优化）。
> 范围：LightRAG 静态多实例二开、隔离验证脚本、Java 业务服务接入。前端与课设演示不在本规划内。
> 测试约定：只使用脚本和单元测试，不引入专业测试平台；本机不要求做集成测试，先完成开发，联调放到有完整环境时再做。

## 总览

| 阶段 | 名称 | 所在位置 | 主要产出 | 依赖 |
| --- | --- | --- | --- | --- |
| P1 | 多租户骨架：配置、注册表与启动校验 | `lightrag-service/` | `tenants.yaml`、`TenantRegistry`、校验器、编排文件，以及 51 项单测 | 无 ✅ 已完成 |
| P2 | 多实例服务入口：路由、生命周期、限流与鉴权 | `lightrag-service/tenant/` | `server.py` 启动入口，`--print-routes` 输出两组前缀路由（各 33 条） | P1 ✅ 已完成 |
| P3 | 隔离验证脚本（替身验证 + 真实执行后置） | `lightrag-service/scripts/` | `verify_isolation.py` + `isolation/` 包，覆盖 6 类场景；`--self-check` 本机可跑 | P2 |
| P4 | Java RAG 模块：租户映射与 RagClient | `backend-java` | 数据表、映射服务、`RagClient`，以及对应单测 | 只依赖 P2 的接口约定 |
| P5 | Java RAG 接口：业务 API、SSE 流式输出与权限 | `backend-java` | `RagController`、Postman 用例 | P4 |

**执行约束**：本机只做编码与单测，不启动容器、不连接 PostgreSQL / Neo4j、不调用真实模型。因此 P3 交付的是
「验证工具 + 替身验证」，真实的跨租户隔离结论必须在有环境时执行一次 `--real` 才能得出；P4 / P5 的 Java 侧同理，
只交付代码与单测，联调放到有环境时进行。

P4 只依赖 LightRAG 的接口约定，可以和 P3 并行开发。按方案要求，**P3 一旦发现串数据，必须先回到 P2 修复**，
修复后才能把 P4 / P5 交付联调。

## 目标目录结构

```text
badminton-mes/
├── lightrag-service/
│   ├── LightRAG/                       # vendored v1.5.7，已删除原 .git
│   ├── config/tenants.yaml
│   ├── tenant/
│   │   ├── settings.py                 # ✅ 环境变量收口
│   │   ├── config.py                   # ✅ 加载 YAML，定义 TenantConfig
│   │   ├── validator.py                # ✅ 启动时的隔离校验
│   │   ├── factory.py                  # ✅ LLM / Embedding / LightRAG 构造
│   │   ├── registry.py                 # ✅ TenantContext / TenantRegistry
│   │   ├── check.py                    # ✅ 自检 CLI
│   │   ├── server.py                   # P2 启动入口
│   │   ├── app.py                      # P2 应用构建与路由注册
│   │   ├── lifespan.py                 # P2 统一生命周期
│   │   ├── llm_limiter.py              # P2 全局 Semaphore
│   │   └── auth.py                     # P2 内部 API Key 校验
│   ├── scripts/
│   │   ├── verify_isolation.py         # P3 入口（薄封装）
│   │   ├── isolation/
│   │   │   ├── clients.py              # HTTP / PG / Neo4j 三类协议与真实实现
│   │   │   ├── scenarios.py            # S1～S6 判定逻辑（不依赖具体数据源）
│   │   │   ├── runner.py               # CLI 编排与退出码
│   │   │   └── report.py               # JSON 证据报告
│   │   ├── fakes.py                    # 替身实现，供 --self-check 与单测共用
│   │   └── README.md                   # 两类用法与后置执行清单
│   ├── tests/                          # pytest 单测
│   ├── docker-compose.yml              # ✅ PostgreSQL + pgvector、Neo4j + APOC
│   ├── .env.example
│   └── requirements.txt
└── backend-java/src/main/java/com/badminton/mes/module/rag/
    ├── config/   RagProperties, RagClientConfig
    ├── client/   RagClient
    ├── dal/      RagTenantBinding 实体 + Repository
    ├── service/  RagTenantResolver, RagDocumentService, RagQueryService, RagGraphService
    └── controller/ RagDocumentController, RagQueryController, RagGraphController
```

---

## P1：多租户骨架——配置、注册表与启动校验

**目标**：在不启动 HTTP 服务的前提下，就能根据配置构造出所有租户的上下文对象，并且配置错误会被拦截。

### 任务

1. **引入 LightRAG**：fork 后固定使用 `v1.5.7` 这个 tag，以子目录形式放入仓库，用 `pip install -e` 安装；补充 `requirements.txt` 和 `.env.example`。
2. **`config/tenants.yaml`**：先配 2 个用户。

   ```yaml
   tenants:
     - user_id: user_1
       workspace: ws_001
     - user_id: user_2
       workspace: ws_002
   ```

3. **`tenant/config.py`**：把 YAML 读成 `TenantConfig(user_id, workspace)` 列表，并为每个租户推导出独立的 `input_dir/<workspace>` 和 `working_dir/<workspace>`。
4. **`tenant/registry.py`**：
   - 定义 `TenantContext(workspace, rag, doc_manager)`。
   - `TenantRegistry` 提供 `register / get / all`；`get` 遇到不存在的 user 时抛出明确的异常。
   - `build_rag(workspace)` 复用官方 server 构造 `LightRAG` 的参数（存储类型、模型函数），只把 `workspace` 替换为租户值。
5. **`tenant/validator.py`**（满足以下任一条件就抛异常，拒绝启动）：
   - `user_id` 或 `workspace` 重复，或 workspace 不符合命名规则（`^[A-Za-z0-9_]+$`）。
   - 各租户的 `input_dir`、`working_dir` 出现重叠或互为父子目录。
   - 设置了 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE`、`WORKSPACE` 等全局变量，导致实例级 workspace 被覆盖。**动手前先查看 v1.5.7 源码，确认实际的覆盖优先级。**
   - 租户列表为空。

### 单测（pytest）

- 合法配置能加载成功；重复的 user/workspace、非法名称、目录冲突、存在全局 workspace 环境变量时，分别抛出对应的异常。
- `TenantRegistry.get` 能命中已注册租户；查询不存在的租户时报错。

### 完成标准

`pytest tests/` 全部通过；用 mock 模型函数时，能为 2 个租户各构造出 `TenantContext`。

---

## P2：多实例服务入口——路由、生命周期、限流与鉴权

**目标**：交付 `python -m tenant.server` 启动入口。应用构建阶段按租户注册 `/tenant/{user_id}` 前缀路由，
`--print-routes` 能在不连数据库、不调模型的前提下打印完整的租户路由表；真实启动后 Swagger 显示
`/tenant/user_1/*` 与 `/tenant/user_2/*` 两组路由。

### 源码核查结论（v1.5.7，动手前已确认）

| 结论 | 证据位置 | 对实现的影响 |
| --- | --- | --- |
| 三个路由工厂签名固定 | `create_document_routes(rag, doc_manager, api_key=None)`、`create_query_routes(rag, api_key=None, top_k=60)`、`create_graph_routes(rag, api_key=None)` | 直接参数化复用，无需改官方文件 |
| 单租户路由数量：文档 18 + 查询 3 + 图谱 12 = 33 | `document_routes.py:4397`、`query_routes.py:367`、`graph_routes.py:161` 内的 `@router.*` | 「各组数量一致」是可断言的验收点 |
| `pipeline_status` 已按 workspace 隔离 | `lightrag.py:1608-1610` 在 `initialize_storages()` 内部调用 `initialize_pipeline_status(workspace=self.workspace)` | 不需要在入口层再包一层隔离 |
| 后台任务集合是**全应用共享**的 | `document_routes.py:2079-2092` `get_managed_background_tasks` 返回 `request.app.state.background_tasks` | 关闭时只能整体 drain 一次，不能按租户分开 drain；先 drain 再逐个 `finalize_storages()` |
| 鉴权白名单在模块导入时就固化 | `utils_api.py:242-254` 用 `global_args.whitelist_paths` 编译 `whitelist_patterns`；默认 `/health,/api/*` | `/tenant/...` 不匹配默认白名单，因此天然要求凭证；**禁止**把 `WHITELIST_PATHS` 设成 `/*` |
| `global_args` 是首次访问即初始化的代理 | `config.py:1059-1113` `_GlobalArgsProxy` → `get_config()` → `initialize_config()` → `parse_args()` | **导入路由模块会触发 argparse**。入口必须先把 `sys.argv` 换成受控参数并 `initialize_config(force=True)`，再导入 `lightrag.api.routers.*`，否则会被 argparse 抢走命令行并可能直接退出 |

### 任务

1. **`tenant/server.py`（启动入口）**
   - 解析自己的参数（`--config`、`--host`、`--port`、`--print-routes`、`--docs/--no-docs`）。
   - 在导入任何 `lightrag.api.*` 之前，构造受控 argv 并 `initialize_config(force=True)`，把
     `working_dir` / `input_dir` / `kv_storage` / `vector_storage` / `graph_storage` /
     `doc_status_storage` / `top_k` / `max_pending_documents` 一次设好。
   - 复用 P1 的 `load_tenants()` + `validate_tenants()`：校验失败直接退出，不进入路由注册。
   - `--print-routes` 只构建 app 并打印路由表，不启动 uvicorn、不执行 lifespan。
2. **`tenant/app.py`（应用构建）**
   - 用 `assemble_registry()` 装配各租户上下文，循环 `include_router(..., prefix=f"/tenant/{user_id}")`。
   - 三个工厂的 `api_key` 统一传内部密钥；tag 前置 `user_id`，便于 Swagger 分组。
   - 构建阶段只注册路由，绝不响应后再改路由表。
3. **`tenant/lifespan.py`（统一生命周期）**
   - 启动：创建唯一的 `app.state.background_tasks` 集合 → 逐个 `initialize_storages()`
     → 逐个 `check_and_migrate_data()`；任一失败即中止启动并按逆序 `finalize_storages()` 回滚。
   - 关闭：`drain_reserved_background_tasks(app.state.background_tasks)`（带超时）
     → 逐个 `finalize_storages()` → **最后只调用一次** `finalize_share_data()`。
4. **`tenant/llm_limiter.py`（全局模型并发上限）**
   - `asyncio.Semaphore(LR_LLM_CONCURRENCY)` 包住 `llm_model_func` 与 `embedding_func`，
     所有租户共用同一个信号量，在模型调用层限流而不是在上传接口上限流。
   - 未设置并发上限时退化为直通包装，不改变语义。
5. **`tenant/auth.py`（内部鉴权）**
   - 内部密钥走 `LR_INTERNAL_API_KEY`，作为 `api_key` 传给三个工厂（对应 `X-API-Key`）。
   - 服务默认只监听 `127.0.0.1`；非回环地址且未配密钥时**拒绝启动**。
   - `/docs` 由开关控制，默认关闭。
6. **容量控制**：`LR_MAX_PENDING_DOCUMENTS` 逐个实例生效，`--print-routes` 时一并打印各租户容量。
7. **文档**：把启动、`--print-routes`、内部密钥与端口约定写进 `lightrag-service/README.md`。

### 单测（pytest，仍不连数据库、不调模型）

- **路由注册**：用替身 `rag` / `doc_manager` 构建 app，断言每个租户前缀下都有 33 条路由，
  且各租户路由数量一致；断言不存在未加前缀的 `/documents/upload`、`/query`、`/graphs`。
- **鉴权**：`TestClient` 不带 Key / 带错误 Key 时返回 401 或 403；带正确 Key 时**不是** 401/403。
- **生命周期**：替身实例记录调用序列，断言每个实例 `initialize_storages` 恰好一次、
  `finalize_storages` 恰好一次、`finalize_share_data` 恰好一次，且 finalize 发生在 drain 之后。
- **限流**：并发发起 N+M 次包装后的调用，断言同时在执行的峰值不超过上限。
- **启动校验**：非法租户配置（重复 workspace、设置 `POSTGRES_WORKSPACE` 等）时，构建阶段即失败。

### 完成标准

`python -m tenant.server --print-routes` 打印出 2 组、各 33 条带前缀路由；
`pytest` 全绿；真实启动与跨租户隔离验证留给 P3。

---

## P3：隔离验证脚本（编码 + 替身验证，真实执行后置）

**目标**：交付一个**结构上可被本机验证**的隔离验证工具。脚本的真实网络/数据库调用与场景判定逻辑严格分离，
因此在本机没有 PostgreSQL、Neo4j 和模型的情况下，也能用替身跑完全部场景分支并断言判定正确。

> **诚实说明**：本阶段交付的是「验证能力」，不是「已验证」。真实的跨租户数据隔离结论，必须在有完整环境时
> 实际执行一次才能得出；在那之前，课设报告只能写"已实现隔离校验与验证工具"，不能写"已验证隔离成立"。

### 设计要点：把 I/O 与判定分开

这是本阶段唯一的关键决策。如果脚本直接 `httpx.get(...)` 再 `assert`，那它在本机就是一段无法验证的代码；
拆成三层后，判定逻辑可以用替身完整覆盖：

| 层 | 职责 | 本机能否验证 |
| --- | --- | --- |
| `clients.py` | 三个协议：HTTP 客户端、PostgreSQL 巡检、Neo4j 巡检；真实实现与替身实现各一套 | 接口与替身可测 |
| `scenarios.py` | S1～S6 的判定逻辑，只依赖协议，不关心数据从哪来 | ✅ 完全可测 |
| `runner.py` | CLI 编排、并发、报告输出、退出码 | ✅ 可测 |

### 场景

| 编号 | 场景 | 判定方式 |
| --- | --- | --- |
| S1 | A 上传含唯一口令的文档，B 查询该口令 | B 的 `naive`/`mix` 回答与检索上下文都不出现该口令；A 能查到 |
| S2 | A、B 上传同名但内容不同的文件 | 两边文档列表各自独立，内容未被覆盖，各自只查到自己那份 |
| S3 | A、B 并发上传 + 并发删除（`asyncio.gather`） | 双方 doc_id 与任务状态不互相出现，最终状态正确 |
| S4 | 重启后恢复（脚本自己管理服务子进程，`--restart`） | 重启后文档数、状态、查询结果与重启前一致 |
| S5 | 直连数据库核对 | PG 各 `lightrag_*` 表按 workspace 分组计数与各租户匹配；**含 `LIGHTRAG_LLM_CACHE`**——缓存串了等于答案串了 |
| S6 | Neo4j Label 隔离 | 各 workspace 的实体/关系节点集合互不交叉，且不落到 `base` 全局 Label |

### 任务

1. **`scripts/isolation/`（新建包）**
   - `clients.py`：`RagHttpClient` / `PgInspector` / `Neo4jInspector` 三个 `Protocol`；
     真实实现（`httpx` / `psycopg` / `neo4j`）**延迟导入**，缺依赖时给出可读报错而不是崩栈。
   - `scenarios.py`：S1～S6 各一个异步函数，入参是协议对象，返回 `ScenarioResult(id, status, evidence, detail)`；
     `status` 取 `pass` / `fail` / `blocked`（环境缺失记 blocked，不伪装成 pass）。
   - `runner.py`：参数解析、按场景挑选执行、并发跑 S3、汇总输出、退出码（全 pass 才 0）。
   - `report.py`：生成 JSON 证据文件（含每场景的实测输入、观察到的事实、判定依据），供课设报告直接引用。
2. **`scripts/verify_isolation.py`**：薄封装，只调用 `runner.main()`，保持文档里的命令不变。
3. **`scripts/fakes.py`**：替身实现（内存版 HTTP 后端、假 PG 表、假 Neo4j 图），**同时**供
   `--self-check` 模式与 pytest 使用——同一份替身，避免"测试用的替身和脚本用的不是一回事"。
4. **`--self-check` 模式**：不需要任何外部服务，用替身把 S1～S6 全跑一遍，用于演示流程与自检编排是否正确。
5. **`scripts/README.md`**：写清两类用法——本机 `--self-check`，有环境时 `--real`。
6. **后置执行清单**：把"有环境后要跑的完整命令、需要准备的配置、预期输出、失败时怎么排查"写成一节，
   交给你在环境就绪时照做，避免到时候还要重新设计方案。

### 单测（pytest，本机全部可跑）

- **判定正确性**：每个场景分别喂"正常数据"与"串数据"，断言前者 `pass`、后者 `fail`——
  这是最关键的一类测试：**证明脚本抓得住串数据**，而不是永远返回 pass。
- **blocked 语义**：环境缺失（连接失败、文档索引超时）时记为 `blocked` 而非 `pass`。
- **超时与轮询**：索引等待用可注入的时钟/轮询替身，验证超时后返回 `blocked`，不写死 `sleep`。
- **runner**：全 pass 退出码 0；任一 fail 退出码非 0；`--only S1,S3` 只跑指定场景；JSON 报告字段完整。
- **口令生成**：每轮随机且唯一，两次运行不重复。
- **真实客户端的降级**：缺 `psycopg` / `neo4j` 时给出可读提示，不抛原始 ImportError。

### 完成标准

- `pytest lightrag-service/tests` 全绿（含新增的脚本单测）；
- `python scripts/verify_isolation.py --self-check` 在本机跑完 S1～S6 并输出结构化结论；
- `--real` 路径的代码完整、参数齐备，但因缺环境**不要求本机执行**——这一点在报告里明确标注。

---

## P4：Java RAG 模块——租户映射与 RagClient

**目标**：在 `module/rag` 下完成底层能力：确定当前登录用户对应哪个 tenant，以及调用 LightRAG 的客户端。暂时不对外暴露接口。

### 任务

1. **Flyway 迁移** `V2026110701__rag_tenant_binding.sql`（MySQL）：

   | 字段 | 说明 |
   | --- | --- |
   | `id` | 主键 |
   | `user_id` | 业务系统用户 ID，唯一 |
   | `tenant_id` | 与 `tenants.yaml` 中的 `user_id` 对应，唯一 |
   | `enabled` | 是否启用 |
   | `created_at` / `updated_at` | 审计字段 |

   演示账号的绑定数据放到 `dev-migration`。文档本身的元数据以 LightRAG 为准，**暂时不建表**（YAGNI，只在确有需要时再加）。
2. **`RagProperties`**（`application.yml` 中的 `rag.*`）：`base-url`、`internal-api-key`（从环境变量读取）、连接/读取超时、上传大小上限。
3. **`RagTenantResolver`**：从 `SecurityContextHolder` 取出 `LoginUser`，查询绑定得到 `tenantId`；未绑定或已禁用时抛出业务异常。**整个流程不接收前端传入的 user_id。**
4. **`RagClient`**（`RestClient` 负责普通请求，`WebClient` 只用于 SSE；如果项目中没有 webflux 依赖，SSE 改用 `RestClient` 的流式读取或 `HttpClient`，以减少依赖）：
   - 统一拼接 `/tenant/{tenantId}` 前缀，统一注入 `X-API-Key`。
   - 方法：`uploadDocument`、`listDocuments`、`deleteDocument`、`reindex`、`query`、`queryStream`、`getGraphLabels`、`getGraph`。
   - 统一做错误转换：LightRAG 的 4xx/5xx 和超时转成项目的业务异常，不把内部地址透传给前端。
   - `tenantId` 只能由 Resolver 传入，Client 内部再按白名单正则校验一次，防止路径注入。

### 单测（JUnit + Mockito / `MockRestServiceServer`）

- Resolver：已绑定时返回 tenant；未绑定、已禁用、未登录时分别抛出对应异常。
- RagClient：请求路径带正确前缀、Header 带上 Key；非法 tenantId 被拒绝；远端返回 500 或超时时转换为业务异常。

### 完成标准

`./gradlew test` 通过，新增类的单测覆盖率达到 85% 以上。

---

## P5：Java RAG 接口——业务 API、SSE 与权限

**目标**：对外提供 `/api/rag/**` 接口，前端只需要登录态，不传任何租户标识。

### 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/rag/documents` | multipart 上传，转发给 LightRAG |
| GET | `/api/rag/documents` | 文档列表，包含异步处理状态 |
| DELETE | `/api/rag/documents/{docId}` | 删除文档 |
| POST | `/api/rag/documents/reindex` | 重新索引 |
| POST | `/api/rag/query` | 同步问答，`mode` 取值为 `naive/local/global/hybrid/mix` |
| POST | `/api/rag/query/stream` | 流式问答，`SseEmitter` 转发 |
| GET | `/api/rag/graph/labels` | 图谱标签 |
| GET | `/api/rag/graph` | 按标签查询子图 |

### 任务

1. Controller 只负责参数校验和调用 Service；Service 负责 `Resolver → RagClient` 的调用链。
2. **权限**：接入现有的 `AuthInterceptor`，要求登录；如有需要，用 `@RequiresRoles` 限定可用角色；`mode` 用枚举校验，并限制上传文件的类型和大小。
3. **SSE**：设置 `SseEmitter` 超时；客户端断开时取消上游请求；上游出错时向前端发送 error 事件，然后正常结束。
4. **越权防护**：`docId` 只在当前租户前缀下操作。由于路由前缀本身就实现了隔离，即使拿到他人的 docId 也无法跨租户删除；这一点需要在 P3 的 S1/S3 中得到验证。
5. 在 `postman_collection.json` 中追加 RAG 目录：两个账号分别登录，再依次执行上传、列表、问答、图谱等请求。

### 单测

- Controller（`@WebMvcTest`）：未登录返回 401；非法 `mode` 和超大文件被拒绝；正常请求调用了对应的 Service。
- Service：两个不同的 LoginUser 解析得到不同的 tenant，并调用 Client 中对应的前缀。

### 完成标准

单测通过；在有完整环境时用 Postman 以两个账号调用，各自只能看到自己的数据。

---

## 风险与待确认事项

| 风险 | 等级 | 处理方式 |
| --- | --- | --- |
| 全局 `*_WORKSPACE` 环境变量覆盖了实例配置 | 🟡 | ✅ P1 已在校验器中拦截，三项变量命中即拒绝启动 |
| Neo4j 的 Label 隔离只是应用层隔离 | 🟡 | Neo4j 不对外暴露，只允许 LightRAG 服务连接 |
| 后台任务集合是全应用共享的（已核实） | 🟡 | P2 关闭时整体 drain 一次再逐个 finalize，不做按租户拆分 |
| 导入路由模块会触发 argparse（已核实） | 🔴 | P2 入口必须在导入 `lightrag.api.*` 之前固定 `sys.argv` 并 `initialize_config(force=True)` |
| `WHITELIST_PATHS` 被误设为 `/*` 会让租户路由免鉴权 | 🔴 | P2 启动时校验白名单，包含 `/*` 直接拒绝启动 |
| 多租户同时索引，耗尽模型配额 | 🟡 | P2 用全局 Semaphore 加 `MAX_PENDING_DOCUMENTS` 限制 |
| Spring Boot 4 下 SSE 转发的依赖选型 | 🟢 | 在 P4 中优先使用不新增依赖的方案 |
| **跨租户数据隔离尚未经过真实执行验证** | 🔴 | P3 只交付脚本与替身验证；有环境后必须实际跑一次 `--real`，在此之前**不得声称隔离已验证** |
| 模型供应商未定，P1/P2/P3 均未调用真实模型 | 🟡 | 填 `LR_LLM_MODEL` / `LR_EMBEDDING_MODEL` / `LR_EMBEDDING_DIM` 即可，代码已按 OpenAI 兼容接口就绪 |
| `WHITELIST_PATHS` 设为 `/tenant/*` 同样会让租户路由免鉴权 | 🟡 | P2 只拦全通配；建议后续追加前缀拦截（未纳入 P2 验收） |
| 进程 CWD 下的 `.env` 是启动校验盲区 | 🟡 | 标准启动目录（`lightrag-service`）不受影响；后续可在入口固定 `.env` 查找路径 |

> v1.5.7 的 `pipeline_status` 已确认按 workspace 隔离（`initialize_storages()` 内部按 `self.workspace` 初始化），
> 原 🔴 风险降级为已解决。
