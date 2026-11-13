# 人脸采集独立页面 · v2

生成方式：内置 image_gen，一次编辑生成。以同目录 face-capture-v1.png 为编辑目标，保留 MES 导航和深绿采集视觉，按用户反馈改为独立页面。

输出：face-capture-page-v2.png。用途：设计评审；工作台与人脸登录设置入口后续直接导航到此页面。

```text
Use case: ui-mockup. Edit the attached face-enrollment UI concept into a DEDICATED FULL DESKTOP PAGE inside the existing 羽毛球 MES application. The user explicitly rejects a popup. Replace the entire workspace content with a real settings page. There must be NO modal, NO dimmed dashboard, NO overlay, NO close X, NO dashboard statistics behind the form, and NO giant floating dialog-like outer card.
Input image role: the previous concept is the edit target and a visual reference for the forest-green capture controls, portrait, typography and existing MES shell. Preserve the dark navy left sidebar width around 220px and white top application header. Sidebar can retain its current five modules; the main content is the new page, not the workspace dashboard. The whole interface is a single coherent desktop web page, 1440x900 landscape or equivalent ratio. Do not draw browser tabs or a device frame.
Art direction: maintain the user's existing calm MES visual language. Light cool-gray workspace #f4f6f8, white functional panels, dark forest headings #254231, primary green #1b704e, muted sage hints #edf4ef, body gray #728176. Crisp simplified Chinese sans-serif. Clear 26-28px page title, 16px section headings, 14px readable body text. Panel radii 8-10px and fine gray borders, restrained shadows only on small controls. Use clean 24-32px spacing. No gradients, glass effects, neon or ornamental graphics.
Page structure:
1. Existing app header above: left breadcrumb "人脸登录设置", right user dropdown "操作员". Existing sidebar is fully visible and not darkened by an overlay. If the application tab strip is retained, its active tab reads "人脸登录设置".
2. Page heading directly on the gray page canvas at the top of the workspace: "人脸登录设置". Subtitle "采集两张清晰照片，为您的工作账号开启人脸登录。". A quiet account line "当前账号：operator_demo". Right aligned outline action with left arrow "返回工作台".
3. Content uses two open columns, about 60% left and 36% right. Each column has ordinary flat white page panels; do not wrap both in a single floating rounded sheet. The left dominant camera panel has section label "人脸采集", a small state label "第 2 张 · 微侧面", and right-aligned camera state "摄像头已开启".
4. Left camera preview around 590x365px, rounded 10px: use the SAME fictional adult East Asian worker portrait from the target image, softly lit on a neutral pale backdrop, simple gray shirt, slight 10-15 degree head turn. Thin white dashed head outline includes hairline, ears and chin. No scanning laser, corner markers, biometric scores or imaginary detection claims.
5. Beneath the camera two well-aligned compact sample thumbnails. First shows that worker's frontal portrait with a small green completion check and label "正面照片 · 已采集". Second is a quiet outlined empty slot labeled "微侧面照片 · 待采集". Two actions below aligned to the camera area: outline "重新采集", strong green "拍摄第 2 张". Small helper "完成两张照片后，即可提交开通".
6. Right upper panel titled "采集步骤", with progress text "1 / 2". First item complete: "正面照片" and "自然直视镜头，保持面部无遮挡". Second item current: "微侧面照片" and "轻微转头，保持双眼和面部清晰". Use a restrained vertical step connector.
7. A quiet sage hint within the right column: heading "拍摄提示", text "光线充足 · 镜头清洁 · 画面中只有本人".
8. Right lower privacy section titled "人脸信息说明", small lock icon. Copy "仅用于本人登录验证。" then "保存用于识别的人脸特征，不保存照片原图。" and "您可随时关闭人脸登录并删除特征。". An explicitly checked checkbox reads "我同意采集和处理本人人脸信息". Checked here means the person already consented before activating the camera; do not imply auto-consent.
All main content and actions fit the visible desktop page with generous but practical white space. Page lower area stays simple. No extra cards, no fake charts, no success screen, no second popup. This is a polished static design draft for review, not an implemented app screenshot. The defining change from the input is full-page navigation and an unmasked normal application shell.
```
