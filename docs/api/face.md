# 人脸验证后端接口

更新：2026-11-13。识别与特征保存由 InsightFace Server 0.3.1 负责，MES 保存用户绑定并复用现有 Redis 会话。

## 接口

除人脸登录外，均需 `Authorization: Bearer <token>`。管理接口从会话读取本人身份。

| 方法与路径 | 参数 | 成功响应 `data` |
| --- | --- | --- |
| `POST /api/system/auth/face_login` | multipart：`image`，恰好 1 张 | `AuthLoginRespVO`，与密码登录相同，含 token、用户、角色和三个微信绑定字段 |
| `POST /api/system/face/enroll` | multipart：`images`，2～3 张；`consent=true` | `acceptedCount`、`faceCount`、`rejectedImages` |
| `POST /api/system/face/reenroll` | multipart：`images`，2～3 张新照片；再次提交 `consent=true` | 与注册响应相同，替换全部旧样本 |
| `GET /api/system/face/profile` | 无 | `enrolled`、`faceLoginAllowed`、`faceCount`、`maxFaces`、`samples` |
| `POST /api/system/face/samples` | multipart：`images`，至少 1 张 | 与注册响应相同 |
| `DELETE /api/system/face/samples/{faceId}` | 本人样本 ID，1～64 位字母、数字、下划线或连字符 | `null`；删空时自动关闭人脸登录 |
| `DELETE /api/system/face/enrollment` | 无 | `null`；重复关闭成功 |

成功统一返回 `{"code":"00000","data":...}`。`rejectedImages` 元素为 `{index, reason}`，序号从 1 开始、原因用中文。全部拒绝仍返回成功，`acceptedCount=0`，前端应提示注册未完成。`samples` 元素为 `{faceId, createdAt}`；未注册时为空数组。

所有已登录用户（包括 ADMIN）均可查询、注册、重新采集、追加和删除本人样本，与密码、人脸或微信登录方式无关。启用账号完成采集后均可使用人脸登录。`DELETE /api/system/users/{id}` 先删除 InsightFace Person，再在数据库事务中删除用户和授权关系。

## 设置页接入约定

1. 进入设置先调用 `GET /api/system/face/profile`。`faceLoginAllowed` 对所有已登录用户返回 `true`；`enrolled=false` 时显示采集引导，样本数为 0、列表为空。绑定对应 Person 已不存在或没有有效样本时也返回未采集。
2. `enrolled=true` 时展示已开通状态、`faceCount` 和 `samples`（含各样本入库时间），提供重新采集和关闭入口。
3. 首次采集调用 `/enroll`；已采集用户调用 `/reenroll`。两者都上传 2～3 张照片并提交 `consent=true`，前端现有向导采集三张。
4. `/reenroll` 先校验同意、张数和文件格式，再在同一用户锁内删除旧 Person、关闭旧绑定、创建新 Person 与新绑定，重新记录同意时间。返回的 `faceCount` 仅统计新样本，支持原来已满 5 张的用户重新采集，也支持未注册用户直接调用。
5. 重采集属于清空后重建：参数校验或删除旧 Person 失败时保留旧资料；旧特征删除成功后，新照片全部拒收或新建失败时保持未采集状态，需要重试。前端在提交前应明确提示替换旧特征，并在响应失败后重新查询状态。全部拒收返回 `acceptedCount=0, faceCount=0`；部分接收返回实际数量与拒绝原因。

重新采集沿用当前用户身份，忽略请求中的 `userId`、`personId`。注册、重新采集、追加、删除和关闭共用单用户互斥锁，默认过期时间 5 分钟，覆盖重建及补偿流程的多次人脸服务调用；每次操作在 `finally` 中释放锁。

## 校验与错误码

上传按魔数接受 JPEG、PNG、WebP；单张 10MB、单请求 32MB，样本默认最多 5 张。登录自动采样裁取取景轮廓附近画面、长边上限 640px，JPEG 质量 0.9。整轮验证上限 30 秒，前端请求使用本轮剩余时间；搜索连接上限 1 秒、读取上限 30 秒，更短的自定义配置仍生效。管理操作继续使用配置的 3 秒／30 秒。

整合 RAG 后，容器 multipart 单文件默认 25MB，以覆盖 20MB 文档上传；总请求默认 32MB。人脸四个上传入口及照片转换仍单独校验 10MB，超限返回 A0702。

| 情况 | HTTP／错误码 |
| --- | --- |
| 未登录访问管理接口 | 401／A0230 |
| 缺上传字段、张数不符、未同意、非法 consent、错误 Content-Type、损坏 multipart、非法样本 ID | 400／A0400 |
| 文件类型错误／上传超限 | 400／A0701、A0702 |
| 照片无法识别人脸／身份识别未通过 | 400／A0700、A0200 |
| 停用账号人脸登录 | 400／A0202 |
| 重复注册／未注册／样本不存在或属于他人／样本超限 | 400／A0111、A0440、A0402、A0425 |
| 同用户写操作占锁／IP 失败达到上限 | 400／A0503、A0501 |
| 人脸服务不可用／读取超时 | 502／C0001、C0200 |

经 Nginx 上传超过 32MB 的请求由 Nginx 返回 HTTP 413。IP 失败计数默认 20 次／15 分钟，成功不清零，C 类故障不计数。识别使用 0.4 阈值和 0.05 分差；响应省略相似度、候选人和特征。

关闭与删除用户沿用严格清理规则：即使没有绑定，也尝试删除残留 Person；InsightFace 故障时返回 C 类错误，供恢复后重试。系统采用照片比对，活体检测尚未启用。

## 验证记录

- 2026-11-13 main 整合：后端默认回归 1043 项全部通过，其中 RAG 98 项、真实 Tomcat 人脸 HTTP 30 项；四个人脸上传入口的 11MB 超限检查仍通过。看板快照 3 项通过，历史两项失败已由 main 修正。本轮完成本地验证，运行容器留待后续部署。
- 2026-11-12 前端重采集接入：已开通结果弹窗新增入口，复用三姿态采集向导，重新同意后提交 `/reenroll`。取消不写入，全部拒收保留照片，失败时后台查询真实状态且不以旧注册状态显示更新成功。前端构建和 22 组浏览器回归通过；Docker 前端已更新，部署版 13 组采集回归和两种桌面视口检查通过。测试相机及接口隔离，现有真实人脸样本保留。
- 2026-11-12 本地部署与持久测试数据：最新后端已重建部署，经 Nginx 确认 ADMIN 的 `faceLoginAllowed=true`。`operator_demo`（userId=5）录入用户1的 A1/A3；新建操作工账号 `face_user2`（userId=17）录入用户2的 C1/C2；均通过正式 `/enroll` 接口录入，各 2 张、无拒收。照片在内存中缩至长边 1280px、JPEG 质量 0.9，原文件保留。两组均入库后，用未入库的 A2/C3（长边 640px）经 Nginx 登录，分别命中 userId=5/17。操作会话已退出；按用户要求保留这两个账号及其样本供继续测试。
- 2026-11-12：开放所有角色的人脸注册、查询、追加与登录，新增 `/reenroll`。离线相关回归 159 项通过，其中服务规则 53 项、真实 Tomcat HTTP 30 项；另以真实认证和人脸业务服务串起七种角色的密码登录、状态查询、首次采集、重采集和人脸登录流程，外部存储及 InsightFace 使用测试替身。覆盖本人身份、重采集顺序与锁、输入校验、全部/部分拒收、删除或新建失败、未注册直接重采集及四个上传入口的 11MB 限制。本轮尚未部署：本地 Docker daemon 未运行。
- 正式前端已接入 v7 采集向导，从用户菜单「人脸登录设置」或工作台提醒进入；一次提交三张居中裁切的 JPEG（长边至多 960px、质量 0.9）及 `consent=true`。提交前可单张重拍，全部拒收保留照片供重试，部分接收展示实际数量和拒绝原因。关闭/后台/确认页释放相机，成功/卸载清理临时图片。27 组模拟浏览器场景通过，Docker 部署版 18 组复测通过；后端接口契约保持现状。
- 新增 `FaceApiHttpTest` 的 23 项真实 Tomcat HTTP 测试，覆盖六个接口的路由、鉴权、当前用户身份、响应字段、异常映射及三个上传接口各自的 11MB 超限处理。
- 人脸及关联认证、用户服务共 112 项测试通过。登录时间仅条件更新有效绑定，覆盖并发关闭后旧实体回写的回归。
- 隔离 Collection、三个临时账号与真实 MySQL／Redis／InsightFace 完成 46 项检查：A 组 2 张成功、第 2 张被拒；A2 和 C3 均以未入库照片登录；并发追加总数保持 5；越权删除、停用、ADMIN、关闭、删空、重新注册及删除用户同步均通过。临时库、账号、绑定和 Redis 数据已清理。
- 使用独立来源 IP 请求验证第 21 次被限流、另一 IP 可登录；局域网双机经 Nginx 的验收留待现场执行。
- 全量回归 898 项，896 项通过；`KanbanSnapshotServiceImplTest` 的两项既有失败与交接记录一致，涉及 `overview`／`overviewForKanban` 的测试预期。
- Docker 后端已重建部署。经现有 Nginx 与部署版后端的 15 项冒烟检查通过，含密码登录、鉴权、错误请求类型、非法 consent、11MB 超限、无人脸识别和关闭幂等；部署验证临时数据已清理。
- 后续时限调整为 30 秒：前端构建、9 组浏览器回归及 21 项后端相关测试通过；超过原 5 秒的响应可正常登录，30 秒截止、后台／切换取消及迟到响应保护正常，Docker 前后端已更新。

离线执行相关测试：

```bash
cd backend-java
bash ./gradlew --offline --no-daemon --max-workers=2 test \
  --tests '*Face*' --tests '*ImageTypeUtilsTest' \
  --tests '*AuthServiceImplTest' --tests '*UserServiceImplTest'
```
