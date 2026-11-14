<script setup lang="ts">
import { ref } from 'vue'
import { Top } from '@element-plus/icons-vue'
import type { RagMode } from '@/api/rag'

const question = defineModel<string>('question', { required: true })
const mode = defineModel<RagMode>('mode', { required: true })
defineProps<{ enabled: boolean; canSend: boolean; generating: boolean; servicePending: boolean }>()
const emit = defineEmits<{ send: []; stop: [] }>()
const composing = ref(false), textarea = ref<HTMLTextAreaElement>()
function keydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && !composing.value && event.keyCode !== 229) {
    event.preventDefault(); emit('send')
  }
}
defineExpose({ focus: () => textarea.value?.focus() })
</script>

<template>
  <div class="rag-composer-wrap">
    <form class="rag-composer" @submit.prevent="emit('send')">
      <label class="rag-sr-only" for="rag-question">输入问题</label>
      <textarea id="rag-question" ref="textarea" v-model="question" maxlength="2000" :disabled="!enabled || generating" rows="3"
        placeholder="输入问题，例如：装配线开机前需要做哪些检查？" @keydown="keydown" @compositionstart="composing = true" @compositionend="composing = false" />
      <div class="rag-composer-toolbar">
        <label class="rag-mode-label">检索方式
          <select v-model="mode" aria-label="检索方式" :disabled="!enabled || generating">
            <option value="naive">标准检索</option><option value="mix">综合检索</option><option value="local">实体关联</option>
            <option value="global">全局概览</option><option value="hybrid">关联与概览</option>
          </select>
        </label>
        <span class="rag-question-count" aria-live="off">{{ question.length }} / 2000</span>
        <button v-if="generating" type="button" class="rag-send-button" @click="emit('stop')"><span class="rag-stop-square" />停止</button>
        <button v-else type="submit" class="rag-send-button" :disabled="!canSend">{{ servicePending ? '待接入' : '发送' }} <el-icon v-if="!servicePending"><Top /></el-icon></button>
      </div>
    </form>
  </div>
</template>
