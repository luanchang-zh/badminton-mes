# 隔离验证工具

这个目录回答一个问题：**A 用户的数据，B 用户到底看不看得到。**

它有两种用法，证据强度完全不同，不要混淆：

| 模式 | 命令 | 能证明什么 |
| --- | --- | --- |
| 自检 | `--self-check` | 证明**验证逻辑本身正确**：喂串数据能判 fail，正常数据判 pass |
| 真实 | `--real` | 证明**真实环境是否隔离**，需要 PostgreSQL、Neo4j 与模型 |

**自检通过不等于隔离成立。** 自检用的是内存替身，只验证判定逻辑；
真实的跨租户隔离结论只有跑过 `--real` 才能得出。

## 本机自检

不需要任何外部服务：

```bash
cd lightrag-service
python scripts/verify_isolation.py --self-check
```

预期输出六个场景全部 PASS，退出码 0：

```text
[PASS   ] S1  A 能查到自己的口令，B 查不到
[PASS   ] S2  两边都只查到自己的口令，同名文件未互相覆盖
[PASS   ] S3  并发上传与删除后双方文档互不干扰
[PASS   ] S4  重启前后双方文档集合一致
[PASS   ] S5  所有目标表只包含本租户的 workspace
[PASS   ] S6  图谱 Label 与实体均按租户分开
合计 6 项：pass=6 fail=0 blocked=0
```

## 六个场景

| 编号 | 抓的问题 | 判定依据 |
| --- | --- | --- |
| S1 | B 能不能查到 A 的内容 | A 的随机口令是否出现在 B 的回答或检索上下文里 |
| S2 | 同名文件会不会互相覆盖 | 双方是否只查到自己那份的口令 |
| S3 | 并发上传删除会不会串号 | 双方文档列表里是否出现对方的 doc_id |
| S4 | 重启后数据会不会错位 | 重启前后文档集合是否一致 |
| S5 | PostgreSQL 是否按 workspace 分开 | 各表是否只包含本租户的 workspace，**含 LLM 缓存表** |
| S6 | Neo4j 图谱是否按租户分开 | Label 是否互不交叉，且没有落到全局 `base` |

**为什么用随机口令**：`ISOLATION-a1b2c3d4e5f6` 这种串模型不可能自己编出来，
它一旦出现在另一个租户的回答里，就是确凿的串数据证据 —— 比「回答看起来对不对」可靠得多。

**为什么 S5 要包含 LLM 缓存**：缓存串了等于答案串了。即使向量检索是隔离的，
一旦 B 命中了 A 的缓存，B 就直接拿到了 A 的答案。

## 有环境时执行

### 1. 准备

```bash
# 启动依赖组件
docker compose -f lightrag-service/docker-compose.yml up -d

# 配置模型与数据库连接（.env）
LR_LLM_MODEL=...
LR_EMBEDDING_MODEL=...
LR_EMBEDDING_DIM=...
LR_INTERNAL_API_KEY=...
LR_BASE_URL=http://127.0.0.1:9621
LR_PG_DSN=postgresql://lightrag:change-me@127.0.0.1:15432/lightrag
NEO4J_URI=bolt://127.0.0.1:17687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=change-me-please
```

### 2. 安装可选驱动

```bash
pip install httpx psycopg[binary] neo4j
```

缺这几个包时 `--real` 会给出可读提示并指出要装什么，不会抛原始 ImportError。

### 3. 启动服务并执行

```bash
python -m tenant.server &
python scripts/verify_isolation.py --real --report docs/isolation-report.json
```

### 4. 预期结果

| 结果 | 含义 | 下一步 |
| --- | --- | --- |
| 六项全 PASS，退出码 0 | 未发现串数据 | 可作为课设的隔离证据 |
| 出现 FAIL，退出码 1 | **确认存在串数据** | 回到 `tenant/validator.py` 与 workspace 配置排查，不要继续开发 |
| 出现 BLOCKED，退出码 2 | 环境不具备 | 检查服务是否启动、索引是否完成、驱动是否装齐 |

BLOCKED **不是通过**。它只说明这次没法判断，必须解决环境后重跑。

## 常用参数

```bash
--only S1,S3        # 只跑指定场景
--report <path>     # 写出 JSON 证据报告
--self-check        # 用替身自检（默认）
--real              # 对真实服务执行
```

## 报告

`--report` 生成的 JSON 包含每个场景的状态、判定说明与实测证据。
报告里有一个 `conclusive` 字段：

- 自检模式恒为 `false` —— 它不能作为隔离结论；
- 真实模式为 `true` —— 才可以直接引用到课设报告里。

## 代码结构

```text
scripts/
├── verify_isolation.py         # 入口（薄封装）
├── fakes.py                    # 替身：自检与单测共用同一份
└── isolation/
    ├── protocols.py            # 三个协议 + 结果类型
    ├── clients.py              # 真实实现（驱动延迟导入）
    ├── scenarios.py            # S1～S4 判定
    ├── storage_scenarios.py    # S5～S6 判定
    ├── runner.py               # CLI 编排与退出码
    └── report.py               # JSON 证据报告
```

判定逻辑只依赖 `protocols.py`，不 import `httpx` / `psycopg` / `neo4j` ——
这条约束由单测强制检查，因为一旦破坏，本机就无法再验证脚本本身。

## 单测

```bash
pytest tests/scripts/
```

关键用例是「同一场景喂两份数据」：正常数据必须判 pass，串数据必须判 fail。
只测 pass 分支的话，一个永远返回 pass 的空实现也能通过，那就等于没验证。
