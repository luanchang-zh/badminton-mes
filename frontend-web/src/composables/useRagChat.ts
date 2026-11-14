import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { newRagRequestId, ragApi, ragError, streamRag } from '@/api/rag'
import type { RagConversation, RagDocument, RagMessage, RagMeta, RagMode, RagProfile, RagQuestion, RagReference } from '@/api/rag'
import { ApiError, MOCK_TOKEN } from '@/utils/request'
import { registerRagCancellation } from '@/utils/rag-cancellation'
import { useUserStore } from '@/stores/user'

interface Generation {
  controller: AbortController; conversationId: number; userId: number; assistantId: number
  epoch: number; terminal: boolean; stopping: boolean; stopPromise?: Promise<void>
}

export function useRagChat() {
  const user = useUserStore()
  const profile = ref<RagProfile | null>(null)
  const conversations = ref<RagConversation[]>([])
  const messages = ref<RagMessage[]>([])
  const documents = ref<RagDocument[]>([])
  const selectedId = ref<number | null>(null)
  const loading = ref(false), messagesLoading = ref(false), olderLoading = ref(false)
  const busy = ref(false), creating = ref(false), notice = ref(''), loadError = ref('')
  const documentState = ref<'unknown' | 'ready' | 'empty' | 'indexing' | 'failed'>('unknown')
  const draft = ref(''), mode = ref<RagMode>('naive'), messagePage = ref(1)
  const demo = computed(() => user.token === MOCK_TOKEN)
  const selected = computed(() => conversations.value.find(c => c.id === selectedId.value))
  const serverGenerating = computed(() => messages.value.find(m => m.status === 'GENERATING' && m.role === 'assistant'))
  const generating = computed(() => busy.value || !!serverGenerating.value)
  const available = computed(() => !demo.value && !!profile.value?.bound && !!profile.value?.enabled)
  const servicePending = computed(() => profile.value?.serviceConfigured === false)
  const inputEnabled = computed(() => available.value && !loading.value && !messagesLoading.value && !creating.value
    && (documentState.value === 'ready' || documentState.value === 'unknown'))
  const canSend = computed(() => inputEnabled.value && !servicePending.value && !generating.value && !!draft.value.trim())
  const blocked = computed(() => {
    if (demo.value) return '演示账号仅支持浏览，请使用正式账号登录后提问'
    if (loadError.value) return loadError.value
    if (!profile.value?.bound) return '当前账号尚未开通知识库，请联系管理员绑定知识库'
    if (!profile.value.enabled) return '当前知识库已停用，请联系管理员启用'
    if (documentState.value === 'empty') return profile.value.canManageDocuments
      ? '知识库暂无资料，请先上传工艺、设备或质量文档'
      : '知识库暂无资料，请联系管理员上传工艺、设备或质量文档'
    if (documentState.value === 'failed') return profile.value.canManageDocuments
      ? '资料索引失败。请打开「上传资料」，点击「重新处理失败资料」。'
      : '资料索引失败，请联系管理员重新处理。'
    if (documentState.value === 'indexing') return '知识库资料尚未就绪，请等待处理完成或联系管理员检查文档'
    return ''
  })
  let active = false, epoch = 0, readSerial = 0, listSerial = 0, temporaryId = -1
  let lifecycle = new AbortController(), messageRead = new AbortController()
  let generation: Generation | null = null, pollTimer: ReturnType<typeof setTimeout> | undefined
  const resend = new Map<number, { question: RagQuestion; retryId?: number }>()
  const storageKey = () => `mes_rag_conversation_${user.userId ?? user.userNo}`
  const valid = (stamp: number) => active && epoch === stamp
  const remember = () => {
    try {
      if (selectedId.value === null) localStorage.removeItem(storageKey())
      else localStorage.setItem(storageKey(), String(selectedId.value))
    } catch { /* 浏览器禁用存储时仍可使用当前会话。 */ }
  }

  async function refreshConversations(stamp = epoch) {
    const serial = ++listSerial
    const all: RagConversation[] = []
    let page = 1, total = 0
    do {
      const result = await ragApi.conversations(page, lifecycle.signal)
      if (!valid(stamp) || serial !== listSerial) return
      all.push(...result.list); total = result.total
      if (!result.list.length || result.pageNo < page) break
      page++
    } while (all.length < total)
    if (valid(stamp) && serial === listSerial) conversations.value = [...new Map(all.map(c => [c.id, c])).values()]
  }

  function schedulePoll() {
    clearTimeout(pollTimer)
    if (!serverGenerating.value || generation || !active) return
    const stamp = epoch, id = selectedId.value
    pollTimer = setTimeout(async () => {
      if (!valid(stamp) || id !== selectedId.value || generation || id === null) return
      try {
        const result = await ragApi.messages(id, undefined, lifecycle.signal)
        if (!valid(stamp) || id !== selectedId.value || generation) return
        // 保留已经加载的早期消息，更新当前页及生成快照。
        const merged = new Map(messages.value.map(m => [m.id, m]))
        result.list.forEach(m => merged.set(m.id, m))
        messages.value = [...merged.values()].sort((a, b) => a.id - b.id)
        if (!serverGenerating.value) void refreshConversations(stamp).catch(() => {})
      } catch (error) {
        if (valid(stamp)) notice.value = ragError(error)
      }
      if (valid(stamp)) schedulePoll()
    }, 1800)
  }

  async function loadMessages(id: number, stamp = epoch) {
    messageRead.abort(); messageRead = new AbortController()
    const serial = ++readSerial
    messagesLoading.value = true; messages.value = []; messagePage.value = 1
    try {
      const result = await ragApi.messages(id, undefined, messageRead.signal)
      if (!valid(stamp) || serial !== readSerial || id !== selectedId.value) return
      messages.value = result.list; messagePage.value = result.pageNo
      schedulePoll()
    } catch (error) {
      if (valid(stamp) && serial === readSerial) notice.value = ragError(error)
    } finally {
      if (valid(stamp) && serial === readSerial) messagesLoading.value = false
    }
  }

  async function checkDocuments(stamp = epoch) {
    if (servicePending.value) return
    const previousNotice = notice.value
    try {
      const result = await ragApi.documents(lifecycle.signal)
      if (!valid(stamp)) return
      documents.value = result
      if (result.some(document => document.status === 'COMPLETED')) {
        documentState.value = 'ready'
      } else if (!result.length) {
        documentState.value = 'empty'
      } else {
        documentState.value = result.every(document => document.status === 'FAILED') ? 'failed' : 'indexing'
      }
      if (notice.value === previousNotice) notice.value = ''
    } catch (error) {
      if (valid(stamp)) { documentState.value = 'unknown'; notice.value = ragError(error) }
    }
  }

  async function initialize() {
    const stamp = epoch
    loading.value = true; loadError.value = ''; notice.value = ''
    if (demo.value) { loading.value = false; return }
    try {
      const loadedProfile = await ragApi.profile(lifecycle.signal)
      if (!valid(stamp)) return
      profile.value = loadedProfile
      if (!available.value) return
      // 文档上游未部署时仍可读取 MES 已保存的历史。
      const documents = checkDocuments(stamp)
      try {
        await refreshConversations(stamp)
        if (!valid(stamp)) return
        let saved: number | undefined
        try { saved = Number(localStorage.getItem(storageKey())) || undefined } catch { /* 存储可选 */ }
        selectedId.value = conversations.value.find(c => c.id === saved)?.id ?? conversations.value[0]?.id ?? null
        if (selectedId.value !== null) await loadMessages(selectedId.value, stamp)
        remember()
      } catch (error) { if (valid(stamp)) notice.value = ragError(error) }
      await documents
    } catch (error) {
      if (valid(stamp)) loadError.value = ragError(error)
    } finally { if (valid(stamp)) loading.value = false }
  }

  async function stop() {
    const current = generation
    if (current?.stopPromise) return current.stopPromise
    const id = current?.assistantId ?? serverGenerating.value?.id
    if (!current && !id) return
    const stamp = epoch
    const work = async () => {
      if (current) current.stopping = true
      try {
        if (id && id > 0) {
          const saved = await ragApi.cancel(id)
          if (valid(stamp) && selectedId.value === saved.conversationId) {
            messages.value = messages.value.map(m => m.id === id ? saved : m)
          }
        } else if (current && valid(stamp)) {
          const message = messages.value.find(m => m.id === current.assistantId)
          if (message) { message.status = 'CANCELLED'; message.errorTip = null }
        }
      } catch (error) {
        if (valid(stamp)) notice.value = `停止请求未送达：${ragError(error)}。已停止接收回答，可刷新查看保存结果`
      } finally {
        if (current) {
          current.controller.abort()
          if (generation === current) { generation = null; busy.value = false }
        }
        if (valid(stamp)) schedulePoll()
      }
    }
    const promise = work()
    if (current) current.stopPromise = promise
    return promise
  }

  async function selectConversation(id: number) {
    if (id === selectedId.value || creating.value) return
    const stamp = epoch
    await stop()
    if (!valid(stamp)) return
    clearTimeout(pollTimer); selectedId.value = id; notice.value = ''; remember()
    await loadMessages(id, stamp)
  }

  async function newConversation() {
    if (!available.value || creating.value || loading.value) return
    const stamp = epoch
    creating.value = true
    try {
      await stop()
      const conversation = await ragApi.create()
      if (!valid(stamp)) return
      messageRead.abort(); readSerial++; clearTimeout(pollTimer)
      conversations.value.unshift(conversation); selectedId.value = conversation.id
      messages.value = []; messagesLoading.value = false; messagePage.value = 1; draft.value = ''; notice.value = ''; remember()
    } catch (error) { if (valid(stamp)) notice.value = ragError(error) }
    finally { if (valid(stamp)) creating.value = false }
  }

  async function renameConversation(id: number, title: string) {
    const stamp = epoch
    const saved = await ragApi.rename(id, title)
    if (valid(stamp)) { listSerial++; conversations.value = conversations.value.map(c => c.id === id ? saved : c) }
  }

  async function deleteConversation(id: number) {
    const stamp = epoch
    if (selectedId.value === id) await stop()
    await ragApi.remove(id)
    if (!valid(stamp)) return
    listSerial++
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (selectedId.value === id) {
      clearTimeout(pollTimer); messageRead.abort(); readSerial++
      selectedId.value = conversations.value[0]?.id ?? null
      messages.value = []; messagePage.value = 1; draft.value = ''; notice.value = ''; remember()
      if (selectedId.value !== null) await loadMessages(selectedId.value, stamp)
      else messagesLoading.value = false
    }
  }

  async function loadOlder() {
    const id = selectedId.value, stamp = epoch, serial = readSerial
    if (id === null || messagePage.value <= 1 || olderLoading.value) return
    olderLoading.value = true
    try {
      const result = await ragApi.messages(id, messagePage.value - 1, messageRead.signal)
      if (!valid(stamp) || serial !== readSerial) return
      const existing = new Set(messages.value.map(m => m.id))
      messages.value = [...result.list.filter(m => !existing.has(m.id)), ...messages.value]
      messagePage.value = result.pageNo
    } catch (error) { if (valid(stamp) && serial === readSerial) notice.value = ragError(error) }
    finally { if (valid(stamp)) olderLoading.value = false }
  }

  function placeholder(role: 'user' | 'assistant', id: number, content: string, conversationId: number, replyToId: number | null): RagMessage {
    const now = new Date().toISOString()
    return { id, conversationId, role, content, mode: mode.value, status: role === 'user' ? 'COMPLETED' : 'GENERATING', replyToId, references: [], errorTip: null, createdAt: now, updatedAt: now }
  }

  async function run(question: RagQuestion, retryMessage?: RagMessage) {
    const stamp = epoch
    const pending = retryMessage && resend.get(retryMessage.id)
    const retryId = pending ? pending.retryId : retryMessage?.id
    question = pending?.question ?? question
    const userId = retryMessage?.replyToId ?? temporaryId--
    const assistantId = pending ? retryMessage!.id : temporaryId--
    if (!retryMessage) messages.value.push(placeholder('user', userId, question.question, question.conversationId, null))
    const assistant = placeholder('assistant', assistantId, '', question.conversationId, userId)
    assistant.mode = question.mode
    if (pending) messages.value = messages.value.map(m => m.id === assistantId ? assistant : m)
    else messages.value.push(assistant)
    resend.set(assistantId, { question, retryId })
    const current: Generation = { controller: new AbortController(), conversationId: question.conversationId, userId, assistantId, epoch: stamp, terminal: false, stopping: false }
    generation = current; busy.value = true; notice.value = ''; clearTimeout(pollTimer)
    const owns = () => valid(stamp) && generation === current && selectedId.value === current.conversationId && !current.stopping
    const message = () => messages.value.find(m => m.id === current.assistantId)
    try {
      await streamRag(retryId !== undefined ? `/rag/messages/${retryId}/retry` : '/rag/query/stream', retryId !== undefined ? { requestId: question.requestId } : question, current.controller.signal, event => {
        if (!owns() || current.terminal) return
        if (event.event === 'meta') {
          const meta = JSON.parse(event.data) as RagMeta
          if (meta.conversationId !== current.conversationId) throw new Error('回答会话不匹配，请刷新重试')
          const u = messages.value.find(m => m.id === current.userId), a = message()
          if (u && u.id < 0) u.id = meta.userMessageId
          if (a) { a.id = meta.assistantMessageId; a.replyToId = meta.userMessageId }
          resend.delete(current.assistantId)
          current.userId = meta.userMessageId; current.assistantId = meta.assistantMessageId
        } else if (event.event === 'delta') {
          const a = message(); if (a) a.content += event.data
        } else if (event.event === 'reference_details') {
          const a = message(); if (a) a.references = JSON.parse(event.data) as RagReference[]
        } else if (event.event === 'done') {
          const saved = JSON.parse(event.data) as RagMessage
          if (saved.id !== current.assistantId || saved.conversationId !== current.conversationId || saved.status === 'GENERATING') throw new Error('回答保存状态异常，请刷新查看')
          messages.value = messages.value.map(m => m.id === current.assistantId ? saved : m)
          current.terminal = true
        } else if (event.event === 'error') {
          const error = JSON.parse(event.data) as { code: string; message: string; userTip: string | null }
          throw new ApiError({ ...error, data: null })
        }
      })
      if (owns() && !current.terminal) throw new Error('回答连接已中断，可重试继续提问')
    } catch (error) {
      if (owns() && !current.controller.signal.aborted) {
        const a = message()
        if (a) { a.status = 'FAILED'; a.errorTip = ragError(error) }
        if (error instanceof ApiError && error.code === 'C0301') notice.value = '问答服务暂时无法连接，请稍后重试；历史对话仍可查看'
        if (error instanceof ApiError && ['A0403', 'A0440'].includes(error.code)) {
          notice.value = ragError(error)
          // 绑定变更后重新读取授权范围，避免继续展示旧租户的缓存。
          conversations.value = []; messages.value = []; selectedId.value = null; profile.value = null
          void initialize()
        }
      }
    } finally {
      if (generation === current) {
        generation = null; busy.value = false
        if (valid(stamp)) {
          void refreshConversations(stamp).catch(error => { if (valid(stamp)) notice.value = ragError(error) })
          schedulePoll()
        }
      }
    }
  }

  async function send() {
    if (!canSend.value) return
    const text = draft.value.trim(), stamp = epoch, selectedAtSend = selectedId.value
    if (selectedAtSend === null) {
      creating.value = true
      try {
        const conversation = await ragApi.create()
        if (!valid(stamp)) return
        conversations.value.unshift(conversation); selectedId.value = conversation.id; remember()
      } catch (error) { if (valid(stamp)) notice.value = ragError(error); return }
      finally { if (valid(stamp)) creating.value = false }
    }
    if (!valid(stamp) || selectedId.value === null) return
    draft.value = ''
    await run({ conversationId: selectedId.value, requestId: newRagRequestId(), question: text, mode: mode.value })
  }

  async function retry(message: RagMessage) {
    if (!inputEnabled.value || servicePending.value || generating.value || !['FAILED', 'CANCELLED'].includes(message.status)) return
    const original = resend.get(message.id)?.question
    const question = messages.value.find(m => m.id === message.replyToId)?.content ?? ''
    await run(original ?? { conversationId: message.conversationId, requestId: newRagRequestId(), question, mode: message.mode }, message)
  }

  function reset() {
    epoch++; readSerial++; listSerial++; lifecycle.abort(); messageRead.abort(); clearTimeout(pollTimer)
    lifecycle = new AbortController(); messageRead = new AbortController()
    profile.value = null; conversations.value = []; messages.value = []; documents.value = []; selectedId.value = null
    draft.value = ''; mode.value = 'naive'; notice.value = ''; loadError.value = ''; documentState.value = 'unknown'
    loading.value = false; messagesLoading.value = false; olderLoading.value = false; busy.value = false; creating.value = false
    resend.clear()
  }
  function activate() { if (!active) { active = true; void initialize() } }
  function deactivate() { void stop(); active = false; reset() }
  const unregister = registerRagCancellation(stop)
  watch(() => [user.token, user.userId], () => {
    // 外部更换身份时，旧流只能关闭，不能使用新账号凭证取消旧消息。
    generation?.controller.abort(); generation = null; reset()
    if (active && user.token) void initialize()
  }, { flush: 'sync' })
  onMounted(activate); onActivated(activate); onDeactivated(deactivate)
  onBeforeRouteLeave(async () => { await stop() })
  onUnmounted(() => { deactivate(); unregister() })
  return { profile, conversations, messages, documents, selectedId, selected, loading, messagesLoading, olderLoading,
    generating, creating, notice, blocked, draft, mode, messagePage, inputEnabled, canSend, available, servicePending,
    initialize, checkDocuments, selectConversation, newConversation, renameConversation, deleteConversation,
    loadOlder, send, stop, retry }
}
