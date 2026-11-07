---
generated_from_state_version: 14
---

# 验证

## 当前结果

- 结果: **已归档**
- 验证情况: **已完成检查，验证结果已确认**
- 目标周期: 1
- 迭代: 2
- 验证器尝试次数: 1
- 完成时间: 2026-11-07T14:16:11.801Z
- 摘要: 第二轮独立验收：9 项验收全部由我本人读文件 + 跑命令复现通过。核心复核点是上一轮的 tenant/__init__.py 缺陷——现已确认为真正的包入口（40 行，md5 4fbc5edc359c4999fc017c40ac74680b，与 registry.py 的 faf4e717c24a8c105dc9bbec37c9cb86 不同，不再字节相同），声明了 14 个符号的 __all__，且 tenant.UnknownTenantError is tenant.registry.UnknownTenantError 等 6 组重导出对象同一性全部为 True，用 `except tenant.UnknownTenantError` 能捕获 TenantRegistry().get('missing_user_xyz') 抛出的异常；全仓只有一处 UnknownTenantError 类定义，没有为过验收而复制第二套类型，也没有删除任何原有测试用例（47 -> 51，新增的 4 项全部在 tests/test_package_exports.py，原 6 个测试文件的用例数 6/9/4/6/17/5 与上一轮完全一致）。回归方面：registry.py 的 md5 与上一轮记录值完全相同，validator.py 仍拒绝 POSTGRES_WORKSPACE/NEO4J_WORKSPACE/WORKSPACE 三类变量且 registry.get 仍无回退分支，config/settings/factory/check 的核心逻辑经阅读与实跑确认未削弱。范围方面：lightrag-service 下除 vendored 源码外无任何 fastapi/APIRouter/lifespan/Semaphore/internal API Key 痕迹，未连接数据库、未调用模型，LightRAG 目录无 .git 且版本为 1.5.7，仓库根 docker-compose.yml 与 HEAD 一致（git diff 为空），根目录仅 .gitignore 被修改（brief 授权范围内）。record-outcome 提交 status=succeeded。

## 验收

| 编号 | 结果 | 来源 | 验收项 | 原因 |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | 在默认 `config/tenants.yaml`（2 个租户）下执行自检 CLI：退出码为 0，输出 2 个租户各自的 user_id、workspace、`<input_dir>/<workspace>` 与 `<working_dir>/<workspace>`，且两个租户的目录路径互不相同。 | 在 lightrag-service 下执行 `python -m tenant.check`（PYTHONIOENCODING=utf-8），退出码 0。实际输出 3 行：『租户配置自检通过，共 2 个租户。』『警告：模型配置未齐全（LR_LLM_MODEL, LR_EMBEDDING_MODEL, LR_EMBEDDING_DIM），跳过实例构建。』以及『以下为配置推导结果（未构建真实实例）:』随后两行：user_1 workspace=ws_001 input=...\lightrag-service\data\inputs\ws_001 working=...\data ag_storage\ws_001；user_2 workspace=ws_002 input=...\data\inputs\ws_002 working=...\data ag_storage\ws_002。已直接读取 config/tenants.yaml，确认就是 user_1/ws_001 与 user_2/ws_002 这 2 个租户。两行 input 与 working 路径分别以各自 workspace 结尾且互不相同。 |
| A2 | passed | brief.md | 把第二个租户的 workspace 改成与第一个相同后执行自检：退出码非 0，错误信息包含重复的 workspace 名称，且不打印装配结果。 | 在系统临时目录写第二租户 workspace=ws_001 的配置（其余不变），`python -m tenant.check --config <临时文件>` 退出码 1。输出首条为『workspace 重复：'ws_001' 同时被 user_1（workspace='ws_001'） 与 user_2（workspace='ws_001'） 使用』，另附两条目录冲突说明。对输出做 token 检索 `rg -c "input=\|working=\|自检通过"` 退出码 1（零命中），确认失败时不打印任何装配结果。 |
| A3 | passed | brief.md | 把某个租户的 workspace 置空或写成 `ws/001`：自检失败并指出该 workspace 非法，退出码非 0。 | 两组临时配置分别执行：workspace 置空 → 退出码 1，输出『user_2（workspace=''） 的 workspace 为空；空值等于全局命名空间』并附目录冲突；workspace 写成 ws/001 → 退出码 1，输出『user_2（workspace='ws/001'） 的 workspace 含路径分隔符：'ws/001'』。两种情况都点名了非法的 workspace 取值。 |
| A4 | passed | brief.md | 在环境中设置 `POSTGRES_WORKSPACE` 后执行自检：失败并明确指出被禁止的环境变量；`NEO4J_WORKSPACE` 与 `WORKSPACE` 同样处理。 | 分别以 `env POSTGRES_WORKSPACE=ws_999 python -m tenant.check`、NEO4J_WORKSPACE、WORKSPACE 注入后运行默认配置，三次退出码都是 1，错误信息分别为『禁止设置环境变量 POSTGRES_WORKSPACE：它会覆盖实例级 workspace，破坏租户隔离』『... NEO4J_WORKSPACE ...』『... WORKSPACE ...』，都明确点名了被禁止的变量。 |
| A5 | passed | brief.md | 装配 2 个租户时：每个 `TenantContext` 的 `rag.workspace` 等于其配置的 workspace，`doc_manager.input_dir` 等于 `<input_dir>/<workspace>`，实例与目录都不共用。 | 两条独立证据。(1) 用记录型替身调用真实 assemble_registry（租户来自真实 ServiceSettings 目录推导）：builder 调用序列为 rag(ws_001)、doc(inputs 基目录, ws_001)、rag(ws_002)、doc(inputs 基目录, ws_002)；user_1.rag.workspace=ws_001、user_2.rag.workspace=ws_002；doc_manager.input_dir 分别为 ...\inputs\ws_001 与 ...\inputs\ws_002（不相等）；rag is not rag 为 True，doc_manager 不共用。(2) 在临时目录写了一个只记录构造入参的 lightrag 桩模块并通过 PYTHONPATH 前置，跑真实 tenant.check（模型参数齐全的分支），退出码 0，桩记录到 LightRAG(workspace=ws_001, working_dir=... ag_storage\ws_001) 与 LightRAG(workspace=ws_002, working_dir=... ag_storage\ws_002)，DocumentManager(input_dir=...\inputs, workspace=ws_001) 与 (..., ws_002)，证明 factory.py 的真实代码路径显式传参而非依赖环境变量。 |
| A6 | passed | brief.md | 用未注册的 user_id 调用 `TenantRegistry.get`：抛出 `UnknownTenantError`，错误信息包含该 user_id，且不返回任何实例。 | 对空 TenantRegistry 调用 get('missing_user_xyz')：抛出 UnknownTenantError（LookupError 子类），信息为『未注册的租户：missing_user_xyz』含该 user_id，无任何返回值（未进入 except 分支时 result 无值），注册表长度未被改变。代码核对 registry.py:47-52：get() 仅在字典命中时 return，未命中直接 raise，函数体内无回退分支。 |
| A7 | passed | brief.md | 在未安装数据库、未配置模型密钥的机器上执行 `pytest lightrag-service/tests`：全部通过，过程中不产生数据库连接与模型 API 调用。 | cd lightrag-service 执行 `python -m pytest` 退出码 0，51 passed（进度行 51 个点，-rA 输出 51 条 PASSED）；按验收原文在仓库根执行 `python -m pytest lightrag-service/tests -q` 同样退出码 0、51 passed。零外部副作用证据：用临时 conftest 插件把 socket.socket.connect / connect_ex / create_connection / getaddrinfo 与 subprocess.Popen 全部替换为抛异常，重跑仍为 51 passed 且拦截计数 blocked_attempts=0。环境事实：importlib.util.find_spec('lightrag') 为 None（LightRAG 未安装），LR_LLM_MODEL / LR_EMBEDDING_MODEL / LR_EMBEDDING_DIM 均未设置，lightrag-service/.env 不存在，运行后 lightrag-service/data 目录仍不存在。tests 与 tenant 下无 skip / xfail 标记。 |
| A8 | passed | brief.md | 执行 `docker compose -f lightrag-service/docker-compose.yml config`：解析成功，渲染结果包含 PostgreSQL 与 Neo4j 两个服务，且两者的环境变量中都不出现 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE`。 | 在仓库根执行 `docker compose -f lightrag-service/docker-compose.yml config` 退出码 0，加 `--format json` 亦退出码 0。解析渲染 JSON：services 排序后为 ['neo4j', 'postgres']，两个服务都在。postgres 环境变量键为 [POSTGRES_DB, POSTGRES_PASSWORD, POSTGRES_USER, TZ]；neo4j 为 [HEALTH_PASSWORD, HEALTH_USER, NEO4J_AUTH, NEO4J_PLUGINS, NEO4J_dbms_security_procedures_allowlist, NEO4J_dbms_security_procedures_unrestricted, TZ]。对渲染全文做 token 检索 POSTGRES_WORKSPACE\|NEO4J_WORKSPACE\|WORKSPACE，rg 退出码 1（零命中）。 |
| A9 | passed | brief.md | 检查渲染结果：PostgreSQL 与 Neo4j 的端口都只绑定 `127.0.0.1`，各自声明了健康检查与持久化命名卷。 | 从 `docker compose config --format json` 逐项读取：postgres ports=[host_ip=127.0.0.1, published=15432, target=5432]；neo4j ports=[127.0.0.1:17474->7474, 127.0.0.1:17687->7687]，三个发布端口的 host_ip 全部是 127.0.0.1，无 0.0.0.0。healthcheck 都存在：postgres 用 pg_isready，neo4j 用 cypher-shell RETURN 1。持久化命名卷：postgres 挂 lightrag-pg-data；neo4j 挂 lightrag-neo4j-data 与 lightrag-neo4j-logs；顶层 volumes 声明的正是这三个卷。 |

## 检查

| 检查 | 命令 | 工作目录 | 状态 | 退出码 | 耗时 |
| --- | --- | --- | --- | ---: | ---: |
| 运行 lightrag-service 单测 | -m pytest | lightrag-service | passed | 0 | 2031 ms |
| 默认两租户配置自检 | -m tenant.check | lightrag-service | passed | 0 | 1105 ms |
| 编排文件静态解析 | compose -f lightrag-service/docker-compose.yml config | . | passed | 0 | 1095 ms |

### Builder 报告的证据

以下为 Builder 报告，不等同于 Runtime 检查凭据或独立验收结果。

- pytest lightrag-service: passed — 51 passed in 0.27s，含新增 4 项包导出单测，全程无数据库连接与模型调用
- tenant 自检 CLI: passed — 默认两租户退出码 0，输出两行互不相同的 input/working 目录
- docker compose 静态解析: passed — docker compose -f lightrag-service/docker-compose.yml config 退出码 0，渲染出 postgres 与 neo4j
- 包导出契约核验: passed — python -c 验证 tenant.UnknownTenantError is tenant.registry.UnknownTenantError 为 True，tenant.__all__ 含 14 个公开符号
- 已知限制: 本机未安装 LightRAG 依赖与真实模型密钥，build_rag/build_llm_func/build_embedding_func 的真实构造路径未执行；自检在模型缺省时降级为只打印配置推导结果。
- 已知限制: 未启动容器，容器间连通性、pgvector 扩展创建与 Neo4j APOC 实际可用性留待有环境时验证（P2/P3）。
- 已知限制: vendored 源码未包含上游 lightrag_webui 前端、docs 文档站与 .github CI 配置，只保留 lightrag 包与安装元数据。

## 阻塞项

_无。_

## 风险与跳过的工作

- 真实 LightRAG 库未安装（find_spec('lightrag') 为 None），A5 的第二条证据用的是我自建的、仅记录构造入参的 lightrag 桩模块；真实 LightRAG 与 DocumentManager 的构造、存储初始化与向量表建立仍未在本机执行，符合 brief『本机只跑单测』的限定，但属于 P2 之前仍待真环境验证的残余风险。
- 未启动容器：healthcheck 命令的实际可执行性、pgvector 扩展创建、Neo4j APOC 加载只经静态渲染确认，brief 明确只要求静态可解析。
- lightrag-service/.pytest_cache 与 __pycache__ 留有本地缓存文件；已用 git check-ignore 确认被 .gitignore 忽略，不会入库。

## 之前的迭代

| 目标周期 | 迭代 | 尝试 | 结果 | 未解决项 | 摘要 | 完成时间 |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | pass | — | 9 项验收全部由 Verifier 独立复现通过：默认两租户自检退出码 0 且两类目录互不相同；重复 workspace、空 workspace、ws/001 以及三个被禁环境变量均退出码 1 并点名具体原因；装配层面每租户独立构造实例与文档管理器，未注册 user_id 抛 UnknownTenantError 且无回退；pytest 47 passed（在禁用 socket/DNS/子进程的包装下同样 47 passed 且零拦截）；docker compose config 渲染出 postgres 与 neo4j，环境变量不含三个被禁变量，端口全部只绑 127.0.0.1，各自带有健康检查与顶层声明的命名卷。范围核对未发现 P2 的 HTTP 路由、lifespan、内部 API Key 或 Semaphore，未连接数据库也未调用模型，vendored 目录无 .git 且版本为 1.5.7。 | 2026-11-07T13:50:25.376Z |
| 1 | 1 | 1 | recovery | — | The user rejected the Skill-coordinated Verifier pass. | 2026-11-07T13:54:13.442Z |
| 1 | 2 | 1 | pass | — | 第二轮独立验收：9 项验收全部由我本人读文件 + 跑命令复现通过。核心复核点是上一轮的 tenant/__init__.py 缺陷——现已确认为真正的包入口（40 行，md5 4fbc5edc359c4999fc017c40ac74680b，与 registry.py 的 faf4e717c24a8c105dc9bbec37c9cb86 不同，不再字节相同），声明了 14 个符号的 __all__，且 tenant.UnknownTenantError is tenant.registry.UnknownTenantError 等 6 组重导出对象同一性全部为 True，用 `except tenant.UnknownTenantError` 能捕获 TenantRegistry().get('missing_user_xyz') 抛出的异常；全仓只有一处 UnknownTenantError 类定义，没有为过验收而复制第二套类型，也没有删除任何原有测试用例（47 -> 51，新增的 4 项全部在 tests/test_package_exports.py，原 6 个测试文件的用例数 6/9/4/6/17/5 与上一轮完全一致）。回归方面：registry.py 的 md5 与上一轮记录值完全相同，validator.py 仍拒绝 POSTGRES_WORKSPACE/NEO4J_WORKSPACE/WORKSPACE 三类变量且 registry.get 仍无回退分支，config/settings/factory/check 的核心逻辑经阅读与实跑确认未削弱。范围方面：lightrag-service 下除 vendored 源码外无任何 fastapi/APIRouter/lifespan/Semaphore/internal API Key 痕迹，未连接数据库、未调用模型，LightRAG 目录无 .git 且版本为 1.5.7，仓库根 docker-compose.yml 与 HEAD 一致（git diff 为空），根目录仅 .gitignore 被修改（brief 授权范围内）。record-outcome 提交 status=succeeded。 | 2026-11-07T14:16:11.801Z |



## 结论

第二轮独立验收：9 项验收全部由我本人读文件 + 跑命令复现通过。核心复核点是上一轮的 tenant/__init__.py 缺陷——现已确认为真正的包入口（40 行，md5 4fbc5edc359c4999fc017c40ac74680b，与 registry.py 的 faf4e717c24a8c105dc9bbec37c9cb86 不同，不再字节相同），声明了 14 个符号的 __all__，且 tenant.UnknownTenantError is tenant.registry.UnknownTenantError 等 6 组重导出对象同一性全部为 True，用 `except tenant.UnknownTenantError` 能捕获 TenantRegistry().get('missing_user_xyz') 抛出的异常；全仓只有一处 UnknownTenantError 类定义，没有为过验收而复制第二套类型，也没有删除任何原有测试用例（47 -> 51，新增的 4 项全部在 tests/test_package_exports.py，原 6 个测试文件的用例数 6/9/4/6/17/5 与上一轮完全一致）。回归方面：registry.py 的 md5 与上一轮记录值完全相同，validator.py 仍拒绝 POSTGRES_WORKSPACE/NEO4J_WORKSPACE/WORKSPACE 三类变量且 registry.get 仍无回退分支，config/settings/factory/check 的核心逻辑经阅读与实跑确认未削弱。范围方面：lightrag-service 下除 vendored 源码外无任何 fastapi/APIRouter/lifespan/Semaphore/internal API Key 痕迹，未连接数据库、未调用模型，LightRAG 目录无 .git 且版本为 1.5.7，仓库根 docker-compose.yml 与 HEAD 一致（git diff 为空），根目录仅 .gitignore 被修改（brief 授权范围内）。record-outcome 提交 status=succeeded。
