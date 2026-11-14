<script setup lang="ts">
import { onBeforeUnmount, onDeactivated, ref } from 'vue'
import { ragApi, ragError } from '@/api/rag'
import type { RagDocument } from '@/api/rag'

defineProps<{ documents: RagDocument[] }>()
const emit = defineEmits<{ refresh: [] }>()
const formats = ['.txt', '.md', '.markdown', '.csv', '.json', '.log']
const open = ref(false)
const file = ref<File | null>(null)
const error = ref('')
const submitted = ref('')
const uploading = ref(false)
let request: AbortController | null = null

function close() {
  request?.abort()
  request = null
  open.value = false
  uploading.value = false
  file.value = null
  error.value = ''
  submitted.value = ''
}

onBeforeUnmount(close)
onDeactivated(close)

function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0]
  file.value = null
  error.value = ''
  if (!selected) return
  if (!formats.some(ext => selected.name.toLowerCase().endsWith(ext))) {
    error.value = '请选择支持的 UTF-8 文本文件'
  } else if (!selected.size || selected.size > 20 * 1024 * 1024) {
    error.value = '文件不能为空，且不得超过 20 MB'
  } else {
    file.value = selected
  }
  if (!file.value) input.value = ''
}

async function upload() {
  const selected = file.value
  if (!selected || uploading.value) return
  const controller = new AbortController()
  request = controller
  uploading.value = true
  error.value = ''
  try {
    await ragApi.uploadDocument(selected, controller.signal)
    if (controller.signal.aborted) return
    file.value = null
    submitted.value = selected.name
    emit('refresh')
  } catch (cause) {
    if (!controller.signal.aborted) error.value = ragError(cause)
  } finally {
    if (request === controller) {
      uploading.value = false
      request = null
    }
  }
}
</script>

<template>
  <el-button type="primary" plain @click="open = true; emit('refresh')">上传资料</el-button>
  <el-dialog v-model="open" title="上传知识库资料" width="min(520px, 94vw)" destroy-on-close
    :close-on-click-modal="!uploading" :close-on-press-escape="!uploading" :show-close="!uploading" @close="close">
    <form v-if="!submitted" id="rag-upload-form" @submit.prevent="upload">
      <label class="rag-dialog-label" for="rag-upload-file">选择文件</label>
      <input id="rag-upload-file" type="file" :accept="formats.join(',')" :disabled="uploading"
        aria-describedby="rag-upload-formats" @change="chooseFile" />
      <p id="rag-upload-formats">支持 UTF-8 编码的 TXT、MD、Markdown、CSV、JSON、LOG，单个文件最多 20 MB。</p>
      <p v-if="error" class="rag-dialog-error" role="alert">{{ error }}</p>
    </form>
    <p v-else role="status">已提交「{{ submitted }}」处理。索引完成后即可用于问答。</p>
    <ul v-if="documents.length" class="document-list" aria-label="资料处理状态">
      <li v-for="document in documents" :key="document.docId">
        <span :title="document.fileName">{{ document.fileName }}</span>
        <span>{{ document.statusText || document.status }}</span>
      </li>
    </ul>
    <template #footer>
      <el-button :disabled="uploading" @click="emit('refresh')">刷新处理状态</el-button>
      <el-button v-if="submitted" @click="submitted = ''">继续上传</el-button>
      <el-button v-else type="primary" :disabled="!file" :loading="uploading" native-type="submit" form="rag-upload-form">上传</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.document-list { max-height: 180px; overflow: auto; padding: 0; list-style: none; }
.document-list li { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-top: 1px solid var(--el-border-color-lighter); }
.document-list span:first-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.document-list span:last-child { flex-shrink: 0; }
</style>
