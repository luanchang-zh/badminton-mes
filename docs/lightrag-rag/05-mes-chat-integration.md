# MES 聊天接入与剩余缺口方案

更新：2026-11-13。用户最新确认 LightRAG 尚未部署。聊天后端及正式 Vue 聊天页面均已实现；真实服务部署、租户开通与问答联调留待后续。

| 范围 | 当前结论 |
| --- | --- |
| 本人开通状态、会话增删查改、历史消息 | 已实现，支持本地数据库读写 |
| 多轮历史、生成状态、幂等、停止与重试 | 已实现，真实 HTTP＋H2＋上游替身验证通过 |
| 结构化引用、同步与 SSE | 已实现，上游缓存合并行和目录脱敏已测试 |
| 聊天前端 | `/rag/chat` 菜单、Vue 页面及 API 已接入；53 项隔离浏览器、8 项 SSE 检查及三种桌面视口通过 |
| LightRAG 部署、账号实际开通、真实问答隔离 | 待实施 |
| 指代追问的独立检索改写 | 后续上游适配；当前已传递 conversation_history |

当前接口契约以 [聊天 API 文档](../api/rag-chat.md) 为准。下文保留详细实施方案，已交付内容按本表和接口文档接续。

## 1. 已整合的成果

远端 main `a9d3c0e` 已合入 `InsightFace`，合并提交为 `317c129`。原有未提交的人脸、UI、测试和设计修改已恢复。

- `lightrag-service/`：LightRAG v1.5.7、固定租户注册表、生命周期、内部鉴权、隔离验证脚本。
- `module/rag/`：MES 登录态解析、绑定表、同步与流式客户端、文档和图谱业务接口。
- 查询请求：`question`（最多 2000 字符）、`mode`（默认 `naive`，支持 `local/global/hybrid/mix`）。
- 同步接口：`POST /api/rag/query`，返回 `answer/contexts/mode`。
- 流式接口：`POST /api/rag/query/stream`，事件为 `meta/reference_details/references/delta/error/done`；前端用携带 MES Bearer Token 的 `fetch` 读取。
- 文档接口：`POST/GET /api/rag/documents`、`POST /api/rag/documents/text`、`DELETE /api/rag/documents/{docId}`、`POST /api/rag/documents/reprocess-failed`。
- 图谱接口：`GET /api/rag/graph/labels`、`GET /api/rag/graph?label=...&maxDepth=...`，深度 1～3。

文档写操作限 ADMIN、CRAFT_ENGINEER；查询和图谱登录即可访问，仍须有启用的租户绑定。文件入口接受 UTF-8 文本类文件，默认业务上限 20MB。`reprocess-failed` 作用于整个租户的失败文档。部分旧规划仍写 `/reindex`，以 Controller 和 Postman 的当前实现为准。

合并时统一了上传配置：容器单文件默认 25MB、总请求默认 32MB；人脸四个上传入口及照片转换处独立校验 10MB，超限保持 A0702。统一异常处理保留人脸分支已有映射。

## 2. 账号绑定与知识库开通

现状：`rag_tenant_binding.user_id` 对应 MES 用户主键；`tenant_id` 对应 `tenants.yaml` 的 `user_id`。数据库对两列分别设唯一约束。当前所有已登录用户首次访问聊天状态接口时，由 `RagBindingService` 初始化独立本地绑定。演示账号已停用并逻辑删除，原有演示账号创建和固定绑定迁移已于 2026-11-14 关闭。

首轮执行固定租户开通，具体步骤如下。

### 2.1 确定账号及映射

先从 `sys_user` 查询 `operator_demo`、`face_user2` 和一个 ADMIN/CRAFT_ENGINEER 账号的实际主键，同时查询其现有 `rag_tenant_binding`，核对后再写新配置。已存在的绑定保持原有知识库归属。

按当前交接中的主键，拟新增映射为：

| MES 账号 | MES 主键 | LightRAG tenant_id | Workspace |
| --- | --- | --- | --- |
| operator_demo | 5 | mes_u_5 | ws_mes_u_5 |
| face_user2 | 17 | mes_u_17 | ws_mes_u_17 |
| 文档管理验收账号 | 查询后确定 | mes_u_实际主键 | ws_mes_u_实际主键 |

### 2.2 先准备服务端租户，再启用 MES 绑定

1. 在 `lightrag-service/config/tenants.yaml` 保留 `user_1/user_2`，追加上述独立租户。例如：

   ```yaml
   - user_id: mes_u_5
     workspace: ws_mes_u_5
   - user_id: mes_u_17
     workspace: ws_mes_u_17
   ```

2. 使用已有环境执行 `python -m tenant.check`，检查标识唯一、目录独立；执行 `python -m tenant.server --print-routes` 核对新增路由前缀。
3. 通过当前实际启动方式重启 LightRAG，检查启动成功、新租户文档列表可访问。
4. 新增 `backend-java/src/main/resources/db/dev-migration/R__Insert_Rag_Chat_Demo_Binding.sql`，按 `user_no` 查主键写入已准备好的 tenant_id。迁移需可重复执行，遇到用户已有不同绑定或租户已被占用时停止并报告冲突。
5. 重建 MES 后端，让 Flyway 应用新迁移，再查询绑定结果。真实已有数据库中新建迁移，保留已应用的版本迁移文件。
6. 通过内部鉴权接口为两个操作工测试租户导入不同的测试文档；业务 API 上传另用已绑定的 ADMIN/CRAFT_ENGINEER 账号验收。

### 2.3 明确开通状态和新用户流程

新增 `RagProfileController`，提供 `GET /api/rag/profile`，返回当前用户的 `bound`、`enabled`、`canManageDocuments`。未绑定或禁用时显示开通状态，接口继续从登录态确定用户。

固定租户下，新用户的步骤是：注册 MES → 管理员确认开通 → 追加租户配置 → 校验并重启 RAG → 写入绑定 → 用户进入聊天。注册成功与知识库开通分别展示状态。

若交付范围要求注册后立即开通，需要增加独立的自动开通阶段：

1. 注册事务提交后写入 `PENDING` 开通任务，记录稳定用户主键。
2. LightRAG 增加仅内部鉴权可调用的幂等租户创建接口，按稳定 ID 初始化 Workspace、目录、存储连接并登记实例。
3. 将路由改为通用租户前缀，由依赖解析器从注册表选择实例；当前代码是在启动时为每个租户逐个注册路由。
4. 服务重启从持久租户清单恢复已开通实例；创建失败清理半初始化资源，任务记录 `FAILED` 并允许重试。
5. 创建成功后写入 MES 绑定并把开通任务置为 `READY`，前端轮询本人的开通状态。

验收：两个用户分别看到自己的文档；密码和人脸登录同一账号访问同一知识库；未绑定账号显示开通状态；文档写操作继续要求 ADMIN/CRAFT_ENGINEER。

## 3. 多轮上下文与会话保存

现状：Java 请求和客户端只传单次问题。上游已支持 `conversation_history`，格式为 `role/content` 消息列表；它用于回答生成的上下文，检索仍以本次查询为依据。

### 3.1 新增两张表与归属校验

新增 `db/migration/V2026111301__create_rag_conversation.sql`：

| 表 | 主要字段 |
| --- | --- |
| rag_conversation | id、user_id、tenant_id、title、active_message_id、created_at、updated_at |
| rag_message | id、conversation_id、role、content、mode、status、request_id、reply_to_id、references_json、created_at、updated_at |

会话表按 `user_id/updated_at` 建列表索引，消息按会话和消息 ID 排序；同一会话下用户请求的 `request_id` 唯一。所有查询先限制当前登录 user_id，再确认会话 tenant_id 与当前绑定一致。

### 3.2 新增会话 API

新增 `RagConversationController`、`RagConversationService`、实体与 Repository。

| 方法 | 拟新增接口 | 用途 |
| --- | --- | --- |
| POST | /api/rag/conversations | 新建会话，返回会话 ID |
| GET | /api/rag/conversations | 分页列出本人会话 |
| GET | /api/rag/conversations/{id}/messages | 分页恢复消息及来源 |
| DELETE | /api/rag/conversations/{id} | 删除本人会话；先取消活跃生成 |
| POST | /api/rag/messages/{id}/cancel | 取消本人消息对应的生成 |
| POST | /api/rag/messages/{id}/retry | 基于原问题生成新的回答记录 |

现有 `/query` 和 `/query/stream` 增加可选 `conversationId/requestId`。带会话 ID 时启用持久会话逻辑；原有单次调用继续可用。

### 3.3 接入历史并保存生成状态

修改 `RagQueryReqVO`、`RagQueryService`、`RagQueryServiceImpl`、`RagClient`、`RagStreamClient`：

1. 在请求线程上完成身份、租户、会话归属校验。
2. 以短事务原子占用会话的 `active_message_id`，写入用户问题和 `GENERATING` 回答记录；同一 requestId 的重复请求复用已有记录。
3. 只加载该会话已成功完成的历史问答，初始取最近 6 轮、历史正文最多 16000 字符，按完整问答对裁剪；同时满足上游 128 条、单消息 32768 字符及总输入上限。
4. 向上游请求增加 `conversation_history: [{role, content}, ...]`，由数据库记录构建 user/assistant 消息。
5. SSE 首先发送 `meta` 事件，告知 conversationId、userMessageId、assistantMessageId；继续发送增量和引用。
6. 增量写入受控缓冲并定期保存快照；生成成功保存 `COMPLETED`，用户停止保存 `CANCELLED`，异常保存 `FAILED`，均释放会话活跃标记。
7. 取消接口校验消息归属后，调用已有 `StreamCancellation.cancel()` 关闭上游句柄；前端随后中止本地读取。页面离开、停用或退出登录同样走取消和本地清理。
8. 重试创建新的 assistant 消息并关联原问题，保留原失败记录；进程重启后将遗留 `GENERATING` 记录收敛为失败状态，恢复会话可继续发送。

### 3.4 验证指代追问

用“请解释点检要求 → 第二项怎么操作 → 它有哪些注意事项”做三轮验收。上游历史只参与回答上下文；涉及指代的检索问题还需补全为独立问题。

补全放在 RAG 服务的自定义适配层，复用现有模型配置，根据本会话的历史与原问题生成独立检索问题，再发起查询。MES 同时保存原问题与实际检索问题，界面展示用户原问。补全失败时给出可重试状态。

验收：三轮追问内容连贯、命中文档正确；刷新恢复；跨用户会话被拒绝；重复发送、停止、重试与服务重启后消息次序和状态正确。

## 4. 结构化引用溯源

落地前基线：上游提供 `reference_id/file_path/content[]`，原 Java 实现只提取文本并压平成 `contexts`。现已新增结构化 `reference_details`，以下数据链路已完成，既有 `references` 字符串数组保留兼容。

按数据经过的顺序修改：

1. 新增 `client/dto/RagReference.java`，字段为 `referenceId/sourceName/chunks`，`chunks` 保留同一文件的所有片段。
2. 在 `RagResponseParser` 增加结构化解析，保留上游 reference_id；将 file_path 规整为展示文件名，内部路径仅用于服务端映射。
3. 修改 `RagQueryResult` 和 `RagQueryRespVO`，增加 `references` 列表，原有 `contexts` 继续返回。
4. 修改 `RagStreamLine`、`RagStreamClient` 的收集器，将结构化引用沿流式链路保留下来，包括“references 与 response 同一行”的缓存响应。
5. 在 `RagSseEventNames/RagSsePayloads` 增加 `reference_details` 事件；已有文本 `references` 保留，前端优先展示结构化事件。
6. 同步与流式解析复用一个转换函数，并将结构化来源保存到消息的 `references_json`。
7. 前端显示 `[1] 文件名`，点击展开 chunks；历史消息读取保存的快照。原文预览或下载另通过当前租户的文档接口定位。首轮交付文件名与片段溯源，页码定位随文档解析能力扩展。

验收：同步与流式来源一致，多个片段来自同一文件时保持分组；缓存命中仍显示引用，历史恢复后编号一致，页面不显示宿主机目录。

## 5. 部署与真实联调

现状：Java 默认连接 `127.0.0.1:9621`；根 Compose 尚未向后端注入 RAG 环境变量。LightRAG 的 Compose 当前只启动 PostgreSQL/Neo4j。Nginx 尚未针对聊天关闭缓冲。

### 5.1 配置实际连接

1. 定位成员 A 服务的实际进程或容器，记录地址、监听端口和启动方式；从 MES 后端容器检查 `/health` 能否访问。
2. 修改根 `docker-compose.yml` 的 backend.environment，显式注入：

   ```yaml
   MES_RAG_BASE_URL: ${MES_RAG_BASE_URL}
   MES_RAG_INTERNAL_API_KEY: ${MES_RAG_INTERNAL_API_KEY}
   MES_RAG_CONNECT_TIMEOUT: ${MES_RAG_CONNECT_TIMEOUT:-3s}
   MES_RAG_READ_TIMEOUT: ${MES_RAG_READ_TIMEOUT:-60s}
   MES_RAG_STREAM_READ_TIMEOUT: ${MES_RAG_STREAM_READ_TIMEOUT:-120s}
   MES_RAG_SSE_TIMEOUT: ${MES_RAG_SSE_TIMEOUT:-180s}
   ```

3. 根 `.env.example` 补变量说明；实际值写入本地 `.env`。密钥与 RAG 侧 `LR_INTERNAL_API_KEY` 一致。
4. 地址按部署位置确定：同一 Docker 网络用服务别名；宿主机进程用容器可达的宿主机地址；成员 A 另一台电脑用其内网地址。宿主机服务还需监听容器可达的接口并启用内部鉴权。

### 5.2 配置流式代理并跑最小查询

1. 修改 `frontend-web/nginx.conf`，为 `/api/rag/query/stream` 添加独立 location，沿用动态 backend 解析与转发头。
2. 该 location 设置 `proxy_buffering off`、`proxy_cache off`、`gzip off`、`proxy_read_timeout 240s`、`proxy_send_timeout 240s`，清空 SSE 请求的 Connection 升级头。其他路由继续使用原有代理设置。
3. 重建 backend、frontend 容器，经现有登录接口取得测试会话，用 Postman 先调同步接口，再调流式接口：

   ```json
   {"question": "本租户测试文档中的点检要求是什么？", "mode": "naive"}
   ```

4. 检查回答来自当前租户文档，流式正文逐段到达并收到 done；停止生成时观察上游连接释放。

### 5.3 完成真实隔离与重启验收

1. 两个独立测试租户分别写入不同的随机标记和同名文档，执行各自查询与交叉查询；检查回答和引用片段。
2. 现有隔离脚本默认使用 `user_1/user_2`。在独立测试环境准备这些租户，或先给脚本补租户选择参数，再执行真实检查。
3. 先执行 `python scripts/verify_isolation.py --real --only S1,S2,S3,S5,S6 --report <报告路径>`，保存真实 HTTP、PostgreSQL 和 Neo4j 观察结果。
4. 单独补 S4：保存重启前两个租户的文档 ID 集合 → 真实重启 LightRAG → 等待就绪 → 再保存集合 → 调用已有比对函数验证一致。现有 runner 未注入重启样本时只是连续读取两次，需给 CLI 增加前后快照文件输入，保证报告记录真实重启采样。
5. 回归密码/人脸登录，保留现有人脸样本；补双物理电脑、实体摄像头和 HTTPS 条件下的最终验收。

文件管理和图谱界面的交付归属仍待确认；后端已有对应接口，可在聊天主链路跑通后接入。

## 6. 已落地的聊天页面

1. `frontend-web/src/api/rag.ts` 封装状态、会话和消息 API；流式调用用 fetch 携带 MES Token，401 沿用系统登录失效跳转。
2. `src/utils/sse.ts` 处理 UTF-8 跨块、CR/LF/CRLF、多行 data、多个事件同块及半截事件；delta 按文本追加，其余载荷按 JSON 解析。连接关闭且没有终态时显示中断。
3. `src/views/rag/RagChatView.vue` 复用既有布局，侧栏菜单入口 `/rag/chat`；组件分别负责会话、消息、输入和来源。会话行 56px，新建为“最近对话”旁的图标。
4. `src/composables/useRagChat.ts` 管理会话、分页历史、状态与流；浏览器仅按用户保存所选会话 ID，正文从服务器恢复。历史搜索覆盖全部会话分页。
5. 停止、切换会话、离开路由和退出登录均清理生成；退出与改密先使用有效凭证取消。身份变更和迟到响应通过用户及会话状态隔离。
6. 失败记录保留并可重试；网络在 meta 前中断时，重发沿用同一接口、消息 ID 和请求编号。新问题与新重试使用新 UUID。
7. 来源展示文件名和全部原文片段，较窄桌面使用 Element Plus 抽屉；Markdown 以 Vue 文本节点安全渲染。鼠标输入不显示额外边框高亮，Tab 导航保留焦点。
8. `tests/rag-chat.browser.js`（Playwright 隔离接口）53 项通过；`node tests/rag-sse.test.mjs` 8 项通过。覆盖 1201×797、1366×768、1920×920。真实模型联调在 LightRAG 部署后执行。

## 7. 执行顺序与验收节点

| 顺序 | 交付 | 必须通过的检查 |
| --- | --- | --- |
| 1 | 实际地址、容器配置、测试租户和账号绑定 | 两个账号经 MES 完成一次真实查询 |
| 2 | 结构化引用 | 同步/流式/缓存命中都保留编号、文件名、片段 |
| 3 | 会话表、API、历史、取消和重试 | 三轮追问、刷新恢复、跨用户隔离和取消回归 |
| 4 | 聊天页面、路由、流式适配 | 两种桌面视口、事件边界解析和完整交互 |
| 5 | 隔离与联合验收 | 两用户知识库、真实重启、前后端检查及人脸回归 |

文件管理和图谱前端按已确认的交付分工安排；自动开通要求若被确认，放在第 1 节点后单独实施。

## 8. 已完成验证基线

- 最新 Docker 部署：默认后端 1058 项（RAG 113 项）、部署版聊天 UI 51 项、人脸采集 13 组及真实 Nginx/MySQL 接口 24 项通过。两个真实客户端构造器已明确注入，补启动 Bean 回归；验收仅增删临时会话，既有人脸样本保留。
- 正式聊天前端：vue-tsc、Vite 生产构建、53 项浏览器交互及 8 项 SSE 边界检查通过；浏览器接口替身只用于测试。三个桌面视口、鼠标/键盘焦点与设计扫描通过。
- 聊天接口本轮：默认后端 1057 项全部通过，其中 RAG 112 项；真实 Tomcat、H2 执行实际迁移和模拟上游覆盖持久化、身份隔离、多轮、幂等、停止、重试和恢复。
- UI 原型：15 项交互检查、1920×920 与 1366×768 布局及抽屉检查通过；脚本无语法错误，设计扫描仅保留分栏容器的局部留白例外。
- 以下保留 main 整合时的验证基线：
- 后端默认测试：1043 项通过，0 失败、0 跳过；其中 RAG 98 项、真实 Tomcat 人脸 HTTP 30 项、看板快照 3 项。历史看板两项失败已由 main 修复。
- 前端：`npm run build` 通过，包含 `vue-tsc` 与生产构建；保留 Vite 配置弃用告警。
- Python：自有 `tenant/scripts/tests` 共 40 个文件 AST 解析通过；Postman JSON 解析通过。Python 子项目尚未获本机质量任务信任，本轮未执行 pytest。
- 本轮完成源码整合与本地检查；实际 RAG 服务、模型、数据库及 Nginx 链路留待第 5 节联调。

MES 容器与 Nginx 流式代理已更新，24 项真实接口检查通过；下一步部署 LightRAG、核对实际地址和内部密钥、为真实联调账号准备租户及绑定，再运行真实文档问答和模型侧隔离验收。
