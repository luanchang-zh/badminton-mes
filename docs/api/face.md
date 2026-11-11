# 人脸验证后端接口

更新：2026-11-11。识别与特征保存由 InsightFace Server 0.3.1 负责，MES 保存用户绑定并复用现有 Redis 会话。

## 接口

除人脸登录外，均需 `Authorization: Bearer <token>`。管理接口从会话读取本人身份。

| 方法与路径 | 参数 | 成功响应 `data` |
| --- | --- | --- |
| `POST /api/system/auth/face_login` | multipart：`image`，恰好 1 张 | `AuthLoginRespVO`，与密码登录相同，含 token、用户、角色和三个微信绑定字段 |
| `POST /api/system/face/enroll` | multipart：`images`，2～3 张；`consent=true` | `acceptedCount`、`faceCount`、`rejectedImages` |
| `GET /api/system/face/profile` | 无 | `enrolled`、`faceLoginAllowed`、`faceCount`、`maxFaces`、`samples` |
| `POST /api/system/face/samples` | multipart：`images`，至少 1 张 | 与注册响应相同 |
| `DELETE /api/system/face/samples/{faceId}` | 本人样本 ID，1～64 位字母、数字、下划线或连字符 | `null`；删空时自动关闭人脸登录 |
| `DELETE /api/system/face/enrollment` | 无 | `null`；重复关闭成功 |

成功统一返回 `{"code":"00000","data":...}`。`rejectedImages` 元素为 `{index, reason}`，序号从 1 开始、原因用中文。全部拒绝仍返回成功，`acceptedCount=0`，前端应提示注册未完成。`samples` 元素为 `{faceId, createdAt}`；未注册时为空数组。

管理员可查询、删除本人已有样本并关闭人脸登录；注册、追加和人脸登录受限。`DELETE /api/system/users/{id}` 先删除 InsightFace Person，再在数据库事务中删除用户和授权关系。

## 校验与错误码

上传按魔数接受 JPEG、PNG、WebP；单张 10MB、单请求 32MB，样本默认最多 5 张。登录采样沿用前端长边 1280px、JPEG 质量 0.9；搜索连接上限 1 秒、读取上限 2 秒，管理操作继续使用配置的 3 秒／30 秒。本机联调中直接上传 C1 原图触发了 `C0200`，按采样规格缩小后通过。

| 情况 | HTTP／错误码 |
| --- | --- |
| 未登录访问管理接口 | 401／A0230 |
| 缺上传字段、张数不符、未同意、非法 consent、错误 Content-Type、损坏 multipart、非法样本 ID | 400／A0400 |
| 文件类型错误／上传超限 | 400／A0701、A0702 |
| 照片无法识别人脸／身份识别未通过 | 400／A0700、A0200 |
| 停用账号／ADMIN 人脸登录 | 400／A0202、A0220 |
| ADMIN 注册或追加 | 403／A0301 |
| 重复注册／未注册／样本不存在或属于他人／样本超限 | 400／A0111、A0440、A0402、A0425 |
| 同用户写操作占锁／IP 失败达到上限 | 400／A0503、A0501 |
| 人脸服务不可用／读取超时 | 502／C0001、C0200 |

经 Nginx 上传超过 32MB 的请求由 Nginx 返回 HTTP 413。IP 失败计数默认 20 次／15 分钟，成功不清零，C 类故障不计数。识别使用 0.4 阈值和 0.05 分差；响应省略相似度、候选人和特征。

关闭与删除用户沿用严格清理规则：即使没有绑定，也尝试删除残留 Person；InsightFace 故障时返回 C 类错误，供恢复后重试。系统采用照片比对，活体检测尚未启用。

## 验证记录

- 新增 `FaceApiHttpTest` 的 23 项真实 Tomcat HTTP 测试，覆盖六个接口的路由、鉴权、当前用户身份、响应字段、异常映射及三个上传接口各自的 11MB 超限处理。
- 人脸及关联认证、用户服务共 112 项测试通过。登录时间仅条件更新有效绑定，覆盖并发关闭后旧实体回写的回归。
- 隔离 Collection、三个临时账号与真实 MySQL／Redis／InsightFace 完成 46 项检查：A 组 2 张成功、第 2 张被拒；A2 和 C3 均以未入库照片登录；并发追加总数保持 5；越权删除、停用、ADMIN、关闭、删空、重新注册及删除用户同步均通过。临时库、账号、绑定和 Redis 数据已清理。
- 使用独立来源 IP 请求验证第 21 次被限流、另一 IP 可登录；局域网双机经 Nginx 的验收留待现场执行。
- 全量回归 898 项，896 项通过；`KanbanSnapshotServiceImplTest` 的两项既有失败与交接记录一致，涉及 `overview`／`overviewForKanban` 的测试预期。
- Docker 后端已重建部署。经现有 Nginx 与部署版后端的 15 项冒烟检查通过，含密码登录、鉴权、错误请求类型、非法 consent、11MB 超限、无人脸识别和关闭幂等；部署验证临时数据已清理。

离线执行相关测试：

```bash
cd backend-java
bash ./gradlew --offline --no-daemon --max-workers=2 test \
  --tests '*Face*' --tests '*ImageTypeUtilsTest' \
  --tests '*AuthServiceImplTest' --tests '*UserServiceImplTest'
```
