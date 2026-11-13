# 侧栏羽毛球图标生成记录

日期：2026-11-12。

工具：内置 ImageGen，编辑用户提供的第一张羽毛球图片，启用透明背景。保留原有白色羽毛、蓝色球带和右上倾斜姿态，移除浅青背景及装饰弧线。

正式素材：`frontend-web/src/assets/images/sidebar-shuttlecock.png`，RGBA PNG，1254×1254；侧栏以 40×40 CSS 像素显示。展开时与「羽毛球 MES」并排，折叠时保留图标。

输入：用户附件 `codex-clipboard-4649428a-0b44-476a-9e9a-992a216966ff.png`。

最终提示词：

```text
Edit target: the attached shuttlecock image. Produce a clean transparent-background cutout of the SAME white shuttlecock with the same vivid blue band, glossy white cork and layered white feather fan, retaining its upward-right diagonal orientation and realistic soft 3D illustration style. Remove the entire pale cyan photographic background, green swoosh/ring, shadows cast on the background and any background objects. Preserve the shuttlecock's silhouette and fine white feather details. Where its rightmost feather tips are cropped at the edge of the reference, complete those tips naturally so the whole object is fully visible. Center this single shuttlecock in a square transparent canvas with a small, even clear margin, object occupying about 90% of canvas height. Intended use: a 40px navigation brand icon over an existing very dark navy sidebar, so white feathers and blue band must read cleanly with crisp anti-aliased alpha edges and no cyan/white background halo. No text, no badge, no container, no extra ornaments. Background must be genuinely transparent.
```
