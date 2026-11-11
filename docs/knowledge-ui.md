# 文件管理与知识图谱交付说明

本次实现基于 `https://github.com/luanchang-zh/badminton-mes.git` 的 `a9d3c0e`，工作分支为 `feat/knowledge-files-graph`。使用本仓库的 Vue 3 / TypeScript / Element Plus、Spring Boot 4，以及已有多租户 LightRAG 服务。

## 本机入口

- 文件管理：`http://127.0.0.1:18082/knowledge/documents`
- 知识图谱：`http://127.0.0.1:18082/knowledge/graph`
- 本次独立初始化的管理员：`admin / admin123`
- Spring Boot：`http://127.0.0.1:8082`
- LightRAG：`http://127.0.0.1:9622`
- Neo4j 控制台：`http://127.0.0.1:17476`

Compose 项目为 `badminton-knowledge`，独立使用 MySQL、Redis、PostgreSQL + pgvector、Neo4j 数据卷。之前 `factory-app` 的代码、服务和数据保留。

## 模型接入

按用户要求复用本机 `factory-app` 已配置的模型 API：生成模型对应旧项目 `MES_AI_*`，Embedding 对应 `MES_KNOWLEDGE_EMBEDDING_*`。不是 Ollama 离线模式，也不使用模拟回答或假图谱。

模型地址、模型名、API Key 和向量维度写入 `lightrag-service/.env`；Java 到 LightRAG 的内部密钥写入 `.env.knowledge.local`。两份文件均被 Git 忽略，不包含在交付源码中。新环境可以参考 `lightrag-service/.env.example` 配置相同字段，并确保 Java 的 `MES_RAG_INTERNAL_API_KEY` 与 Python 的 `LR_INTERNAL_API_KEY` 相同。

租户配置为 `lightrag-service/config/tenants-local.yaml`：原有两个演示账号与管理员分别使用 `ws_001`、`ws_002`、`ws_admin`。开发迁移只给尚未绑定的 admin 创建独立租户，不改变已有绑定。浏览器不传递 tenant/workspace 或内部密钥。

## 启动和重建

依赖：Docker Desktop、Node.js 22.12+、Python 3.10+。后端通过 Docker 中的 JDK 21 / Gradle 9.3.1 构建，避免 Windows 本地 Gradle 回环连接问题。

```powershell
# 首次准备 Python 运行环境，在 lightrag-service 中执行
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-local.txt

# 在仓库根目录启动已经构建的本机版本
.\scripts\start-knowledge-local.ps1

# 修改代码后重建：后端执行全量单测并生成 bootJar，前端执行类型检查和生产构建
.\scripts\start-knowledge-local.ps1 -Build
```

脚本启动独立 Compose 服务及隐藏的本机 LightRAG 进程。LightRAG 日志位于 `lightrag-service/local-server*.log`。修改模型或 Python 服务代码后，需要重启该进程；`/health` 只表示服务存活，处理是否成功以文件状态为准。

## 文件管理

支持 UTF-8 的 TXT、Markdown、CSV、JSON、LOG，单文件最大 20 MiB，每批最多 20 个文件。前后端同时校验类型、大小和文本编码；沿用仓库原有文本接口，不将 PDF/DOCX 二进制误当 UTF-8 文本上传。

提供文件名/文档编号搜索、状态筛选、分页、统计、后台进度轮询、批量上传与失败重试、正文预览、原文下载、版本导入和删除。列表沿用原接口最近 200 条的范围，达到上限时明确提示筛选范围。

上传、版本导入、删除及重新处理限 `ADMIN` / `CRAFT_ENGINEER`；已登录且绑定租户的账号可以读取自己的资料与图谱。重新处理按钮明确作用于整个当前租户的失败、待处理或中断文档。

更新采用保留版本的方式：先校验原文档在当前租户中存在，再给新文件生成唯一版本名并交给 LightRAG。新版本导入失败时不会删除旧资料；新旧版本均可能参与检索，确认新版本后可删除旧版。上游没有原位替换事务，因此没有把“先删除旧文件，再上传”包装成安全更新。

## 知识图谱

直接消费 `/api/rag/graph` 的真实 LightRAG / Neo4j 节点和关系，支持：

- 按起始实体及 1～3 层深度加载子图。
- 当前子图节点搜索、实体类型筛选、节点聚焦、拖拽和缩放。
- 节点详情、直接关联、关系方向、描述与权重。
- 子图截断提示、空状态、服务不可用提示。
- PNG 预览与下载。实体名和关系描述按模型原始返回展示，默认提取语言由上游 LightRAG 控制。

## 新增接口

| 接口 | 功能 |
| --- | --- |
| `GET /api/rag/documents/{docId}/text` | 正文预览 |
| `GET /api/rag/documents/{docId}/content` | 带下载文件名、no-store 和 nosniff 的 UTF-8 原文下载 |
| `POST /api/rag/documents/{docId}/versions` | multipart 新版本导入，返回任务追踪号 |
| `GET /tenant/{user_id}/documents/{doc_id}/content` | 仅供 Java 调用的 LightRAG 扩展；按绑定实例读取，校验内部密钥 |

新增内容读取接口未修改 vendored LightRAG 核心源码。Java 接口仍由登录上下文解析租户，Python 路由在注册时绑定实例，避免用户传入路径访问其他 workspace。

联调同时修复两项原有问题：同步/流式 RAG 客户端多构造器未明确注入，导致 Spring 上下文启动失败；Embedding 适配器嵌套使用上游默认 1536 维包装器，与配置的 1024 维模型不一致。另修复 Nginx 丢失 Host 端口引发非默认端口登录 403 的问题。

## 验证

- Java：全量单元测试 930 项通过，生产 bootJar 构建通过；包含新增 Spring 装配、内容读取、下载响应、版本导入和路径校验测试。
- Python：122 项已收集，121 项通过、1 项条件测试跳过；新增内部鉴权、跨租户内容隔离和 Embedding 维度回归测试。
- 前端：6 项图谱/文件边界测试通过，TypeScript 检查与 Vite 生产构建通过。
- 真实接口：复用实际模型 API，完成上传、处理完成状态、正文预览、精确原文下载、新版本导入、保留旧版、删除和 Neo4j 图谱查询。
- 演示资料两份，明确标注为软件联调数据；生成 24 个节点、23 条关系。它们由模型实际提取，不代表认证生产标准。

验收结果保存在 `output/knowledge/verification.json`。截图保存在同目录。前端专项测试：在 `frontend-web` 中运行 `node --test tests/knowledge.test.cjs`；Python 测试：在 `lightrag-service` 中运行 `.venv/Scripts/python -m pytest tests`。
