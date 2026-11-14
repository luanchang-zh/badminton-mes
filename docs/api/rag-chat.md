# RAG 聊天后端接口

更新：2026-11-14。LightRAG 尚未部署；MES 聊天接口已实现，通过隔离上游完成协议与业务验证。

## 登录与知识库状态

所有接口要求 `Authorization: Bearer <MES token>`。用户与租户由后端会话和 `rag_tenant_binding` 确定。

`GET /api/rag/profile` 返回：

```json
{"code":"00000","message":"ok","userTip":null,"data":{"bound":true,"enabled":true,"canManageDocuments":false,"serviceConfigured":false}}
```

已登录用户首次请求此接口时，服务端按用户主键初始化独立的本地绑定，适用于所有角色及新注册用户。重复或并发访问只保留一条绑定，已有租户归属继续使用。`enabled` 表示绑定已启用，`serviceConfigured` 表示本地已配置上游地址和内部密钥。服务未配置时，前端开放本地会话和输入，生成按钮显示“待接入”，跳过文档及生成请求。

## 会话与消息

| 方法与路径 | 请求 | 成功 data |
| --- | --- | --- |
| POST /api/rag/conversations | 可无正文，或 `{"title":"设备点检"}`；标题最多 120 字符 | 会话对象 |
| GET /api/rag/conversations | pageNo=1，pageSize=20（1～100） | PageResult，会话按更新时间倒序 |
| PATCH /api/rag/conversations/{id} | `{"title":"设备点检"}`，非空 | 修改后的会话 |
| GET /api/rag/conversations/{id}/messages | pageNo=1，pageSize=50（1～100） | PageResult，消息按 ID 升序 |
| DELETE /api/rag/conversations/{id} | 无 | null，先取消活跃生成再删除消息及会话 |
| POST /api/rag/messages/{id}/cancel | 无 | 消息对象，已完成消息保持原状态 |
| POST /api/rag/messages/{id}/retry | `{"requestId":"新的请求编号"}` | SSE，生成新的 assistant 消息 |

会话对象字段：`id/title/activeMessageId/createdAt/updatedAt`。消息对象字段：`id/conversationId/role/content/mode/status/replyToId/references/errorTip/createdAt/updatedAt`。

`requestId` 为 8～64 位字母、数字、下划线或连字符；建议每次新发送或重试使用 UUID，网络重发沿用同一个编号。编号在会话内唯一，保存在回答记录上。消息 ID 与请求编号分别承担定位和去重职责。

同一用户只有当前绑定租户的会话可见；变更绑定后，旧租户会话停止访问。其他用户的会话或消息返回同一不存在错误。

## 同步和流式问答

既有 `POST /api/rag/query` 与 `POST /api/rag/query/stream` 继续支持只传 `question/mode` 的单次问答。`question` 非空、最多 2000 字符；mode 缺省 naive，支持 naive/local/global/hybrid/mix。

持久聊天先创建会话，再提交：

```json
{
  "conversationId": 1001,
  "requestId": "736abcc5-c6c9-4f77-863c-744606700dd8",
  "question": "开机前需要检查哪些项目？",
  "mode": "naive"
}
```

同步持久聊天的 data 为：

```json
{
  "conversationId": 1001,
  "userMessageId": 2001,
  "assistantMessageId": 2002,
  "message": {
    "id": 2002,
    "conversationId": 1001,
    "role": "assistant",
    "content": "检查传动机构 [1]",
    "mode": "naive",
    "status": "COMPLETED",
    "replyToId": 2001,
    "references": [{"referenceId":"1","sourceName":"点检规程.md","chunks":["每天检查传动机构"]}],
    "errorTip": null,
    "createdAt": "2026-11-13T10:00:00",
    "updatedAt": "2026-11-13T10:00:02"
  }
}
```

单次同步问答仍返回 `answer/contexts/mode`，新增 `references`。持久同步问答内部消费上游 NDJSON 流，再返回 JSON，因此同样可以通过消息取消接口关闭上游句柄。

## SSE 事件约定

前端使用 fetch POST，携带 MES Token，读取 `text/event-stream`。`delta` 的 data 是文本，其他载荷是 JSON；多行文本需要按 SSE 规范拼接。

| event | data | 语义 |
| --- | --- | --- |
| meta | conversationId/userMessageId/assistantMessageId | 持久会话启动或幂等重放，首先发送 |
| reference_details | RagReference[] | 引用编号、展示文件名、片段列表 |
| references | string[] | 保留原有文本上下文事件 |
| delta | 文本 | 追加回答片段 |
| error | code/message/userTip | 流内失败；连接随后结束 |
| done | 消息对象 | 持久会话终态；检查 status，可为 COMPLETED/CANCELLED/FAILED |

原有无 conversationId 的流式问答不发送 meta，done 仍为 `answer/mode`；结构化来源事件同样可用。重复提交已结束请求时，重放已保存的 meta、来源、内容和 done，不重新调用模型。

## 生成、历史与恢复

- 首次发送保存 user 与 GENERATING assistant，短事务锁定会话活跃消息；长时间模型读取在事务外执行。
- 同一会话生成中再次发送返回 A0409。相同 requestId 的问题和模式必须一致。
- 历史从该会话已完成问答中构建，最多最近 6 轮、正文总计 16000 字符，保持成对裁剪；失败、停止和当前未完成回答不进入历史。
- 历史通过上游 `conversation_history` 传递。当前请求仍以本轮问题检索；指代追问的独立查询改写属于后续 LightRAG 适配。
- 增量定期保存快照，停止保存当前内容为 CANCELLED，异常保存为 FAILED。服务启动时恢复遗留 GENERATING 消息，清除活跃标记；服务关闭时释放上游流。
- 重试只允许 FAILED/CANCELLED 的回答，复用其原问题和模式，创建新的 assistant 并保留旧记录。
- 来源保存为消息快照；展示文件名去掉服务器路径，编号及全部原文片段保留。

## 错误与联调

| 场景 | HTTP／业务码 |
| --- | --- |
| 未登录 | 401／A0230 |
| 未绑定或绑定已禁用 | 400／A0403 |
| 会话或消息不存在、越权、租户变更 | 400／A0440 |
| 会话正在生成、请求编号冲突 | 400／A0409 |
| 无效参数、重试已完成的消息 | 400／A0400 |
| 上游未连接或超时 | 502／C0301，流式启动后通过 error 事件报告 |
| 上游处理错误 | 502／C0302，流内同样使用 error |

LightRAG 未部署时，本地状态查询及有绑定账号的会话管理可使用；生成调用返回真实连接错误并保留失败消息。生产接口没有离线固定回答。

Postman 集合第 6 组包含状态、会话、流式问答、历史、取消及重试示例。上线时应用 Flyway 新迁移，并配置服务地址、内部密钥及 Nginx 流式转发。

## 验证记录

2026-11-13：默认后端 1058 项通过，其中 RAG 113 项，含真实客户端 Bean 创建回归。新增真实 Tomcat HTTP＋H2 执行两份实际迁移，覆盖登录、归属、租户变更、分页、持久历史、重复请求、部分输出取消、失败重试和重启恢复。上游客户端用隔离 HTTP 响应验证历史字段、缓存合并行及结构化引用；LightRAG 与模型调用使用替身。

## 前端接入

正式入口为 MES 菜单“知识库问答” `/rag/chat`，客户端见 `frontend-web/src/api/rag.ts`。已接入会话管理、分页历史、持久 SSE、停止、重试和结构化来源。普通请求沿用系统 Axios 客户端；流式请求使用 fetch Bearer Token，401 跳转登录。网络在 meta 前断开时，重发沿用原请求编号；停止、离开页面、退出和改密会清理生成。

前端生产构建、53 项隔离浏览器检查和 8 项 SSE 边界检查通过。LightRAG 待部署，真实模型及租户隔离联调为下一阶段。

2026-11-13 Docker 更新：前后端已重建，Nginx 对 query/stream 与消息 retry 关闭缓冲并配置 240 秒超时；真实 Nginx＋MySQL 的 24 项检查通过，含两账号归属、会话读写、上游离线错误持久化、重试与清理。部署版聊天 UI 51 项检查通过，接口替身仅用于 UI 回归。LightRAG 真实检索仍待服务部署。
