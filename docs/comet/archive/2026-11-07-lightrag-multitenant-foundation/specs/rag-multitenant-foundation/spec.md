# 多租户知识库底座（P1）

## 能力概述

本能力回答一个问题：**在真正对外提供 RAG 服务之前，如何用一份可信配置确定"谁是哪个租户、每个租户用哪个 LightRAG 实例、数据落在哪个目录"，并保证配置错误不会悄悄变成跨用户数据混写。**

能力边界是配置、装配与校验，不含 HTTP 路由、数据库连接与模型调用。

## 需求

### R1：固定可运行的 LightRAG 源码

系统 MUST 在 `lightrag-service/LightRAG/` 内提供 LightRAG v1.5.7 源码，且不携带原仓库的 Git 历史。源码必须以可编辑安装方式被本项目使用，使后续二开改动直接落在本仓库内。

约束：

- 目录内不得出现 `.git`；
- 模型与存储的接入参数不写死在源码里，只通过环境变量注入。

### R2：声明式租户配置与目录推导

系统 MUST 从 `config/tenants.yaml` 读取 `user_id → workspace` 映射，并为每个租户推导互不重叠的 `input_dir` 与 `working_dir`，目录规则为 `<base_dir>/<workspace>`。

Scenario: 默认两个租户各自拥有独立目录
  假设配置文件中声明了 2 个租户，workspace 分别为 `ws_001` 与 `ws_002`
  当执行自检 CLI
  那么输出包含两个租户各自的 user_id 与 workspace
  并且上传目录与工作目录分别落在各自的 workspace 子目录下，路径互不相同
  验收：A1

### R3：统一实例注册表

系统 MUST 通过注册表集中管理租户上下文，每个上下文持有该租户专属的 `LightRAG` 实例与 `DocumentManager`；查询未注册租户时必须失败，不得回退到其他实例。

Scenario: 两个租户装配出互不共用的上下文
  假设注册表已完成 2 个租户的装配
  当分别取出两个租户的上下文
  那么每个实例的 workspace 等于该租户配置的 workspace
  并且两个上下文不共享 `LightRAG` 实例，也不共享上传目录
  验收：A5

Scenario: 查询未注册的租户被拒绝
  假设注册表只注册了 `user_1` 与 `user_2`
  当用 `user_unknown` 查询上下文
  那么抛出 `UnknownTenantError`，错误信息包含该 user_id
  并且不返回任何实例
  验收：A6

### R4：启动前隔离校验

系统 MUST 在装配前校验隔离配置，任一项不满足即拒绝启动，并给出可读原因：

- `user_id` 与 `workspace` 均不得重复；
- `workspace` 不得为空，不得包含路径分隔符，必须匹配 `^[A-Za-z0-9_]+$`；
- 各租户推导出的上传目录、工作目录不得相同或互为父子目录；
- 环境变量 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE`、`WORKSPACE` 存在时必须拒绝，因为前两者会覆盖实例级 workspace；
- 租户列表不得为空。

Scenario: 重复的 workspace 被拒绝
  假设两个租户被配置成同一个 workspace
  当执行隔离校验
  那么校验失败，错误信息指出重复的 workspace
  并且不输出任何装配结果
  验收：A2

Scenario: 非法 workspace 被拒绝
  假设某个租户的 workspace 为空字符串或包含 `/`
  当执行隔离校验
  那么校验失败，错误信息指出非法的 workspace 值
  验收：A3

Scenario: 全局 workspace 环境变量被拒绝
  假设运行环境中设置了 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE` 或 `WORKSPACE`
  当执行隔离校验
  那么校验失败，错误信息指出被禁止的环境变量名称
  验收：A4

### R5：本机可独立验收

系统 MUST 提供不依赖数据库与模型服务的本机验收方式：自检 CLI 输出装配结果，单测覆盖配置解析、校验与注册表装配；两者在未配置数据库与模型密钥的机器上必须可运行。

Scenario: 无数据库与模型密钥时单测全绿
  假设本机没有可用的 PostgreSQL / Neo4j，也没有配置模型密钥
  当执行 `pytest lightrag-service/tests`
  那么全部用例通过
  并且过程中不产生数据库连接与模型 API 调用
  验收：A7

### R6：依赖组件的 Docker 编排示例

系统 MUST 提供一份独立于根目录既有编排文件的 Docker Compose 示例，覆盖 LightRAG 依赖的全部外部组件，使后续阶段可直接启动而不必临时搭建环境：

- PostgreSQL：使用带 pgvector 扩展的官方镜像，承载 KV、向量与文档状态存储；
- Neo4j：启用 APOC，供图谱子图查询使用；
- 两者都要声明健康检查与持久化命名卷，发布端口只绑定回环地址；
- 镜像仓库地址通过环境变量注入，默认值与项目已有的国内镜像前缀一致；
- 编排体内不得出现 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE`、`WORKSPACE`。

本需求只要求静态可解析，不要求启动容器或验证连通性；LightRAG 应用容器不属于本需求。

Scenario: 编排文件可静态解析且不含全局覆盖变量
  假设使用默认的镜像变量取值
  当执行 `docker compose -f lightrag-service/docker-compose.yml config`
  那么命令成功退出
  并且渲染结果包含 PostgreSQL 与 Neo4j 两个服务
  并且两者的环境变量中都不出现 `POSTGRES_WORKSPACE`、`NEO4J_WORKSPACE`、`WORKSPACE`
  验收：A8

Scenario: 组件的端口、健康检查与持久化符合约定
  假设编排文件使用默认配置
  当读取渲染后的 PostgreSQL 与 Neo4j 服务定义
  那么两个服务的发布端口都只绑定 `127.0.0.1`
  并且各自声明了健康检查与持久化命名卷
  验收：A9

## 错误处理

- 配置缺失或无法解析：终止并提示文件路径与解析错误位置。
- 校验失败：逐条列出失败原因，进程退出码非 0。
- 模型配置缺省：视为警告而非错误，自检 CLI 注明"未配置模型，跳过真实实例构建"。

## 非范围

- HTTP 路由与租户前缀、内部鉴权、生命周期启停、并发限流（P2）。
- LightRAG 应用容器与镜像构建（P2）。
- 隔离验证场景与跨库核对（P3）。
- Java 业务接入与前端（P4、P5）。
