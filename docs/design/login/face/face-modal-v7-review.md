# 人脸采集 v7：聚焦式向导弹窗

用户明确解除早前的页面、单卡片、右侧进度及风格约束，允许自行设计，也允许使用弹窗。本版选择短流程向导：在个人设置中开启，用居中弹窗聚焦一个动作，拍完三个角度后确认照片，再开启人脸登录。

## 预览与结构

- [完整交互流程](http://127.0.0.1:5174/design/face-capture-modal/index.html)：从未勾选同意的准备界面开始。
- [直接查看拍摄状态](http://127.0.0.1:5174/design/face-capture-modal/index.html?preview=capture)：评审用状态入口。
- [准备界面截图](proposals-20261111/face-modal-v7-intro.jpg)、[拍摄界面截图](proposals-20261111/face-modal-v7-capture.jpg)、[照片确认截图](proposals-20261111/face-modal-v7-review.jpg)。

源码：`frontend-web/design/face-capture-modal/`。本轮为可编辑 Vue / Element Plus 设计预览，画面使用已有虚构人物素材，状态在组件内模拟。真实相机、姿态判定与后端入库在接入阶段完成。当前运行服务为 5174，旧版预览保留。

流程：准备与同意 → 正面 → 左转头 → 右转头 → 照片确认 → 完成。拍摄中可回到上一步、按需查看当前姿态示例、查看摄像头问题及重试状态；确认页可以指定重拍某一张，保留另外两张。确认前关闭会清理本次临时状态；完成后设置页显示已开启，重新采集需要再次同意。

## 选材与视觉决定

延续上一轮的免费模板研究，补读本地官方 Prompt Library 的以下材料，并通过 shadcn MCP 读取 Dialog 与 AlertDialog 完整示例：

| 来源 | 内容类型 | 采用的部分 |
| --- | --- | --- |
| [Editorial Image Lightbox](https://superdesign.dev/library/editorial-image-lightbox) | 风格与布局说明 | 遮罩下的单一任务焦点、明确退出与内容检查；采用现有 MES 配色 |
| [Modal Design — success celebration](https://superdesign.dev/library/modal-design-success-celebration-pastel-legibility-fixed) | 风格与布局说明 | 清晰的成功反馈及完成操作；使用静态勾选反馈 |
| [Aperture 验证页](https://p.superdesign.dev/draft/a40c68d3-ca57-4c9f-834e-a4de361c7f1b) | 风格与布局说明 | 单一任务、居中操作和简短的安全说明 |
| shadcn MCP `dialog-demo`、`alert-dialog-demo` | React 组件示例 | 标题/内容/操作层次、关闭与焦点交互；实际实现复用 Element Plus |

源文件位于本地 `~/.local/share/ai-ui-workflow/references/superdesign-prompts/prompts/` 对应 slug 下。未使用 Superdesign Credits 生成，未新增依赖。当前未提供 Penpot MCP，交付为 HTML/Vue 可编辑预览。

弹窗宽 620px，14px 圆角，背景使用无模糊的深蓝灰遮罩。主操作蓝 `#2866b7`，正文 `#233246`，取景台 `#f2f5f8`，成功色 `#357e68`；沿用项目中文无衬线字体。横向三段进度表达采集状态，椭圆取景区集中视线；姿态示例默认收起。按钮高 44px，当前动作 24px。照片确认阶段用三张等宽照片和各自的重拍入口，成功阶段只保留结果及完成操作。

复用 Element Plus Dialog、Checkbox、Button、Popover、ConfigProvider 和现有图标、FaceScanIcon、三姿态素材。首轮浏览器检查后提高小字号文字对比度；通过中文 Locale 统一关闭按钮辅助名称。保留组件库短暂的开关过渡，无持续动画或扫描效果；减少动态效果模式下，额外覆盖组件库弹窗进场与入口按钮焦点过渡。

## 实际验证

- 18 项 Playwright 流程检查通过：同意前后状态、拍摄推进、帮助与重试、按需示例、上一步、三张确认、单张重拍、开启成功、重新采集清理、Escape 关闭与焦点返回。
- 1440×900 完成准备、拍摄和照片确认截图检查，无横向溢出，图片加载完成。拍摄弹窗约 700px 高。
- 1366×768 的准备/拍摄/确认/成功四态全部位于视口内，分别高 636/613/567/430px，无横向溢出；桌面短窗口对镜头与间距做适配。
- 捕获状态静置 10 秒，DOM 172 → 172、图片 2 → 2、Canvas 0 → 0、媒体 0 → 0、运行动画 0 → 0。关闭后连续三次打开/关闭，均恢复到节点 115、图片 0、Canvas 0、媒体 0、动画 0。
- 减少动态效果复测：开启时运行中动画为空；键盘焦点保持在弹窗内，关闭后回到设置页入口。关闭按钮辅助名称为「关闭此对话框」。
- 严格 vue-tsc 与独立 Vite 构建通过。Chrome 控制台无 error / warn；变更及新文件格式检查通过。
