<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RagConversationList from '@/components/rag/RagConversationList.vue'
import RagMessageList from '@/components/rag/RagMessageList.vue'
import RagComposer from '@/components/rag/RagComposer.vue'
import RagSources from '@/components/rag/RagSources.vue'
import { useRagChat } from '@/composables/useRagChat'
import { useUserStore } from '@/stores/user'
import { ragError } from '@/api/rag'
import type { RagConversation, RagMessage } from '@/api/rag'
import './rag-chat.css'

defineOptions({ name: 'RagChatView' })
const user = useUserStore()
const chat = useRagChat()
const { conversations, messages, selectedId, selected, loading, messagesLoading, olderLoading, generating, creating,
  notice, blocked, draft, mode, messagePage, inputEnabled, canSend, available } = chat
const composer = ref<InstanceType<typeof RagComposer>>()
const focusMode = ref<'pointer' | 'keyboard'>('pointer')
const sourcesMessage = ref<RagMessage | null>(null), sourceId = ref(''), sourceOpen = ref(false)
const sourceReferences = computed(() => messages.value.find(m => m.id === sourcesMessage.value?.id)?.references ?? [])
const narrow = ref(window.matchMedia('(max-width: 1550px)').matches)
const media = window.matchMedia('(max-width: 1550px)')
const resize = () => { narrow.value = media.matches }
onMounted(() => media.addEventListener('change', resize))
onUnmounted(() => media.removeEventListener('change', resize))
const latestAnswer = computed(() => [...messages.value].reverse().find(m => m.role === 'assistant' && m.references.length))
function openSources(message: RagMessage, referenceId = '') { sourcesMessage.value = message; sourceId.value = referenceId; sourceOpen.value = true }
async function selectConversation(id: number) { sourceOpen.value = false; await chat.selectConversation(id) }
async function createConversation() { sourceOpen.value = false; await chat.newConversation(); await nextTick(); composer.value?.focus() }
function fillExample(text: string) { draft.value = text; composer.value?.focus() }
const examples = ['装配线开机前需要做哪些检查？', '工序报工出现异常时怎么处理？', '设备停机后应如何排查故障？']

const dialog = ref<'rename' | 'delete' | null>(null), target = ref<RagConversation | null>(null), title = ref(''), dialogError = ref(''), saving = ref(false)
watch(() => user.token, () => { sourceOpen.value = false; sourcesMessage.value = null; dialog.value = null; target.value = null; title.value = ''; dialogError.value = ''; saving.value = false })
function editConversation(conversation: RagConversation, action: 'rename' | 'delete') {
  target.value = conversation; title.value = conversation.title; dialogError.value = ''; dialog.value = action
}
async function confirmDialog() {
  if (!target.value || saving.value) return
  if (dialog.value === 'rename' && (!title.value.trim() || title.value.trim().length > 120)) { dialogError.value = '请输入 1～120 字的会话名称'; return }
  saving.value = true; dialogError.value = ''
  const token = user.token
  try {
    if (dialog.value === 'rename') await chat.renameConversation(target.value.id, title.value.trim())
    else { await chat.deleteConversation(target.value.id); sourceOpen.value = false }
    if (token === user.token) { ElMessage.success(dialog.value === 'rename' ? '会话已重命名' : '会话已删除'); dialog.value = null }
  } catch (error) { if (token === user.token) dialogError.value = ragError(error) }
  finally { if (token === user.token) saving.value = false }
}
</script>

<template>
  <section class="rag-workspace" :data-focus-mode="focusMode" aria-label="知识库问答" @pointerdown.capture="focusMode = 'pointer'" @keydown.capture="event => { if (event.key === 'Tab') focusMode = 'keyboard' }">
    <RagConversationList :conversations="conversations" :selected-id="selectedId" :loading="loading" :disabled="!available" :creating="creating"
      @select="selectConversation" @create="createConversation" @rename="conversation => editConversation(conversation, 'rename')" @delete="conversation => editConversation(conversation, 'delete')" />
    <section class="rag-chat" aria-label="当前对话">
      <header class="rag-chat-heading">
        <div class="rag-chat-title"><h1>{{ selected?.title || '新对话' }}</h1><time v-if="selected" :datetime="selected.updatedAt">{{ new Date(selected.updatedAt).toLocaleString('zh-CN', { hour12: false }) }}</time></div>
        <button v-if="latestAnswer" type="button" class="rag-text-button" @click="openSources(latestAnswer)">查看来源</button>
      </header>
      <div v-if="notice" class="rag-notice" role="status"><span>{{ notice }}</span><button type="button" class="rag-text-button" @click="chat.checkDocuments()">重新检测</button><button type="button" class="rag-icon-button" aria-label="关闭提示" @click="notice = ''">×</button></div>
      <div v-if="!messages.length && !messagesLoading" class="rag-empty">
        <template v-if="loading"><h2>正在加载知识库…</h2></template>
        <template v-else-if="blocked"><h2>知识库暂不可用</h2><p>{{ blocked }}</p><button type="button" class="rag-text-button" @click="chat.initialize()">重新加载</button></template>
        <template v-else><h2>有什么需要查询的？</h2><p>从工艺、设备和质量资料中查找答案。</p><div class="rag-examples"><button v-for="example in examples" :key="example" type="button" @click="fillExample(example)">{{ example }}<span aria-hidden="true">↗</span></button></div></template>
      </div>
      <RagMessageList v-else :messages="messages" :loading="messagesLoading" :older-loading="olderLoading" :has-older="messagePage > 1" :conversation-id="selectedId" :user-name="user.userName" :can-retry="inputEnabled && !generating"
        @sources="openSources" @retry="chat.retry" @older="chat.loadOlder" />
      <div v-if="messages.length && blocked && !loading" class="rag-blocked-note" role="status">{{ blocked }}</div>
      <RagComposer ref="composer" v-model:question="draft" v-model:mode="mode" :enabled="inputEnabled" :can-send="canSend" :generating="generating" @send="chat.send" @stop="chat.stop" />
      <span class="rag-sr-only" role="status" aria-live="polite">{{ generating ? '正在生成回答' : '回答已结束' }}</span>
    </section>
    <RagSources v-if="sourceOpen && !narrow" :references="sourceReferences" :selected-reference="sourceId" @close="sourceOpen = false" />
    <el-drawer v-model="sourceOpen" :size="328" title="回答来源" :append-to-body="true" :class="{ 'rag-source-drawer': true, 'rag-source-drawer--hidden': !narrow }" :modal="narrow" :lock-scroll="narrow" :with-header="true" :before-close="done => { sourceOpen = false; done() }" v-if="narrow">
      <RagSources :references="sourceReferences" :selected-reference="sourceId" drawer @close="sourceOpen = false" />
    </el-drawer>
    <el-dialog :model-value="dialog !== null" :title="dialog === 'rename' ? '重命名会话' : '删除会话'" width="420px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @update:model-value="value => { if (!value && !saving) dialog = null }">
      <form v-if="dialog === 'rename'" @submit.prevent="confirmDialog"><label class="rag-dialog-label" for="rag-conversation-title">会话名称</label><el-input id="rag-conversation-title" v-model="title" maxlength="120" show-word-limit :disabled="saving" autofocus /></form>
      <p v-else class="rag-delete-description">确定删除“{{ target?.title }}”及全部消息？删除后无法恢复。</p>
      <p v-if="dialogError" class="rag-dialog-error" role="alert">{{ dialogError }}</p>
      <template #footer><el-button :disabled="saving" @click="dialog = null">取消</el-button><el-button :type="dialog === 'delete' ? 'danger' : 'primary'" :loading="saving" @click="confirmDialog">{{ dialog === 'delete' ? '删除' : '保存' }}</el-button></template>
    </el-dialog>
  </section>
</template>
