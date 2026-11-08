# Comet Native 实战问题与规避指南

Windows 11 + Cursor + comet 0.5.3，本项目在 P1～P3 三个 change 中踩过的坑。
每条给出**现象 → 原因 → 解决**。接手前请先读完 A 与 G 两节。

---

## A. 环境与工具层

### A1. Windows 控制台中文乱码

**现象**：中文输出变成 `������`，看错误信息全靠猜。

**解决**：命令前加 `PYTHONIOENCODING=utf-8`；或把 stdout 重定向到文件再用 ReadFile 读。
入口脚本可加 `sys.stdout.reconfigure(encoding="utf-8", errors="replace")`。

### A2. `comet --json` 输出前面带日志行

**现象**：`json.loads(整个文件)` 报 `Expecting value: line 1 column 2`。

**原因**：Comet 会在 JSON 前打印 `[Comet] 执行工作流命令，已用 5 秒`。

**解决**：用锚点定位，不要用 `index("{")`：
```python
i = text.index('{"command"')            # 大多数命令
i = text.index('{"protocolVersion"')    # runtime dispatch
d = json.loads(text[i:])
```

### A3. 长内容写文件被截断

**现象**：heredoc 写 300 行以上文件，写到一半结束，报 `unterminated string literal`。

**解决**：单文件分 2～4 块写，每块写完立即 `ast.parse` 校验；
或先写 `%TEMP%/part1.py`、`part2.py` 再拼接。

### A4. 一次调用写多个文件导致内容混合（最严重）

**现象**：`tenant/__init__.py` 变成 `registry.py` 的字节副本；
`tests/conftest.py` 里混进了另外两个测试文件的全部内容。

**原因**：同一写入调用提交多个文件时，后一个内容覆盖/追加到了前一个。

**解决**：**一个文件一次调用**。写完立即 `wc -l` + `head` 核对开头是否是自己期望的内容。
出现混合后用「唯一 marker → 切割 → 分别落盘」拆分。

---

## B. 正式文档规约（Shape 阶段最容易返工）

### B1. brief 必须有四个核心章节

`# 目标`、`# 范围`、`# 非目标`、`# 验收示例`，内容不能为空。

### B2. `# 范围` 下必须有 `## 目录结构` 与四个三级子节

`### 新建`、`### 修改`、`### 删除`、`### 明确不建`，无内容写「无」，不能省略。
Runtime 逐项检查存在性与非空。

### B3. 验收项来源只能二选一

| 做法 | 结果 |
| --- | --- |
| brief 的 `# 验收示例` 写 `- ` 顶层列表项 | ✅ 每项生成一个验收 ID |
| spec 里写完整 `Scenario:` 块 | ✅ 每个 Scenario 生成一个验收 ID |
| brief 写 6 条 + spec 又写 `验收：A1`～`A10` | ❌ 编号对不上，只生成 6 项 |

本项目做法：全部写成 brief 的 `- ` 列表项，spec 里**不写** `验收：Ax` 引用。

### B4. 没有目标 Spec 会被拒绝

`spec-exemption-missing (brief.md): This change declares no target Spec.`
`specs/<capability>/spec.md` 必须存在且完整；纯文档改动也要写明具体豁免理由。

### B5. `specs/` 下只能放 `spec.md`，其他文件名会被 Hook 拒绝。

---

## C. Builder 交接与 Verifier 回报

### C1. `acceptanceReview[*].evidence` 必须是字符串数组

```
OUTCOME_REJECTED: acceptance_review[0] (A1).evidence must be an array of
concrete strings of at most 4000 characters; received string
```
正确形状：
```json
{"id": "A1", "status": "implemented-with-evidence",
 "evidence": ["证据一", "证据二"], "note": "补充说明"}
```

### C2. 不要加 `outcomeId` 字段

`INVALID_REQUEST: ... 不支持字段：outcomeId`。请求体只有 `operation` / `receipt` / `result`。

### C3. Builder 交接必须覆盖全部验收项

`Native Builder handoff is incomplete; missing: A1, A2, A5, ...`
**每一轮（含修复轮）**都要写全部验收项，没改动的也要给回归确认的证据。

### C4. evidence 要写实测事实，不能写结论

差的：`"已实现租户隔离"`
好的：`"python -m tenant.check 退出码 0，两行 input 路径分别以 ws_001 / ws_002 结尾"`

Verifier 会独立复跑，写空话挡不住。

---

## D. 归档阶段的高危坑（本项目损失最多时间的地方）

### D1. rename 报 EPERM（Windows 特有）

**现象**：
```
EPERM: operation not permitted, rename
'docs/comet/changes/<change>' -> 'docs/comet/archive/<日期>-<change>'
```
反复 `--recover` 仍失败。

**原因**：Cursor 文件监听器对 `docs/` 目录树持有句柄。

**关键判据**（可据此确认不是权限问题）：
同路径 Python `os.rename` 报 WinError 5，但 PowerShell `Move-Item` **每次成功**；
且仓库内其他目录（如 `lightrag-service/tests`）可以正常改名。

**解决**：用 PowerShell 完成同一动作，目标名必须与 Runtime 期望**完全一致**：
```powershell
Move-Item -LiteralPath 'docs\comet\changes\<change>' `
          -Destination 'docs\cometrchive\<日期>-<change>'
```
再执行 `comet native archive <change> --recover`。

**副作用警告**：手动移动后**不要**再跑 `cancel`——Runtime 仍去 `changes/` 找它，会报 `brief-missing`。

### D2. `Canonical Spec changed after Archive applied it`（CRLF 陷阱）

**现象**：报规格在归档后被改动，但磁盘文件与归档快照**字节完全相同**。

**原因**：
- Windows 上 `git config core.autocrlf=true`，检出时把 LF 改写成 CRLF；
- Runtime 事务记录的哈希按 **LF** 计算；
- 归档校验：`create` 比**文本**，已应用的比**哈希**。

**解决**：
```python
b = pathlib.Path(spec_path).read_bytes()
pathlib.Path(spec_path).write_bytes(b.replace(b'
', b'
'))
```
转 LF 后 sha256 立刻与事务记录一致。

**预防**：本仓库 `core.autocrlf=true` 会自动制造此问题，涉及 Comet 正式文档前后都检查换行符。

### D3. 能力关联导致归档**永久卡死**（最严重，P2 就死在这）

**现象**：新建 change 时没做任何关联操作，但 `capability-association.yaml` 自动指向了上一个阶段的能力；
归档事务里该能力被记成 `operation: modify`，而 `delta.yaml` 是 `operations: []`、
`base_hash` 也对不上 → 归档永远无法 finalize，连 `cancel` 都被挡住。

**原因**：`comet native new` 会按任务描述做能力检索，命中历史能力就自动关联。

**预防（比修复便宜得多）**：
1. **建完 change 立刻检查** `docs/comet/changes/<change>/capability-association.yaml`；
2. 若被关联到不该关联的历史能力，**在 Shape 阶段**执行：
   ```bash
   comet native spec disassociate <change>      --expected-state-version <n> --expected-action disassociate-capability
   ```
3. 每个阶段用**独立能力名**（`-foundation` / `-server` / `-verifier` / `-client`），
   不要复用上一阶段的能力目录。

**已卡死时**：此状态无法自愈，只能把有价值内容迁出后清理运行时状态。

### D4. `Native Archive transaction is stale`

`state.state_version !== transaction.start_state_version`。
事务推进中把状态版本改成了 13，但事务自身仍记 12，自我判定过期——Comet 内部不一致。
同样无法通过编辑文件修复。

---

## E. verify.checks 中断与恢复

### E1. 运行时检查被打断

**现象**：
```
Native 检查输入快照不完整：原始输入在观察期间变化：README.md；.gitignore；.env.example；...
```

**原因**：Comet 运行时检查对整个工作区做**文件指纹快照**。检查执行期间只要有文件变动
（含大目录新增/删除、`core.autocrlf` 改写换行），就判「输入在观察期间变化」而作废。

**本项目实际踩法**：Verify 期间还在改文档、移动归档目录 → 反复中断。

**预防**：
1. **进入 Verify 前不要动工作区**，需要改的在 Build 结束前改完；
2. 无关改动先 `git commit` 或 `git stash`，让工作区尽量小；
3. 一次 `native next` 执行完再动其他文件。

### E2. 检查 Action 卡在 running / unknown

**现象**：`能否由原宿主确认此执行及全部后代进程已经停止？`

**解决**：确认进程确实结束后，提交**带四个标识字段**的 `--stopped-actions`：
```json
[{
  "actionId": "lightrag-isolation-verifier:8",
  "attempt": 1,
  "inputHash": "<从 status 的 activeActions 取>",
  "claimToken": "<从 activeActions[].claim.token 取>",
  "evidence": "宿主同步执行 pytest，命令返回后即结束；已核对进程列表无残留。"
}]
```
```bash
comet native doctor <change> --repair --confirmed --stopped-actions <该文件>
```
**只写 reason 不写这四个字段会被拒**：`sdk-run-invalid`。

### E3. `resolve-loop-stop` 要求重新提交 Builder 候选

**现象**：`Runtime checks completed, but their input changed before acceptance`

这不是报错，是让你**提交新候选**。用 `--revise-implementation` 重新走 Build，
**不要重跑原检查**，也不要声称它从未执行。

---

## F. 任务收尾

### F1. 两个学习检查彼此独立

```bash
comet task . --complete --workflow native --change <change>   --learning-check no-observation   --project-learning-check submitted --memory <slug>
```
`--learning-check` 是个人记忆，`--project-learning-check` 是项目经验。

### F2. 项目记忆写入

```bash
comet knowledge remember . --title "<简明标题>"   --text "<现象、做法、验证结果>" --type failure-resolution   --workflow native --change <change> --json
```

---

## G. 给接手 agent 的流程建议（按本项目教训排序）

| 优先级 | 做法 | 为什么 |
| --- | --- | --- |
| 1 | **一次 change 只做一个阶段**，不把已完成能力关联进新 change | P2 因自动关联到 P1 而永久卡死 |
| 2 | **开工前确保工作区干净**（`git status` 为空） | P3 卡在运行时检查因文件指纹变化中断 |
| 3 | **建完 change 先看 `capability-association.yaml`** | 意外关联趁 Shape 撤销，比归档时救便宜得多 |
| 4 | **每个阶段结束立即 commit** | 减少 Verify 期间工作区变动面 |
| 5 | **写文件后立刻校验**（`ast.parse` / `wc -l` / `head`） | 截断与内容混合各踩过一次 |
| 6 | **Builder 每轮写全部验收项** | 只写改动项会被直接拒绝 |
| 7 | **证据要具体**（命令 + 退出码 + 关键输出） | Verifier 独立复跑，结论性描述没用 |

### 本项目三个 change 的结局

| change | 结局 | 卡点 |
| --- | --- | --- |
| `lightrag-multitenant-foundation`（P1） | ✅ 归档，9/9 | 无（EPERM 用 PowerShell 绕过） |
| `lightrag-multitenant-server`（P2） | ⚠️ 代码已提交，Comet 归档事务永久卡死 | D3 能力关联 + D4 事务 stale |
| `lightrag-isolation-verifier`（P3） | ⚠️ 代码已提交，未完成独立验收 | E1 运行时检查反复中断 |

最终处理：设计与验收内容迁到 `docs/lightrag-rag/` 后清理运行时状态，
`comet native status` 回到 0 个 change。

---

## H. 判断「继续修」还是「停手」

按这个顺序判断，避免在不可自愈的状态上反复尝试：

1. 跑 `comet native doctor <change> --json`，看 `findings[].code`：
   | code | 处理 |
   | --- | --- |
   | `sdk-archive-transaction-incomplete` | `--recover` 尝试 |
   | `shape-drift` | 检查正式文档是否被改动（**含换行符**） |
   | `native-execution-*` | 提交 `--stopped-actions` |
   | `sdk-run-invalid` | 检查 stopped-actions 是否缺四个标识字段 |
2. **同一错误连续出现 2 次且修复无效 → 停止**，不要试第三次。
3. 以下任一情况直接判定**不可自愈**：
   - 事务 `start_state_version` 与当前 `state_version` 不一致；
   - `archive.execute` 已 succeeded 但 `archive.finalize` 永远失败；
   - `cancel` 被归档事务挡住，而归档事务又修不好。
4. 判定不可自愈后：**先迁出有价值产物**（设计文档、验收结论），再清理：
   ```
   docs/comet/changes/
   docs/comet/archive/
   .comet/current-change.json
   .comet/runtime
   ~/.comet/workspaces/<workspaceId>/runtime/native/
   ```
   **保留 `.comet/config.yaml`**，下次可直接新建 change。
