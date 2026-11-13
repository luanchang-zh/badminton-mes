# 目标

交付一套**可在本机验证的跨租户隔离验证工具**。核心做法是把三类外部 I/O（RAG HTTP 接口、PostgreSQL 巡检、
Neo4j 巡检）抽象成协议，使场景判定逻辑不依赖真实数据源，从而在本机没有数据库和模型的情况下，
也能用替身完整验证「判定逻辑是否抓得住串数据」。

本阶段交付的是**验证能力**，不是验证结论。真实的隔离结论必须在有 PostgreSQL、Neo4j 与模型的环境中
执行一次 `--real` 才能得出。

# 范围

## 目录结构

### 新建

- `lightrag-service/scripts/isolation/__init__.py` — 子包入口，导出协议、结果类型与运行入口。
- `lightrag-service/scripts/isolation/protocols.py` — 三个 `Protocol`：租户 HTTP 客户端、PostgreSQL 巡检、Neo4j 巡检；以及 `ScenarioResult` 结果类型与 `ScenarioStatus` 枚举。
- `lightrag-service/scripts/isolation/clients.py` — 上述协议的真实实现；`httpx` / `psycopg` / `neo4j` 延迟导入，缺依赖时抛带修复提示的专用异常。
- `lightrag-service/scripts/isolation/scenarios.py` — S1～S6 的判定逻辑，只依赖协议，返回 `ScenarioResult`。
- `lightrag-service/scripts/isolation/runner.py` — CLI 参数、场景挑选、并发编排、汇总输出、退出码。
- `lightrag-service/scripts/isolation/report.py` — 生成 JSON 证据报告（每场景的输入、观察事实、判定依据）。
- `lightrag-service/scripts/fakes.py` — 替身实现：内存版 RAG 后端、假 PG 表、假 Neo4j 图；`--self-check` 与 pytest 共用同一份。
- `lightrag-service/scripts/verify_isolation.py` — 入口薄封装，只调用 `runner.main()`。
- `lightrag-service/scripts/README.md` — 两类用法（`--self-check` / `--real`）与后置执行清单。
- `lightrag-service/tests/scripts/__init__.py` — 测试子包标记。
- `lightrag-service/tests/scripts/test_scenarios.py` — 每个场景的「正常数据判 PASS / 串数据判 FAIL」双分支测试。
- `lightrag-service/tests/scripts/test_runner.py` — 退出码、场景挑选、报告字段测试。
- `lightrag-service/tests/scripts/test_clients.py` — 缺依赖降级、口令生成唯一性测试。

### 修改

- `lightrag-service/conftest.py` — 把 `scripts` 目录加入导入路径，使测试能 `import isolation.*`。

### 删除

无

### 明确不建

- 不启动 Docker 容器、不连接真实 PostgreSQL / Neo4j、不调用真实模型 — 本机不做实地执行。
- 不建 pytest 集成测试标记体系 — 本阶段只用单测。
- 不建 CI 流水线配置 — 超出范围。
- 不改动 P1 / P2 已交付的 `tenant` 包与 vendored 源码。

# 非目标

- 不在本机得出真实的跨租户隔离结论；`--real` 只保证代码路径完整、参数齐备。
- 不验证模型回答质量、检索准确率 —— 只验证「A 的数据是否出现在 B 的结果里」。
- 不做性能压测或容量测试。
- 不回归测试既有 MES 的 Java、前端与小程序代码。

# 约束与不变量

- 场景判定逻辑必须只依赖协议，不得直接 import `httpx` / `psycopg` / `neo4j`。
- 环境缺失、连接失败、索引等待超时一律记 `blocked`，**不得**记 `pass`。
- 等待索引完成必须用轮询 + 超时，不得写死 `sleep`。
- 每轮口令随机生成，同一进程内两次运行不得重复。
- `--self-check` 不得触碰任何外部服务。
- 代码规范：单文件不超过 400 行，单函数不超过 50 行，公开函数带函数级注释。

# 关键决定

- **I/O 与判定分离**：这是本阶段唯一的关键决策。若脚本直接调用 `httpx` 再断言，本机就无法验证它是否正确；
  拆成协议 + 判定 + 编排三层后，判定逻辑可用替身完整覆盖。
- **同一份替身两用**：`fakes.py` 同时供 `--self-check` 与 pytest 使用，避免「测试用的替身」与「脚本自身的替身」不一致。
- **状态三值**：`pass` / `fail` / `blocked`。只有 `pass` 和 `fail` 是结论；`blocked` 表示环境不具备，不能当作通过。

# 验证期望

- 本机执行 `pytest lightrag-service/tests` 全绿。
- 本机执行 `python scripts/verify_isolation.py --self-check` 能跑完 S1～S6 并输出结构化结论。
- 不连接数据库、不调用模型。

# 验收示例

- `--self-check` 使用替身跑完全部 6 个场景，退出码为 0，输出中每个场景都有明确状态。
- 喂入「B 的答案里出现 A 的口令」这种串数据时，S1 判定为 `fail`；喂入正常数据时判定为 `pass`。
- S2 在同名文件内容不同、且各自只查到自己的口令时判定为 `pass`；任一方查到对方口令时判定为 `fail`。
- S3 并发上传删除后，若任一方文档列表出现对方的 doc_id，判定为 `fail`。
- S4 重启前后文档数量或状态不一致时判定为 `fail`。
- S5 若 PostgreSQL 中某个表存在租户数据落在错误 workspace 的行，判定为 `fail`。
- S6 若两个租户的图谱实体出现在同一 Label 上，判定为 `fail`。
- 环境缺失（无法连接数据库）时，对应场景记为 `blocked` 而不是 `pass`。
- 索引等待超时时该场景记为 `blocked`，且不出现固定时长的无条件睡眠。
- 同一进程内连续两次运行生成的口令不相同。
- 缺少 `psycopg` 或 `neo4j` 依赖时，使用 `--real` 会给出可读提示并指出需要安装的包名，而不是抛出原始 ImportError。
