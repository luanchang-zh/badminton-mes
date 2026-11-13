# 个人设置中的人脸采集 · v4

方式：内置 image_gen。生成一份同人物三姿态模板，再以现有 v3 与模板为参考修订页面；本轮为设计评审。

输出：

- face-pose-examples-v1.png：正面、本人左转头、本人右转头，三图同一人物。
- face-personal-settings-v4.png：个人设置内的采集页面，纯白背景，进度紧贴镜头左侧，示例位于下方。

## 三姿态示例模板

```text
Use case: photorealistic-natural.
Asset type: one reusable face-pose example template for a desktop face-enrollment page.
Create a clean three-panel photographic reference strip showing ONE identical fictional adult East Asian person, plain medium-gray crew-neck shirt, short neat black hair, natural neutral expression, eyes open, no glasses or accessories. The exact same identity, hairstyle, lighting, shirt, camera distance and head scale must match across all three views.
Composition: a wide horizontal triptych on a genuinely pure white #FFFFFF background. Three equal-width panels with generous white gutters and identical head-and-shoulder framing. Head and shoulders fully inside each panel, no clipping of hair or ears. A straightforward professionally lit studio reference photo, soft even front lighting, no theatrical treatment.
Panel order from left to right:
1. Front view: face straight toward camera, zero yaw, natural gaze.
2. Subject turns their head about 25 degrees to THEIR LEFT; nose points toward the viewer's RIGHT. Shoulders remain square. Both eyes and facial contour remain visible.
3. Subject turns their head about 25 degrees to THEIR RIGHT; nose points toward the viewer's LEFT. Shoulders remain square. Both eyes and facial contour remain visible.
This is a practical pose guide, not a profile portrait: turns are mild, well below 45 degrees, no tilt up or down, no extreme side profile.
Constraints: single consistent character in all panels, plain white backdrop and margins, photographic clarity, no cards, no decorative frames, no labels, no text, no arrows, no numbering, no logos, no watermark. Do not add UI or browser chrome. The user will label the three examples in the page separately.
```

## 页面修订

```text
Use case: ui-mockup.
Edit target: Image 1, the latest minimal full-page face capture design for 羽毛球 MES.
Supporting insert: Image 2, the newly AI-generated three-pose photo template. Use this ONE identical fictional person for the example strip and the simulated camera preview.
Update the page according to these precise requirements:
1. Place face enrollment INSIDE USER PERSONAL SETTINGS. Retain the existing MES navy sidebar, white app header and user avatar. Header context should say only "个人设置". Main page heading "人脸采集". Access is through user settings, so remove the unrelated active blue highlight from 工作台; do not add a new business module or show an open dropdown. No modal, mask or popup.
2. The entire MAIN PAGE background must be PURE WHITE #FFFFFF, not pale gray, not blue, not a gradient. Preserve the navy global sidebar as existing app chrome. Keep the workspace visually spare and flat, with no extra background cards or outer rounded container.
3. The MAIN CONTENT is a dominant camera capture viewport, around 700x390px, horizontally centered in the available workspace. Use 8px rounded corners and a subtle edge only. Simulated camera frame shows the person from Image 2, gray crew-neck shirt, head turned gently to their LEFT (nose toward viewer's right), natural neutral lighting, thin white dashed face contour. No camera toolbar, no status pill or camera-on text.
4. Put the capture progress immediately to the LEFT of the camera, with only a 20-24px gap between the progress column and the camera edge. This is NOT a faraway navigation panel. A slim vertical three-step indicator, aligned to the camera height. First circle is a completed green check with short label "正面". Second is current with a small green circle and label "左转头". Third is inactive outlined gray circle and label "右转头". Use a thin connector. No heading such as 采集步骤, no descriptions, no fraction, no completion badges.
5. One short instruction above the camera, "向左转头". Keep it 18px, not a giant promotional heading.
6. Immediately BELOW the camera is a compact action row: left checked checkbox "同意用于人脸登录" with quiet text link "查看说明"; right secondary outline "重拍" and a single green primary "拍摄照片". The consent state shown follows prior explicit user opt-in. Preserve clean spacing and usable button sizes around 40px high.
7. BELOW that action row, show THREE EXAMPLE TEMPLATES in ONE HORIZONTAL ROW aligned to the camera width. Use the supplied Image 2 triptych, with its exact identity, equal framing and correct pose order: front, subject's LEFT turn, subject's RIGHT turn. The examples should be modest guides around 160-190px wide by 130-150px high, separated by white space; no shaded card backgrounds or decorative borders. Under them put only the short captions "正面", "左转头", "右转头". No separate 示例模板 heading or explanatory paragraph. No example tiles to the right of the camera anymore.
8. Upper-right plain text action "返回工作台" may remain. No arrow flourish.
Typography and style: same Chinese sans-serif and existing dark forest headings #254231, green primary #1b704e, neutral supporting gray. Page heading 26px, functional instruction 18px, progress/captions/controls 14px. Do not enlarge every label. Main content fits a landscape desktop viewport around 1440x900 (or the target image ratio) without scrolling. Camera and progress should occupy the upper-middle, example strip clearly below them. Plenty of WHITE space, not empty decorative cards.
Allowed main-content text only: "人脸采集", "返回工作台", "向左转头", "正面", "左转头", "右转头", "同意用于人脸登录", "查看说明", "重拍", "拍摄照片". Repeating the three pose names once in progress and once under examples is functional and allowed. Existing app navigation/user text stays. Do NOT add subtitles, current-account metadata, warning/advice/privacy paragraphs, extra icon tiles, ornamental status labels, numbers outside the real step sequence, statistics or success messages. Deliver one polished revised design screenshot, not a collage of multiple UI versions.
```
