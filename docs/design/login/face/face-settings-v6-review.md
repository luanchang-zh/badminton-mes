# 人脸采集 v6：选材、设计与验收

## 范围与结论

用户认为 v5 观感不自然，要求按 direct-frontend-design 美化并增加模板检索。本轮保持个人设置、纯白背景、右侧进度、下方三姿态示例和同一卡片的布局要求，完成可交互的 Vue / Element Plus 设计预览。

预览入口：`frontend-web/design/face-settings/index.html`。本次本地地址为 <http://127.0.0.1:5174/design/face-settings/index.html>；[模板对照页](http://127.0.0.1:5174/design/face-settings/references.html)包含七份原始缩略图、来源与采用理由。预览展示同一虚构人物的已有示例，不调用相机或真实人脸接口；初始停在第二姿态以便评审步骤层次。生产路由、旧采集流程和后端维持现有版本。

## 资源检索与选用

Superdesign CLI 缺少本地缓存；依共享前端工作流，改用本地安装的官方免费 Prompt Library。按 verification、settings、profile、onboarding 筛选，阅读以下七份完整说明，并查看 Formcraft、Fieldcraft 和 Multi-step 的实际预览。所有原始链接与说明类型保存于 `frontend-web/design/face-settings/references.json`。

| 模板 | 内容类型 | 取舍 |
| --- | --- | --- |
| Workspace Settings · Formcraft | 风格、布局说明 | 采用设置页的限宽、内容层级和蓝色操作；保留 MES 侧栏 |
| Formcraft — Workspace Settings | 风格、布局说明 | 比较设置项和底部操作密度，与上一方向合并 |
| Verify it's you · Aperture | 风格、布局说明 | 参考验证任务的视觉聚焦；舍弃胶囊、玻璃和重复标签 |
| Forms that feel effortless to fill · Fieldcraft | 风格、布局说明 | 采用清晰分组与底部操作；保留项目蓝色 |
| Multi-step Form Flow | 布局与交互，含参考 HTML | 借鉴步骤推进和返回关系；本轮仅设计桌面布局 |
| Editorial SaaS Onboarding | 风格、布局说明 | 比较留白，舍弃衬线大字与暖色调 |
| Superdesign Setup — Inline Preview | 风格、布局说明 | 参考预览与操作反馈，省去技术字段和终端装饰 |

三种方向对比：工业蓝白设置页与项目最一致，作为主体；石墨色验证页有利于聚焦，借用其克制的信息层级；编辑式引导页的字号与色彩差异较大，仅参考留白。

shadcn MCP 实际检索 Card / Sidebar / Field，读取 `card-demo` 和 `field-checkbox` 完整示例。Vue 实现对照官方 [Stepper](https://www.shadcn-vue.com/docs/components/stepper) 与 [Blocks](https://www.shadcn-vue.com/blocks)；相机引导对照 [Persona 官方自拍采集说明](https://help.withpersona.com/articles/1dsXklKVFkvpSdX90CTtNo/)。本轮使用项目已有技术栈，不安装新组件或依赖，不调用 Superdesign Credits 生成。

| 需求 | 采用的资源 | 原因与依赖 |
| --- | --- | --- |
| 应用外框与导航 | BasicLayout、TagsView 的既有尺寸与配色 | 保留 220px 深蓝侧栏、56px 顶栏和 40px 标签栏；既有 Element Plus 图标 |
| 相机与姿态例图 | 已有三姿态 PNG、FaceCapture 的轮廓路径 | 同一人物、相同方向；以 CSS 显示原素材 |
| 步骤进度 | shadcn-vue 垂直 Stepper 交互参考 | 使用有序列表与 aria-current 表达步骤；Vue 现有状态 |
| 同意、拍摄、重拍、说明 | ElCheckbox / ElButton / ElDialog / ElIcon | 复用键盘与弹窗行为；现有 Element Plus |

## 设计交接

卡片桌面宽度 920px，6px 外圆角，镜头 4px 圆角；主背景纯白，侧栏沿用 `#001529`，操作蓝 `#3377c9`，正文 `#303133`，细边框 `#e2e6ec`。沿用项目中文无衬线字体与 4/8px 间距，页面标题 22px、当前动作 17px、操作文字 13px。右侧步骤仅保留姿态名，去掉重复状态文案。示例改成小尺寸横排，操作收在同一卡片底部。

首轮真实浏览器检查后收窄卡片、删除步骤副文案，调整轮廓和文字对比度。1366×768 检查发现底部操作超出首屏，增加桌面短窗口适配：缩小镜头与示例并收紧垂直间距，卡片底部由 807px 降到 714px，操作完整可见。该规则按桌面窗口高度触发，没有增加移动端布局。

动效仅保留链接颜色 140ms 反馈和 Element Plus 弹窗过渡，无持续扫描、呼吸、漂浮或 RAF。减少动态效果模式关闭相关过渡及说明弹窗动画。

## 实际验收

- Chrome：1440×900、1366×768、1920×1080 检查通过；无横向溢出，三姿态图片全部加载，进度位于镜头右侧。模板对照页七张图全部加载，无横向溢出。
- 首轮、精简轮及短窗口修正均经真实截图检查。最终截图：[1440 桌面](proposals-20261111/face-settings-v6-1440.jpg)、[1366 桌面](proposals-20261111/face-settings-v6-1366.jpg)。
- Playwright：12 项检查通过，包括同意撤回/恢复、说明弹窗、姿态推进、完成、重新采集、第一张禁用重拍、退回重拍、侧栏折叠/展开、键盘焦点。
- 长期运行检查：1440 桌面静置 10 秒前后节点数 151 → 151、图片 4 → 4、Canvas 0 → 0、媒体元素 0 → 0、运行中动画 0 → 0；离开页面并重新进入后恢复到相同计数。后台页同样无持续动画。源码无 RAF、轮询、相机流或自建计时器。
- 减少动态效果实测为 true，链接和按钮过渡时长为 0，运行中动画为 0。Chrome 控制台无 error / warn。
- 包含预览文件的严格 vue-tsc 检查和独立 Vite 构建通过；未新增依赖。正式项目没有独立 lint 脚本，本次检查以预览入口为范围。

当前会话未暴露 Penpot MCP，因此交付 HTML/Vue 可编辑预览；没有声称完成 Penpot 画布。后续接入个人设置路由时再连接真实摄像头、三姿态采样与后端接口。
