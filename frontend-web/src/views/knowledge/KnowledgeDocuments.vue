<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import { usePermission } from '@/composables/usePermission'
import * as api from '@/api/rag'
import type { KnowledgeDocument } from '@/api/rag'
import { validateKnowledgeFile } from '@/utils/knowledge'

defineOptions({ name: 'KnowledgeDocuments' })
const { hasRole } = usePermission()
const canWrite = computed(() => hasRole(['ADMIN', 'CRAFT_ENGINEER']))
const route = useRoute()
const documents = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const error = ref('')
const query = ref('')
const status = ref('')
const page = ref(1)
const pageSize = ref(10)
const pending = ref('')
const filtered = computed(() => documents.value.filter(doc => (!status.value || doc.status === status.value) && (doc.fileName + ' ' + doc.docId).toLowerCase().includes(query.value.trim().toLowerCase())))
const rows = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const completed = computed(() => documents.value.filter(doc => doc.status === 'COMPLETED').length)
const processing = computed(() => documents.value.filter(doc => doc.status === 'PROCESSING').length)
const failed = computed(() => documents.value.filter(doc => doc.status === 'FAILED').length)
const uploadVisible = ref(false)
const uploading = ref(false)
const previous = ref<KnowledgeDocument | null>(null)
const queue = ref<UploadUserFile[]>([])
const outcomes = reactive<Record<number, { success: boolean; message: string }>>({})
const uploader = ref<{ clearFiles: () => void; handleRemove: (file: UploadFile) => void }>()
const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref<api.DocumentContent | null>(null)
let requestId = 0
let previewRequest = 0
let timer: ReturnType<typeof setInterval> | undefined

function message(e: unknown) { return e instanceof Error ? ('userTip' in e && e.userTip ? String(e.userTip) : e.message) : '操作失败，请稍后重试' }
async function load(quiet = false) {
  const version = ++requestId
  if (!quiet) loading.value = true
  error.value = ''
  try {
    const result = await api.listDocuments()
    if (version !== requestId) return
    documents.value = result || []
    page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
  } catch (e) { if (version === requestId) error.value = message(e) }
  finally { if (version === requestId) loading.value = false }
}
function openUpload(doc?: KnowledgeDocument) {
  previous.value = doc || null
  queue.value = []
  Object.keys(outcomes).forEach(key => delete outcomes[Number(key)])
  uploadVisible.value = true
}
function changed(file: UploadFile) {
  const problem = validateKnowledgeFile(file.name, file.size || 0)
  if (problem) { uploader.value?.handleRemove(file); ElMessage.error(problem) }
}
async function upload() {
  uploading.value = true
  let accepted = 0
  try {
    for (const file of queue.value) {
      if (!file.raw || file.uid == null || outcomes[file.uid]?.success) continue
      outcomes[file.uid] = { success: false, message: '正在上传…' }
      try {
        const trackId = await api.uploadDocument(file.raw, previous.value?.docId)
        outcomes[file.uid] = { success: true, message: `已接收 · 任务 ${trackId || '已创建'}` }
        accepted++
      } catch (e) { outcomes[file.uid] = { success: false, message: message(e) } }
    }
    if (accepted) { ElMessage.success(`${accepted} 份资料已接收，后台正在解析与提取图谱`); await load() }
  } finally { uploading.value = false }
}
async function showPreview(doc: KnowledgeDocument) {
  const version = ++previewRequest
  preview.value = null; previewVisible.value = true; previewLoading.value = true
  try { const result = await api.readDocument(doc.docId); if (version === previewRequest) preview.value = result }
  catch (e) { ElMessage.error(message(e)) }
  finally { if (version === previewRequest) previewLoading.value = false }
}
async function remove(doc: KnowledgeDocument) {
  try { await ElMessageBox.confirm(`确认删除“${doc.fileName}”？对应知识片段和实体关系将由 LightRAG 清理。`, '删除资料', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  pending.value = doc.docId
  try { await api.deleteDocument(doc.docId); ElMessage.success('删除任务已提交，清理完成后列表会更新'); await load() }
  catch (e) { ElMessage.error(message(e)) }
  finally { pending.value = '' }
}
async function retry() {
  try { await ElMessageBox.confirm('将重新处理当前个人知识库内所有失败、待处理或中断的资料。', '重新处理失败资料', { confirmButtonText: '开始处理', cancelButtonText: '取消' }) } catch { return }
  pending.value = 'retry'
  try { await api.reprocessFailed(); ElMessage.success('已提交重新处理任务'); await load() }
  catch (e) { ElMessage.error(message(e)) }
  finally { pending.value = '' }
}
async function download(doc: KnowledgeDocument) { try { await api.downloadDocument(doc) } catch (e) { ElMessage.error(message(e)) } }
onMounted(() => {
  load()
  timer = setInterval(() => { if (route.path === '/knowledge/documents' && !document.hidden && !loading.value) load(true) }, 8000)
})
onUnmounted(() => { requestId++; previewRequest++; clearInterval(timer) })
</script>

<template>
  <div class="page-container knowledge-documents">
    <header class="knowledge-heading"><div><span class="eyebrow">KNOWLEDGE WORKSPACE</span><h1>文件管理</h1><p>从原始资料到知识关联，每一次更新都有迹可循。</p></div><div class="heading-actions"><el-tag round effect="plain">当前账号的独立知识库</el-tag><el-button v-if="canWrite" type="primary" @click="openUpload()">上传资料</el-button></div></header>
    <div class="document-stats"><article><span>全部资料</span><strong>{{ documents.length }}</strong></article><article><span>处理完成</span><strong class="green">{{ completed }}</strong></article><article><span>正在处理</span><strong class="blue">{{ processing }}</strong></article><article><span>需要关注</span><strong class="orange">{{ failed }}</strong></article></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="!canWrite" title="当前账号可查看和下载资料；上传、更新和删除需要管理员或工艺工程师角色。" type="info" :closable="false" />
    <el-card shadow="never" class="document-card">
      <div class="document-toolbar"><el-input v-model="query" clearable placeholder="搜索文件名或文档编号" aria-label="搜索文件名或文档编号" @input="page=1" /><el-select v-model="status" clearable placeholder="全部处理状态" aria-label="筛选处理状态" @change="page=1"><el-option label="处理中" value="PROCESSING" /><el-option label="已完成" value="COMPLETED" /><el-option label="失败" value="FAILED" /><el-option label="未知" value="UNKNOWN" /></el-select><el-button :loading="loading" @click="load()">刷新</el-button><el-button v-if="canWrite" :disabled="!!pending || !documents.length" @click="retry">重新处理失败资料</el-button><router-link to="/knowledge/graph">查看知识图谱 →</router-link></div>
      <p class="list-caption">{{ filtered.length }} 份资料 · 支持 UTF-8 文本，单个文件最大 20 MiB</p>
      <el-alert v-if="documents.length>=200" title="当前接口展示最近 200 份资料，筛选在已加载的资料内进行。" type="info" :closable="false" />
      <el-table v-loading="loading" :data="rows" row-key="docId" empty-text="暂无资料，上传一份工艺或设备文档开始构建知识库">
        <el-table-column label="资料" min-width="220"><template #default="{row}"><div class="document-name"><span class="file-badge">{{ row.fileName?.split('.').pop()?.toUpperCase().slice(0,4) || 'TXT' }}</span><div><strong>{{ row.fileName }}</strong><small :title="row.docId">{{ row.docId }}</small></div></div></template></el-table-column>
        <el-table-column label="处理状态" width="140"><template #default="{row}"><el-tag :type="row.status==='COMPLETED' ? 'success' : row.status==='FAILED' ? 'danger' : 'info'" effect="light">{{ row.statusText }}</el-tag><small class="raw-status">{{ row.rawStatus }}</small></template></el-table-column>
        <el-table-column label="操作" min-width="205"><template #default="{row}"><div class="row-actions"><el-button link type="primary" @click="showPreview(row as KnowledgeDocument)">查看正文</el-button><el-button link type="primary" @click="download(row as KnowledgeDocument)">下载</el-button><el-button v-if="canWrite" link type="primary" :disabled="row.status==='PROCESSING' || !!pending" @click="openUpload(row as KnowledgeDocument)">更新版本</el-button><el-button v-if="canWrite" link type="danger" :disabled="!!pending" @click="remove(row as KnowledgeDocument)">删除</el-button></div></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" class="document-pagination" :total="filtered.length" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" />
    </el-card>
    <el-dialog v-model="uploadVisible" :title="previous ? '更新资料版本' : '上传知识资料'" width="min(620px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!uploading" :show-close="!uploading">
      <el-alert v-if="previous" :title="previous.fileName" description="新版本以独立文件导入，保留旧版本，避免模型处理失败时丢失原资料。新版本处理完成后，可在列表删除旧版。" type="info" show-icon :closable="false" />
      <el-upload ref="uploader" v-model:file-list="queue" drag action="#" :auto-upload="false" :multiple="!previous" :limit="previous ? 1 : 20" :disabled="uploading" accept=".txt,.md,.markdown,.csv,.json,.log" :on-change="changed" :on-exceed="() => ElMessage.warning(previous ? '每次只能选择一个新版本文件' : '每批最多上传 20 个文件')"><div class="upload-icon">＋</div><strong>将文件拖到这里，或点击选择</strong><template #tip><p class="list-caption">TXT / Markdown / CSV / JSON / LOG · UTF-8 编码 · 单文件不超过 20 MiB</p></template></el-upload>
      <div v-for="file in queue" :key="file.uid" class="upload-outcome"><span>{{ file.name }}</span><el-tag v-if="file.uid != null && outcomes[file.uid]" :type="outcomes[file.uid]!.success ? 'success' : 'warning'">{{ outcomes[file.uid]!.message }}</el-tag><span v-else>等待上传</span></div>
      <template #footer><el-button :disabled="uploading" @click="uploadVisible=false">关闭</el-button><el-button type="primary" :loading="uploading" :disabled="!queue.some(file => file.uid != null && !outcomes[file.uid]?.success)" @click="upload">{{ previous ? '导入新版本' : '上传并建立知识索引' }}</el-button></template>
    </el-dialog>
    <el-drawer v-model="previewVisible" title="资料正文" size="min(760px, 94vw)"><div v-loading="previewLoading"><template v-if="preview"><h3>{{ preview.fileName }}</h3><p class="list-caption">{{ preview.docId }}</p><el-alert v-if="preview.content.length>50000" title="当前预览前 50,000 字符，完整内容请下载文件。" :closable="false" /><pre class="document-content">{{ preview.content.slice(0,50000) }}</pre></template><el-empty v-else-if="!previewLoading" description="正文暂不可用，请稍后重试" /></div></el-drawer>
  </div>
</template>

<style scoped>
.knowledge-heading{display:flex;justify-content:space-between;gap:20px;align-items:center;padding:10px 2px}.eyebrow{font-size:10px;letter-spacing:2px;color:#6d8aa6;font-weight:600}.knowledge-heading h1{font-size:26px;letter-spacing:1px;color:#213c56;margin:10px 0}.knowledge-heading p,.list-caption{font-size:12px;color:#8996a7;line-height:1.8}.heading-actions{display:flex;align-items:center;gap:16px;flex-wrap:wrap}.document-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.document-stats article{background:#fff;border:1px solid #e7edf5;border-radius:10px;padding:18px 22px;display:flex;align-items:center;justify-content:space-between}.document-stats span{font-size:12px;color:#8393a5}.document-stats strong{font-size:26px;color:#304962;font-weight:600}.document-stats .green{color:#28a28a}.document-stats .blue{color:#4a8bd5}.document-stats .orange{color:#d8a054}.document-card{border-radius:10px}.document-toolbar{display:flex;gap:10px;flex-wrap:wrap;align-items:center}.document-toolbar>.el-input{width:240px}.document-toolbar>.el-select{width:155px}.document-toolbar>.el-button{margin:0}.document-toolbar>a{margin-left:auto;color:#4089ce;font-size:12px;text-decoration:none}.document-name{display:flex;gap:12px;align-items:center}.file-badge{display:flex;align-items:center;justify-content:center;border-radius:9px;background:#eef5ff;color:#5187bd;width:42px;height:48px;flex-shrink:0;font-size:10px;font-weight:700}.document-name strong{font-size:13px;overflow-wrap:anywhere;color:#40556b}.document-name small{display:block;color:#9aa6b6;font-size:10px;max-width:230px;overflow:hidden;text-overflow:ellipsis;margin-top:7px}.raw-status{display:block;color:#a3adba;font-size:10px;margin-top:6px}.row-actions{display:flex;gap:8px 12px;flex-wrap:wrap}.row-actions .el-button{margin:0;font-size:12px}.document-pagination{justify-content:flex-end;margin-top:22px;max-width:100%;overflow:auto}.document-content{white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.9 inherit;color:#40556b}.upload-icon{font-size:44px;color:#6e9ecb;margin-bottom:15px}.upload-outcome{display:flex;justify-content:space-between;gap:12px;align-items:center;font-size:12px;padding:12px 0;border-bottom:1px solid #edf1f6}.upload-outcome>span{overflow-wrap:anywhere;color:#8190a2}.upload-outcome .el-tag{height:auto;white-space:normal;max-width:65%;line-height:1.8} :deep(.el-upload),:deep(.el-upload-dragger){width:100%} :deep(.el-table th){background:#f7f9fc}@media(max-width:800px){.knowledge-heading{align-items:flex-start;flex-direction:column}.document-stats{grid-template-columns:repeat(2,1fr)}.document-stats article{padding:14px}.document-toolbar>.el-input{width:100%}.document-toolbar>a{margin-left:0}}
</style>
