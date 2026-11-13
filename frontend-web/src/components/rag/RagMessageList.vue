<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Document, CopyDocument, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import RagAnswer from './RagAnswer'
import type { RagMessage } from '@/api/rag'

const props = defineProps<{ messages: RagMessage[]; loading: boolean; olderLoading: boolean; hasOlder: boolean; conversationId: number | null; userName: string; canRetry: boolean }>()
const emit = defineEmits<{ sources: [message: RagMessage, referenceId?: string]; retry: [message: RagMessage]; older: [] }>()
const scroller = ref<HTMLElement>(), following = ref(true)
let previousHeight = 0
const scrollToBottom = async () => { await nextTick(); if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight; following.value = true }
function scroll() { const el = scroller.value; if (el) following.value = el.scrollHeight - el.clientHeight - el.scrollTop < 80 }
function older() { previousHeight = scroller.value?.scrollHeight ?? 0; emit('older') }
watch(() => props.conversationId, () => { following.value = true; void scrollToBottom() })
watch(() => props.loading, value => { if (!value) void scrollToBottom() })
watch(() => props.olderLoading, async (value, old) => {
  if (old && !value) { await nextTick(); if (scroller.value) scroller.value.scrollTop += scroller.value.scrollHeight - previousHeight }
})
watch(() => props.messages.map(m => `${m.id}:${m.content.length}:${m.status}`).join(','), () => {
  if (!props.olderLoading && following.value) void scrollToBottom()
})
async function copy(message: RagMessage) {
  try { await navigator.clipboard.writeText(message.content); ElMessage.success('已复制回答') }
  catch { ElMessage.error('复制失败，请选中回答文字复制') }
}
</script>

<template>
  <div class="rag-messages-wrap">
    <div ref="scroller" class="rag-messages" role="log" aria-label="对话消息" aria-live="off" :aria-busy="loading" @scroll="scroll">
      <div v-if="loading" class="rag-message-loading" role="status">正在加载消息…</div>
      <button v-if="hasOlder && !loading" type="button" class="rag-older-button" :disabled="olderLoading" @click="older">{{ olderLoading ? '正在加载…' : '加载更早的消息' }}</button>
      <article v-for="message in messages" :key="message.id" class="rag-message" :class="`rag-message--${message.role}`" :data-message-id="message.id">
        <div class="rag-avatar" aria-hidden="true">{{ message.role === 'assistant' ? 'MES' : (userName || '我').slice(0, 1) }}</div>
        <div class="rag-message-content">
          <div v-if="message.role === 'user'" class="rag-user-bubble">{{ message.content }}</div>
          <template v-else>
            <div class="rag-assistant-name">知识库助手</div>
            <RagAnswer v-if="message.content" :content="message.content" :references="message.references" @cite="id => emit('sources', message, id)" />
            <div v-if="message.status === 'GENERATING'" class="rag-message-status">正在生成回答…</div>
            <div v-else-if="message.status === 'FAILED'" class="rag-message-status rag-message-status--error" role="status">{{ message.errorTip || '回答生成失败，请重试' }}</div>
            <div v-else-if="message.status === 'CANCELLED'" class="rag-message-status">已停止生成</div>
            <div v-if="message.references.length" class="rag-source-cards">
              <button v-for="source in message.references" :key="source.referenceId" type="button" class="rag-source-card" @click="emit('sources', message, source.referenceId)">
                <el-icon><Document /></el-icon><span class="rag-source-filename">{{ source.sourceName || '知识库文档' }}</span><span class="rag-source-number">来源 {{ source.referenceId }}</span><span aria-hidden="true">›</span>
              </button>
            </div>
            <p v-else-if="message.status === 'COMPLETED'" class="rag-no-sources">本次回答暂无来源片段，可补充设备型号或工序继续提问。</p>
            <div v-if="message.status !== 'GENERATING'" class="rag-message-actions">
              <button v-if="message.content" type="button" class="rag-text-button" @click="copy(message)"><el-icon><CopyDocument /></el-icon>复制</button>
              <button v-if="message.status === 'FAILED' || message.status === 'CANCELLED'" type="button" class="rag-text-button" :disabled="!canRetry" @click="emit('retry', message)"><el-icon><RefreshRight /></el-icon>重新生成</button>
            </div>
          </template>
        </div>
      </article>
    </div>
    <button v-if="!following && messages.length" type="button" class="rag-jump-latest" @click="scrollToBottom">回到最新 ↓</button>
  </div>
</template>
