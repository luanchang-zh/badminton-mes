# 个人设置中的人脸采集 · v5

方式：内置 ImageGen，基于 v4 页面及已有三姿态模板定向修改。交付范围为静态设计评审。

用户要求：进度移至右侧；采集区、进度、示例合入同一张卡片；沿用羽毛球 MES 现有工业后台风格。

复用：现有深蓝侧栏、白色工作区、Element Plus 蓝色按钮与 4px 圆角；三姿态沿用 face-pose-examples-v1.png。免费 Prompt Library 的 technical-system-onboarding 只作步骤与结构参考，属于风格说明，没有组件源码。Superdesign CLI 离线预检因未缓存失败，改读已安装的官方免费参考库。未调用 Superdesign Credits 生成。

输出：face-personal-settings-v5.png。

## 完整提示词

```text
Use case: ui-mockup.
Asset type: one high-fidelity desktop face-enrollment design revision for the existing 羽毛球 MES application, not working UI.
Edit target: image 1, face-personal-settings-v4.png. Supporting insert: image 2, face-pose-examples-v1.png; reuse this same fictional East Asian man and all three head poses.
Primary request: move the vertical enrollment progress to the RIGHT of the camera, and place CAMERA + RIGHT-SIDE PROGRESS + BOTTOM THREE-POSE EXAMPLES together INSIDE ONE SINGLE BOUNDED CARD. Match the actual existing MES industrial administration interface. This is a continuation of the selected design, not a new concept.

Preserve: dark navy #001529 left sidebar and its existing Chinese navigation items, white top application header with 个人设置 and 操作员. Main workspace background pure white #FFFFFF. The same portrait identity, same three mild poses and exact pose order front / subject's own left / subject's own right. Use the existing photographic strip; do not change faces or add people.
Desktop composition: full application screenshot around 1600 by 1000 pixels, front-on with crisp Chinese text. The sidebar is about 220px wide, top header about 56px tall. Workspace content has 28–32px margins. At the top of workspace, a modest left aligned title 人脸采集 in 22px dark gray semibold; a small 返回工作台 text button aligned on the right. Avoid oversized green title.
One main rectangular white card, around 1120px wide, visible completely within the screenshot with reasonable surrounding space. Use 1px #dcdfe6 gray border and 4px corners, no heavy shadow. All three regions MUST be enclosed by the SAME continuous outer card boundary. No separate example cards or separate progress card. Keep the shell background pure white. Compact technical administration aesthetics, precise grid alignment and unobtrusive dividers; do not add factory illustrations or texture.
Within the card, 24px padding:
- A small current action 向左转头 above the camera, in neutral dark-gray 16–18px semibold.
- Main row: dominant camera viewport on the LEFT about 790 x 400px, lightly rounded 3px, light neutral photographic backdrop, the same man turning mildly to his own left (nose toward viewer's right). Keep a restrained white dashed facial outline showing hair, ears, chin. On the RIGHT of this viewport place a narrow 170–190px vertical three-step progress indicator, close to the camera but separated by 28px of space. Vertically centered in the row: green completed check 正面, blue active circle 左转头, gray hollow pending circle 右转头, connected with thin vertical lines. The progress must unambiguously be to the right, never on the left.
- Under the camera, inside the SAME card: a compact operation row. On the left a checked checkbox followed by 同意用于人脸登录 and a small 查看说明 link; on the right two rectangular Element Plus style buttons 重拍 (white with gray border) and 拍摄照片 (solid #409eff blue). Align this operation row to the camera width; do not put buttons under the progress.
- A subtle horizontal gray divider inside the outer card separates the operation row from the examples below.
- Bottom example region inside the same card: THREE equally sized compact portraits aligned horizontally beneath the camera, using the provided strip of the same man. Plain white backdrop, modest head-and-shoulder thumbnails, natural portrait proportions. Labels directly below: 正面 / 左转头 / 右转头 in neutral dark gray 14px. This is one integrated example strip, no individual bordered thumbnail cards, no extra title or instructions. The right progress column ends above and has comfortable whitespace below.
Visual language: match existing Vue/Element Plus MES backend. Helvetica Neue / PingFang SC / Microsoft YaHei sans serif, 14px body. Text #303133, secondary #606266, fine boundaries #dcdfe6 or #ebeef5, primary #409eff, success #67c23a. Functional industrial enterprise panel, small radii 3–4px, flat surfaces, exact spacing. No green brand takeover, no green large headline or huge controls.
Text should be limited to existing navigation + 个人设置 + 操作员 + 人脸采集 + 返回工作台 + 向左转头 + the step labels + checkbox and 查看说明 + two buttons + three pose captions. Remove redundant status badges, subtitles, extra descriptions, counters, repeated titles, technical IDs and marketing text.
Constraints: exactly ONE main card enclosing all three requested parts; progress strictly on the right; examples below camera inside the card; pure-white workspace; no dialog mask or modal; no extra navigation destination; no sci-fi neon, metallic textures, gradients, glow, glass, fake equipment panels, hazard stripes, decorative grid, pills or heavy shadows. Preserve exact readable Chinese text.
```
