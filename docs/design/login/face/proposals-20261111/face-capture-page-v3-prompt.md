# 人脸采集独立页面 · v3

生成方式：内置 image_gen。以 v2 为参考，按用户反馈精简重复标签、提示卡片与说明文字。

输出：face-capture-page-v3.png。

```text
Use case: ui-mockup. Edit the attached desktop full-page face enrollment design. This is a precision simplification pass requested by the user: remove redundant labels, explanatory text, and superfluous cards. The previous design is too verbose and visually over-labelled.
Edit target: existing full-page 羽毛球 MES face capture mockup. Preserve its NORMAL application shell (dark navy sidebar and white top header), the forest-green primary color, Chinese typography, the same fictional worker portrait and the current capture progress. Keep a dedicated full page, not a modal. No overlay.
Critical change: completely remove the subtitle under the page heading, the account metadata line, the repeated "人脸采集" panel heading, the preview toolbar and its status labels, the entire "采集步骤" info card and step descriptions, the entire "拍摄提示" card, the entire "人脸信息说明" card, the paragraph disclosure copy, the bottom explanatory/helper sentence, any progress fraction "1 / 2", and any other repeated metadata. Do not replace the removed material with different decorative headings, micro-labels, numbered badges, icon tiles or reassurance copy.
New layout within the existing app content:
- A single modest page heading "人脸采集" at upper left. A quiet plain outline/text action "返回工作台" at upper right, no arrow flourish.
- Below, a clean open workspace with one dominant camera preview on the left, approximately 660x430px at a 1440x900 desktop size. The camera sits directly in the workspace, no extra surrounding card or duplicate title. Retain the same fictional adult East Asian worker in a gray shirt, neutral pale backdrop, slight 10-15 degree turn. Use only a thin dashed white face guide including ears and chin. No camera toolbar, no camera on label, no scores, no scan line.
- One short functional instruction above the camera: "轻轻向右转头". This is the only instruction sentence on the page.
- To the right of the camera, two vertically arranged thumbnail slots aligned to the camera height, each approximately 200x145px, separated by thoughtful whitespace. First contains the same worker's frontal portrait with a small green completion check, labelled ONLY "正面". Second has a thin outlined empty head silhouette, labelled ONLY "微侧面". No words such as 已采集 / 待采集 repeated around them, no progress badge.
- Under the camera, one compact consent row with a checked checkbox and the text "同意用于人脸登录", followed by a quiet text link "查看说明". It represents an active camera AFTER the user already opted in. No paragraph of legal or technical prose in the main capture view.
- Below/right aligned actions: secondary "重拍" and one strong forest-green primary "拍摄照片". All controls visible in the viewport.
Keep the whole workspace exceptionally quiet, practical and focused. Light neutral background, white surfaces only where needed, 8-10px rounding on camera/thumbs, minimal borders. Font hierarchy through size, weight and spacing. Main heading 26px, instruction 18px, control text 14-15px. No giant empty card wall, no decorative numbers, no extra symbols or pills, no button suffix arrows, no gradient text, no glows.
Text whitelist for the MAIN CONTENT area is exactly: "人脸采集", "返回工作台", "轻轻向右转头", "正面", "微侧面", "同意用于人脸登录", "查看说明", "重拍", "拍摄照片". Existing app shell navigation labels and user dropdown may remain. Do not invent any other visible text. All text clear simplified Chinese. Landscape desktop screenshot, no browser frame.
The result must feel like a concise purpose-built tool, not a tutorial, information dashboard, marketing page or card catalogue.
```
