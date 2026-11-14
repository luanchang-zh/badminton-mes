// 由 Playwright browser_run_code_unsafe 以 filename 加载；接口替身仅存在于隔离浏览器上下文。
async page => {
  const baseURL = 'http://127.0.0.1:5173'
  const browser = page.context().browser(), checks = []
  const assert = (condition, label) => { if (!condition) throw new Error(label); checks.push(label) }
  const now = '2026-11-13T09:42:00'
  const reference = { referenceId: '1', sourceName: '装配线设备点检规程.md', chunks: ['检查电源、急停按钮和防护装置。', '确认传动带与链条没有松动，空载运行无异响。'] }
  const answer = '开机前，建议按以下顺序完成设备点检。[1]\n\n1. **电源与安全装置**：检查电源、急停按钮和防护装置。\n2. **传动机构**：检查传动带、链条及紧固件。\n3. **气路与作业区域**：确认气压正常，清理设备周围的杂物。\n4. **空载试运行**：观察有无异响和异常振动。\n\n发现异常时，先停止开机并报修。[1]'
  const msg = (id, conversationId, role, content, status = 'COMPLETED', replyToId = null) => ({ id, conversationId, role, content, status, replyToId, mode: 'naive', references: role === 'assistant' && status === 'COMPLETED' ? [reference] : [], errorTip: null, createdAt: now, updatedAt: now })
  async function setup(config = {}) {
    const context = await browser.newContext({ viewport: { width: 1920, height: 920 } })
    const p = await context.newPage(), errors = [], calls = []
    p.on('pageerror', error => errors.push(error.message))
    const state = {
      conversations: [1, 2, 3].map((id, index) => ({ id, title: ['装配线设备点检', '工序报工与质量追溯', '设备异常停机处理'][index], activeMessageId: null, createdAt: now, updatedAt: now })),
      messages: new Map([[1, [msg(11, 1, 'user', '装配线开机前需要做哪些检查？'), msg(12, 1, 'assistant', answer, 'COMPLETED', 11)]], [2, [msg(21, 2, 'user', '报工流程是什么？'), msg(22, 2, 'assistant', '按工序提交报工记录。', 'COMPLETED', 21)]], [3, []]]),
      nextId: 100, nextConversation: 4, nextStream: 'success', lastRequest: null, profile: { bound: true, enabled: true, canManageDocuments: false },
      documents: [{ docId: 'doc-1', fileName: reference.sourceName, status: 'COMPLETED' }], ...config,
    }
    await p.addInitScript(({ demo }) => {
      if (!sessionStorage.getItem('rag-test-seeded')) {
        sessionStorage.setItem('rag-test-seeded', '1'); localStorage.clear()
        localStorage.setItem('mes_token', demo ? 'mock-demo-session' : 'isolated-rag-session')
        localStorage.setItem('mes_user', JSON.stringify({ userId: 914, userNo: 'rag-test', userName: '王师傅', roleCodes: ['OPERATOR'] }))
      }
      // 把已收到的 SSE 响应分段交付，覆盖停止、切换与早期取消的竞态。
      const original = window.fetch
      window.__ragAborts = 0
      window.fetch = async (input, init) => {
        const response = await original(input, init)
        if (!response.headers.get('x-rag-test-slow')) return response
        const text = await response.text(), frames = text.split(/(?<=\n\n)/).filter(Boolean)
        let timer, index = 0, closed = false
        const stream = new ReadableStream({
          start(controller) {
            const step = () => {
              if (closed) return
              if (index >= frames.length) { closed = true; controller.close(); return }
              controller.enqueue(new TextEncoder().encode(frames[index++])); timer = setTimeout(step, 650)
            }
            timer = setTimeout(step, response.headers.get('x-rag-test-early') ? 1800 : 0)
            init.signal.addEventListener('abort', () => { window.__ragAborts++; clearTimeout(timer); if (!closed) { closed = true; controller.error(new DOMException('Aborted', 'AbortError')) } }, { once: true })
          },
          cancel() { clearTimeout(timer); closed = true },
        })
        return new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } })
      }
    }, { demo: config.demo })
    await p.route(`${baseURL}/api/**`, async route => {
      const request = route.request(), url = new URL(request.url()), path = url.pathname, method = request.method()
      const body = request.postDataJSON(), record = { path, method, body, authorization: request.headers().authorization }
      calls.push(record)
      const ok = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: '00000', message: 'ok', userTip: null, data }) })
      if (path.endsWith('/auth/profile')) return ok({ userId: 914, userNo: 'rag-test', userName: '王师傅', roleCodes: ['OPERATOR'] })
      if (path.endsWith('/face/profile')) return ok({ enrolled: true, faceLoginAllowed: true, faceCount: 1, maxFaces: 5, samples: [] })
      if (path.endsWith('/auth/logout')) return ok(null)
      if (path === '/api/rag/profile') return ok(state.profile)
      if (path === '/api/rag/documents') {
        if (state.documentsError) return route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify({ code: 'C0301', message: 'unreachable', userTip: '问答服务暂时无法连接', data: null }) })
        return ok(state.documents)
      }
      if (path === '/api/rag/conversations') {
        if (method === 'POST') {
          const created = { id: state.nextConversation++, title: '新对话', activeMessageId: null, createdAt: now, updatedAt: now }
          state.conversations.unshift(created); state.messages.set(created.id, []); return ok(created)
        }
        const pageSize = Number(url.searchParams.get('pageSize')), pageNo = Number(url.searchParams.get('pageNo'))
        return ok({ list: state.conversations.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: state.conversations.length, pageNo, pageSize })
      }
      const conversationMatch = /^\/api\/rag\/conversations\/(\d+)$/.exec(path)
      if (conversationMatch) {
        const id = Number(conversationMatch[1])
        if (method === 'PATCH') {
          if (state.renameError) return route.fulfill({ status: 400, contentType: 'application/json', body: JSON.stringify({ code: 'A0400', message: 'rename failed', userTip: '名称保存失败，请重试', data: null }) })
          const conversation = state.conversations.find(c => c.id === id); conversation.title = body.title; return ok(conversation)
        }
        state.conversations = state.conversations.filter(c => c.id !== id); state.messages.delete(id); return ok(null)
      }
      const historyMatch = /^\/api\/rag\/conversations\/(\d+)\/messages$/.exec(path)
      if (historyMatch) {
        const id = Number(historyMatch[1]), all = state.messages.get(id) ?? [], pageSize = Number(url.searchParams.get('pageSize'))
        const pageNo = Math.max(1, Math.min(Number(url.searchParams.get('pageNo')), Math.ceil(all.length / pageSize)))
        if (state.delayedHistory === id) await new Promise(resolve => setTimeout(resolve, 700))
        return ok({ list: all.slice((pageNo - 1) * pageSize, pageNo * pageSize), total: all.length, pageNo, pageSize })
      }
      const cancelMatch = /^\/api\/rag\/messages\/(\d+)\/cancel$/.exec(path)
      if (cancelMatch) {
        const message = [...state.messages.values()].flat().find(m => m.id === Number(cancelMatch[1]))
        if (message.status === 'GENERATING') message.status = 'CANCELLED'
        return ok(message)
      }
      const retryMatch = /^\/api\/rag\/messages\/(\d+)\/retry$/.exec(path)
      if (path === '/api/rag/query/stream' || retryMatch) {
        if (state.nextStream === 'network-error') { state.nextStream = 'success'; return route.abort('failed') }
        if (state.nextStream === 'unauthorized') return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'A0230', message: 'expired', userTip: '登录状态已失效，请重新登录', data: null }) })
        const old = retryMatch && [...state.messages.values()].flat().find(m => m.id === Number(retryMatch[1]))
        const conversationId = old ? old.conversationId : body.conversationId, all = state.messages.get(conversationId)
        let question = old && all.find(m => m.id === old.replyToId)
        if (!old) { question = msg(state.nextId++, conversationId, 'user', body.question); all.push(question) }
        const message = msg(state.nextId++, conversationId, 'assistant', '', 'GENERATING', question.id)
        all.push(message); state.lastRequest = body
        const behavior = state.nextStream; state.nextStream = 'success'
        const frame = (event, data) => `event: ${event}\ndata: ${typeof data === 'string' ? data : JSON.stringify(data)}\n\n`
        let output = frame('meta', { conversationId, userMessageId: question.id, assistantMessageId: message.id })
        if (behavior === 'error') {
          message.status = 'FAILED'; message.errorTip = '问答服务暂时无法连接'
          output += frame('error', { code: 'C0301', message: 'unreachable', userTip: message.errorTip })
        } else if (behavior === 'truncated') {
          message.content = '部分回答'; message.status = 'CANCELLED'; output += frame('delta', '部分回答')
        } else {
          const content = behavior === 'unsafe' ? '<img src=x onerror="window.__ragXss=1">\n\n[危险](javascript:alert(1))\n\n- \n\n```html\n<script>window.__ragXss=2</script>\n```' : '检查安全装置和传动机构。[1]'
          output += frame('reference_details', [reference]) + frame('delta', content)
          message.references = [reference]; message.content = content
          if (!['slow', 'early'].includes(behavior)) { message.status = 'COMPLETED'; output += frame('done', message) }
          else {
            for (let i = 0; i < 12; i++) output += frame('delta', `后续片段${i}。`)
          }
        }
        return route.fulfill({ contentType: 'text/event-stream', headers: ['slow', 'early'].includes(behavior) ? { 'x-rag-test-slow': '1', ...(behavior === 'early' ? { 'x-rag-test-early': '1' } : {}) } : {}, body: output })
      }
      return ok([])
    })
    await p.goto(`${baseURL}/rag/chat`)
    await p.locator('.rag-workspace').waitFor()
    await p.waitForFunction(() => !document.querySelector('.rag-empty')?.innerText.includes('正在加载'))
    await p.waitForTimeout(120)
    return { context, p, errors, calls, state }
  }
  const envs = []
  try {
    const env = await setup(); envs.push(env)
    const { p, state, calls, errors } = env
    await p.locator('.rag-answer').first().waitFor()
    assert(await p.locator('.rag-conversation').count() === 3 && await p.locator('.rag-conversation').first().evaluate(el => el.getBoundingClientRect().height) === 56, '历史会话为 56px，侧栏从真实分页接口读取')
    assert(calls.filter(c => c.path.startsWith('/api/rag/')).every(c => c.authorization === 'Bearer isolated-rag-session'), '全部 RAG 请求携带 MES 登录凭证')
    assert(!await p.getByText('Enter 发送，Shift + Enter 换行', { exact: true }).count() && !await p.getByText('对话仅自己可见', { exact: true }).count(), '删除用户指定的底部说明')
    const composerBorder = await p.locator('.rag-composer').evaluate(el => getComputedStyle(el).borderColor)
    await p.locator('#rag-question').click(); await p.locator('#rag-question').fill('边框检查')
    assert(await p.locator('#rag-question').evaluate(el => getComputedStyle(el).outlineStyle === 'none' && getComputedStyle(el).boxShadow === 'none') && composerBorder === await p.locator('.rag-composer').evaluate(el => getComputedStyle(el).borderColor), '鼠标输入与打字保持原边框，无高亮或光晕')
    await p.locator('select[aria-label="检索方式"]').click(); await p.keyboard.press('Escape')
    assert(await p.locator('select[aria-label="检索方式"]').evaluate(el => getComputedStyle(el).outlineStyle === 'none'), '鼠标选择检索模式无焦点高亮')
    await p.locator('#rag-question').click(); await p.keyboard.press('Tab')
    assert(await p.locator('select[aria-label="检索方式"]').evaluate(el => getComputedStyle(el).outlineStyle === 'solid'), '键盘 Tab 保留可见焦点')
    await p.locator('#rag-question').fill('')
    await p.getByRole('button', { name: '查看来源 1', exact: true }).first().click()
    assert(await p.locator('.rag-sources').isVisible() && await p.locator('.rag-source-excerpts blockquote').count() === 2, '引用编号展开结构化来源及全部原文片段')
    await p.screenshot({ path: '/tmp/mes-rag-vue-1920.png' })
    await p.locator('.rag-sources').getByRole('button', { name: '关闭来源' }).click()
    await p.setViewportSize({ width: 1366, height: 768 })
    await p.getByRole('button', { name: '查看来源 1', exact: true }).first().click()
    await p.locator('.rag-source-drawer').waitFor()
    await p.waitForTimeout(350)
    assert(await p.locator('.rag-source-drawer').evaluate(el => Math.abs(el.getBoundingClientRect().right - innerWidth) < 2 && el.getBoundingClientRect().left < innerWidth - 300), '来源抽屉完整进入当前桌面视口')
    assert(await p.locator('.el-overlay').last().isVisible(), '窄桌面来源使用遮罩抽屉')
    await p.screenshot({ path: '/tmp/mes-rag-vue-1366.png' })
    await p.keyboard.press('Escape'); await p.waitForTimeout(350)
    assert(!await p.locator('.rag-source-drawer').isVisible(), '来源抽屉支持 Escape 关闭')
    assert(await p.evaluate(() => document.activeElement?.getAttribute('aria-label') === '查看来源 1'), '来源抽屉关闭后焦点返回引用按钮')
    for (const viewport of [{ width: 1201, height: 797 }, { width: 1366, height: 768 }, { width: 1920, height: 920 }]) {
      await p.setViewportSize(viewport)
      assert(await p.evaluate(() => { const r = document.querySelector('.rag-composer').getBoundingClientRect(); return r.bottom <= innerHeight && document.documentElement.scrollWidth <= innerWidth && document.documentElement.scrollHeight <= innerHeight }), `${viewport.width}×${viewport.height} 输入区可见且页面无溢出`)
    }
    const other = p.locator('.rag-conversation').filter({ hasText: '工序报工与质量追溯' })
    await other.hover(); await other.getByRole('button', { name: '管理会话：工序报工与质量追溯' }).click(); await p.getByRole('menuitem', { name: '重命名' }).click()
    await p.locator('#rag-conversation-title').fill('')
    await p.getByRole('button', { name: '保存', exact: true }).click()
    assert(await p.getByRole('alert').filter({ hasText: '请输入 1～120 字' }).isVisible(), '重命名校验空名称')
    await p.locator('#rag-conversation-title').fill('报工记录查询'); state.renameError = true
    await p.getByRole('button', { name: '保存', exact: true }).click()
    await p.getByText('名称保存失败，请重试', { exact: true }).waitFor()
    assert(await p.locator('#rag-conversation-title').inputValue() === '报工记录查询', '重命名失败保留弹窗和输入')
    state.renameError = false; await p.getByRole('button', { name: '保存', exact: true }).click(); await p.waitForTimeout(150)
    assert((await p.locator('.rag-chat-title h1').innerText()) === '装配线设备点检' && state.conversations.find(c => c.id === 2).title === '报工记录查询', '非当前会话可以重命名，当前对话保持不变')
    await p.getByRole('searchbox', { name: '搜索历史对话' }).fill('报工记录')
    assert(await p.locator('.rag-conversation').count() === 1, '历史会话搜索使用服务器返回的会话')
    await p.getByRole('searchbox', { name: '搜索历史对话' }).fill('')
    await p.getByRole('button', { name: '新建对话', exact: true }).click()
    await p.locator('.rag-empty h2').filter({ hasText: '有什么需要查询的' }).waitFor()
    assert(state.conversations.length === 4 && await p.locator('.rag-conversation.is-active').innerText().then(text => text.includes('新对话')), '最近对话旁的图标创建并选中新会话')
    await p.locator('.rag-examples button').first().click()
    assert(await p.locator('#rag-question').inputValue() === '装配线开机前需要做哪些检查？', '示例问题只填入输入区')
    await p.locator('select[aria-label="检索方式"]').selectOption('mix')
    await p.locator('#rag-question').dispatchEvent('compositionstart')
    await p.locator('#rag-question').press('Enter')
    assert(!calls.some(c => c.path === '/api/rag/query/stream'), '中文输入法组合输入期间 Enter 不发送')
    await p.locator('#rag-question').dispatchEvent('compositionend')
    await p.locator('#rag-question').fill('开机检查'); await p.locator('#rag-question').press('Shift+Enter')
    assert((await p.locator('#rag-question').inputValue()).includes('\n'), 'Shift+Enter 保留换行')
    await p.locator('#rag-question').press('Enter')
    await p.locator('.rag-answer').filter({ hasText: '检查安全装置和传动机构' }).waitFor()
    await p.getByRole('button', { name: '发送', exact: true }).waitFor()
    assert(state.lastRequest.mode === 'mix' && state.lastRequest.conversationId === 4 && /^[\w-]{8,64}$/.test(state.lastRequest.requestId), '流式发送携带模式、会话与幂等 UUID，meta/delta/done 正确归并')
    state.nextStream = 'error'; await p.locator('#rag-question').fill('测试连接失败'); await p.getByRole('button', { name: '发送', exact: true }).click()
    await p.locator('.rag-message-status--error').waitFor(); const failedRequest = state.lastRequest.requestId
    assert(await p.locator('.rag-notice').innerText().then(text => text.includes('历史对话仍可查看')), '服务未连接保留问题、失败消息与历史')
    await p.getByRole('button', { name: '重新生成', exact: true }).last().click(); await p.getByRole('button', { name: '发送', exact: true }).waitFor()
    const retried = calls.filter(c => /\/retry$/.test(c.path)).at(-1)
    assert(retried && retried.body.requestId !== failedRequest && await p.locator('.rag-message-status--error').count() === 1, '重试使用新编号并保留原失败回答')
    state.nextStream = 'network-error'; await p.locator('#rag-question').fill('网络重发检查'); await p.getByRole('button', { name: '发送', exact: true }).click()
    await p.locator('.rag-message-status--error').filter({ hasText: '网络连接中断' }).waitFor()
    const interrupted = calls.filter(c => c.path === '/api/rag/query/stream').at(-1)
    await p.getByRole('button', { name: '重新生成', exact: true }).last().click(); await p.getByRole('button', { name: '发送', exact: true }).waitFor()
    assert(calls.filter(c => c.path === '/api/rag/query/stream').at(-1).body.requestId === interrupted.body.requestId, 'meta 前发送失败沿用原 UUID 重发，避免重复问题')
    state.nextStream = 'error'; await p.locator('#rag-question').fill('重试连接中断检查'); await p.getByRole('button', { name: '发送', exact: true }).click(); await p.locator('.rag-message-status--error').filter({ hasText: '问答服务暂时无法连接' }).last().waitFor()
    state.nextStream = 'network-error'; await p.getByRole('button', { name: '重新生成', exact: true }).last().click(); await p.locator('.rag-message-status--error').filter({ hasText: '网络连接中断' }).last().waitFor()
    const interruptedRetry = calls.filter(c => /\/retry$/.test(c.path)).at(-1)
    await p.getByRole('button', { name: '重新生成', exact: true }).last().click(); await p.getByRole('button', { name: '发送', exact: true }).waitFor()
    const resumedRetry = calls.filter(c => /\/retry$/.test(c.path)).at(-1)
    assert(resumedRetry.path === interruptedRetry.path && resumedRetry.body.requestId === interruptedRetry.body.requestId, '重试请求在 meta 前断线，保留原消息 ID 和重试 UUID')
    state.nextStream = 'slow'; await p.locator('#rag-question').fill('慢速回答'); await p.getByRole('button', { name: '发送', exact: true }).click()
    await p.getByRole('button', { name: '停止', exact: true }).waitFor(); await p.waitForTimeout(140)
    const slowMessage = [...state.messages.values()].flat().at(-1)
    await p.getByRole('button', { name: '停止', exact: true }).click(); await p.getByRole('button', { name: '发送', exact: true }).waitFor()
    assert(calls.some(c => c.path === `/api/rag/messages/${slowMessage.id}/cancel`) && await p.evaluate(() => window.__ragAborts > 0), '停止先调用后端取消接口，再关闭本地流')
    state.nextStream = 'slow'; await p.locator('#rag-question').fill('切换会话停止'); await p.getByRole('button', { name: '发送', exact: true }).click()
    await p.waitForTimeout(120); const activeMessage = [...state.messages.values()].flat().at(-1)
    const inactive = p.locator('.rag-conversation').filter({ hasText: '报工记录查询' }); await inactive.hover(); await inactive.getByRole('button', { name: '管理会话：报工记录查询' }).click(); await p.getByRole('menuitem', { name: '删除', exact: true }).click()
    await p.getByRole('dialog', { name: '删除会话' }).getByRole('button', { name: '删除', exact: true }).click()
    await p.waitForTimeout(120)
    assert(await p.getByRole('button', { name: '停止', exact: true }).isVisible() && !calls.some(c => c.path === `/api/rag/messages/${activeMessage.id}/cancel`), '删除其他会话不停止当前生成')
    await p.locator('.rag-conversation-select').filter({ hasText: '装配线设备点检' }).click()
    await p.locator('.rag-chat-title h1').filter({ hasText: '装配线设备点检' }).waitFor()
    await p.waitForTimeout(800)
    assert(calls.some(c => c.path === `/api/rag/messages/${activeMessage.id}/cancel`) && !await p.locator('.rag-messages').innerText().then(text => text.includes('后续片段')), '切换会话取消生成，晚到片段不会串到新对话')
    state.nextStream = 'unsafe'; await p.locator('#rag-question').fill('安全渲染检查'); await p.getByRole('button', { name: '发送', exact: true }).click()
    await p.getByRole('button', { name: '发送', exact: true }).waitFor()
    assert(await p.evaluate(() => !window.__ragXss && !document.querySelector('.rag-answer img') && !document.querySelector('.rag-answer a[href^="javascript:"]')), '模型 HTML、脚本和 javascript 链接按安全文本展示，空列表项不阻塞页面')
    state.nextStream = 'truncated'; await p.locator('#rag-question').fill('断线检查'); await p.getByRole('button', { name: '发送', exact: true }).click()
    await p.locator('.rag-message-status--error').filter({ hasText: '回答连接已中断' }).waitFor()
    assert(await p.locator('.rag-answer').last().innerText() === '部分回答', 'SSE 提前结束保留部分回答并提供重试')
    state.nextStream = 'early'; await p.locator('#rag-question').fill('meta 前停止'); await p.getByRole('button', { name: '发送', exact: true }).click()
    await p.getByRole('button', { name: '停止', exact: true }).click(); await p.getByRole('button', { name: '发送', exact: true }).waitFor()
    assert(await p.locator('.rag-message-status').last().innerText() === '已停止生成', 'meta 到达前也可以停止并保留已输入的问题')
    assert(errors.length === 0, '主流程没有浏览器脚本错误')
    await env.context.close()

    const paged = await setup(); envs.push(paged)
    paged.state.messages.set(1, Array.from({ length: 62 }, (_, i) => msg(500 + i, 1, i % 2 ? 'assistant' : 'user', `历史消息 ${i}`, 'COMPLETED', i % 2 ? 499 + i : null)))
    await paged.p.reload(); await paged.p.getByText('历史消息 61', { exact: true }).waitFor()
    assert(!await paged.p.getByText('历史消息 0', { exact: true }).count(), '进入历史会话先读取最新一页消息')
    await paged.p.getByRole('button', { name: '加载更早的消息' }).click(); await paged.p.getByText('历史消息 0', { exact: true }).waitFor()
    assert(await paged.p.locator('.rag-message').count() === 62, '向前加载历史消息不丢失、不重复')
    paged.state.delayedHistory = 3
    await paged.p.locator('.rag-conversation-select').filter({ hasText: '设备异常停机处理' }).click()
    await paged.p.locator('.rag-conversation-select').filter({ hasText: '工序报工与质量追溯' }).click()
    await paged.p.getByText('按工序提交报工记录。', { exact: true }).waitFor(); await paged.p.waitForTimeout(750)
    assert(await paged.p.locator('.rag-chat-title h1').innerText() === '工序报工与质量追溯' && await paged.p.getByText('按工序提交报工记录。', { exact: true }).isVisible(), '快速切换会话忽略过期历史响应')
    await paged.context.close()

    for (const [config, expected] of [
      [{ profile: { bound: false, enabled: false, canManageDocuments: false }, conversations: [] }, '尚未开通'],
      [{ profile: { bound: true, enabled: false, canManageDocuments: false }, conversations: [] }, '已停用'],
      [{ documents: [], conversations: [] }, '暂无资料'],
      [{ documents: [{ status: 'PROCESSING' }], conversations: [] }, '尚未就绪'],
      [{ demo: true }, '演示账号'],
    ]) {
      const e = await setup(config); envs.push(e)
      await e.p.locator('.rag-empty').getByText(new RegExp(expected)).waitFor()
      assert(await e.p.locator('#rag-question').isDisabled(), `${expected} 状态禁用发送并给出处理指引`)
      await e.context.close()
    }
    const pending = await setup({ profile: { bound: true, enabled: true, canManageDocuments: true, serviceConfigured: false }, conversations: [] }); envs.push(pending)
    await pending.p.getByText('问答服务待接入，当前可管理会话和编辑问题。', { exact: true }).waitFor()
    assert(!await pending.p.locator('#rag-question').isDisabled() && !await pending.p.getByRole('button', { name: '新建对话', exact: true }).isDisabled(), '服务待接入时输入与新建会话可用')
    await pending.p.locator('#rag-question').fill('接入前编辑问题')
    await pending.p.locator('select[aria-label="检索方式"]').selectOption('mix')
    await pending.p.locator('#rag-question').press('Enter')
    assert(await pending.p.locator('#rag-question').inputValue() === '接入前编辑问题' && await pending.p.getByRole('button', { name: '待接入', exact: true }).isDisabled(), '接入前可编辑问题和模式，Enter 保留草稿')
    await pending.p.getByRole('button', { name: '新建对话', exact: true }).click()
    await pending.p.locator('.rag-conversation').waitFor()
    assert(pending.state.conversations.length === 1, '接入前会话仍保存到 MES 接口')
    assert(!pending.calls.some(call => call.path === '/api/rag/documents' || call.path.includes('/query') || call.path.endsWith('/retry')), '服务未配置时页面不请求文档、问答或重试接口')
    await pending.context.close()

    const offline = await setup({ documentsError: true }); envs.push(offline)
    await offline.p.locator('.rag-answer').waitFor()
    assert(await offline.p.locator('.rag-notice').isVisible() && !await offline.p.locator('#rag-question').isDisabled(), '上游文档接口离线仍可看历史并重新尝试问答')
    await offline.context.close()

    const single = await setup({ conversations: [{ id: 1, title: '唯一会话', activeMessageId: null, createdAt: now, updatedAt: now }] }); envs.push(single)
    await single.p.locator('.rag-conversation-more').click(); await single.p.getByRole('menuitem', { name: '删除', exact: true }).click(); await single.p.getByRole('dialog', { name: '删除会话' }).getByRole('button', { name: '删除', exact: true }).click()
    await single.p.locator('.rag-empty h2').filter({ hasText: '有什么需要查询的' }).waitFor()
    assert(await single.p.locator('.rag-conversation').count() === 0 && !await single.p.locator('#rag-question').isDisabled(), '删除最后会话进入空对话，可继续提问')
    await single.context.close()

    const exit = await setup(); envs.push(exit)
    exit.state.nextStream = 'slow'; await exit.p.locator('#rag-question').fill('退出时停止'); await exit.p.getByRole('button', { name: '发送', exact: true }).click(); await exit.p.waitForTimeout(140)
    const exitMessage = exit.state.messages.get(1).at(-1)
    await exit.p.locator('.basic-layout__user').click(); await exit.p.getByRole('menuitem', { name: '退出登录' }).click(); await exit.p.waitForURL('**/login')
    const cancelIndex = exit.calls.findIndex(c => c.path === `/api/rag/messages/${exitMessage.id}/cancel`), logoutIndex = exit.calls.findIndex(c => c.path.endsWith('/auth/logout'))
    assert(cancelIndex >= 0 && logoutIndex > cancelIndex && exit.calls[cancelIndex].authorization === 'Bearer isolated-rag-session', '退出登录先用有效凭证停止生成，再销毁登录会话')
    assert(!await exit.p.locator('.rag-workspace').count(), '退出登录卸载聊天页面')
    await exit.context.close()

    const list = Array.from({ length: 101 }, (_, i) => ({ id: i + 1, title: i === 100 ? '跨页会话' : `会话 ${i + 1}`, activeMessageId: null, createdAt: now, updatedAt: now }))
    const searchAll = await setup({ conversations: list }); envs.push(searchAll)
    await searchAll.p.getByRole('searchbox', { name: '搜索历史对话' }).fill('跨页会话')
    await searchAll.p.getByText('跨页会话', { exact: true }).waitFor()
    assert(searchAll.calls.some(c => c.path === '/api/rag/conversations' && c.method === 'GET') && await searchAll.p.locator('.rag-conversation').count() === 1, '读取全部会话分页，搜索可找到第二页的历史')
    await searchAll.context.close()

    const identity = await setup(); envs.push(identity)
    await identity.p.locator('.rag-conversation-select').filter({ hasText: '工序报工与质量追溯' }).click()
    await identity.p.getByText('按工序提交报工记录。', { exact: true }).waitFor(); await identity.p.reload()
    await identity.p.getByText('按工序提交报工记录。', { exact: true }).waitFor()
    assert(await identity.p.locator('.rag-chat-title h1').innerText() === '工序报工与质量追溯', '刷新页面恢复当前用户上次选择的会话')
    identity.state.nextStream = 'slow'; await identity.p.locator('#rag-question').fill('离开页面检查'); await identity.p.getByRole('button', { name: '发送', exact: true }).click(); await identity.p.waitForTimeout(140)
    const routeMessage = identity.state.messages.get(2).at(-1)
    await identity.p.getByRole('menuitem', { name: '工作台', exact: true }).click(); await identity.p.waitForURL('**/dashboard')
    assert(identity.calls.some(c => c.path === `/api/rag/messages/${routeMessage.id}/cancel`), '离开聊天路由时停止后台生成并清理流')
    await identity.p.goBack(); await identity.p.locator('.rag-workspace').waitFor(); await identity.p.locator('.rag-message-status').filter({ hasText: '已停止生成' }).waitFor()
    assert(await identity.p.locator('.rag-chat-title h1').innerText() === '工序报工与质量追溯', '缓存页面重新激活时读取后端最新保存状态')
    identity.state.conversations = [{ id: 9, title: '另一账号的会话', activeMessageId: null, createdAt: now, updatedAt: now }]
    identity.state.messages = new Map([[9, [msg(901, 9, 'user', '新的账号问题'), msg(902, 9, 'assistant', '新的账号回答', 'COMPLETED', 901)]]])
    await identity.p.evaluate(async () => {
      const { useUserStore } = await import('/src/stores/user.ts'); const user = useUserStore()
      localStorage.setItem('mes_token', 'identity-two'); user.token = 'identity-two'; user.userId = 915; user.userName = '另一用户'
    })
    await identity.p.getByText('新的账号回答', { exact: true }).waitFor()
    assert(!await identity.p.getByText('按工序提交报工记录。', { exact: true }).count() && await identity.p.locator('.rag-conversation').count() === 1, '身份变更立即清除旧账号消息与会话缓存')
    assert(identity.errors.length === 0, '路由缓存和身份切换没有脚本错误')
    await identity.context.close()

    const expired = await setup(); envs.push(expired)
    expired.state.nextStream = 'unauthorized'; await expired.p.locator('#rag-question').fill('过期检查'); await expired.p.getByRole('button', { name: '发送', exact: true }).click(); await expired.p.waitForURL('**/login?redirect=**')
    assert(await expired.p.evaluate(() => localStorage.getItem('mes_token') === null && localStorage.getItem('mes_user') === null), '流式接口返回 401 时清除凭证并跳转登录')
    await expired.context.close()
    return { passed: checks.length, checks, screenshots: ['/tmp/mes-rag-vue-1920.png', '/tmp/mes-rag-vue-1366.png'] }
  } catch (error) { throw new Error(`${error.message}\nCompleted ${checks.length} checks: ${checks.join(' | ')}`) } finally { await Promise.allSettled(envs.map(e => e.context.close())) }
}
