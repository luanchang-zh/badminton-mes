# 验收报告：rag-native-files-chat

## 当前结果

- 结果：**pass**（当前阶段：`verified`）
- 验收轮次：5
- 验收包摘要：`0a0fb9092eb6b569`
- 同失败连续次数：0
- 更新时间：2026-11-21T10:15:14Z
- 摘要：A1–A6 全部通过。损坏 PDF 的页面提示已在真实环境确认，第 5 轮独立探针同时确认有效文件仍通过校验，截断的 Office 文件会得到可操作的失败提示。

## 逐项验收明细

| 编号 | 结果 | 来源 | 验收项 | 依据 |
| --- | --- | --- | --- | --- |
| A1 | passed | brief.md | 通过 MES 页面分别上传包含唯一标记的 PDF、DOCX、XLSX、PPTX，四份文件均完成索引，可以预览提取文本，问答返回对应标记且引用正确文件。 | 真实页面上传的 PDF、DOCX、XLSX 和 PPTX 均完成索引，预览及同步问答包含各自的唯一标记，引用指向正确文件及片段。第 5 轮独立探针对同一批有效原件复跑了当前 Java 校验，四种格式均通过。（native-file-e2e.log:1–13；native-files.cjs:44–82、99–109；file-results.json:2–25；第 5 轮独立 Java 探针） |
| A2 | passed | brief.md | 下载的原始文件与上传文件字节一致。现有 UTF-8 文件可上传、预览、下载、更新版本和删除。二进制文件沿用相同的上传权限与版本和删除流程，其他租户账号无法访问。 | 四种二进制文档及 UTF-8 文本、Markdown 的下载字节均与原件一致。真实环境验证了文本和 PDF 的版本导入、预览、字节核对、删除和旧版本保留，另一租户读取 PDF 正文或原件均返回 A0404。本轮上传错误分支的修复保留了有效原件和 UTF-8 文件的通过路径。（file-results.json:2–36、54；live-chat.cjs:96–115；native-files.cjs:75–92；RagDocumentServiceImpl.java:55–94；第 5 轮独立 Java 探针） |
| A3 | passed | brief.md | 扫描版 PDF、加密或损坏文件、超过单文件 20 MiB 的文件获得明确失败提示，且无法进入检索。Docker 复用既有 LightRAG 依赖和模型配置。 | 真实页面上传仅首字节受损的 PDF 后，显示文件可能损坏、加密或与扩展名不符的操作提示，响应没有索引任务号。Java 回归确认拒绝发生在转发前，独立探针也确认首字节受损的 PDF 和截断的 DOCX 均走新提示。已有真实回执覆盖无文字层、加密及保留文件头的损坏 PDF 的失败状态和检索排除，以及超出 20 MiB 的拒绝。第 5 轮 Docker 健康检查通过，LightRAG 依赖及模型配置沿用原配置。（invalid-pdf-header.log:1；invalid-pdf-header.cjs:22–33；RagFileSignature.java:28–49；RagErrorCodeConstants.java:51–53；RagClientTest.java:60–69；native-file-e2e.log:8–11；file-results.json:55；docker-up.log:31–37） |
| A4 | passed | brief.md | 将 `<think>` 标签拆在不同分片，流式页面、同步响应和刷新后的历史都只含回答正文。覆盖未闭合思考块、旧消息、代码块中的同名示例标签，以及清理后紧跟 `###` 的标题。 | 同步、流式和旧历史继续共用思考过滤。旧历史回放保留了列表和引用中的字面标签，并隐藏围栏后的思考。第 4 轮独立探针用 109 种分片长度复核了列表围栏空行、分片标签、紧接的标题和未闭合的思考块。第 5 轮 Java 检查通过，相关过滤源码沿用该实现。（RagAnswerTextTest.java:9–45；historical-reply.log:1；RagStreamClient.java:273–306；RagClient.java:280；RagConversationService.java:176、261；第 5 轮 java-rag.log） |
| A5 | passed | brief.md | 在 1280、1920、2560 像素宽桌面及 390 像素窄屏实际渲染。长会话标题未撑宽历史栏，输入区与来源面板可操作，长表格不会让整个页面产生横向滚动。 | 第 4 轮独立浏览器复跑确认 1280、1920 和 2560 像素视口的历史栏宽约为 220、269 和 280 像素，390 像素视口可打开历史抽屉。四种视口的长表格均在容器内横向滚动，页面宽度等于视口宽度，输入区和指定来源可操作。本轮沿用同一前端实现。（chat-layout.log:1；chat-ui.cjs:18–20、57–87；chat-layout-results.json；第 4 轮独立浏览器探针） |
| A6 | passed | brief.md | 流式结束和历史重载均能正确呈现标题、列表、引用、链接、代码块和表格。数字引用展开对应来源，模型生成的 HTML 不会作为页面代码执行。 | 真实模型 SSE 和刷新后的历史均呈现标题、列表、引用、链接、代码块和表格。数字引用可打开来源，HTML 以文本呈现且未执行。第 4 轮独立浏览器复跑确认列表围栏中的空行和字面标签仍在代码节点内，围栏后的标题正常渲染。本轮沿用同一渲染和消息处理实现。（chat-live-lifecycle.log:2；live-chat.cjs:67–94；chat-ui.cjs:46–56、82–85；RagAnswer.ts:129–162；第 4 轮独立浏览器探针） |

## 执行检查记录

| ID | 命令 | 状态 | 退出码 | 耗时 |
| --- | --- | --- | ---: | ---: |
| frontend-build | `npm --prefix frontend-web run build` | passed | 0 | 33500 ms |
| layout-detection | `bash -c "$HOME/.codex/skills/impeccable/scripts/impeccable" detect --json frontend-web/src/views/rag frontend-web/src/components/rag frontend-web/src/layouts/BasicLayout.vue` | passed | 0 | 1870 ms |
| historical-reply | `python .codex_tmp/rag-native-verification/history-replay.py` | passed | 0 | 2628 ms |
| chat-layout | `node .codex_tmp/rag-native-verification/chat-ui.cjs` | passed | 0 | 4798 ms |
| lightrag-image | `docker compose --progress plain -f docker-compose.yml -f compose.lightrag.yml build lightrag` | passed | 0 | 7591 ms |
| frontend-unit | `node --test frontend-web/tests/knowledge.test.cjs tests/rag-sse.test.mjs` | passed | 0 | 721 ms |
| python-tenant | `docker run --rm --mount type=bind,source=C:/Users/23156/Desktop/java/badminton-mes/lightrag-service,target=/src,readonly --workdir /src --env PYTHONPATH=/src:/src/LightRAG badminton-mes/lightrag:rag-native-files sh -c pip install --timeout 25 --retries 2 pytest pytest-asyncio && python -m pytest -q -p no:cacheprovider tests/test_document_content.py tests/test_app_routes.py tests/test_server.py` | passed | 0 | 28887 ms |
| native-file-e2e | `node .codex_tmp/rag-native-verification/native-files.cjs` | passed | 0 | 49501 ms |
| chat-live-lifecycle | `node .codex_tmp/rag-native-verification/live-chat.cjs` | passed | 0 | 32798 ms |
| java-rag | `bash -c cd backend-java && bash ./gradlew --offline --no-daemon test --tests "com.badminton.mes.module.rag.*" bootJar` | passed | 0 | 79316 ms |
| docker-up | `docker compose -f docker-compose.yml -f compose.lightrag.yml up -d --no-build --wait --wait-timeout 240` | passed | 0 | 29458 ms |
| invalid-pdf-header | `node .codex_tmp/rag-native-verification/invalid-pdf-header.cjs` | passed | 0 | 5581 ms |
| diff-clean | `git diff --check` | passed | 0 | 198 ms |

## 历史验收轮次

| 轮次 | 结果 | 失败项 | 摘要 | 时间 |
| ---: | --- | --- | --- | --- |
| 1 | fail | A1, A2, A3, A4, A5, A6 | 本轮发现两处可由源码确认的功能缺陷，分别是 UTF-8 原件字节丢失和未闭合反引号后的思考内容泄露。其余失败项已有部分真实回执，需要补齐指定场景的端到端证据。 | 2026-11-21T09:25:36Z |
| 2 | fail | A4 | A1、A2、A3、A5、A6 通过。A4 存在可复现的思考内容泄漏，列表内 Markdown 围栏结束后，后端过滤器仍可能保留后续思考块。 | 2026-11-21T09:40:43Z |
| 3 | fail | A4, A6 | A1、A2、A3、A5 通过。列表内围栏的正常空行仍会触发思考内容泄漏及 Markdown 块渲染错误，A4、A6 未通过。 | 2026-11-21T09:54:59Z |
| 4 | fail | A3 | A1、A2、A4、A5、A6 通过。A3 的文件头损坏场景已拦截，但提示把受支持格式误报为类型不支持，未满足明确失败提示的验收要求。 | 2026-11-21T10:08:45Z |
| 5 | pass | — | A1–A6 全部通过。损坏 PDF 的页面提示已在真实环境确认，第 5 轮独立探针同时确认有效文件仍通过校验，截断的 Office 文件会得到可操作的失败提示。 | 2026-11-21T10:15:14Z |

