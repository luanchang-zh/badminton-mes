# LightRAG 多租户知识库开发规划

> 依据：`badminton-mes.wiki/LightRAG_课设技术方案.md`（第七节第 2～4 步 + 附录五项优化）。
> 范围：LightRAG 静态多实例二开、隔离验证脚本、Java 业务服务接入。前端与课设演示不在本规划内。
> 测试约定：只使用脚本和单元测试，不引入专业测试平台；本机不要求做集成测试，先完成开发，联调放到有完整环境时再做。

## 总览

| 阶段 | 名称 | 所在位置 | 主要产出 | 依赖 |
| --- | --- | --- | --- | --- |
| P1 | 多租户骨架：配置、注册表与启动校验 | `lightrag-service/` | `tenants.yaml`、`TenantRegistry`、校验器，以及对应单测 | 无 |
| P2 | 多实例服务入口：路由、生命周期、限流与鉴权 | `lightrag-service/` | `multi_tenant_server.py`，Swagger 中有两组前缀路由 | P1 |
| P3 | 隔离验证脚本 | `lightrag-service/scripts/` | `verify_isolation.py`，覆盖 5 类场景 | P2 |
| P4 | Java RAG 模块：租户映射与 RagClient | `backend-java` | 数据表、映射服务、`RagClient`，以及对应单测 | 只依赖 P2 的接口约定 |
| P5 | Java RAG 接口：业务 API、SSE 流式输出与权限 | `backend-java` | `RagController`、Postman 用例 | P4 |

P4 只依赖 LightRAG 的接口约定，可以和 P3 并行开发。按方案要求，**P3 一旦发现串数据，必须先回到 P2 修复**，修复后才能把 P4/P5 交付联调。

## 目标目录结构

```text
badminton-mes/
├── lightrag-service/
│   ├── LightRAG/                       # fork，固定在 v1.5.7，尽量不改动
│   ├── config/tenants.yaml
│   ├── tenant/
│   │   ├── config.py                   # 加载 YAML，定义 TenantConfig
│   │   ├── registry.py                 # TenantContext / TenantRegistry
│   │   ├── validator.py                # 启动时的隔离校验
│   │   ├── llm_limiter.py              # 全局 Semaphore 包装 LLM 调用
│   │   └── auth.py                     # 内部 API Key 校验
│   ├── multi_tenant_server.py          # 新的启动入口
│   ├── scripts/verify_isolation.py
│   ├── tests/                          # pytest 单测
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

**目标**：`python multi_tenant_server.py` 能够启动，Swagger（`/docs`）中显示 `/tenant/user_1/*` 和 `/tenant/user_2/*` 两组路由。

### 任务

1. **路由注册**（只在应用构建阶段进行）：

   ```python
   for user_id, ctx in registry.all():
       prefix = f"/tenant/{user_id}"
       app.include_router(create_document_routes(ctx.rag, ctx.doc_manager, api_key), prefix=prefix)
       app.include_router(create_query_routes(ctx.rag, api_key, args.top_k), prefix=prefix)
       app.include_router(create_graph_routes(ctx.rag, api_key), prefix=prefix)
   ```

   需要先阅读 v1.5.7 中 `lightrag_server.py` 的 `create_app`，确认路由工厂的签名和 tag 写法；可以给 tag 加上 user 前缀，方便在 Swagger 中区分。
2. **lifespan 统一生命周期**：
   - 启动：初始化共享状态（只做一次），然后逐个调用 `initialize_storages()`（如果该版本需要，再调用 `initialize_pipeline_status`）；任何一个失败都中止启动。
   - 关闭：停止接收新任务，等待各租户的后台索引任务结束（设置超时，超时后安全取消），再逐个调用 `finalize_storages()`，最后只清理一次共享状态。
   - **重点核查**：官方 pipeline status 和后台任务的存放位置是否按 workspace 区分；如果是全局唯一的，需要在入口层按租户做隔离。
3. **`tenant/llm_limiter.py`**：用 `asyncio.Semaphore(LLM_GLOBAL_CONCURRENCY)` 包装所有租户共用的 `llm_model_func` / `embedding_func`。在模型调用层限流，而不是在上传接口上限流。
4. **容量控制**：通过 `.env` 配置 `MAX_PENDING_DOCUMENTS`，确认每个实例各自生效。
5. **内部鉴权 `tenant/auth.py`**：所有租户路由都校验 `X-API-Key == LIGHTRAG_INTERNAL_API_KEY`，优先复用官方的 `api_key` 参数；服务只监听内网地址或 `127.0.0.1`；`/docs` 是否开放由开关控制。
6. 将启动命令写入 `lightrag-service/README.md`。

### 单测

- 用 mock 的 rag 构建 app，断言 `app.routes` 中两组前缀路由都存在且数量一致。
- 不带 Key 或 Key 错误时返回 401/403，Key 正确时放行（使用 FastAPI `TestClient`）。
- 并发调用超过上限时，用 Semaphore 包装后的函数同时执行的数量不超过设定值。

### 完成标准

服务能启动，Swagger 可以看到两组路由；单测全部通过。

---

## P3：隔离验证脚本

**目标**：`scripts/verify_isolation.py` 一条命令跑完 5 类场景，并输出 PASS/FAIL 汇总。它是普通 Python 脚本（使用 `httpx`、`psycopg`、`neo4j` 驱动），不依赖测试框架。

### 场景

| 编号 | 场景 | 判定方式 |
| --- | --- | --- |
| S1 | A 上传带唯一口令的文档，B 查询这个口令 | B 的 `naive`/`mix` 回答和检索上下文中不出现该口令；A 能查到 |
| S2 | A、B 上传同名文件，但内容不同 | 两边的文档列表各自独立，内容未被覆盖，各自都能查到自己的口令 |
| S3 | A、B 同时上传和删除（`asyncio.gather`） | 双方的任务状态和文档 ID 互不出现在对方的列表中，最终状态正确 |
| S4 | 重启后恢复（脚本加 `--phase before/after`，中间手动重启服务） | 重启后双方的文档数量、状态、查询结果与重启前一致 |
| S5 | 直连数据库核对 | PG 中各 `lightrag_*` 表按 `workspace` 分组，计数与各租户匹配；Neo4j 中各 workspace 的 Label 节点互不交叉 |

### 要求

- 每次运行生成随机口令，避免历史数据干扰；脚本结束时清理本次上传的文档（可加 `--keep` 保留）。
- 等待索引完成用轮询文档状态的方式实现，并设置超时，不要固定 sleep。
- 连接信息从 `.env` 读取，不硬编码。

### 完成标准

在有完整环境时 S1～S5 全部 PASS；本机阶段只要求脚本写完，并能对 mock 环境或空库正常执行。**出现 FAIL 时先回到 P2 修复。**

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
| v1.5.7 的 pipeline status 或后台任务是全局单例，多实例共用会导致状态串扰 | 🔴 | 在 P2 中先读源码确认；如果确实如此，就在入口层按 workspace 隔离，P3 的 S3 专门验证 |
| 全局 `*_WORKSPACE` 环境变量覆盖了实例配置 | 🟡 | 在 P1 的校验器中拦截 |
| Neo4j 的 Label 隔离只是应用层隔离 | 🟡 | Neo4j 不对外暴露，只允许 LightRAG 服务连接 |
| 多租户同时索引，耗尽模型配额 | 🟡 | 在 P2 中用全局 Semaphore 加 `MAX_PENDING_DOCUMENTS` 限制 |
| Spring Boot 4 下 SSE 转发的依赖选型 | 🟢 | 在 P4 中优先使用不新增依赖的方案 |
