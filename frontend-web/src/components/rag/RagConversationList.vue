<script setup lang="ts">
import { computed, ref } from 'vue'
import { Delete, Edit, MoreFilled, Search } from '@element-plus/icons-vue'
import type { RagConversation } from '@/api/rag'

const props = defineProps<{ conversations: RagConversation[]; selectedId: number | null; loading: boolean; disabled: boolean; creating: boolean }>()
const emit = defineEmits<{ select: [id: number]; create: []; rename: [conversation: RagConversation]; delete: [conversation: RagConversation] }>()
const search = ref('')
const filtered = computed(() => props.conversations.filter(c => c.title.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase())))
function command(action: string, conversation: RagConversation) {
  if (action === 'rename') emit('rename', conversation)
  else if (action === 'delete') emit('delete', conversation)
}
function time(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const today = new Date(), yesterday = new Date(); yesterday.setDate(today.getDate() - 1)
  const clock = date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  if (date.toDateString() === today.toDateString()) return `今天 ${clock}`
  if (date.toDateString() === yesterday.toDateString()) return `昨天 ${clock}`
  return `${date.getMonth() + 1}月${date.getDate()}日 ${clock}`
}
</script>

<template>
  <aside class="rag-history" aria-label="历史会话">
    <div class="rag-history-heading">
      <h2>最近对话</h2>
      <button type="button" class="rag-icon-button" :disabled="disabled || loading || creating" aria-label="新建对话" title="新建对话" @click="emit('create')">
        <el-icon><Edit /></el-icon>
      </button>
    </div>
    <label class="rag-search">
      <el-icon><Search /></el-icon>
      <input v-model="search" aria-label="搜索历史对话" placeholder="搜索历史对话" type="search" />
    </label>
    <div v-if="loading && !conversations.length" class="rag-history-empty" role="status">正在加载会话…</div>
    <div v-else-if="!filtered.length" class="rag-history-empty">{{ search.trim() ? '未找到相关会话' : '暂无历史对话' }}</div>
    <ul v-else class="rag-conversation-list">
      <li v-for="conversation in filtered" :key="conversation.id" class="rag-conversation" :class="{ 'is-active': conversation.id === selectedId }">
        <button class="rag-conversation-select" type="button" :aria-current="conversation.id === selectedId ? 'true' : undefined" @click="emit('select', conversation.id)">
          <span class="rag-conversation-title" :title="conversation.title">{{ conversation.title }}</span>
          <time :datetime="conversation.updatedAt">{{ time(conversation.updatedAt) }}</time>
        </button>
        <el-dropdown trigger="click" placement="bottom-end" @command="action => command(action, conversation)">
          <button type="button" class="rag-icon-button rag-conversation-more" :aria-label="`管理会话：${conversation.title}`" :title="`管理会话：${conversation.title}`">
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="rename" :icon="Edit">重命名</el-dropdown-item>
              <el-dropdown-item command="delete" :icon="Delete">删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </li>
    </ul>
  </aside>
</template>
