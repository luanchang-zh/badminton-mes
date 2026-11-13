<script setup lang="ts">
import { Close, Document } from '@element-plus/icons-vue'
import type { RagReference } from '@/api/rag'
defineProps<{ references: RagReference[]; selectedReference: string; drawer?: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <section class="rag-sources" aria-label="回答来源">
    <header class="rag-sources-heading"><h2>回答来源 <span>{{ references.length }}</span></h2><button v-if="!drawer" type="button" class="rag-icon-button" aria-label="关闭来源" @click="emit('close')"><el-icon><Close /></el-icon></button></header>
    <div class="rag-sources-body">
      <p v-if="!references.length" class="rag-no-sources">当前回答暂无来源片段。</p>
      <details v-for="source in references" :key="source.referenceId" :open="source.referenceId === selectedReference || !selectedReference" class="rag-source-detail">
        <summary><el-icon><Document /></el-icon><span>{{ source.sourceName || '知识库文档' }}</span><span class="rag-source-number">{{ source.referenceId }}</span></summary>
        <div class="rag-source-excerpts">
          <p v-if="!source.chunks.length">此来源暂无可展示的原文片段。</p>
          <blockquote v-for="(chunk, index) in source.chunks" :key="index"><span class="rag-excerpt-label">片段 {{ index + 1 }}</span>{{ chunk }}</blockquote>
        </div>
      </details>
    </div>
  </section>
</template>
