# 人脸登录配套设计稿 · 2026-11-11

生成方式：内置 image_gen；两次图片编辑生成，未使用 Superdesign Credits。参考图为项目现有的工作台和采集弹窗截图。生成稿保存于同目录，作为设计预览，等待用户选定后实现。

## 进入系统后的引导弹窗

输出：face-invitation-v1.png

```text
Use case: ui-mockup. Asset type: high-fidelity desktop web application mockup for the existing 羽毛球 MES system.
Edit target: the attached real 1440x900 application screenshot. Preserve the exact existing desktop application shell, dark navy left sidebar, white header and the dashboard page behind. Remove the old floating green face-enrollment notification banner at the top. Add a tasteful centered modal design above a very subtle darkened dashboard backdrop. This is a static design review, not a marketing landing page. Keep the full screenshot at landscape 1440x900 or equivalent aspect, crisp Chinese typography, no browser chrome, no cropped controls.
Primary request: design the optional face-login enrollment invitation shown after entering the workspace. Main modal is approximately 560px wide and 480px high, centered in the workspace, warm-white surface, restrained 18px rounding, a fine pale sage border and soft realistic shadow. Compact close X at top right. Main content carefully spaced, left aligned, with one small elegant face-outline icon on a very pale green tile, a clear title, explanatory copy, a quiet two-photo visual, and an unambiguous action pair. The UI should feel finished and consistent with the user's existing sage / forest-green login design. Do not recolor the rest of the dashboard.
Visual system: white #ffffff, quiet sage #f1f5ef, dark forest #254231, primary button green #1b704e, body gray-green #7a8a80. Chinese sans-serif, heading 26px semibold, body 15px, supporting text 13px. Button height 44px. Plenty of clean white space, restrained separators, no oversized glows, no neon, no gradient headline, no ornamental data or badges.
Text verbatim, readable and correctly typeset:
Title: "开通人脸登录"
Subtitle: "下次看一眼镜头，即可进入工作台"
Main explanation: "采集两张照片，为您的工作账号开启快捷登录。"
Supporting line: "仅用于本人登录验证，可随时关闭并删除特征。"
Small two-photo visual labels: "正面照片" and "微侧面照片", abstract line head silhouettes, not real personal photos.
Footer actions on the same row: quiet secondary "暂不开启", stronger forest-green primary "开始采集" with a small right arrow.
Tiny footer note: "也可在右上角「人脸登录设置」中开启"
Constraints: preserve the actual MES app background composition from the input. Modal is the clear focal point. This invitation does not start the camera and does not combine or pre-check user consent. Show no camera permission warning, countdown or privacy checkbox on this first invitation. All text is simplified Chinese. Avoid changing or inventing sidebar modules and background dashboard statistics.
```

## 人脸信息采集

输出：face-capture-v1.png

```text
Use case: ui-mockup. Asset type: high-fidelity desktop web face-enrollment capture interface for the existing 羽毛球 MES application.
Image 1 is the EDIT TARGET: the existing enrollment dialog screenshot. Image 2 is a STYLE REFERENCE: the companion invitation mockup created for the same flow. Redesign only the enrollment dialog above the existing MES dashboard. Preserve the underlying app's dark navy sidebar, white header, module layout, muted backdrop, and overall desktop screenshot aspect ratio. Make the second design feel like the next screen after image 2. No browser chrome, no crop, no phone.
Primary request: an elegant large centered face-capture dialog, about 1000px by 700px on a 1440x900 desktop screen. White surface, restrained 18px rounding, subtle sage border, soft natural shadow. Heading top-left, small close X at top-right. Two columns separated by generous space, not nested cards everywhere. Left is the dominant live camera preview; right provides a two-step capture guide, plain helpful instructions and explicit privacy consent. The state shown is the second photo ready to capture, with the first photo already captured.
Visual system to match image 2: forest #254231 heading, primary #1b704e, muted sage #f1f5ef, white and gray-green supporting text. Chinese sans-serif, heading 26px, body 14-15px, small labels 12px. Clean aligned grid, understated typography, crisp legible simplified Chinese.
Camera preview: naturally lit fictional adult East Asian office worker in a simple neutral gray shirt, short neat hair, shoulders visible, neutral pale background, head turned only slightly 10-15 degrees. This fictional portrait represents a sample webcam frame, not an attached real identity. No body identification overlays, no fake detection metrics. A thin dashed light face contour includes top of head, ears and chin; no corner scanner marks, no laser line. Small top row inside preview: left "第 2 张 · 微侧面", right "摄像头已开启". Camera frame roughly 450x330px with 12px rounding. Below, two compact horizontal thumbnail slots: first contains the same person's frontal photo and a small completion check, labeled "正面照片 · 已采集"; second is an outlined placeholder labeled "微侧面照片 · 待采集".
Text verbatim:
Title "采集人脸信息"
Subtitle "采集两张清晰照片，为您的工作账号开启人脸登录。"
Right section heading "采集步骤" with understated count "1 / 2".
Step one marked complete: "正面照片" and description "自然直视镜头，保持面部无遮挡".
Step two current: "微侧面照片" and description "轻微转头，保持双眼和面部清晰".
A quiet pale sage instruction area labeled "拍摄提示" with copy "光线充足 · 镜头清洁 · 画面中只有本人".
Privacy section under a fine divider: small lock icon, "仅用于本人登录验证", then body "保存用于识别的人脸特征，不保存照片原图。您可随时关闭人脸登录并删除特征。"
An explicitly checked consent box with text "我同意采集和处理本人人脸信息"; it is checked in this active-camera state because the user opted in earlier, not preselected by the app.
Footer horizontal separator. Bottom-left subtle identity text "当前账号：operator_demo". Bottom-right secondary "重新采集" and clear primary green "拍摄第 2 张". Add tiny close alternative "暂不设置" only if there is room without clutter.
Constraints: visually prioritize camera then instructions, show all buttons in viewport with no internal scroll. Deliberate whitespace, polished real enterprise UX. Preserve existing background product, avoid gradients/neon/glowing dots, no unrelated stats, no new sidebar, no imaginary security claims such as liveness detection or encryption guarantees. This is a static concept image, not an implemented UI.
```
