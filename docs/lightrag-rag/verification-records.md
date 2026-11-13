# 验收记录

本文件保留 P1、P2 两轮 Comet Native 独立验收的结论摘要。
完整报告原在 `docs/comet/archive/*/verification.md`，该目录已清理，结论与关键证据摘录如下。

> **重要**：P3 尚未完成独立验收。本文件不包含 P3 的隔离结论。

## P1：多租户知识库底座

| 项 | 内容 |
| --- | --- |
| change | `lightrag-multitenant-foundation` |
| 验收结果 | **9/9 通过** |
| 验收轮次 | 2 轮（第一轮发现 `tenant/__init__.py` 是 `registry.py` 的字节副本，回 Build 修复后第二轮确认修复真实） |
| 提交 | `1dca1f2` feat(lightrag): 新增多租户知识库底座 P1 |
| 单测 | 51 passed |

### 9 项验收

| 编号 | 验收内容 | 结果 |
| --- | --- | --- |
| A1 | 默认 2 租户自检输出各自独立的 input/working 目录 | passed |
| A2 | 重复 workspace 被拒绝且不打印装配结果 | passed |
| A3 | 空 workspace 或含路径分隔符被拒绝 | passed |
| A4 | `POSTGRES_WORKSPACE` / `NEO4J_WORKSPACE` / `WORKSPACE` 被拒绝 | passed |
| A5 | 两个 `TenantContext` 不共享实例与目录 | passed |
| A6 | 未注册租户抛 `UnknownTenantError` | passed |
| A7 | 无数据库与模型密钥时 pytest 全绿，零外部副作用 | passed |
| A8 | 编排文件静态解析成功，两个服务都不含全局 workspace 变量 | passed |
| A9 | 两组件端口只绑 `127.0.0.1`，各有健康检查与持久化卷 | passed |

### 关键证据摘要

- A7 零副作用验证：把 `socket.connect` / `getaddrinfo` / `subprocess.Popen` 全部替换为抛异常后重跑，仍 51 passed，拦截计数为 0。
- A5 双证据：记录型替身调用真实 `assemble_registry`，确认 builder 调用序列为 `rag(ws_001) → doc(base, ws_001) → rag(ws_002) → doc(base, ws_002)`；另用 lightrag 桩模块跑真实 `tenant.check`，确认 `factory.py` 显式传参而非依赖环境变量。

## P2：多实例 HTTP 服务入口

| 项 | 内容 |
| --- | --- |
| change | `lightrag-multitenant-server` |
| 验收结果 | **10/10 通过** |
| 验收轮次 | 2 轮（第一轮发现 `.env` 配置快照不一致导致 A3/A4/A6 可绕过，回 Build 修复后第二轮确认） |
| 提交 | `08dc0cd` feat(lightrag): 新增多租户 HTTP 服务入口 P2 |
| 单测 | 82 passed（含 P2 新增 31 项） |

### 10 项验收

| 编号 | 验收内容 | 结果 |
| --- | --- | --- |
| A1 | 两租户各有 33 条带前缀路由，无未加前缀的业务路由 | passed |
| A2 | `--print-routes` 输出两组前缀路由，不连库不调模型 | passed |
| A3 | 非法租户配置拒绝启动且不打印路由 | passed |
| A4 | 无密钥或错误密钥返回 401/403 | passed |
| A5 | 正确密钥不被 401/403 拦截 | passed |
| A6 | 全通配白名单拒绝启动 | passed |
| A7 | 每实例初始化与释放各一次，共享状态清理一次 | passed |
| A8 | 释放发生在后台任务结束之后 | passed |
| A9 | 并发调用峰值不超过上限 | passed |
| A10 | 未配置上限时直通 | passed |

### 第一轮发现的真实缺陷（已修复）

`tenant/server.py` 先读配置并校验，之后上游模块才 `load_dotenv` 加载 `.env`，导致**只写在 `.env` 里的安全项可以绕过启动校验**：

- `.env` 里的 `POSTGRES_WORKSPACE`：隔离校验看不到，服务照常启动；
- `.env` 里的 `WHITELIST_PATHS=/*`：校验看默认值放行，上游据此放开全部租户鉴权；
- `.env` 里的 `LR_INTERNAL_API_KEY`：工厂收到 `None`，鉴权完全开放。

修复方式：新增 `load_dotenv_files()`，在 `main()` 里**先加载 `.env` 再读配置**，并补 4 项 `.env` 通道回归用例。

### 关键证据摘要

- 路由数量：`--print-routes` 输出 `[user_1] 33 条路由` 与 `[user_2] 33 条路由`，共 66 条，33 = 文档 18 + 查询 3 + 图谱 12。
- 并发上限用**外部独立计数器**验证（不采信代码自报的 `peak_in_flight`）：limit=3 并发 15 → peak=3。
- 关闭顺序用真实上游 `drain_reserved_background_tasks` 验证：`drain:begin → bg:cleanup-done → drain:end → finalize → shared`。
- `--print-routes` 零外部调用：把 `socket` / `psycopg` / `neo4j` / `openai` 全部替换为抛异常探针，仍 exit 0 且命中列表为空。

## 已记录的遗留风险（P2 验收时确认，未修复）

| 风险 | 说明 |
| --- | --- |
| 进程 CWD 下的 `.env` 是校验盲区 | 在 `lightrag-service` 之外启动且该目录有 `.env` 时，校验读默认值放行，随后上游才载入 CWD 文件。标准启动目录不受影响。 |
| `WHITELIST_PATHS=/tenant/*` 未被拦截 | 只拦全通配 `/*`；`/tenant/*` 同样能让租户路由免鉴权。 |
| 内部密钥为空时鉴权完全开放 | 目前仅靠“非回环必须有密钥”兜底。 |

## P3：跨租户隔离验证工具

| 项 | 内容 |
| --- | --- |
| change | `lightrag-isolation-verifier` |
| 代码状态 | **已完成**，未提交 |
| 验收状态 | **未完成** —— Comet 运行时检查反复因工作区文件指纹变化而中断，未进入独立验收 |
| 单测 | 118 passed（P1+P2 82 + P3 36） |
| 自检 | `--self-check` 六场景全 PASS，退出码 0 |
| 反向验证 | 串数据替身下 S1/S2/S3/S5/S6 均判 fail |

> P3 的真实跨租户隔离结论**仍未得出**。工具已就绪，需在有 PostgreSQL、Neo4j 与模型的环境中执行 `--real` 才能得出结论。
