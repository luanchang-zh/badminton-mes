---
generated_from_state_version: 11
---

# 验证

## 当前结果

- 结果: **验收通过，需要你确认**
- 验证情况: **已完成检查，但需要你确认验证结果**
- 目标周期: 1
- 迭代: 2
- 验证器尝试次数: 1
- 完成时间: 2026-11-08T04:03:35.173Z
- 摘要: 独立复验第 2 轮：上一轮 A3/A4/A6 的 .env 通道缺陷确实已修复——server.main 在读取任何配置前调用 load_dotenv_files()，使校验与运行时共用同一份配置快照。在 lightrag-service/.env 上实测：POSTGRES_WORKSPACE、NEO4J_WORKSPACE、WORKSPACE 与 WHITELIST_PATHS=/* 均以退出码 1 拒绝且零路由输出，具名白名单 /health,/api/* 正常启动并输出 66 条路由，进程环境同名键优先于 .env（双向实测）。密钥链路走真实 main() 入口验证：.env 密钥进入 settings 并传到路由工厂，无密钥/错密钥 403、正确密钥 500（业务层拒绝，非鉴权）。A1/A2/A5/A7-A10 均以独立脚本重新取证，A9 用外部计数器而非自报峰值，A7/A8 用真实上游 drain。全量单测 82 passed。README 已补充 P2 章节，EXIT_STARTUP_ERROR 已移除，验证过程未留 .env 或临时文件。

## 验收

| 编号 | 结果 | 来源 | 验收项 | 原因 |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | 构建 2 个租户的应用后，路由表中存在 `/tenant/user_1/...` 与 `/tenant/user_2/...` 两组路由，每组各 33 条，且不存在未加租户前缀的 `/documents/upload`、`/query`、`/graphs`。 | 实测 python -m tenant.server --print-routes（lightrag-service 目录、无 .env）退出码 0。独立脚本按原始行统计：user_1 33 条、user_2 33 条，共 66 条；按 (租户,路径) 分组后无重复项。未加前缀的 /documents/upload、/query、/graphs 均不存在，所有路径都以 /tenant/ 开头；两租户各自都含 /tenant/<uid>/documents/upload、/query、/graphs。 |
| A2 | passed | brief.md | 执行 `python -m tenant.server --print-routes`，输出 2 个租户各自带 `/tenant/<user_id>` 前缀的路由，过程中不连接数据库、不调用模型。 | 在 socket.connect / connect_ex / create_connection / getaddrinfo 以及 psycopg、psycopg2、neo4j、asyncpg、openai 客户端全部被探针替换为「记录并抛错」的条件下执行 main(['--print-routes'])：退出码 0、输出 66 条 http:// 路由、探针命中列表为空 []，证明打印过程未建立任何外部连接或模型调用（也不进入 lifespan，未初始化存储）。 |
| A3 | passed | brief.md | 租户配置非法（重复 workspace 或设置了 `POSTGRES_WORKSPACE`）时执行 `--print-routes`，进程以非 0 退出码结束且不打印任何路由。 | 在 lightrag-service 下创建 .env 后执行 python -m tenant.server --print-routes：POSTGRES_WORKSPACE=shared → 退出码 1、输出含「禁止设置环境变量 POSTGRES_WORKSPACE」、路由行数 0；NEO4J_WORKSPACE=shared 与 WORKSPACE=shared 同样退出码 1、0 条路由；重复 workspace 配置文件同样退出码 1、0 条路由。矩阵脚本在独立子进程并清空同名进程环境变量后复跑 7 组场景全部符合预期。 |
| A4 | passed | brief.md | 请求任一租户路由时不带 `X-API-Key`，或带错误的 `X-API-Key`，响应状态为 401 或 403。 | 对 /tenant/user_1/documents/status_counts 与 /tenant/user_2/... 分别探测：不带 X-API-Key → 403，携带错误密钥 → 403。.env 通道（LR_INTERNAL_API_KEY=dotenv-secret-abc）与进程环境通道（proc-env-key）两种来源、两个租户结果一致。 |
| A5 | passed | brief.md | 请求任一租户路由时带正确的 `X-API-Key`，响应状态不是 401 也不是 403。 | 以 .env 中的密钥值作为正确密钥请求同一路由 → 500（占位实例不支持 doc_status，异常来自业务层而非鉴权层），不属于 401/403。走真实 main() 入口（仅替换 uvicorn.run 与实例构造器）复测得同样结果：os.environ 与 ServiceSettings.from_env(None) 都读到 .env 的密钥，两租户 correct 均为 500。/health 无密钥 200。 |
| A6 | passed | brief.md | `WHITELIST_PATHS` 被配置为通配全部路径时，服务拒绝启动且错误信息指出白名单会绕过租户鉴权。 | .env 写 WHITELIST_PATHS=/*（以及 /health,/* 混写）后执行 --print-routes：退出码 1、输出含「WHITELIST_PATHS 配置为全通配（如 /*），会让 /tenant/* 路由绕过租户鉴权」、路由行数 0。反向验证：.env 写 WHITELIST_PATHS=/health,/api/* 时退出码 0 且输出 66 条路由，说明修复没有误杀具名白名单。 |
| A7 | passed | brief.md | 应用启动再关闭后，每个租户实例的存储初始化恰好一次、存储释放恰好一次，共享状态清理恰好一次。 | 独立脚本驱动真实 build_lifespan（仅将 finalize_shared_state 换为记录器，真实清理需要已初始化的共享存储）：启动再关闭后事件序列为 init:user_1, migrate:user_1, init:user_2, migrate:user_2, finalize:user_1, finalize:user_2, shared；每租户 init 与 finalize 各 1 次，shared 1 次且位于序列末尾。 |
| A8 | passed | brief.md | 关闭时若仍有后台任务在运行，实例存储释放发生在等待后台任务结束之后。 | 同一脚本向 app.state.background_tasks 注入真实 asyncio 任务（sleep 0.4s，finally 记录完成时刻），drain 走真实 lightrag.kg.shared_storage.drain_reserved_background_tasks（未打桩）。实测 TASK_DONE_TIME=74040.0705、RELEASE_TIMES=[user_1: 74040.0706, user_2: 74040.0706]，两次 finalize 均晚于后台任务真正结束的时刻。 |
| A9 | passed | brief.md | 全局并发上限为 N 时并发发起多于 N 个模型调用，任一时刻正在执行的调用数不超过 N，且全部调用最终完成。 | 用外部独立计数器（不读 limiter.peak_in_flight）观测：limit=3 并发 15 次 → independent_peak=3、completed=15；再以 limit=2 跨两个不同包装器（模拟两租户）并发 12 次 → peak=2、completed=12；get_shared_limiter 连续两次返回同一实例（第二次入参 99 被忽略，limit 仍为 2），共享性成立。 |
| A10 | passed | brief.md | 未配置全局并发上限时并发发起多个模型调用，调用不被限流阻塞且全部成功完成。 | limit=None 时并发 8 次调用：independent_peak=8（完全并发、未被串行化）、completed=8、limiter.limit is None，直通行为成立。 |

## 检查

| 检查 | 命令 | 工作目录 | 状态 | 退出码 | 耗时 |
| --- | --- | --- | --- | ---: | ---: |
| 运行 lightrag-service 单测 | -m pytest tests/ | lightrag-service | passed | 0 | 11032 ms |
| 打印租户路由表 | -m tenant.server --print-routes | lightrag-service | passed | 0 | 5425 ms |

### Builder 报告的证据

以下为 Builder 报告，不等同于 Runtime 检查凭据或独立验收结果。

- pytest lightrag-service: passed — 82 passed（P1 51 + P2 31），无数据库连接与模型调用
- .env 通道拒绝启动复核: passed — POSTGRES_WORKSPACE、WHITELIST_PATHS=/* 写进 .env 后均 exit=1 且 0 条路由
- .env 密钥生效复核: passed — LR_INTERNAL_API_KEY 写进 .env 后进入 settings，TestClient 无密钥/错密钥 403
- README 与死代码清理: passed — README 覆盖 --print-routes/启动/鉴权/拒绝启动条件；移除未使用的 EXIT_STARTUP_ERROR
- 已知限制: 真实 LightRAG 实例的存储初始化与查询链路仍未在本机执行（需 PostgreSQL/Neo4j 与模型密钥，留给 P3）。
- 已知限制: 未实际拉起 uvicorn 监听端口，端口可用性与真实 HTTP 往返留待 P3。
- 已知限制: /tenant/* 这类前缀白名单同样会让上游免鉴权，当前只拦截全通配（brief 要求范围），已作为后续风险记录。
- 已知限制: P1 的包导出契约测试由「完全相等」改为「至少包含」，因为 P2 新增了公开符号。

## 阻塞项

- **user**: Verification passed; user acceptance is required before Archive. — next: `await-user`

## 风险与跳过的工作

- CWD 的 .env 仍是启动校验盲区：若在 lightrag-service 之外启动、且该工作目录存在 .env 而 lightrag-service/.env 不存在，校验会读到默认 WHITELIST_PATHS=/health,/api/* 并放行，随后上游 config.py 的 load_dotenv(".env") 才把 CWD 的 .env 载入。实测 startup_validation=PASSED，上游 whitelist_patterns 编译为 [('', True)]（全通配）；POSTGRES_WORKSPACE=shared-from-cwd 时 --print-routes 退出码 0 并输出 66 条路由，未被拒绝。README 指定的标准启动目录（lightrag-service）不受影响。
- WHITELIST_PATHS=/tenant/* 未被拦截：A6 与 Spec R3 只要求拦「/* 这类全通配」，但 brief 约束写的是「禁止 WHITELIST_PATHS 覆盖租户前缀」。实测进程环境设为 /tenant/* 时启动校验通过，上游编译为 [('/tenant', True)]，不带 X-API-Key 请求 /tenant/user_1 与 /tenant/user_2 的 documents/status_counts 均返回 500（已放行到业务层），两个租户的鉴权全部失效。Builder 已在 knownLimits 记录，属 brief 与 Spec 表述差异，建议后续收紧。
- LR_INTERNAL_API_KEY 为空时租户路由鉴权完全开放（实测无密钥请求返回 500 而非 401/403），当前仅靠「监听非回环地址必须有密钥」兜底；符合 Spec R3 现状，但同机多用户回环部署时无实质鉴权。
- A7 的共享状态清理以替身记录（真实 finalize_share_data 需要已初始化的共享存储，本机无数据库无法执行）；清理次数与末位顺序由实现代码（_stop_all 唯一调用点）与替身观测双重确认。

## 之前的迭代

| 目标周期 | 迭代 | 尝试 | 结果 | 未解决项 | 摘要 | 完成时间 |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | fail | A3, A4, A6 | 独立复核结果：78 项 pytest 全绿（junit 78/0/0），--print-routes 退出码 0、66 条路由、两租户各 33 条且全部带前缀；A1/A2/A5/A7/A8/A9/A10 由我自行复现通过，其中路由收集确实递归展开 FastAPI 的 _IncludedRouter（只读顶层会得到 0 条），关停顺序为真实 drain → finalize → finalize_share_data，限流峰值与配置上限严格一致。但 A3/A4/A6 在 README 指定的 .env 配置通道下失败：tenant.server 先读 os.environ 建 settings、上游后才 load_dotenv('.env')，导致 .env 中的 LR_INTERNAL_API_KEY 传不到路由工厂（无密钥请求返回 500 而非 401/403）、WHITELIST_PATHS=/* 被上游编译成全通配却未被启动校验拦截（服务照常启动，租户路由鉴权被绕过）、POSTGRES_WORKSPACE 写入 .env 时也不拒绝启动。故判定 fail，需修正配置加载时序后重验。 | 2026-11-08T03:28:14.138Z |
| 1 | 2 | 1 | pass | — | 独立复验第 2 轮：上一轮 A3/A4/A6 的 .env 通道缺陷确实已修复——server.main 在读取任何配置前调用 load_dotenv_files()，使校验与运行时共用同一份配置快照。在 lightrag-service/.env 上实测：POSTGRES_WORKSPACE、NEO4J_WORKSPACE、WORKSPACE 与 WHITELIST_PATHS=/* 均以退出码 1 拒绝且零路由输出，具名白名单 /health,/api/* 正常启动并输出 66 条路由，进程环境同名键优先于 .env（双向实测）。密钥链路走真实 main() 入口验证：.env 密钥进入 settings 并传到路由工厂，无密钥/错密钥 403、正确密钥 500（业务层拒绝，非鉴权）。A1/A2/A5/A7-A10 均以独立脚本重新取证，A9 用外部计数器而非自报峰值，A7/A8 用真实上游 drain。全量单测 82 passed。README 已补充 P2 章节，EXIT_STARTUP_ERROR 已移除，验证过程未留 .env 或临时文件。 | 2026-11-08T04:03:35.173Z |



## 结论

独立复验第 2 轮：上一轮 A3/A4/A6 的 .env 通道缺陷确实已修复——server.main 在读取任何配置前调用 load_dotenv_files()，使校验与运行时共用同一份配置快照。在 lightrag-service/.env 上实测：POSTGRES_WORKSPACE、NEO4J_WORKSPACE、WORKSPACE 与 WHITELIST_PATHS=/* 均以退出码 1 拒绝且零路由输出，具名白名单 /health,/api/* 正常启动并输出 66 条路由，进程环境同名键优先于 .env（双向实测）。密钥链路走真实 main() 入口验证：.env 密钥进入 settings 并传到路由工厂，无密钥/错密钥 403、正确密钥 500（业务层拒绝，非鉴权）。A1/A2/A5/A7-A10 均以独立脚本重新取证，A9 用外部计数器而非自报峰值，A7/A8 用真实上游 drain。全量单测 82 passed。README 已补充 P2 章节，EXIT_STARTUP_ERROR 已移除，验证过程未留 .env 或临时文件。
