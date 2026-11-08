# LightRAG 多租户知识库：设计与验收文档

这里是 P1～P3 的全部设计文档与验收记录，已从 Comet 工作流产物转为普通 Markdown，
不再受任何状态机管理，可以直接阅读与引用。

## 文档

| 文件 | 内容 |
| --- | --- |
| `01-multitenant-foundation.md` | **P1 规格**：配置解析、目录推导、启动隔离校验、实例注册表、依赖组件编排 |
| `02-multitenant-server.md` | **P2 规格**：按租户注册前缀路由、统一 lifespan、全局模型并发上限、内部密钥鉴权 |
| `03-isolation-verifier.md` | **P3 规格**：六个隔离场景的判定逻辑、三值状态语义、自检与真实执行 |
| `p3-brief.md` | **P3 需求说明**：为什么交付验证能力而不是验证结论 |
| `verification-records.md` | **验收记录**：P1 9/9、P2 10/10 的结论与关键证据；P3 尚未验收 |

## 实现位置

| 阶段 | 代码 | 状态 |
| --- | --- | --- |
| P1 | `lightrag-service/tenant/`（settings / config / validator / factory / registry / check） | 已提交 `1dca1f2` |
| P2 | `lightrag-service/tenant/`（server / app / lifespan / llm_limiter / auth / placeholder） | 已提交 `08dc0cd` |
| P3 | `lightrag-service/scripts/` + `lightrag-service/tests/scripts/` | 已完成，未提交 |

## 关键设计取舍

### 三阶段的分工

| 阶段 | 保证什么 | 不保证什么 |
| --- | --- | --- |
| P1 | 每个租户拿到不同的 `workspace` 与目录，配置错误被拦截 | 数据实际写入存储时是否真按 workspace 分开 |
| P2 | 每个租户有独立路由前缀与内部密钥，实例生命周期隔离 | 检索时会不会读到别的租户的向量或图谱 |
| P3 | 提供可执行、可验证的隔离检查工具 | **不在本机得出隔离结论** |

### 为什么 P3 交付「能力」而不是「结论」

本机没有 PostgreSQL、Neo4j 与模型。如果验证脚本直接 `httpx.get(...)` 再断言，
那它在本机就是一段无法验证的代码。因此 P3 把 I/O 与判定分开：

```text
protocols.py        三个协议（HTTP / PG / Neo4j）
scenarios.py        S1~S4 判定，只依赖协议
storage_scenarios.py S5~S6 判定，只依赖协议
clients.py          真实实现（驱动延迟导入）
fakes.py            替身：自检与单测共用同一份
runner.py           CLI 编排与退出码
```

这样本机能验证**判定逻辑抓得住串数据**——给每个场景喂正常数据和故意串的数据，
前者必须判 pass、后者必须判 fail。只测 pass 分支的话，一个永远返回 pass 的空实现也能通过。

### 三值状态

`pass` / `fail` / `blocked`。`blocked` 表示环境不具备（连不上、缺依赖、索引超时），
**它不是通过**，在退出码上也独立为 2，与通过的 0、失败的 1 区分。

## 尚未完成的部分

| 项 | 说明 |
| --- | --- |
| P3 真实执行 | 需在有环境时跑 `python scripts/verify_isolation.py --real` |
| P4 | Java 侧租户映射与 `RagClient` |
| P5 | Java 对外接口、SSE 流式转发、Postman 用例 |

## 相关文档

- 完整开发规划：`backend-java/docs/LightRAG多租户知识库开发规划.md`
- 技术方案原始出处：`badminton-mes.wiki/LightRAG_课设技术方案.md`
