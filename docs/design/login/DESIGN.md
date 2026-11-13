# 登录页设计稿

## 2026-11-13 提交素材压缩

经用户确认，仅保留两张正式页面必需的新图片：`src/assets/images/face-pose-examples.webp`（228928 字节）和 `src/assets/images/sidebar-mes-logo-refined.webp`（66174 字节）。沿用原分辨率，使用 WebP Q95 编码，合计 295102 字节，约为原 PNG 的 9.8%。登录、侧栏、人脸向导和设计预览已改用 WebP；截图、设计稿、未使用图片及原 PNG 留在本地并由 Git 忽略。

---

## 2026-11-13 登录页顶部胶囊使用 MES 图标

顶部胶囊最左侧图标复用已确认的 `src/assets/images/sidebar-mes-logo-refined.png`，保留 MES 字样、完整传送带、显示器等全部图案。以 43 个画布单位的深蓝圆角框居中展示 69 单位的图片，裁去外围背景留白，随现有登录页比例缩放。

前端 vue-tsc / Vite 构建及 Docker 部署通过。1920×920、1366×768 下的登录与注册页面共四种状态检查通过，图标加载、容器边界、文字间距及页面横向边界正常，浏览器无未捕获异常。[顶部胶囊截图](face/login-capsule-mes-icon.png)、[登录页截图](face/login-capsule-mes-page.png)。

---

## 2026-11-13 MES 图标适配深色侧栏

按用户要求完整保留 MES 字样、传送带及三个滚轮、显示器及三个柱形，齿轮、羽毛球和橙色弧线一并保留。去掉白底、圆角底板和底部角饰，灰色主体改成浅银色。透明导出出现杂点，最终使用深蓝背景与侧栏融合；正式图片为 `src/assets/images/sidebar-mes-logo-refined.png`，原图保留。

40px 显示框内居中展示 64px 图片，仅裁去外围背景留白。Docker 前端构建、部署版展开/折叠加载与边界检查及 7 组提示回归通过，浏览器无未捕获异常；欢迎提示仍约 2.02 秒开始退出。[展开截图](face/refined-icon-expanded.png)、[折叠截图](face/refined-icon-collapsed.png)、[内置 ImageGen 提示词与素材记录](mes-logo-refined-imagegen.md)。

---

## 2026-11-13 使用用户提供的 MES 图标

侧栏顶部直接采用用户最新提供的 MES PNG（齿轮、羽毛球、传送带和监视器图案），正式素材为 `src/assets/images/sidebar-mes-logo.png`，原始尺寸 1254×1254。显示尺寸沿用 40px，与项目标题并排；折叠侧栏单独保留图标。

附件与项目素材逐字节核对一致；Docker 前端构建与部署通过，展开和折叠两态均确认图片加载、40×40 尺寸及容器边界，浏览器无未捕获异常。[展开截图](face/provided-icon-expanded.png)、[折叠截图](face/provided-icon-collapsed.png)。

---

## 2026-11-13 欢迎提示缩短至 2 秒

按用户最新要求，将欢迎提示的固定关闭计时从 5000ms 改为 2000ms。沿用原生绿色样式、窗口中心对齐、悬停不延长及人脸提示平滑补位。

Docker 前端已重建部署至 80 端口，7 组相关浏览器检查通过，覆盖两种桌面视口、侧栏折叠、2 秒截止、手动关闭和减少动态效果。部署版实测约 2021ms 开始退出，提示补位无回跳；账号 API 使用隔离替身。

---

## 2026-11-13 原生欢迎提示、窗口居中与 M 标识

依据用户澄清，欢迎提示恢复 Element Plus 原生绿色成功样式，包括原生尺寸、字体和成功图标。人脸采集提醒沿用现有白底蓝色面板。两者以浏览器窗口 `50%` 为横向基准，中心在同一条垂直线上；保留各自宽度，侧栏展开和折叠均不影响位置。继续保留 20px 上下间距、欢迎提示固定 5 秒关闭和人脸提示 280ms 平滑补位。

侧栏图标采用蓝底白色 M 字形，使用 `src/assets/mes-mark.svg`，40px 显示，展开时与项目标题并排，折叠时单独保留。几何矢量源文件约 253 字节，可清晰缩放；旧羽毛球位图作为历史本地素材保留，正式页面改为引用新 SVG。

验证：前端 vue-tsc / Vite 构建、7 组提示/品牌与 9 组认证检查通过。Docker 前端已更新至 80 端口，部署版 7 组复测通过；1920×920 与 1366×768 覆盖原生绿色样式、窗口中心线、不同宽度、侧栏折叠、图标加载、5 秒关闭、手动关闭和减少动态效果。部署版欢迎提示约 5023ms 开始退出，人脸提示从顶部 74px 平滑移至 16px，记录 14 个中间帧。测试使用真实视图与隔离账号 API。[提示对齐截图](face/prompt-stack-before.png)、[侧栏与单提示截图](face/brand-sidebar-and-notice.png)。

---

## 2026-11-12 侧栏品牌图标与统一登录提示

用户提供的羽毛球图经内置 ImageGen 去除浅青背景和装饰弧线，保留白色羽毛与蓝色球带。RGBA PNG 作为 `src/assets/images/sidebar-shuttlecock.png` 接入侧栏顶部，以 40px 显示，与「羽毛球 MES」并排；折叠侧栏保留图标。[素材与完整提示词记录](sidebar-brand-imagegen.md)。

欢迎消息与人脸采集提示共用 `src/styles/account-notices.css`：同为 720px 上限、白底、10px 圆角、细灰边框、轻阴影和蓝色线性图标，文本左边线及外框对齐；两条提示之间保留 20px 间距。居中位置按既有侧栏宽度令牌计算，侧栏折叠后同步调整。人脸提示保留此前的 280ms 平滑补位。

欢迎消息明确采用固定 5 秒关闭计时，鼠标悬停不延长；手动关闭同时清理计时器。新增可键盘操作的关闭按钮，并保留 Escape 和减少动态效果支持。

验证：vue-tsc / Vite 构建、7 组提示/品牌和 9 组认证检查通过；Docker 前端已重建至 80 端口，部署版 7 组复测通过。1920×920 与 1366×768 检查统一宽度、左边线、背景及圆角，覆盖 5 秒截止、悬停、手动关闭、补位、无欢迎消息、侧栏折叠与图标加载。部署版欢迎消息约 5011ms 开始退出；人脸提示从顶部 98px 平滑移至 16px，记录 14 个中间帧。登录视图与欢迎消息真实渲染，账号 API 使用隔离替身。[双提示截图](face/prompt-stack-before.png)、[侧栏与单提示截图](face/brand-sidebar-and-notice.png)。

---

## 2026-11-12 欢迎消息与采集提醒平滑补位

保留 Element Plus 欢迎消息和现有人脸采集提醒的外观。提醒在顶部居中消息下方留出 20px 间距；欢迎消息退出后，提醒移至顶部 16px；恢复已有会话而没有欢迎消息时，直接显示在首位。消息高度与占位通过可见期间的 MutationObserver / ResizeObserver 同步，悬停暂停和手动关闭均沿用欢迎消息的真实生命周期。

位置移动放在外层容器，采用 280ms `cubic-bezier(.455, .03, .515, .955)` transform 过渡；内部提醒保留 200ms 淡入淡出，避免两种位移互相覆盖。减少动态效果时关闭过渡。进入采集、稍后关闭和组件卸载时断开观察并释放消息元素引用。

验证：vue-tsc / Vite 构建、6 组提示动效与 9 组认证回归通过；Docker 前端已重建，部署版 6 组复测通过。使用真实登录视图与 Element Plus 欢迎消息，账号 API 采用隔离替身；1920×920 与 1366×768 覆盖自动退出、悬停暂停、手动关闭、无欢迎消息、减少动态效果及资源释放。部署版逐帧采样从顶部 74px 平滑移至 16px，记录 14 个中间帧，位置单调且无回跳。脚本为 `tests/face-prompt.browser.js`，[同时显示截图](face/prompt-stack-before.png)、[上移完成截图](face/prompt-stack-after.png)。

---

## 2026-11-12 已开通用户重新采集入口

已开通状态的结果弹窗在「完成」旁新增描边按钮「重新采集人脸」，沿用现有 Element Plus 按钮、蓝色主操作、620px 弹窗和 v7 向导。点击后进入准备页，重新勾选同意再打开摄像头；确认页显示「确认并更新」及替换原有信息的说明，提交三张照片至 `/api/system/face/reenroll`。

进入向导与取消操作不修改服务端样本。重采集成功显示实际有效数量及部分拒收原因；全部拒收保留照片供重拍或重试，状态归为未采集。接口失败时在后台刷新状态，保留确认页中的照片；旧的已开通状态不会被当成本次更新成功。提交期间锁定关闭与重复操作，沿用现有摄像头和图片资源清理。

验证：前端 vue-tsc / Vite 构建通过；13 组采集与重采集、9 组认证回归共 22 组通过。Docker 前端已重建部署到 80 端口，部署版 13 组采集回归复测通过；1920×920、1366×768 检查按钮、键盘进入、同意门槛、取消与弹窗边界，均通过，浏览器无未捕获异常。相机和 API 使用隔离测试替身，包含真实 JPEG 编码，未修改已录入的两组真实样本。[结果弹窗截图](face/enrollment-recapture-settings.png)、[准备页截图](face/enrollment-recapture-intro.png)、[部署版截图](face/enrollment-recapture-deployed.png)。

---

## 2026-11-11 v7 正式接入与本地部署

用户确认按向导弹窗实施。正式系统已替换旧双栏采集界面：工作台提醒和用户菜单「人脸登录设置」打开 620px 向导，同意后开启真实摄像头，依次采集三个角度，在确认页逐张重拍或提交已有后端注册接口。已开通、账号受限、状态加载失败和关闭删除特征入口继续保留。

实现位于 `src/components/face/FaceEnrollment.vue`、`FaceEnrollmentWizard.vue` 和 `face-enrollment.css`。摄像头画面按取景区居中裁切，JPEG 长边至多 960px，质量 0.9；视频、姿态示例和照片预览统一镜像。示例素材迁入正式 assets，作为页面必需资源加入 Git 保留规则。注册错误在确认页展示，全部拒收可重拍/重试，部分接收按实际数量与原因显示结果，重复注册会刷新服务端状态。

生命周期覆盖关闭、后台、设备断开、授权迟到、编码超时及编码迟到；进入确认页即释放相机，成功或卸载清理 Blob URL。提交期间禁用关闭和重复操作，网络失败保留照片供重试。Chrome 1440×900 和 1366×768 检查通过，[正式组件截图](face/enrollment-production-capture.jpg)已保存；浏览器验收相机与接口使用隔离测试替身。

验证：前端 vue-tsc / Vite 构建通过；9 组认证、9 组采集边界及 9 组 30 秒登录场景共 27 组浏览器回归通过。Docker 前端已重建部署到 [本地系统](http://localhost)，部署版认证与采集 18 组复测通过；真实 Nginx → 后端职位接口返回 HTTP 200 / 00000。保留既有 advancedChunks 弃用提示。脚本为 `tests/face-auth.browser.js`、`face-enrollment.browser.js` 和 `face-auto-login.browser.js`；脚本自动使用当前目标站点地址。

性能：正式向导拍摄态静置 10 秒，内部节点数 47 → 47、Canvas 0 → 0、照片 URL 0 → 0，保持一条必要的视频轨道；结束静置时无运行中动画。关闭后轨道、照片 URL 和弹窗动画均为 0。姿态帮助图片加载及尺寸正常，无溢出；Chrome 控制台无 error / warn。双物理电脑和实体摄像头的现场验收沿用既有待办。

---

## 2026-11-11 自主设计：聚焦式向导弹窗 v7

用户明确解除早前的页面、布局及风格限制，允许弹窗。本版采用居中向导：同意后依次拍摄三个角度，用椭圆取景区突出当前动作；示例按需展开，拍完统一确认，支持单张重拍与完成反馈。使用现有 Vue / Element Plus 制作[完整交互预览](http://127.0.0.1:5174/design/face-capture-modal/index.html)，源码在 `frontend-web/design/face-capture-modal/`。v7 已确认并按上方记录接入正式系统，预览保留作对照。

18 项流程检查、两种桌面视口、四种主要状态布局、静置与三次开关循环、减少动态效果、类型和独立构建检查通过。模板来源、具体行为及截图见[本轮设计与验收记录](face/face-modal-v7-review.md)。本轮继续交付设计预览，采集画面使用示例素材。

---

## 2026-11-11 模板选材与可交互采集预览 v6

按用户要求使用 direct-frontend-design 重新美化：筛选七份免费模板，结合 shadcn MCP Card / Sidebar / Checkbox 示例和 shadcn-vue Stepper，采用紧凑的工业蓝白设置页方向。复用项目 Vue、Element Plus、导航风格及已生成的三姿态素材，完成[可交互预览](http://127.0.0.1:5174/design/face-settings/index.html)与[模板对照页](http://127.0.0.1:5174/design/face-settings/references.html)。源码位于 `frontend-web/design/face-settings/`，v6 留作历史方案，后续以 v7 方向评审。

调整镜头、标题、示例及底部操作比例，保留右侧进度和单卡片，删除重复状态说明。三种桌面视口、12 项 Playwright 预览交互、严格类型检查、独立构建和静置/页面回访检查通过。截图、模板来源、设计令牌与详细结果见[本轮评审记录](face/face-settings-v6-review.md)。本轮为设计预览，未接入正式采集页面。

---

## 2026-11-11 右侧进度与单卡片工业后台版

接续原聊天中断的修改，使用内置 ImageGen 完成[页面 v5](face/proposals-20261111/face-personal-settings-v5.png)：将三步进度移到镜头右侧，镜头、进度和下方三姿态示例全部收进同一张白色卡片。主内容区保持纯白，沿用 MES 深蓝侧栏、Element Plus 蓝色操作按钮、细灰边框和小圆角。标题回到后台页面的常规层级，保留当前动作、同意入口和拍摄操作。

复用 v4 的人物与[三姿态模板](face/proposals-20261111/face-pose-examples-v1.png)，左右方向按人物自身标注。免费 Prompt Library 的 `technical-system-onboarding` 只提供结构与风格参考；颜色、字体和控件依照项目现有样式。Superdesign CLI 离线预检因未缓存而失败，已改读本地安装的官方免费参考库。本轮未执行 Superdesign Credits 生成，未引入组件依赖。[完整 ImageGen 提示词](face/proposals-20261111/face-personal-settings-v5-prompt.md)已保存。

v5 已由上方 v6 交互预览接替，v4、v5 保留作历史对照。目视核对确认右侧进度、单卡片边界、下方示例、纯白背景及简洁文案符合本轮要求。Chrome 已打开本地 1568×1003 PNG 并确认加载完成。本轮交付为静态稿；页面交互、媒体生命周期和运行性能将在实现阶段验证。

---

## 2026-11-11 个人设置中的采集页面

用户确认继续评审设计稿。本版将人脸采集归入「个人设置」，主内容区使用纯白背景，三步进度紧贴镜头左侧，正面、左转头、右转头示例放在镜头下方。页面保留当前动作与必要操作，沿用精简文字要求。

使用内置 ImageGen 生成同一人物的[三姿态模板](face/proposals-20261111/face-pose-examples-v1.png)，并据此完成[页面 v4](face/proposals-20261111/face-personal-settings-v4.png)。左右转头均按示例人物自身方向标注。[完整提示词](face/proposals-20261111/face-personal-settings-v4-prompts.md)已保存。v4 已由上方 v5 修订版接替。

---

## 2026-11-11 精简采集页面

按用户反馈重新核对完整全局规范，删除副标题、账号行、镜头状态、进度数字、重复标题及说明卡片。当前[独立页面 v3](face/proposals-20261111/face-capture-page-v3.png)只保留镜头、当前动作提示、两张样本缩略图、同意入口及采集按钮。[ImageGen 提示词](face/proposals-20261111/face-capture-page-v3-prompt.md)已保存，v2 留作历史记录。

---

## 2026-11-11 人脸采集改为独立页面

按用户最新反馈，人脸登录设置与采集流程统一放在独立页面，工作台入口直接跳转。页面使用正常的 MES 导航和内容区，顶部展示当前账号与「返回工作台」，左侧为镜头、样本预览及采集动作，右侧为步骤、拍摄提示和同意说明。

使用内置 ImageGen 将初版采集稿修订为[独立页面 v2](face/proposals-20261111/face-capture-page-v2.png)，以现有稿为视觉参考；[完整提示词](face/proposals-20261111/face-capture-page-v2-prompt.md)已保存。当前优先评审此版，以下弹窗稿作为历史方案保留。本轮交付为静态设计预览。

---

## 2026-11-11 30 秒验证与人脸采集设计预览

登录自动验证上限由 5 秒改为 30 秒，从摄像头就绪开始计算，包含对齐、编码、请求和重采；内部预留 200ms 呈现结果。前端请求采用本轮剩余时间，后端搜索连接上限保持 1 秒、读取上限放宽到 30 秒，更短的自定义读取超时仍生效。成功、明确失败、切换或进入后台仍立即结束并释放相机。

本轮只实现时限调整。进入系统后的引导弹窗与采集界面先提供两张静态设计稿，沿用白色面板、深绿主操作和现有 MES 工作台背景：

- [引导弹窗 v1](face/proposals-20261111/face-invitation-v1.png)：居中提示，展示两张照片的采集要求，提供「暂不开启／开始采集」。
- [人脸采集 v1](face/proposals-20261111/face-capture-v1.png)：双栏构图，左侧镜头及样本缩略图，右侧步骤、拍摄提示和独立同意选项；画面展示已同意后的第 2 张采集状态。
- [完整 ImageGen 提示词](face/proposals-20261111/imagegen-prompts.md)。使用内置 `image_gen`，以现有工作台及弹窗截图为编辑参考，两次生成；图片留在项目本地，沿用图片忽略规则。

免费参考来自 Superdesign 官方库的本地副本：`verify-its-you-aperture-otp-2fa-graphite-platinum` 的验证信息层次及 `technical-system-onboarding` 的流程组织。来源为 [superdesign-prompts](https://github.com/superdesigndev/superdesign-prompts)，内容属于风格与布局提示，无组件源码；组件继续复用 Vue／Element Plus。本次按用户要求由 ImageGen 出图，未调用 Superdesign Credits 生成。后续实现沿用现有 200ms ease-out 入退场与减少动态效果规则。

验证：

- vue-tsc 与 Vite 构建、后端 21 项相关测试通过，包含延迟 2.2 秒的搜索响应及更短配置超时。
- Playwright 9 组回归通过：真实延迟响应约 6.778 秒仍登录成功；使用虚拟时钟覆盖超过旧 5 秒后继续等待、30 秒截止、编码挂起、迟到回调和切换取消。
- Chrome DevTools 1440×900：30 秒提示可见；等待中静置 10 秒 DOM 153 → 153、运行中动画 0 → 0、Canvas 0 → 0，媒体轨道保持 1；进入后台和切换后轨道归零。无横向溢出及控制台 error／warn。[验收截图](face/proposals-20261111/login-30s-qa.png)使用模拟相机。
- Docker 前后端已重建。80 端口部署版验证超过 6 秒仍等待，取消后停止相机且迟到响应不保存会话。

---

## 2026-11-11 迁回 Docker 版宽松布局

以用户提供的第一张截图及运行中的 80 端口页面为基准，将开发版画布从 1504×1046 恢复为 1672×941。沿用原版介绍卡、羽毛球尺寸、标题字号、表单宽度、品牌区和间距，保留新版胶囊顶栏及密码／人脸登录切换。登录卡在原宽度上增加高度容纳切换栏；注册表单沿用原版布局，人脸自动采样、5 秒截止与摄像头释放逻辑保持原有实现。

1920×920 下，介绍卡区域宽 827.1px，主标题字号 56.7px，登录卡宽 532.8px，表单宽 418.4px。胶囊沿原顶栏内容位置向外扩展，登录卡底部 901.4px，全部操作完整可见。

实际验收：

- Playwright 只读对比 80 端口与 5173：1920×920、1440×900、1366×768、1920×1080。画布、主标题、介绍卡、羽毛球及登录卡位置与宽度最大差值 0.015625px；登录卡高度单独验收。密码页、人脸服务错误和注册页均无内部滚动或页面溢出。
- 人脸回归 8 组通过，使用模拟相机及接口、真实浏览器时钟：自动成功 1385ms，重采成功 1228ms，挂起请求 3993ms 结束，编码挂起在 4800.5ms 截止；切换和超时后摄像头释放，迟到响应不建立会话。
- Chrome DevTools 1920×920 静置 10 秒：DOM 184 → 184，运行中动画 0 → 0，Canvas 0 → 0；图片全部解码，面板溢出 0，控制台无 error / warn。
- vue-tsc 与 Vite 构建通过。未重建 Docker；开发预览入口为 `http://127.0.0.1:5173/login`。

复用现有图片、Element Plus Tabs 与项目设计令牌；shadcn Tabs 示例仅作交互参考。脚本：`tests/login-layout-transfer.browser.js`、`tests/face-auto-login.browser.js`。截图：[密码登录](layout-transfer/password-1920.png)、[人脸登录](layout-transfer/face-1920.png)；后者以模拟视频流展示取景样式。

---

## 2026-11-10 502 故障恢复与错误状态布局

用户截图中的状态来自真实人脸登录请求 502：Docker Desktop 当时处于 inactive，8080 后端与 18097 人脸服务无法连接。启动现有 Docker Desktop 后 InsightFace 恢复，后端因 MySQL、Redis 容器未运行而重启；启动原有 mes-mysql、mes-redis 后后端稳定运行。现有容器与数据卷沿用。

实际验证：InsightFace `/v1/health` 返回 ready；后端与当前 Vite 代理的职位接口均返回 HTTP 200 / code 00000。无真人数据的空白 JPEG 经 `5173/api/system/auth/face_login` 返回 HTTP 400 / A0700（无可用人脸），耗时 2.736769 秒，确认前端代理 → MES → InsightFace 真实链路恢复。

前端修复：连接失败、502/503/504 显示「暂时无法连接登录服务」；超时单独提示。错误与重试动作放入固定取景区，替换原状态条，不再向面板外叠加高度。1190×884 下错误面板溢出量为 0，注册按钮底部 714.1px，位于面板底部 768.1px 以内。截图 `face/backend-unavailable.png` 使用模拟 502 验证故障布局。

工程验证：vue-tsc / Vite 构建与 diff 空白检查通过；自动验证脚本 8 组场景通过，新增真实格式的 502 空响应、单次终止与错误状态布局检查。

---

## 2026-11-10 人脸轮廓与 5 秒自动验证

按用户新要求局部调整：登录取景轮廓使用 SVG 头部形状，包含头顶、双耳外缘与耳内线、双颊、下颌；白色虚线用于镜头画面，未开启或结束时使用绿色虚线保持对比。保留当前示例图的布局和现有 Element Plus 交互，未再绘制 Penpot 新稿。

- 切换到人脸登录后自动开启摄像头。就绪后预留 650ms 对齐时间，再自动裁取人脸轮廓周围画面并编码 JPEG，最长边 640px，交给已有后端接口识别。用户无需点击拍照或确认。
- 整轮预算从摄像头就绪开始计算，内部截止 4800ms，为结果呈现预留 200ms；浏览器原生权限选择由浏览器处理。截止范围包括等待画面、图像编码、请求和重采。
- 仅 A0700（画面不合格）重采，最多三张；身份拒绝、限流和服务错误直接结束。保持后端现有失败计数规则。帧之间不重复弹出全局错误。
- 通过 AbortController 取消超时及离开页面后的请求，保存登录状态前再检查 signal，迟到响应不建立浏览器会话。成功、失败、后台或组件卸载均停止相机并清理计时器。
- 后端登录搜索独立设置连接至多 1 秒、读取至多 2 秒；注册与样本管理仍使用原有超时配置。新增本机延迟 HTTP 服务测试验证搜索超时。

实际结果：前端 vue-tsc / Vite 构建通过；InsightFaceClientTest 14 项、InsightFaceLoginTimeoutTest 1 项、AuthServiceImplFaceLoginTest 5 项通过。原有浏览器流程 9 组通过，新增自动验证场景 7 组通过，后者使用模拟相机和接口、真实浏览器时钟与取消机制：自动成功 948ms，画面不合格后重采成功 1233ms，挂起接口 3935ms 结束，采样编码挂起时摄像头在 4800.5ms 停止；迟到响应和切换页面未建立会话。自动上传图像实测 354×403，仅取轮廓区域。

脚本：`tests/face-auto-login.browser.js`；截图：`face/auto-face-contour.png`。Chrome 检查确认耳部路径存在，手动拍照/确认按钮为 0；切换离开后相机轨道全部停止，静置 10 秒 DOM 174 → 174、Canvas 0，短暂切换动画结束后运行中动画为 0。真实相机身份验证需连接本机后端与 InsightFace 服务；本次本机后端未启动。

---

## 2026-11-10 用户示例图与浏览器批注调整

以用户提供的 1504×1046 示例图为主参考，停止继续绘制新方案；页面复用现有流水线和羽毛球素材，调整顶部胶囊导航、左侧介绍与卡片、右侧登录面板的构图。登录方式改为 Element Plus Tabs，保留键盘操作与原有注册字段。

最终按浏览器批注：人脸页底部与密码登录统一为「还没有账号？＋注册工作账号」，镜头仅保留虚线人脸轮廓，移除绿色四角、扫描线和独立注册引导。切换到人脸登录时自动调用 getUserMedia，浏览器负责权限询问；采集弹窗继续保留同意后开启摄像头。验证与提交按钮在真实拍摄和待验证状态显示。

验证：类型检查与 Vite 构建通过，9 组 Playwright 模拟流程通过；实际核对边框为 dashed、四角和扫描线节点数为 0、登录页手动开启摄像头按钮为 0。密码与人脸页注册按钮的高度、边框、背景和圆角一致。截图 `face/reference-final.png` 使用模拟镜头。Chrome 1504×1046 静置 10 秒：DOM 174 → 174、运行中动画 0 → 0、Canvas 0 → 0，无横向溢出，控制台无 error / warn。

本轮此前检索了 SuperDesign 官方免费库中的 Aperture 验证与 Technical System Onboarding，并阅读其预览；shadcn MCP 检索了 login、Tabs、Alert、Progress，取得 login-04、tabs-demo 和 alert-demo 源码示例，同时查阅 shadcn-vue Stepper/Tabs。资源属于风格及交互参考，运行组件继续复用已有 Vue / Element Plus；未新增依赖。SuperDesign CLI 因 DNS 失败，外部执行重试被自动审批拒绝（未经审查的最新版 npm 包执行风险），改用官方公开库，无 Credits 生成。最终视觉依照用户图与批注。

---

## 2026-11-10 人脸登录与首次采集

- 沿用现有 Vue 3、Element Plus、绿色品牌和流水线素材。登录页提供「密码登录 / 人脸登录」；注册字段与界面保持现状，注册成功后用刚创建的凭证登录并进入工作台，自动登录失败时保留新工号供密码登录。
- 工作台读取服务端人脸状态，对允许开通且未采集的账号显示顶部提醒，可稍后处理；右上角「人脸登录设置」可再次进入。管理员沿用服务端的密码登录限制。
- 采集先勾选同意，拍摄正面及微侧面两张 JPEG，以 `images` 和 `consent=true` 提交 `/system/face/enroll`。`acceptedCount > 0` 且 `faceCount > 0` 时显示完成；全部拒收时逐张展示原因。人脸登录以单个 `image` 提交 `/system/auth/face_login`，会话、身份和角色使用服务端响应。
- 共享 FaceCapture 管理设备权限、预览、重拍和上传前照片；切换登录方式、关闭弹窗、取消同意、后台切换或卸载时停止摄像头，释放照片 Object URL。照片只保留于组件内存。提供关闭并删除特征的设置入口。

### 资源与 Penpot 交接

免费参考来自已安装的 Superdesign Prompt Library，本次未使用 Credits：

1. `prompts/sign-in-to-verdant-classic-split-emerald/README.md`：绿色认证区、切换与控件层次；属于风格和布局说明。
2. `prompts/welcome-back-lumen-centered-card-true-cobalt/README.md`：认证卡片、辅助入口层级；属于布局说明，颜色沿用本项目。

本地参考库：`/home/liuhan/.local/share/ai-ui-workflow/references/superdesign-prompts/`。shadcn MCP 检索并查看 `@shadcn/alert-dialog`，项目已具备 Vue / Element Plus Dialog、MessageBox、Checkbox，直接复用现有组件，未增加依赖。

真实 Penpot 文件「羽毛球MES人脸验证」：`17092c62-56e2-8122-8008-eb6dc228fb98`；页面「人脸登录 · 桌面流程」：`17092c62-56e2-8122-8008-eb6dc228fb99`。包含登录、顶部提醒和采集弹窗三块可编辑 1440×900 画板；导出目视检查后修正标题裁切，并引入现有流水线背景素材。

新增界面使用主色 `#168565`，淡底 `#f1f7f4`，正文深绿灰，4/8px 间距，10–20px 圆角；沿用原登录页字体，Penpot 使用 Noto Sans SC。按钮过渡 150ms，顶部提醒进入/退出 200ms，仅改变 opacity/transform；支持减少动态效果。

### 实际验证

- `npm run build`（vue-tsc + Vite）通过。保留项目已有 advancedChunks 弃用提示；package.json 未配置 lint 或独立 test 脚本。
- Chrome DevTools：1440×900 和 1366×768，登录区无横向溢出；修正小桌面人脸面板超出视口的问题，并为错误/重拍状态保留面板内部滚动。控制台无 error / warn。
- Chrome 静置 10 秒并切换三轮登录方式：DOM 144 → 144，Canvas 0 → 0，运行中动画 0 → 0。
- Playwright 持久验收脚本 `tests/face-auth.browser.js` 完成 9 组检查：权限拒绝；密码必填；注册页无额外入口、注册后进入工作台并提示；同意约束、两张照片及拒收原因；成功状态与刷新；拒绝和通过的人脸登录；删除资料和关闭镜头；状态加载重试；后台/切换方式释放镜头及 1366×768 弹窗适配。接口和摄像头均在隔离浏览器内模拟，验证 multipart 字段和服务端会话使用；测试未向真实服务写入人脸资料。
- 本机 8080 后端未运行，真实相机与 InsightFace 联调待后端和人脸服务启动后执行。
- 截图与画布导出保存于 `face/`；测试浏览器已清除测试会话与接口拦截。

---

## 2026-11-08 新方向评审稿：淡绿衬底与中文衬线字

用户提供学院登录页作为新的视觉参考，要求先出设计稿。本次交付独立 HTML 预览：[login-editorial.html](../../../frontend-web/design/login-editorial.html)，预览入口为 `http://127.0.0.1:5173/design/login-editorial.html`。

设计采用 50% / 50% 双栏、左侧淡绿流水线影像、中文衬线大标题、短金色分隔线和三项无卡片的竖排介绍。右侧为白色面板、墨绿按钮、浅灰绿输入框，包含登录及注册两种预览状态。显示字体使用本机 Noto Serif CJK SC / 宋体类字体栈，正文与表单沿用中文无衬线字。主要令牌为底色 `#edf3ef`、文字 `#183a35`、按钮 `#1c6c61`、边框 `#dce5e1`；宽屏表单上限 520px。

资源复用：沿用现有流水线 WebP；复用项目 user、lock、factory、task-list、process、qr-scan SVG 图标。Superdesign 执行 `search-prompts --query "editorial serif split login sage green"`，读取 `bold-editorial-style` 索引和完整内容，同时读取已有 `design-at-the-speed-of-thought-editorial-split-sign-up` 的完整说明；它们提供风格与布局说明。采用衬线标题与无衬线表单的层次，具体构图与浅绿配色以用户附图为准。表单结构沿用此前已读取的 shadcn login-02 参考与项目现有登录/注册需求，无新增组件依赖，无 Superdesign 生成操作。

预览支持登录/注册切换、密码显隐、必填和密码长度/一致性校验、登录帮助弹窗。提交只展示设计交互反馈；正式认证与注册接口接入记录见下方当前正式版本。预览勾选项表达“记住工号”的视觉状态。

Chrome 检查：登录在 1152×797、1920×922，注册在 1152×797、1366×768、1440×900、1920×922 均完整可见；较矮窗口将注册密码与确认密码并排。两项登录空值错误、五项注册空值错误、密码显隐及帮助弹窗正常。10 秒静置和三次切换后 DOM 112 → 112、图片 1 → 1、Canvas 0 → 0、持续动画 0 → 0；认证网络请求 0。背景与装饰静止，无 RAF、轮询和 Observer；控件采用 150ms 状态过渡，减少动态效果模式为 0s。控制台无 error / warn。Node 语法检查、本地资源/HTML 引用及 diff 空白检查通过。

视觉扫描保留与附图对应的淡色照片遮罩、两处英文小字、圆形功能图标及细链接反馈；眼睛图标只表达密码显隐，自动填充内阴影用于匹配输入背景。未加入持续装饰动效。

评审截图：[登录设计稿](editorial-login-1920x922.jpg)、[注册设计稿](editorial-register-1920x922.jpg)。

---

## 2026-11-08 当前版本：用户参考图 + A 表单 + 注册

本节为当前实现。用户最终明确以新附图为准，右侧使用 A 的表单样式并添加注册。左侧按附图采用浅青绿流水线背景、羽毛球装饰、产品标题和横排三张介绍卡；右侧采用 A（shadcn login-02）的白底、居中标题、细边框输入框、黑色主按钮和描边次按钮。桌面分栏为 60% / 40%，保留已有窄窗口隐藏图片区的行为。此前的竖排胶囊、流光边框和绿色玻璃表单已移除。

### 资源与实现交接

- Superdesign 已完成 `search-prompts --query "split screen login minimal form"`、所选条目的索引及 `get-prompts --full` 读取。`login-page-ui-split-screen-electric-cobalt-brand-panel` 提供布局和交互说明，使用白底独立认证区这一结构。颜色与左侧内容以用户附图为准。官方条目未提供本项目可直接复用的 Vue 代码，本轮生成操作为 0。
- shadcn MCP 重新读取 `@shadcn/login-02` 完整示例，沿用 A 的表单层次与控件样式。组件复用现有 Vue / Element Plus 的 ElForm、ElInput、ElSelect、ElButton、ElPopover 和图标，无新增依赖。
- 左侧标题 30–56px，介绍正文 14–20px，三张介绍卡等宽横排；右侧表单 320–440px，控件高 44–48px。字体沿用项目中文字体栈。表单颜色 `#18181b`、`#71717a`、`#e4e4e7`、`#fff`，左侧绿色强调 `#138e73`。
- 使用本次登录页迭代中已生成的背景和透明羽毛球素材。流水线：[login-assembly-line.webp](../../../frontend-web/src/assets/images/login-assembly-line.webp)，1122×1402 RGB，103668 bytes；羽毛球：[login-shuttlecock.webp](../../../frontend-web/src/assets/images/login-shuttlecock.webp)，1312×1199 RGBA，150312 bytes，alpha 范围 0–255。原始生成提示词分别为 [assembly-line-prompt.txt](assembly-line-prompt.txt) 与 [shuttlecock-cutout-prompt.txt](shuttlecock-cutout-prompt.txt)。WebP 仅做编码转换，渐变遮罩、静态轨道与底部波纹由 CSS 实现。

### 注册流程

演示账号区域已替换为“注册工作账号”。注册表单包含工号、姓名、职位、密码及确认密码，提供必填、长度、密码一致性校验，以及职位加载、失败重试、空列表和提交等待状态。

复用现有后端 `GET /api/system/auth/registration_roles` 和 `POST /api/system/auth/register`。职位只展示服务端允许的选项。注册成功返回登录、预填新工号并清空密码，原有登录认证、错误提示、密码显隐和 redirect 保持可用。

本次整体任务已通过真实临时账号完成“选择操作工职位 → 注册 → 返回登录 → 新账号登录 → dashboard → 退出”验收，返回角色为 OPERATOR，注册请求一次。验收账号已清理。最终视觉调整后复测注册入口、返回登录、五项空值校验及三次表单挂载/卸载；空值提交没有发出注册请求。

### Chrome 与构建验证

- 登录与注册：1152×797、1366×768、1440×900、1920×922 均完整可见，无横向或纵向溢出；注册另验收 2560×1440。三张介绍卡在同一行，标题与羽毛球互不遮挡，两张素材均正常加载。
- 10 秒静置：DOM 112 → 112、图片 2 → 2、Canvas 0 → 0、运行中动画 0 → 0。三次登录/注册切换后同样回到 DOM 112、图片 2、Canvas 0，介绍区实例 1，注册组件卸载。
- 输入及按钮状态过渡 150ms，减少动态效果模式为 0s。当前静态页面没有 RAF、轮询、持续动画或自建 Observer，离屏和后台没有该页装饰循环。
- 动效调整：

| 调整前 | 当前版本 |
| --- | --- |
| 3 个持续流光边框动画及可见性监听 | 用户选定的静态横排卡，无持续动画与相关监听 |
| 半透明表单卡片与背景 blur | A 的白色表单面板，无背景 blur |

- 控制台无 error / warn。视觉扫描的 6 处命中已人工复核：圆弧、波纹和标题下划线来自用户参考；文本链接下划线表示操作；1000px 内阴影用于 Chrome 自动填充背景覆盖，均保留。
- `npm run build`（vue-tsc + Vite）通过；diff 空白检查通过。保留既有 advancedChunks 弃用提示。

实际截图：[登录](reference-a-login-1440x900.jpg)、[注册](reference-a-register-1440x900.jpg)。下方保留历史迭代记录。

---

## 2026-11-08 宽屏与 Chrome 自动填充适配

用户提供 1920×922 的 Chrome 截图。按同尺寸、100% 缩放复现后，确认原先固定 320px 表单、40px 控件、13px 输入文字在宽屏中过于紧凑。此前只验收了 1366 和 1440 桌面宽度，遗漏了该场景。

- 表单、控件、标题和辅助文字改用有上限的 CSS clamp 尺寸，保留 A 的左右各半、左图右表单及黑白配色。1920 宽时表单 440px，控件高 48px，输入字号 16px；较窄桌面渐进收紧。
- 截图密码框内部颜色为 `rgb(232, 240, 254)`，与 [MDN 记录的 Chrome 自动填充默认样式](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Selectors/:autofill#description) 一致。为 `:autofill` / `:-webkit-autofill` 增加白色内阴影、深色文本与光标，保持保存密码及原有 autocomplete；强制高对比度模式交还系统文本色。
- 实测 1366×768、1440×900、1920×922、1920×1080、2560×1440：表单完整可见，无横向或纵向溢出。1366 / 1440 的表单宽度约为 355 / 374px；1920 及以上上限 440px。
- 自动填充兼容规则以同色背景模拟检查：白色内阴影与文本、光标颜色生效；自动填充状态的实际呈现以用户 Chrome 的密码管理器为准。
- 减少动态效果和强制颜色模式模拟正常；10 秒静置 DOM 70 → 70、运行中动画 0 → 0、Canvas 0 → 0；演示填入及重载正常，控制台无 error / warn。
- `npm run build`（vue-tsc + Vite）通过，diff 空白检查通过。保留已有 advancedChunks 弃用提示。

同尺寸截图：`wide-before-1920x922.jpg`、`wide-after-1920x922.jpg`。下方保留模板 A 初次接入记录。

---

## 2026-11-08 正式页面：模板 A 左右对调

用户选定 [shadcn login-02](https://ui.shadcn.com/view/new-york-v4/login-02)，要求左侧图片与产品介绍，右侧登录表单。已在 `frontend-web/src/views/login/LoginView.vue` 实施，实际入口为 `http://127.0.0.1:5173/login?redirect=/dashboard`。

### 设计与组件交接

- 沿用 A 的 50% / 50% 整屏布局、白色表单面板、320px 表单宽度、24px 登录标题和黑色主按钮，左右对调。颜色为白、浅灰与 `#18181b`；字体沿用项目现有中文字体栈。
- 左侧采用车间背景与简短产品介绍，图片上方的白色遮罩负责文字对比度。正式表单复用 ElForm / ElInput / ElButton / ElPopover 与现有 Element Plus 图标，无新增依赖。
- 复用现有认证、错误处理、redirect 与演示账号填入；提交采用原生 form submit，并加入等待态防重复提交保护。填入演示账号时清理旧校验反馈。
- 背景使用内置 ImageGen 从第二版视觉稿中的车间场景提取并扩展，随后仅作 WebP 编码。生产素材：`frontend-web/src/assets/images/login-workshop.webp`，1122×1402，108726 bytes；生成提示词：`template-a-background-prompt.txt`。
- 150ms 控件状态过渡；图片静止，无新增 RAF、轮询或持续动画；减少动态效果时输入与按钮过渡均为 0s。

### 本轮验证

- Vue 单文件组件的 script / template / scoped CSS 编译通过；`npm run build` 的 vue-tsc 类型检查与 Vite 生产构建通过。构建仍提示既有 `advancedChunks` 配置已弃用，本次未调整分包配置。
- 1440×900：左右区域各 720px；1366×768：左右区域各 683px。文档尺寸与视口一致，无横向、纵向溢出，表单完整可见，背景加载成功。
- 两个字段空提交均显示必填错误；演示账号填入后错误清除。密码显隐、管理员协助提示与 Escape 关闭均可用。
- 真实接口验证：错误账号返回 A0200「工号或密码错误」，按钮恢复可用；管理员按 Enter 提交成功，接口返回 200 并跳转 `/dashboard`。两次 Enter 提交分别对应一条认证请求。退出登录正常返回本页面。
- 10 秒静置：DOM 70 → 70、图片 1 → 1、Canvas 0 → 0、运行中动画 0 → 0，离屏动画为 0。登录/退出完成后重新进入页面，状态恢复正常。
- 减少动态效果模拟：输入框与按钮 transitionDuration 均为 0s。重新进入页面后控制台无 error / warn，diff 空白检查通过。
- 视觉扫描命中两处已人工复核：白色图片遮罩保障文字对比度；协助按钮悬停下划线表示可点击状态，均保留。

效果截图：`implemented-a-1440x900.jpg`、`implemented-a-1366x768.jpg`。以下保留前期视觉探索记录。

---

## 2026-11-08 第二版：浅灰白与自然绿

用户否决第一版深蓝色和羽毛球建模，要求左侧改为产品介绍加背景，并明确使用 imagegen 重做设计稿。

使用内置 ImageGen 生成完整桌面 UI 视觉稿：`login-v2-imagegen.png`。完整生成提示词保存在 `login-v2-imagegen-prompt.txt`。画面采用浅灰白底、炭灰文字、自然绿主按钮；左侧用明亮车间照片作为背景，配产品简介和生产计划、现场执行、质量追溯三项功能，右侧为白底登录表单。

本轮交付 ImageGen 视觉稿，作为新的评审版本。下方保留第一版实现与检查记录。

---

2026-11-08 · 评审稿 v1 · 工业蓝双栏

## 目标与范围

为工厂管理人员提供清晰、易读的桌面登录入口。主操作为工号、密码登录；辅助操作为密码显示、联系管理员指引和演示账号填入。

本轮交付独立 HTML 设计稿与截图，入口为 `http://127.0.0.1:5173/design/login.html`。正式页面仍使用现有 `src/views/login/LoginView.vue`。设计稿位于前端源码根目录下的 `design/`，由 Vite 开发服务预览，不属于默认生产构建的入口或 public 资源。

## 资源检索与选择

按 direct-frontend-design 完成现有资源盘点、Superdesign 检索、shadcn 组件参考、设计交接、预览实现、动效及 Chrome 验收。

Superdesign CLI：已验证登录状态；先执行 `search-prompts`，再获取候选索引和选中条目的 `get-prompts --full`。

| 官方 Prompt Library 条目 | 返回类型 | 本稿使用方式 |
| --- | --- | --- |
| `login-page-ui-split-screen-electric-cobalt-brand-panel` | 风格、布局与交互说明 | 采用品牌区与表单区分离、可见字段标签、单一主按钮 |
| `warm-industrial-gray-style` | 风格、网格与组件描述 | 采用工业排版、细线与精确对齐 |
| `saas-landing-page-for-developer-tool` | 风格与页面方案描述 | 采用蓝色操作强调与平面背景 |

以上参考提供设计说明。另通过 shadcn MCP 搜索 `login`，查看 `@shadcn/login-04` 和完整示例。该条目包含 React 的双栏表单代码；项目现有 Vue / Element Plus 已覆盖输入、表单校验及按钮，正式接入沿用现有组件。

现有素材：项目网页只有框架默认图标，没有专用品牌 Logo 或产品照片。本稿采用项目名文字标识；从 `miniapp-wechat/miniprogram/assets/icons/` 复用 user.svg、lock.svg。羽毛球结构图由静态 CSS 几何构成，标注羽片、扎线与球头。

本轮执行资源检索与本地制作，Superdesign Credits 消耗为 0。

## 设计交接

桌面目标为 1440×900、1366×768。整体采用 55% / 45% 双栏，24px 外边距。品牌区使用深工业蓝，表单区为白底，主操作使用独立的较亮蓝色。左侧结构图建立羽毛球制造的辨识度，右侧保持单一登录任务。

| 角色 | 设计值 |
| --- | --- |
| 品牌背景 | `#143b58` |
| 主操作与焦点 | `#2264d1` |
| 表单背景 | `#ffffff` |
| 正文 | `#1d2f41` |
| 辅助文字 | `#657487` |
| 输入边框 | `#d9e1e9` |
| 字体 | 沿用项目 Helvetica Neue / PingFang SC / Microsoft YaHei 字体栈 |
| 字号 | 品牌标题约 48px，紧凑桌面 40px；登录标题 30px；正文 14px；标签 13px |
| 间距 | 以 8px 为基础节奏，表单最大宽度 364px |
| 圆角 | 品牌区域 18px，表单控件 7px |

| 功能需求 | 设计稿实现 | 正式接入复用来源 |
| --- | --- | --- |
| 工号与密码 | 带显式标签的原生 input，复用现有图标 | `LoginView.vue` 的 ElForm / ElInput、现有校验规则 |
| 显示密码 | 显示 / 隐藏切换，aria-pressed 同步 | ElInput 的 show-password |
| 登录校验与等待 | 双字段校验、首个错误获得焦点、禁用重复提交，900ms 等待状态演示 | 现有 handleLogin / loading / userStore.login |
| 演示账号 | 填入 demo / demo 并清理字段错误 | 现有 MOCK_ACCOUNT / fillDemo |
| 登录协助 | 就地展开管理员处理指引 | 轻量折叠内容，保持企业账号统一管理的流程 |
| 登录成功跳转与接口错误 | 设计稿提交仅展示交互反馈 | 正式接入保留原有 redirect 与 request 错误处理 |

设计稿不增加注册、微信扫码登录等业务能力。截图采用 `?presentation=1` 隐藏稿件评审工具条；普通预览地址保留返回当前登录页的入口。

## 动效与长期运行

输入框焦点与按钮反馈为 150ms CSS transition，按压位移 1px。结构图保持静止。`prefers-reduced-motion: reduce` 关闭控件过渡和按压位移。

仅交互时创建 900ms 等待、4500ms 消息提示两个有界定时器；重复提示会清理旧定时器。pagehide 清理定时器并恢复提交按钮，以支持前进后退缓存。无 Canvas、RAF、轮询、持续动画和新增动画依赖。

## 验证记录

- 原页面基线：1440×900，49 个 DOM 元素，运行中的动画 0，Canvas 0。
- 设计稿：1440×900 和 1366×768 均与文档尺寸一致，无横向或纵向溢出，表单、底部信息可见，本地图片加载成功。
- 空表单提交：两字段显示错误，工号获得焦点；演示填入清理错误。
- 密码显隐：type 与 aria-pressed 一致；大写提示按实际键盘状态加入无障碍描述。
- 登录等待：disabled=true、aria-busy=true，结束后按钮恢复；设计稿没有发起认证请求。
- 协助区域：展开内容和 aria-expanded 同步。
- 减少动态效果：两个控件 transitionDuration 均为 0s，运行中的动画 0。
- 10 秒静置：独立目录下 DOM 106 → 106（包括 Vite 开发客户端节点），图片 3 → 3，Canvas 0 → 0，运行中的动画 0 → 0；结构图离屏持续动画为 0。
- 导航到原登录页再返回并重载：输入框为空，按钮可用；独立目录下 DOM 106，静置后动画 0。
- 后台观察：工具对非当前标签仍报告 visibility=visible，未取得真实隐藏标签样本；源码检查确认没有持续循环，交互定时器均有界并在离页清理。
- Chrome 控制台没有 error / warn。本轮为独立 HTML/CSS/JS 稿件；完成 Node 语法检查、HTML ID / ARIA 引用 / 本地素材检查和 diff 空白检查。Vue / TypeScript 与构建配置未变，未执行项目构建任务。
- kill-ai-slop 人工复核：保留羽片纹理的重复线、结构图圆形及文本链接悬停下划线；它们分别表达材料结构、几何示意与可点击状态。移除了无功能的装饰符号与斜向按钮箭头。

截图：`before-1440x900.png`、`preview-1440x900.png`、`preview-1366x768.png`。
