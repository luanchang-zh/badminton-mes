# 目标

为 LightRAG 多用户知识库建设的第一阶段（P1）打好底座：在本仓库内固定 LightRAG v1.5.7 源码，并交付一个**不启动 HTTP 服务、不连接数据库**即可自检的多租户配置与实例装配骨架，供 P2 的多实例路由直接复用。

- 引入 LightRAG v1.5.7 源码到 `lightrag-service/LightRAG/`，删除其内部 `.git`，只保留运行所需源码与依赖声明，由本仓库统一修改和管理。
- 用声明式配置描述固定用户与 Workspace 的映射，并在启动前完成隔离校验，配置错误直接拒绝。
- 提供统一实例注册表，按租户产出 `LightRAG` 实例与 `DocumentManager`，两者都绑定该租户的 workspace 与独立目录。
- 提供本地自检入口：只读取配置与装配结果，不碰数据库、不调用模型，供后续联调前快速确认隔离前提是否成立。
- 提供覆盖全部依赖组件的 Docker 编排示例（PostgreSQL + pgvector、Neo4j + APOC），让后续联调不必再逐个搭建环境。

# 范围

## 目录结构

### 新建

- `lightrag-service/LightRAG/` — vendored 的 LightRAG v1.5.7 源码，删除内部 `.git`，保留 `lightrag/` 包、`pyproject.toml`、`LICENSE` 等 pip 安装与运行必需文件；不放上游前端源码、文档站与 CI 配置。
- `lightrag-service/tenant/settings.py` — 从环境变量读取存储类名、模型接入参数、基目录与容量上限，统一收口配置读取。
- `lightrag-service/tenant/config.py` — 解析 `tenants.yaml` 为 `TenantConfig` 列表，并推导每个租户的 `input_dir` / `working_dir`。
- `lightrag-service/tenant/validator.py` — 启动前隔离校验：重复标识、workspace 命名、目录重叠、禁止的全局覆盖变量。
- `lightrag-service/tenant/factory.py` — 构造 LLM / Embedding 调用函数与 `LightRAG` 实例，延迟导入 LightRAG 以便替换。
- `lightrag-service/tenant/registry.py` — `TenantContext` 与 `TenantRegistry`，负责装配、查找与统一释放入口。
- `lightrag-service/tenant/check.py` — 自检 CLI，打印各租户的 workspace 与目录，失败时给出可读原因。
- `lightrag-service/tenant/__init__.py` — 导出上述公开类型。
- `lightrag-service/config/tenants.yaml` — 默认 2 个演示租户的 user_id → workspace 映射。
- `lightrag-service/tests/` — pytest 单测，覆盖配置解析、校验与注册表装配，不依赖数据库与外部模型。
- `lightrag-service/requirements.txt` — 运行期与测试期依赖声明。
- `lightrag-service/docker-compose.yml` — 依赖组件编排：PostgreSQL（pgvector 镜像，自动创建 `vector` 扩展所需权限）与 Neo4j（启用 APOC，供图谱子图查询使用），含健康检查、命名卷与回环端口绑定。
- `lightrag-service/.env.example` — 模型接入、存储类型、目录、容量上限与两个组件镜像变量（便于换国内镜像源）的样例。
- `lightrag-service/README.md` — 源码引入方式、安装命令、本机自检命令与依赖组件启动命令。

### 修改

- `.gitignore` — 追加 `lightrag-service` 的虚拟环境、Python 缓存与本地数据目录忽略规则，避免 vendored 源码产生的中间文件混入版本库。

### 删除

无

### 明确不建

- 不建 HTTP 服务入口与租户路由前缀 — 属于 P2，P1 不注册任何路由。
- 不在编排文件里放 LightRAG 应用容器 — 多租户服务入口与镜像属于 P2，P1 的编排只覆盖被它依赖的组件。
- 不建隔离验证脚本与跨库核对 — 属于 P3，本阶段不连接 PostgreSQL / Neo4j。
- 不建 Java 侧 RAG 模块与对外接口 — 属于 P4、P5。
- 不建动态实例池、LRU、自动扩缩容 — 技术方案已明确不实现。
- 不建租户元数据表与数据库迁移 — P1 不接触数据库。

## Source coverage

覆盖边界：用户指定的技术方案（`badminton-mes.wiki/LightRAG_课设技术方案.md`）与其派生的开发规划（`backend-java/docs/LightRAG多租户知识库开发规划.md`）中，**第 P1 阶段**（多租户骨架）范围内的条目及必要依赖；P2～P5 内容按用户"只做这部分"的既有划分记为非目标。

| 来源条目与位置 | 读取状态 | 需要保留的内容 | Spec 位置 | 验收 ID | 覆盖状态 | 理由或替代关系 |
| --- | --- | --- | --- | --- | --- | --- |
| S1：技术方案 附录·优化 1「统一实例注册表」 | complete | 用注册表集中管理每个用户的 `LightRAG` 与 `DocumentManager`，启动时创建固定用户实例 | `specs/rag-multitenant-foundation/spec.md` R3 | A5、A6 | covered | 本阶段实现 |
| S2：技术方案 附录·优化 2「声明式用户配置」 | complete | 独立 YAML 描述 user_id → workspace，新增用户只改配置 | R2 | A1 | covered | 本阶段实现 |
| S3：技术方案 附录·优化 4 校验点 1、2 | complete | 校验 user_id / workspace 不重复，检查上传与工作目录按 workspace 独立 | R4 | A2、A3 | covered | 本阶段实现 |
| S4：技术方案 附录·优化 4 校验点 3 | complete | 核对 `NEO4J_WORKSPACE`、`POSTGRES_WORKSPACE` 等全局配置是否覆盖实例级 workspace | R4 | A4 | covered | 本阶段实现，源码已确认存在覆盖 |
| S5：技术方案 4.1 改造原则、4.2 实现流程 | complete | 复用官方路由工厂与 `DocumentManager` | — | — | background | 属于 P2 路由注册；P1 只固定 `DocumentManager` 的 workspace 入参 |
| S6：技术方案 附录·优化 3「统一生命周期」、优化 5「并发与资源限制」 | complete | lifespan 统一启停、全局 Semaphore 与容量上限 | — | — | non-goal | 依赖 P2 的服务入口，P1 不实现 |
| S7：技术方案 六「必须验证的关键问题」 | complete | 隔离、生命周期与检索一致性验证 | — | — | non-goal | 属于 P3 隔离验证 |
| S8：开发规划 P1 任务 1「引入 LightRAG」 | complete | 固定使用经测试的 v1.5.7 版本 | R1 | A1 | covered | 本阶段实现 |
| S9：开发规划 P1 任务 2「tenants.yaml 先配 2 个用户」 | complete | 默认 2 个演示租户 | R2 | A1 | covered | 本阶段实现 |
| S10：开发规划 P1 任务 3「目录推导」 | complete | 每租户独立的 `input_dir`、`working_dir` | R2 | A1、A5 | covered | 本阶段实现 |
| S11：开发规划 P1 任务 4「registry / build_rag」 | complete | 注册表接口与实例构造，查不到租户要报错 | R3 | A5、A6 | covered | 本阶段实现 |
| S12：开发规划 P1 完成标准 | complete | pytest 全部通过，2 个租户各构造出上下文 | R5 | A7 | covered | 本阶段实现 |
| S13：用户补充约束「完整拉取源码删 .git」「模型配置后定」「只跑单测」 | complete | 源码引入方式、模型配置延后、验收只用单测 | R1、R5 | A1、A7 | covered | 本阶段实现 |
| S14：用户补充「本机也要先写好 Docker 编排示例，覆盖所有用到的组件」 | complete | 交付覆盖全部依赖组件的编排文件 | R6 | A8、A9 | covered | 本阶段实现，只做静态校验 |

# 非目标

- 不实现 P2 的多实例 HTTP 服务：租户路由前缀、Swagger 分组、lifespan 启停、内部 API Key、全局 Semaphore。
- 不实现 P3 的隔离验证：交叉查询、同名文件、并发上传删除、重启恢复、跨 PG / Neo4j 核对。
- 不实现 P4、P5 的 Java 侧租户映射、`RagClient`、`/api/rag/**` 接口与 SSE。
- 不确定具体模型供应商，不接入真实 LLM / Embedding，不执行向量化与图谱构建。
- 不改动 LightRAG 的文档解析、实体关系抽取、图谱构建与检索算法。
- 不启动容器、不校验容器间真实连通性，不执行建表、向量化与图谱写入；编排文件只做静态解析校验。
- 不做本机集成测试与真实数据库联调；不回归测试项目原有的 Java、前端与小程序代码。

# 约束与不变量

- 验收只在本机执行单测与自检 CLI：不启动服务、不连接 PostgreSQL / Neo4j、不发起模型 API 调用。
- 编排文件不改动根目录既有的 `docker-compose.yml`（MySQL / Redis / 后端 / 前端），RAG 组件独立编排、独立启动。
- 只测试本次二开涉及的 `tenant` 模块；不运行 LightRAG 上游测试，也不测试既有 MES 代码。
- workspace 是唯一隔离键：传空等于全局命名空间，因此必须禁止空值、路径分隔符与非白名单字符。
- 每租户目录统一推导为 `<base_dir>/<workspace>`，与官方 `DocumentManager` 的目录规则保持一致。
- 编排文件中不得出现 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE`、`WORKSPACE`，否则容器内会覆盖实例级 workspace。
- 代码规范：单文件不超过 400 行，单函数不超过 50 行，公开函数带函数级注释。

# 关键决定

- **源码引入方式**：整份 v1.5.7 源码放入 `lightrag-service/LightRAG/`，删除内部 `.git`，以 `pip install -e` 使用；后续补丁由本仓库统一管理。
- **隔离边界**：`LightRAG(workspace=...)` 与 `DocumentManager(input_dir, workspace=...)` 均显式传参，不依赖环境变量推导。
- **禁止全局覆盖变量**：`POSTGRES_WORKSPACE` 在 `postgres_impl.initialize()` 中优先级高于实例 workspace，`NEO4J_WORKSPACE` 在 `neo4j_impl.__init__` 中直接覆盖入参，二者必须拒绝；`WORKSPACE` 不覆盖显式传参，但为避免双来源配置一并拒绝。
- **模型配置延后**：P1 只定义环境变量契约（OpenAI 兼容），`settings` 允许缺省；自检 CLI 在模型未配置时输出警告但不失败，真实实例构建留到后续阶段。
- **编排独立**：RAG 组件用 `lightrag-service/docker-compose.yml` 单独编排，依赖镜像通过 `LR_PG_IMAGE` / `LR_NEO4J_IMAGE` 变量注入，默认走项目已有的国内镜像前缀，便于受限网络下拉取。

# 验证期望

- 本机执行 `python -m tenant.check` 与 `pytest lightrag-service/tests -q`，两者都不需要数据库或模型密钥。
- 编排文件用 `docker compose config` 做静态校验，不需要拉取镜像或启动容器。
- 单测对 `LightRAG` 使用替身注入，覆盖装配与校验分支，不引入网络与存储副作用。

# 验收示例

1. 在默认 `config/tenants.yaml`（2 个租户）下执行自检 CLI：退出码为 0，输出 2 个租户各自的 user_id、workspace、`<input_dir>/<workspace>` 与 `<working_dir>/<workspace>`，且两个租户的目录路径互不相同。
2. 把第二个租户的 workspace 改成与第一个相同后执行自检：退出码非 0，错误信息包含重复的 workspace 名称，且不打印装配结果。
3. 把某个租户的 workspace 置空或写成 `ws/001`：自检失败并指出该 workspace 非法，退出码非 0。
4. 在环境中设置 `POSTGRES_WORKSPACE` 后执行自检：失败并明确指出被禁止的环境变量；`NEO4J_WORKSPACE` 与 `WORKSPACE` 同样处理。
5. 装配 2 个租户时：每个 `TenantContext` 的 `rag.workspace` 等于其配置的 workspace，`doc_manager.input_dir` 等于 `<input_dir>/<workspace>`，实例与目录都不共用。
6. 用未注册的 user_id 调用 `TenantRegistry.get`：抛出 `UnknownTenantError`，错误信息包含该 user_id，且不返回任何实例。
7. 在未安装数据库、未配置模型密钥的机器上执行 `pytest lightrag-service/tests`：全部通过，过程中不产生数据库连接与模型 API 调用。
8. 执行 `docker compose -f lightrag-service/docker-compose.yml config`：解析成功，渲染结果包含 PostgreSQL 与 Neo4j 两个服务，且两者的环境变量中都不出现 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE`。
9. 检查渲染结果：PostgreSQL 与 Neo4j 的端口都只绑定 `127.0.0.1`，各自声明了健康检查与持久化命名卷。
