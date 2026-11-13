# P4：Java RAG 租户映射与客户端

# 目标

在 `backend-java` 提供两个底层能力，供 P5 的对外接口复用：

- **租户解析**：从当前登录用户确定其对应的 LightRAG 租户，前端不参与决定；
- **HTTP 客户端**：按租户前缀调用 LightRAG 的文档、查询与图谱接口，并统一处理错误与超时。

本阶段不暴露任何对外接口。

# 范围

## 目录结构

### 新建

- `backend-java/src/main/resources/db/migration/V2026110801__create_rag_tenant_binding.sql` — 租户绑定表，`user_id` 与 `tenant_id` 均唯一。
- `backend-java/src/main/resources/db/dev-migration/R__Insert_Rag_Tenant_Binding.sql` — 演示绑定数据。
- `module/rag/constants/RagErrorCodeConstants.java` — 模块错误码：未绑定、已禁用、上游不可达、上游返回错误、上传超限、非法租户标识。
- `module/rag/enums/RagDocumentStatus.java` — 上游文档状态归一化为四种：处理中、已完成、失败、未知。
- `module/rag/config/RagProperties.java` — `mes.rag.*` 配置绑定。
- `module/rag/dal/entity/RagTenantBinding.java` — 绑定实体。
- `module/rag/dal/repository/RagTenantBindingRepository.java` — 按 `user_id` 查询启用中的绑定。
- `module/rag/service/RagTenantResolver.java` — 从登录上下文解析租户，不提供接受外部 tenant 参数的重载。
- `module/rag/client/RagClient.java` — 同步 HTTP 客户端，统一前缀与内部密钥，统一错误转换。
- `module/rag/client/dto/` — 上游请求与响应 DTO：上传结果、文档摘要、查询结果、图谱节点与边。
- `test/.../module/rag/service/RagTenantResolverTest.java` — 租户解析各分支。
- `test/.../module/rag/client/RagClientTest.java` — 路径前缀、密钥头、非法租户、错误转换。
- `test/.../module/rag/enums/RagDocumentStatusTest.java` — 状态归一化。
- `test/.../module/rag/config/RagPropertiesTest.java` — 配置绑定与默认值。

### 修改

- `backend-java/src/main/resources/application.yml` — 新增 `mes.rag` 配置段。

### 删除

无

### 明确不建

- 不建 Controller 与对外 API — 属于 P5。
- 不建 `RagDocumentService` / `RagQueryService` 等业务编排层 — P4 只交付 Resolver 与 Client，业务编排在 P5 按接口需要再加，避免为尚不存在的调用方提前分层。
- 不引入 WebFlux — 现有 `ErpRemoteClient` 已确立同步 `RestClient` 范式，SSE 需求到 P5 再评估。
- 不建租户绑定的管理接口 — 演示阶段用迁移数据维护。

# 非目标

- 不连接真实 MySQL、Redis 与 LightRAG 服务；单测用 Mockito 与 `MockRestServiceServer`。
- 不实现流式输出（SSE）。
- 不做多租户绑定关系的增删改查界面。
- 不改动既有的 MES 业务模块。

# 约束与不变量

- 租户标识的唯一来源是登录上下文加绑定表；**不得存在任何接受前端传入 tenant 的方法**。
- `RagClient` 内部必须再次校验租户标识格式（`^[A-Za-z0-9_]+$`），作为路径注入的第二道防线。
- 内部密钥只从配置读取，不得出现在日志或异常信息中。
- 上游的 4xx、5xx、连接失败与超时必须统一转换为带模块错误码的 `ServiceException`，不得向调用方暴露内部地址。
- 单测不得依赖 MySQL、Redis 或真实 LightRAG 服务。
- 代码规范：单文件不超过 400 行，单函数不超过 50 行，公开方法带 Javadoc。

# 关键决定

- **沿用同步 `RestClient`**：既有 `ErpRemoteClient` 已用此范式，RAG 调用同样是请求-响应式，无需为 P4 引入 WebFlux。
- **不做业务编排层**：P4 的调用方是 P5，现在不知道接口形状，提前抽象会做错。只交付 Resolver 与 Client 两个确定需要的能力。
- **状态归一化放在 P4**：上游状态字符串的差异是上游事实，越靠近边界处理越好，避免 P5 每个接口各自判断。

# 验证期望

- `./gradlew test` 全绿，不触发 `integrationTest` 与 `stressTest`；
- 新增类的单测覆盖率不低于 85%。

# 验收示例

- 当前登录用户已绑定且启用时，租户解析器返回绑定表里的租户标识。
- 当前登录用户未绑定租户时，解析器抛出「租户未绑定」错误码。
- 绑定存在但被禁用时，解析器抛出「租户已禁用」错误码。
- 无登录上下文时，解析器抛出未授权错误，而不是返回空值或默认租户。
- 客户端发起请求时，请求路径包含该租户的前缀，且请求头包含内部密钥。
- 传给客户端的租户标识含路径分隔符或非法字符时，客户端在发请求前即拒绝。
- 远端返回 500 时，客户端抛出带「上游返回错误」错误码的服务异常，异常信息不包含上游原始报文。
- 远端连接超时时，客户端抛出带「上游不可达」错误码的服务异常。
- 上游状态字符串 `processed`、`completed`、`success`、`done` 都归一化为「已完成」。
- 上游返回未知状态字符串时归一化为「未知」，不抛异常。
- 未配置 `mes.rag.base-url` 时使用默认回环地址。
